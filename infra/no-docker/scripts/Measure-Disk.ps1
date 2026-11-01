. (Join-Path $PSScriptRoot 'Common.ps1')

function Get-DirectoryBytes {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path)) { return 0L }
    $files = @(Get-ChildItem -LiteralPath $Path -File -Recurse -ErrorAction SilentlyContinue)
    if ($files.Count -eq 0) { return 0L }
    $measurement = $files | Measure-Object -Property Length -Sum
    return [long]$measurement.Sum
}

$categories = @(
    @{ Name = 'Source (without .git)'; Path = $script:ProjectRoot },
    @{ Name = 'Runtime tools'; Path = (Join-Path $script:RuntimeRoot 'tools') },
    @{ Name = 'Kafka data'; Path = (Join-Path $script:DataRoot 'kafka') },
    @{ Name = 'Alloy data'; Path = (Join-Path $script:DataRoot 'alloy') },
    @{ Name = 'Runtime logs'; Path = $script:LogRoot },
    @{ Name = 'Build output'; Path = (Join-Path $script:ProjectRoot 'dist') }
)

$rows = foreach ($category in $categories) {
    $bytes = Get-DirectoryBytes $category.Path
    if ($category.Name -eq 'Source (without .git)') {
        $runtimeBytes = Get-DirectoryBytes $script:RuntimeRoot
        $distBytes = Get-DirectoryBytes (Join-Path $script:ProjectRoot 'dist')
        $gitBytes = Get-DirectoryBytes (Join-Path $script:ProjectRoot '.git')
        $bytes = [math]::Max(0, $bytes - $runtimeBytes - $distBytes - $gitBytes)
    }
    [pscustomobject]@{
        Category = $category.Name
        GB = [math]::Round($bytes / 1GB, 3)
        MB = [math]::Round($bytes / 1MB, 1)
        Path = $category.Path
    }
}
$rows | Format-Table -AutoSize
$runtimeTotal = (Get-DirectoryBytes $script:RuntimeRoot) + (Get-DirectoryBytes (Join-Path $script:ProjectRoot 'apps')) +
    (Get-DirectoryBytes (Join-Path $script:ProjectRoot 'web'))
Write-Host "Runtime footprint: $([math]::Round($runtimeTotal / 1GB, 3)) GB (target <= 10 GB)"
