param(
    [Parameter(Mandatory, ParameterSetName='Connected')][string]$ApiBaseUrl,
    [Parameter(Mandatory, ParameterSetName='Pending')][switch]$BackendPending
)
$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
if (-not $BackendPending) {
try { $api = [uri]$ApiBaseUrl } catch { throw 'ApiBaseUrl must be a public HTTPS origin.' }
if (-not $api.IsAbsoluteUri -or $api.Scheme -ne 'https' -or $api.IsLoopback -or
    $api.UserInfo -or $api.Query -or $api.Fragment -or $api.AbsolutePath -ne '/') {
    throw 'ApiBaseUrl must be a public HTTPS origin without credentials, path, query or fragment.'
}
}
$flutter = Join-Path $projectRoot '.runtime\tools\flutter\bin\flutter.bat'
if (-not (Test-Path -LiteralPath $flutter)) {
    $command = Get-Command flutter -ErrorAction SilentlyContinue
    if ($null -eq $command) { throw 'Flutter 3.47.5 is required on PATH or under .runtime/tools/flutter.' }
    $flutter = $command.Source
}
Push-Location (Join-Path $projectRoot 'frontend')
try {
    & $flutter pub get --enforce-lockfile
    if ($LASTEXITCODE -ne 0) { throw 'Flutter dependency resolution failed.' }
    $defines = if ($BackendPending) {
        @('--dart-define=BACKEND_PENDING=true')
    } else {
        @("--dart-define=API_BASE_URL=$($api.GetLeftPart([UriPartial]::Authority))")
    }
    & $flutter build web --release @defines
    if ($LASTEXITCODE -ne 0) { throw 'Flutter Pages build failed.' }
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot '_headers') -Destination build/web/_headers -Force
} finally { Pop-Location }
Write-Host 'Cloudflare Pages assets ready in frontend/build/web.'
