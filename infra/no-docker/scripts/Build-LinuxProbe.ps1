. (Join-Path $PSScriptRoot 'Common.ps1')
$cloud = Import-CloudEnvironment
$root = Join-Path $script:RuntimeRoot 'packages/cloud-check'
New-Item -ItemType Directory -Force -Path $root | Out-Null
$values = @{CHECK_SUPABASE_ONLY='false'}
$sql = @($script:ServiceDefinitions | Where-Object Store -eq 'postgres')
$values.CHECK_SQL_COUNT = [string]$sql.Count
for ($i=0; $i -lt $sql.Count; $i++) {
    $service = $sql[$i]
    $settings = Get-ServiceProcessEnvironment -Service $service -Cloud $cloud
    $prefix = "CHECK_SQL_${i}_"
    $values[$prefix+'NAME'] = $service.Name
    $values[$prefix+'URL'] = [string]$cloud.SUPABASE_JDBC_BASE
    $values[$prefix+'USER'] = $settings.CLOUD_POSTGRES_USERNAME
    $values[$prefix+'PASSWORD'] = $settings.CLOUD_POSTGRES_PASSWORD
    $values[$prefix+'SCHEMA'] = $service.Schema
}
$mongo = @($script:ServiceDefinitions | Where-Object Store -eq 'mongo')
$values.CHECK_MONGO_COUNT = [string]$mongo.Count
for ($i=0; $i -lt $mongo.Count; $i++) {
    $prefix = "CHECK_MONGO_${i}_"
    $values[$prefix+'NAME'] = $mongo[$i].Name
    $values[$prefix+'URI'] = [string]$cloud[$mongo[$i].MongoKey]
    $values[$prefix+'DB'] = $mongo[$i].Name.Replace('-service','_db')
}
$mapping = @{
    CHECK_REDIS_HOST='REDIS_HOST';CHECK_REDIS_PORT='REDIS_PORT';CHECK_REDIS_USER='REDIS_USERNAME';
    CHECK_REDIS_PASSWORD='REDIS_PASSWORD';CHECK_REDIS_TLS='REDIS_SSL_ENABLED';
    CHECK_S3_ENDPOINT='STORAGE_S3_ENDPOINT';CHECK_S3_REGION='STORAGE_REGION';
    CHECK_S3_BUCKET='STORAGE_BUCKET';CHECK_S3_KEY='STORAGE_ACCESS_KEY';CHECK_S3_SECRET='STORAGE_SECRET_KEY'
}
foreach ($key in $mapping.Keys) { $values[$key] = [string]$cloud[$mapping[$key]] }
$utf8 = [Text.UTF8Encoding]::new($false)
$lines = foreach ($key in ($values.Keys | Sort-Object)) {
    $value = [string]$values[$key]
    if ($value -match '[\r\n]') { throw "Multiline value unsupported: $key" }
    $escaped = $value.Replace('\','\\').Replace('"','\"')
    "$key=`"$escaped`""
}
[IO.File]::WriteAllText((Join-Path $root 'check.env'), (($lines -join "`n")+"`n"), $utf8)
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'CloudConnectionCheck.java') -Destination $root -Force
[IO.File]::WriteAllText((Join-Path $root 'logback.xml'), '<configuration><root level="OFF"/></configuration>', $utf8)
$unit = @'
[Unit]
Description=DoAn cloud dependency verification
[Service]
Type=oneshot
User=doan
Group=doan
EnvironmentFile=/opt/doan/check/check.env
ExecStart=/usr/bin/java -Dlogback.configurationFile=/opt/doan/check/logback.xml -cp /opt/doan/check:/opt/doan/check/lib/* CloudConnectionCheck
StandardOutput=append:/opt/doan/logs/cloud-check.log
StandardError=null
TimeoutStartSec=120

'@
[IO.File]::WriteAllText((Join-Path $root 'doan-cloud-check.service'), ($unit -replace "`r`n","`n"), $utf8)
Write-Host "Private cloud probe prepared: $root"
