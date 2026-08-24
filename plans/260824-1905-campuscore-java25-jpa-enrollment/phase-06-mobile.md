---
title: "Phase 5: Mobile JSON parity"
status: pending
---

# Phase 5: Mobile JSON parity

## Objective

Use the same registration and assistant contract in Expo without duplicating
business rules. Preserve ambiguous network operations and make offline and
session states explicit.

## Owned paths

`mobile/src` route registry, typed API client, registration/assistant screens,
localization/tokens, and mobile tests.

## Exit criterion

Round/semester/section/selected/slip screens support idempotent enroll/drop,
retry with the same key, reconcile controls, conflict/prerequisite/credit
reason cards, PDF open/share, 401/403/offline handling, English/Vietnamese
copy, 44px targets and safe-area navigation. Unit tests/typecheck pass; a
simulator/device result is recorded honestly as PASS, NOT_RUN, or BLOCKED.
