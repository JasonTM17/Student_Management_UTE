# Java K8s Pilot Evidence

## Snapshot

- Base snapshot before this patch: `fdc547c8d3d42abb4e986e91c06f520c8b3aae46`.
- Committed candidate snapshot: `7353d8e` (`feat: add isolated Java thesis pilot path`).
- Documentation follow-up snapshot: `1fcf258` (`docs: record exact-head pilot review timeout`).
- Branch: `feature/java-thesis-platform`.
- `.agents/` is unrelated untracked state and remains unmodified.
- Candidate scope: optional nginx fragments, `k8s/overlays/thesis-pilot`, Java
  Deployment/Service, Kustomize preflight and docs.

## What is implemented

- Canonical base keeps comments-only thesis fragments, so it has no Java thesis
  upstream or route.
- Pilot replaces the fragments, creates namespace
  `campuscore-thesis-pilot`, starts Java after Postgres/Redis, and proxies only
  `/api/v1/thesis` to `thesis-service:4010`.
- Java uses the existing access-token/JWT and health-key contract, `thesis`
  schema, pilot-only Flyway bootstrap, Redis URL, non-root runtime settings,
  dependency-backed readiness, and probes.
- Existing nine-image release parity checks now also render and inspect the
  pilot overlay, assert the pilot nginx mount and ClusterIP edge, while
  rejecting a public GHCR thesis image.
- `contracts/thesis-public-contract.json` and `scripts/check-thesis-contract.mjs`
  add a source-level oracle for all 22 Java controller mappings, 8 FE thesis
  bindings, and the local-enabled/production-disabled nginx fragments.

## Evidence and limits

- `git diff --check`: PASS before the next commit.
- `kubectl kustomize k8s/base`: PASS.
- `kubectl kustomize k8s/overlays/thesis-pilot`: PASS.
- `node scripts/run-k8s-preflight.mjs`: PASS for the canonical overlays and
  thesis pilot contract; `docker compose -f docker-compose.yml config`: PASS.
- `node scripts/check-thesis-contract.mjs`: PASS for 22 Java mappings and 8 FE
  bindings. This remains source-level evidence only.
- `mvn -q -f java-services/thesis-service/pom.xml test`: PASS; Surefire records
  18 tests with zero failures/errors, including the readiness controller tests.
- Runtime smoke, Java image verification and apply: `NOT_RUN`; C: has
  approximately 0.33 GB free after Maven and heavy commands are intentionally
  held.
- Java image: local-only `campuscore-thesis-service:pilot-local`; no digest,
  registry publication, provenance, image smoke, or deployment evidence.
- Schema bootstrap, differential contract, reconciliation/restore, canary,
  observability and rollback: NOT_RUN/open.
- Production Compose now mounts disabled thesis fragments; local Compose mounts
  the Java fragments and waits for Redis as well as Postgres.
- The route is pilot-only and is not a shared staging/prod cutover.

## Review status

- Advisor review of the pre-patch exact head: conditional GO for design,
  NO-GO for apply/public cutover; no approval inferred for this new snapshot.
- Fresh post-commit Advisor, Kongming, Wukong, and disk-audit sidecars were
  dispatched against `7353d8e`; two bounded wait windows expired without a
  result, so all four are recorded as `NOT_RUN`, not approval. The sidecars
  were closed without edits or cleanup.
- A second exact-head set of Advisor, Kongming, Wukong, and FE Stitch/a11y
  sidecars was dispatched against `cf7f9bc`; two bounded wait windows again
  expired without a result. They are recorded as `NOT_RUN`, not approval, and
  were closed without edits.
