# Creates the application's schemas/roles using the versioned setup SQL.
# Does not set database passwords or create business records.
. (Join-Path $PSScriptRoot 'Common.ps1')
$cloud = Import-CloudEnvironment
foreach ($key in @('SUPABASE_JDBC_BASE', 'SUPABASE_DB_USER', 'SUPABASE_DB_PASSWORD')) {
    if (Test-PlaceholderValue $cloud[$key]) { throw "Missing/placeholder: $key" }
}
$jdk = Get-Command javac.exe -ErrorAction SilentlyContinue
if ($null -eq $jdk) { throw 'A JDK with javac is required for schema initialization.' }
$jdkJava = Join-Path (Split-Path $jdk.Source -Parent) 'java.exe'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$work = Join-Path $script:RuntimeRoot ('checks\' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force -Path $work | Out-Null
try {
    $service = $script:ServiceDefinitions | Where-Object Name -eq 'auth-service'
    $archive = [IO.Compression.ZipFile]::OpenRead((Get-ServiceJar $service))
    try {
        $driver = @($archive.Entries | Where-Object { $_.FullName -like 'BOOT-INF/lib/postgresql-*.jar' })
        if ($driver.Count -ne 1) { throw 'Expected exactly one PostgreSQL JDBC driver in auth-service.jar.' }
        $driverPath = Join-Path $work $driver[0].Name
        [IO.Compression.ZipFileExtensions]::ExtractToFile($driver[0], $driverPath)
    } finally { $archive.Dispose() }
    $backup = Set-TemporaryProcessEnvironment -Environment @{
        INIT_SUPABASE_URL = [string]$cloud.SUPABASE_JDBC_BASE
        INIT_SUPABASE_USER = [string]$cloud.SUPABASE_DB_USER
        INIT_SUPABASE_PASSWORD = [string]$cloud.SUPABASE_DB_PASSWORD
    }
    try {
        $source = Join-Path $PSScriptRoot 'InitializeSupabase.java'
        $sql = Join-Path $script:NoDockerRoot 'config\supabase\init-schemas.sql'
        $results = & $jdkJava -cp $driverPath $source $sql 2>$null
        $code = $LASTEXITCODE
        $results | Where-Object { $_ -match '^SupabaseSchemas=(PASS|FAIL) ' } | Write-Host
        if ($code -ne 0 -and -not $results) { Write-Host 'Schema initializer failed before returning results.' }
    } finally { Restore-ProcessEnvironment -Backup $backup }
} finally {
    $prefix = [IO.Path]::GetFullPath((Join-Path $script:RuntimeRoot 'checks')).TrimEnd('\') + '\'
    if (-not [IO.Path]::GetFullPath($work).StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Unexpected schema initializer cleanup path.'
    }
    Remove-Item -LiteralPath $work -Recurse -Force
}
exit $code
