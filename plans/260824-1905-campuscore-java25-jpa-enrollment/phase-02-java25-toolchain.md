---
title: "Phase 1: Java 21 baseline and Java 25 toolchain"
status: completed
---

# Phase 1: Java 21 baseline and Java 25 toolchain

## Objective

Make the Java runtime/compiler contract explicit and reproducible while
keeping Spring Boot 3.5.16. Establish a Java 21 baseline, then prove the same
candidate on a real Java 25 runtime. JDK 24/26 output is diagnostic only.

## Owned paths

`java-services/pom.xml`, Maven wrapper files, Java Dockerfile/runtime metadata,
CI toolchain definitions, Compose build metadata, and the relevant setup
documentation. Do not alter domain behavior in this phase.

## Steps

1. Inspect existing Maven/CI/container conventions and add Maven Wrapper 3.9.x
   plus Enforcer/toolchain checks that fail clearly when the required JDK is
   unavailable.
2. Run the baseline reactor on Java 21 and record its exact environment and
   result separately from the target gate.
3. Set compiler release/runtime image/documentation to Java 25 only after the
   baseline is recorded. Keep source compatibility at the plan-approved level
   unless a tested migration requires a narrow change.
4. Run the focused and full Maven gates using a real Java 25 runtime (local
   installation or isolated approved container) and record limitations.

## Exit criterion

Java 21 baseline evidence and real Java 25 build/test evidence exist, the
project refuses ambiguous host JDK selection, and no JPA/enrollment behavior
has been mixed into this toolchain commit.

## Verification budget

`./mvnw -q -f java-services/pom.xml -pl restful-api -am verify`, followed by
the full reactor command from the parent plan, with `java -version` and
`mvn -version` captured. If Java 25 cannot be supplied, mark the target gate
`BLOCKED_CAPABILITY`/`NOT_RUN`; do not relabel JDK 24/26 as PASS.

## Evidence result (2026-08-24)

- Java 21 baseline: PASS in `maven:3.9.12-eclipse-temurin-21`, explicit
  `campuscore.java-baseline=true`, full `verify`, exit 0.
- Java 25 target: PASS in `maven:3.9.12-eclipse-temurin-25`, full `verify`,
  exit 0. Surefire aggregate: 32 XML files, 162 tests, 0 failures, 0 errors,
  0 skipped. Spring Boot startup identified Java 25.0.2.
- Host guard: EXPECTED FAIL on the available JDK 24; Enforcer rejected the
  default Java 25 range instead of silently compiling with the host runtime.
- Docker build/runtime and CI definitions now target Temurin 25, while the
  Java 21 job remains separate.

The run exposed noisy scheduled assistant recovery SQL errors in isolated H2
contexts that do not create the assistant schema. They did not fail the Maven
gate and are deferred to the persistence-isolation defect checkpoint.
