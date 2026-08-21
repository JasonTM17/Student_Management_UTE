# Phase 55 — Academic internal-context bridge

## Outcome

Add internal-only academic context endpoints inside the single
`java-services/restful-api` modular monolith so server-side consumers can look
up curricula, departments and student enrollments without widening the public
academic route surface.

## Exact source checkpoint

- Source commit: `d92ce53e884adcd83b5dd479aebeb584d9a83946`
- Security hardening follow-up:
  `97a9b7e12d70aeb6a55959ea66b593515ede778f`
- Parent baseline: `255a25a8b91e3ea5410e257e205fd530538fe4ac`
- Branch: `feature/java-thesis-platform`
- Feature gate: `migration.academic-context.enabled=true`

## Implemented scope

- `GET /api/v1/internal/academic-context/curricula/{curriculumId}`
- `GET /api/v1/internal/academic-context/departments/{departmentId}`
- `GET /api/v1/internal/academic-context/students/{studentId}/enrollments`

The internal bridge:

- is guarded by `X-Service-Token` and `internal.service-token`, sourced from
  `INTERNAL_SERVICE_TOKEN`, with no built-in fallback token;
- returns `403` for missing or invalid service tokens;
- uses the existing academic read services and does not add a new writer;
- permits only the three owned academic-context `GET` routes at the Spring
  Security layer so unowned internal paths still require authentication;
- keeps the public academic curriculum, enrollment and thesis route surface
  unchanged.

## Verification

- Focused gate PASS:
  `mvn -q -f java-services/restful-api/pom.xml '-Dtest=io.campuscore.restfulapi.academic.AcademicReadPersistenceTest,io.campuscore.restfulapi.academic.AcademicEnrollmentReadPersistenceTest,io.campuscore.restfulapi.thesis.ThesisTopicPersistenceTest,io.campuscore.restfulapi.RestfulApiContractTest' '-DforkCount=0' test`
- Security hardening focused gate PASS:
  `mvn -q -f java-services/restful-api/pom.xml '-Dtest=io.campuscore.restfulapi.academic.AcademicContextSecurityTest,io.campuscore.restfulapi.RestfulApiContractTest,io.campuscore.restfulapi.academic.AcademicReadPersistenceTest,io.campuscore.restfulapi.academic.AcademicEnrollmentReadPersistenceTest,io.campuscore.restfulapi.thesis.ThesisTopicPersistenceTest' '-DforkCount=0' test`
- Initial full canonical Java monolith gate PASS:
  `mvn -q -f java-services/pom.xml clean test`
- Surefire summary from `java-services/restful-api/target/surefire-reports`:
  26 reports / 177 tests / 0 failures / 0 errors / 1 skipped.
- Full Java reactor after the hardening follow-up is `NOT_RUN`; C: free space
  was low during this checkpoint, so the hardening proof is limited to the
  focused backend regression and related Phase 55 contracts above.
- Production SQL-write grep PASS for the changed production security and
  academic-context files.
- High-confidence secret-marker scan PASS for the changed production and test
  files.
- `git diff --check` PASS with only Git Windows LF-to-CRLF working-copy
  warnings on touched files.

## Open gates

- Public academic route canary/rollback and PostgreSQL parity remain separate
  later slices.
- Internal context consumers still rely on the shared service-token contract.
- FE Stitch/web/mobile remains a later phase once the backend foundation is
  complete.
