# Phase 17 — Backend-first academic enrollment read foundation

## Outcome

Add a small Java monolith academic enrollment and grade read foundation needed
before the student enrollment, grade and transcript screens can converge on the
RESTful API:

- feature-gated current-student enrollment list;
- feature-gated admin enrollment list/detail reads;
- feature-gated current-student grades and transcript reads;
- feature-gated lecturer/admin grade item and student-grade reads;
- legacy-compatible enrollment envelope with nested student, section, course,
  semester, lecturer and schedule summaries;
- legacy-compatible grade/transcript envelopes with weighted totals and GPA
  summary.

This is not an academic-service cutover, enroll/drop port, waitlist port,
grade editing or publishing, timetable port, CSV/export port, event consumer
move, PostgreSQL parity claim, gateway canary, rollback proof, frontend change
or mobile runtime claim.

## Scope and authority

In scope:

- expose Java enrollment reads only when both the `persistence` profile and
  `migration.academic-enrollment-read.enabled=true` are active;
- use JDBC `SELECT` queries only against the migrated legacy Prisma
  `academic` schema;
- preserve `/api/v1/enrollments/my`, `/api/v1/enrollments` and
  `/api/v1/enrollments/{id}` for the selected read-only shape;
- preserve selected read-only grade routes:
  `/api/v1/enrollments/my/grades`, `/api/v1/enrollments/my/transcript`,
  `/api/v1/grades/items/lecturer/my`,
  `/api/v1/grades/student-grades/lecturer/my`, and
  `/api/v1/grades/student-grades/enrollment/{enrollmentId}`;
- enforce student self-scope for detail reads by hiding other students' records
  as `404`;
- keep default enrollment/grade routes returning `404` in the normal RESTful
  API shell.

Non-goals:

- no enroll, drop, waitlist, grade item/student grade write, grade publish,
  timetable, RabbitMQ/event, export, write ownership or frontend migration in
  this phase;
- no schema DDL, Flyway migration or data reconciliation claim;
- no Stitch web/mobile rewiring.

## Acceptance criteria

- Student self read returns only enrollments for the current JWT `studentId` and
  supports an optional `semesterId` filter.
- Admin list read preserves a legacy-style `{ data, meta }` envelope, supports
  page/limit/status/semesterId/studentId/courseId/sectionId filters, and keeps
  latest enrollments first.
- Detail read allows admins/super admins and the current student, while hiding
  other students' records.
- Student grade reads include only completed or published/appealed enrollment
  records and preserve course, section, semester, lecturer, grade and status
  fields.
- Transcript reads group the same student grade records by semester and derive
  cumulative GPA, earned credits and attempted credits.
- Lecturer grade reads require a `lecturerId` claim and preserve grade item,
  student-grade, calculated weighted total and total-weight fields for the
  selected read-only routes. Section-level grade reads must scope lecturer
  callers to sections they own; admin/super-admin callers may read the selected
  section without a lecturer ownership filter.
- Role/query boundaries reject anonymous access, student access to admin list,
  missing student/lecturer claims, invalid page sizes and unexpected query
  parameters.
- Legacy-schema migration safety covers academic enrollment read mode so
  Hibernate DDL and Flyway are not accidentally used as a migration authority
  for this candidate.

## Verification

Observed local gates for this phase on Windows with JDK 24, `forkCount=0`, a
bounded Maven heap and test temp files redirected to a D-drive temporary
directory outside the repository:

```powershell
mvn -q -f java-services/restful-api/pom.xml clean '-Dtest=io.campuscore.restfulapi.academic.AcademicEnrollmentReadPersistenceTest,MigrationSafetyConfigTest,RestfulApiContractTest' '-DforkCount=0' test
node scripts/check-doc-hygiene.mjs
node scripts/check-architecture.mjs
node scripts/check-thesis-contract.mjs
git diff --check
rg -n "\\b(INSERT|UPDATE|DELETE|MERGE|CREATE|DROP|ALTER|TRUNCATE)\\b|jdbc\\.update|execute\\(" java-services/restful-api/src/main/java/io/campuscore/restfulapi/academic/repository/AcademicEnrollmentReadRepository.java java-services/restful-api/src/main/java/io/campuscore/restfulapi/academic/service/AcademicEnrollmentReadService.java java-services/restful-api/src/main/java/io/campuscore/restfulapi/academic/web/AcademicEnrollmentReadController.java
```

The focused Maven gate passed locally. It covers enrollment self/admin/detail,
student grade/transcript, lecturer grade item/student-grade aggregation,
default-off route behavior and role/query negative cases. The source mutation
grep returned no runtime academic enrollment package matches, supporting the
SELECT-only claim for this slice.

Continuation repair evidence: the Java controller now passes JWT roles and
`lecturerId` into the section/enrollment grade read service methods, and the
repository has explicit lecturer-owned filters for section and enrollment grade
reads. The H2 persistence test records 6/6 passing tests, including lecturer
other-section denial as empty list or `404`, missing lecturer claim as `403`,
and weighted-total calculation that ignores unscored rows.

Root reactor remains subject to the current Windows native-memory/pagefile
capacity limitation observed in earlier phases; do not claim a root-reactor
pass until it is actually observed.

PostgreSQL restore parity, runtime smoke, route canary, full academic write
parity, rollback and independent final review remain open.
