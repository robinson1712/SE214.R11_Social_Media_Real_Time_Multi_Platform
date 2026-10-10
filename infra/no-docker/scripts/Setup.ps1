param(
    [switch]$SkipDownloads,
    [switch]$Force
)

. (Join-Path $PSScriptRoot 'Common.ps1')
Initialize-RuntimeDirectories

[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
$downloadRoot = Join-Path $script:RuntimeRoot 'downloads'
New-Item -ItemType Directory -Force -Path $downloadRoot | Out-Null

function Reset-RuntimeChild {
    param([Parameter(Mandatory)][string]$Path)
    $resolvedRuntime = [System.IO.Path]::GetFullPath($script:RuntimeRoot).TrimEnd('\') + '\'
    $resolvedTarget = [System.IO.Path]::GetFullPath($Path)
    if (-not $resolvedTarget.StartsWith($resolvedRuntime, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to remove path outside .runtime: $resolvedTarget"
    }
    if (Test-Path -LiteralPath $resolvedTarget) {
        Remove-Item -LiteralPath $resolvedTarget -Recurse -Force
    }
}

function Get-ExpectedHash {
    param([Parameter(Mandatory)][hashtable]$Tool)
    if ($Tool.ContainsKey('ChecksumUri')) {
        $checksumContent = (Invoke-WebRequest -UseBasicParsing -Uri $Tool.ChecksumUri).Content
        $checksumText = if ($checksumContent -is [byte[]]) {
            [Text.Encoding]::UTF8.GetString($checksumContent)
        } else {
            [string]$checksumContent
        }
        $length = if ($Tool.Algorithm -eq 'SHA512') { 128 } else { 64 }
        $escapedName = [regex]::Escape([string]$Tool.FileName)
        $match = [regex]::Match($checksumText, "(?im)([a-f0-9]{$length})\s+\*?$escapedName")
        if (-not $match.Success) {
            $bsdMatch = [regex]::Match($checksumText, "(?ims)$escapedName\s*:\s*([a-f0-9\s]+)")
            if ($bsdMatch.Success) {
                $bsdHash = [regex]::Replace($bsdMatch.Groups[1].Value, '\s', '')
                if ($bsdHash.Length -eq $length) { return $bsdHash.ToLowerInvariant() }
            }
        }
        if (-not $match.Success) {
            $match = [regex]::Match($checksumText, "(?im)\b([a-f0-9]{$length})\b")
        }
        if (-not $match.Success) { throw "Cannot find checksum for $($Tool.FileName)." }
        return $match.Groups[1].Value.ToLowerInvariant()
    }

    $release = Invoke-RestMethod -Uri $Tool.DigestApi -Headers @{ 'User-Agent' = 'social-media-no-docker-setup' }
    $asset = $release.assets | Where-Object { $_.name -eq $Tool.FileName } | Select-Object -First 1
    if ($null -eq $asset -or [string]::IsNullOrWhiteSpace([string]$asset.digest)) {
        throw "Release API did not provide a digest for $($Tool.FileName)."
    }
    return ([string]$asset.digest).Replace('sha256:', '').ToLowerInvariant()
}

function Get-VerifiedArchive {
    param([Parameter(Mandatory)][string]$Name, [Parameter(Mandatory)][hashtable]$Tool)
    $archive = Join-Path $downloadRoot $Tool.FileName
    $expected = Get-ExpectedHash -Tool $Tool

    if ((Test-Path -LiteralPath $archive) -and -not $Force) {
        $cachedHash = (Get-FileHash -LiteralPath $archive -Algorithm $Tool.Algorithm).Hash.ToLowerInvariant()
        if ($cachedHash -eq $expected) { return $archive }
        Write-Host "Discarding incomplete or invalid cached download for $Name."
        Remove-Item -LiteralPath $archive -Force
    }

    $partial = "$archive.part"
    if (Test-Path -LiteralPath $partial) { Remove-Item -LiteralPath $partial -Force }
    Write-Host "Downloading $Name $($Tool.Version)..."
    $curl = Get-Command 'curl.exe' -ErrorAction SilentlyContinue
    if ($null -ne $curl) {
        & $curl.Source --fail --location --retry 3 --retry-delay 2 --output $partial $Tool.Uri
        if ($LASTEXITCODE -ne 0) {
            if (Test-Path -LiteralPath $partial) { Remove-Item -LiteralPath $partial -Force }
            throw "Failed to download $Name from $($Tool.Uri)."
        }
    } else {
        Invoke-WebRequest -UseBasicParsing -Uri $Tool.Uri -OutFile $partial
    }

    $actual = (Get-FileHash -LiteralPath $partial -Algorithm $Tool.Algorithm).Hash.ToLowerInvariant()
    if ($actual -ne $expected) {
        Remove-Item -LiteralPath $partial -Force
        throw "Checksum mismatch for $($Tool.FileName). Expected $expected, got $actual."
    }
    Move-Item -LiteralPath $partial -Destination $archive
    return $archive
}

function Install-ZipTool {
    param(
        [Parameter(Mandatory)][string]$Name,
        [Parameter(Mandatory)][hashtable]$Tool,
        [Parameter(Mandatory)][string]$Destination,
        [Parameter(Mandatory)][string]$ExecutableName
    )
    $installedExecutable = if ($Name -eq 'Java') {
        Join-Path $Destination (Join-Path 'bin' $ExecutableName)
    } else {
        Join-Path $Destination $ExecutableName
    }
    if ((Test-Path -LiteralPath $installedExecutable) -and -not $Force) { return }
    $archive = Get-VerifiedArchive -Name $Name -Tool $Tool
    $extractRoot = Join-Path $downloadRoot ("extract-$($Name.ToLowerInvariant())")
    Reset-RuntimeChild -Path $extractRoot
    Reset-RuntimeChild -Path $Destination
    Expand-Archive -LiteralPath $archive -DestinationPath $extractRoot -Force
    $executable = Get-ChildItem -LiteralPath $extractRoot -Recurse -File -Filter $ExecutableName | Select-Object -First 1
    if ($null -eq $executable) { throw "$ExecutableName was not found in $archive." }
    $sourceRoot = if ($Name -eq 'Java') { $executable.Directory.Parent.FullName } else { $executable.Directory.FullName }
    New-Item -ItemType Directory -Force -Path (Split-Path $Destination -Parent) | Out-Null
    Move-Item -LiteralPath $sourceRoot -Destination $Destination
    Reset-RuntimeChild -Path $extractRoot
    Remove-Item -LiteralPath $archive -Force
}

function Update-ExistingRuntimeEnvironment {
    param([Parameter(Mandatory)][string]$Path)

    $existing = [System.IO.File]::ReadAllText($Path)
    $updated = [regex]::Replace($existing, '(?m)^[ \t]*ADMIN_EMAILS[ \t]*=.*(?:\r?\n|$)', '')
    $updated = [regex]::Replace(
        $updated,
        '(?m)^([ \t]*SUPABASE_JDBC_BASE[ \t]*=[ \t]*)"jdbc:postgresql://aws-0-REGION\.pooler\.supabase\.com:5432/postgres\?sslmode=require"([ \t]*(?:#.*)?$)',
        '$1"jdbc:postgresql://<copy-session-pooler-host-from-dashboard>:5432/postgres?sslmode=require"$2'
    )
    if ($updated -notmatch '(?m)^[ \t]*ADMIN_BOOTSTRAP_TOKEN[ \t]*=') {
        $anchor = [regex]::Match($updated, '(?m)^[ \t]*RATE_LIMIT_REPLENISH[ \t]*=')
        $newline = if ($existing.Contains("`r`n")) { "`r`n" } else { "`n" }
        if (-not $anchor.Success) {
            throw "Cannot migrate ${Path}: RATE_LIMIT_REPLENISH setting was not found."
        }
        $bootstrapSetting = '    # Optional first-admin setup secret; leave blank unless bootstrapping once.' + $newline +
            '    ADMIN_BOOTSTRAP_TOKEN = ""' + $newline
        $updated = $updated.Insert($anchor.Index, $bootstrapSetting)
    }

    if ($updated -ne $existing) {
        [System.IO.File]::WriteAllText($Path, $updated, [System.Text.UTF8Encoding]::new($false))
        Write-Host "Updated existing runtime configuration: $Path"
    }
}

if (-not (Test-Path -LiteralPath $script:EnvFile)) {
    $template = Get-Content -Raw -LiteralPath (Join-Path $script:NoDockerRoot 'env.example.ps1')
    $bytes = New-Object byte[] 48
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    $secret = [Convert]::ToBase64String($bytes)
    $template = $template.Replace('__GENERATED_JWT_SECRET__', $secret)
    Set-Content -LiteralPath $script:EnvFile -Value $template -Encoding UTF8
    Write-Host "Created $script:EnvFile"
} else {
    Update-ExistingRuntimeEnvironment -Path $script:EnvFile
}

if (-not $SkipDownloads) {
    $lock = Import-PowerShellDataFile (Join-Path $script:NoDockerRoot 'tools.lock.psd1')
    Install-ZipTool -Name 'Java' -Tool $lock.Java -Destination (Join-Path $script:ToolsRoot 'java') -ExecutableName 'java.exe'

    $kafkaDestination = Join-Path $script:ToolsRoot 'kafka'
    if ($Force -or -not (Test-Path -LiteralPath (Join-Path $kafkaDestination 'bin\windows\kafka-server-start.bat'))) {
        $archive = Get-VerifiedArchive -Name 'Kafka' -Tool $lock.Kafka
        $extractRoot = Join-Path $downloadRoot 'extract-kafka'
        Reset-RuntimeChild -Path $extractRoot
        Reset-RuntimeChild -Path $kafkaDestination
        New-Item -ItemType Directory -Force -Path $extractRoot | Out-Null
        & tar.exe -xzf $archive -C $extractRoot
        if ($LASTEXITCODE -ne 0) { throw 'Failed to extract Kafka archive with tar.exe.' }
        $kafkaStart = Get-ChildItem -LiteralPath $extractRoot -Recurse -File -Filter 'kafka-server-start.bat' | Select-Object -First 1
        if ($null -eq $kafkaStart) { throw 'Kafka scripts were not found after extraction.' }
        Move-Item -LiteralPath $kafkaStart.Directory.Parent.Parent.FullName -Destination $kafkaDestination
        Reset-RuntimeChild -Path $extractRoot
        Remove-Item -LiteralPath $archive -Force
    }

    Install-ZipTool -Name 'Caddy' -Tool $lock.Caddy -Destination (Join-Path $script:ToolsRoot 'caddy') -ExecutableName 'caddy.exe'
    Install-ZipTool -Name 'Alloy' -Tool $lock.Alloy -Destination (Join-Path $script:ToolsRoot 'alloy') -ExecutableName 'alloy-windows-amd64.exe'
}

Write-Host ''
Write-Host 'Setup complete.'
Write-Host "1. Fill cloud credentials in $script:EnvFile"
Write-Host '2. Run config/supabase/init-schemas.sql in the Supabase SQL editor.'
Write-Host '3. Create a public Supabase Storage bucket named social-media.'
Write-Host '4. Build artifacts, then run Doctor.ps1 and Start.ps1.'
