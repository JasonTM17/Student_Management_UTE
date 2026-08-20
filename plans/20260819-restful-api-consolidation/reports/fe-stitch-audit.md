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
  references. `frontend/.stitch/metadata.json` records the 22 named screens
  without pretending that metadata is a rendered visual diff.
- Existing web capture artifact:
  `plans/20260819-java-thesis-strangler-migration/assets/fe-stitch-visual-qa/summary.json`
  reports 28 routes × 2 viewports, 56/56, zero measured overflow and zero
  missing mobile navigation findings.
- The independent audit identified ten current routes absent from that matrix:
  admin academic-years, classrooms, departments, enrollments, lecturers,
  sections, semesters; lecturer grade detail; dashboard sign-out; and thesis
  topic detail.
- Fresh browser smoke on 2026-08-20: the locally running public landing page
  rendered at 1440px, 390px, and 768px with zero measured horizontal overflow;
  the page console returned no errors. This is a limited runtime observation:
  the prior `campuscore-frontend:local` image has no source-revision label, so
  it is not accepted as exact-current-source, authenticated, or Stitch
  pixel-diff evidence.
- A source-current Next development server was separately checked on
  `127.0.0.1:3011` on 2026-08-20. The public landing page returned HTTP 200 at
  1440px, 390px and 768px; measured document widths (1425, 375 and 753px)
  remained within their viewports, and the browser recorded zero console errors.
  The server was started with `npm run dev -- -p 3011` from exact source commit
  `95b4a181e7ebcae51b8b60a334f208bbb7027147`; tracked files were clean before
  the audit, apart from the preserved untracked `.agents` file. The observation
  used the Codex in-app browser and did not create a screenshot artifact or
  pixel hash. This confirms only the public landing's responsive source
  behavior. It does not cover signed-in routes, API/network success, reference
  pixel comparison, or a production build/image.
- The native scaffold now defines 23 unique registry-to-component bindings
  under `mobile/`; 13 direct mobile Stitch references are checked against the
  shared metadata ledger. The read-only audit snapshot reported zero native
  screens because it ran before this scaffold was completed; current
  source-level evidence does not replace Expo/device evidence.

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
5. The initial mobile bottom bar always exposed student destinations and the
   navigator accepted any screen name. The navigation now selects destinations
   per active preview role and falls back to that role's home screen when an
   unauthorized direct transition is attempted.
6. The initial mobile sign-in preview could look like a successful Java login.
   It now labels the local flow as preview-only and disables live sign-in until
   a real Java auth contract exists and has runtime evidence.
7. Wukong's exact-head review found that route presence had been conflated with
   signed-in state. The navigator now models `signedOut`, `preview`, and
   `authenticated` separately; preview role switching is explicitly limited to
   the preview state. This repairs the source-level ambiguity, but it is not
   server authorization or authenticated runtime parity.

## Remaining P0/P1 gates

- Exact-source browser/Playwright visual rerun remains `NOT_RUN`; the public
  landing-page-only browser smoke does not cover authenticated routes or a
  Stitch reference diff.
- The visual QA harness has been tightened to capture the ten previously
  missing route families at desktop, 390px, and 768px tablet widths, including
  sign-out and discovered topic/grading detail routes. A fresh browser capture
  is still `NOT_RUN`, so this is verifier-source progress rather than visual
  acceptance.
- Add normalized/reference-size Stitch diff or an explicitly reviewed visual
  comparison; the current matrix only checks structure and viewport metrics.
- Gate console/network errors instead of recording 401/400 responses as a
  passing visual run. The harness now fails noisy captures; the rerun must
  prove the current app is clean.
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
