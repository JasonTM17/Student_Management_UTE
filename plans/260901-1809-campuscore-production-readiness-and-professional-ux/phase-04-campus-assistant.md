# Phase 04 — Campus assistant and knowledge lifecycle

Introduce `/api/v1/assistant/**`, `/api/v1/admin/assistant/knowledge/**`, `/internal/rag/assistant/**`, and one-release deprecated aliases for the thesis paths. Keep chat JSON/SSE/conversation/feedback/cancel wire compatibility. Add domains `THESIS`, `REGISTRATION`, `ACADEMIC_CATALOG`, `ANNOUNCEMENT`, `POLICY`, `GENERAL_FAQ` and prevent personalized/PII answers.

Make Supabase the production authoring authority behind the authenticated Java admin API; only the private RAG service receives its service key. Add revision/release metadata and an immutable canonical manifest. Add Flyway V16 staged projection plus atomic `active_release_id`; fetch, validate, stage and switch in one transaction, retaining the last good release on every failure. Reconcile on publish/manual trigger and every 15 minutes.

Use DeepSeek only as bounded synthesizer with explicit non-thinking request configuration and lexical citation-owned fallback. Implement `*_FILE` secret resolution with file precedence and fail-closed production validation. Exit criterion: backend tests prove role isolation, release atomicity, fallback and JSON/SSE behavior.
