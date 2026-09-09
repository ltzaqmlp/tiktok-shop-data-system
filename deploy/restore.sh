#!/bin/sh
set -eu
cd "$(dirname "$0")"
test "$#" = 1 -o "$#" = 2 || { echo 'Usage: ./restore.sh backup/file.dump [test|prod]'; exit 1; }
test -f "$1"
backup_file="$1"
environment="${2:-prod}"
case "$environment" in test|prod) ;; *) echo 'Usage: ./restore.sh backup/file.dump [test|prod]' >&2; exit 1 ;; esac
project="shop-$environment"
env_file=".env.$environment"
if [ ! -f "$env_file" ] && [ "$environment" = prod ] && [ -f .env ]; then env_file=.env; fi
test -f "$env_file" || { echo "Missing $env_file" >&2; exit 1; }
compose() { docker compose --project-name "$project" --env-file "$env_file" "$@"; }
echo 'This replaces the database. Stop application writes and type RESTORE to continue:'
read -r answer
test "$answer" = RESTORE || exit 1
compose stop nginx app
compose exec -T postgres pg_restore -U shop_app -d shop_operations --clean --if-exists --no-owner --single-transaction < "$backup_file"
compose start app nginx
