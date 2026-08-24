DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM academic."SectionSchedule"
        WHERE "dayOfWeek" NOT BETWEEN 1 AND 7
           OR "startTime" !~ '^([01][0-9]|2[0-3]):[0-5][0-9](:[0-5][0-9])?$'
           OR "endTime" !~ '^([01][0-9]|2[0-3]):[0-5][0-9](:[0-5][0-9])?$'
    ) THEN
        RAISE EXCEPTION 'V15 preflight failed: invalid section schedule day/time';
    END IF;
END $$;

ALTER TABLE academic."SectionSchedule" ADD COLUMN IF NOT EXISTS "startTimeValue" TIME;
ALTER TABLE academic."SectionSchedule" ADD COLUMN IF NOT EXISTS "endTimeValue" TIME;
ALTER TABLE academic."SectionSchedule" ADD COLUMN IF NOT EXISTS "version" BIGINT NOT NULL DEFAULT 0;

UPDATE academic."SectionSchedule"
SET "startTimeValue" = "startTime"::time,
    "endTimeValue" = "endTime"::time
WHERE "startTimeValue" IS NULL OR "endTimeValue" IS NULL;

ALTER TABLE academic."SectionSchedule"
    ALTER COLUMN "startTimeValue" SET NOT NULL;
ALTER TABLE academic."SectionSchedule"
    ALTER COLUMN "endTimeValue" SET NOT NULL;
ALTER TABLE academic."SectionSchedule"
    ADD CONSTRAINT section_schedule_time_order CHECK ("startTimeValue" < "endTimeValue");

CREATE INDEX IF NOT EXISTS section_schedule_slot_idx
    ON academic."SectionSchedule" ("dayOfWeek", "startTimeValue", "endTimeValue");
