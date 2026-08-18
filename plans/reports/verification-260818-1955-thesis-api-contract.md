# Thesis API Contract Verification

**Date:** 2026-08-18
**Scope:** Frontend `thesis-api.ts` vs Backend `ThesisController` + DTOs
**Status:** ALIGNED — all non-admin endpoints covered, types match

## Endpoint Alignment

| Backend Endpoint | Frontend Function | Match |
|------------------|-------------------|-------|
| `GET /rounds?status=` | `listRounds()` | ✅ |
| `POST /rounds/{id}/open-registration` | `openRegistration(id)` | ✅ |
| `POST /rounds/{id}/close-registration` | `closeRegistration(id)` | ✅ |
| `POST /rounds/{id}/publish-proposals` | `publishProposals(id)` | ✅ |
| `GET /topics?roundId=&status=` | `listTopics(roundId)` | ✅ |
| `POST /topics/{id}/publish` | `publishTopic(id)` | ✅ |
| `GET /groups?roundId=` | `listGroups(roundId)` | ✅ |
| `GET /groups/{id}` | `getGroup(id)` | ✅ |
| `POST /groups` (CreateGroupRequest{roundId}) | `createGroup(roundId)` | ✅ |
| `POST /groups/{id}/members` | `addMember(groupId, studentId)` | ✅ |
| `POST /groups/{id}/topic` | `assignTopic(groupId, topicId)` | ✅ |
| `POST /groups/{id}/decision` | `decideGroup(groupId, approved, reason)` | ✅ |
| `GET /councils?roundId=` | `listCouncils(roundId)` | ✅ |
| `POST /councils/{id}/schedule` | `scheduleCouncil(id, scheduledAt, room)` | ✅ |
| `POST /councils/{id}/open-scoring` | `openScoring(id)` | ✅ |
| `POST /reviews` | `submitReview(councilId, groupId, score, comment)` | ✅ |
| `POST /results/publish` | `publishResult(councilId, groupId)` | ✅ |
| `POST /assistant/chat` | `chat(message, locale)` | ✅ |

**Not in frontend (admin-only, expected):** `POST /rounds`, `POST /topics`, `POST /councils`, `POST /councils/{id}/members` — these belong in an admin panel, not the student/lecturer thesis workspace.

## Type Alignment (Frontend ↔ Backend DTO)

| Frontend Type | Backend DTO | Match |
|---------------|-------------|-------|
| `ThesisRound` | `RoundResponse` | ✅ |
| `ThesisTopic` | `TopicResponse` | ✅ |
| `ThesisGroup` | `GroupResponse` | ✅ |
| `ThesisCouncil` | `CouncilResponse` | ✅ |
| `ThesisCouncilMember` | `CouncilMemberResponse` | ✅ |
| `ThesisResult` | `ResultResponse` | ✅ |
| `AssistantReply` | `ChatResponse` | ✅ |

All `UUID` ↔ `string`, `Instant` ↔ `string` (ISO-8601), `BigDecimal` ↔ `number` mappings correct.

## Infrastructure

- Nginx: `/api/v1/thesis/*` → `thesis-service:4010` ✅ (verified in `nginx/nginx.conf`)
- Frontend API proxy: `/api/v1/[...path]` → local edge (`127.0.0.1:8080`) ✅
- Backend prefix: `/api/v1/thesis` ✅

## Notes

- Backend `thesis-service` requires running PostgreSQL + Flyway migrations for live API smoke test (not executed — requires DB seed).
- CSRF: frontend `api.ts` auto-attaches `X-CSRF-Token` header from `cc_csrf` cookie for mutating requests ✅
- Auth: all endpoints require JWT (OAuth2 resource server) ✅
