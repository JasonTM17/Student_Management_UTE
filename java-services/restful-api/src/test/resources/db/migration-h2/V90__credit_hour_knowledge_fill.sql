-- H2 twin of V90 is intentionally a data no-op, mirroring the V87 twin: the
-- reduced H2 migration chain does not model PostgreSQL's immutable
-- revision/release projection (knowledge_release / knowledge_runtime_*), and
-- the digest()/jsonb helpers the full seed uses are PostgreSQL-side. The
-- credit-hour FAQ document seeded by the main chain is exercised by the
-- PostgreSQL and Compose runtime CI job; H2 assistant tests assert retrieval
-- behavior against the compact corpus that already exists in this chain.
SELECT 1;
