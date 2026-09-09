#!/bin/sh
set -eu
cd "$(dirname "$0")"
environment="${1:-prod}"
case "$environment" in test|prod) ;; *) echo 'Usage: ./backup.sh [test|prod]' >&2; exit 1 ;; esac
project="shop-$environment"
env_file=".env.$environment"
if [ ! -f "$env_file" ] && [ "$environment" = prod ] && [ -f .env ]; then env_file=.env; fi
test -f "$env_file" || { echo "Missing $env_file" >&2; exit 1; }
compose() { docker compose --project-name "$project" --env-file "$env_file" "$@"; }
backup_dir="backup-$environment"
mkdir -p "$backup_dir"
stamp=$(date +%Y%m%d-%H%M%S)
file="$backup_dir/daily-$stamp.dump"
compose exec -T postgres pg_dump -U shop_app -d shop_operations -Fc > "$file.tmp"
test -s "$file.tmp"
mv "$file.tmp" "$file"
if [ "$(date +%u)" = 7 ]; then cp "$file" "$backup_dir/weekly-$stamp.dump"; fi
ls -1t "$backup_dir"/daily-*.dump | tail -n +8 | while IFS= read -r old; do rm -- "$old"; done
ls -1t "$backup_dir"/weekly-*.dump 2>/dev/null | tail -n +5 | while IFS= read -r old; do rm -- "$old"; done
printf 'Backup saved: %s\n' "$file"
