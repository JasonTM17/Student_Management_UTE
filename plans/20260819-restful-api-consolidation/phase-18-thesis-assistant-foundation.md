# Phase 18 — Backend-first thesis assistant/chatbotAI contract foundation

## Status

**Source candidate / HOLD.** This phase adds the Java RESTful API contract
surface needed by the existing web and mobile assistant clients, but it is not
provider-backed chatbot cutover evidence.

## Outcome and success signal

Create a feature-default-off Java monolith endpoint for
`POST /api/v1/thesis/assistant/chat` that preserves the current client response
shape:

- `answer`: user-facing assistant guidance;
- `model`: server-selected model identifier;
- `degraded`: `true` while running the deterministic local fallback.

This phase is complete only when the endpoint is proven disabled by default,
authenticated when enabled, validation rejects malformed requests, and the
fallback shape remains compatible with web/mobile callers.

## Scope and authority

### In scope

- Spring MVC controller under the Java RESTful API monolith.
- Server-side feature flag `THESIS_ASSISTANT_ENABLED=false` by default.
- Deterministic bilingual local fallback for thesis/course-project guidance.
- Contract tests for auth, validation, response shape and default-off behavior.

### Non-goals

- No external LLM provider call.
- No API key, prompt persistence, vector database, tool execution, streaming or
  moderation claim.
- No chatbot public route cutover, gateway canary, telemetry SLO, cost control
  or rollback claim.
- No frontend or mobile screen changes in this phase.

## Current evidence and assumptions

- The web client already posts `{ message, locale }` to
  `/api/v1/thesis/assistant/chat` and expects `{ answer, model, degraded }`.
- The mobile API client targets the same route seam and must send the same
  `{ message, locale }` payload shape before live mobile mode can be trusted.
- The Java monolith did not previously expose this route.
- Default-off behavior protects the unfinished Java backend from accidental
  public traffic.

## Affected components

- `java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/assistant`
- `java-services/restful-api/src/test/java/io/campuscore/restfulapi/thesis/ThesisAssistantContractTest.java`
- `java-services/restful-api/src/test/java/io/campuscore/restfulapi/RestfulApiContractTest.java`
- `java-services/restful-api/src/main/resources/application.yml`
- `java-services/restful-api/src/test/resources/application-test.yml`
- `mobile/src/api/client.ts`
- `mobile/tests/screen-atlas.test.mjs`
- `mobile/README.md`

## Acceptance criteria

1. With default config, `/api/v1/thesis/assistant/chat` returns the standard
   `NOT_FOUND` envelope.
2. With `migration.thesis-assistant.enabled=true`, anonymous requests are
   rejected.
3. Authenticated valid requests return HTTP 200 with `answer`, `model` and
   `degraded=true`.
4. Empty messages and unsupported locales return the standard validation error
   envelope.
5. Source checks show the assistant package does not call a provider, store
   prompts, or write database state.
6. The mobile API seam sends `{ message, locale }` and expects
   `{ answer, model, degraded }`, matching the web client and Java controller.

## Verification commands

```powershell
$env:TEMP='D:\Student_Management-recovery\tmp'
$env:TMP='D:\Student_Management-recovery\tmp'
$env:MAVEN_OPTS='-Xmx384m -XX:MaxMetaspaceSize=192m -XX:ReservedCodeCacheSize=64m -XX:+UseSerialGC -XX:ActiveProcessorCount=1'
mvn -q -f java-services/restful-api/pom.xml '-Dtest=io.campuscore.restfulapi.thesis.ThesisAssistantContractTest,RestfulApiContractTest' '-DforkCount=0' test
node scripts/check-doc-hygiene.mjs
node scripts/check-architecture.mjs
node scripts/check-thesis-contract.mjs
npm test --prefix mobile
git diff --check
```

## Risks and rollback

- Risk: clients could mistake the local fallback for a full provider-backed
  assistant. Mitigation: the endpoint is default-off, returns `degraded=true`,
  and documentation keeps provider mode open.
- Risk: provider integration later could leak secrets or prompt content.
  Mitigation: provider mode must be a separate server-side port with redaction,
  timeout, rate limiting, evals and rollback evidence before cutover.
- Rollback: leave `THESIS_ASSISTANT_ENABLED=false` or remove the route from the
  Java candidate; no database or public route state is changed in this phase.
