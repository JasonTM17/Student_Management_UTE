#!/usr/bin/env bash
set -euo pipefail

archive="${1:?Usage: restore-postgres.sh <custom-archive.dump> <isolated-db-name>}"
target_db="${2:?Usage: restore-postgres.sh <custom-archive.dump> <isolated-db-name>}"
project="${COMPOSE_PROJECT_NAME:-campuscore-production}"
compose_file="${COMPOSE_FILE:-docker-compose.prod.yml}"
db_user="${POSTGRES_USER:-campuscore}"
source_db="${POSTGRES_DB:-campuscore_restful}"

[[ -f "$archive" ]] || { echo 'Backup archive does not exist.' >&2; exit 2; }
[[ "$target_db" != "$source_db" ]] || {
  echo 'Restore requires an isolated database name; production overwrite is refused.' >&2
  exit 2
}
[[ "$target_db" =~ ^[a-zA-Z_][a-zA-Z0-9_]*$ ]] || { echo 'Invalid isolated database name.' >&2; exit 2; }

"$(dirname "$0")/verify-backup.sh" "$archive"
docker compose -p "$project" -f "$compose_file" exec -T postgres \
  psql --username "$db_user" --dbname "$source_db" --command "CREATE DATABASE \"$target_db\""
docker compose -p "$project" -f "$compose_file" exec -T postgres \
  pg_restore --exit-on-error --no-owner --no-privileges --username "$db_user" --dbname "$target_db" < "$archive"
printf 'Restored %s into isolated database %s. Run Flyway/readiness checks before considering recovery complete.\n' "$archive" "$target_db"
