# Phase 02 - FE Stitch parity and responsive contract

Status: in-progress

## Evidence baseline

Stitch project `16486483525927292845` currently contains 22 relevant screens,
covering desktop and mobile dashboard, auth, student profile, registration
rounds, thesis lifecycle, lecturers/admin operations, and notifications. The
canonical reference is the Academic Continuity design system: Be Vietnam Pro,
professional blue, cool near-white surfaces, a 4px spacing rhythm, 4–8px
utility radii, 1280px desktop content cap, and mobile bottom navigation.

The read-only atlas is recorded in
`plans/20260819-java-thesis-strangler-migration/reports/fe-stitch-atlas.md`.

## Implemented candidate work

- Replaced the stale Mastercard design contract with the Stitch contract in
  `frontend/.stitch/DESIGN.md` and `frontend/DESIGN.md`.
- Aligned shared light-mode FE tokens and font stack in
  `frontend/src/app/globals.css`.
- Added `/dashboard/notifications` and its localized route with loading,
  error, empty, all/unread, mark-read, mark-all-read, and responsive states.
- Added mobile bottom navigation for student and lecturer shells, including an
  unread notification badge and safe-area padding.
- Added focused thesis routes for topic catalog, topic detail, progress, and
  defense/evaluation, using only current thesis and Java council read APIs.
- Added bilingual copy and smoke/E2E route coverage for the new surfaces.
- Added a compact admin mobile quick-nav, automatic `label`/control IDs for
  single-control admin fields, and non-truncating mobile dashboard labels.

## Acceptance criteria

- [x] `npm test` passes (20 tests at the current candidate).
- [x] `npm run typecheck` passes.
- [x] `npm run lint` passes with zero warnings.
- [x] `git diff --check` passes.
- [x] Both unprefixed and localized route files exist for notifications and
  thesis lifecycle screens.
- [x] Authenticated browser screenshots cover desktop and 390px mobile layout
  for dashboard, notifications, thesis, profile, registration, lecturer, and
  admin route families; the captured matrix is 56/56 PASS.
- [ ] Admin mobile data views are fully converted from horizontal-scroll-first
  tables to stacked cards/lists where Stitch requires it; current work keeps
  narrow tables usable with bounded scroll and repaired chart overflow.
- [x] The 22-screen Stitch atlas is attached to the candidate, with a larger
  28-route × 2-viewport rendered evidence matrix.
- [ ] Accessibility, authenticated mutation parity, and isolated repeatable
  E2E are still release-blocking follow-up gates.

## Known limitation

The captured visual matrix proves the supplied authenticated runtime stayed on
the expected routes and found no automated layout/overflow failures. It is not
an accessibility audit or a substitute for isolated E2E: the shared Compose
full suite now passes twice with direct auth-service session setup, but its
checkout fixture still mutates the shared database and the isolated runner
needs its local service dependencies installed before it can be accepted.

The independent FE audit remains `INCONCLUSIVE/HOLD`: ten route families still
need capture, the Stitch reference-size image-diff is not implemented, several
data views remain horizontal-scroll-first on mobile, and console/network error
allowlisting plus axe/keyboard coverage are open. The bounded shell/a11y fixes
above pass typecheck and lint but do not close those release gates.
