# Phase 06 — Thesis registration-round read boundary

## Outcome

Add the first missing Thesis workspace prerequisite to the single Java
`restful-api`: a gated, read-only `GET /api/v1/thesis/rounds` endpoint. This
does not change nginx, Compose, public routing, the canonical writer, or the
legacy `thesis-service` owner.

## Scope

- Read the existing `thesis.thesis_registration_round` schema through a JPA
  read model already created by the single-app Flyway migration.
- Preserve the legacy response fields, optional `status` filtering, and
  descending `registrationStart` ordering expected by `frontend/src/lib/thesis-api.ts`.
- Activate only in the `persistence` profile and when
  `THESIS_READ_ENABLED=true`; the default shell exposes no route.
- Expose only the two required query methods from the Round repository rather
  than a generic Spring Data write surface. This is a source-level guard;
  runtime activation still requires a PostgreSQL credential restricted to
  `SELECT`.
- Add tests for the default-disabled route, sorting/filtering, and H2 schema
  parity for optional round dates.

## Verification

| Command | Result | Limitation |
| --- | --- | --- |
| `mvn -q -f java-services/restful-api/pom.xml -DskipTests compile` with bounded JVM heap | PASS | Compile only; no Spring test runtime. |
| `mvn -q -f java-services/restful-api/pom.xml test` | BLOCKED_CAPABILITY | The initial run revealed and fixed missing nullable round-date columns in the H2 test fixture. A rerun initialized the corrected JPA model but the Windows JVM then failed because the paging file/commit limit was exhausted. |
| `mvn -q -f java-services/restful-api/pom.xml -Dtest=ThesisTopicPersistenceTest -DforkCount=0 test` with bounded JVM heap | BLOCKED_CAPABILITY | JVM native-memory allocation failed before test execution for the same host resource condition. |

The source test is retained; a later run in a writable Windows environment
with sufficient commit memory must be green before the boundary is considered
verified. This phase is neither PostgreSQL proof nor a route cutover.

## Remaining gates

- Run the persistence and contract suites on a healthy host.
- Compare immutable fixtures against the legacy Thesis service.
- Add groups/councils/reviews/results/chat read or write boundaries one at a
  time under explicit ownership and rollback gates.
- Do not direct frontend traffic to this endpoint until isolated PostgreSQL,
  authenticated parity, shadow/canary, and rollback evidence exist.
