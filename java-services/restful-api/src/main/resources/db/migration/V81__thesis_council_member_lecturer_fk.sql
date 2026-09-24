-- V81: Enforce referential integrity on thesis_council_member.lecturer_id -> academic."Lecturer"(id).
--
-- V79 normalized all lecturer_id values in thesis_council_member to match Lecturer profile IDs.
-- This constraint ensures future migrations or direct SQL operations cannot introduce invalid
-- or user-account identifiers into thesis council memberships.

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_thesis_council_member_lecturer'
          AND table_schema = 'thesis'
          AND table_name = 'thesis_council_member'
    ) THEN
        ALTER TABLE thesis.thesis_council_member
            ADD CONSTRAINT fk_thesis_council_member_lecturer
            FOREIGN KEY (lecturer_id)
            REFERENCES academic."Lecturer"(id)
            ON DELETE RESTRICT;
    END IF;
END $$;
