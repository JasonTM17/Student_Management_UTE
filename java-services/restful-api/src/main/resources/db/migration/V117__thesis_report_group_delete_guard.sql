-- Tighten the thesis report delete rule from CASCADE to RESTRICT.
--
-- V30 declared thesis_group_report.group_id with ON DELETE CASCADE, so any
-- group delete — a fixture cleanup, a demo reconciliation like V112, or a
-- future migration — silently erased the report row and orphaned the stored
-- artifact. After this migration every group delete must remove the report
-- row explicitly first: a silent cascade loss becomes a loud referential
-- error, which is exactly the intended guard.
--
-- The DO block mirrors the V66 census style: fail loudly on data the new
-- constraint could not hold (orphaned report rows) and resolve the actual FK
-- name instead of assuming the PostgreSQL-generated one, before replacing it.

DO $$
DECLARE
    orphan_count INTEGER;
    fk_name TEXT;
BEGIN
    SELECT COUNT(*) INTO orphan_count
    FROM thesis.thesis_group_report r
    WHERE NOT EXISTS (
        SELECT 1 FROM thesis.thesis_group g WHERE g.id = r.group_id);
    IF orphan_count > 0 THEN
        RAISE EXCEPTION
            'V117 stopped: % thesis report rows reference a missing thesis group; reconcile them before tightening the delete rule',
            orphan_count;
    END IF;

    -- The V30 inline REFERENCES clause let PostgreSQL name the constraint
    -- (thesis_group_report_group_id_fkey). Resolve the live name in case an
    -- environment renamed it.
    SELECT c.conname INTO fk_name
    FROM pg_constraint c
    JOIN pg_class t ON t.oid = c.conrelid
    JOIN pg_namespace n ON n.oid = t.relnamespace
    JOIN pg_class rt ON rt.oid = c.confrelid
    JOIN pg_namespace rn ON rn.oid = rt.relnamespace
    JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = c.conkey[1]
    WHERE c.contype = 'f'
      AND n.nspname = 'thesis'
      AND t.relname = 'thesis_group_report'
      AND a.attname = 'group_id'
      AND rn.nspname = 'thesis'
      AND rt.relname = 'thesis_group'
    LIMIT 1;
    IF fk_name IS NOT NULL THEN
        EXECUTE format('ALTER TABLE thesis.thesis_group_report DROP CONSTRAINT %I', fk_name);
    END IF;
END $$;

ALTER TABLE thesis.thesis_group_report
    ADD CONSTRAINT thesis_group_report_group_id_fkey
    FOREIGN KEY (group_id) REFERENCES thesis.thesis_group (id) ON DELETE RESTRICT;
