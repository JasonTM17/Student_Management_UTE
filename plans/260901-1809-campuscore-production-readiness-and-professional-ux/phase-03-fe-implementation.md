# Phase 03 — Professional web implementation

Refactor the shared token/layout/component layer first, then update public/auth pages, portal shell, dashboards, tables/forms/dialogs, admin screens and assistant panel. Preserve existing behavior and API semantics. Replace raw model names, enums and reason codes with bilingual product copy. Keep the assistant launcher branded and accessible, and expose citations/fallback states without exposing provider internals.

Add axe Playwright coverage, keyboard journeys, visual baselines and console/network assertions. Exit criterion: all critical routes render without overflow or hydration errors and the FE tester wave passes.
