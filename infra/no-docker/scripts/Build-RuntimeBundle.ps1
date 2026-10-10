param(
    [string]$OutputDirectory = '',
    [string]$ApiBaseUrl = '',
    [switch]$SkipBackendBuild,
    [switch]$SkipFrontendBuild,
    [switch]$IncludeTools
)

. (Join-Path $PSScriptRoot 'Common.ps1')
if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
    $OutputDirectory = Join-Path $script:ProjectRoot 'dist\no-docker-runtime'
}
$OutputDirectory = [System.IO.Path]::GetFullPath($OutputDirectory)
$projectPrefix = [System.IO.Path]::GetFullPath($script:ProjectRoot).TrimEnd('\') + '\'
if (-not $OutputDirectory.StartsWith($projectPrefix, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'OutputDirectory must be inside the project workspace.'
}

if (-not $SkipBackendBuild) {
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'Build.ps1') -SkipFrontend
    if ($LASTEXITCODE -ne 0) { throw 'Backend build failed.' }
}

if (-not $SkipFrontendBuild) {
    $flutterPath = Join-Path $script:ToolsRoot 'flutter\bin\flutter.bat'
    if (-not (Test-Path -LiteralPath $flutterPath)) {
        $flutter = Get-Command flutter -ErrorAction SilentlyContinue
        if ($null -eq $flutter) { throw 'Flutter SDK is required to build Flutter Web.' }
        $flutterPath = $flutter.Source
    }
    Push-Location (Join-Path $script:ProjectRoot 'frontend')
    try {
        & $flutterPath pub get --enforce-lockfile
        if ($LASTEXITCODE -ne 0) { throw 'flutter pub get failed.' }
        if ([string]::IsNullOrWhiteSpace($ApiBaseUrl)) {
            & $flutterPath build web --release
        } else {
            & $flutterPath build web --release "--dart-define=API_BASE_URL=$ApiBaseUrl"
        }
        if ($LASTEXITCODE -ne 0) { throw 'Flutter Web build failed.' }
    }
    finally { Pop-Location }
}

if (Test-Path -LiteralPath $OutputDirectory) {
    Remove-Item -LiteralPath $OutputDirectory -Recurse -Force
}
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$apps = Join-Path $OutputDirectory 'apps'
New-Item -ItemType Directory -Force -Path $apps | Out-Null

foreach ($service in $script:ServiceDefinitions) {
    $jar = Get-ServiceJar $service
    if (-not (Test-Path -LiteralPath $jar)) { throw "Missing built artifact: $jar" }
    Copy-Item -LiteralPath $jar -Destination (Join-Path $apps "$($service.Name).jar")
}

$webSource = Join-Path $script:ProjectRoot 'frontend\build\web'
if (-not (Test-Path -LiteralPath (Join-Path $webSource 'index.html'))) {
    throw "Missing Flutter Web build: $webSource"
}
Copy-Item -LiteralPath $webSource -Destination (Join-Path $OutputDirectory 'web') -Recurse
Copy-Item -LiteralPath (Join-Path $script:ProjectRoot 'config-repo') -Destination (Join-Path $OutputDirectory 'config-repo') -Recurse

$targetInfra = Join-Path $OutputDirectory 'infra\no-docker'
New-Item -ItemType Directory -Force -Path (Split-Path $targetInfra -Parent) | Out-Null
Copy-Item -LiteralPath $script:NoDockerRoot -Destination $targetInfra -Recurse
$grafanaTarget = Join-Path $targetInfra 'config\grafana'
New-Item -ItemType Directory -Force -Path $grafanaTarget | Out-Null
Copy-Item -LiteralPath (Join-Path $script:ProjectRoot 'docker\observability\dashboards\service-overview.json') `
    -Destination (Join-Path $grafanaTarget 'service-overview.json')

foreach ($document in @('README.md', 'NO_DOCKER_SETUP.md', 'PLAN.md', 'LICENSE',
        'start-no-docker.bat', 'stop-no-docker.bat')) {
    $source = Join-Path $script:ProjectRoot $document
    if (Test-Path -LiteralPath $source) { Copy-Item -LiteralPath $source -Destination $OutputDirectory }
}

if ($IncludeTools) {
    $sourceTools = Join-Path $script:RuntimeRoot 'tools'
    if (-not (Test-Path -LiteralPath $sourceTools)) { throw 'Run Setup.ps1 before packaging with -IncludeTools.' }
    $targetRuntime = Join-Path $OutputDirectory '.runtime'
    New-Item -ItemType Directory -Force -Path $targetRuntime | Out-Null
    Copy-Item -LiteralPath $sourceTools -Destination (Join-Path $targetRuntime 'tools') -Recurse
}

$files = @(Get-ChildItem -LiteralPath $OutputDirectory -File -Recurse | ForEach-Object {
    [pscustomobject]@{
        Path = $_.FullName.Substring($OutputDirectory.Length + 1).Replace('\', '/')
        Bytes = $_.Length
        Sha256 = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
    }
})
$totalBytes = [long]($files | Measure-Object -Property Bytes -Sum).Sum
$commit = (& git -C $script:ProjectRoot rev-parse HEAD 2>$null | Select-Object -First 1)
$manifest = [ordered]@{
    CreatedAtUtc = (Get-Date).ToUniversalTime().ToString('o')
    GitCommit = $commit
    TotalBytes = $totalBytes
    TotalGB = [math]::Round($totalBytes / 1GB, 3)
    Files = $files
}
$manifest | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $OutputDirectory 'manifest.json') -Encoding UTF8

if ($totalBytes -gt 10GB) { throw "Runtime bundle is $([math]::Round($totalBytes / 1GB, 2)) GB, above the 10 GB target." }
Write-Host "Runtime bundle created at $OutputDirectory"
Write-Host "Size: $([math]::Round($totalBytes / 1MB, 1)) MB before downloaded runtime tools"
