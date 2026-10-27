# 4. AI and academic communication

[Tiếng Việt](assistant-engagement.vi.md) · [Index](README.en.md) · [Next: operations](operations-verification.en.md)

## T27. Grounded lexical RAG and difficulty routing

**Flow.** Receive message/locale/scope → authenticate and validate → select published corpus → normalize/tokenize → retrieve/rank → construct bounded context → answer with citations. The repository adds weights for title/content substring and whole-word matches: 3/1/4/2 respectively. This is project-defined weighted lexical ranking, not standard BM25, embeddings or vector similarity.

`topK` bounds retrieval; prompt-document count has a separate limit. The fast path requires lexical confidence; an “AI” label does not imply a model call. `AssistantDifficultyRouter` uses length, term count, comparison/explanation markers and document/domain diversity to decide synthesis. Credit-limit queries have deterministic curated answers to reduce paraphrase drift.

DeepSeek is optional, with configurable model/base URL/timeout. Missing configuration, timeouts and provider failures have grounded fallbacks carrying `degraded`/`reasonCode`, rather than pretending a model responded. Missing sources must not produce fabricated citations. This favors explainability and local operation; quality still depends on corpus, language and question.

**Owners:** [ranking](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/assistant/repository/ThesisAssistantKnowledgeRepository.java), [orchestration](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/assistant/service/ThesisAssistantService.java), [difficulty router](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/assistant/service/AssistantDifficultyRouter.java), [bounded properties](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/assistant/properties/AssistantProperties.java), [provider client](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/assistant/client/DeepSeekClient.java).

## T28. Specialized corpora and scoped personal context

Academic help and the specialized workspace have different purposes/corpora. `scope` belongs to the conversation/turn; changing corpus must not silently reuse another scope's history. The specialized workspace serves technical/project knowledge. Regulatory questions need the appropriate academic corpus. Showcase prompts use the relevant domain.

`AssistantPersonalContextAdvisor` reads schedules, grades, credits, registration, attendance and supervision through internal services with a known caller identity. Personal records derive from the authenticated caller, not an arbitrary student code in a prompt. Questions about other people or unresolved identity must retain that boundary. Personal context and public knowledge are different sources; student records do not belong in the public corpus as a quality shortcut.

Fallbacks must state which data could not be verified. Empty context does not mean zero grades, no classes or a completed curriculum.

**Owners:** [personal context](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/assistant/service/AssistantPersonalContextAdvisor.java), [specialized workspace](../../frontend/src/app/dashboard/assistant-specialized/page.tsx), [scope regression](../../frontend/tests/assistant-scope-guard.test.js), [knowledge domains](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/assistant/service/AssistantKnowledgeDomains.java).

## T29. SSE streaming, heartbeats and cancellation

Web SSE transports metadata, deltas, citations, replacements, heartbeats and terminal states according to the stream contract. Reducer/UI logic must handle partial arrival, attach content to the right message, apply replace events and stop loading after terminal events. Heartbeats indicate liveness rather than answer content.

Stop/cancel actions propagate to transport and backend cancellation state. Closing a tab or aborting a request does not always prove that a provider stopped or no quota was consumed. Executor/leases separate running work from a short-lived browser connection. After streaming headers are sent, failures use event schemas because the HTTP status can no longer freely change.

The proxy forwards before EOF. Native mobile uses buffered JSON, without claiming SSE support. Readability, focus, scrolling and composer locking are separate acceptance requirements from an endpoint returning 200.

**Owners:** [stream hook](../../frontend/src/components/assistant/useAssistantStream.ts), [stream DTOs](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/assistant/dto/), [executor](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/assistant/service/AssistantStreamExecutor.java), [cancellation registry](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/assistant/service/AssistantCancellationRegistry.java), [lifecycle tests](../../frontend/tests/assistant-stream-lifecycle.test.js).

## T30. Quota and turn ledgers: CAS, leases and idempotency

**Problem.** Multiple tabs can dispatch one intent twice; a process dying between quota reservation and answer persistence can leave a stuck turn. In-memory counters cannot coordinate instances or recovery.

The turn ledger binds owner, client request ID, request hash, conversation, state and lease generation. A normal path is `RESERVED → SNAPSHOT_READY → DISPATCHED → COMPLETED`; repository-defined failure/cancellation/expiry paths handle other outcomes. Conditional updates on state, owner, generation and deadline form compare-and-swap: only the current lease holder can finalize. An old worker cannot commit after ownership advances to a new generation.

User/global buckets are atomically reserved/charged in short dispatch transactions, with a dispatch registry supporting idempotency. Daily buckets use `Asia/Ho_Chi_Minh`, not arbitrary browser time or default UTC. Current `AssistantProperties` clamps limits to at most 20 per user/day and 200 globally/day. Provider dispatches, fast paths and standalone branches have different charge/refund semantics; “every send costs exactly one quota” is inaccurate.

Recovery distinguishes pre-dispatch from dispatched/unknown outcomes. Refunds follow state and charge date instead of blindly refunding on browser abort. The ledger preserves evidence and limits duplicates internally; it cannot guarantee exactly-once execution at an external provider lacking that contract.

**Owners:** [turn repository](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/assistant/repository/ThesisAssistantTurnRepository.java), [assistant service](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/assistant/service/ThesisAssistantService.java), [timezone](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/assistant/service/AssistantTimezone.java), [retention job](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/assistant/job/ThesisAssistantRetentionJob.java).

## T31. RLS and two-person knowledge publication

Assistant persistence uses a separate DataSource/transaction manager, a restricted runtime role and PostgreSQL Row-Level Security. The transaction runner binds trusted identity with transaction-local settings; arbitrary headers cannot grant authority. Context must not leak to the next pooled-connection request. Owner operations and admin governance have distinct boundaries.

A probe/cache operation can use an isolated `REQUIRES_NEW` transaction so a PostgreSQL statement error does not poison the caller and break fallback. This is deliberate failure isolation, not a reason to split every business operation into independent commits.

Revisions move `DRAFT → PENDING_REVIEW → PUBLISHED`; authors cannot publish their own revision. The backend verifies author/reviewer and privacy policy before release/snapshot promotion. Older content is versioned/archived; editing a draft does not immediately change runtime knowledge. Admin UI demonstrates workflow; RLS acceptance requires the actual restricted role, not only H2 or a superuser connection.

**Owners:** [RLS runner](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/assistant/rls/AssistantRlsTransactionRunner.java), [runtime verifier](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/assistant/rls/AssistantRlsRuntimeVerifier.java), [knowledge governance](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/assistant/service/AssistantKnowledgeAdminService.java), [release promoter](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/assistant/service/SqlKnowledgeReleasePromoter.java), [CI RLS lane](../../.github/workflows/ci.yml).

## T32. Input/output guards and privacy boundaries

The input guard normalizes and checks VI/EN patterns for authority bypass, instruction override and invalid content. The output guard checks answers before display/persistence, with policy-specific audit. Message/context/output bounds and timeouts create a finite resource budget.

Pattern/rule guards are one defense layer, not proof against every prompt injection. Services/RLS independently enforce data access; a provider cannot grant permissions. Unverifiable identity/scope must not be bypassed through fallback. Tokens, keys, sensitive prompts and raw provider errors do not belong in public evidence.

**Owners:** [input guard](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/assistant/guard/AssistantInputGuard.java), [output guard](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/assistant/guard/AssistantOutputGuard.java), [guard audit regression](../../frontend/tests/assistant-output-guard-audit.test.js), [assistant auth checks](../../frontend/tests/assistant-auth-gate.test.js).

## T33. TinyMCE, articles, announcements and audit

TinyMCE is self-hosted. Rich HTML must preserve tables/images/formatting while removing scripts, event handlers, dangerous URLs and unsafe CSS. Backend Jsoup sanitization is the write boundary; frontend sanitization adds render-time defense. Allowing validated inline image data does not make `data:` a permitted navigation scheme. Editor previews and readers need compatible round-trip policies so saving does not silently lose promised formatting.

Announcements target roles, years, departments/faculties and sections. Lecturers publish within authorized teaching scope. Publication/expiry/archive, versioning and audit control changes. Recipient notifications are scheduled after commit, including future publication jobs, instead of notifying students before a valid article exists.

Notifications form an event inbox with read/unread state and deep links. Link policies check path/locale to avoid open redirects. Audit records actor, target, action and appropriate before/after state; audit reads also require authority. Audit proves what was recorded, not backup integrity or email delivery.

**Owners:** [announcement writes](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/engagement/service/AnnouncementWriteService.java), [HTML sanitizer](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/engagement/service/AnnouncementHtmlSanitizer.java), [scheduled notifications](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/engagement/job/ScheduledAnnouncementNotifyJob.java), [link policy](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/notification/service/NotificationLinkPolicy.java), [audit recorder](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/audit/service/AdminAuditRecorder.java), [editor tests](../../frontend/tests/editor-integrity.test.js).

## T34. Academic email: recipient scope, validation and SMTP

The compose workspace supports academic notices, registration confirmations and grade alerts. The UI computes credits/GPA from entered data and checks finite values, integer fields, grade ranges and list/string bounds before sending. Send remains actionable so validation can identify fields needing repair. Java DTO validation and body-size filtering remain authoritative.

Lecturers send only to students in their assigned sections. The server resolves recipients and student identity from governed data rather than trusting payload identity. Admin recipients must also satisfy the existing/active-account contract. Test email uses the signed-in account's email rather than a caller-selected destination. Mail authority is not an unrestricted internet relay.

Thymeleaf renders UTF-8 HTML and a plain-text alternative. Preview does not prove SMTP delivery. `MAIL_ENABLED=false` skips dispatch; even a successful controller response does not prove mailbox receipt. Live acceptance separately checks configuration, SMTP outcome and actual receipt. Async/post-commit paths keep mail failures separate from completed grading/thesis transactions.

**Owners:** [compose helpers](../../frontend/src/lib/mail-compose.ts), [workspace](../../frontend/src/app/dashboard/lecturer/mail/page.tsx), [controller/scope](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/mail/controller/MailController.java), [recipient repository](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/mail/repository/MailRecipientScopeRepository.java), [DTO limits](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/mail/dto/MailDtos.java), [email implementation](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/mail/service/EmailServiceImpl.java), [compose tests](../../frontend/tests/mail-compose.test.js).
