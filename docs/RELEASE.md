# Course Release Checklist

This checklist proves a reproducible local/course demo only. It does not
authorize production deployment.

## Clean candidate

- Record exact `main`, candidate and tree SHA.
- Verify `git status --short` is empty before review.
- Confirm no legacy backend, gateway, K8s or monitoring runtime reference
  remains. The API and web images are published separately with immutable
  release tags; publication is not deployment.

## API and database

```powershell
.\mvnw.cmd -q -f java-services/pom.xml verify
docker compose config
docker compose build restful-api web
docker compose up -d postgres mailpit restful-api web
curl.exe http://127.0.0.1:4010/api/v1/health/liveness
curl.exe -H "X-Health-Key: local-course-health-key" http://127.0.0.1:4010/api/v1/health/readiness
curl.exe http://127.0.0.1:4010/v3/api-docs
```

Verify a fresh PostgreSQL database, Flyway versions through the actual latest
V21 migration, deterministic seed counts, pending registration, email
verification, login, refresh/logout, forgot/reset, role isolation, registration
round/window/eligibility rules, enrollment transaction rules, grade publishing,
thesis group limits and assistant citations/locale fallback/outage behavior.
Also verify the forward-only V13-V21 registration/assistant/auth migrations,
conversation ownership, idempotency ledger, quota buckets, terminal CAS/cancel
race and knowledge revision workflow. Provider tests must use a no-network fake by default; a live
DeepSeek smoke is optional, chargeable and only allowed after a rotated runtime
key is injected explicitly. Java 25 is the release runtime authority; Java 21
is a separately recorded compatibility baseline using
`-Dcampuscore.java-baseline=true`. A local run on JDK 24/26 is recorded as
NOT_RUN for both gates. Always use `.\mvnw.cmd`/`./mvnw` so Maven 3.9.x is
enforced by the build.

Capture a real local lifecycle in Mailpit: pending register -> verification
mail (HTML and text) -> verify -> login -> refresh -> logout -> forgot/reset
mail -> reset -> login with the new password. Prove challenge values are
SHA-256 hashes in PostgreSQL, single-use, expired/invalid-safe and absent from
application logs. Do not substitute an SMTP configuration check for captured
mail evidence.

Supabase synchronization is a separate remote database gate. Apply no remote
migration until the exact project, current migration/schema drift, backup or
rollback path, and compatibility with Supabase-managed schemas have been
verified. Never upload local Mailpit messages, challenge rows, session rows,
test tokens or credentials.

For a new Supabase target, first prove the B20 schema-only baseline plus the
reviewed V21 successor against a fresh Flyway V1-V21 PostgreSQL database with
`CampusCoreSupabaseBaselinePostgresIT`. The test is local-only because it
creates sentinel schemas; never point its environment variables at a hosted
database. The hosted apply must use Flyway 11.7.2 with the exact reviewed
`classpath:db/supabase-baseline` location (and only explicitly reviewed
forward-only successor migrations),
must leave `auth`, `storage`, `realtime` and `supabase_migrations` unchanged,
and must be followed by schema/history/zero-row queries documented in
`docs/integrations/supabase-database.md`.

## Clients

```powershell
npm test --prefix frontend
npm run typecheck --prefix frontend
npm run lint --prefix frontend
npm run build --prefix frontend
npm test --prefix mobile
npm run typecheck --prefix mobile
```

Review web routes at 1440px, 768px and 390px. Review authenticated student,
lecturer and admin flows, keyboard/focus behavior, loading/empty/error/
forbidden states, console output and failed network requests. Review mobile
login, refresh, logout and core student flows on an emulator or device.

## Git and cleanup

- Run `git diff --check` and `git fsck --connectivity-only --no-progress`.
- Obtain fresh Advisor, Kongming, Wukong, exact-head reviewer and Stitch
  verdicts on the same SHA.
- Treat unavailable reviewer capability as `BLOCKED_CAPABILITY`, never PASS.
- Report source/push and production cutover separately. Local tests, Compose,
  screenshots and a provider stub do not prove production readiness.
- Remove obsolete branches only with `git branch -d` after containment proof.
- Remove only exact old local microservice image tags after runtime proof.
- Build API and frontend images with an immutable release tag and full commit
  tag. Set `JAVA_API_ORIGIN` at container runtime for the frontend proxy, run
  authenticated API/health smoke checks, push only after `docker login` is
  verified, and record the resulting Docker Hub digests. Never rely on a
  `latest`-only tag.
- Never run `docker system prune` or `docker volume prune`. The disposable
  `scripts/run-course-e2e.mjs` runner may use `down -v` only with its generated
  `campuscore-course-e2e-*` project after collision/port preflight; never use it
  against the default developer project.
