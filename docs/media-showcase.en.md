# Current application screenshots and GIFs

[English](media-showcase.en.md) · [Tiếng Việt](media-showcase.vi.md) · [Technical guide](technical/README.en.md)

## Capture provenance

Screens were rendered from a new 27 October 2026 source build in the isolated `campuscore-docs` Compose project with fresh seed data. The UI displays CampusUTE branding; the codebase is CampusCore.

- Source: `fb9b60af3b0ad2f854047036c1cc5bb19466ead3`
- Next build: `N4g1GZ8EeONCvY1oewd5t`
- API image: `sha256:af7b5525ef900b4ecd47c55020c2186ac5747ed750015b366a184edb4bd6b661`
- Four roles actually signed in; `/api/v1/auth/me` returned 200 with the expected identity.
- 1440×1000 PNGs; 1200px-wide GIF screenshot sequences hold each view for approximately four seconds; not continuous action recordings.
- External DeepSeek and SMTP dispatch were disabled; rate limits stayed enabled.

[Full manifest and checksums](assets/showcase-2026-10/provenance.json) · [Reproduction](technical/operations-verification.en.md)

## Observed limitations

One seeded group report metadata request returned 404 in each locale. These captures establish no upload, download or recovery acceptance. Appeals and curriculum-batch histories may be empty because no records were submitted. Reconciliation warned about seeded seat-count drift; no fixture repair was performed for screenshots. The specialized assistant received a technical-corpus prompt with external providers off.

Media establishes rendered views and manifest-recorded requests, not every mutation, delivered email, complete RLS/race verification, real-device mobile execution or production. A dependency npm audit finding is recorded in the manifest and was not repaired by this docs update.

The existing lecturer mail recipient summary still displays the literal `{max}` placeholder. Screens preserve this copy defect; this update does not change product code.

## GIF

| Journey | Animation |
|---|---|
| Student | [student-tour-en.gif](assets/showcase-2026-10/student-tour-en.gif) |
| Lecturer / Admin | [staff-tour-en.gif](assets/showcase-2026-10/staff-tour-en.gif) |
| Thesis governance | [thesis-governance-tour-en.gif](assets/showcase-2026-10/thesis-governance-tour-en.gif) |
| VI / EN | [language-tour.gif](assets/showcase-2026-10/language-tour.gif) |

## Complete screen gallery

| Screen | Tiếng Việt | English |
|---|---|---|
| admin announcements | [PNG](assets/showcase-2026-10/admin-announcements-vi.png) | [PNG](assets/showcase-2026-10/admin-announcements-en.png) |
| admin appeals | [PNG](assets/showcase-2026-10/admin-appeals-vi.png) | [PNG](assets/showcase-2026-10/admin-appeals-en.png) |
| admin audit | [PNG](assets/showcase-2026-10/admin-audit-vi.png) | [PNG](assets/showcase-2026-10/admin-audit-en.png) |
| admin curriculum defaults | [PNG](assets/showcase-2026-10/admin-curriculum-defaults-vi.png) | [PNG](assets/showcase-2026-10/admin-curriculum-defaults-en.png) |
| admin dashboard | [PNG](assets/showcase-2026-10/admin-dashboard-vi.png) | [PNG](assets/showcase-2026-10/admin-dashboard-en.png) |
| admin knowledge | [PNG](assets/showcase-2026-10/admin-knowledge-vi.png) | [PNG](assets/showcase-2026-10/admin-knowledge-en.png) |
| admin sections | [PNG](assets/showcase-2026-10/admin-sections-vi.png) | [PNG](assets/showcase-2026-10/admin-sections-en.png) |
| admin thesis | [PNG](assets/showcase-2026-10/admin-thesis-vi.png) | [PNG](assets/showcase-2026-10/admin-thesis-en.png) |
| admin users | [PNG](assets/showcase-2026-10/admin-users-vi.png) | [PNG](assets/showcase-2026-10/admin-users-en.png) |
| dean dashboard | [PNG](assets/showcase-2026-10/dean-dashboard-vi.png) | [PNG](assets/showcase-2026-10/dean-dashboard-en.png) |
| dean thesis | [PNG](assets/showcase-2026-10/dean-thesis-vi.png) | [PNG](assets/showcase-2026-10/dean-thesis-en.png) |
| lecturer announcements | [PNG](assets/showcase-2026-10/lecturer-announcements-vi.png) | [PNG](assets/showcase-2026-10/lecturer-announcements-en.png) |
| lecturer attendance | [PNG](assets/showcase-2026-10/lecturer-attendance-vi.png) | [PNG](assets/showcase-2026-10/lecturer-attendance-en.png) |
| lecturer dashboard | [PNG](assets/showcase-2026-10/lecturer-dashboard-vi.png) | [PNG](assets/showcase-2026-10/lecturer-dashboard-en.png) |
| lecturer grades | [PNG](assets/showcase-2026-10/lecturer-grades-vi.png) | [PNG](assets/showcase-2026-10/lecturer-grades-en.png) |
| lecturer mail | [PNG](assets/showcase-2026-10/lecturer-mail-vi.png) | [PNG](assets/showcase-2026-10/lecturer-mail-en.png) |
| lecturer schedule | [PNG](assets/showcase-2026-10/lecturer-schedule-vi.png) | [PNG](assets/showcase-2026-10/lecturer-schedule-en.png) |
| lecturer thesis | [PNG](assets/showcase-2026-10/lecturer-thesis-vi.png) | [PNG](assets/showcase-2026-10/lecturer-thesis-en.png) |
| public home | [PNG](assets/showcase-2026-10/public-home-vi.png) | [PNG](assets/showcase-2026-10/public-home-en.png) |
| public login | [PNG](assets/showcase-2026-10/public-login-vi.png) | [PNG](assets/showcase-2026-10/public-login-en.png) |
| public recovery | [PNG](assets/showcase-2026-10/public-recovery-vi.png) | [PNG](assets/showcase-2026-10/public-recovery-en.png) |
| student assistant | [PNG](assets/showcase-2026-10/student-assistant-vi.png) | [PNG](assets/showcase-2026-10/student-assistant-en.png) |
| student assistant answer | [PNG](assets/showcase-2026-10/student-assistant-answer-vi.png) | [PNG](assets/showcase-2026-10/student-assistant-answer-en.png) |
| student attendance | [PNG](assets/showcase-2026-10/student-attendance-vi.png) | [PNG](assets/showcase-2026-10/student-attendance-en.png) |
| student conduct | [PNG](assets/showcase-2026-10/student-conduct-vi.png) | [PNG](assets/showcase-2026-10/student-conduct-en.png) |
| student curriculum | [PNG](assets/showcase-2026-10/student-curriculum-vi.png) | [PNG](assets/showcase-2026-10/student-curriculum-en.png) |
| student dashboard | [PNG](assets/showcase-2026-10/student-dashboard-vi.png) | [PNG](assets/showcase-2026-10/student-dashboard-en.png) |
| student enrollments | [PNG](assets/showcase-2026-10/student-enrollments-vi.png) | [PNG](assets/showcase-2026-10/student-enrollments-en.png) |
| student grades | [PNG](assets/showcase-2026-10/student-grades-vi.png) | [PNG](assets/showcase-2026-10/student-grades-en.png) |
| student notifications | [PNG](assets/showcase-2026-10/student-notifications-vi.png) | [PNG](assets/showcase-2026-10/student-notifications-en.png) |
| student registration | [PNG](assets/showcase-2026-10/student-registration-vi.png) | [PNG](assets/showcase-2026-10/student-registration-en.png) |
| student schedule | [PNG](assets/showcase-2026-10/student-schedule-vi.png) | [PNG](assets/showcase-2026-10/student-schedule-en.png) |
| student thesis | [PNG](assets/showcase-2026-10/student-thesis-vi.png) | [PNG](assets/showcase-2026-10/student-thesis-en.png) |
| student transcript | [PNG](assets/showcase-2026-10/student-transcript-vi.png) | [PNG](assets/showcase-2026-10/student-transcript-en.png) |

## Current diagrams

![Architecture](assets/showcase-2026-10/architecture-en.png)

[SVG](assets/showcase-2026-10/architecture-en.svg)
