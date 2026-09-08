#!/bin/sh
set -eu
cd "$(dirname "$0")"
mkdir -p backup
stamp=$(date +%Y%m%d-%H%M%S)
file="backup/daily-$stamp.dump"
docker compose exec -T postgres pg_dump -U shop_app -d shop_operations -Fc > "$file.tmp"
test -s "$file.tmp"
mv "$file.tmp" "$file"
if [ "$(date +%u)" = 7 ]; then cp "$file" "backup/weekly-$stamp.dump"; fi
ls -1t backup/daily-*.dump | tail -n +8 | while IFS= read -r old; do rm -- "$old"; done
ls -1t backup/weekly-*.dump 2>/dev/null | tail -n +5 | while IFS= read -r old; do rm -- "$old"; done
printf 'Backup saved: %s\n' "$file"
