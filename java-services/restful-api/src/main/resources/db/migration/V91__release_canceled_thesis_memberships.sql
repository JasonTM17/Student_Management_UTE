-- Preserve historical membership rows while releasing cancelled groups' active seats.
-- The table lock makes the backfill, uniqueness replacement, and trigger installation one
-- migration boundary; rollback after applying this migration must be a forward correction.
LOCK TABLE thesis.thesis_group, thesis.thesis_group_member IN SHARE ROW EXCLUSIVE MODE;

ALTER TABLE thesis.thesis_group_member
    ADD COLUMN IF NOT EXISTS active_participation BOOLEAN NOT NULL DEFAULT TRUE;

UPDATE thesis.thesis_group_member member
SET active_participation = FALSE
FROM thesis.thesis_group group_row
WHERE group_row.id = member.group_id
  AND group_row.status = 'CANCELLED';

ALTER TABLE thesis.thesis_group_member
    DROP CONSTRAINT IF EXISTS thesis_student_one_group_per_round;

CREATE UNIQUE INDEX IF NOT EXISTS thesis_student_one_active_group_per_round
    ON thesis.thesis_group_member (round_id, student_id)
    WHERE active_participation = TRUE;

-- The marker is derived from the parent state, so direct membership writes cannot opt out
-- of active uniqueness by supplying FALSE. A cancelled roster accepts only the marker update
-- performed by the parent cancellation trigger and remains otherwise immutable at this boundary.
CREATE OR REPLACE FUNCTION thesis.derive_group_member_active_participation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    group_status VARCHAR(32);
BEGIN
    -- Inspect OLD as well as NEW: moving a historical row to an active parent
    -- must not bypass cancelled-roster immutability. UPDATE also fences ordinary
    -- non-key status updates until concurrent membership writes have committed.
    IF TG_OP = 'UPDATE' AND OLD.group_id IS DISTINCT FROM NEW.group_id THEN
        PERFORM id FROM thesis.thesis_group
        WHERE id IN (OLD.group_id, NEW.group_id)
        ORDER BY id
        FOR UPDATE;
    END IF;

    IF TG_OP IN ('UPDATE', 'DELETE') THEN
        SELECT status INTO group_status
        FROM thesis.thesis_group
        WHERE id = OLD.group_id
        FOR UPDATE;
        IF group_status = 'CANCELLED' THEN
            IF TG_OP = 'UPDATE'
                    AND OLD.active_participation = TRUE
                    AND NEW.active_participation = FALSE
                    AND (to_jsonb(NEW) - 'active_participation')
                        = (to_jsonb(OLD) - 'active_participation') THEN
                RETURN NEW;
            END IF;
            RAISE EXCEPTION 'Cancelled thesis group % has a read-only roster', OLD.group_id
                USING ERRCODE = '23514', CONSTRAINT = 'thesis_cancelled_group_roster_read_only';
        END IF;
        IF TG_OP = 'DELETE' THEN
            RETURN OLD;
        END IF;
    END IF;

    SELECT status INTO group_status
    FROM thesis.thesis_group
    WHERE id = NEW.group_id
    FOR UPDATE;
    IF NOT FOUND THEN
        RETURN NEW;
    END IF;

    IF group_status = 'CANCELLED' THEN
        RAISE EXCEPTION 'Cancelled thesis group % has a read-only roster', NEW.group_id
            USING ERRCODE = '23514', CONSTRAINT = 'thesis_cancelled_group_roster_read_only';
    END IF;

    NEW.active_participation := TRUE;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS thesis_group_member_active_participation ON thesis.thesis_group_member;
CREATE TRIGGER thesis_group_member_active_participation
BEFORE INSERT OR UPDATE OR DELETE ON thesis.thesis_group_member
FOR EACH ROW EXECUTE FUNCTION thesis.derive_group_member_active_participation();

CREATE OR REPLACE FUNCTION thesis.prevent_cancelled_group_reopen()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF OLD.status = 'CANCELLED' AND NEW.status <> 'CANCELLED' THEN
        RAISE EXCEPTION 'Cancelled thesis group % cannot reopen', OLD.id
            USING ERRCODE = '23514', CONSTRAINT = 'thesis_cancelled_group_terminal';
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS thesis_cancelled_group_terminal ON thesis.thesis_group;
CREATE TRIGGER thesis_cancelled_group_terminal
BEFORE UPDATE OF status ON thesis.thesis_group
FOR EACH ROW EXECUTE FUNCTION thesis.prevent_cancelled_group_reopen();

CREATE OR REPLACE FUNCTION thesis.release_cancelled_group_memberships()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.status = 'CANCELLED' AND OLD.status IS DISTINCT FROM 'CANCELLED' THEN
        UPDATE thesis.thesis_group_member
        SET active_participation = FALSE
        WHERE group_id = NEW.id
          AND active_participation = TRUE;
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS thesis_release_cancelled_group_memberships ON thesis.thesis_group;
CREATE TRIGGER thesis_release_cancelled_group_memberships
AFTER UPDATE OF status ON thesis.thesis_group
FOR EACH ROW EXECUTE FUNCTION thesis.release_cancelled_group_memberships();
