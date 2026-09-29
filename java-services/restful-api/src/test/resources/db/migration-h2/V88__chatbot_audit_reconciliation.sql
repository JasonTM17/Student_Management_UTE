-- H2 twin of V88 is intentionally a data no-op: the reduced H2 migration
-- chain does not model PostgreSQL's immutable revision/release projection
-- (knowledge_release / knowledge_runtime_*), and the digest()/jsonb helpers
-- the reconciliation uses are PostgreSQL-side. The reconciled corpus is
-- exercised by the PostgreSQL and Compose runtime CI job.
SELECT 1;
