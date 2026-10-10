Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$script:ProjectRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\..'))
$script:NoDockerRoot = Join-Path $script:ProjectRoot 'infra\no-docker'
$script:RuntimeRoot = Join-Path $script:ProjectRoot '.runtime'
$script:LogRoot = Join-Path $script:RuntimeRoot 'logs'
$script:StateRoot = Join-Path $script:RuntimeRoot 'state'
$script:StateFile = Join-Path $script:StateRoot 'processes.json'
$script:ToolsRoot = Join-Path $script:RuntimeRoot 'tools'
$script:DataRoot = Join-Path $script:RuntimeRoot 'data'
$script:EnvFile = Join-Path $script:RuntimeRoot 'env.ps1'

$script:ServiceDefinitions = @(
    [pscustomobject]@{ Name = 'eureka-server';       Port = 8761; Module = 'infra\eureka-server';             Stage = 10; Store = '' },
    [pscustomobject]@{ Name = 'config-server';       Port = 8888; Module = 'infra\config-server';             Stage = 20; Store = '' },
    [pscustomobject]@{ Name = 'auth-service';        Port = 8081; Module = 'services\auth-service';           Stage = 30; Store = 'postgres'; Schema = 'sma_auth';       EnvPrefix = 'AUTH' },
    [pscustomobject]@{ Name = 'user-service';        Port = 8082; Module = 'services\user-service';           Stage = 30; Store = 'postgres'; Schema = 'sma_user';       EnvPrefix = 'USER' },
    [pscustomobject]@{ Name = 'media-service';       Port = 8083; Module = 'services\media-service';          Stage = 30; Store = 'postgres'; Schema = 'sma_media';      EnvPrefix = 'MEDIA' },
    [pscustomobject]@{ Name = 'post-service';        Port = 8084; Module = 'services\post-service';           Stage = 30; Store = 'postgres'; Schema = 'sma_post';       EnvPrefix = 'POST' },
    [pscustomobject]@{ Name = 'comment-service';     Port = 8085; Module = 'services\comment-service';        Stage = 30; Store = 'postgres'; Schema = 'sma_comment';    EnvPrefix = 'COMMENT' },
    [pscustomobject]@{ Name = 'reaction-service';    Port = 8086; Module = 'services\reaction-service';       Stage = 30; Store = 'postgres'; Schema = 'sma_reaction';   EnvPrefix = 'REACTION' },
    [pscustomobject]@{ Name = 'story-service';       Port = 8087; Module = 'services\story-service';          Stage = 30; Store = 'mongo';    MongoKey = 'MONGODB_STORY_URI' },
    [pscustomobject]@{ Name = 'reels-service';       Port = 8088; Module = 'services\reels-service';          Stage = 30; Store = 'mongo';    MongoKey = 'MONGODB_REELS_URI' },
    [pscustomobject]@{ Name = 'group-service';       Port = 8089; Module = 'services\group-service';          Stage = 30; Store = 'postgres'; Schema = 'sma_group';      EnvPrefix = 'GROUP' },
    [pscustomobject]@{ Name = 'fanpage-service';     Port = 8090; Module = 'services\fanpage-service';        Stage = 30; Store = 'postgres'; Schema = 'sma_fanpage';    EnvPrefix = 'FANPAGE' },
    [pscustomobject]@{ Name = 'dating-service';      Port = 8091; Module = 'services\dating-service';         Stage = 30; Store = 'postgres'; Schema = 'sma_dating';     EnvPrefix = 'DATING' },
    [pscustomobject]@{ Name = 'chat-service';        Port = 8092; Module = 'services\chat-service';           Stage = 30; Store = 'mongo';    MongoKey = 'MONGODB_CHAT_URI' },
    [pscustomobject]@{ Name = 'notification-service'; Port = 8093; Module = 'services\notification-service'; Stage = 30; Store = 'mongo';    MongoKey = 'MONGODB_NOTIFICATION_URI' },
    [pscustomobject]@{ Name = 'feed-service';        Port = 8094; Module = 'services\feed-service';           Stage = 30; Store = '' },
    [pscustomobject]@{ Name = 'moderation-service';  Port = 8095; Module = 'services\moderation-service';     Stage = 30; Store = 'postgres'; Schema = 'sma_moderation'; EnvPrefix = 'MODERATION' },
    [pscustomobject]@{ Name = 'search-service';      Port = 8096; Module = 'services\search-service';         Stage = 30; Store = '' },
    [pscustomobject]@{ Name = 'api-gateway';         Port = 8080; Module = 'infra\api-gateway';               Stage = 40; Store = '' }
)

function Initialize-RuntimeDirectories {
    foreach ($path in @($script:RuntimeRoot, $script:LogRoot, $script:StateRoot, $script:ToolsRoot, $script:DataRoot)) {
        New-Item -ItemType Directory -Force -Path $path | Out-Null
    }
}

function Import-CloudEnvironment {
    if (-not (Test-Path -LiteralPath $script:EnvFile)) {
        throw "Missing $script:EnvFile. Run infra/no-docker/scripts/Setup.ps1 first."
    }
    $CloudEnvironment = $null
    . $script:EnvFile
    if ($null -eq $CloudEnvironment -or $CloudEnvironment -isnot [hashtable]) {
        throw "$script:EnvFile must define a CloudEnvironment hashtable."
    }
    # Older runtime files may predate optional settings added during refactors.
    # Merge the shipped defaults in memory; never rewrite the user's secrets.
    $templatePath = Join-Path $script:NoDockerRoot 'env.example.ps1'
    $defaults = & {
        param($Path)
        $CloudEnvironment = $null
        . $Path
        return $CloudEnvironment
    } $templatePath
    foreach ($key in $CloudEnvironment.Keys) { $defaults[$key] = $CloudEnvironment[$key] }
    $gatewayPort = 0
    if (-not [int]::TryParse([string]$defaults.GATEWAY_PORT, [ref]$gatewayPort) -or $gatewayPort -lt 1024 -or $gatewayPort -gt 65535) {
        throw 'GATEWAY_PORT must be an integer from 1024 to 65535.'
    }
    ($script:ServiceDefinitions | Where-Object Name -eq 'api-gateway').Port = $gatewayPort
    return $defaults
}

function Test-PlaceholderValue {
    param([AllowNull()][object]$Value)
    if ($null -eq $Value) { return $true }
    $text = [string]$Value
    return [string]::IsNullOrWhiteSpace($text) -or
        $text -match '^<.+>$' -or
        $text -match '<[^>]+>' -or
        $text -match 'PROJECT_REF|aws-0-REGION|developer-name|__GENERATED_'
}

function Get-JavaExecutable {
    $bundled = Join-Path $script:ToolsRoot 'java\bin\java.exe'
    if (Test-Path -LiteralPath $bundled) { return $bundled }
    $command = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($null -ne $command) { return $command.Source }
    throw 'Java is missing. Run Setup.ps1 or install Java 17 or newer.'
}

function Get-ToolPath {
    param([Parameter(Mandatory)][ValidateSet('kafka','caddy','alloy')][string]$Tool)
    $relative = switch ($Tool) {
        'kafka' { 'kafka\bin\windows\kafka-server-start.bat' }
        'caddy' { 'caddy\caddy.exe' }
        'alloy' { 'alloy\alloy-windows-amd64.exe' }
    }
    return Join-Path $script:ToolsRoot $relative
}

function Get-KafkaScript {
    param([Parameter(Mandatory)][string]$Name)
    return Join-Path $script:ToolsRoot ("kafka\bin\windows\{0}.bat" -f $Name)
}

function Get-KafkaClasspath {
    return Join-Path $script:ToolsRoot 'kafka\libs\*'
}

function Get-ServiceJar {
    param([Parameter(Mandatory)][pscustomobject]$Service)
    $packaged = Join-Path $script:ProjectRoot ("apps\{0}.jar" -f $Service.Name)
    if (Test-Path -LiteralPath $packaged) { return $packaged }
    return Join-Path $script:ProjectRoot ("{0}\target\{1}.jar" -f $Service.Module, $Service.Name)
}

function Get-FrontendRoot {
    $packaged = Join-Path $script:ProjectRoot 'web'
    if (Test-Path -LiteralPath (Join-Path $packaged 'index.html')) { return $packaged }
    return Join-Path $script:ProjectRoot 'frontend\build\web'
}

function Add-JdbcParameter {
    param([Parameter(Mandatory)][string]$Url, [Parameter(Mandatory)][string]$Name, [Parameter(Mandatory)][string]$Value)
    $parts = $Url -split '\?', 2
    $parameters = @()
    if ($parts.Count -eq 2) {
        $parameters = @($parts[1] -split '&' | Where-Object {
            $_ -and ([uri]::UnescapeDataString(($_ -split '=', 2)[0]) -ine $Name)
        })
    }
    $parameters += "$Name=$([uri]::EscapeDataString($Value))"
    return $parts[0] + '?' + ($parameters -join '&')
}

function Get-CommonProcessEnvironment {
    param([Parameter(Mandatory)][hashtable]$Cloud)
    $corsAllowedOrigins = [string]$Cloud.CORS_ALLOWED_ORIGINS
    if ([string]::IsNullOrWhiteSpace($corsAllowedOrigins)) { $corsAllowedOrigins = 'http://localhost:3001' }
    $environment = @{
        SPRING_PROFILES_ACTIVE = 'cloud-free'
        EUREKA_URI = 'http://127.0.0.1:8761/eureka/'
        CONFIG_SERVER_URI = 'http://127.0.0.1:8888'
        CONFIG_REPO_PATH = (Join-Path $script:ProjectRoot 'config-repo')
        KAFKA_BOOTSTRAP_SERVERS = '127.0.0.1:9092'
        ZIPKIN_ENDPOINT = 'http://127.0.0.1:9411/api/v2/spans'
        TRACING_SAMPLING_PROBABILITY = '1.0'
        CORS_ALLOWED_ORIGINS = $corsAllowedOrigins
        TRUSTED_PROXY_IPS = '127.0.0.1,::1'
        CLOUD_REDIS_HOST = [string]$Cloud.REDIS_HOST
        CLOUD_REDIS_PORT = [string]$Cloud.REDIS_PORT
        CLOUD_REDIS_USERNAME = [string]$Cloud.REDIS_USERNAME
        CLOUD_REDIS_PASSWORD = [string]$Cloud.REDIS_PASSWORD
        CLOUD_REDIS_SSL_ENABLED = [string]$Cloud.REDIS_SSL_ENABLED
        JWT_SECRET = [string]$Cloud.JWT_SECRET
        RATE_LIMIT_REPLENISH = [string]$Cloud.RATE_LIMIT_REPLENISH
        RATE_LIMIT_BURST = [string]$Cloud.RATE_LIMIT_BURST
        RATE_LIMIT_AUTH_REPLENISH = [string]$Cloud.RATE_LIMIT_AUTH_REPLENISH
        RATE_LIMIT_AUTH_BURST = [string]$Cloud.RATE_LIMIT_AUTH_BURST
        RATE_LIMIT_WS_REPLENISH = [string]$Cloud.RATE_LIMIT_WS_REPLENISH
        RATE_LIMIT_WS_BURST = [string]$Cloud.RATE_LIMIT_WS_BURST
    }
    return $environment
}

function Get-ServiceProcessEnvironment {
    param([Parameter(Mandatory)][pscustomobject]$Service, [Parameter(Mandatory)][hashtable]$Cloud)
    $environment = Get-CommonProcessEnvironment -Cloud $Cloud
    $environment.SERVER_PORT = [string]$Service.Port

    if ($Service.Name -eq 'config-server') {
        # Its native backend is selected by this runtime profile. It can still
        # serve application-cloud-free.yml to clients requesting cloud-free.
        $environment.SPRING_PROFILES_ACTIVE = 'native'
    }

    if ($Service.Store -eq 'postgres') {
        # Keep free-plan connection budgets even if optional Config Server loading fails.
        $environment.SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE = '0'
        $environment.SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE = '2'
        $environment.SPRING_DATASOURCE_HIKARI_CONNECTION_TIMEOUT = '15000'
        $environment.SPRING_DATASOURCE_HIKARI_VALIDATION_TIMEOUT = '5000'
        $environment.SPRING_DATASOURCE_HIKARI_IDLE_TIMEOUT = '60000'
        $userKey = "SUPABASE_$($Service.EnvPrefix)_USER"
        $passwordKey = "SUPABASE_$($Service.EnvPrefix)_PASSWORD"
        $username = [string]$Cloud[$userKey]
        $password = [string]$Cloud[$passwordKey]
        if ([string]::IsNullOrWhiteSpace($username)) { $username = [string]$Cloud.SUPABASE_DB_USER }
        if ([string]::IsNullOrWhiteSpace($password)) { $password = [string]$Cloud.SUPABASE_DB_PASSWORD }
        $environment.CLOUD_POSTGRES_JDBC_URL = Add-JdbcParameter -Url ([string]$Cloud.SUPABASE_JDBC_BASE) -Name 'currentSchema' -Value $Service.Schema
        $environment.CLOUD_POSTGRES_USERNAME = $username
        $environment.CLOUD_POSTGRES_PASSWORD = $password
    }
    elseif ($Service.Store -eq 'mongo') {
        $environment.CLOUD_MONGODB_URI = [string]$Cloud[$Service.MongoKey]
    }

    if ($Service.Name -eq 'auth-service') {
        $environment.ADMIN_BOOTSTRAP_TOKEN = [string]$Cloud.ADMIN_BOOTSTRAP_TOKEN
    }

    if ($Service.Name -eq 'media-service') {
        $environment.CLOUD_STORAGE_S3_ENDPOINT = [string]$Cloud.STORAGE_S3_ENDPOINT
        $environment.CLOUD_STORAGE_PUBLIC_ENDPOINT = ([string]$Cloud.STORAGE_PUBLIC_ENDPOINT).TrimEnd('/')
        $environment.CLOUD_STORAGE_ACCESS_KEY = [string]$Cloud.STORAGE_ACCESS_KEY
        $environment.CLOUD_STORAGE_SECRET_KEY = [string]$Cloud.STORAGE_SECRET_KEY
        $environment.CLOUD_STORAGE_REGION = [string]$Cloud.STORAGE_REGION
        $environment.CLOUD_STORAGE_BUCKET = [string]$Cloud.STORAGE_BUCKET
    }
    return $environment
}

function Set-TemporaryProcessEnvironment {
    param([Parameter(Mandatory)][hashtable]$Environment)
    $backup = @{}
    foreach ($entry in $Environment.GetEnumerator()) {
        $backup[$entry.Key] = [Environment]::GetEnvironmentVariable($entry.Key, 'Process')
        [Environment]::SetEnvironmentVariable($entry.Key, [string]$entry.Value, 'Process')
    }
    return $backup
}

function Restore-ProcessEnvironment {
    param([Parameter(Mandatory)][hashtable]$Backup)
    foreach ($entry in $Backup.GetEnumerator()) {
        [Environment]::SetEnvironmentVariable($entry.Key, $entry.Value, 'Process')
    }
}

function Read-ProcessState {
    if (-not (Test-Path -LiteralPath $script:StateFile)) { return @() }
    $content = Get-Content -Raw -LiteralPath $script:StateFile
    if ([string]::IsNullOrWhiteSpace($content)) { return @() }
    $parsed = ConvertFrom-Json $content
    foreach ($record in @($parsed)) { Write-Output $record }
}

function Write-ProcessState {
    param([Parameter(Mandatory)][AllowEmptyCollection()][object[]]$Processes)
    Initialize-RuntimeDirectories
    ConvertTo-Json -InputObject @($Processes) -Depth 5 | Set-Content -LiteralPath $script:StateFile -Encoding UTF8
}

function Test-ProcessRecordAlive {
    param([Parameter(Mandatory)][pscustomobject]$Record)
    $process = Get-Process -Id ([int]$Record.Pid) -ErrorAction SilentlyContinue
    if ($null -eq $process) { return $false }
    try {
        $actual = $process.StartTime.ToUniversalTime()
        # PowerShell 7 deserializes ISO timestamps as DateTime; casting that
        # value to string first drops the UTC marker and changes its meaning.
        $expected = if ($Record.StartedAtUtc -is [datetime]) {
            $Record.StartedAtUtc.ToUniversalTime()
        } else {
            [datetime]::Parse([string]$Record.StartedAtUtc, [Globalization.CultureInfo]::InvariantCulture,
                [Globalization.DateTimeStyles]::RoundtripKind).ToUniversalTime()
        }
        return [math]::Abs(($actual - $expected).TotalSeconds) -lt 3
    }
    catch { return $false }
}

function Add-ProcessRecord {
    param([Parameter(Mandatory)][System.Diagnostics.Process]$Process, [Parameter(Mandatory)][string]$Name, [Parameter(Mandatory)][string]$Kind)
    $records = @(Read-ProcessState | Where-Object { Test-ProcessRecordAlive $_ })
    $Process.Refresh()
    $records += [pscustomobject]@{
        Name = $Name
        Kind = $Kind
        Pid = $Process.Id
        StartedAtUtc = $Process.StartTime.ToUniversalTime().ToString('o')
    }
    Write-ProcessState -Processes $records
}

function Start-ManagedProcess {
    param(
        [Parameter(Mandatory)][string]$Name,
        [Parameter(Mandatory)][string]$Kind,
        [Parameter(Mandatory)][string]$FilePath,
        [string[]]$ArgumentList = @(),
        [hashtable]$Environment = @{},
        [string]$WorkingDirectory = $script:ProjectRoot
    )
    Initialize-RuntimeDirectories
    $stdout = Join-Path $script:LogRoot "$Name.out.log"
    $stderr = Join-Path $script:LogRoot "$Name.err.log"
    $backup = Set-TemporaryProcessEnvironment -Environment $Environment
    try {
        $process = Start-Process -FilePath $FilePath -ArgumentList $ArgumentList `
            -WorkingDirectory $WorkingDirectory -WindowStyle Hidden -PassThru `
            -RedirectStandardOutput $stdout -RedirectStandardError $stderr
    }
    finally {
        Restore-ProcessEnvironment -Backup $backup
    }
    Add-ProcessRecord -Process $process -Name $Name -Kind $Kind
    return $process
}

function Wait-TcpPort {
    param([Parameter(Mandatory)][int]$Port, [int]$TimeoutSeconds = 60)
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        try {
            $client = New-Object System.Net.Sockets.TcpClient
            $task = $client.ConnectAsync('127.0.0.1', $Port)
            if ($task.Wait(500) -and $client.Connected) { $client.Dispose(); return $true }
            $client.Dispose()
        }
        catch { }
        Start-Sleep -Milliseconds 500
    } while ((Get-Date) -lt $deadline)
    return $false
}

function Wait-ServiceHealth {
    param([Parameter(Mandatory)][string]$Name, [Parameter(Mandatory)][int]$Port, [int]$TimeoutSeconds = 120)
    $uri = "http://127.0.0.1:$Port/actuator/health"
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        $record = @(Read-ProcessState | Where-Object Name -eq $Name)
        if ($record.Count -gt 0 -and -not (Test-ProcessRecordAlive $record[0])) {
            Write-Warning "$Name exited before becoming healthy. See its runtime logs."
            return $false
        }
        try {
            $response = Invoke-WebRequest -UseBasicParsing -Uri $uri -TimeoutSec 3
            if ($response.StatusCode -eq 200) { return $true }
        }
        catch { }
        Start-Sleep -Seconds 1
    } while ((Get-Date) -lt $deadline)
    Write-Warning "$Name did not become healthy at $uri. See $script:LogRoot\$Name.err.log"
    return $false
}

function ConvertTo-ForwardSlashPath {
    param([Parameter(Mandatory)][string]$Path)
    return [System.IO.Path]::GetFullPath($Path).Replace('\', '/')
}

function Quote-NativeArgument {
    param([Parameter(Mandatory)][string]$Value)
    return '"' + $Value.Replace('"', '\"') + '"'
}
