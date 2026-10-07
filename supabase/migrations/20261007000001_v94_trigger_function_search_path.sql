-- Supabase advisor WARN `function_search_path_mutable` on the three V94
-- thesis trigger functions; bodies are schema-qualified so pg_catalog pin is
-- behaviour-neutral. Mirrors java Flyway V107.
ALTER FUNCTION thesis.derive_group_member_active_participation() SET search_path = pg_catalog;
ALTER FUNCTION thesis.prevent_cancelled_group_reopen() SET search_path = pg_catalog;
ALTER FUNCTION thesis.release_cancelled_group_memberships() SET search_path = pg_catalog;
