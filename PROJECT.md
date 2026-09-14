# Project: CampusCore RAG LLM Chatbot Full Optimization

## Architecture
- **Frontend**: Next.js / React application with `useAssistantStream.ts`, `assistant-student-resolver.ts`, and `thesis-api.ts`.
- **Backend Gateway**: Spring Boot (`ThesisAssistantController.java`) exposing `/api/v1/assistant/chat` and `/api/v1/assistant/chat/stream`.
- **Knowledge Store**: PostgreSQL / Supabase with schema `assistant.knowledge_document`, `assistant.knowledge_document_revision`, `assistant.knowledge_release`, `assistant.knowledge_runtime_document`. Flyway migrations in `java-services/restful-api/src/main/resources/db/migration`.
- **Retrieval Engine**: `ThesisAssistantKnowledgeRepository.java` running database-agnostic weighted term & phrase relevance matching.
- **AI Routing & LLM**: `AssistantDifficultyRouter.java` selecting fast local/lexical vs `deepseek-v4-flash`, `ThesisAssistantService.java` for RAG orchestration and context injection, `DeepSeekClient.java` for LLM completions.

## Feature Inventory
| # | Feature | Description | Milestone | Source |
|---|---------|-------------|-----------|--------|
| 1 | R1: Remove Client-Side Regex Interception | Make `resolveStudentAssistantQuery` return null for academic regulation, tuition, retake, prerequisite, scholarship, and exam queries, ensuring 100% of academic questions route to backend API Gateway | M1 | ORIGINAL_REQUEST § R1, explorer_survey_1 |
| 2 | R3: Academic Regulation Knowledge Base Migration | Add and verify Flyway migration V40 populating 6 bilingual regulation topics (prerequisites vs prior courses vs corequisites, Grade F/retakes/warnings, tuition rules/deadlines, credit limits/withdrawal, scholarships/graduation) | M2 | ORIGINAL_REQUEST § R3, explorer_survey_3 |
| 3 | R2: Overhaul Backend RAG Weighted Retrieval | Replace static `ORDER BY priority ASC` with relevance-scored weighted retrieval (phrase matching, term frequency, title bonus, domain weighting) and accented Vietnamese stop words | M3 | ORIGINAL_REQUEST § R2, explorer_survey_2 |
| 4 | R4: Optimize DeepSeek Synthesis Router | Lower difficulty thresholds (`MANY_TERMS`, `LONG_QUESTION_CHARS`) and expand Vietnamese academic intent keywords in `AssistantDifficultyRouter.java` | M4 | ORIGINAL_REQUEST § R4, explorer_survey_3 |
| 5 | R4: Fix Context Truncation & System Prompt | Replace 277-character excerpt truncation in `ThesisAssistantService.java` with full document content up to `maxContextChars`, and optimize Vietnamese prompt in `DeepSeekClient.java` | M4 | ORIGINAL_REQUEST § R4, explorer_survey_3 |
| 6 | Full E2E & Integrated Verification | Validate all acceptance criteria across frontend streaming, backend weighted retrieval, Flyway schema, and DeepSeek synthesis | M5 | ORIGINAL_REQUEST § Acceptance Criteria |

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| M1 | R1 Frontend Bypass Removal | `frontend/src/lib/assistant-student-resolver.ts`, `frontend/tests/assistant-contract.test.js` | none | DONE |
| M2 | R3 Academic Regulation Migration & Enrichment | `V40__seed_academic_regulations_knowledge.sql`, `V41__enrich_comprehensive_academic_knowledge.sql` | none | DONE |
| M3 | R2 Backend RAG Weighted Retrieval | `ThesisAssistantKnowledgeRepository.java`, `ThesisAssistantService.java`, tests | M2 | DONE |
| M4 | R4 DeepSeek Router & Context Injection | `AssistantDifficultyRouter.java`, `ThesisAssistantService.java`, `DeepSeekClient.java`, tests | M3 | DONE |
| M5 | Integrated Verification & Acceptance | Frontend test suite (`npm test`), Backend test suite (`mvn test`), E2E verification | M1, M4 | DONE |

## Interface Contracts
### Frontend ↔ Backend API Gateway
- Request: POST `/api/v1/assistant/chat/stream` or `/api/v1/assistant/chat`
  Payload: `{ message: string, locale: string, clientRequestId?: string, conversationId?: string }`
  Response: SSE Stream (`meta`, `delta`, `replace`, `citation`, `done`, `error`) or JSON `AssistantChatResponse`.
- Policy & Academic Queries: Must NOT return synthetic `CampusCore Student Assistant` meta or dummy citations. Must stream real backend events.

### Backend Retrieval ↔ LLM Context
- `ThesisAssistantKnowledgeRepository.search()` returns `List<KnowledgeDocument>` ordered by `relevanceScore DESC, published_at DESC, priority ASC`.
- Top-K = 5.
- `ThesisAssistantService` builds `context` from full document content (up to `properties.maxContextChars() = 6000`), NOT from `excerpt()` (which is limited to 277 chars).

## Code Layout
- `frontend/src/lib/assistant-student-resolver.ts`: Client query classifier & resolver.
- `frontend/src/components/assistant/useAssistantStream.ts`: Client streaming assistant hook.
- `frontend/tests/assistant-contract.test.js`: Contract tests for assistant resolver.
- `java-services/restful-api/src/main/resources/db/migration/V40__seed_academic_regulations_knowledge.sql`: Flyway schema & seed migration.
- `java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/assistant/ThesisAssistantKnowledgeRepository.java`: RAG repository & weighted retrieval SQL.
- `java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/assistant/ThesisAssistantService.java`: Service managing retrieval, stop words, context injection, and LLM orchestration.
- `java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/assistant/AssistantDifficultyRouter.java`: Difficulty classifier routing to local vs deepseek-v4-flash.
- `java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/assistant/DeepSeekClient.java`: DeepSeek client & Vietnamese system prompt.
