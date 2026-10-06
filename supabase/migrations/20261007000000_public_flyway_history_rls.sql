-- Supabase security lint: rls_disabled_in_public
--
-- public.flyway_schema_history is a leftover from the pre-default-schema
-- deployment (Flyway now writes thesis.flyway_schema_history; the public copy
-- stopped receiving rows on 2026-10-02). Keeping the table preserves the
-- migration audit trail, but PostgREST exposes the public schema to
-- anon/authenticated, so the table must be RLS-enabled. No policies are
-- created: non-owner roles get deny-all, while the owner (postgres /
-- service_role datasource) retains full access for any future Flyway write.

ALTER TABLE IF EXISTS public.flyway_schema_history ENABLE ROW LEVEL SECURITY;
