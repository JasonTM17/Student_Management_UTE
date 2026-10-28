# Current application screenshots and GIFs

[English](media-showcase.en.md) · [Tiếng Việt](media-showcase.vi.md) · [Technical guide](technical/README.en.md)

## Capture provenance

Screens were rendered from a local build of the current source in the isolated `campuscore-docs` Compose project with fresh seed data. The UI displays CampusUTE branding; the codebase is CampusCore.

- Source: `fb9b60af3b0ad2f854047036c1cc5bb19466ead3`
- Next build: `N4g1GZ8EeONCvY1oewd5t`
- API image: `sha256:af7b5525ef900b4ecd47c55020c2186ac5747ed750015b366a184edb4bd6b661`
- Four roles actually signed in; `/api/v1/auth/me` returned 200 with the expected identity.
- 1440×1000 PNGs; 1200px-wide GIF screenshot sequences hold each view for approximately four seconds; not continuous action recordings.
- External DeepSeek and SMTP dispatch were disabled; rate limits stayed enabled.

[Full manifest and checksums](assets/showcase/provenance.json) · [Reproduction](technical/operations-verification.en.md)

The manifest hashes match all 77 binary assets. Its two SVG hashes/sizes describe
the original Windows CRLF text; Git-normalized LF SVG blobs differ byte-for-byte.
Reconstructing CRLF matches those recorded hashes; this is line-ending provenance,
not a fresh screenshot capture.

## Observed limitations

One seeded group report metadata request returned 404 in each locale. These captures establish no upload, download or recovery acceptance. Appeals and curriculum-batch histories may be empty because no records were submitted. Reconciliation warned about seeded seat-count drift; no fixture repair was performed for screenshots. The specialized assistant received a technical-corpus prompt with external providers off.

Media establishes rendered views and manifest-recorded requests, not every mutation, delivered email, complete RLS/race verification, real-device mobile execution or production. A dependency npm audit finding is recorded in the manifest and was not repaired by this docs update.

The existing lecturer mail recipient summary still displays the literal `{max}` placeholder. Screens preserve this copy defect; this update does not change product code.

## GIF

| Journey | Animation |
|---|---|
| Student | [student-tour-en.gif](assets/showcase/student-tour-en.gif) |
| Lecturer / Admin | [staff-tour-en.gif](assets/showcase/staff-tour-en.gif) |
| Thesis governance | [thesis-governance-tour-en.gif](assets/showcase/thesis-governance-tour-en.gif) |
| VI / EN | [language-tour.gif](assets/showcase/language-tour.gif) |

## Complete screen gallery

| Screen | Tiếng Việt | English |
|---|---|---|
| admin announcements | [PNG](assets/showcase/admin-announcements-vi.png) | [PNG](assets/showcase/admin-announcements-en.png) |
| admin appeals | [PNG](assets/showcase/admin-appeals-vi.png) | [PNG](assets/showcase/admin-appeals-en.png) |
| admin audit | [PNG](assets/showcase/admin-audit-vi.png) | [PNG](assets/showcase/admin-audit-en.png) |
| admin curriculum defaults | [PNG](assets/showcase/admin-curriculum-defaults-vi.png) | [PNG](assets/showcase/admin-curriculum-defaults-en.png) |
| admin dashboard | [PNG](assets/showcase/admin-dashboard-vi.png) | [PNG](assets/showcase/admin-dashboard-en.png) |
| admin knowledge | [PNG](assets/showcase/admin-knowledge-vi.png) | [PNG](assets/showcase/admin-knowledge-en.png) |
| admin sections | [PNG](assets/showcase/admin-sections-vi.png) | [PNG](assets/showcase/admin-sections-en.png) |
| admin thesis | [PNG](assets/showcase/admin-thesis-vi.png) | [PNG](assets/showcase/admin-thesis-en.png) |
| admin users | [PNG](assets/showcase/admin-users-vi.png) | [PNG](assets/showcase/admin-users-en.png) |
| dean dashboard | [PNG](assets/showcase/dean-dashboard-vi.png) | [PNG](assets/showcase/dean-dashboard-en.png) |
| dean thesis | [PNG](assets/showcase/dean-thesis-vi.png) | [PNG](assets/showcase/dean-thesis-en.png) |
| lecturer announcements | [PNG](assets/showcase/lecturer-announcements-vi.png) | [PNG](assets/showcase/lecturer-announcements-en.png) |
| lecturer attendance | [PNG](assets/showcase/lecturer-attendance-vi.png) | [PNG](assets/showcase/lecturer-attendance-en.png) |
| lecturer dashboard | [PNG](assets/showcase/lecturer-dashboard-vi.png) | [PNG](assets/showcase/lecturer-dashboard-en.png) |
| lecturer grades | [PNG](assets/showcase/lecturer-grades-vi.png) | [PNG](assets/showcase/lecturer-grades-en.png) |
| lecturer mail | [PNG](assets/showcase/lecturer-mail-vi.png) | [PNG](assets/showcase/lecturer-mail-en.png) |
| lecturer schedule | [PNG](assets/showcase/lecturer-schedule-vi.png) | [PNG](assets/showcase/lecturer-schedule-en.png) |
| lecturer thesis | [PNG](assets/showcase/lecturer-thesis-vi.png) | [PNG](assets/showcase/lecturer-thesis-en.png) |
| public home | [PNG](assets/showcase/public-home-vi.png) | [PNG](assets/showcase/public-home-en.png) |
| public login | [PNG](assets/showcase/public-login-vi.png) | [PNG](assets/showcase/public-login-en.png) |
| public recovery | [PNG](assets/showcase/public-recovery-vi.png) | [PNG](assets/showcase/public-recovery-en.png) |
| student assistant | [PNG](assets/showcase/student-assistant-vi.png) | [PNG](assets/showcase/student-assistant-en.png) |
| student assistant answer | [PNG](assets/showcase/student-assistant-answer-vi.png) | [PNG](assets/showcase/student-assistant-answer-en.png) |
| student attendance | [PNG](assets/showcase/student-attendance-vi.png) | [PNG](assets/showcase/student-attendance-en.png) |
| student conduct | [PNG](assets/showcase/student-conduct-vi.png) | [PNG](assets/showcase/student-conduct-en.png) |
| student curriculum | [PNG](assets/showcase/student-curriculum-vi.png) | [PNG](assets/showcase/student-curriculum-en.png) |
| student dashboard | [PNG](assets/showcase/student-dashboard-vi.png) | [PNG](assets/showcase/student-dashboard-en.png) |
| student enrollments | [PNG](assets/showcase/student-enrollments-vi.png) | [PNG](assets/showcase/student-enrollments-en.png) |
| student grades | [PNG](assets/showcase/student-grades-vi.png) | [PNG](assets/showcase/student-grades-en.png) |
| student notifications | [PNG](assets/showcase/student-notifications-vi.png) | [PNG](assets/showcase/student-notifications-en.png) |
| student registration | [PNG](assets/showcase/student-registration-vi.png) | [PNG](assets/showcase/student-registration-en.png) |
| student schedule | [PNG](assets/showcase/student-schedule-vi.png) | [PNG](assets/showcase/student-schedule-en.png) |
| student thesis | [PNG](assets/showcase/student-thesis-vi.png) | [PNG](assets/showcase/student-thesis-en.png) |
| student transcript | [PNG](assets/showcase/student-transcript-vi.png) | [PNG](assets/showcase/student-transcript-en.png) |

## Current diagrams

The architecture diagrams are Mermaid; see the [README](../README.en.md#architecture) and [ARCHITECTURE.md](ARCHITECTURE.md).
