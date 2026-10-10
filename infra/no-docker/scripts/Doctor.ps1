param(
    [switch]$Quiet,
    [switch]$SkipNetwork
)

. (Join-Path $PSScriptRoot 'Common.ps1')
Initialize-RuntimeDirectories
$checks = New-Object System.Collections.Generic.List[object]

function Add-Check {
    param([string]$Name, [bool]$Passed, [string]$Detail, [bool]$Blocking = $true)
    $checks.Add([pscustomobject]@{
        Check = $Name
        Result = if ($Passed) { 'OK' } elseif ($Blocking) { 'FAIL' } else { 'WARN' }
        Detail = $Detail
        Blocking = $Blocking
    })
}

function Test-RemotePort {
    param([string]$HostName, [int]$Port)
    try {
        $client = New-Object System.Net.Sockets.TcpClient
        $task = $client.ConnectAsync($HostName, $Port)
        $connected = $task.Wait(4000) -and $client.Connected
        $client.Dispose()
        return $connected
    }
    catch { return $false }
}

$cloud = $null
try {
    $cloud = Import-CloudEnvironment
    Add-Check 'Environment file' $true $script:EnvFile
}
catch {
    Add-Check 'Environment file' $false $_.Exception.Message
}

if ($null -ne $cloud) {
    $requiredKeys = @(
        'ENVIRONMENT_ID', 'SUPABASE_JDBC_BASE', 'SUPABASE_DB_USER', 'SUPABASE_DB_PASSWORD',
        'MONGODB_STORY_URI', 'MONGODB_REELS_URI', 'MONGODB_CHAT_URI', 'MONGODB_NOTIFICATION_URI',
        'REDIS_HOST', 'REDIS_PORT', 'REDIS_PASSWORD', 'STORAGE_S3_ENDPOINT',
        'STORAGE_PUBLIC_ENDPOINT', 'STORAGE_ACCESS_KEY', 'STORAGE_SECRET_KEY', 'STORAGE_REGION',
        'STORAGE_BUCKET', 'JWT_SECRET'
    )
    if ([string]$cloud.GRAFANA_ENABLED -eq 'true') {
        $requiredKeys += @('GRAFANA_PROMETHEUS_URL', 'GRAFANA_PROMETHEUS_USER', 'GRAFANA_LOKI_URL',
            'GRAFANA_LOKI_USER', 'GRAFANA_OTLP_URL', 'GRAFANA_OTLP_USER', 'GRAFANA_API_TOKEN')
    }
    $missing = @($requiredKeys | Where-Object { Test-PlaceholderValue $cloud[$_] })
    Add-Check 'Cloud credentials' ($missing.Count -eq 0) $(if ($missing.Count) { 'Missing/placeholder: ' + ($missing -join ', ') } else { 'All required values are set' })

    $jdbcMatch = [regex]::Match([string]$cloud.SUPABASE_JDBC_BASE, '^jdbc:postgresql://([^:/?]+)(?::(\d+))?')
    Add-Check 'Supabase JDBC URL' $jdbcMatch.Success 'Expected jdbc:postgresql://host:port/database?sslmode=require'
    $jdbc = [string]$cloud.SUPABASE_JDBC_BASE
    Add-Check 'Supabase TLS mode' ($jdbc -match '(?:[?&])sslmode=(?:require|verify-full)(?:&|$)') 'Use sslmode=require for local demo or verify-full with trusted CA for deployment'
    Add-Check 'JDBC credential separation' ($jdbc -notmatch '(?:[?&])(?:user|password)=|://[^/]*@') 'Keep username/password in their separate environment fields'
    if ($jdbcMatch.Success -and $jdbcMatch.Groups[1].Value -like '*.pooler.supabase.com') {
        Add-Check 'Supabase session pooler' ($jdbcMatch.Groups[2].Value -eq '5432') 'Persistent Hibernate clients use session mode on port 5432'
    }
    foreach ($service in $script:ServiceDefinitions | Where-Object Store -eq 'postgres') {
        $userKey = "SUPABASE_$($service.EnvPrefix)_USER"
        $passwordKey = "SUPABASE_$($service.EnvPrefix)_PASSWORD"
        $hasUser = -not [string]::IsNullOrWhiteSpace([string]$cloud[$userKey])
        $hasPassword = -not [string]::IsNullOrWhiteSpace([string]$cloud[$passwordKey])
        Add-Check "DB credential pair $($service.Name)" ($hasUser -eq $hasPassword) "Set both $userKey and $passwordKey, or leave both empty"
    }
    foreach ($service in $script:ServiceDefinitions | Where-Object Store -eq 'mongo') {
        $mongoUri = [string]$cloud[$service.MongoKey]
        if (-not (Test-PlaceholderValue $mongoUri)) {
            $db = $service.Name.Replace('-service', '_db')
            Add-Check "Mongo URI $($service.Name)" ($mongoUri -match ('^mongodb(?:\+srv)?://[^/]+/' + [regex]::Escape($db) + '(?:\?|$)')) "Use an Atlas URI with database $db"
        }
    }
    if ($jdbcMatch.Success -and -not $SkipNetwork -and $missing.Count -eq 0) {
        $dbPort = if ($jdbcMatch.Groups[2].Success) { [int]$jdbcMatch.Groups[2].Value } else { 5432 }
        Add-Check 'Supabase reachable' (Test-RemotePort $jdbcMatch.Groups[1].Value $dbPort) "$($jdbcMatch.Groups[1].Value):$dbPort"
        Add-Check 'Valkey reachable' (Test-RemotePort ([string]$cloud.REDIS_HOST) ([int]$cloud.REDIS_PORT)) "$($cloud.REDIS_HOST):$($cloud.REDIS_PORT)"
        try {
            $storageUri = [uri]$cloud.STORAGE_S3_ENDPOINT
            $storagePort = if ($storageUri.IsDefaultPort) { 443 } else { $storageUri.Port }
            Add-Check 'Storage reachable' (Test-RemotePort $storageUri.Host $storagePort) "$($storageUri.Host):$storagePort"
        }
        catch { Add-Check 'Storage URL' $false 'Invalid STORAGE_S3_ENDPOINT' }
    }
}

try {
    $java = Get-JavaExecutable
    $javaInfo = New-Object System.Diagnostics.ProcessStartInfo
    $javaInfo.FileName = $java
    $javaInfo.Arguments = '-version'
    $javaInfo.UseShellExecute = $false
    $javaInfo.RedirectStandardError = $true
    $javaProcess = [System.Diagnostics.Process]::Start($javaInfo)
    $versionLine = $javaProcess.StandardError.ReadLine()
    $javaProcess.WaitForExit()
    $versionMatch = [regex]::Match($versionLine, 'version "(\d+)')
    $valid = $versionMatch.Success -and [int]$versionMatch.Groups[1].Value -ge 17
    Add-Check 'Java runtime' $valid "$java - $versionLine"
}
catch { Add-Check 'Java runtime' $false $_.Exception.Message }

foreach ($tool in @('kafka', 'caddy', 'alloy')) {
    if ($tool -eq 'alloy' -and $null -ne $cloud -and [string]$cloud.GRAFANA_ENABLED -ne 'true') { continue }
    $path = Get-ToolPath $tool
    Add-Check "$tool tool" (Test-Path -LiteralPath $path) $path
}

foreach ($service in $script:ServiceDefinitions) {
    $jar = Get-ServiceJar $service
    Add-Check "JAR $($service.Name)" (Test-Path -LiteralPath $jar) $jar
}
$frontend = Get-FrontendRoot
Add-Check 'Flutter Web build' (Test-Path -LiteralPath (Join-Path $frontend 'index.html')) $frontend

$managed = @(Read-ProcessState | Where-Object { Test-ProcessRecordAlive $_ })
foreach ($port in @(9092, 9411, 3001) + @($script:ServiceDefinitions.Port)) {
    $listener = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($null -ne $listener) {
        $known = $managed | Where-Object { [int]$_.Pid -eq [int]$listener.OwningProcess }
        Add-Check "Local port $port" ($null -ne $known) $(if ($known) { "Managed by $($known.Name)" } else { "Already used by PID $($listener.OwningProcess)" })
    }
}

$drive = New-Object System.IO.DriveInfo ([System.IO.Path]::GetPathRoot($script:ProjectRoot))
$freeGb = [math]::Round($drive.AvailableFreeSpace / 1GB, 2)
Add-Check 'Free disk space' ($freeGb -ge 2) "$freeGb GB available; packaged runtime target is below 10 GB"

if (-not $Quiet) {
    $checks | Format-Table -AutoSize
}
$failures = @($checks | Where-Object { $_.Blocking -and $_.Result -eq 'FAIL' })
if ($failures.Count -gt 0) { exit 1 }
exit 0
