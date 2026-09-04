# Architecture

![CampusCore system architecture](assets/campuscore-system-architecture.png)

The [vector source](assets/campuscore-system-architecture.svg) is kept beside
the rendered PNG so the diagram remains reviewable and zoomable. It describes
the local/course runtime plus the prepared production topology; the Caddy edge
is shown as a bundle boundary, not as evidence of production cutover.

## Runtime

```text
Next.js web ─────┐
                 ├── Java Spring Boot REST API ─── PostgreSQL
Expo mobile ─────┘
Local course compose also runs rag-service and Mailpit. They are not a second
public API; Java remains the only /api/v1 owner.
```


The Java application is the only public backend owner of `/api/v1`. Web uses
cookie authentication with CSRF protection; mobile uses bearer access and
refresh tokens. Both clients consume the same OpenAPI contract. In production,
Caddy is the prepared TLS edge and routes public traffic to the web/API
containers; it is not evidence that a production cutover has happened.

## Domain ownership

The Java API owns authentication, identity, people, catalog, sections,
enrollment, grades, schedules, announcements, notifications, thesis core and
the assistant. Auth and academic records use stable string IDs;
thesis and assistant records use UUIDs. Student and lecturer codes remain
unique business identifiers.

Flyway owns the schema. A fresh course database is seeded deterministically
with roles, demo users, academic catalog, sections, announcements,
notifications, thesis data and curated assistant knowledge. No legacy service
schema is read at runtime and no old data migration is required.

## Assistant

The assistant performs bounded retrieval over published campus knowledge
revisions and a fixed public academic-catalog projection in PostgreSQL. The
private `rag-service` sidecar uses the same Java artifact/profile on port 4011;
the REST API proxies `/internal/rag/**` and remains the only public `/api/v1`
owner. In production, Supabase's `assistant` schema is the authoring and
published-release authority. The sidecar validates release count, hash,
privacy and domain metadata before atomically promoting a PostgreSQL runtime
snapshot. Local/course mode seeds the same snapshot through Flyway.

Lexical ranking, locale fallback, immutable snapshots and server-owned
citations remain the deterministic baseline. When `DEEPSEEK_ENABLED=true` and
a server-only `DEEPSEEK_API_KEY` exists, the sidecar may send only the current
question and filtered snapshot context to the allowlisted DeepSeek chat endpoint
using `deepseek-v4-flash` with thinking disabled. Provider I/O runs outside the
short reservation/quota transactions; completion and cancellation compete on
one terminal CAS. The provider is never called from a client, never receives
raw history/profile data, and never becomes the source of citations. Timeout,
quota, circuit, missing-key, no-match, provider, privacy, or database failures
return a truthful lexical/degraded response.

The API exposes backward-compatible JSON and an SSE stream. Conversations and
messages are owner-scoped to students/lecturers, expire after 90 days, and can
be physically deleted by their owner after confirmation. Knowledge revisions
use Draft → pending review → published; publication requires a different
admin, so a single demo admin cannot self-publish. Feedback is limited to
`UP`/`DOWN` plus the fixed reason list; no free-form feedback is stored.
Turn leases are recovered every 30 seconds: pre-dispatch expiry is retryable,
while a dispatched expiry is fenced as `FAILED_AMBIGUOUS` without redispatch or
quota refund. The daily job performs the separate retention purge.

## Client and UI direction

The web portal uses the HCMUTE reference language: institutional blue shell,
student identity rail, yellow navigation groups, blue header tables, ribbon
page titles, tab strips, dense information layout and explicit loading/error/
empty/forbidden states. The implementation uses project-owned assets and does
not copy credentials, cookies, HTML or proprietary source code.

## Non-goals

Finance, analytics, support tickets, realtime sockets, email verification,
email password reset, thesis council/review/evaluation, vector search, Redis,
RabbitMQ, MinIO, Nginx, Kubernetes, observability stack and production
cutover are outside the course runtime.
