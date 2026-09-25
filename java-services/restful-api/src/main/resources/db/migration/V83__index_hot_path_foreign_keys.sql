-- V83: Index the hot-path foreign keys that ship without a supporting index.
-- Every lookup below is a sequential scan today: the lecturer timetable and
-- section assignment reads hit academic."Section"."lecturerId", the admin
-- catalog browses Course by department and semester, the announcement feed
-- filters by category (V64 added the column and FK without an index), and
-- council membership checks resolve by lecturer (V81 added the FK but no
-- index on the column). Idempotent, DDL-only, safe to replay.
CREATE INDEX IF NOT EXISTS academic_section_lecturer_idx
    ON academic."Section" ("lecturerId");

CREATE INDEX IF NOT EXISTS academic_course_department_idx
    ON academic."Course" ("departmentId");

CREATE INDEX IF NOT EXISTS academic_course_semester_idx
    ON academic."Course" ("semesterId");

CREATE INDEX IF NOT EXISTS engagement_announcement_category_idx
    ON engagement."Announcement" ("categoryId");

CREATE INDEX IF NOT EXISTS thesis_council_member_lecturer_idx
    ON thesis.thesis_council_member ("lecturer_id");
