#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")"
environment="${1:-prod}"
case "$environment" in test|prod) ;; *) echo 'Usage: ./update-no-cache.sh [test|prod]' >&2; exit 1 ;; esac
project="shop-$environment"
env_file=".env.$environment"
if [ ! -f "$env_file" ] && [ "$environment" = prod ] && [ -f .env ]; then env_file=.env; fi
test -f "$env_file" || { echo "Missing $env_file" >&2; exit 1; }
compose() { docker compose --project-name "$project" --env-file "$env_file" "$@"; }
echo "[1/3] Rebuilding backend/frontend without Docker cache..."
compose build --no-cache app nginx
echo "[2/3] Recreating application containers..."
compose up -d --force-recreate app nginx
echo "[3/3] Current compose status:"
compose ps
echo "Expected backend buildVersion: 2026.09.07-product-period-v3"
