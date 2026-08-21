# Phase 51 — Academic catalog read extension

## Outcome

Extend the existing feature-default-off Java academic catalog candidate with
low-risk academic-year and classroom read routes while preserving legacy write
ownership in the Nest academic service.

## Scope

Java now exposes these routes only when both the `persistence` profile and
`migration.academic-read.enabled=true` are active:

- `GET /api/v1/academic-years`
- `GET /api/v1/academic-years/{id}`
- `GET /api/v1/classrooms`
- `GET /api/v1/classrooms/{id}`

The routes reuse the existing academic read migration flag because they are the
same catalog-read boundary as semesters and courses.

## Legacy compatibility selected for this slice

- Academic-year list/detail uses the legacy `page`/`limit` envelope and
  `startDate DESC` ordering.
- Academic-year responses include nested semester summaries.
- Classroom list uses the legacy `page`/`limit` envelope and building/room
  ordering.
- Classroom detail includes nested section summaries.
- Anonymous access, invalid pagination, repeated/unexpected query parameters,
  and missing details continue to use the stable Java error envelopes.
- Classroom `equipment String[]` parity is intentionally deferred to a later
  PostgreSQL-read parity slice because this wave avoids adding array handling
  before a disposable restore/canary gate.

## Non-goals

- `POST /academic-years`, `PUT /academic-years/{id}` and
  `DELETE /academic-years/{id}` remain owned by the legacy academic service.
- `POST /classrooms`, `PUT /classrooms/{id}` and
  `DELETE /classrooms/{id}` remain owned by the legacy academic service.
- No public route ownership, PostgreSQL parity, canary, rollback, frontend
  wiring, mobile wiring or production cutover moved in this phase.

## Evidence

- Source commit: `e68faa7 feat(java): add academic catalog reads`.
- Focused gate PASS:
  `mvn -q -f java-services/restful-api/pom.xml '-Dtest=io.campuscore.restfulapi.academic.AcademicReadPersistenceTest,io.campuscore.restfulapi.RestfulApiContractTest' '-DforkCount=0' test`.
- Full Java reactor PASS after one test-fixture compatibility repair:
  `mvn -q -f java-services/pom.xml test`; surefire summary from
  `java-services/restful-api/target/surefire-reports`: 25 reports / 171 tests /
  0 failures / 0 errors / 0 skipped.
- SQL-write grep PASS for the changed academic read repository/service/
  controller: no SQL write, DDL or mutation markers in production candidate
  code.
- `git diff --check` PASS with only Git Windows CRLF working-copy warnings.
- Broad staged secret scan hit only JWT test-helper terminology; high-confidence
  secret marker scan PASS.

## Repair note

The first full reactor run failed because this phase's H2 classroom fixture
added new non-null classroom columns without defaults, while older academic
schedule/section/waitlist tests still insert minimal classroom rows. The repair
kept the richer Phase 51 fixture but added H2 defaults so existing minimal
fixtures remain compatible. This was a test-schema interaction, not a production
schema change.

## Remaining gates

- PostgreSQL read parity on an approved disposable restore is still `NOT_RUN`.
- Route canary and rollback rehearsal are still `NOT_RUN`.
- Public academic route ownership remains with the legacy Nest academic service.
- FE Stitch web/mobile runtime parity remains blocked by the backend foundation
  gate in `plan.md`.
