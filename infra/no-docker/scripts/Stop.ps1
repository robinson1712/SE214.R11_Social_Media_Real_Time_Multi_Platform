. (Join-Path $PSScriptRoot 'Common.ps1')

$records = @(Read-ProcessState)
if ($records.Count -eq 0) {
    Write-Host 'Nothing to stop.'
    exit 0
}

for ($index = $records.Count - 1; $index -ge 0; $index--) {
    $record = $records[$index]
    if (-not (Test-ProcessRecordAlive $record)) {
        Write-Host "Skipping stale PID $($record.Pid) for $($record.Name)."
        continue
    }
    Write-Host "Stopping $($record.Name) (PID $($record.Pid))..."
    try {
        & taskkill.exe /PID ([string]$record.Pid) /T /F 2>$null | Out-Null
    }
    catch {
        Write-Warning "Could not stop $($record.Name) immediately: $($_.Exception.Message)"
    }
}

Start-Sleep -Milliseconds 500
$survivors = @($records | Where-Object { Test-ProcessRecordAlive $_ })
Write-ProcessState -Processes $survivors
if ($survivors.Count -gt 0) {
    Write-Warning ('Could not stop: ' + (($survivors | ForEach-Object Name) -join ', '))
    exit 1
}
Write-Host 'All managed processes stopped. Cloud data and Kafka data were preserved.'
