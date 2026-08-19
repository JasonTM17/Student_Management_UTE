# Phase 01 — RESTful API architecture contract

## Decision

The project will converge on one Java Spring Boot RESTful API application. The
application is modular in source code, but it is not split into multiple Java
runtime services or Maven deployables. The final course topology is:

| Runtime | Responsibility | Status |
| --- | --- | --- |
| `restful-api` | All backend modules and the server-side chatbot adapter | Target, not cut over |
| `frontend` | Next.js Stitch web client | Existing and retained |
| `mobile` | Expo/React Native client using the same API | To be created |
| PostgreSQL | One database cluster, one migration owner | Target, not merged yet |

Redis, MinIO, notification push, payment providers and operator tooling are
optional adapters/profiles. They must not force the course project back into a
microservices topology. The legacy Compose/Kubernetes topology remains a
rollback profile until all retirement gates pass.

## Outcome Contract

### Public API

- Canonical prefix remains `/api/v1`.
- Resources use noun-based REST paths, HTTP status codes and JSON payloads.
- Errors use one stable problem shape with a public code, message and request
  correlation id; provider or database internals never leak to clients.
- List endpoints keep stable pagination, filtering, sorting and empty-state
  semantics before and after each wave.
- Existing FE bindings, especially thesis assistant bindings, are compatibility
  fixtures. A new canonical chatbot path may be introduced only with an alias
  and a deprecation record.

### Authentication and authorization

- Web keeps the existing `cc_access_token`, `cc_refresh_token`, `cc_csrf` cookie
  contract and `X-CSRF-Token` requirements for state-changing requests.
- Mobile uses short-lived bearer access tokens plus refresh tokens in platform
  secure storage. It must not put secrets in AsyncStorage or source bundles.
- JWT claim names, role/permission semantics, refresh rotation, revocation and
  service-to-service boundaries are frozen before the auth wave.
- Every module authorizes the current principal at the application boundary;
  chatbot context is filtered by the same authorization rules.

### Domain package boundaries

The first implementation uses packages under one application. A package may
call another package through an application service/port, not through HTTP or a
second database client.

| Package | Initial ownership |
| --- | --- |
| `auth` | identity, sessions, roles and permissions |
| `academic` | faculties, departments, semesters, courses, sections, attendance |
| `people` | students, lecturers and profile relationships |
| `enrollment` | enrollments, grades and transcript projections |
| `finance` | invoices, scholarships and course-scope payment records |
| `engagement` | announcements and support tickets |
| `notification` | inbox, unread counts and REST polling endpoints |
| `analytics` | dashboard and reporting read models |
| `thesis` | thesis topics, progress, evaluation and assistant compatibility |
| `chatbot` | provider port, prompt policy, quota/rate limit, timeout, redaction,
  audit and fallback |

No module may silently become a new process. If a future module needs isolation,
that is a separate architecture decision after this course objective.

### Data ownership

- One PostgreSQL cluster is the target; Flyway is the only schema migration
  owner for the new app.
- The first migration wave preserves existing logical schemas (`auth`,
  `academic`, `people`, `finance`, `engagement`, `notifications`, `public`, and
  `thesis`) to limit table/ID risk. This is still one database and one API.
- A domain has exactly one canonical writer at a time. Shadow reads and
  differential comparisons are allowed; unreviewed dual-write is not.
- Before switching a writer, record a backup identifier, restore result,
  row-count/hash reconciliation, foreign-key checks and a rollback route.
- Prisma migration history is retained as legacy evidence until the matching
  domain is reconciled and retirement is explicitly approved.

### Notification, files and payments

- The simple target API exposes notification list/unread/read REST resources.
  Polling is the baseline; legacy Socket.IO stays available only as a temporary
  compatibility path.
- File access goes through a storage port. Local filesystem storage is the
  default course profile; MinIO is an optional adapter and is not a reason to
  keep a separate service.
- Payment integration is an adapter with sandbox-only defaults. Finance is late
  in the migration order and retains a legacy route until signatures, idempotency
  and reconciliation pass.

### Chatbot module

- Canonical target is a REST resource owned by `restful-api`; the existing thesis
  assistant route may remain as a compatibility alias during migration.
- The module accepts a validated user message and an authorized context DTO. The
  provider receives no database credentials and cannot query arbitrary data.
- Mock provider is deterministic and enabled by default for local/course tests.
  Real provider mode is opt-in through server-side environment variables only.
- Each request has a maximum duration, bounded output, quota/rate limit and
  redacted audit metadata. Provider errors return a stable fallback response.

## Migration seams

1. **Freeze:** inventory public routes, response shapes, auth cookies/headers,
   websocket channels, file URLs, IDs, schemas, queues and environment keys.
2. **Shell:** add one standalone Java app with health, error, security,
   correlation and test fixtures; no public traffic change.
3. **Thesis + engagement:** port low-risk reads/writes behind compatibility
   controllers, starting with deterministic fixtures and existing thesis tests.
4. **Notifications:** add REST polling parity; hold Socket.IO until clients are
   migrated.
5. **Academic + people + enrollment:** reconcile catalog, people, grades and
   transcript data, then switch one writer at a time.
6. **Analytics:** port read models and compare dashboard aggregates.
7. **Finance:** sandbox and idempotency review before any write cutover.
8. **Auth:** migrate identity ownership last, after every client and service
   contract is tested.
9. **Cutover:** route one boundary at a time, observe, and prove rollback before
   retiring the corresponding legacy owner.

## Phase gate

Phase 01 is complete only when this contract is committed, the independent
review record is attached, and the integration owner has explicitly accepted
the direct-collapse falsification. It does not authorize implementation
cutover, deletion, production release or a heavy Docker build.

## Exact verification for this phase

```text
git status --short
git diff --check
git show --stat --oneline HEAD
```

The expected result is that only the intended plan/documentation files are
committed and the pre-existing untracked `.agents/` directory remains
unstaged.
