[CmdletBinding()]
param(
  [ValidateSet('test', 'prod')]
  [string]$Environment = 'prod'
)

$ErrorActionPreference = "Stop"
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
Write-Host "[1/3] Rebuilding backend/frontend without Docker cache..."
docker compose @composeArgs build --no-cache app nginx
if ($LASTEXITCODE -ne 0) { throw 'Docker image build failed; application containers were not changed.' }
Write-Host "[2/3] Recreating application containers..."
docker compose @composeArgs up -d --force-recreate app nginx
if ($LASTEXITCODE -ne 0) { throw 'Application container recreation failed.' }
Write-Host "[3/3] Current compose status:"
docker compose @composeArgs ps
if ($LASTEXITCODE -ne 0) { throw 'Unable to read compose status.' }
Write-Host "Expected backend buildVersion: 2026.09.07-product-period-v3"
