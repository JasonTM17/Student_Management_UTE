# Stitch/FE audit report — 2026-08-19

## Verdict

`HOLD` for visual acceptance. The repository has substantial web coverage and
the Stitch atlas is healthy, but the prior matrix is structural evidence rather
than reference-diff evidence and several current routes/states are not covered.

## Inventory

- Frontend route files: 76 `page.tsx` files, representing 38 logical route
  templates in unlocalized and `[locale]` variants.
- Stitch project `16486483525927292845`: 23 records, 9 desktop, 13 mobile,
  plus one supplementary image; 22 records expose HTML and screenshot
  references.
- Existing web capture artifact:
  `plans/20260819-java-thesis-strangler-migration/assets/fe-stitch-visual-qa/summary.json`
  reports 28 routes × 2 viewports, 56/56, zero measured overflow and zero
  missing mobile navigation findings.
- The independent audit identified ten current routes absent from that matrix:
  admin academic-years, classrooms, departments, enrollments, lecturers,
  sections, semesters; lecturer grade detail; dashboard sign-out; and thesis
  topic detail.
- The native scaffold now defines 23 navigable screens under `mobile/`.
  The read-only audit snapshot reported zero native screens because it ran
  before this scaffold was completed; current source presence does not replace
  Expo/device evidence.

## Findings and repairs

1. Dashboard current-term values could collapse into one character per line.
   `WorkspaceMetricCard` now uses normal word wrapping and the semester card
   receives a smaller responsive value style.
2. The assistant and web bottom navigation previously shared `z-40` and the
   assistant sat at the viewport bottom. The assistant now uses a mobile-safe
   bottom offset and `z-50`; the bottom bar is limited to `md` and below.
3. The web canvas now uses the exact Stitch background HSL equivalent of
   `#F9F9FF`, and major admin/workspace surfaces use a 1280px cap.
4. The 768–1023px tablet boundary now receives the fluid content layout without
   reserving mobile bottom-bar space; the hamburger remains available.

## Remaining P0/P1 gates

- Fresh browser/Playwright visual rerun is `NOT_RUN` in this environment.
- Capture all 38 route families at desktop, 390px, and 768–1023px widths,
  including both locales and the ten missing routes.
- Add normalized/reference-size Stitch diff or an explicitly reviewed visual
  comparison; the current matrix only checks structure and viewport metrics.
- Gate console/network errors instead of recording 401/400 responses as a
  passing visual run.
- Exercise loading, empty, error, permission, long-text, keyboard, and
  accessibility states.
- Install mobile dependencies in a bounded environment, run Expo typecheck,
  and smoke the primary role flows on an emulator/device.

## API boundary

The API audit confirms that the web still calls legacy `/api/v1` NestJS domain
owners. The Java app currently exposes health/readiness, shell contract probes,
an identity probe, and an opt-in thesis-topic read candidate. It does not yet
implement the frontend's auth, academic, enrollment/grades, finance,
engagement, notification, analytics, complete thesis, or chatbot contracts.
Therefore this phase preserves the existing client route owner and records
Java convergence as a candidate, not a cutover.
