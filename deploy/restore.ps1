param([Parameter(Mandatory=$true)][string]$BackupFile)
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
$file = (Resolve-Path -LiteralPath $BackupFile).Path
if ((Read-Host '此操作替换数据库内容。停止业务写入后输入 RESTORE 继续') -ne 'RESTORE') { return }
docker compose stop nginx app
$containerId = docker compose ps -q postgres
docker cp $file "${containerId}:/tmp/shop-restore.dump"
if ($LASTEXITCODE -ne 0) { throw 'Copy failed; application remains stopped' }
docker compose exec -T postgres pg_restore -U shop_app -d shop_operations --clean --if-exists --no-owner --single-transaction /tmp/shop-restore.dump
if ($LASTEXITCODE -ne 0) { throw 'Restore failed; application remains stopped' }
docker compose exec -T postgres rm -- /tmp/shop-restore.dump
docker compose start app nginx
