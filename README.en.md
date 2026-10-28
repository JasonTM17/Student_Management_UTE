# CampusCore — CampusUTE academic portal

**A Vietnamese–English academic system:** course registration, grading, attendance, projects/theses, academic communication and a source-grounded AI assistant.

[Tiếng Việt](README.vi.md) · [English](README.en.md) · [Complete technical guide](docs/technical/README.en.md) · [Current screenshots/GIFs](docs/media-showcase.en.md)

![Java 21](https://img.shields.io/badge/Java-21-orange?logo=openjdk)
![Spring Boot 4.1.2](https://img.shields.io/badge/Spring_Boot-4.1.2-green?logo=springboot)
![Next.js 15](https://img.shields.io/badge/Next.js-15-black?logo=nextdotjs)
![PostgreSQL 15](https://img.shields.io/badge/PostgreSQL-15-blue?logo=postgresql)

CampusCore is the project/codebase name; the UI currently displays **CampusUTE**. Its backend is a domain-organized **Spring Boot modular monolith**, with a Next.js portal and PostgreSQL/Flyway. RAG runs as an internal sidecar. The Expo mobile client is under development and shares Java's API.

## Contents

- [Overview and roles](#overview)
- [Screenshots and GIFs](#showcase)
- [Whole-project techniques](#techniques)
- [Architecture](#architecture)
- [Technology](#stack)
- [Run locally](#quick-start)
- [Demo accounts](#demo)
- [Verification and evidence boundaries](#verification)
- [Related documentation](#docs)

<a id="overview"></a>
## Overview and roles

| Role | Main workflows | Boundary |
|---|---|---|
| Student | Registration/drop, curriculum, schedules, grades, appeals, attendance, conduct, thesis groups | Own records and explicitly authorized resources |
| Lecturer | Teaching schedule, rosters, grades, attendance, announcements/email, thesis proposals/supervision/grading | Actual sections/topics/assignments |
| Department / faculty head | Academic review/governance, progress and workload | Department/faculty plus legitimate academic assignments |
| Admin / Super Admin | Accounts, catalogs, sections, rounds, appeals, audit, RAG knowledge | Corresponding administrative authority and business guards |

The **academic office issues accounts**; public self-registration is unavailable. Visible login tabs are Student, Lecturer and Dean. Administrative entry uses role-based routing/deep links. Menu visibility does not replace service/database authorization.

<a id="showcase"></a>
## Screenshots and GIFs

Captured from a **new local build of the 27 October 2026 source**, using a separate PostgreSQL/demo environment without production data. Every role signs in and checks identity through the API. [Complete gallery, provenance and limitations](docs/media-showcase.en.md) · [Runtime manifest](docs/assets/showcase-2026-10/provenance.json).

### Public pages and login

| Vietnamese homepage | English homepage |
|---|---|
| ![Vietnamese homepage](docs/assets/showcase-2026-10/public-home-vi.png) | ![English homepage](docs/assets/showcase-2026-10/public-home-en.png) |

| Current login portals | Account recovery guidance |
|---|---|
| ![Current login](docs/assets/showcase-2026-10/public-login-en.png) | ![Account recovery](docs/assets/showcase-2026-10/public-recovery-en.png) |

### Student journey

| Dashboard | Registration and curriculum plan |
|---|---|
| ![Student dashboard](docs/assets/showcase-2026-10/student-dashboard-en.png) | ![Course registration](docs/assets/showcase-2026-10/student-registration-en.png) |

| Weekly schedule | Cumulative transcript |
|---|---|
| ![Schedule](docs/assets/showcase-2026-10/student-schedule-en.png) | ![Transcript](docs/assets/showcase-2026-10/student-transcript-en.png) |

| Attendance | Projects and theses |
|---|---|
| ![Attendance](docs/assets/showcase-2026-10/student-attendance-en.png) | ![Student thesis workspace](docs/assets/showcase-2026-10/student-thesis-en.png) |

| Conduct results | Specialized assistant with technical-corpus answer |
|---|---|
| ![Conduct](docs/assets/showcase-2026-10/student-conduct-en.png) | ![Specialized answer](docs/assets/showcase-2026-10/student-assistant-answer-en.png) |

### Lecturers and academic governance

| Course grading | Academic email |
|---|---|
| ![Grade management](docs/assets/showcase-2026-10/lecturer-grades-en.png) | ![Mail compose workspace](docs/assets/showcase-2026-10/lecturer-mail-en.png) |

| Lecturer thesis workspace | Faculty-head overview |
|---|---|
| ![Lecturer theses](docs/assets/showcase-2026-10/lecturer-thesis-en.png) | ![Faculty thesis overview](docs/assets/showcase-2026-10/dean-dashboard-en.png) |

### Administration

| Accounts and roles | Project/thesis rounds |
|---|---|
| ![Users](docs/assets/showcase-2026-10/admin-users-en.png) | ![Rounds](docs/assets/showcase-2026-10/admin-thesis-en.png) |

| Curriculum default registration | Governed knowledge |
|---|---|
| ![Curriculum dry-run and apply](docs/assets/showcase-2026-10/admin-curriculum-defaults-en.png) | ![Knowledge governance](docs/assets/showcase-2026-10/admin-knowledge-en.png) |

### Animated walkthroughs

GIFs are **sequences of real captured screens**, held for approximately four seconds each. They are not continuous action recordings and do not prove mutations were performed.

**Student:** dashboard → registration → schedule → transcript → thesis → assistant.

![Student journey in English](docs/assets/showcase-2026-10/student-tour-en.gif)

**Lecturer and admin:** grades → attendance → email → admin overview → knowledge.

![Staff journey in English](docs/assets/showcase-2026-10/staff-tour-en.gif)

**Thesis governance:** student → lecturer → faculty head → round management → audit.

![Thesis governance journey](docs/assets/showcase-2026-10/thesis-governance-tour-en.gif)

**Languages:** compare VI/EN homepages, student dashboards and dean overviews.

![Language comparison](docs/assets/showcase-2026-10/language-tour.gif)

<a id="techniques"></a>
## Whole-project techniques

The [English technical guide](docs/technical/README.en.md) / [Vietnamese edition](docs/technical/README.vi.md) explains **42 topics**, including the problem, flow, invariants, failures, trade-offs and source/test owners.

| Technical area | Highlights | Details |
|---|---|---|
| Architecture and frontend | Modular monolith, service/repository, bounded JDBC/JPA; cookie/SSE-preserving BFF, traversal guards; identity-safe React Query; locales/civil dates | [T01–T05](docs/technical/architecture-security.en.md) |
| Security and sessions | Access/refresh JWTs, current authority/credential revision, CSRF, 2FA, single-flight, Web Locks, rotation overlap, RBAC + scope, rate limits | [T06–T10](docs/technical/architecture-security.en.md#t06-jwt-and-cookies-authentication-versus-current-authority) |
| Enrollment and curriculum | Student/Section locks, conditional seats, unique indexes, idempotency/hash, prerequisites/schedules/credits, dry-run/apply/cursors, W withdrawals | [T11–T14](docs/technical/academic.en.md) |
| Academic records | Draft/publication, 50/50 grading, best-attempt GPA, timed/audited appeals, exact attendance warnings, conduct, CSV/PDF/iCalendar | [T15–T18](docs/technical/academic.en.md#t15-grade-entry-publication-and-cumulative-transcripts) |
| Projects/theses | Transaction-preserving facade extraction, round-type state machine, faculty/department scope, eligibility/groups/exceptions, artifact rollback, counter-review, councils and guarded publication | [T19–T26](docs/technical/thesis.en.md) |
| AI and knowledge | Weighted lexical RAG, difficulty routing, corpus scope, personal context, SSE/heartbeats/cancellation, quota/leases/CAS, RLS, two-person review and guards | [T27–T32](docs/technical/assistant-engagement.en.md) |
| Communication | Self-hosted TinyMCE and two-layer sanitization, audience/version/scheduling, safe notification links, audit, scoped recipients and DTO/client validation | [T33–T34](docs/technical/assistant-engagement.en.md#t33-tinymce-articles-announcements-and-audit) |
| Mobile/operations | Expo Bearer/refresh, Flyway, local/database/Supabase storage, multi-stage Docker, readiness/timeouts, backup/restore, CI/image identity, evidence and media reproduction | [T35–T42](docs/technical/operations-verification.en.md) |

Key distinctions: row locks serialize contested records without making every transaction serializable; retrieval uses weighted lexical matching, not BM25/vector search; attendance warnings are advisory; quota semantics follow dispatch branches rather than every click; report metadata does not establish surviving file bytes.

<a id="architecture"></a>
## Architecture

![Current architecture](docs/assets/showcase-2026-10/architecture-en.png)

[SVG](docs/assets/showcase-2026-10/architecture-en.svg) · [Architecture boundaries](docs/ARCHITECTURE.md)

```text
Next.js web ── same-origin /api/v1 ──► Java REST API ──► PostgreSQL
Expo mobile ───── Bearer JWT ────────►       │
                                            ├──► Report storage
                                            ├──► SMTP (optional)
                                            └──► Internal RAG sidecar ──► DeepSeek (optional)
```

<a id="stack"></a>
## Technology

| Layer | Technology and version owner |
|---|---|
| Java API | Java 21, Spring Boot 4.1.2, Spring Security/Validation, JDBC, limited JPA — [Maven](java-services/pom.xml) |
| Web | Next.js 15.5.28, React 18.3.1, TypeScript, React Query, Tailwind, Radix, TinyMCE — [package.json](frontend/package.json) |
| Data | PostgreSQL 15, Flyway, constraints/triggers/indexes/RLS — [database image](database/Dockerfile), [migrations](java-services/restful-api/src/main/resources/db/migration/) |
| Mobile | Expo 57.0.31, React Native 0.86.3, React 19.2.3, TypeScript — [mobile manifest](mobile/package.json) |
| Integrations | Internal lexical RAG; configurable DeepSeek, SMTP, Supabase storage — [Compose](docker-compose.yml) |
| QA/operations | JUnit/Mockito, PostgreSQL IT, node:test, Playwright, Docker/Compose, GitHub Actions — [CI](.github/workflows/ci.yml) |

<a id="quick-start"></a>
## Run locally

Docker/Compose must be running. Read `.env.example` and the [demo runbook](docs/DEMO_RUNBOOK.md) before selecting ports, demo accounts or SMTP.

```powershell
git clone https://github.com/JasonTM17/Student_Management_UTE.git
cd Student_Management_UTE
Copy-Item .env.example .env
# Configure local values and secrets using .env.example.
docker compose up -d --build
docker compose ps
curl.exe http://127.0.0.1:4010/api/v1/health/liveness
```

| Service | Default host port | Override |
|---|---|---|
| Web | 3000 | `FRONTEND_HOST_PORT` |
| API | 4010 | `RESTFUL_API_HOST_PORT` |
| PostgreSQL | 5432 | `POSTGRES_HOST_PORT` |
| Mailpit UI / SMTP | 8025 / 1025 | `MAILPIT_UI_HOST_PORT` / `MAILPIT_SMTP_HOST_PORT` |
| RAG | Internal 4011 only | No published host port |

The PostgreSQL migration directory contains **146 versioned SQL files**, through **V150** (version gaps are intentional); [CI](.github/workflows/ci.yml) uses **Node 20** and Java 21. Locked client versions are recorded in the package lockfiles.

OpenAPI is `/v3/api-docs`, Swagger `/swagger-ui.html` on the API origin. Readiness requires the configured `X-Health-Key`. Compose provisions the assistant role before migration; RAG runs Flyway and API disables it. A running Mailpit does not mean `MAIL_ENABLED` is on. See [operations](docs/technical/operations-verification.en.md) for builds, storage and recovery.

<a id="demo"></a>
## Demo accounts

Use only in authorized local/evaluation environments with `DEMO_ACCOUNTS_ENABLED=true`. Local Compose opts in; `render.yaml` also explicitly enables demo accounts, including admin, under the documented owner decision. Review demo exposure for each public deployment. `DemoAccountGate` reapplies policy on boot; manual SQL unlocking is not a substitute.

| Role | Seed account |
|---|---|
| Students | `student@campuscore.edu`, `student2@campuscore.edu`, `student3@campuscore.edu`, `student4@campuscore.edu` |
| Lecturer | `lecturer@campuscore.edu` |
| Faculty head | `truongkhoa@campuscore.demo` |
| Department head | `truongbomon@campuscore.demo` |
| Admin / second knowledge reviewer | `admin@campuscore.edu`, `admin002@campuscore.demo` |

The published demo seed password is `password123`. Quick-fill additionally requires build opt-in/password variables in the [runbook](docs/DEMO_RUNBOOK.md); administrators have no client password prefill. Some display-only profiles are deliberately locked; directory presence is not proof of login access. Email OTP/2FA and recovery depend on configured SMTP.

<a id="verification"></a>
## Verification and evidence boundaries

```powershell
npm test --prefix frontend
npm run typecheck --prefix frontend
npm test --prefix mobile
mvn -f java-services/pom.xml verify
node scripts/check-doc-hygiene.mjs
git diff --check
```

PostgreSQL race/RLS suites need disposable databases and the [CI configuration](.github/workflows/ci.yml). The [evidence matrix](docs/technical/operations-verification.en.md#t40-verification-layers-and-evidence-limits) distinguishes unit, H2, PostgreSQL, browser and live providers. Current media proves local rendering and requests recorded in its manifest, not every mutation, production load, email receipt or deployment.

<a id="docs"></a>
## Related documentation

- [Documentation index](docs/README.md)
- [Complete techniques — English](docs/technical/README.en.md) / [Tiếng Việt](docs/technical/README.vi.md)
- [Media — English](docs/media-showcase.en.md) / [Tiếng Việt](docs/media-showcase.vi.md)
- [Architecture](docs/ARCHITECTURE.md) · [Authorization](docs/security/authorization-model.md)
- [Thesis group governance](docs/integrations/thesis-group-governance.md) · [Report storage/recovery](docs/integrations/thesis-report-storage.md)
- [Demo](docs/DEMO_RUNBOOK.md) · [Deployment](docs/deployment.md) · [Production runbook](docs/PRODUCTION_RUNBOOK.md) · [Backup](ops/backup/README.md)
- [Mobile](mobile/README.md) · [Release](docs/RELEASE.md)
