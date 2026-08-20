# Phase 14 — Backend-first people read foundation

## Outcome

Add a small Java monolith people-profile read foundation needed before frontend
account/profile/admin screens can converge on the RESTful API:

- feature-gated student profile list/detail reads;
- feature-gated lecturer profile list/detail reads;
- legacy-compatible list envelopes, nested user snapshots and academic snapshot
  hydration from the migrated `people` schema.

This is not a people-service cutover, write ownership move, enrollment history
implementation, event publisher, PostgreSQL parity claim, gateway canary,
rollback proof, frontend change or mobile runtime claim.

## Scope and authority

In scope:

- expose Java people reads only when both the `persistence` profile and
  `migration.people-read.enabled=true` are active;
- use JDBC `SELECT` queries only against the migrated legacy `people` schema;
- preserve the current Nest list envelope: `{ data, meta }`;
- preserve student nested `user` and `curriculum.department` snapshots;
- preserve lecturer nested `user` and `department` snapshots;
- keep default `/api/v1/students` and `/api/v1/lecturers` routes returning
  `404` in the normal RESTful API shell.

Non-goals:

- no student/lecturer create, update, delete, enrollment history, RabbitMQ
  event publishing, auth-user hydration, academic live hydration or route
  ownership move in this phase;
- no schema DDL, Flyway migration or data reconciliation claim;
- no Stitch web/mobile rewiring.

## Acceptance criteria

- Student list/detail preserves ordering by `createdAt DESC`, status filtering,
  pagination metadata and nested user/curriculum/department shape.
- Lecturer list/detail preserves ordering by `createdAt DESC`, pagination
  metadata and nested user/department shape.
- Routes require authentication and fail closed for anonymous, invalid page,
  repeated query, unexpected query and not-found cases.
- Legacy-schema migration safety covers people read mode so Hibernate DDL and
  Flyway are not accidentally used as a migration authority for this candidate.
- Root reactor test and `git diff --check` pass on the exact source snapshot
  before commit.

## Verification

Observed local gates for this phase on Windows with JDK 24, `forkCount=0`, a
bounded Maven heap and test temp files redirected to a D-drive temporary
directory outside the repository:

```powershell
mvn -q -f java-services/restful-api/pom.xml '-Dtest=PeopleReadPersistenceTest,RestfulApiContractTest,MigrationSafetyConfigTest' '-DforkCount=0' test
mvn -q -f java-services/pom.xml '-DforkCount=0' test
git diff --check
node scripts/check-doc-hygiene.mjs
node scripts/check-architecture.mjs
node scripts/check-thesis-contract.mjs
```

These gates passed locally against H2/source tests before commit. They are
source/H2/local evidence only. PostgreSQL restore parity,
runtime smoke, route canary, event parity, data reconciliation, rollback and
independent final review remain open.
