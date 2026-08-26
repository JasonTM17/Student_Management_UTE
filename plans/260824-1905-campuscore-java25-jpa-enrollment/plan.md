---
title: "CampusCore Professional Successor - Java 25 JPA Enrollment"
description: "Consolidate CampusCore on one Java REST API with verified auth and mail, JPA-backed assistant/registration mutation state, chatbot governance, and HCMUTE-style registration across web and mobile."
status: in-progress
priority: P1
effort: ""
tags: [java25, jpa, enrollment, restful-api, frontend, mobile, chatbot]
created: 2026-08-24
---

# CampusCore Professional Successor - Java 25 JPA Enrollment

## Outcome contract

CampusCore is handed off as one Java Spring Boot 3.5.16 REST API, PostgreSQL
15/Flyway, Next.js web, and Expo mobile. Java 25 LTS is the target runtime and
JPA is the primary persistence boundary for assistant state/knowledge and
registration mutation tables. Existing academic/auth/admin read and management
slices outside this chatbot/auth delivery remain explicit JDBC compatibility
boundaries and are not claimed as a completed whole-domain JPA rewrite.
Account lifecycle includes pending
student registration, verified-email login, refresh/logout, profile/password,
forgot/reset with one-use hashed challenges, lock recovery, and bilingual
HTML/text transactional mail through `spring.mail.*`. Registration is a first-class,
server-authoritative HCMUTE-style workflow with registration rounds, cohort
windows, configurable credit limits (default 28), prerequisites, hard schedule
conflict rejection, capacity locking, idempotent mutations, audit history, and
SHA-256-verified PDF registration slips. The bounded assistant remains
source-bound, privacy-guarded, SSE on web, and JSON on mobile.

## Scope guard

In scope: Java 25 toolchain, bounded JPA migration for assistant and
registration mutation state, additive Flyway V13+, registration
domain/API, problem-details errors, PDF slip, web registration/admin/chatbot UX,
student auth lifecycle, SMTP/Mailpit templates, web/mobile auth routes, mobile
JSON parity, tests, docs, safe runtime consolidation, split commits, safe merge
and local candidate-branch cleanup after all mandatory gates pass. After every
local gate passes, the user also authorizes a guarded schema/data
synchronization to the exact Supabase project named `Student_Management`;
this is not authority to upload local sessions/challenges, secrets or test data
and is not a production application cutover.

Out of scope: Spring Boot 4, Maven 4, PostgreSQL major upgrade, waitlist,
payment/finance, realtime, personal academic data in the assistant, production
cutover, live provider billing, remote branch/image/volume deletion, and any
destructive cleanup without an exact authorized target.

## Current evidence and authority

- Base candidate: `main` and `origin/main` at
  `38c7447974f93553596d30e4cbb15d5ce626fa28`.
- Dirty boundary at plan start: 34 modified tracked paths and 34 untracked
  paths; tracked diff `b93a0aabbe56fb1124c8ce43c77a6cfde063eef0fb88858598c2c65a02a82f34`;
  untracked manifest `c483c1ae06ad0d55578b7e2fb6c06ae430880bddf12a27f83c4d0ba6016483d8`.
- Existing source is already one Maven module/API, but host Maven runs on JDK
  24 and Java 25 proof is not yet available.
- Existing assistant V11/V12 files are dirty/untracked and must be reverified;
  predecessor PASS evidence is historical context only.
- Current plan was approved by the user; integration owner is the root agent.
- On 2026-08-25 the user explicitly extended this authority to verified email,
  resend, forgot/reset, SMTP templates, full assistant JPA cutover, safe merge
  into dirty-main through a clean integration worktree, and local candidate
  cleanup only after an archive tag and mandatory exact-head gates exist.
- Later on 2026-08-25 the user required local completion first and then a
  database upload to Supabase `Student_Management`. Remote writes therefore
  occur only after local terminal evidence, exact project/drift/backup checks
  and a reviewed resolution of Supabase-managed schema compatibility.
- The reviewed compatibility ruling keeps Spring as auth authority, moves
  application-owned identity objects to `campuscore_auth` in forward V20, and
  uses an opt-in schema-only B20 for a brand-new Supabase target. Local
  PostgreSQL 15.19 proves V1/V12/V18 -> V20 plus B20 parity without mutating
  managed-schema sentinels or copying application rows.
- On 2026-08-25, after the local V21 migration/concurrency and terminal gates,
  the exact Student_Management target was rechecked and received the reviewed
  V21 successor. Remote history now has the schema marker, B20 and V21
  checksum `-249127582`; all application tables remain empty and managed
  schemas are unchanged. Supabase's current advisor reports critical RLS
  disabled on the 48 direct-server tables; no blanket RLS change was applied,
  so public Data API access and production cutover remain HOLD pending a
  policy decision and restore evidence.

## Ordered phases

| # | Phase | Exit criterion | Status |
|---|---|---|---|
| 0 | Freeze and successor artifacts | New plan validates, exact snapshot/ownership ledger exists, isolated feature worktree is created, no data deletion | completed |
| 1 | Java 21 baseline and Java 25 toolchain | Reproducible Java 21 baseline plus real Java 25 build/test evidence | completed |
| 2 | JPA foundation and forward migrations | Fresh and upgraded DBs migrate without data loss; assistant and registration-mutation JPA parity plus negative constraints pass | completed |
| 2A | Auth database, mail and lifecycle API | Hashed one-use challenges, generic enumeration-safe APIs, session revocation and bilingual mail pass H2/PostgreSQL/Mailpit gates | completed |
| 3 | Registration domain/API/PDF | Canonical and compatibility mutation routes share the idempotent writer; round, eligibility, locking, problem codes, pagination and deterministic PDF contract pass | completed |
| 4 | Web registration, admin and chatbot | 390/768/1440 browser flows, accessibility and SSE states pass | completed |
| 4A | Web and mobile auth | Register/verify/resend/forgot/reset routes, URL-token scrubbing, deep links and state/error coverage pass | completed |
| 5 | Mobile parity | JSON registration/auth, retry/offline/session states and typecheck pass; device status honest | completed |
| 6 | Consolidation and docs | Runtime scan shows one API + PostgreSQL; docs/OpenAPI/CI match implementation | completed |
| 6A | Supabase `Student_Management` synchronization | Exact project and remote backup/drift are verified; compatible reviewed migrations/data are applied and queried without copying volatile/security data | completed |
| 7 | Independent review and handoff | Exact-head reviews, focused/full gates, split commits and branch push complete | completed |
| 8 | Container/package and repository publication | Immutable Docker Hub images, verified digests, GitHub About/Release/package links, safe merge and branch cleanup complete | pending |

## Public contract

Canonical registration routes are `/api/v1/registration/rounds`,
`/api/v1/me/registration/*`, and `/api/v1/me/enrollments`. Mutations require
`Idempotency-Key`; compatibility aliases remain deprecated. Errors use
`application/problem+json` with stable reason codes for window, cohort,
capacity, credit, prerequisite, corequisite, schedule, owner, idempotency and
version conflicts. Registration slips are generated after commit and return a
stable SHA-256 hash header. Web assistant uses validated SSE; mobile uses the
same JSON core.

`POST /api/v1/auth/register` returns `202 RegistrationPendingResponse` without
tokens or cookies. Public lifecycle routes are
`/api/v1/auth/email-verifications/confirm`,
`/api/v1/auth/email-verifications/resend`,
`/api/v1/auth/password-reset-requests`, and
`/api/v1/auth/password-reset/confirm`. Challenges store only token/email/IP
hashes, are single-use and attempt-limited, and never appear in logs. Existing
active/admin-managed accounts are verified; self-registered students must
verify before login or registration. Web tokens are removed from the URL after
one read with `Referrer-Policy: no-referrer`; mail uses a client-only URL
fragment so the raw challenge never reaches access logs. Mobile uses
`campuscore://auth/*`
deep links plus manual token fallback.

## Ownership and review

DB/JPA, backend/API, Stitch FE, mobile, QA/security and documentation writers
must own disjoint paths and run in one wave at a time. Kongming, Wukong, FE
reviewer, tester, debugger and code-reviewer are read-only review lanes. The
root agent integrates, freezes identity, stages explicit paths, commits and
pushes. Workers do not merge, push, delete data or spawn nested agents.

## Verification and release boundary

Focused checks run at each phase; full Maven, PostgreSQL, web, mobile,
authenticated Playwright, Mailpit capture, secret, encoding, docs, Compose and
diff gates run at phase/terminal checkpoints. Java 24/26 host runs are not Java 25 proof. Local/source/runtime
PASS is separate from CI, push, device, provider and production evidence.

## Scope ruling recorded after independent architecture review (2026-08-25)

Kongming identified that a whole-domain JPA claim would be inaccurate because
catalog administration, some academic reads/management paths, thesis-group
management and the Spring auth compatibility boundary still use JDBC. The
minimal safe ruling is to close this plan on the delivered assistant JPA
cutover, registration mutation JPA writers, and auth/mail lifecycle; retain
those other JDBC paths as a separately owned follow-up migration. This ruling
does not alter the public API or hosted schema contract and prevents a false
release claim. The follow-up must add dual-read/parity evidence before any
remaining writer is switched.

## Risks and rollback

JPA conversion is a high-risk rewrite: use repository parity and dual-read
comparison before switching each domain, with JDBC writes disabled only after
parity. Flyway is forward-only; rollback is backup/restore rehearsal, not
down-migration. Do not return to an old writer after idempotency/version columns
are active. Chatbot rollback is provider-disabled lexical fallback. Preserve
archive tags, ignored E2E files, Docker volumes and old images unless an exact
owner-authorized cleanup gate passes.

Auth challenges are forward-only schema changes. SMTP failure must not roll
back account creation, raw challenge tokens must remain memory-only, and resend
must recover delivery. Merge uses a clean integration worktree and must preserve
the exact dirty-main status/hash; inability to prove that boundary is
`BLOCKED_SAFE_BOUNDARY`, not permission to reset or stash user work.

Supabase is managed PostgreSQL and reserves schemas such as `auth`. The
reviewed delta moves CampusCore objects to `campuscore_auth`; no migration may
create, alter or grant against managed `auth`, `storage`, `realtime` or
`supabase_migrations`. Remote rollback requires a verified backup/restore point
and migration manifest. B20 is schema-only and a separate reference-data
allowlist defaults to empty; Spring remains the authentication authority.

## User extension: container, GitHub metadata, merge and cleanup (2026-08-25)

After every mandatory local, remote and review gate above is PASS, the
integration owner will:

1. Build backend and frontend images from the final exact SHA with immutable
   version tags (never rely on a `latest`-only publication), run container
   health/smoke checks, and push only the explicitly authorized Docker Hub
   repositories.
2. Verify Docker Hub login presence without printing credentials, record pushed
   tag digests, and distinguish registry publication from deployment.
3. Update GitHub About metadata, create a release tied to the final archive tag
   and SHA, and attach/link the published Docker packages. If repository-admin
   credentials are unavailable, record `NOT_RUN` rather than fabricating a
   release.
4. Create an archive tag, preserve dirty `main` on a named WIP branch, merge the
   candidate through a clean integration worktree, and compare dirty-content
   and index identities before and after. Never merge directly into the dirty
   checkout.
5. Delete only the merged local candidate branch and its disposable worktree
   after the archive tag, clean status, exact merge proof and no active owner
   are confirmed. Do not delete remote branches, user WIP, Docker volumes,
   credentials or unrelated containers/images.

<!-- slug: campuscore-java25-jpa-enrollment -->
