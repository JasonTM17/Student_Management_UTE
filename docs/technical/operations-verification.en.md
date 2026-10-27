# 5. Mobile, data, operations and verification

[Tiếng Việt](operations-verification.vi.md) · [Index](README.en.md) · [Current media](../media-showcase.en.md)

## T35. Expo mobile shares the API and separates preview from authentication

The mobile app uses Expo, React Native and TypeScript. `src/api/client.ts` owns one base URL and in-memory Bearer access/refresh tokens. A 401 triggers one refresh/retry, excluding login/refresh/logout to avoid loops. Refresh/logout send the refresh token in the body according to Java's contract; native devices cannot assume browser session cookies.

The navigator distinguishes `signedOut`, `preview` and `authenticated`, with role-based routes and direct-transition checks. Preview role switching is a design aid, not authority. Password-change-required accounts need a distinct state before business screens. Recent mobile work also aligns active-enrollment counting, section schedules, notification date/time and profile validation.

The native assistant uses buffered JSON. Missing backend contracts must produce explicit unavailable states. An atlas, typecheck or unit suite does not prove real-device execution, push delivery, offline synchronization or durable secure token storage; these need separate design/acceptance.

**Owners:** [mobile guide](../../mobile/README.md), [API client](../../mobile/src/api/client.ts), [navigator](../../mobile/src/navigation/MobileNavigator.tsx), [manifest](../../mobile/package.json).

## T36. Flyway and domain-owned data

PostgreSQL schemas separate domain data ownership. Flyway applies versioned migrations; history/checksums detect changes to applied files. Entity mapping validates schema rather than replacing migrations. New environments must migrate before serving traffic. Read seed-data changes separately from schema changes.

In current Compose, the RAG sidecar runs Flyway and REST API migration is disabled, avoiding implicit competing boot owners. The assistant-role provisioner runs first so the restricted runtime role can log in while preserving RLS. The database image packages PostgreSQL, not an alternative application schema.

Foreign keys, unique/partial indexes, checks/triggers and transactions jointly preserve invariants. H2 speeds local tests but has SQL branches differing from PostgreSQL; mappings, RLS, `ON CONFLICT` and races require actual PostgreSQL evidence. Do not edit released migrations to silence a local failure; use a new migration or an authorized repair process with appropriate backup.

**Owners:** [migration policy](../../java-services/restful-api/src/main/resources/db/migration/README.md), [migration files](../../java-services/restful-api/src/main/resources/db/migration/), [database image](../../database/Dockerfile), [role provisioner](../../scripts/provision-assistant-runtime.mjs), [Compose](../../docker-compose.yml).

## T37. Storage providers and complete backups

Report storage exposes an interface with `local`, `database` and `supabase` providers. Local stores bytes on a volume, database uses binary columns, and Supabase uses a private server-side bucket integration. The client receives no service-role key and performs no privileged direct writes.

Local suits durable-volume deployments, but a database-only backup omits files. Database storage includes bytes in DB backups at the cost of size. Object storage separates byte traffic from SQL while requiring object/reference reconciliation and backups. Each row records its provider/bucket; changing environment variables does not recover old files.

PostgreSQL backup needs retention, artifact validation and restore drills in an isolated database. Volumes/buckets need their own recovery route. Restoring and checking application/file reads establishes recovery; a dump's existence or one successful script does not establish whole-system recovery.

**Owners:** [storage selection](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/config/ThesisReportStorageConfiguration.java), [storage contract](../integrations/thesis-report-storage.md), [backup operations](../../ops/backup/README.md), [production runbook](../PRODUCTION_RUNBOOK.md).

## T38. Multi-stage Docker, configuration and resources

Frontend dependencies/build use Node stages, followed by a non-root Next standalone runtime. Java builds with Maven/JDK and runs on a JRE under its own user. Dockerfiles pin base-image digests. Separate runtime stages reduce unnecessary tools; digest pinning identifies inputs without proving vulnerability freedom.

Compose defines health dependencies, internal networking, memory limits and volumes. Host ports are configurable; defaults do not describe every machine. Build-time `NEXT_PUBLIC_API_URL=/api/v1` and server-side `JAVA_API_ORIGIN` retain same-origin browser calls. On Windows/Git Bash, prevent MSYS from converting `/api/v1` into a Git installation path; use PowerShell environment assignments or the repository's conversion exclusions.

Client-visible demo credentials are an explicit build opt-in. Demo accounts should be enabled only in authorized evaluation environments. Real secrets come from runtime/secret files, never `NEXT_PUBLIC_*`, screenshots or committed logs. Documentation capture uses separate project/image/port names without recreating the working local stack.

**Owners:** [frontend Dockerfile](../../frontend/Dockerfile), [API Dockerfile](../../java-services/restful-api/Dockerfile), [local Compose](../../docker-compose.yml), [production Compose](../../docker-compose.prod.yml), [secrets](../../ops/secrets/README.md), [deployment](../deployment.md).

## T39. Liveness, readiness, timeouts and degraded operation

Liveness answers whether the process/API path operates; readiness checks required dependencies, with a configured health key. The frontend health proxy must report backend connectivity failure. A frontend HTTP 200 does not establish database or assistant readiness.

Database readiness, connection/validation timeouts and availability tracking bound waits when the database sleeps or disconnects. Keepalive/probes have their own budgets; database failure must not masquerade as a successful empty list. Sidecar/provider failures may degrade grounded assistant behavior, but authority verification failures cannot degrade into anonymous data access.

Site appearance is governed presentation configuration. Invalid body/CSRF input must fail rather than overwrite valid settings with defaults. Theme/logo and dashboard totals do not replace readiness or deployment evidence.

**Owners:** [health controller](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/system/controller/HealthController.java), [database readiness](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/system/service/DatabaseReadinessService.java), [frontend health](../../frontend/src/app/health/route.ts), [site appearance](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/site/), [health regression](../../frontend/tests/health-route.test.js).

## T40. Verification layers and evidence limits

| Layer | What it answers | What it does not establish |
|---|---|---|
| Helper/unit | Are formulas, parsers, reducers and policies correct for defined inputs? | Real-engine transactions, browsers or deployment |
| Contract/static | Do routes, copy, dependencies and boundaries match expected structure? | Complete runtime behavior |
| Persistence/H2 | Do services and SQL fixtures preserve rules? | Every PostgreSQL lock/RLS behavior |
| PostgreSQL IT | Do actual races, locks, constraints, RLS and migrations behave correctly? | Hosted database or external providers |
| Playwright/browser | What does a user see and which API path is exercised? | Every concurrency case or production load |
| Live SMTP/provider/storage | Does the supplied integration work? | Every configuration, environment or future time |

Manifests/workflows own current commands; avoid stale hand-maintained test totals. Main entry points:

```powershell
npm test --prefix frontend
npm run typecheck --prefix frontend
npm test --prefix mobile
npm run typecheck --prefix mobile
mvn -f java-services/pom.xml verify
node scripts/check-doc-hygiene.mjs
git diff --check
```

PostgreSQL IT requires suite-specific profiles/environment and disposable database names; read the workflow/test first. Mutation suites must not target production. The Java Docker capture build skips tests to construct the runtime; that build is not a successful `verify` run.

**Owners:** [frontend scripts](../../frontend/package.json), [mobile scripts](../../mobile/package.json), [Maven profiles](../../java-services/restful-api/pom.xml), [CI](../../.github/workflows/ci.yml), [E2E runner](../../scripts/run-course-e2e.mjs).

## T41. CI, container publication and release identity

CI defines Java, PostgreSQL and client lanes. A workflow file is not evidence of a successful current run. Image publication is separate: it accepts a specific main SHA, requires independent review confirmation and checks CI before build/push. Distinguish source SHA, image ID/digest, frontend build ID and the image actually deployed.

Rollback needs a previous image plus schema compatibility and data recovery when migrations changed data. Reverting a tag does not undo migrations or restore documents. PR merge, passing CI, published images and production acceptance are separate milestones.

**Owners:** [CI](../../.github/workflows/ci.yml), [image-publication gate](../../.github/workflows/publish-ghcr.yml), [release checklist](../RELEASE.md), [container guide](../docker-desktop-hub.md).

## T42. Reproducing truthful screenshots/GIFs

The [showcase](../media-showcase.en.md) records local provenance, source/build/API identity and fixture limitations. PNGs are actual rendered screens. GIFs sequence those real screenshots; they are not continuous recordings of actions. Screens display CampusUTE branding; CampusCore is the project/codebase name.

After starting a dedicated demo runtime and deliberately enabling its demo accounts:

```powershell
$env:DOCS_CAPTURE_BASE_URL='http://127.0.0.1:3210'
$env:DOCS_CAPTURE_DEMO_PASSWORD='<local-demo-password>'
node scripts/capture-docs-media.mjs
node scripts/encode-docs-tours.mjs
```

Capture uses Playwright from frontend dependencies; encoding requires FFmpeg in PATH. Every role uses a separate browser context, actual login and `/auth/me` check. VI/EN capture waits for hydration/fonts/data. Manifests record routes/status/observed API failures, never passwords/tokens. `DOCS_CAPTURE_ROLES` and `DOCS_CAPTURE_SHOTS` limit affected recaptures; `DOCS_CAPTURE_OUTPUT` selects the output directory.

Links alone are insufficient: decode GIFs, inspect timing/dimensions, review contact sheets and original images for loading, errors, unreadable text or the wrong role. Record fixture limitations rather than fabricating content or changing business data for attractive images. New asset paths avoid caching an earlier screenshot URL on repository viewers.

**Owners:** [capture script](../../scripts/capture-docs-media.mjs), [encoder](../../scripts/encode-docs-tours.mjs), [media provenance](../assets/showcase-2026-10/provenance.json).
