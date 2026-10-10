param([switch]$SkipFrontend, [switch]$Offline)
. (Join-Path $PSScriptRoot 'Common.ps1')
Initialize-RuntimeDirectories
$maven = Get-Command mvn.cmd -ErrorAction SilentlyContinue
if ($null -eq $maven) {
    $wrapperRoot = Join-Path $env:USERPROFILE '.m2\wrapper\dists'
    $mavenPath = Get-ChildItem -LiteralPath $wrapperRoot -Filter mvn.cmd -Recurse -ErrorAction SilentlyContinue |
        Sort-Object FullName -Descending | Select-Object -First 1 -ExpandProperty FullName
} else { $mavenPath = $maven.Source }
if (-not $mavenPath) { throw 'Maven is required: install Maven or add mvn.cmd to PATH.' }
Push-Location $script:ProjectRoot
try {
    $arguments = @('-B', '-Pnative-runtime', 'clean', 'package')
    if ($Offline) { $arguments += '-o' }
    & $mavenPath @arguments
    if ($LASTEXITCODE -ne 0) { throw 'Backend build/tests failed; runtime JARs were not replaced.' }
    $apps = Join-Path $script:ProjectRoot 'apps'
    New-Item -ItemType Directory -Force -Path $apps | Out-Null
    foreach ($service in $script:ServiceDefinitions) {
        $jar = Join-Path $script:ProjectRoot ("{0}\.runtime\build\{1}.jar" -f $service.Module, $service.Name)
        if (-not (Test-Path -LiteralPath $jar)) { throw "Missing fresh JAR for $($service.Name)." }
    }
    foreach ($service in $script:ServiceDefinitions) {
        Copy-Item -LiteralPath (Join-Path $script:ProjectRoot ("{0}\.runtime\build\{1}.jar" -f $service.Module, $service.Name)) -Destination (Join-Path $apps "$($service.Name).jar") -Force
    }
    if (-not $SkipFrontend) {
        $flutter = Join-Path $script:ToolsRoot 'flutter\bin\flutter.bat'
        if (-not (Test-Path -LiteralPath $flutter)) {
            $command = Get-Command flutter -ErrorAction SilentlyContinue
            if ($null -eq $command) { throw 'Flutter SDK is required to build the web interface.' }
            $flutter = $command.Source
        }
        Push-Location (Join-Path $script:ProjectRoot 'frontend')
        try {
            & $flutter pub get --enforce-lockfile
            if ($LASTEXITCODE -ne 0) { throw 'Flutter dependency resolution failed.' }
            & $flutter build web --release
            if ($LASTEXITCODE -ne 0) { throw 'Flutter Web build failed.' }
        } finally { Pop-Location }
    }
} finally { Pop-Location }
Write-Host 'Fresh runtime artifacts built. Run Doctor.ps1, then Start.ps1.'
