# CampusCore production package runbook

This package is deployment-ready only after the external VPS, DNS, firewall,
TLS and secret-provisioning gates below are completed. A green local/CI check
does not mean that CampusCore is running in production.

## Configuration boundaries and unresolved self-hosting gates

This VPS Compose package is separate from the Render/Vercel configuration in
[render.yaml](../render.yaml) and [deployment notes](deployment.md). The Render
blueprint runs one API with Flyway and database report storage; local Compose
uses a RAG sidecar, SQL authority and an assistant-role provisioner. Production
Compose instead uses Supabase knowledge authority and has no role provisioner.
Do not treat its startup commands as a proven fresh-host procedure until these
owner/operator gates are resolved:

- Provision and verify the restricted assistant login before the RAG/API
  readiness checks; V84 creates it NOLOGIN and production Compose does not
  include the local provisioner. A secret file alone does not create the role.
- Verify required outbound connectivity: the RAG/API services are attached only
  to internal `app`/`data` networks, yet Supabase, DeepSeek and SMTP need egress.
  This audit has not validated provider access in that topology.
- Verify mounted secrets are readable by the non-root Java runtime while
  remaining private; host ownership plus mode `0600` alone is not proof.
- Explicitly set `CAMPUSCORE_IMAGE_TAG` to a reviewed full 40-character SHA.
  Production Compose defaults to `latest`; unlike the GHCR smoke overlay, it
  does not reject an unset tag. Do not rely on that fallback for a release.

## Preflight

1. Install Docker Engine/Compose on the VPS and allow inbound TCP `80` and
   `443` only. Keep PostgreSQL, REST, RAG and web ports private.
2. Point the domain in `.env.production` at the VPS and wait for DNS
   propagation. Set `ACME_EMAIL` and a real domain; placeholders must fail.
3. Pull the exact reviewed full SHA in `CAMPUSCORE_IMAGE_TAG` and verify all
   four registry digests match the release manifest.
4. Provision the named files in [ops/secrets](../ops/secrets/README.md) with
   mode `0600` and verified container readability; rotate any key ever pasted into
   chat or committed history. Keep DeepSeek disabled until a newly rotated key
   has been loaded and a live smoke is explicitly approved.
5. Generate the signing secrets with `openssl rand -base64 48` — one value each
   for `ops/secrets/jwt_secret` and `ops/secrets/jwt_refresh_secret`. They must
   differ from each other and from every value committed to this repository.
   `docker-compose.prod.yml` sets `SECURITY_JWT_REJECT_KNOWN_DEFAULTS=true`, so
   the API aborts startup when it detects a secret published here.
6. Confirm a recent PostgreSQL backup and test its checksum before startup.

## Start and smoke

Run only after the gates above pass. Set `CAMPUSCORE_DOMAIN` in the shell too:
Compose `--env-file` does not export variables for the subsequent `curl` calls.

```sh
docker compose --env-file .env.production -f docker-compose.prod.yml pull
docker compose --env-file .env.production -f docker-compose.prod.yml up -d --wait
curl --fail --silent --show-error https://$CAMPUSCORE_DOMAIN/health
curl --fail --silent --show-error https://$CAMPUSCORE_DOMAIN/api/v1/health/liveness
curl --fail --silent --show-error -H "X-Health-Key: $(cat ops/secrets/readiness_key)" \
  https://$CAMPUSCORE_DOMAIN/api/v1/health/readiness
```

Then exercise login, one representative student/lecturer/admin route, campus
assistant JSON, assistant SSE event order, citation rendering and logout from
an external browser. Verify that `/internal/rag/*`, PostgreSQL and service
ports are not reachable from the public interface.

## Demo-account and rate-limit switches

`DemoAccountGate` re-applies demo account state on every boot from
`DEMO_ACCOUNTS_ENABLED` (ACTIVE when `true`, LOCKED when `false`). The public
Render blueprint explicitly configures `DEMO_ACCOUNTS_ENABLED=true` (owner
decision) so visitors can use the published runbook credentials —
this includes a demo admin login; see the risk note in `render.yaml` and the
guidance in [DEMO_RUNBOOK.md](DEMO_RUNBOOK.md). Configuration does not prove
the current live environment has those settings.

- `DEMO_ACCOUNTS_ROLES` (optional, comma-separated roles such as `STUDENT`):
  when set, only demo accounts holding one of these roles stay ACTIVE and the
  rest of the demo list is LOCKED. Empty/unset keeps the historical behavior
  (all demo accounts follow the enabled switch). Accounts with no role row are
  LOCKED.
- `RATE_LIMIT_TRUST_PROXY_HEADERS=true`: the API takes the rightmost
  `X-Forwarded-For` element as the client IP — set this when a proxy (Render,
  Vercel, Nginx) appends the real client address. Without it the rate limiter
  uses the peer address.
- `RATE_LIMIT_TRUST_REAL_IP=true`: additionally trust an `X-Real-IP` header
  ahead of `X-Forwarded-For`. Only enable it for proxies that overwrite (not
  append) that header, otherwise clients can spoof their bucket.
- Dedicated buckets to know when reading logs: `/api/v1/mail/**` 5/hour per
  user, `/api/v1/users/*/password-reset` 3/hour per user, JSON body caps of
  1 MB global / 64 KB assistant / 4 KB on credential routes (login, refresh,
  change-password, logout).
- Mutating-request defaults: login 15/minute (configurable), refresh 30/minute,
  thesis 20/minute, and enrollment 15/minute. See the [rate-limit policy](../java-services/restful-api/src/main/java/io/campuscore/restfulapi/security/ratelimit/filter/RateLimitFilter.java)
  and [configuration defaults](../java-services/restful-api/src/main/resources/application.yml).

## Render + Neon operating notes

The deployment history records Render (REST API) + Neon Free Singapore
(PostgreSQL) + Vercel (web). The former Supabase database is a historical
pre-cutover snapshot; the heartbeat workflow calls the source retired. Its
availability is unverified, and it is not a synchronized standby. Recorded
cutover evidence, historical quota limits and the untested
reverse-migration gate are recorded in [deployment.md](deployment.md#cutover-database-supabase--neon-free-singapore--15102026-utc).
Adapted preflight for data-touching migrations:

V147 adds the grade-appeal workflow and starts a one-time grace window for
grades already `PUBLISHED` or `APPEALED`: rows whose `gradePublishedAt` is
NULL receive the V147 migration execution time. Set `GRADE_APPEAL_WINDOW_DAYS`
on the backend (default `7`) before rollout. Tell students that grades
published before this feature receive a fresh window beginning at V147 release.
The backfill updates only NULL timestamps, so a retry does not extend a window
that has already started. V150 also backfills remaining NULL timestamps on
official non-W grades; verify its impact during upgrades too. New publication
and republishing use the transaction's
publication timestamp; unpublishing clears it.

1. **Snapshot before apply.** When a pushed migration contains `INSERT`,
   `UPDATE`, `DELETE`, or trigger `DISABLE` blocks (seed/repair files, not pure
   DDL), take a backup of the active Neon production database first, using
   a consistent `pg_dump` of the affected schemas and a checksum manifest.
   Do not treat the frozen Supabase snapshot as a backup of new Neon writes. The
   reconciliation classes — `V108` (seat counts), `V111`/`V112` (demo dataset
   parity), `V120`–`V125` (department-head role, credential revision, institution
   naming, demo role password/session reconciliation) — mutate live rows and were
   verified locally before push; keep that discipline.
2. **Verify Flyway outcome, not just deploy status.** A `live` deploy means
   the container booted; confirm the migration landed and succeeded:

   ```sql
   SELECT version, description, success
   FROM thesis.flyway_schema_history
   ORDER BY installed_rank DESC LIMIT 5;
   ```

   `success = t` is required. A failed row needs a repair forward-migration,
   never an edit of the applied file (CI enforces immutability).
3. **Watch scheduled jobs after fixes.** The assistant catalog-sync job
   (`PREREQUISITE_MAP_SYNC`) defaults to daily at `04:12` in the server
   timezone via Spring `@Scheduled` (no fixed `zone` is declared). There is no
   HTTP trigger, so scheduler behavior must be observed on
   the next real tick. Failure signature in Render logs:
   `SQLState 42501` means permission denied; a `bad SQL grammar` wrapper alone
   does not identify that cause. Inspect the SQLState without logging secrets.
   After the daily run, check prerequisite-map revisions when drift exists:

   ```sql
   SELECT id, slug, version, state, published_at
   FROM assistant.knowledge_document_revision
   WHERE slug IN ('catalog-prerequisite-map-vi', 'catalog-prerequisite-map-en')
   ORDER BY published_at DESC LIMIT 5;
   ```

   A no-drift run legitimately creates no new revision; assessing a
   healthy no-drift run requires confirming the scheduler tick and matching
   content; absence of an error alone does not prove execution. Current job
   failures log `PREREQUISITE_MAP_SYNC failed` at WARN.
4. **Post-deploy smoke.** `node scripts/prod-smoke.mjs` does not mutate academic
   records by default, but logins and the assistant check write sessions, last-login
   timestamps and chat history. It runs a
   fixture-specific check (demo logins, three-to-four-member thesis roster, curriculum
   plan, assistant answer, admin user search); no runtime bound is guaranteed.
   Set `BASE_URL` explicitly to the authorized target before running it after a
   Render/Vercel deploy and after migration-affecting pushes.

## Rollback

Stop Caddy traffic, set the previous verified full SHA, pull and restart the
same four images, then repeat the smoke. Never retag or pull `latest`. If a
database migration is involved, restore an isolated archive first and obtain a
recovery decision; do not run `docker compose down -v`.

## Provider and data boundaries

Supabase service-role and DeepSeek keys are server-only runtime files. The
browser must not receive either service key or raw provider errors. Assistant
responses do carry a model field; personal academic data may be returned only
within the caller's authorized scope. If synthesis fails with matching campus
documents available, the grounded
path can return a cited lexical answer with degraded status. No-match,
authentication, DB/RLS and general-answer paths have different outcomes; see
the [assistant contract](integrations/deepseek-assistant.md).

## Render cold-start mitigation

The GitHub Actions [keep-alive workflow](../.github/workflows/keepalive-heartbeat.yml)
is scheduled every 10 minutes, but the schedule is not reliable enough to
guarantee a free Render service stays warm. The optional Supabase `pg_cron`
ping also depends on extensions and is not an uptime guarantee. For lower
cold-start frequency, use an independently operated external pinger against
`GET /api/v1/health/liveness` every 5 minutes.
Confirm that pinger in its own operator console before claiming it is active;
this repository does not establish its configuration. The frontend still
retries idempotent GETs once after gateway 502/503/504 responses to absorb an
individual cold start. A paid Render instance removes the sleep interval and
remains a cost decision.

## Restoring or moving the production database

Roles, grants and RLS policies are not carried by a data-only restore, and
`flyway_schema_history` will still say every migration is applied. After a data-only
restore into a new Supabase project (a separate target from the recorded Neon
deployment), verify target-specific role/grant/policy setup:

1. Point `SPRING_DATASOURCE_*` **and** `ASSISTANT_DB_USERNAME`
   (`campuscore_assistant_runtime.<project-ref>`) at the new project ref. A stale
   ref on either makes the pooler answer `tenant/user ... not found`.
2. Check `SELECT rolname FROM pg_roles WHERE rolname = 'campuscore_assistant_runtime'`
   and inspect `pg_policies` for the assistant schema against the current
   migration chain and runtime verifier. Missing role/policy state requires an
   isolated restore or reviewed repair with backup; do not blindly replay
   selected applied migrations or rely on a historical policy count. Provision
   the restricted login and grants through the authorized operator procedure,
   then update `ASSISTANT_DB_PASSWORD` and verify the actual pooler connection.
3. Deploy, then run `node scripts/prod-smoke.mjs`. A broken assistant login now
   degrades chat to 503 instead of stopping the backend, but that is still an
   outage for the assistant: the smoke script reports it.

## Break-glass super administrator

V109 seeds the `SUPER_ADMIN` role, but no migration seeds a super-admin account.
Only a super administrator may reset the
password of, disable or delete an `ADMIN`, `TRUONG_KHOA` or custom-role account
(plain administrators are refused with `ROLE_ESCALATION`). Without one, a
forgotten administrator or dean password can only be recovered with direct
database access.

- A previous operator record names `superadmin@campuscore.edu` with role
  `SUPER_ADMIN` and `mustChangePassword = TRUE`; its current existence and
  credential custody cannot be established from seed source. Verify the private
  account inventory before relying on it. Keep initial credentials outside Git.
- First login: the owner uses the administrative deep link (the public login
  currently shows student/lecturer/dean tabs), rotates the
  password at once, then deletes the initial credential file. Enable two-factor
  sign-in for this account.
- Use it only for recovery (reset an administrator or dean password, unlock or
  re-enable such an account). Day-to-day work stays on the plain administrator.
- Re-creating it (idempotent insert, run with the owner's authorisation): insert
  one `campuscore_auth."User"` row and one `"UserRole"` row for the `SUPER_ADMIN`
  role, hashing the password with `extensions.crypt(<pw>, extensions.gen_salt('bf', 10))`
  on Supabase (Spring's `BCryptPasswordEncoder` accepts the `$2a$10$` result).
- Every administrator action it performs is written to
  `campuscore_audit."AdminAudit"` like any other administrator.
