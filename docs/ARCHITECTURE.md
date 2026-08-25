# Architecture

## Runtime

```text
Next.js web ─────┐
                 ├── Java Spring Boot REST API ─── PostgreSQL
Expo mobile ─────┘
```

The Java application is the only public backend owner of `/api/v1`. Web uses
cookie authentication with CSRF protection; mobile uses bearer access and
refresh tokens. Both clients consume the same OpenAPI contract.

## Domain ownership

The Java API owns authentication, identity, people, catalog, sections,
enrollment, grades, schedules, announcements, notifications, thesis core and
the local thesis assistant. Auth and academic records use stable string IDs;
thesis and assistant records use UUIDs. Student and lecturer codes remain
unique business identifiers.

Flyway owns the schema. A fresh course database is seeded deterministically
with roles, demo users, academic catalog, sections, announcements,
notifications, thesis data and curated assistant knowledge. No legacy service
schema is read at runtime and no old data migration is required.

Authentication lifecycle data is also Flyway-owned. Student self-registration
creates a pending account and a hashed, single-use email-verification challenge;
admin-created staff accounts are pre-verified. Password-reset challenges are
hashed, time-limited and generic at the public boundary. SMTP delivery is
performed after commit through the internal mail template adapter; Mailpit is
the local/e2e sink and raw tokens never enter logs or persistent columns.
Application-owned users, roles, sessions, challenges and rate buckets live in
`campuscore_auth`. V20 moves legacy CampusCore objects out of `auth`, so a
hosted Supabase database retains exclusive control of its managed `auth`
schema.

## Assistant

The thesis assistant performs bounded retrieval over published thesis knowledge
revisions plus a fixed public academic-catalog projection in PostgreSQL.
Lexical ranking, locale fallback, immutable source snapshots and server-owned
citations remain the deterministic baseline. When `DEEPSEEK_ENABLED=true` and a
server-only `DEEPSEEK_API_KEY` exists, Java may send only the current question
and filtered snapshot context to the allowlisted DeepSeek chat endpoint using
`deepseek-v4-flash` with thinking disabled. Provider I/O runs outside the short
reservation/quota transactions; completion and cancellation compete on one
terminal CAS. The provider is never called from a client, never receives raw
history/profile data, and never becomes the source of citations. Timeout,
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

## Persistence boundary

Assistant conversations, messages, citations, usage buckets, turn leases,
provider dispatch fences, feedback and knowledge revision/audit records are
mapped through JPA repositories. Registration mutation state uses the same JPA
writer boundary. Flyway remains the schema authority and Hibernate is
validation-only (`ddl-auto=validate`); provider I/O stays outside the short
database transactions. Some legacy catalog/academic read and administration
paths, the Spring auth compatibility repositories, and thesis-group management
remain JDBC by deliberate compatibility scope; this plan does not claim a
whole-domain JDBC removal.

For a brand-new Supabase target, Flyway can add the opt-in schema-only B20
baseline location followed by the reviewed V21 successor. B20 creates only
`campuscore_auth`, `academic`, `thesis`, `assistant`, `engagement` and
`notifications`; it contains no seed, identity, session, challenge,
assistant-history or test rows. Existing CampusCore databases keep their
V-history and apply V21 normally. Remote synchronization
is a separate guarded operation, not a change of runtime authority or a
production cutover.

## Non-goals

Finance, analytics, support tickets, realtime sockets, thesis
council/review/evaluation, unbounded/client-side
external AI, vector search, Redis,
RabbitMQ, MinIO, Nginx, Kubernetes, observability stack and production
cutover are outside the course runtime.
