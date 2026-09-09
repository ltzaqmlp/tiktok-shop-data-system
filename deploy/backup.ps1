[CmdletBinding()]
param(
  [ValidateSet('test', 'prod')]
  [string]$Environment = 'prod'
)

$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
$projectName = "shop-$Environment"
$envFile = Join-Path $PSScriptRoot ".env.$Environment"
if (!(Test-Path -LiteralPath $envFile)) {
  if ($Environment -eq 'prod' -and (Test-Path -LiteralPath (Join-Path $PSScriptRoot '.env'))) {
    $envFile = Join-Path $PSScriptRoot '.env'
  } else {
    throw "找不到环境配置文件：$envFile"
  }
}
$composeArgs = @('--project-name', $projectName, '--env-file', $envFile)
$backupDir = Join-Path $PSScriptRoot "backup-$Environment"
New-Item -ItemType Directory -Force $backupDir | Out-Null
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$containerFile = "/tmp/shop-$Environment-backup-$stamp.dump"
docker compose @composeArgs exec -T postgres pg_dump -U shop_app -d shop_operations -Fc -f $containerFile
if ($LASTEXITCODE -ne 0) { throw 'pg_dump failed' }
$containerId = docker compose @composeArgs ps -q postgres
$destination = Join-Path $backupDir "daily-$stamp.dump"
docker cp "${containerId}:$containerFile" $destination
if ($LASTEXITCODE -ne 0) { throw 'Backup copy failed' }
docker compose @composeArgs exec -T postgres rm -- $containerFile
if ((Get-Date).DayOfWeek -eq 'Sunday') { Copy-Item -LiteralPath $destination -Destination (Join-Path $backupDir "weekly-$stamp.dump") }
foreach ($prefix in @('daily','weekly')) {
  $keep = if ($prefix -eq 'daily') { 7 } else { 4 }
  Get-ChildItem -LiteralPath $backupDir -Filter "$prefix-*.dump" |
    Sort-Object Name -Descending | Select-Object -Skip $keep | ForEach-Object { Remove-Item -LiteralPath $_.FullName }
}
Write-Output "Backup saved: $destination"
