param(
    [Parameter(Mandatory)][string]$BackendHost,
    [Parameter(Mandatory)][string]$PagesOrigin,
    [string]$AlloyBinary,
    [switch]$BuildOnVm
)
. (Join-Path $PSScriptRoot 'Common.ps1')
if ($BackendHost -notmatch '^[a-z0-9][a-z0-9.-]+[a-z0-9]$' -or $BackendHost -match '\.\.') {
    throw 'BackendHost must be a DNS hostname without scheme, port or path.'
}
$origin = [uri]$PagesOrigin
if ($origin.Scheme -ne 'https' -or $origin.UserInfo -or $origin.Query -or $origin.Fragment -or $origin.AbsolutePath -ne '/') {
    throw 'PagesOrigin must be an HTTPS origin.'
}
$cloud = Import-CloudEnvironment
foreach ($service in $script:ServiceDefinitions) {
    if (-not $BuildOnVm -and -not (Test-Path -LiteralPath (Get-ServiceJar $service))) { throw "Missing JAR for $($service.Name)." }
    if ($service.Store -eq 'mongo' -and ([string]$cloud[$service.MongoKey] -match '<db_password>|YOUR.PASSWORD')) {
        throw "Set $($service.MongoKey) before packaging."
    }
}
if (-not $BuildOnVm -and [string]$cloud.GRAFANA_ENABLED -eq 'true' -and
    (-not $AlloyBinary -or -not (Test-Path -LiteralPath $AlloyBinary -PathType Leaf))) {
    throw 'Supply the Linux Alloy binary when GRAFANA_ENABLED=true.'
}
$packageRoot = Join-Path $script:RuntimeRoot ('packages/linux-' + [guid]::NewGuid().ToString('N'))
foreach ($child in @('apps','env','config','config-repo','bin','systemd','kafka')) {
    New-Item -ItemType Directory -Force -Path (Join-Path $packageRoot $child) | Out-Null
}
$utf8 = New-Object Text.UTF8Encoding($false)
function Write-Utf8File([string]$Path, [string]$Text) {
    [IO.File]::WriteAllText($Path, ($Text -replace "`r`n", "`n"), $utf8)
}
function Write-SystemdEnvironment([string]$Path, [hashtable]$Values) {
    $lines = foreach ($key in ($Values.Keys | Sort-Object)) {
        $value = [string]$Values[$key]
        if ($value -match '[\r\n]') { throw "Multiline environment value not supported: $key" }
        $escaped = $value.Replace('\', '\\').Replace('"', '\"')
        "$key=`"$escaped`""
    }
    Write-Utf8File $Path (($lines -join "`n") + "`n")
}
$cloud.CORS_ALLOWED_ORIGINS = $origin.GetLeftPart([UriPartial]::Authority)
$serviceLines = @()
foreach ($service in ($script:ServiceDefinitions | Sort-Object Stage)) {
    if (-not $BuildOnVm) {
        Copy-Item -LiteralPath (Get-ServiceJar $service) -Destination (Join-Path $packageRoot "apps/$($service.Name).jar")
    }
    $environment = Get-ServiceProcessEnvironment -Service $service -Cloud $cloud
    $environment.CONFIG_REPO_PATH = '/opt/doan/config-repo'
    $environment.JAVA_OPTS = [string]$cloud.JAVA_OPTS
    $environment.TRACING_SAMPLING_PROBABILITY = '0.1'
    Write-SystemdEnvironment (Join-Path $packageRoot "env/$($service.Name).env") $environment
    $unit = @"
[Unit]
Description=DoAn $($service.Name)
After=network-online.target doan-kafka.service
Wants=network-online.target

[Service]
User=doan
Group=doan
WorkingDirectory=/opt/doan
EnvironmentFile=/opt/doan/env/$($service.Name).env
ExecStart=/usr/bin/java `$JAVA_OPTS -jar /opt/doan/apps/$($service.Name).jar
ExecStartPost=/opt/doan/bin/wait-health.sh $($service.Port) 180
Restart=on-failure
RestartSec=10
TimeoutStartSec=240
StandardOutput=append:/opt/doan/logs/$($service.Name).out.log
StandardError=append:/opt/doan/logs/$($service.Name).err.log
NoNewPrivileges=true
PrivateTmp=true

"@
    Write-Utf8File (Join-Path $packageRoot "systemd/doan-$($service.Name).service") $unit
    $serviceLines += "$($service.Name)`t$($service.Port)"
}
Write-Utf8File (Join-Path $packageRoot 'config/services.tsv') (($serviceLines -join "`n") + "`n")
Copy-Item -Path (Join-Path $script:ProjectRoot 'config-repo/*') -Destination (Join-Path $packageRoot 'config-repo') -Recurse
if ($BuildOnVm) {
    $sourceArchive = Join-Path $packageRoot 'source.tar'
    & git -C $script:ProjectRoot archive --format=tar "--output=$sourceArchive" HEAD pom.xml common infra/api-gateway infra/config-server infra/eureka-server services
    if ($LASTEXITCODE) { throw 'Source package creation failed.' }
} else {
    Copy-Item -Path (Join-Path $script:ToolsRoot 'kafka/*') -Destination (Join-Path $packageRoot 'kafka') -Recurse
}
$kafkaConfig = (Get-Content -LiteralPath (Join-Path $script:NoDockerRoot 'config/kafka/server.properties') -Raw).Replace('{{KAFKA_DATA_DIR}}','/opt/doan/data/kafka')
Write-Utf8File (Join-Path $packageRoot 'config/kafka.properties') $kafkaConfig
$topics = Get-Content -LiteralPath (Join-Path $script:NoDockerRoot 'config/kafka/topics.txt')
Write-Utf8File (Join-Path $packageRoot 'config/topics.txt') (($topics -join "`n") + "`n")
foreach ($file in @('start.sh','wait-health.sh','install.sh','build-runtime.sh')) {
    Write-Utf8File (Join-Path $packageRoot "bin/$file") (Get-Content -LiteralPath (Join-Path $script:NoDockerRoot "linux/$file") -Raw)
}
Write-Utf8File (Join-Path $packageRoot 'systemd/doan-kafka.service') @'
[Unit]
Description=DoAn native Kafka
After=network-online.target
Wants=network-online.target
[Service]
User=doan
Group=doan
WorkingDirectory=/opt/doan
ExecStart=/usr/bin/java -Xms128m -Xmx512m -Dlog4j.configuration=file:/opt/doan/kafka/config/log4j.properties -Dkafka.logs.dir=/opt/doan/logs -cp /opt/doan/kafka/libs/* kafka.Kafka /opt/doan/config/kafka.properties
Restart=on-failure
RestartSec=10
StandardOutput=append:/opt/doan/logs/kafka.out.log
StandardError=append:/opt/doan/logs/kafka.err.log
NoNewPrivileges=true
PrivateTmp=true

'@
Write-Utf8File (Join-Path $packageRoot 'systemd/doan-start.service') @'
[Unit]
Description=Start DoAn services sequentially after boot
After=network-online.target
Wants=network-online.target
[Service]
Type=oneshot
ExecStart=/opt/doan/bin/start.sh
RemainAfterExit=yes
TimeoutStartSec=3600
Restart=on-failure
RestartSec=30
[Install]
WantedBy=multi-user.target

'@
if ([string]$cloud.GRAFANA_ENABLED -eq 'true') {
    if (-not $BuildOnVm) {
        Copy-Item -LiteralPath $AlloyBinary -Destination (Join-Path $packageRoot 'bin/alloy')
    } else {
        $alloyHash = (Get-FileHash -LiteralPath (Join-Path $script:ToolsRoot 'alloy-linux/alloy-linux-amd64.zip') -Algorithm SHA256).Hash.ToLowerInvariant()
        Write-Utf8File (Join-Path $packageRoot 'config/alloy.sha256') $alloyHash
    }
    Copy-Item -LiteralPath (Join-Path $script:NoDockerRoot 'config/alloy/config.alloy') -Destination (Join-Path $packageRoot 'config/config.alloy')
    $grafana = @{SMA_GATEWAY_ADDRESS="127.0.0.1:$($cloud.GATEWAY_PORT)";SMA_LOG_DIR='/opt/doan/logs'}
    foreach ($key in @('GRAFANA_PROMETHEUS_URL','GRAFANA_PROMETHEUS_USER','GRAFANA_LOKI_URL','GRAFANA_LOKI_USER','GRAFANA_OTLP_URL','GRAFANA_OTLP_USER','GRAFANA_API_TOKEN')) {
        $grafana[$key] = [string]$cloud[$key]
    }
    Write-SystemdEnvironment (Join-Path $packageRoot 'env/alloy.env') $grafana
    Write-Utf8File (Join-Path $packageRoot 'systemd/doan-alloy.service') @'
[Unit]
Description=DoAn Grafana Alloy
After=network-online.target
[Service]
User=doan
Group=doan
EnvironmentFile=/opt/doan/env/alloy.env
ExecStart=/opt/doan/bin/alloy run --storage.path=/opt/doan/data/alloy /opt/doan/config/config.alloy
Restart=on-failure
RestartSec=10
StandardOutput=append:/opt/doan/logs/alloy.out.log
StandardError=append:/opt/doan/logs/alloy.err.log

'@
}
$caddyConfig = @"
$BackendHost {
    @api path /api/* /ws /ws/* /ws-notifications /ws-notifications/*
    handle @api {
        reverse_proxy 127.0.0.1:$($cloud.GATEWAY_PORT) {
            header_up X-Forwarded-For {http.request.remote.host}
            header_up X-Real-IP {http.request.remote.host}
            header_up -Forwarded
        }
    }
    handle {
        respond 404
    }
}
"@
Write-Utf8File (Join-Path $packageRoot 'config/Caddyfile') $caddyConfig
$archive = Join-Path $script:RuntimeRoot 'packages/azure-runtime.tar.gz'
& tar.exe -czf $archive -C $packageRoot .
if ($LASTEXITCODE) { throw 'Failed to package Linux runtime.' }
Write-Host "Private Linux runtime package: $archive"
Write-Host 'Contains backend credentials; transfer only to the intended VM.'
