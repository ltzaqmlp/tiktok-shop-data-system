$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot
Write-Host "[1/3] Rebuilding backend/frontend without Docker cache..."
docker compose build --no-cache app nginx
Write-Host "[2/3] Recreating application containers..."
docker compose up -d --force-recreate app nginx
Write-Host "[3/3] Current compose status:"
docker compose ps
Write-Host "Expected backend buildVersion: 2026.09.07-product-period-v3"
