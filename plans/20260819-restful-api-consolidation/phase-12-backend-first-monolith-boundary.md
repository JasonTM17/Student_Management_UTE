# Phase 12 — Canonical Java modular-monolith build boundary

## Outcome

Make `java-services/restful-api` the only module in the canonical Java reactor.
This is a source/build-boundary change: it does not move HTTP traffic, change a
canonical writer, enable a feature flag, touch a database, build or publish an
image, or start frontend/mobile implementation.

## Current evidence and decision

At exact current HEAD `4b5e7712f4a9fd309f3bc616e314c6c8db6ce8f0`, the root
Java reactor already names only `restful-api`, and the focused monolith suite
still passes on a real JDK. The older `ba90cf61e89ac48110a7be680b443513909dd771`
snapshot in this phase record is now stale for current-head verification.

Fresh bounded evidence on 2026-08-21:

- `mvn -q -f java-services/pom.xml test`
  - `JAVA_HOME=C:\Program Files\Java\jdk-26.0.1`
  - result: PASS
  - Surefire summary: 27 reports / 178 tests / 0 failures / 0 errors / 1
    skipped
- `mvn -q -f java-services/restful-api/pom.xml test`
  - `JAVA_HOME=C:\Program Files\Java\jdk-26.0.1`
  - `MAVEN_OPTS=-Xmx1g -Djava.io.tmpdir=D:/Student_Management/.tmp/java-tmp`
  - result: PASS

The legacy Java directories remain in source as rollback/compatibility
references. They are deliberately not deleted and may still be built directly
with their own `pom.xml` when a bounded compatibility rehearsal needs them.

## Scope and non-goals

In scope:

- change `java-services/pom.xml` so its module list contains only
  `restful-api`, and rename its metadata to describe the modular monolith;
- update the monolith README and this phase record with the canonical and
  legacy-direct-build commands, and align the CI, retained shadow Dockerfiles,
  and Java/release architecture documentation;
- run the root reactor's focused test command with a real JDK and temporary
  files on D:.

Non-goals:

- no deletion or source move for `auth-service`, `engagement-service`,
  `notification-service`, or `thesis-service`;
- no new Java deployable, no Compose/nginx/Kubernetes change, and no route or
  client cutover;
- no Flyway/Prisma migration, data copy, writer handoff, or cleanup of the
  active PostgreSQL target;
- no Stitch screen or Expo implementation.

## Invariants

- `restful-api` remains one JAR/container and the sole canonical Java build
  target.
- Legacy services remain recoverable direct builds; they must not be silently
  broken by removing them from the reactor.
- Every domain keeps its current canonical writer. Read candidates remain
  disabled by default.
- The source-level test pass is never reported as PostgreSQL parity, runtime
  cutover, canary, rollback, CI, push, or production proof.

## Ordered execution

1. Freeze base/HEAD and untracked scope; exclude untracked agent mappings from
   implementation evidence.
2. Obtain independent Advisor and Kongming sequencing advice.
3. Change the root Java reactor to the monolith-only module list; preserve the
   legacy sibling POMs untouched.
4. Update local build documentation and the parent plan's backend-first gate.
5. Run `mvn -q -f java-services/pom.xml test` using the selected real JDK, a
   bounded heap, and `java.io.tmpdir` on D:; run `git diff --check`.
6. Freeze the changed exact HEAD and request independent reviewer plus Wukong
   probes for the claim that the canonical build cannot accidentally execute a
   legacy Java deployable.
7. Commit only the explicit phase, reactor, CI, Dockerfile, README, and
   architecture/release documentation files with a Conventional Commit after
   every gate is observed. Do not push until all required review gates pass.

## Acceptance and rollback

Acceptance requires a reactor build that executes the monolith's 45-test suite
without selecting a legacy module, a CI quality-gate dependency on that reactor
job, documentation that distinguishes canonical from direct legacy builds, and
no unrelated worktree changes.

Rollback is a small source revert of the reactor metadata only. It does not
need to restart or reroute a runtime because this phase intentionally makes no
runtime change.

## Follow-up safety repair — notification-only startup

After the canonical-reactor commit, a bounded Wukong probe falsified the broader
claim that every read candidate could start in isolation: notification tests
were still masked by thesis/Flyway defaults. The repair generalizes the
read-only migration safety seam so both engagement and notification read flags
disable Hibernate schema management and reject accidental Flyway execution.

The focused source/H2 gate is:

```powershell
mvn -q -f java-services/restful-api/pom.xml '-Dtest=NotificationReadPersistenceTest,MigrationSafetyConfigTest' test
```

It passes with `migration.thesis-read.enabled=false`,
`migration.notifications-read.enabled=true`, and `spring.flyway.enabled=false`,
so notification read startup is no longer relying on a test-created thesis
schema. This is still source/H2 evidence only; it does not prove PostgreSQL
runtime parity, shared-database isolation, canary, rollback, or cutover.
