# Phase 1 - Java toolchain evidence

## Scope

The integration owner changed only the build/runtime contract in the isolated
feature worktree. Domain, client, and assistant behavior remain candidate input
for later waves.

## Target contract

- Java 25 LTS is the default compiler/runtime (`maven.compiler.release=25`).
- Maven 3.9.x is required and a Maven Wrapper 3.9.12 is committed from the
  repository root (`mvnw`, `mvnw.cmd`, `.mvn/wrapper/*`).
- Java 21 remains an explicit compatibility profile, activated only with
  `campuscore.java-baseline=true`.
- Docker build and runtime images use Temurin 25.
- CI has separate Java 25 and Java 21 baseline jobs.

## Evidence

| Gate | Command/environment | Result |
|---|---|---|
| XML/POM parse + Java 21 profile | `docker run --rm --entrypoint mvn -v "D:\\worktrees\\Student_Management-feature-campuscore-java25-jpa-enrollment:/workspace" -w /workspace maven:3.9.12-eclipse-temurin-21 -q -f java-services/pom.xml -Dcampuscore.java-baseline=true -DskipTests validate` | PASS, exit 0 |
| Full Java 21 compatibility baseline | same image, `-Dcampuscore.java-baseline=true verify` | PASS, exit 0 |
| Java 25 target validate | `maven:3.9.12-eclipse-temurin-25`, `-DskipTests validate` | PASS, exit 0 |
| Full Java 25 target verify | same image, `verify` | PASS, exit 0; 32 Surefire XML files, 162 tests, 0 failures, 0 errors, 0 skipped |
| Host guard | host Maven on JDK 24, default `mvn -f java-services/pom.xml -q -DskipTests validate` | EXPECTED FAIL; Enforcer rejected Java outside `[25,26)` |

The Java 25 test process reported `Java 25.0.2` in Spring Boot test startup.
The candidate jar was produced at
`java-services/restful-api/target/campuscore-restful-api-0.1.0-SNAPSHOT.jar`.

## Known limitation / follow-up

Several pre-existing H2 persistence contexts emit scheduled assistant recovery
errors because those intentionally isolated test databases do not create the
assistant schema. The Maven process still exits 0 and all reported tests are
green; this noise is retained as a Phase 2 isolation defect to address without
weakening the gate.

Java 25 proof is container-local. No host Java 25 installation, remote CI run,
PostgreSQL integration run, live provider, device, or production evidence is
claimed here.

## Exit ruling

Phase 1 toolchain exit criterion is met for source + local container runtime.
The next step is an isolated DB/JPA writer wave; this report becomes stale if
the toolchain contract changes.
