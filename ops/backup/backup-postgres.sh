#!/usr/bin/env bash
set -euo pipefail

project="${COMPOSE_PROJECT_NAME:-campuscore-production}"
compose_file="${COMPOSE_FILE:-docker-compose.prod.yml}"
backup_dir="${BACKUP_DIR:-ops/backups}"
retention_days="${BACKUP_RETENTION_DAYS:-14}"
db_user="${POSTGRES_USER:-campuscore}"
db_name="${POSTGRES_DB:-campuscore_restful}"

[[ "$backup_dir" != "/" && "$backup_dir" != "." ]] || { echo 'Refusing an unsafe backup directory.' >&2; exit 2; }
mkdir -p "$backup_dir"
timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
archive="$backup_dir/campuscore-${timestamp}.dump"

docker compose -p "$project" -f "$compose_file" exec -T postgres \
  pg_dump --format=custom --no-owner --no-privileges --username "$db_user" --dbname "$db_name" > "$archive"

if command -v sha256sum >/dev/null 2>&1; then
  sha256sum "$archive" > "$archive.sha256"
else
  shasum -a 256 "$archive" > "$archive.sha256"
fi

find "$backup_dir" -maxdepth 1 -type f -name 'campuscore-*.dump*' -mtime "+$retention_days" -print -delete
printf 'Backup written: %s\nChecksum: %s\n' "$archive" "$archive.sha256"
