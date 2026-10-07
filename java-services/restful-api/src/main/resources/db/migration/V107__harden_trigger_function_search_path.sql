-- Supabase advisor WARN `function_search_path_mutable`: the V94 trigger
-- functions run with the caller's mutable search_path. Their bodies are fully
-- schema-qualified, so pin the search_path to pg_catalog (always implicitly
-- searched) — same convention as the supabase 20260927 pin — no behaviour
-- change, closes the injection vector.

ALTER FUNCTION thesis.derive_group_member_active_participation() SET search_path = pg_catalog;
ALTER FUNCTION thesis.prevent_cancelled_group_reopen() SET search_path = pg_catalog;
ALTER FUNCTION thesis.release_cancelled_group_memberships() SET search_path = pg_catalog;
