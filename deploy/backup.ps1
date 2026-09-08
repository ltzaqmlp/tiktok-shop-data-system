$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
New-Item -ItemType Directory -Force backup | Out-Null
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$containerFile = "/tmp/shop-backup-$stamp.dump"
docker compose exec -T postgres pg_dump -U shop_app -d shop_operations -Fc -f $containerFile
if ($LASTEXITCODE -ne 0) { throw 'pg_dump failed' }
$containerId = docker compose ps -q postgres
$destination = Join-Path $PSScriptRoot "backup/daily-$stamp.dump"
docker cp "${containerId}:$containerFile" $destination
if ($LASTEXITCODE -ne 0) { throw 'Backup copy failed' }
docker compose exec -T postgres rm -- $containerFile
if ((Get-Date).DayOfWeek -eq 'Sunday') { Copy-Item -LiteralPath $destination -Destination "backup/weekly-$stamp.dump" }
foreach ($prefix in @('daily','weekly')) {
  $keep = if ($prefix -eq 'daily') { 7 } else { 4 }
  Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'backup') -Filter "$prefix-*.dump" |
    Sort-Object Name -Descending | Select-Object -Skip $keep | ForEach-Object { Remove-Item -LiteralPath $_.FullName }
}
Write-Output "Backup saved: $destination"
