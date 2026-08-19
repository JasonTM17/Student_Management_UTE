# Phase 03 — persistence seam and thesis read path

## Status

The bounded implementation is committed at `0064df8` (with the persistence
seam introduced by `8c904a1`). The phase remains **in progress** because the
PostgreSQL, differential, backup/restore and exact-head review gates are still
open.

## Scope and owner

This phase adds persistence behind the standalone `restful-api` app and ports
only the authenticated thesis-topic read path:

```text
GET /api/v1/thesis/topics?roundId=<uuid>&status=<optional>
```

The new Java app is a **read-only candidate** for this path. The existing Java
thesis pilot/legacy owners remain the canonical writer and public route owner.
No nginx, Compose, Kubernetes or frontend API base URL has been changed.
The controller is additionally gated by `THESIS_READ_ENABLED`; the persistence
profile alone does not expose the read route.

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
- A read-side round-existence port that preserves the legacy `404` response for
  an unknown registration round instead of returning an empty list.
- Authenticated and anonymous negative tests plus a role-baseline matrix for
  student, lecturer and admin readers on an existing round.
- Auth contract repair found by Wukong:
  - access **or refresh** session cookie triggers CSRF on unsafe methods;
  - bearer bypass matches the legacy case-sensitive `Bearer ` prefix;
  - invalid/missing auth and CSRF failures use the stable JSON error envelope.
- Stable error coverage for validation, malformed JSON, malformed JWT, unknown
  routes and access denial paths.

## Verification observed

| Command/check | Result | Limitation |
| --- | --- | --- |
| `mvn -q -f java-services/restful-api/pom.xml test` | `PASS` | 21 tests: 9 API/security contract, 5 CSRF, 7 persistence/read-path; 0 failures/errors. Maven log uses JDK 24.0.2. |
| `mvn -q -f java-services/restful-api/pom.xml '-Dtest=RestfulApiContractTest,CsrfCookieFilterTest,ThesisTopicPersistenceTest' test` | `PASS` | Focused rerun after the round-existence parity fix; 21 tests, 0 failures/errors. |
| `src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker` | `PASS` | Test-only subclass mock maker keeps the harness runnable on the current JDK without changing production runtime behavior. |
| Flyway H2 profile | `PASS` | Creates a disposable in-memory `thesis` schema and repository mapping. H2 2.3 emits a Flyway support-version warning. |
| Production PostgreSQL Flyway script | `NOT_RUN` | No disposable PostgreSQL restore was created; the running shared DB was not mutated. |
| Legacy differential/API parity | `NOT_RUN` | New route is not wired to nginx/Compose and no route owner changed. |
| Backup/restore/reconciliation | `NOT_RUN` | Required before any writer handoff. |
| Frontend/mobile E2E | `NOT_RUN` | Web client still uses legacy topology; a 23-screen native scaffold now exists, but Expo/device/API runtime evidence is not available. |
| `git diff --check` | `PASS` | Clean at the `0064df8` implementation handoff; unrelated `.agents/` remains untracked and unstaged. |

The first H2 rehearsal intentionally exposed that H2 2.3 does not accept the
PostgreSQL `TIMESTAMPTZ` alias used by the production-compatible script. The
production migration was preserved; a reduced H2-only script with
`TIMESTAMP WITH TIME ZONE` is used for the in-memory rehearsal. This failure and
the workaround are recorded rather than treated as PostgreSQL proof.

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
3. The candidate now proves the baseline that authenticated students,
   lecturers and admins can read an existing round, matching the current legacy
   controller. Define and test department/round ownership rules before
   exposing more than this authenticated-only list; those rules are not
   inferred from the current legacy read contract.
4. Install the phase-03 exact-head Advisor/Kongming/Wukong review after commit.
5. Only after these gates may an opt-in runtime route be considered; public
   cutover and writer handoff remain `HOLD`.
