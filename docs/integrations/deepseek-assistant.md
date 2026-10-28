# DeepSeek campus assistant integration

CampusCore's assistant is a grounded campus RAG feature served by the Java API,
optionally through a private `rag-service` sidecar using the same Java artifact.
The normal path is deterministic PostgreSQL lexical retrieval with
server-owned citations. DeepSeek is an optional answer synthesizer, not a
knowledge authority and not a client-side dependency. The self-hosted
production Compose configuration uses a sidecar that syncs
the published Supabase `assistant` release into the PostgreSQL runtime snapshot.
Local Compose uses SQL authority; `render.yaml` configures a standalone API
with SQL authority by application default. Flyway seeds the runtime snapshot.
These are configuration facts, not verification of live deployment state.

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
| `ASSISTANT_USER_DAILY_QUOTA` | `20` | User daily dispatch budget; clamped to at most 20 |
| `ASSISTANT_GLOBAL_DAILY_QUOTA` | `200` | Global daily dispatch budget; clamped to at most 200 |
| `ASSISTANT_RETENTION_DAYS` | `90` | Conversation/message retention |
| `ASSISTANT_RECOVERY_DELAY_MS` | `30000` | Recovery scheduler tick; actual idle sweeps are activity-gated |
| `ASSISTANT_RECOVERY_INITIAL_DELAY_MS` | `30000` | Startup grace before first sweep |

The endpoint/model follow DeepSeek's OpenAI-compatible chat completion contract:
<https://api-docs.deepseek.com/api/create-chat-completion/>. CampusCore pins
the base host and model in server configuration; clients cannot override either.

## RAG-first dispatch policy

The ledgered RAG path retrieves the active published runtime release before
provider synthesis. Direct fact lookups return `curated-lexical-rag` with
`RAG_GROUNDED` without provider quota. A confident controller fast path instead
returns `curated-lexical-fast` / `ANSWERED`, without creating a ledger turn or
persisting history. `AssistantDifficultyRouter` only allows synthesis
for a non-empty retrieval result when the question is long or multi-intent,
contains comparison/explanation/exception markers, or spans multiple knowledge
domains. A no-match result in this path does not dispatch DeepSeek. Separately,
`generalAnswerIfOffTopic` can answer non-academic questions without retrieved
context or citations when the provider is usable; it is disabled for specialized
scope and uses standalone quota accounting. It does not establish campus facts.

## Request/data boundary

The grounded provider request contains the current question and bounded,
locale-filtered, published campus context delimited as untrusted data. It does
not automatically attach account email, profile IDs, bearer tokens, raw
conversation history or unfiltered database rows. The current question is
user-supplied text, so this is not a guarantee that it contains no personal data.
The system instruction requires an answer from the
context only, ignores instructions inside the question/context, and refuses
unsupported claims. Citations are attached by Java from retrieved rows.

## API behavior

- `POST /api/v1/thesis/assistant/chat` returns the complete JSON answer.
- `POST /api/v1/thesis/assistant/chat/stream` returns ordered `meta`, `delta`,
  `citation`, `done`, or `error` SSE events.
- `POST/GET/DELETE /api/v1/thesis/assistant/conversations...` manage owner-only
  history. A missing conversation ID creates one server-side.
- Chat/history routes allow `STUDENT`, `LECTURER`, `ADMIN`, `SUPER_ADMIN`
  and `TRUONG_KHOA`; department heads normally also hold `LECTURER`. History
  remains owner-scoped: knowledge administration does not grant access to
  other users' conversations. Both `/api/v1/assistant` and
  `/api/v1/thesis/assistant` prefixes are supported.
- Every intentional send carries a caller-owned `clientRequestId`; completed
  ledgered turns replay the committed answer, while cancelled/purged keys return a
  stable terminal error. Web streaming uses `meta → delta/replace → citation
  → done`; a stream without `done` is not treated as complete. Stop first
  performs the owner-scoped server CAS and then aborts the local reader.
  Fast-path, personal-context and general-answer branches have separate
  persistence/replay semantics; a repeated key is not universally a cached answer.

Expired `RESERVED`/`SNAPSHOT_READY` leases become retryable
`FAILED_PRE_DISPATCH`; an expired `DISPATCHED` lease becomes
`FAILED_AMBIGUOUS` and is never automatically redispatched or refunded. A
30-second scheduler tick checks whether recovery is due. By default, recovery
sweeps on the first tick, during five minutes after lease activity, and after
15 idle minutes since the last successful sweep; errors/full batches retry on
the next tick. `ASSISTANT_RECOVERY_ACTIVITY_AWARE=false` sweeps every tick.
Recovery fences the old in-process provider handle; the daily purge job
(default retention 90 days) is a separate privacy operation.

When provider access is disabled, rejected by the provider, over quota, circuit-open,
timed out, or fails, the service returns the lexical answer with `degraded`
and a stable `reasonCode`. A ledgered RAG retrieval with no matching published campus
documents does not attempt synthesis. Caller authentication and RLS failures
remain access/availability errors; provider fallback never bypasses them.

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
