-- Pin trusted lookup for schema-qualified thesis helper functions.
ALTER FUNCTION thesis.digest(text, text) SET search_path = pg_catalog;
ALTER FUNCTION thesis.validate_group_membership_state(uuid) SET search_path = pg_catalog;
ALTER FUNCTION thesis.enforce_group_membership_state() SET search_path = pg_catalog;
