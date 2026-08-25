# Restore rehearsal boundary

Status: `HOLD/NOT_PROVEN` for hosted Supabase restoreability.

The project preflight reported `pitr_enabled=false`, no downloadable backup
on the current plan, and no restore point available. A disposable vanilla
PostgreSQL 17.6 container was used to test whether the retained schema-only
logical dump could be replayed without a hosted environment. The replay
stopped before application verification because the dump includes the
Supabase-managed `supabase_vault` extension, which is not installed in the
vanilla PostgreSQL image. The container was removed after the attempt.

This is an environment capability boundary, not evidence that the hosted
backup is corrupt. The exact dump remains outside the repository with the
SHA-256 recorded in `manifest.json`. A real rollback rehearsal requires a
plan-supported Supabase backup/PITR restore target (or an equivalent Supabase
branch) and must be run before any application traffic cutover.
