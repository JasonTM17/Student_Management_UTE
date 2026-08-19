# Frontend to Java REST API audit — 2026-08-19

## Verdict

`HOLD` for client cutover. The web and native clients can share the target
`/api/v1` base path, but the current Java app is a shell plus one conditional
thesis-topic read candidate. The legacy NestJS service mesh remains the
canonical route and writer owner.

## Current transport evidence

- `frontend/src/lib/api.ts` defaults to `/api/v1` and automatically retries
  401 responses through `/auth/refresh`.
- `frontend/src/app/api/v1/[...path]/route.ts` is a path-transparent proxy;
  it does not adapt payloads or response shapes.
- `frontend/src/lib/local-edge-proxy.ts` defaults local forwarding to
  `127.0.0.1:8080`.
- `frontend/next.config.mjs` also retains health, docs, notifications, and
  Socket.IO rewrites.
- `mobile/src/api/client.ts` uses one bearer-oriented base URL and the stable
  Java error fields `code`, `message`, and request id when available. It now
  defaults to `preview` and fails closed with `MOBILE_API_PREVIEW`; live calls
  require explicit `EXPO_PUBLIC_API_MODE=live`. The mobile sign-in UI labels
  local preview data explicitly and disables live sign-in until Java implements
  the auth contract, so navigation cannot be mistaken for authenticated parity.
  The navigator separately models `signedOut`, `preview`, and
  `authenticated`; the current candidate can enter only the preview state.

## Route status

### Java implemented or candidate

| Route | Status | Evidence boundary |
| --- | --- | --- |
| `/api/v1/health/liveness` | Implemented in source | Spring controller and contract tests; no deployed runtime claim. |
| `/api/v1/health/readiness` | Implemented in source | Shared readiness key and tests. |
| `/api/v1/contract`, `/api/v1/contract/ping` | Temporary shell probes | Not a domain contract. |
| `/api/v1/me` | Identity probe | Shape differs from frontend `User` and `/auth/me`. |
| `/api/v1/thesis/topics` | Opt-in read candidate | Requires `persistence` and `THESIS_READ_ENABLED`; unknown round now preserves legacy `404`; no public route switch. |

### Missing for current frontend behavior

- Auth login, refresh, logout, profile, registration and password/email flows.
- Academic sections, semesters, departments, courses, classrooms, academic
  years, users, lecturers, enrollment, grades, transcript and exports.
- Finance invoices, checkout, idempotency, provider callbacks/webhooks,
  payment-intent polling, admin writes, exports and reconciliation.
- Engagement announcements; notification REST reads and mutations; legacy
  Socket.IO realtime behavior.
- Analytics dashboards and cockpit routes.
- Thesis rounds, groups, councils, mutations, review/result flows, and the
  server-side assistant/chatbot contract.

The concrete frontend call sites are in `frontend/src/lib/api.ts` and
`frontend/src/lib/thesis-api.ts`; legacy owners are under `auth-service`,
`academic-service`, `finance-service`, `engagement-service`,
`notification-service`, `analytics-service`, and `java-services/thesis-service`.

## Required convergence gates

1. Freeze method, path, query/body, response, status, headers, cookies, and
   role requirements for every frontend call.
2. Add Java controller contracts before changing edge ownership; preserve
   `/api/v1` initially.
3. Match auth refresh/revocation, JWT claims, cookies, bearer, CSRF and
   negative cases.
4. Add differential fixtures against legacy for arrays vs `{data, meta}`
   shapes, pagination, validation, finance checkout, thesis DTOs and
   notifications.
5. Treat notification polling and Socket.IO as separate migration decisions;
   do not silently delete realtime behavior.
6. Treat finance provider handoff, callbacks, webhooks, idempotency and
   reconciliation as security/reliability gates, not CRUD endpoints.
7. Rehearse PostgreSQL migration, backup/restore, row/hash reconciliation,
   writer ownership, edge route switch, observation window and rollback.
8. Run authenticated web/mobile E2E against the Java route owner only after
   the above gates pass.

Until then, label Java routes `candidate` or `unverified`; do not redirect
either client to the incomplete app and do not retire legacy services.
