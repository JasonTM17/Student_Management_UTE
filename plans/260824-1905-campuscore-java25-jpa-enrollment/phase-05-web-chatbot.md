---
title: "Phase 4: Web registration, admin and chatbot workspace"
status: pending
---

# Phase 4: Web registration, admin and chatbot workspace

## Objective

Deliver a professional institutional-blue registration workspace, admin round
operations, and an accessible assistant launcher/panel without coupling UI
state to unverified backend assumptions.

## Owned paths

`frontend` registration/admin/assistant components, typed API/SSE clients,
styles/tokens, and browser/unit tests. Shared API contract changes must be
coordinated with the integration owner.

## UX/state requirements

Use server-time countdown, eligibility/priority/credit summary, deterministic
search/filter, all schedule slots, selected-course tray, conflict drawer,
shared confirmation dialog, explicit loading/empty/error/forbidden/session and
stale states, and post-mutation reconciliation. The 56px assistant launcher
must remain visible, labeled, keyboard reachable, safe-area aware, and
contrast-compliant; panel uses `100dvh`, validated SSE event order, feedback,
privacy warning, stop/cancel, replace fallback, and paused autoscroll.

## Exit criterion

Frontend tests/typecheck/lint/build pass; authenticated Playwright flows at
390/768/1440px cover registration, delete/drop, feedback, assistant fallback,
focus, reduced motion and session expiry; no launcher or panel overlaps the
navigation shell.
