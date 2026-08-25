# Phase 4 - Web registration, admin and chatbot evidence

## Delivered

- HCMUTE-style registration workspace routes remain available at
  `/dashboard/registration` with `/dashboard/register` compatibility routing.
- Responsive round banner, server-time/eligibility summary, search/filter
  controls, dense desktop table, mobile cards, selected-course tray,
  validation/conflict drawer, confirmation dialog, pending/error/forbidden/
  session-expired states and PDF-slip action are wired to the Java API.
- The authenticated assistant launcher is a 56px accessible fixed control with
  status cue, safe-area offset and reduced-motion behavior. The panel uses
  `100dvh`, owner-scoped history, citations, privacy warning, feedback and
  server cancellation. SSE events are parsed/reduced with sequence and
  terminal-state checks; malformed/fallback output is represented explicitly.
- Admin knowledge and registration-round surfaces are present, including
  different-admin publication/archive controls and read-only catalog coverage.
- A disposable `docker-compose.e2e.yml` and guarded course E2E runner use a
  unique Compose project and only remove their own generated resources.

## Evidence

| Gate | Result |
|---|---|
| `npm test --prefix frontend` | PASS, 24/24 |
| `npm run typecheck --prefix frontend` | PASS |
| `npm run lint --prefix frontend` | PASS, zero warnings |
| `npm run build --prefix frontend` | PASS, Next.js 15.5.23; registration/admin/assistant routes generated |
| `docker compose -p campuscore-course-e2e-config-check -f docker-compose.e2e.yml config --quiet` | PASS |

## Honest limits

The authenticated Playwright suite is now **PASS (5/5 scenarios)** with the
installed Chrome runtime: assistant stream/citation/feedback, admin knowledge,
responsive launcher/panel, registration → Mailpit verify/reset → fresh login,
and 390/768/1440 auth route/keyboard/locale/no-referrer coverage. Device/native,
remote CI, live DeepSeek and production evidence remain separate and are not
claimed. A standalone third-party accessibility audit is also not claimed;
the browser suite covers the implemented keyboard/focus/landmark assertions.

## Exit ruling

Static/component and authenticated browser gates are **PASS** for the bounded
web contract. Independent native-device and third-party accessibility review
remain explicit follow-up evidence, not hidden release claims.
