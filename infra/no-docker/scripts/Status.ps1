. (Join-Path $PSScriptRoot 'Common.ps1')

$records = @(Read-ProcessState)
if ($records.Count -eq 0) {
    Write-Host 'No managed no-Docker processes are recorded.'
    exit 0
}

$status = foreach ($record in $records) {
    [pscustomobject]@{
        Name = $record.Name
        Kind = $record.Kind
        Pid = $record.Pid
        Status = if (Test-ProcessRecordAlive $record) { 'RUNNING' } else { 'STOPPED' }
        StartedAtUtc = $record.StartedAtUtc
    }
}
$status | Format-Table -AutoSize

$runningServices = @($script:ServiceDefinitions | Where-Object {
    $service = $_
    $records | Where-Object { $_.Name -eq $service.Name -and (Test-ProcessRecordAlive $_) }
})
Write-Host "`nSpring services: $($runningServices.Count)/$($script:ServiceDefinitions.Count) running"
