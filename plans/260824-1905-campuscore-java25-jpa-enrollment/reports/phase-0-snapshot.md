# Phase 0 snapshot and ownership classification

Captured 2026-08-24 from `D:\Student_Management` before creating the feature
worktree. No `.env` content was read or copied.

## Identity

- `HEAD`: `38c7447974f93553596d30e4cbb15d5ce626fa28`.
- `main` tracks `origin/main` with zero ahead/behind at capture.
- `git diff --binary | git hash-object --stdin`: `d1cd51a9b05c8d58f006d3f466edce9973091a19`.
- Canonical sorted SHA-256 lines for the 34 untracked files, hashed through
  `git hash-object --stdin`: `7ae56ceac68ee85c6d6a9ce792ef241c03a599cd`.
- Porcelain v2 reported 34 worktree modifications, 34 untracked files, and no
  staged entries. The earlier audit identities in `plan.md` remain the
  immutable plan-start record; these values are the Phase 0 recheck.

## Modified tracked paths (34)

### KEEP / reverify as assistant or shared contract input

```text
frontend/src/components/assistant/AssistantPanel.tsx
frontend/src/i18n/messages.ts
frontend/src/lib/thesis-api.ts
frontend/tests/phase-six-repair.test.js
java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/assistant/ThesisAssistantController.java
java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/assistant/ThesisAssistantDtos.java
java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/assistant/ThesisAssistantKnowledgeRepository.java
java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/assistant/ThesisAssistantService.java
java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/service/ThesisMutationService.java
java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/web/ThesisMutationController.java
java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/web/ThesisMutationDtos.java
java-services/restful-api/src/main/resources/application.yml
java-services/restful-api/src/test/java/io/campuscore/restfulapi/thesis/ThesisAssistantContractTest.java
java-services/restful-api/src/test/java/io/campuscore/restfulapi/thesis/assistant/ThesisAssistantOutageWebTest.java
java-services/restful-api/src/test/java/io/campuscore/restfulapi/thesis/assistant/ThesisAssistantServiceTest.java
mobile/src/api/client.ts
mobile/src/design/tokens.ts
mobile/src/screens/assistant/AssistantScreen.tsx
mobile/README.md
mobile/tests/phase-six-repair.test.mjs
mobile/tests/screen-atlas.test.mjs
```

### REVIEW / shared build, runtime or navigation boundary

```text
.env.example
.github/workflows/ci.yml
README.md
README.vi.md
docker-compose.yml
docs/ARCHITECTURE.md
docs/RELEASE.md
frontend/package.json
frontend/src/app/admin/page.tsx
frontend/src/app/dashboard/layout.tsx
frontend/src/components/admin/AdminFrame.tsx
frontend/src/lib/api.ts
java-services/restful-api/pom.xml
```

## Untracked paths (34)

All are explicit assistant documentation, implementation, migration, test, or
script inputs and will be copied only after candidate path validation. They are
not release evidence until rebuilt and retested on the successor branch.

```text
docs/integrations/deepseek-assistant.md
frontend/e2e/assistant-admin.spec.ts
frontend/src/app/admin/assistant-knowledge/page.tsx
frontend/src/lib/assistant-stream.ts
frontend/tests/assistant-contract.test.js
java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/assistant/AssistantCancellationRegistry.java
java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/assistant/AssistantCompletionProvider.java
java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/assistant/AssistantConfiguration.java
java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/assistant/AssistantInputGuard.java
java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/assistant/AssistantProperties.java
java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/assistant/DeepSeekClient.java
java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/assistant/DeepSeekProperties.java
java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/assistant/ThesisAssistantCatalogRepository.java
java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/assistant/ThesisAssistantKnowledgeAdminController.java
java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/assistant/ThesisAssistantRepository.java
java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/assistant/ThesisAssistantRetentionJob.java
java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/assistant/ThesisAssistantTurnRepository.java
java-services/restful-api/src/main/resources/db/migration/V11__create_assistant_rag_governance.sql
java-services/restful-api/src/main/resources/db/migration/V12__harden_academic_assistant.sql
java-services/restful-api/src/test/java/io/campuscore/restfulapi/thesis/assistant/AssistantCancellationRegistryTest.java
java-services/restful-api/src/test/java/io/campuscore/restfulapi/thesis/assistant/DeepSeekClientTest.java
java-services/restful-api/src/test/java/io/campuscore/restfulapi/thesis/assistant/ThesisAssistantApiContractTest.java
java-services/restful-api/src/test/java/io/campuscore/restfulapi/thesis/assistant/ThesisAssistantCatalogAllowlistTest.java
java-services/restful-api/src/test/java/io/campuscore/restfulapi/thesis/assistant/ThesisAssistantGovernanceWebTest.java
java-services/restful-api/src/test/java/io/campuscore/restfulapi/thesis/assistant/ThesisAssistantOwnershipTest.java
java-services/restful-api/src/test/java/io/campuscore/restfulapi/thesis/assistant/ThesisAssistantRepositoryTest.java
java-services/restful-api/src/test/java/io/campuscore/restfulapi/thesis/assistant/ThesisAssistantTurnLedgerH2Test.java
java-services/restful-api/src/test/java/io/campuscore/restfulapi/thesis/assistant/ThesisAssistantTurnLedgerPostgresIT.java
java-services/restful-api/src/test/java/io/campuscore/restfulapi/thesis/assistant/ThesisAssistantTurnMigrationPostgresIT.java
java-services/restful-api/src/test/java/io/campuscore/restfulapi/thesis/assistant/ThesisAssistantTurnStateMachineTest.java
java-services/restful-api/src/test/resources/db/migration-h2/V5__create_assistant_rag_governance.sql
java-services/restful-api/src/test/resources/db/migration-h2/V6__harden_academic_assistant.sql
scripts/check-assistant-secrets.mjs
scripts/run-course-e2e.mjs
```

## Protected/deferred inventory

Ignored E2E Compose files, the empty/legacy Nginx path, active Docker
containers/images/volumes, other worktrees, credentials, IDE/Codex data and
the dirty root checkout are outside this copy operation. They remain
`DEFERRED`; no physical cleanup or branch deletion is authorized by Phase 0.

## Import rule

The feature worktree starts at the clean base. Assistant paths may be imported
as explicit candidate input only after each destination is resolved beneath the
worktree root. Shared REVIEW paths require revalidation in the relevant phase;
their historical dirty content is never silently treated as authoritative.

## Candidate import provenance

The candidate was created from the clean base and received the 34 tracked
patches with `git apply --3way` plus 34 explicitly enumerated untracked files.
Before and after comparison produced the same tracked diff hash
`d1cd51a9b05c8d58f006d3f466edce9973091a19`, the same untracked manifest hash
`7ae56ceac68ee85c6d6a9ce792ef241c03a599cd`, and zero semantic content
mismatches (only 15 line-ending byte differences). An ignored `.env` exists in
the candidate; its content/provenance is deliberately unverified, it was not
read or staged, and it is protected from all commits and scans (`NOT_PROVEN`,
not a secret-safety PASS).

The duplicate plan artifact created during synchronization was moved out of
the candidate to
`D:\worktrees\phase0-quarantine\campuscore-java25-jpa-enrollment-nested-plan-duplicate`.
It is retained for reversible inspection and is not part of the candidate
identity.
