param([Parameter(Mandatory)][string]$Output)
$ErrorActionPreference='Stop'
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$CloudEnvironment=$null
. (Join-Path $root 'infra/no-docker/env.example.ps1')
$defaults=$CloudEnvironment.Clone()
$CloudEnvironment=$null
. (Join-Path $root '.runtime/env.ps1')
foreach($key in $CloudEnvironment.Keys) {$defaults[$key]=$CloudEnvironment[$key]}
[IO.File]::WriteAllText([IO.Path]::GetFullPath($Output),($defaults | ConvertTo-Json -Depth 4),[Text.UTF8Encoding]::new($false))
