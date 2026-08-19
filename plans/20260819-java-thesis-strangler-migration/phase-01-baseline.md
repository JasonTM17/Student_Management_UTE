# Phase 01 - Baseline Freeze

Goal:

Capture the exact current state before any cutover work, so later review can prove what changed and why.

Checklist:

- Freeze branch and head.
- Preserve `.agents/` and any unrelated dirty state.
- Record the Java module map and the frontend route map.
- Record the Stitch project metadata and the design-system delta.
- Capture current verification results:
  - frontend smoke
  - frontend typecheck
  - frontend lint status
  - Java test status

Done when:

- The repo snapshot is explicitly documented.
- The FE/Stitch mismatch is written down.
- The Java pilot boundary is identified.
