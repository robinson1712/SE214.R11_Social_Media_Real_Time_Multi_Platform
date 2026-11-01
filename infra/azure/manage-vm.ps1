param(
    [Parameter(Mandatory)][ValidateSet('Status','Start','Stop')][string]$Action
)
$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$az = Join-Path $root '.runtime/tools/azure-cli/bin/az.cmd'
if (-not (Test-Path -LiteralPath $az)) { throw 'Azure CLI portable is required under .runtime/tools/azure-cli.' }
$env:AZURE_CONFIG_DIR = Join-Path $root '.runtime/state/azure-cli'
$subscription = '165c7def-d5c5-4d34-8648-cdd073c4381e'
$group = 'doan1-demo-rg'
$name = 'doan1-backend'
if ($Action -eq 'Start') {
    & $az vm start --subscription $subscription -g $group -n $name --only-show-errors
    if ($LASTEXITCODE) { throw 'VM start failed.' }
    Write-Host 'VM started; Java services start sequentially. Wait for the backend to become ready.'
} elseif ($Action -eq 'Stop') {
    & $az vm deallocate --subscription $subscription -g $group -n $name --only-show-errors
    if ($LASTEXITCODE) { throw 'VM deallocation failed.' }
    Write-Host 'VM deallocated. Compute billing stops; disk and public IP may still consume credit.'
}
& $az vm show --subscription $subscription -g $group -n $name -d `
    --query '{name:name,state:powerState,size:hardwareProfile.vmSize,host:fqdns,ip:publicIps}' -o json --only-show-errors
if ($LASTEXITCODE) { throw 'VM status query failed.' }
