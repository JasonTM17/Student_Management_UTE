-- H2 parity reserves the same typed schedule projection used by PostgreSQL.
CREATE TABLE IF NOT EXISTS academic."SectionSchedule" (
    "id" VARCHAR(120) PRIMARY KEY,
    "sectionId" VARCHAR(120) NOT NULL,
    "dayOfWeek" SMALLINT NOT NULL,
    "startTime" TIME NOT NULL,
    "endTime" TIME NOT NULL,
    "version" BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT section_schedule_day_valid CHECK ("dayOfWeek" BETWEEN 1 AND 7),
    CONSTRAINT section_schedule_order_valid CHECK ("startTime" < "endTime")
);
