param(
    [switch]$SkipDoctor,
    [ValidateRange(1,16)][int]$BusinessStartupBatchSize = 2
)

. (Join-Path $PSScriptRoot 'Common.ps1')
Initialize-RuntimeDirectories

$alive = @(Read-ProcessState | Where-Object { Test-ProcessRecordAlive $_ })
if ($alive.Count -gt 0) {
    throw "Managed processes are already running: $(($alive.Name) -join ', '). Run Stop.ps1 first."
}
Write-ProcessState -Processes @()

if (-not $SkipDoctor) {
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'Doctor.ps1')
    if ($LASTEXITCODE -ne 0) {
        throw 'Doctor checks failed. Run Doctor.ps1 without -Quiet for details.'
    }
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'Test-CloudConnections.ps1')
    if ($LASTEXITCODE -ne 0) {
        throw 'Cloud login/schema checks failed. Correct the reported dependency before starting services.'
    }
}

$cloud = Import-CloudEnvironment
$java = Get-JavaExecutable
$javaHome = Split-Path (Split-Path $java -Parent) -Parent
$javaOptions = @(([string]$cloud.JAVA_OPTS -split '\s+') | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
$kafkaClasspath = Get-KafkaClasspath
$kafkaLogConfig = Join-Path $script:ToolsRoot 'kafka\config\log4j.properties'
$kafkaLogDirectory = ConvertTo-ForwardSlashPath $script:LogRoot
$kafkaJavaOptions = @(
    '-Xms128m',
    '-Xmx512m',
    "-Dlog4j.configuration=file:$kafkaLogConfig",
    "-Dkafka.logs.dir=$kafkaLogDirectory"
)
$kafkaEnvironment = @{
    JAVA_HOME = $javaHome
    Path = (Join-Path $javaHome 'bin') + ';' + $env:Path
}

try {
    $kafkaTemplate = Join-Path $script:NoDockerRoot 'config\kafka\server.properties'
    $kafkaConfig = Join-Path $script:StateRoot 'kafka-server.properties'
    $kafkaData = Join-Path $script:DataRoot 'kafka'
    New-Item -ItemType Directory -Force -Path $kafkaData | Out-Null
    $renderedKafka = (Get-Content -Raw -LiteralPath $kafkaTemplate).Replace(
        '{{KAFKA_DATA_DIR}}', (ConvertTo-ForwardSlashPath $kafkaData))
    Set-Content -LiteralPath $kafkaConfig -Value $renderedKafka -Encoding ASCII

    if (-not (Test-Path -LiteralPath (Join-Path $kafkaData 'meta.properties'))) {
        Write-Host 'Formatting the Kafka KRaft data directory (first run only)...'
        $backup = Set-TemporaryProcessEnvironment -Environment $kafkaEnvironment
        try {
            $clusterId = (& $java @kafkaJavaOptions -cp $kafkaClasspath kafka.tools.StorageTool random-uuid | Select-Object -Last 1).Trim()
            if ([string]::IsNullOrWhiteSpace($clusterId)) { throw 'Kafka did not generate a cluster ID.' }
            & $java @kafkaJavaOptions -cp $kafkaClasspath kafka.tools.StorageTool format -t $clusterId -c $kafkaConfig
            if ($LASTEXITCODE -ne 0) { throw 'Kafka storage format failed.' }
        }
        finally { Restore-ProcessEnvironment -Backup $backup }
    }

    Write-Host 'Starting Kafka...'
    Start-ManagedProcess -Name 'kafka' -Kind 'infrastructure' -FilePath $java `
        -ArgumentList @($kafkaJavaOptions + @('-cp', (Quote-NativeArgument $kafkaClasspath), 'kafka.Kafka', (Quote-NativeArgument $kafkaConfig))) `
        -Environment $kafkaEnvironment | Out-Null
    if (-not (Wait-TcpPort -Port 9092 -TimeoutSeconds 60)) { throw 'Kafka did not open port 9092.' }

    $topics = Get-Content -LiteralPath (Join-Path $script:NoDockerRoot 'config\kafka\topics.txt') |
        Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
    $backup = Set-TemporaryProcessEnvironment -Environment $kafkaEnvironment
    try {
        foreach ($topic in $topics) {
            & $java @kafkaJavaOptions -cp $kafkaClasspath org.apache.kafka.tools.TopicCommand `
                --bootstrap-server 127.0.0.1:9092 --create --if-not-exists `
                --topic $topic --partitions 1 --replication-factor 1 | Out-Null
            if ($LASTEXITCODE -ne 0) { throw "Failed to create Kafka topic $topic." }
        }
    }
    finally { Restore-ProcessEnvironment -Backup $backup }

    if ([string]$cloud.GRAFANA_ENABLED -eq 'true') {
        Write-Host 'Starting Grafana Alloy...'
        $alloyEnvironment = @{
            SMA_GATEWAY_ADDRESS = "127.0.0.1:$($cloud.GATEWAY_PORT)"
            SMA_LOG_DIR = ConvertTo-ForwardSlashPath $script:LogRoot
            GRAFANA_PROMETHEUS_URL = [string]$cloud.GRAFANA_PROMETHEUS_URL
            GRAFANA_PROMETHEUS_USER = [string]$cloud.GRAFANA_PROMETHEUS_USER
            GRAFANA_LOKI_URL = [string]$cloud.GRAFANA_LOKI_URL
            GRAFANA_LOKI_USER = [string]$cloud.GRAFANA_LOKI_USER
            GRAFANA_OTLP_URL = [string]$cloud.GRAFANA_OTLP_URL
            GRAFANA_OTLP_USER = [string]$cloud.GRAFANA_OTLP_USER
            GRAFANA_API_TOKEN = [string]$cloud.GRAFANA_API_TOKEN
        }
        $alloyConfig = Join-Path $script:NoDockerRoot 'config\alloy\config.alloy'
        $alloyStorage = Join-Path $script:DataRoot 'alloy'
        New-Item -ItemType Directory -Force -Path $alloyStorage | Out-Null
        Start-ManagedProcess -Name 'alloy' -Kind 'observability' -FilePath (Get-ToolPath 'alloy') `
            -ArgumentList @('run', "--storage.path=$(Quote-NativeArgument $alloyStorage)", (Quote-NativeArgument $alloyConfig)) `
            -Environment $alloyEnvironment | Out-Null
        if (-not (Wait-TcpPort -Port 9411 -TimeoutSeconds 30)) { throw 'Alloy Zipkin receiver did not open port 9411.' }
    }

    $eureka = $script:ServiceDefinitions | Where-Object Name -eq 'eureka-server'
    $common = Get-ServiceProcessEnvironment -Service $eureka -Cloud $cloud
    $jar = Get-ServiceJar $eureka
    Write-Host 'Starting Eureka...'
    Start-ManagedProcess -Name $eureka.Name -Kind 'spring' -FilePath $java `
        -ArgumentList @($javaOptions + @('-jar', (Quote-NativeArgument $jar))) -Environment $common | Out-Null
    if (-not (Wait-ServiceHealth -Name $eureka.Name -Port $eureka.Port)) { throw 'Eureka failed to start.' }

    $configServer = $script:ServiceDefinitions | Where-Object Name -eq 'config-server'
    Write-Host 'Starting Config Server...'
    Start-ManagedProcess -Name $configServer.Name -Kind 'spring' -FilePath $java `
        -ArgumentList @($javaOptions + @('-jar', (Quote-NativeArgument (Get-ServiceJar $configServer)))) `
        -Environment (Get-ServiceProcessEnvironment -Service $configServer -Cloud $cloud) | Out-Null
    if (-not (Wait-ServiceHealth -Name $configServer.Name -Port $configServer.Port)) { throw 'Config Server failed to start.' }

    $businessServices = @($script:ServiceDefinitions | Where-Object Stage -eq 30)
    for ($offset = 0; $offset -lt $businessServices.Count; $offset += $BusinessStartupBatchSize) {
        $last = [math]::Min($offset + $BusinessStartupBatchSize - 1, $businessServices.Count - 1)
        $batch = @($businessServices[$offset..$last])
        foreach ($service in $batch) {
            Write-Host "Starting $($service.Name)..."
            Start-ManagedProcess -Name $service.Name -Kind 'spring' -FilePath $java `
                -ArgumentList @($javaOptions + @('-jar', (Quote-NativeArgument (Get-ServiceJar $service)))) `
                -Environment (Get-ServiceProcessEnvironment -Service $service -Cloud $cloud) | Out-Null
        }
        foreach ($service in $batch) {
            if (-not (Wait-ServiceHealth -Name $service.Name -Port $service.Port -TimeoutSeconds 180)) {
                throw "$($service.Name) failed to start."
            }
        }
    }

    $gateway = $script:ServiceDefinitions | Where-Object Name -eq 'api-gateway'
    Write-Host 'Starting API Gateway...'
    Start-ManagedProcess -Name $gateway.Name -Kind 'spring' -FilePath $java `
        -ArgumentList @($javaOptions + @('-jar', (Quote-NativeArgument (Get-ServiceJar $gateway)))) `
        -Environment (Get-ServiceProcessEnvironment -Service $gateway -Cloud $cloud) | Out-Null
    if (-not (Wait-ServiceHealth -Name $gateway.Name -Port $gateway.Port -TimeoutSeconds 180)) { throw 'API Gateway failed to start.' }

    Write-Host 'Starting Caddy for Flutter Web...'
    $caddyEnvironment = @{
        FRONTEND_ROOT = ConvertTo-ForwardSlashPath (Get-FrontendRoot)
        GATEWAY_UPSTREAM = "127.0.0.1:$($gateway.Port)"
    }
    $caddyConfig = Join-Path $script:NoDockerRoot 'config\Caddyfile'
    Start-ManagedProcess -Name 'frontend' -Kind 'web' -FilePath (Get-ToolPath 'caddy') `
        -ArgumentList @('run', '--config', (Quote-NativeArgument $caddyConfig), '--adapter', 'caddyfile') `
        -Environment $caddyEnvironment | Out-Null
    if (-not (Wait-TcpPort -Port 3001 -TimeoutSeconds 30)) { throw 'Caddy did not open port 3001.' }

    Write-Host ''
    Write-Host 'System is ready:'
    Write-Host '  Flutter Web:  http://localhost:3001'
    Write-Host "  API Gateway:  http://localhost:$($gateway.Port)"
    Write-Host '  Eureka:       http://localhost:8761'
    Write-Host "  Logs:         $script:LogRoot"
}
catch {
    Write-Error -ErrorRecord $_ -ErrorAction Continue
    try { & (Join-Path $PSScriptRoot 'Stop.ps1') }
    catch { Write-Warning "Automatic cleanup failed: $($_.Exception.Message)" }
    exit 1
}
