param([switch]$SkipMissing, [switch]$SupabaseOnly)

. (Join-Path $PSScriptRoot 'Common.ps1')
$cloud = Import-CloudEnvironment
$required = @('SUPABASE_JDBC_BASE', 'SUPABASE_DB_USER', 'SUPABASE_DB_PASSWORD',
    'REDIS_HOST', 'REDIS_PORT', 'REDIS_PASSWORD', 'STORAGE_S3_ENDPOINT',
    'STORAGE_ACCESS_KEY', 'STORAGE_SECRET_KEY', 'STORAGE_REGION', 'STORAGE_BUCKET')
$mongo = @($script:ServiceDefinitions | Where-Object Store -eq 'mongo')
if ($SupabaseOnly) {
    $required = @('SUPABASE_JDBC_BASE', 'SUPABASE_DB_USER', 'SUPABASE_DB_PASSWORD')
    $mongo = @()
}
$missing = @($required + @($mongo | ForEach-Object { $_.MongoKey }) | Where-Object { Test-PlaceholderValue $cloud[$_] })
if ($missing.Count -gt 0) {
    Write-Host ('Missing/placeholder: ' + ($missing -join ', '))
    if (-not $SkipMissing -or @($required | Where-Object { Test-PlaceholderValue $cloud[$_] }).Count -gt 0) { exit 1 }
}

# Use the exact drivers packaged with the application, also in distributed bundles.
Add-Type -AssemblyName System.IO.Compression.FileSystem
$checkRoot = Join-Path $script:RuntimeRoot ('checks\' + [guid]::NewGuid().ToString('N'))
$lib = Join-Path $checkRoot 'lib'
New-Item -ItemType Directory -Force -Path $lib | Out-Null
$probeEnvironment = @{}
try {
    foreach ($name in @('auth-service', 'story-service', 'media-service')) {
        $service = $script:ServiceDefinitions | Where-Object Name -eq $name
        $archive = [IO.Compression.ZipFile]::OpenRead((Get-ServiceJar $service))
        try {
            foreach ($entry in $archive.Entries | Where-Object { $_.FullName -like 'BOOT-INF/lib/*.jar' }) {
                $destination = Join-Path $lib $entry.Name
                if (-not (Test-Path -LiteralPath $destination)) {
                    [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $destination)
                }
            }
        } finally { $archive.Dispose() }
    }
    $jdk = Get-Command javac.exe -ErrorAction SilentlyContinue
    if ($null -eq $jdk) { throw 'A JDK with javac is required for connection checks; the runtime JRE alone is insufficient.' }
    $jdkJava = Join-Path (Split-Path $jdk.Source -Parent) 'java.exe'
    $source = Join-Path $PSScriptRoot 'CloudConnectionCheck.java'
    & $jdk.Source -proc:none -encoding UTF-8 -cp "$lib\*" -d $checkRoot $source
    if ($LASTEXITCODE -ne 0) { throw 'Connection checker compilation failed.' }
    $sql = @($script:ServiceDefinitions | Where-Object Store -eq 'postgres')
    $probeEnvironment.CHECK_SQL_COUNT = [string]$sql.Count
    $probeEnvironment.CHECK_SUPABASE_ONLY = $SupabaseOnly.ToString().ToLowerInvariant()
    for ($i = 0; $i -lt $sql.Count; $i++) {
        $service = $sql[$i]
        $settings = Get-ServiceProcessEnvironment -Service $service -Cloud $cloud
        $prefix = "CHECK_SQL_${i}_"
        $probeEnvironment[$prefix + 'NAME'] = $service.Name
        $probeEnvironment[$prefix + 'URL'] = [string]$cloud.SUPABASE_JDBC_BASE
        $probeEnvironment[$prefix + 'USER'] = $settings.CLOUD_POSTGRES_USERNAME
        $probeEnvironment[$prefix + 'PASSWORD'] = $settings.CLOUD_POSTGRES_PASSWORD
        $probeEnvironment[$prefix + 'SCHEMA'] = $service.Schema
    }
    $validMongo = @($mongo | Where-Object { -not (Test-PlaceholderValue $cloud[$_.MongoKey]) })
    $probeEnvironment.CHECK_MONGO_COUNT = [string]$validMongo.Count
    for ($i = 0; $i -lt $validMongo.Count; $i++) {
        $prefix = "CHECK_MONGO_${i}_"
        $probeEnvironment[$prefix + 'NAME'] = $validMongo[$i].Name
        $probeEnvironment[$prefix + 'URI'] = [string]$cloud[$validMongo[$i].MongoKey]
        $probeEnvironment[$prefix + 'DB'] = $validMongo[$i].Name.Replace('-service', '_db')
    }
    foreach ($entry in @{
        CHECK_REDIS_HOST='REDIS_HOST'; CHECK_REDIS_PORT='REDIS_PORT'; CHECK_REDIS_USER='REDIS_USERNAME';
        CHECK_REDIS_PASSWORD='REDIS_PASSWORD'; CHECK_REDIS_TLS='REDIS_SSL_ENABLED';
        CHECK_S3_ENDPOINT='STORAGE_S3_ENDPOINT'; CHECK_S3_REGION='STORAGE_REGION';
        CHECK_S3_KEY='STORAGE_ACCESS_KEY'; CHECK_S3_SECRET='STORAGE_SECRET_KEY'; CHECK_S3_BUCKET='STORAGE_BUCKET'
    }.GetEnumerator()) { $probeEnvironment[$entry.Key] = [string]$cloud[$entry.Value] }
    $backup = Set-TemporaryProcessEnvironment -Environment $probeEnvironment
    try {
        # Suppress SDK logging (it can contain URIs); only explicit probe results are shown.
        $logConfig = Join-Path $checkRoot 'logback.xml'
        Set-Content -LiteralPath $logConfig -Value '<configuration><root level="OFF"/></configuration>' -Encoding UTF8
        $results = & $jdkJava "-Dlogback.configurationFile=$logConfig" -cp "$checkRoot;$lib\*" CloudConnectionCheck 2>$null
        $code = $LASTEXITCODE
        $results | Where-Object { $_ -match '^[A-Za-z0-9-]+=(PASS|FAIL) ' } | Write-Host
        if ($code -ne 0 -and -not $results) { Write-Host 'Connection checker failed before returning results.' }
        if ($missing.Count -gt 0) { $code = 1 }
    } finally { Restore-ProcessEnvironment -Backup $backup }
} finally {
    # Only remove this invocation's verified workspace child, never runtime data.
    $expectedPrefix = [IO.Path]::GetFullPath((Join-Path $script:RuntimeRoot 'checks')).TrimEnd('\') + '\'
    if (-not [IO.Path]::GetFullPath($checkRoot).StartsWith($expectedPrefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Unexpected connection checker cleanup path.'
    }
    Remove-Item -LiteralPath $checkRoot -Recurse -Force
}
exit $code
