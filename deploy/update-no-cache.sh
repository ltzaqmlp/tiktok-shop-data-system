#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")"
echo "[1/3] Rebuilding backend/frontend without Docker cache..."
docker compose build --no-cache app nginx
echo "[2/3] Recreating application containers..."
docker compose up -d --force-recreate app nginx
echo "[3/3] Current compose status:"
docker compose ps
echo "Expected backend buildVersion: 2026.09.07-product-period-v3"
