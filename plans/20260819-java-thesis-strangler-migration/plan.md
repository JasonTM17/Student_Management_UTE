---
title: Java Thesis Strangler Migration and Stitch Alignment
status: pending
---

# Outcome

Convert the project from the current Node/Nest public backend shape to a Java-first service layer, using strangler migration rather than a big-bang rewrite, while keeping the Next.js frontend as the shell and aligning the FE to the Stitch academic design system.

Success means:

1. Java services own the migrated public boundaries.
2. The production-shaped deploy path exercises the Java boundary, not just local compose.
3. FE tokens, typography, spacing, and responsiveness match the Stitch academic design system closely enough to be the canonical UI reference.
4. Rollback, observability, and data reconciliation are proven on the exact head before public cutover.

# Scope

In scope:

- `java-services/` as the Maven/Spring Boot spine.
- Pilot migration of thesis-facing behavior first.
- Auth, engagement, notification, academic, people, finance, and core compatibility in later waves.
- FE alignment to Stitch screens and design tokens.
- Release gates, rollback, observability, and parity testing.

Non-goals:

- No big-bang rewrite of all frontend code to Java.
- No forced monolith collapse.
- No public traffic cutover without diff tests and rollback proof.
- No treating a green local test run as production authorization.

Authority:

- Preserve the current branch and dirty/untracked state.
- Do not delete or reset unrelated workspace changes.
- Use exact-head evidence for any release claim.
- Keep worker scope disjoint when multiple threads are used.

# Current Evidence

- Branch: `feature/java-thesis-platform`.
- Current committed head: `b63875e` (`docs: record admin mobile card progress`).
- Base head: `fdc547c8d3d42abb4e986e91c06f520c8b3aae46`.
- Untracked workspace state: `.agents/`.
- Java stack exists and is already testable: parent Maven build plus `auth`, `engagement`, `notification`, and `thesis` modules.
- Focused `mvn -q -f java-services/thesis-service/pom.xml test` and `verify` both pass after the readiness patch; Surefire records 18 tests with zero failures/errors, including the three `HealthControllerTest` cases.
- `mvn -q test` in `java-services` passes.
- The FE candidate now has a Stitch-aligned contract in `frontend/.stitch/DESIGN.md` and a short implementation pointer in `frontend/DESIGN.md`.
- The FE candidate now has a real notifications center for unprefixed and localized dashboard routes, mobile bottom navigation, and focused thesis topic/progress/evaluation routes.
- Frontend smoke, typecheck, lint, and `git diff --check` pass on the current dirty candidate.
- Frontend production build passes, and the rendered visual QA artifact records 28 routes × 2 viewports = 56/56 captures with zero horizontal overflow and zero missing mobile navigation findings.
- External Compose Playwright full suite passes 20/20 twice when `E2E_AUTH_LOGIN_URL=http://127.0.0.1:4007/api/v1` is used for seeded session setup; edge auth/CSRF/security tests still run through nginx. The shared Compose DB and the mutating checkout fixture are not accepted as an isolation proof.
- After the Java pilot and FE accessibility patch, `kubectl kustomize k8s/base`, `kubectl kustomize k8s/overlays/thesis-pilot`, `node scripts/run-k8s-preflight.mjs`, `docker compose -f docker-compose.yml config`, frontend typecheck, frontend lint, and `git diff --check` pass. No K8s apply, Java build, image build, or runtime pilot smoke has been run.
- `node scripts/check-thesis-contract.mjs` passes a source-level oracle for all 22 Java thesis mappings, 8 current FE bindings, and the enabled/disabled nginx route fragments. This is not runtime response, authorization, mutation, data, or rollback parity.
- The latest FE bounded a11y pass adds explicit label/control associations to auth and profile forms; frontend typecheck and lint pass after that patch, while browser and isolated E2E reruns remain open.
- Admin Users now has a bounded stacked mobile-card implementation with desktop-table fallback; the latest frontend smoke is 22/22, typecheck and lint pass, and the remaining dense admin tables are still open.
- The isolated `npm run test:e2e` runner is present but did not complete because local service dependencies are not installed (`backend/node_modules/.bin/prisma` is missing); this is recorded as NOT_RUN, not PASS.
- During this continuation, Docker Desktop was restarted without elevated service control. A bounded `WinGet` FFmpeg cache under the user Temp directory was validated and permanently removed after its Recycle Bin copy kept consuming space; `msrdc.exe` is still active and `DiagOutputDir` ETL files were recently updated, so that directory remains preserved. A monitored current-source Docker build was aborted at the 0.70 GB safety threshold; no `pilot-local` image was produced. The resulting unused build cache was pruned once (241.4 MB logical reclaim, C: physical free stayed near 0.27 GB). Heavy image and deploy work remains deferred.
- The committed candidate adds an isolated `k8s/overlays/thesis-pilot` route and Java Deployment/Service with Postgres/Redis waits, while keeping the canonical base/generic nine-image release path unchanged. Base nginx receives only empty optional fragments so the pilot can avoid duplicating the full gateway config; no thesis upstream or route resolves in the canonical base.
- Stitch project `16486483525927292845` defines the academic reference system:
  - `Be Vietnam Pro`
  - blue fidelity palette
  - 4px/8px radii
  - academic, dense, minimalist layout

# Assumptions

- The user wants a durable migration plan, not only a diagnosis.
- The Java migration should stay strangler-style and preserve service boundaries.
- The frontend should remain Next.js unless a future decision explicitly changes that.
- `thesis-service` is the first realistic Java pilot boundary.

# Ownership Model

- Integration owner: one thread owns the plan, release gates, and merge order.
- Reviewer threads:
  - Advisor: scope, trade-offs, and sequencing.
  - Kongming: architecture, release containment, rollback.
  - Wukong: falsification of risky claims.
- FE audit owner: keeps Stitch parity evidence and UI verification.
- Java migration owner: keeps boundary-by-boundary service cutover.

# Ordered Stages

1. Freeze baseline and record exact evidence. **Complete for the base snapshot; candidate remains dirty.**
2. Audit FE against Stitch and classify drift. **Complete for the 22-screen atlas plus 56 rendered web/mobile captures; accessibility and mutation parity remain open.**
3. Lock migration strategy and write release gates. **In progress through this plan and phase documents.**
4. Make `thesis-service` the first Java public boundary. **Pilot overlay implemented in the working tree; not complete until Kustomize/runtime, differential, schema, and rollback gates pass.**
5. Move adjacent public behavior in controlled waves. **Pending.**
6. Prove data reconciliation, observability, and rollback. **Pending and release-blocking.**
7. Align frontend implementation to the Stitch system. **In progress; tokens, notifications, mobile nav, and thesis decomposition are implemented, and the 56-capture matrix is green; accessibility, table/card parity, and isolated E2E remain open.**
8. Remove remaining Node public ownership only after parity and rollback are proven. **Pending.**

# Acceptance Criteria

The goal is complete only when all of these are true:

- The Java boundary is live in the production-shaped deploy path.
- Public service cutover is done by boundary, not by vague intent.
- Differential contract tests prove parity for status, body, headers, cookies, errors, auth, and event behavior.
- Data migration and rollback are verified on the exact head.
- Gateway canary and rollback containment are proven.
- Health, readiness, logs, traces, metrics, and alerts are wired in the actual deploy path.
- FE is aligned to the Stitch academic theme instead of the old Mastercard-inspired design language.
- Stitch parity evidence covers the 22-screen web/mobile atlas, with rendered screenshots at desktop and mobile widths for every authenticated family that can be reached in the supplied runtime.

# Verification Commands

Baseline:

```powershell
git status --short --branch
git log --oneline -5
```

Frontend:

```powershell
cd frontend
npm test
npm run typecheck
npm run lint
npm run build
```

Java:

```powershell
cd java-services
mvn -q test
```

Release gates:

```powershell
git diff --check
```

Kubernetes pilot render (read-only):

```powershell
kubectl kustomize k8s/base
kubectl kustomize k8s/overlays/thesis-pilot
node scripts/run-k8s-preflight.mjs
node scripts/check-thesis-contract.mjs
```

The pilot render commands pass, but Java/Maven, image build, runtime smoke, and
apply remain deferred while C: remains below 1 GB free. The source contract
checker is green but does not substitute for runtime parity.

Rendered FE QA (requires an authenticated local runtime):

```text
desktop: dashboard, thesis, notifications, profile, registration, evaluation
mobile: 390px dashboard, bottom navigation, thesis, notifications, profile
localized: /en/* and /vi/* for the same route families
```

Later, after implementation:

```powershell
docker compose up
kubectl apply -k k8s/base
```

plus whatever exact smoke and rollback commands the deploy path uses.

# Risks

- Auth/csrf/cookie parity drift.
- Schema ownership drift between Node and Java.
- False confidence from local tests that do not cover deploy topology.
- FE drift if the Stitch tokens are not applied to actual runtime surfaces.
- A local preview that cannot reach the auth/API services can only prove public/login rendering, not authenticated dashboard parity.
- The optional nginx fragment mechanism must remain empty in base and resolve
  only in the thesis-pilot overlay; a stale or duplicated gateway config would
  invalidate route ownership evidence.
- The pilot image is local-only and has no digest, provenance, registry smoke,
  or release publication evidence.

# Rollback and Recovery

- Keep the old Node boundary alive until Java parity is proven.
- Roll back by boundary, not by whole-system panic.
- Preserve the last known good deploy artifact and schema migration path.
- Never remove rollback evidence before the cutover is accepted.

# Documentation and Release Impact

- Update `docs/RELEASE.md` with Java-specific release lanes.
- Update architecture docs when ownership changes.
- Keep the Java pilot overlay and its preflight contract documented without
  adding it to the public nine-image release list.
- Keep handoff notes for each boundary cutover.
- Record any FE token changes in the UI docs or design notes.

# Blockers

- Live deploy-path proof is still missing.
- Auth is still shadow-only on the Java side.
- External E2E is repeatable with the explicit direct-auth session setup, but the shared database/checkout mutation still needs an isolated run. The isolated runner needs local Node service dependencies installed before it can execute; low C: space currently makes that unsafe.
- FE accessibility, business mutation parity, and full Stitch-to-production acceptance are still open despite the green visual capture matrix.
- Exact-head Advisor/Kongming/Wukong review of the post-commit snapshot is
  still required. Fresh bounded sidecars for `7353d8e` timed out and were
  recorded as `NOT_RUN`; the first Advisor review was conditional and
  read-only on the pre-patch head, so no approval is inferred.
- A second exact-head set for `cf7f9bc` (Advisor, Kongming, Wukong, and FE
  Stitch/a11y) also timed out in two bounded windows and remains `NOT_RUN`.
- Java thesis schema bootstrap/ownership, differential contract coverage,
  authenticated pilot smoke, metrics/logs/traces, canary, and rollback evidence
  remain open. `FLYWAY_ENABLED=true` is pilot-only for now and must not be
  silently carried into a shared production-like environment.
