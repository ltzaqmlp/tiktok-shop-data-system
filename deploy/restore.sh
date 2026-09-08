#!/bin/sh
set -eu
cd "$(dirname "$0")"
test "$#" = 1 || { echo 'Usage: ./restore.sh backup/file.dump'; exit 1; }
test -f "$1"
echo 'This replaces the database. Stop application writes and type RESTORE to continue:'
read -r answer
test "$answer" = RESTORE || exit 1
docker compose stop nginx app
docker compose exec -T postgres pg_restore -U shop_app -d shop_operations --clean --if-exists --no-owner --single-transaction < "$1"
docker compose start app nginx
