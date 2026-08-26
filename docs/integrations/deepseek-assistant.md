# DeepSeek thesis assistant integration

CampusCore's assistant is a thesis-only, grounded RAG feature exposed through
the Java API and executed by the internal `rag-service` container in Docker
Compose. The normal path is deterministic PostgreSQL lexical retrieval with
server-owned citations. DeepSeek is an optional answer synthesizer, not a
knowledge authority and not a client-side dependency.

## Runtime configuration

Set these variables only in the backend process or a secret manager. The
repository contains placeholders, never credentials:

| Variable | Default | Purpose |
| --- | --- | --- |
| `DEEPSEEK_ENABLED` | `false` | Feature flag; missing key always disables provider calls |
| `DEEPSEEK_API_KEY` | empty | Rotated server-only credential |
| `DEEPSEEK_BASE_URL` | `https://api.deepseek.com` | Fixed allowlisted provider host |
| `DEEPSEEK_MODEL` | `deepseek-v4-flash` | Non-thinking model selection |
| `DEEPSEEK_TIMEOUT_MS` | `8000` | Bounded connect/read budget |
| `DEEPSEEK_MAX_OUTPUT_TOKENS` | `800` | Output cap |
| `ASSISTANT_MAX_MESSAGE_CHARS` | `2000` | Input bound |
| `ASSISTANT_MAX_CONTEXT_CHARS` | `6000` | Retrieval context bound |
| `ASSISTANT_USER_DAILY_QUOTA` | `20` | Provider attempts per user/day |
| `ASSISTANT_GLOBAL_DAILY_QUOTA` | `200` | Provider attempts globally/day |
| `ASSISTANT_RETENTION_DAYS` | `90` | Conversation/message retention |
| `ASSISTANT_RECOVERY_DELAY_MS` | `30000` | Expired lease recovery sweep |
| `ASSISTANT_RECOVERY_INITIAL_DELAY_MS` | `30000` | Startup grace before first sweep |
| `ASSISTANT_RAG_SERVICE_TOKEN` | local placeholder | Shared token used only between `restful-api` and `rag-service` |
| `ASSISTANT_RAG_BASE_URL` | empty | Public API proxy target; Compose sets this to the internal service URL |

The endpoint/model follow DeepSeek's OpenAI-compatible chat completion contract:
<https://api-docs.deepseek.com/api/create-chat-completion/>. CampusCore pins
the base host and model in server configuration; clients cannot override either.

## Request/data boundary

The provider request contains only the current question and a bounded,
locale-filtered, published thesis context delimited as untrusted data. It does
not include email, profile IDs, bearer tokens, raw conversation history, or
unfiltered database rows. The system instruction requires an answer from the
context only, ignores instructions inside the question/context, and refuses
unsupported claims. Citations are attached by Java from retrieved rows.

## API behavior

- `POST /api/v1/thesis/assistant/chat` returns the complete JSON answer.
- `POST /api/v1/thesis/assistant/chat/stream` returns ordered `meta`, `delta`,
  `citation`, `done`, or `error` SSE events.
- `POST/GET/DELETE /api/v1/thesis/assistant/conversations...` manage owner-only
  history. A missing conversation ID creates one server-side.
- Only students and lecturers can chat or read/delete their own history.
  Admins manage knowledge revisions but do not see user conversations by
  default.
- Docker Compose routes those authenticated public calls from `restful-api` to
  `rag-service`. The internal `/internal/rag/**` endpoints are active only when
  `ASSISTANT_RAG_SERVICE_MODE=true` and require `X-Rag-Service-Token` plus
  `X-Assistant-Owner`.
- Every intentional send carries a caller-owned `clientRequestId`; completed
  keys replay the committed answer, while cancelled/purged keys return a
  stable terminal error. Web streaming uses `meta → delta/replace → citation
  → done`; a stream without `done` is not treated as complete. Stop first
performs the owner-scoped server CAS and then aborts the local reader.

Expired `RESERVED`/`SNAPSHOT_READY` leases become retryable
`FAILED_PRE_DISPATCH`; an expired `DISPATCHED` lease becomes
`FAILED_AMBIGUOUS` and is never automatically redispatched or refunded. A
30-second recovery sweep fences the old in-process provider handle, while the
90-day purge job remains a separate privacy-retention operation.

When provider access is disabled, unauthenticated, over quota, circuit-open,
timed out, or fails, the service returns the lexical answer with `degraded`
and a stable `reasonCode`. If retrieval has no matching published thesis
documents, no provider call is attempted.

## Knowledge lifecycle

Admin authoring creates `DRAFT`, then `PENDING_REVIEW`; a different admin must
publish. Published revisions are the only retrieval source. A single demo admin
therefore sees a pending state rather than silently self-publishing. Audit rows
record create, update, submit, publish and archive actions. The admin DELETE
endpoint is a compatibility alias for a soft archive: retrieval stops, while
all revisions and audit evidence remain available. User conversation DELETE is
different and is a confirmed physical privacy deletion; its request ledger
keeps a short tombstone so an old key cannot replay deleted content.

## Local verification

Use a fake provider and no-network tests for CI. Confirm `DEEPSEEK_API_KEY` is
absent from tracked files and logs. A live smoke is not part of normal CI and
must be separately authorized after rotating any key that was pasted into a
chat or shell.
