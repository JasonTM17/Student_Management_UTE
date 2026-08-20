# Release

## Release policy

- Branch pushes run CI only.
- Public registries publish only from semver tags such as `vX.Y.Z`.
- `latest` moves only with a semver release.
- GitHub Actions prefers `DOCKERHUB_TOKEN`; `DOCKERHUB_PASSWORD` remains a legacy fallback only.
- The CD badge in [README.md](../README.md) should point to the latest public semver tag because the gated publish workflow runs from release tags.

The current public release is **`v1.4.1`**. It follows the `v1.4.0` bilingual product, payment orchestration, and release verification baseline.

## Required quality gate

A release tag is valid only when `quality-gate` succeeds on that exact SHA.

Current required lanes:

- `java-restful-api-quality`
- `core-quality`
- `core-integration`
- `auth-quality`
- `auth-integration`
- `notification-quality`
- `notification-integration`
- `finance-quality`
- `finance-integration`
- `academic-quality`
- `academic-integration`
- `engagement-quality`
- `engagement-integration`
- `people-quality`
- `people-integration`
- `analytics-quality`
- `analytics-integration`
- `frontend-quality`
- `frontend-fast-e2e`
- `compose-contract`
- `k8s-contract`
- `image-smoke`
- `edge-e2e`
- `security-scan`
- `quality-gate`

## Java modular-monolith migration lane

`java-services/restful-api` is the only canonical Java build target, not an
additional public release image yet. Retained Java sibling services, including
the historical thesis pilot, are shadow/rollback sources and are never selected
by the default reactor. Node remains the route and writer owner until every
gate below passes on the exact candidate SHA.

Required before a Java boundary can enter a public release:

- `mvn -f java-services/pom.xml verify` passes with the supported JDK and only
  selects `restful-api`; direct shadow-child verification is recorded separately
  when a rollback rehearsal depends on it.
- The service is exercised in the production-shaped Compose/Kubernetes path,
  including readiness, metrics, logs, traces, and gateway routing.
- Differential contract tests compare Node and Java status, body, headers,
  cookies, errors, authorization, CSRF, and emitted events.
- Schema ownership, expand/contract migration, reconciliation, backup restore,
  and write rollback are demonstrated with recorded evidence.
- A gateway canary routes the selected boundary to Java and a bounded rollback
  returns traffic to Node without data loss or auth/session drift.
- Advisor and Kongming review the same exact snapshot; Wukong independently
  falsifies the cutover and rollback claims.

Until then, do not add the Java monolith image to the nine-image public release
list or describe the repository as fully Java-converted. The current status and
remaining blockers are tracked in
`plans/20260819-restful-api-consolidation/plan.md`.

## Public images

Each public release must publish these nine images:

1. `campuscore-backend`
2. `campuscore-auth-service`
3. `campuscore-notification-service`
4. `campuscore-finance-service`
5. `campuscore-academic-service`
6. `campuscore-engagement-service`
7. `campuscore-people-service`
8. `campuscore-analytics-service`
9. `campuscore-frontend`

Every release is verified with:

- a semver tag such as `v1.4.1`
- an immutable short SHA tag
- `latest`

## Post-publish verification

After CD finishes, verify:

- manifests for all nine images with `node scripts/verify-release-manifests.mjs`
- published GHCR images with image smoke
- published Docker Hub images when Docker Hub credentials are configured

Example local verification:

```powershell
$env:RELEASE_TAG='v1.4.1'
$env:RELEASE_SHORT_SHA=(git rev-parse --short HEAD).Trim()
node scripts/verify-release-manifests.mjs
```

The verifier checks GHCR and Docker Hub for all nine images across the semver tag, short SHA, and `latest`. It uses modest concurrency, per-request timeouts, and retries to reduce false negatives from transient registry behavior.

## Release notes

Each release should have a tracked note under `docs/releases/` and a matching GitHub release page.

- Use [docs/releases/TEMPLATE.md](./releases/TEMPLATE.md) as the starting point.
- Keep compatibility and operator notes explicit so the release page is useful without reading the full repo.
- Attach the current GitHub social preview PNG when the release includes repo-facing asset changes.
- Do not move an existing release tag to include docs-only follow-up commits; publish a new patch tag only when runtime artifacts change.

## Kubernetes handoff

The repository ships Kustomize manifests under `k8s/` for the full nine-image topology.

Local-first path:

- `node scripts/run-k8s-preflight.mjs`
- `node scripts/run-k8s-local-smoke.mjs`
- `node scripts/run-k8s-local-deploy.mjs`
- `node scripts/run-k8s-local-edge.mjs`

Public/operator handoff path:

- `k8s/overlays/staging-generic`
- `k8s/overlays/prod-generic`
- `k8s/overlays/staging-operator`
- `k8s/overlays/prod-operator`
- `k8s/templates/private-operator/staging`
- `k8s/templates/private-operator/prod`

Use [docs/K8S_HANDOFF.md](./K8S_HANDOFF.md) for staging and production overlay handoff, and [docs/CLOUDFLARE.md](./CLOUDFLARE.md) when the public domain is fronted through Cloudflare.
