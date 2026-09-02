# Execution Ledger

Active plan: `plans/260901-1809-campuscore-production-readiness-and-professional-ux/plan.md`

## Completed evidence

- Phase 01: preserved the original dirty checkout in the external checksummed recovery bundle; fetched `origin/main` at `e56f599d4edac216864eb6a2d61fa4509b53d627`; created the clean `release/campuscore-production-readiness` integration worktree. No broad reset, clean, stash, or historical worktree deletion was used.
- Phase 03 implementation: added the hybrid editorial public/auth treatment, portal density/accessibility fixes, canonical campus assistant UI and the 390/768/1024/1440 Playwright/axe matrix. Frontend unit (67/67), typecheck, lint, build, and a focused isolated Playwright regression (7 passed, 3 configuration skips) passed. A full 120-case run was interrupted by the runner environment and remains `NOT_RUN`, not PASS.
- Phase 04 implementation: added six public campus domains, canonical plus one-cycle deprecated aliases, citation release provenance, DeepSeek runtime secret-file support, Flyway V16 runtime projection, Supabase two-reviewer immutable releases, and transactional last-good sync. `mvn -q -f java-services/pom.xml verify` passed 207 tests with zero failures/errors/skips. Deterministic fetch/validate/stage/pointer fault tests passed. Local Supabase reset applied all three migrations; migration parity, schema lint, and database advisors passed with no findings; Node corpus tests passed 4/4.
- Phase 05 implementation: added private-network production Compose, Caddy edge, runtime secret mounts, backup/restore templates, manual exact-main-SHA paired-registry workflow, and canonical `campuscore-*` image names for Docker Hub and GHCR. Validation/runtime evidence is still in progress.
- Phase 06: mobile canonical API compatibility was committed as `bb9117e0`; unit tests 18/18 and typecheck passed. Real-device certification is intentionally deferred.
- Phase 06 follow-up: upgraded the Expo client dependency set to Expo 57 / React Native 0.86, regenerated the lockfile, and repaired the two React Native type deltas. `npm run typecheck --prefix mobile`, `npm test --prefix mobile` (18/18), `CI=1 npx expo-doctor` (21/21), and `npm audit --prefix mobile --audit-level=high --json` (0 high/critical; 10 moderate) passed. Device certification remains deferred.
- FE terminal browser evidence: isolated `chromium` passed 18/24 (6 intentional skips), `tablet-768` passed 20/24 (4 skips), and `desktop-1024` passed 20/24 (4 skips). The first desktop-1440 sweep found a real `400 /api/v1/sections` on admin announcements because the page requested `limit=200` against the API's 100-row cap; the page now requests 100 and the source regression passes. A focused desktop-1440 admin sweep then passed 1/1 across all 56 route/locale/theme combinations. The mobile-390 full sweep was intentionally stopped at the user's request and is `NOT_RUN_DEFERRED`.
- E2E isolation evidence: the old default `:3000` run was `NOT_RUN_WRONG_TARGET` after it reached an unrelated HealthCare process; the runner now fail-closes on a preflighted CampusCore `:3101` and disposable Compose project. One subsequent long run also lost its Docker/API process mid-suite (`NOT_RUN_INTERRUPTED`); no result was translated to PASS.
- Publish workflow static evidence: YAML parses successfully and the manual workflow now requires exact main SHA/review confirmation, builds four full-SHA image tags, verifies cross-registry digest/OCI revision and attestation manifests, runs Trivy high/critical scanning, and uploads per-image plus aggregate release-manifest artifacts. Registry publication has not been dispatched.
- Capability record: the first Kongming, FE reviewer, and Wukong wave returned `BLOCKED_CAPABILITY` because the agent service quota was unavailable; none was translated into PASS.

## Current step

- Current phases: 03/05 terminal FE and production-package qualification.
- Next unfinished step: commit the reviewed release set and merge it into `main` per the user's current instruction. Full mobile-390 qualification, independent specialist review, fresh registry stack, CI, publication, and live provider smoke remain deferred/blocked and must be completed before any production-readiness or deployment claim.
- Exit criterion: all local terminal gates pass on one candidate identity, or an observed gate produces a bounded repair/blocker with exact evidence.

## External blockers and deferred evidence

- A newly rotated DeepSeek key is required for the two live provider smokes. The key pasted in chat is compromised and is never used, committed, logged, or embedded.
- Hosted Supabase credentials are required only for a live hosted authoring/sync proof; local migration, grants, RPC, and fault behavior remain testable.
- VPS/domain/DNS/TLS are required for real production cutover, which is outside this plan's completion claim.
- Independent specialist capacity must be retried after candidate freeze; exact-head review is mandatory before merge or publication.

## Resume point

Commit the current release set on the integration branch, merge it into `main` without touching unrelated dirty worktrees, and preserve the exact deferred-gate record. Resume with independent review, full mobile-390, CI, and registry publication only after the user asks to continue qualification.
