# Backup, restore, and rollback

The production database backup is a PostgreSQL custom archive plus a SHA-256
sidecar. Run it from the repository checkout on the VPS:

```sh
BACKUP_RETENTION_DAYS=14 ./ops/backup/backup-postgres.sh
./ops/backup/verify-backup.sh ops/backups/campuscore-<timestamp>.dump
./ops/backup/restore-postgres.sh ops/backups/campuscore-<timestamp>.dump campuscore_restore_<timestamp>
```

Restore always requires a database name different from `POSTGRES_DB`; the
script refuses an in-place overwrite. After an isolated restore, verify the
latest successful Flyway version, readiness, authentication, representative
JSON/SSE assistant calls, and row counts before approving a recovery.

Rollback is an application/image operation: stop the edge, set
`CAMPUSCORE_IMAGE_TAG` to the previous verified full commit SHA, pull the four
matching digests, run `docker compose -f docker-compose.prod.yml up -d --wait`,
and repeat the smoke checks. Never use `latest`, and never run `down -v` on a
production volume. If a migration is irreversible, restore into an isolated
database first and obtain an explicit recovery decision before changing the
live volume.
