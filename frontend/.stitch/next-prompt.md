# Stitch baton — next integration pass

## Current handoff

The canonical Stitch project is `16486483525927292845`. The design system is
already synthesized in `DESIGN.md`; the atlas currently contains 23 reference
records (9 desktop, 13 mobile, 1 supplementary image). The web shell has
existing route, smoke, typecheck, lint, and historical 56/56 visual evidence.

## Next bounded pass

1. Keep the explicit mobile Stitch-reference mapping current for dashboard,
   registration, thesis, evaluation, notifications, profile, sign-in, and
   lecturer operations. Each new direct reference must include its screen ID
   and a regression assertion; do not mark an inferred screen as direct visual
   parity.
2. Keep web and native clients on the same `/api/v1` Java RESTful API seam;
   record any endpoint not yet implemented as a contract blocker, not fake
   client success.
3. Preserve role-aware navigation: a preview role must not navigate to a route
   outside its registry authorization. Continue to exercise loading, empty,
   error, permission, and long-text states for each touched web component and
   preserve 44px touch targets and bottom navigation.
4. Run the available web tests/typecheck/lint. Run Expo typecheck only after
   dependencies are intentionally provisioned; do not install while C: is in
   the current low-space state.
5. Request a fresh browser/Playwright visual capture before changing the visual
   acceptance status. The previous in-app browser attempt is `NOT_RUN`.

## Guardrails

- Use existing Stitch references and local assets first; no blind generation.
- Keep edits disjoint by area: `frontend/.stitch/`, `frontend/src/`, or
  `mobile/`.
- Do not change nginx, Compose, Kubernetes, or legacy route ownership during a
  Stitch polish pass.
- Before handoff, report exact files, tests, visual evidence, runtime limits,
  and the next unresolved gap.
