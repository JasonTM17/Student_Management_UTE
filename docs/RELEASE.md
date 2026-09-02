# Course Release Checklist

This checklist proves a reproducible local/course demo only. It does not
authorize production deployment.

## Clean candidate

- Record exact `main`, candidate and tree SHA.
- Verify `git status --short` is empty before review.
- Confirm no legacy backend, K8s, monitoring or retired image reference
  remains. The internal `rag-service` sidecar is an intentional course-runtime
  component and is addressed only through its token-protected
  `/internal/rag/**` contract. The current release workflow publishes the
  four course images to both Docker Hub (`nguyenson1710/campuscore-*`) and
  GHCR (`ghcr.io/jasontm17/campuscore-*`) from the same commit.

## API and database

```powershell
mvn -q -f java-services/pom.xml verify
docker compose config
docker compose up -d --build postgres mailpit rag-service restful-api web
curl.exe http://127.0.0.1:4010/api/v1/health/liveness
curl.exe -H "X-Health-Key: local-course-health-key" http://127.0.0.1:4010/api/v1/health/readiness
curl.exe http://127.0.0.1:4010/v3/api-docs
curl.exe http://127.0.0.1:3000/
curl.exe http://127.0.0.1:8025/
```

Verify a fresh PostgreSQL database, Flyway versions, deterministic seed
counts, login, role isolation, enrollment transaction rules, grade publishing,
thesis group limits and assistant citations/locale fallback/outage behavior.
Also verify the forward-only V12 assistant migration, conversation ownership,
idempotency ledger, quota buckets, terminal CAS/cancel race and knowledge
revision workflow. Provider tests must use a no-network fake by default; a live
DeepSeek smoke is optional, chargeable and only allowed after a rotated runtime
key is injected explicitly. Java 21 is the course compile, CI, and Docker runtime authority for this
successor. A local run on another JDK is recorded as NOT_RUN for that gate.

The published GHCR images mirror the local stack and can be smoked without a
rebuild. Pin `CAMPUSCORE_IMAGE_TAG` to the reviewed full commit SHA for a
reproducible smoke. The published-artifact overlay rejects an unset tag so a
moving registry alias cannot be selected accidentally. This
includes `campuscore-database`, a thin
`postgres:15-alpine` wrapper with no migrations or credentials. Flyway in the
REST API remains the only schema/seed owner; upgrading PostgreSQL requires a
fresh database and the same Flyway/health checks above:

```powershell
$env:CAMPUSCORE_IMAGE_TAG = "<full-commit-sha>"
docker compose -f docker-compose.yml -f docker-compose.rag.override.yml up -d --no-build postgres mailpit rag-service restful-api web
```

The same workflow publishes Docker Hub tags with the `campuscore-*` names.
Each registry receives an immutable full-commit-SHA tag; the workflow binds it
to `org.opencontainers.image.revision`, refuses wrong-revision or mismatched
tags, and fails closed when a previous run left only one registry populated;
that partial state requires an operator-run blob copy after source verification.
It emits BuildKit provenance and SBOM
attestations and does not perform a non-atomic cross-registry `latest` move.
Verify both registry manifests
before announcing a release:

```powershell
docker manifest inspect docker.io/nguyenson1710/campuscore-frontend:<full-commit-sha>
docker manifest inspect ghcr.io/jasontm17/campuscore-frontend:<full-commit-sha>
```

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
- Never run `docker system prune`, `docker volume prune` or
  `docker compose down -v`.
