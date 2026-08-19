# Phase 03 — persistence seam and thesis read path

## Scope and owner

This phase adds persistence behind the standalone `restful-api` app and ports
only the authenticated thesis-topic read path:

```text
GET /api/v1/thesis/topics?roundId=<uuid>&status=<optional>
```

The new Java app is a **read-only candidate** for this path. The existing Java
thesis pilot/legacy owners remain the canonical writer and public route owner.
No nginx, Compose, Kubernetes or frontend API base URL has been changed.

The production Flyway script is intentionally copied with the existing `V1`
filename/content shape so an eventual handoff can compare checksum/history. The
test profile uses a separate, reduced H2-compatible migration because H2 does
not accept PostgreSQL's `TIMESTAMPTZ` alias. H2 success is therefore a rehearsal
of repository/controller wiring, not PostgreSQL compatibility proof.

## Delivered in the working candidate

- JPA, Flyway and PostgreSQL dependencies in the single Maven project.
- Default profile still excludes database auto-configuration; `persistence`
  enables PostgreSQL datasource/Flyway/JPA.
- Production migration at
  `java-services/restful-api/src/main/resources/db/migration/V1__create_thesis_schema.sql`.
- Test-only H2 migration at
  `java-services/restful-api/src/test/resources/db/migration-h2/V1__create_thesis_schema.sql`.
- Internal `thesis` package with `ThesisTopic`, `TopicStatus`, repository,
  service and REST controller.
- Authenticated and anonymous negative tests for the new read path.
- Auth contract repair found by Wukong:
  - access **or refresh** session cookie triggers CSRF on unsafe methods;
  - bearer bypass matches the legacy case-sensitive `Bearer ` prefix;
  - invalid/missing auth and CSRF failures use the stable JSON error envelope.
- Stable error coverage for validation, malformed JSON, malformed JWT, unknown
  routes and access denial paths.

## Verification observed

| Command/check | Result | Limitation |
| --- | --- | --- |
| `mvn -q -f java-services/restful-api/pom.xml test` | `PASS` | 17 tests: 9 API/security contract, 5 CSRF, 3 persistence/read-path; 0 failures/errors. Maven log uses JDK 24.0.2. |
| Flyway H2 profile | `PASS` | Creates a disposable in-memory `thesis` schema and repository mapping. H2 2.3 emits a Flyway support-version warning. |
| Production PostgreSQL Flyway script | `NOT_RUN` | No disposable PostgreSQL restore was created; the running shared DB was not mutated. |
| Legacy differential/API parity | `NOT_RUN` | New route is not wired to nginx/Compose and no route owner changed. |
| Backup/restore/reconciliation | `NOT_RUN` | Required before any writer handoff. |
| Frontend/mobile E2E | `NOT_RUN` | Clients still use legacy topology; no mobile app exists yet. |
| `git diff --check` | `PENDING` | Must run after the phase files are staged, before commit. |

## Canonical writer and rollback

- Canonical writer remains the existing thesis owner and its `thesis` schema
  history. The new app may read a restored/disposable copy only until a fresh
  PostgreSQL handoff review approves otherwise.
- Rollback for this phase is removal of the new app's opt-in profile/route from
  the candidate; there is no public route switch to reverse.
- A later handoff must record the database backup identifier, restore command,
  migration checksum, row-count/hash reconciliation, route switch mechanism,
  observation window and stop criteria. Restarting Java is not rollback proof.

## Open gates

1. Add a real adapter/fixture for comparing the same thesis query against the
   legacy owner without making the legacy writer dual-write.
2. Run the production migration against a disposable PostgreSQL database and
   compare schema history/checksum with the existing thesis migration.
3. Define the thesis read authorization matrix (student/lecturer/admin and
   department/round ownership) before exposing more than the current
   authenticated-only list.
4. Install the phase-03 exact-head Advisor/Kongming/Wukong review after commit.
5. Only after these gates may an opt-in runtime route be considered; public
   cutover and writer handoff remain `HOLD`.
