param(
    [Parameter(Mandatory)][string[]]$Name
)

. (Join-Path $PSScriptRoot 'Common.ps1')
$cloud = Import-CloudEnvironment
$services = @($script:ServiceDefinitions | Where-Object { $_.Name -in $Name })
if ($services.Count -ne @($Name | Select-Object -Unique).Count) {
    throw 'Specify existing Java service names.'
}
foreach ($service in $services) {
    if ($service.Store -eq 'mongo') {
        $uri = [string]$cloud[$service.MongoKey]
        if ([string]::IsNullOrWhiteSpace($uri) -or $uri -match '<db_password>|YOUR.PASSWORD') {
            throw "Set $($service.MongoKey) in .runtime/env.ps1 before restarting."
        }
    }
}
$java = Get-JavaExecutable
$javaOptions = @(([string]$cloud.JAVA_OPTS -split '\s+') | Where-Object { $_ })
foreach ($service in $services) {
    $records = @(Read-ProcessState | Where-Object Name -eq $service.Name)
    foreach ($record in $records) {
        if (Test-ProcessRecordAlive $record) {
            Write-Host "Stopping $($service.Name)..."
            Stop-Process -Id ([int]$record.Pid) -Force
            Wait-Process -Id ([int]$record.Pid) -Timeout 15 -ErrorAction SilentlyContinue
        }
    }
    Write-ProcessState -Processes @(Read-ProcessState | Where-Object {
        $_.Name -ne $service.Name -and (Test-ProcessRecordAlive $_)
    })
    Write-Host "Starting $($service.Name)..."
    Start-ManagedProcess -Name $service.Name -Kind 'spring' -FilePath $java `
        -ArgumentList @($javaOptions + @('-jar', (Quote-NativeArgument (Get-ServiceJar $service)))) `
        -Environment (Get-ServiceProcessEnvironment -Service $service -Cloud $cloud) | Out-Null
    if (-not (Wait-ServiceHealth -Name $service.Name -Port $service.Port -TimeoutSeconds 180)) {
        throw "$($service.Name) failed to restart; inspect its runtime logs."
    }
    Write-Host "$($service.Name) is healthy."
}
