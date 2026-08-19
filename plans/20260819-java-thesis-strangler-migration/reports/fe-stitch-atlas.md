# FE Stitch atlas and parity review

Date: 2026-08-19
Project: `16486483525927292845`
Review mode: read-only Stitch metadata, repository inspection, and Playwright visual capture

## Screen families

| Family | Stitch coverage | Repository target | Current disposition |
| --- | ---: | --- | --- |
| Dashboard/home | 3 | `frontend/src/app/dashboard/page.tsx`, shell layout | Captured at desktop and 390px mobile; authenticated route stayed in session |
| Authentication | 1 | `frontend/src/app/login/page.tsx` | Captured at desktop and 390px mobile; public/auth shell checks pass |
| Student profile | 2 | `frontend/src/app/dashboard/profile/page.tsx` | Captured in the authenticated student matrix; accessibility parity remains a separate gate |
| Registration rounds | 3 | dashboard registration/admin semester surfaces | Captured in the authenticated matrix; dense admin/table semantics remain under review |
| Thesis lifecycle | 8 | dashboard thesis plus `topics`, `topics/[id]`, `progress`, `evaluation` | Decomposed candidate routes; council read path added |
| Lecturer/admin operations | 3 | admin and lecturer routes | Captured at desktop and mobile; table/card interaction parity remains a separate gate |
| Notifications | 2 | dashboard notifications and localized route | Implemented with API-backed inbox states |

Total Stitch screen count: 22 relevant screens (web and mobile combined).

## Exact design contract

- Font: Be Vietnam Pro.
- Primary: `#003F87`; primary container: `#0056B3`.
- Surface: `#F9F9FF`; white content surface: `#FFFFFF`.
- Text: `#191C21` and variant `#424752`.
- Outline: `#C2C6D4` with stronger `#727784`.
- Baseline spacing: 4px; common layout steps 8/16/24/32px.
- Utility radius: 4–8px; status badges use pill radius.
- Desktop max content width: 1280px; mobile gutter: 16px.
- Mobile navigation: bottom bar for frequent actions; complete menu remains
  available from the menu button.

## Findings and status

1. Notifications was previously only a shell popover/proxy route. It is now a
   real bilingual page with all/unread filtering and read mutations.
2. Mobile navigation previously used only an off-canvas sidebar and student
   rail. A four-item bottom bar now covers home, thesis, notifications, and
   profile for both student and lecturer roles.
3. Thesis was previously compressed into one workspace route. Topic catalog,
   topic detail, progress, and defense/evaluation routes now provide the
   screen-level decomposition Stitch describes.
4. Java thesis service exposes council reads but not review/result reads. The
   evaluation screen therefore shows council scheduling and readiness only;
   it does not display invented grades.
5. The authenticated visual matrix is now captured: 28 routes × 2 viewports =
   56 screenshots, with zero automated capture failures, zero horizontal
   overflow findings, and zero missing mobile bottom-nav findings. This proves
   the checked runtime capture only; it does not prove accessibility, every
   business mutation, production observability, or release safety.

## Verification record

- `frontend`: `npm test` — PASS, 24/24.
- `frontend`: `npm run typecheck` — PASS.
- `frontend`: `npm run lint` — PASS, zero warnings.
- Latest bounded a11y patch: explicit `htmlFor`/`id` associations for
  forgot-password, reset-password, and profile controls; typecheck and lint
  PASS after the patch. Browser and E2E reruns remain open.
- Latest bounded responsive patch: Admin Users now renders stacked mobile cards
  and retains the desktop table; smoke/typecheck/lint PASS after the patch.
- Admin Courses and Admin Classrooms now also render stacked mobile cards with
  desktop-table fallbacks; smoke/typecheck/lint PASS after the patch.
- Admin Departments, Semesters, and Lecturers now also render stacked mobile
  cards with desktop-table fallbacks; smoke/typecheck/lint PASS after the patch.
- `frontend`: `git diff --check` — PASS.
- `frontend`: `npm run build` — PASS.
- `java-services`: `mvn -q verify` — PASS; H2/Spring tests ran, with normal Mockito/Byte Buddy warnings.
- Browser visual QA: `assets/fe-stitch-visual-qa/summary.json` records 56/56 PASS at 1280×900 and 390×844, zero overflow, and zero missing mobile navigation findings.
- External Playwright: full suite — 20/20 PASS twice with explicit direct auth-service session setup; edge auth/CSRF/security tests still exercise nginx. The shared Compose database and mutating checkout fixture remain outside isolation proof.
- Isolated E2E runner: `npm run test:e2e` — NOT RUN to completion because the local `backend` workspace has no installed Prisma CLI (`backend/node_modules/.bin/prisma` missing); the runner's temporary database cleanup path ran.

## Release interpretation

The visual evidence is strong enough to accept the FE candidate as a Stitch-aligned implementation snapshot, not as a production-ready release claim. The external suite is repeatable under the documented direct-auth setup, but release acceptance remains HOLD until an isolated database run covers the mutating checkout fixture and the Java deploy/cutover gates are proven.
