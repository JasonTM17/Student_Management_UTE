# CampusCore Stitch implementation site

## Canonical reference

- Stitch project: `16486483525927292845` (Smart Student Management Portal).
- The direct screen ledger is [`metadata.json`](./metadata.json), refreshed
  from Stitch on 2026-08-19. It records 22 named reference screens and keeps
  the supplementary image separate from implementation scope.
- Design system: Academic Continuity; the local token contract is
  [`DESIGN.md`](./DESIGN.md).
- Current Stitch atlas observed on 2026-08-19: 23 screen records — 9 desktop,
  13 mobile, and 1 supplementary image record. The 22 screen records have
  HTML and screenshot references; the supplementary image has no HTML record.
- The web implementation lives under `frontend/src` and the native mobile
  implementation lives under the repository-level `mobile/` app. Responsive
  web is not counted as the native app.

## Fidelity contract

Every visual change must preserve the Academic Continuity tokens: Be Vietnam
Pro, blue-first `#003F87` / `#0056B3` actions, `#F9F9FF` canvas, white cards,
4px spacing rhythm, 4–8px utility radii, 16px mobile gutters, 44px touch
targets, desktop fixed-sidebar navigation, and mobile bottom navigation.

Each screen review covers the normal, loading, empty, error, permission, and
long-text states. Compare desktop references at the Stitch desktop width and
the web at a 390px viewport; compare native mobile against the mobile atlas.

## Evidence boundary

- Web smoke: 27/27 PASS; typecheck PASS; lint PASS with zero warnings on the
  current frontend source.
- Existing visual QA artifact: 28 web routes × 2 viewports = 56/56 captures,
  0 overflow findings, and 0 missing mobile navigation findings.
- Direct Stitch atlas inventory is current, but a fresh in-app browser visual
  rerun is `NOT_RUN` because the browser runtime failed to initialize. Do not
  upgrade the visual claim until that runtime or an equivalent Playwright
  capture is available.
- Native mobile dependencies are intentionally not installed while C: is low;
  device/emulator, Expo, and mobile API runtime evidence remain `NOT_RUN`.

## Ownership and change guard

- `frontend/.stitch/` is the design evidence and baton area.
- `frontend/src/` is the web integration owner's code area.
- `mobile/` is the native app area and must use the same `/api/v1` contract.
- Do not regenerate or download Stitch assets unless an existing local asset is
  first checked and a concrete reference gap is recorded in the baton.
- Do not claim that a Stitch reference is production behavior, a Java route is
  implemented, or a mobile screen is runtime-tested without corresponding
  evidence.
