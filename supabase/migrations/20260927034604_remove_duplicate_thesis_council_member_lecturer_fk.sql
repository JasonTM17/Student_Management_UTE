-- Remove the duplicate lecturer FK while preserving the canonical constraint.
-- The table lock keeps the catalog preflight and the drop in one DDL window.
SET lock_timeout = '3s';
LOCK TABLE thesis.thesis_council_member IN ACCESS EXCLUSIVE MODE;

DO $$
DECLARE
    member_table oid := to_regclass('thesis.thesis_council_member');
    lecturer_table oid := to_regclass('academic."Lecturer"');
    lecturer_column smallint;
    id_column smallint;
    canonical pg_constraint%ROWTYPE;
    duplicate_constraint pg_constraint%ROWTYPE;
    relation_count integer;
    expected_definition constant text :=
        'FOREIGN KEY (lecturer_id) REFERENCES academic."Lecturer"(id) ON DELETE RESTRICT';
BEGIN
    IF member_table IS NULL OR lecturer_table IS NULL THEN
        RAISE EXCEPTION 'Expected thesis council member and academic lecturer tables';
    END IF;

    SELECT attnum INTO lecturer_column
      FROM pg_attribute
     WHERE attrelid = member_table AND attname = 'lecturer_id' AND NOT attisdropped;
    SELECT attnum INTO id_column
      FROM pg_attribute
     WHERE attrelid = lecturer_table AND attname = 'id' AND NOT attisdropped;
    IF lecturer_column IS NULL OR id_column IS NULL THEN
        RAISE EXCEPTION 'Expected FK columns are missing';
    END IF;

    SELECT * INTO canonical
      FROM pg_constraint
     WHERE conrelid = member_table AND conname = 'fk_thesis_council_member_lecturer';
    IF canonical.oid IS NULL
       OR canonical.contype <> 'f'
       OR NOT canonical.convalidated
       OR canonical.condeferrable
       OR canonical.condeferred
       OR canonical.conkey <> ARRAY[lecturer_column]::smallint[]
       OR canonical.confrelid <> lecturer_table
       OR canonical.confkey <> ARRAY[id_column]::smallint[]
       OR canonical.confdeltype <> 'r'
       OR canonical.confupdtype <> 'a'
       OR canonical.confmatchtype <> 's'
       OR pg_get_constraintdef(canonical.oid, true) <> expected_definition THEN
        RAISE EXCEPTION 'Canonical lecturer FK is missing or differs from the expected definition';
    END IF;

    SELECT * INTO duplicate_constraint
      FROM pg_constraint
     WHERE conrelid = member_table AND conname = 'thesis_council_member_lecturer_fk';

    SELECT count(*) INTO relation_count
      FROM pg_constraint
     WHERE conrelid = member_table
       AND contype = 'f'
       AND conkey = ARRAY[lecturer_column]::smallint[]
       AND confrelid = lecturer_table
       AND confkey = ARRAY[id_column]::smallint[]
       AND convalidated
       AND NOT condeferrable
       AND NOT condeferred
       AND confdeltype = 'r'
       AND confupdtype = 'a'
       AND confmatchtype = 's';

    IF duplicate_constraint.oid IS NOT NULL THEN
        IF duplicate_constraint.contype <> 'f'
           OR NOT duplicate_constraint.convalidated
           OR duplicate_constraint.condeferrable
           OR duplicate_constraint.condeferred
           OR duplicate_constraint.conkey <> ARRAY[lecturer_column]::smallint[]
           OR duplicate_constraint.confrelid <> lecturer_table
           OR duplicate_constraint.confkey <> ARRAY[id_column]::smallint[]
           OR duplicate_constraint.confdeltype <> 'r'
           OR duplicate_constraint.confupdtype <> 'a'
           OR duplicate_constraint.confmatchtype <> 's'
           OR pg_get_constraintdef(duplicate_constraint.oid, true) <> expected_definition
           OR relation_count <> 2 THEN
            RAISE EXCEPTION 'Named lecturer FK is not an exact duplicate; no constraint was removed';
        END IF;

        ALTER TABLE thesis.thesis_council_member
            DROP CONSTRAINT thesis_council_member_lecturer_fk;
    ELSIF relation_count <> 1 THEN
        RAISE EXCEPTION 'Unexpected lecturer FK count; expected only the canonical constraint';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
         WHERE conrelid = member_table
           AND conname = 'fk_thesis_council_member_lecturer'
           AND contype = 'f'
           AND convalidated
           AND pg_get_constraintdef(oid, true) = expected_definition
    )
       OR EXISTS (
        SELECT 1 FROM pg_constraint
         WHERE conrelid = member_table
           AND conname = 'thesis_council_member_lecturer_fk'
    ) THEN
        RAISE EXCEPTION 'Lecturer FK postflight failed';
    END IF;

    SELECT count(*) INTO relation_count
      FROM pg_constraint
     WHERE conrelid = member_table
       AND contype = 'f'
       AND conkey = ARRAY[lecturer_column]::smallint[]
       AND confrelid = lecturer_table
       AND confkey = ARRAY[id_column]::smallint[];
    IF relation_count <> 1 THEN
        RAISE EXCEPTION 'Expected exactly one lecturer_id FK after cleanup';
    END IF;
END;
$$;

RESET lock_timeout;
