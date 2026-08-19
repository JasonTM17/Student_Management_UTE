# Phase 02 — single RESTful API shell

## Status

Implemented as commit `e086c04` (`feat: add single restful api shell`). This is
the first code phase of the consolidation plan. It is a shell gate, not domain
parity, database migration or public cutover.

## Delivered

- Standalone Maven project at `java-services/restful-api` using Spring Boot 3.4.5
  and Java 21 compiler release.
- One `RestfulApiApplication` main class and one eventual JAR/container target.
- Public health seam:
  - `GET /api/v1/health/liveness`
  - key-protected `GET /api/v1/health/readiness`
- Frozen identity seams:
  - web `cc_access_token` cookie resolution;
  - mobile `Authorization: Bearer ...` resolution;
  - cookie-authenticated unsafe requests require `cc_csrf` and
    `X-CSRF-Token` equality.
- Bounded `X-Request-Id` propagation and stable validation/HTTP error envelope.
- Authenticated `/api/v1/me` claim probe and `/api/v1/contract/ping` mutation
  probe so future modules can be tested against the same security boundary.
- Actuator/OpenAPI configuration and a non-root Dockerfile for the future image.

## Verification observed

| Command/check | Result | Limitation |
| --- | --- | --- |
| `mvn -q -f java-services/restful-api/pom.xml test` | `PASS` | 8 tests: 6 Spring contract tests + 2 CSRF filter tests. Maven test log used JDK 24.0.2. |
| `mvn -q -f java-services/restful-api/pom.xml -DskipTests package` | `PASS` | Produced `target/campuscore-restful-api-0.1.0-SNAPSHOT.jar` (~35 MB). |
| Local JAR smoke on port `14010` | `PASS` | Liveness 200, readiness without key 403, readiness with key 200; process stopped gracefully. Runtime log used JDK 26.0.1. |
| `node scripts/check-thesis-contract.mjs` | `PASS` | Existing thesis source contract only; does not prove the new app owns thesis routes. |
| `git diff --check` | `PASS` | No whitespace errors at the implementation handoff. |
| Docker image build | `NOT_RUN` | Intentionally deferred because C: free space is volatile/low and image provenance is not needed for this shell gate. |
| Database/Flyway integration | `NOT_RUN` | Deliberately not wired until the migration seam and canonical-writer design are implemented. |
| Browser/mobile E2E | `NOT_RUN` | No client route has been switched to the new shell; native mobile app is not created yet. |

## Safety boundary

- No existing Node service, Java pilot, Compose service, nginx route, database
  schema, Prisma migration or `.agents/` content was deleted or rewritten.
- The endpoint `/api/v1/contract` explicitly reports `migration: not-cut-over`.
- The source requires a server-side JWT secret of at least 32 characters; test
  credentials are only in `src/test/resources/application-test.yml`.
- The default shell has no database or provider credentials, so its readiness
  response reports only `application-shell`. It must not be promoted as a
  production readiness signal.

## Next bounded phase

Add a persistence adapter and Flyway ownership behind a disposable Postgres
profile, then port the first low-risk thesis read path with a legacy differential
fixture. The next phase must keep exactly one writer and include backup/restore
and negative authorization tests before any route change.
