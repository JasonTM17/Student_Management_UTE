# Phase 04 — Stitch web fidelity and native mobile scaffold

## Status

**Deferred / HOLD by backend-first sequencing.** The web P0 layout repairs and
native mobile scaffold are preserved, but no further Stitch web/mobile
implementation will be performed until the backend foundation gate in
`plan.md` passes. No fresh browser capture, Expo typecheck with installed
dependencies, emulator, or device run has been observed.

## Outcome and success signal

Deliver a Stitch-backed client layer for the one-API target:

- retain the existing responsive Next.js web surface;
- fix the highest-risk visual regressions found by independent audit;
- add a separate Expo/React Native app with more than 20 navigable mobile
  screen definitions and shared Academic Continuity tokens;
- keep both clients pointed at `/api/v1` and make missing Java routes explicit
  migration blockers.

This phase is complete only when the 23-screen native registry is typechecked
with its real Expo dependencies, the agreed web route matrix is recaptured at
desktop/390px/tablet widths, and an emulator/device smoke covers the primary
student, thesis, notification, assistant, lecturer, and admin flows.

## Scope, authority, and non-goals

### In scope

- `frontend/.stitch/DESIGN.md`, `SITE.md`, `metadata.json`, and
  `next-prompt.md` as the canonical Stitch baton and evidence boundary.
- Shared web layout fixes in dashboard metrics, assistant placement, canvas,
  content cap, and tablet navigation.
- `mobile/` Expo scaffold, native token file, API client seam, reusable UI,
  bottom navigation, menu/role preview, and 23 screen definitions.
- Route/API readiness mapping for the Java modular monolith.

### Non-goals

- No Stitch asset regeneration or blind screen generation.
- No edge/nginx/Compose/Kubernetes cutover.
- No claim that local preview data is live Java API data.
- No npm install, emulator boot, or image build while C: free space is low
  unless the runtime owner explicitly provisions a bounded environment.

## Current evidence

| Evidence | Result | Limitation |
| --- | --- | --- |
| Stitch MCP project inventory | `PASS` | Fresh 2026-08-19 read: 23 records, 9 desktop, 13 mobile, 1 supplementary image; 22 screen records have HTML and screenshot references. The local metadata ledger records the 22 named screens. |
| Existing web visual QA | `PASS` | 28 logical routes × 2 viewports = 56/56 captures, 0 measured overflow, 0 missing mobile-nav findings. It does not cover all current routes, tablet, full-page states, or reference pixel diff. |
| Independent Stitch/FE audit | `HOLD` | Found metric wrapping, assistant occlusion, missing current-route captures, token/breakpoint drift, and un-gated console errors. Browser rerun was `NOT_RUN`. |
| Web P0 repair | `PASS` source-level | Metric wrapping, assistant offset/z-index, exact background, 1280px cap, and tablet bottom-nav boundary are covered by a new smoke assertion. Rendered proof remains open. |
| Public landing browser smoke | `PASS` limited runtime | The running local frontend image showed no horizontal overflow at 1440px, 390px, or 768px and no public-page console errors. The image lacks a source-revision label; it is not exact-current-source, authenticated, full-route, or reference-diff evidence. |
| Web smoke/typecheck/lint | `PASS` | 28 smoke tests, Next route type generation/TypeScript, and ESLint zero-warning gate pass on the current web tree. |
| Native route/role/reference regression | `PASS` source-level | Five dependency-free tests prove 23 unique registry-to-component bindings, 13 direct mobile Stitch references backed by the 22-screen metadata ledger, role-specific bottom navigation, unauthorized-route fallback, transparent preview sign-in, explicit live Java auth handoff, and assistant locale payload parity. The navigator distinguishes `signedOut`, `preview`, and `authenticated` state; only preview role switching is allowed. |
| Native mobile source transpile | `PASS` | TypeScript syntax transpile passed for all 14 mobile `.ts/.tsx` files. This is not dependency-aware typecheck. |
| Native API preview guard | `PASS` source-level | The client defaults to `preview` and fails closed with `MOBILE_API_PREVIEW`; live calls require explicit `EXPO_PUBLIC_API_MODE=live`. |
| Expo/device runtime | `NOT_RUN` | `mobile/node_modules` is intentionally absent; no emulator/device or live API run. |
| Java API contract audit | `HOLD` | See `reports/frontend-java-api-audit.md`; Java target now has shell probes plus feature-gated auth/session, notification/engagement, academic catalog, people, finance, analytics, selected academic enrollment/grade/transcript reads, and a local thesis assistant contract fallback candidate. Complete academic writes, grade publishing, provider-backed chatbot parity, PostgreSQL parity, route canary and rollback evidence are still not implemented. |

### Fresh bounded checks — 2026-08-20, `ba90cf6`

- `npm test --prefix frontend`: PASS, 28/28 smoke tests.
- `npm test --prefix mobile`: PASS, 5/5 dependency-free atlas/API/navigation
  tests.

These checks keep the Stitch web/mobile source contract warm at the current
checkout. They do not replace the open browser visual rerun, authenticated E2E,
Expo dependency-aware typecheck, emulator/device smoke, or Java API cutover
evidence required by this phase.

### Fresh bounded checks — 2026-08-20, mobile auth seam

- `npm test --prefix mobile`: PASS, 5/5 dependency-free atlas/API/navigation
  tests. The mobile auth source seam keeps preview local, calls Java
  `/auth/login` only in explicit live mode, stores the returned bearer token in
  memory, and enters `authenticated` only after a token-bearing response.

- `npm test --prefix mobile`: PASS, 6/6 dependency-free atlas/API/navigation
  tests after wiring the assistant screen. Preview assistant responses remain
  local; explicit live mode calls the Java `/thesis/assistant/chat` route after
  bearer sign-in and surfaces the Java `degraded` fallback label.

This is source-level client evidence only. It is not Expo typecheck, emulator,
device, live Java API, authenticated E2E, gateway canary, or production cutover
evidence.

## Affected components and ownership

- Integration owner: `frontend/src`, `frontend/.stitch`, phase artifacts, and
  final exact-head gates.
- Native client slice: `mobile/` only; it uses the Java API seam and preview
  data until domain routes exist.
- Backend owner: `java-services/restful-api`; no public traffic change in this
  phase.
- Legacy services remain canonical route and writer owners for all missing
  domains.

## Ordered work

1. Freeze the Stitch project/token/screen inventory and update the baton.
2. Repair web P0 layout hazards and add source-level regression assertions.
3. Finish the native app registry, shared components, API client, role-aware
   menu and bottom navigation, direct Stitch-reference ledger, and all 23
   screen definitions.
4. Map each mobile/web API call to Java `implemented`, `candidate`, or
   `unverified` status; do not route clients to an incomplete Java app.
5. Run web static/smoke gates and native syntax checks under the disk budget.
6. Provision a bounded Expo/browser runtime only when available; capture
   desktop, 390px, and 768–1023px tablet evidence, including the ten routes
   missing from the prior matrix and both locales.
7. Obtain exact-head Advisor/Kongming/Wukong review after the final client and
   backend commits; stale pre-commit reviews do not count.

## Acceptance and exact verification

```powershell
Set-Location frontend
npm test
npm run typecheck
npm run lint
Set-Location ..
git diff --check
```

Native runtime acceptance additionally requires `npm install`/Expo typecheck
in a bounded environment, an emulator/device smoke, and an authenticated API
contract run. Those commands are intentionally not claimed here.

## Risks, rollback, and recovery

- The mobile scaffold can be removed or reverted as a bounded client-only
  change; it does not alter legacy services or Java traffic.
- If the web layout repair regresses a reference, revert only the six changed
  frontend files and retain the audit/report artifacts.
- Java route gaps must be resolved by domain waves; no client cutover is the
  rollback mechanism.
- Low disk and unavailable browser runtime are capability limits, not green
  visual/runtime gates.

## Documentation and unresolved decisions

- The API audit remains a `HOLD` for frontend cutover until Java domain routes,
  response parity, notification policy, finance/provider safety, persistence
  ownership, differential tests, and rollback evidence exist.
- The browser visual matrix needs full-page/reference-size diff and console
  error policy before it can become acceptance evidence.
- Whether mobile uses only bearer/refresh or a carefully scoped cookie mode is
  still governed by the parent consolidation plan; the scaffold defaults to
  bearer/refresh.
