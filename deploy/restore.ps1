[CmdletBinding()]
param(
  [Parameter(Mandatory=$true)][string]$BackupFile,
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
$file = (Resolve-Path -LiteralPath $BackupFile).Path
if ((Read-Host '此操作替换数据库内容。停止业务写入后输入 RESTORE 继续') -ne 'RESTORE') { return }
docker compose @composeArgs stop nginx app
$containerId = docker compose @composeArgs ps -q postgres
docker cp $file "${containerId}:/tmp/shop-restore.dump"
if ($LASTEXITCODE -ne 0) { throw 'Copy failed; application remains stopped' }
docker compose @composeArgs exec -T postgres pg_restore -U shop_app -d shop_operations --clean --if-exists --no-owner --single-transaction /tmp/shop-restore.dump
if ($LASTEXITCODE -ne 0) { throw 'Restore failed; application remains stopped' }
docker compose @composeArgs exec -T postgres rm -- /tmp/shop-restore.dump
docker compose @composeArgs start app nginx
