# Phase 03 — Isolated Java Thesis Kubernetes Pilot

## Outcome

Give the Java thesis boundary a production-shaped, opt-in Kubernetes path while
keeping the canonical nine-image base, generic overlays, release workflow, and
Node ownership unchanged.

## Scope and authority

In scope:

- `k8s/overlays/thesis-pilot` in namespace `campuscore-thesis-pilot`.
- Java `thesis-service` Deployment/Service on port 4010.
- Secret-backed JWT/readiness settings, `thesis` schema datasource, Redis URL,
  Postgres/Redis startup waits, and health probes.
- Explicit nginx upstream/routes available only through the pilot overlay.
- Kustomize preflight checks and documentation of the unpublished image gate.
- A source-level thesis contract manifest/checker covering Java mappings, FE
  bindings, and enabled/disabled nginx route fragments.

Out of scope:

- Public GHCR/Docker Hub publication of the Java image.
- Changes to staging/prod generic overlays or the nine-image release list.
- Shared staging/production traffic, Node owner removal, canary approval,
  differential parity, reconciliation, restore, or rollback sign-off.

Authority and preservation:

- `.agents/` remains untracked and untouched.
- No broad cleanup, reset, or dependency installation is permitted while C:
  remains critically low.
- The base nginx fragment files must stay route-empty; only the pilot overlay
  may resolve `thesis-service:4010`.

## Implementation contract

The base mounts two route fragments containing comments only. The pilot overlay
replaces that ConfigMap and adds:

- `upstream thesis_service_upstream { server thesis-service:4010; }`.
- Exact and prefix `/api/v1/thesis` routes with the same edge rate/connection
  limits as the existing Compose gateway.
- A non-root Java container with readiness key, liveness, startup, resource and
  termination settings.
- `campuscore-thesis-service:pilot-local` with `IfNotPresent`; this is not
  registry or provenance evidence.

## Acceptance criteria

- `kubectl kustomize k8s/base` contains no thesis Deployment, Service, upstream,
  or route.
- `kubectl kustomize k8s/overlays/thesis-pilot` contains the isolated namespace,
  Java workload, Postgres/Redis waits, health probes, and thesis routes.
- Canonical nine-image tags remain aligned in base, bootstrap, Docker Desktop,
  generic, operator, private-template, and thesis-pilot renders.
- `run-k8s-preflight.mjs` rejects a public GHCR thesis image and verifies the
  pilot route/service contract.
- Runtime smoke, auth/CSRF negative tests, data/schema ownership, metrics/logs/
  traces, canary and rollback evidence are recorded before any shared route is
  enabled.

## Verification plan

Run when disk headroom is restored:

```powershell
kubectl kustomize k8s/base
kubectl kustomize k8s/overlays/thesis-pilot
node scripts/run-k8s-preflight.mjs
node scripts/check-thesis-contract.mjs
mvn -f java-services/pom.xml verify
```

Then build/load the local image and run the pilot smoke path. Do not treat a
successful render as proof of runtime readiness, parity, rollback, or public
release readiness.

## Current status

The overlay, preflight contract, and source-level thesis contract checker are
implemented after base commit `fdc547c8d3d42abb4e986e91c06f520c8b3aae46` and
committed in the current candidate. Base and pilot Kustomize renders, the full
K8s preflight, Compose config render, and `node scripts/check-thesis-contract.mjs`
PASS. Runtime pilot smoke, Java/Maven verification, image provenance, and apply
are still `NOT_RUN`; C: is approximately 0.56 GB. The source checker is not
runtime parity evidence. Advisor's pre-patch review was conditional GO for this
design and NO-GO for apply/public cutover; exact-head post-patch reviews remain
required. The four sidecars dispatched against `cf7f9bc` timed out twice and
are recorded as `NOT_RUN`, not approval.
