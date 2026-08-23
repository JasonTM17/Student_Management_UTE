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
mvn -q -f java-services/pom.xml verify
docker compose config
docker compose build restful-api
docker compose up -d postgres restful-api
curl http://127.0.0.1:4010/api/v1/health/liveness
curl http://127.0.0.1:4010/api/v1/health/readiness
curl http://127.0.0.1:4010/v3/api-docs
```

Verify a fresh PostgreSQL database, Flyway versions, deterministic seed
counts, login, role isolation, enrollment transaction rules, grade publishing,
thesis group limits and assistant citations/locale fallback/outage behavior.

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
- Remove obsolete branches only with `git branch -d` after containment proof.
- Remove only exact old local microservice image tags after runtime proof.
- Never run `docker system prune`, `docker volume prune` or
  `docker compose down -v`.
