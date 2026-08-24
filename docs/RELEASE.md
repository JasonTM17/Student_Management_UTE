# Course Release Checklist

This checklist proves a reproducible local/course demo only. It does not
authorize production deployment.

## Clean candidate

- Record exact `main`, candidate and tree SHA.
- Verify `git status --short` is empty before review.
- Confirm no legacy backend, gateway, K8s, monitoring or multi-image runtime
  reference remains.

## API and database

```powershell
.\mvnw.cmd -q -f java-services/pom.xml verify
docker compose config
docker compose build restful-api
docker compose up -d postgres restful-api
curl.exe http://127.0.0.1:4010/api/v1/health/liveness
curl.exe -H "X-Health-Key: local-course-health-key" http://127.0.0.1:4010/api/v1/health/readiness
curl.exe http://127.0.0.1:4010/v3/api-docs
```

Verify a fresh PostgreSQL database, Flyway versions through the actual latest
V18 migration, deterministic seed counts, login, role isolation, registration
round/window/eligibility rules, enrollment transaction rules, grade publishing,
thesis group limits and assistant citations/locale fallback/outage behavior.
Also verify the forward-only V13-V18 registration/assistant migrations,
conversation ownership, idempotency ledger, quota buckets, terminal CAS/cancel
race and knowledge revision workflow. Provider tests must use a no-network fake by default; a live
DeepSeek smoke is optional, chargeable and only allowed after a rotated runtime
key is injected explicitly. Java 25 is the release runtime authority; Java 21
is a separately recorded compatibility baseline using
`-Dcampuscore.java-baseline=true`. A local run on JDK 24/26 is recorded as
NOT_RUN for both gates. Always use `.\mvnw.cmd`/`./mvnw` so Maven 3.9.x is
enforced by the build.

## Clients

```powershell
npm test --prefix frontend
npm run typecheck --prefix frontend
npm run lint --prefix frontend
npm run build --prefix frontend
npm test --prefix mobile
npm run typecheck --prefix mobile
```

Review web routes at 1440px, 768px and 390px. Review authenticated student,
lecturer and admin flows, keyboard/focus behavior, loading/empty/error/
forbidden states, console output and failed network requests. Review mobile
login, refresh, logout and core student flows on an emulator or device.

## Git and cleanup

- Run `git diff --check` and `git fsck --connectivity-only --no-progress`.
- Obtain fresh Advisor, Kongming, Wukong, exact-head reviewer and Stitch
  verdicts on the same SHA.
- Treat unavailable reviewer capability as `BLOCKED_CAPABILITY`, never PASS.
- Report source/push and production cutover separately. Local tests, Compose,
  screenshots and a provider stub do not prove production readiness.
- Remove obsolete branches only with `git branch -d` after containment proof.
- Remove only exact old local microservice image tags after runtime proof.
- Never run `docker system prune` or `docker volume prune`. The disposable
  `scripts/run-course-e2e.mjs` runner may use `down -v` only with its generated
  `campuscore-course-e2e-*` project after collision/port preflight; never use it
  against the default developer project.
