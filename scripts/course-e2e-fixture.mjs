// Only used after the E2E runner has created its collision-checked stack.
// Real registration deadlines and Flyway migration checksums stay untouched.
// V76 closes any round whose windowEnd has passed, so widening the demo
// windows alone is not enough: the fixture must also re-open the rounds it
// widens, otherwise the register flow renders "no round is open" on a stack
// that is supposed to demonstrate a live registration window.
export const registrationWindowSql = `
DO $$
DECLARE updated integer;
BEGIN
  UPDATE academic."RegistrationRound"
  SET "windowStart" = CURRENT_TIMESTAMP - INTERVAL '1 day',
      "windowEnd" = CURRENT_TIMESTAMP + INTERVAL '1 day',
      "status" = 'OPEN'
  WHERE "id" IN ('round-registration-demo', 'round-add-drop-demo')
    AND "semesterId" = 'semester-demo';
  GET DIAGNOSTICS updated = ROW_COUNT;
  IF updated <> 2 THEN
    RAISE EXCEPTION 'Expected exactly two demo registration rounds';
  END IF;
END $$;
`;

// Only enabled by the focused report-upload browser test. Prepare a valid
// approved leader group and a writable report window in the disposable DB.
export const reportWindowSql = `
DO $$
BEGIN
  IF (SELECT COUNT(*) FROM thesis.thesis_registration_round
      WHERE id = '22d65ee6-f485-40ae-a66b-528d35007745') <> 1 THEN
    RAISE EXCEPTION 'Expected the isolated TLCN report round';
  END IF;
  INSERT INTO thesis.thesis_group
      (id, round_id, leader_student_id, topic_id, status, approval_status,
       approved_by, approved_at)
  SELECT 'a471fb89-7ba4-48cc-95b5-8514a43f8aa1',
         '22d65ee6-f485-40ae-a66b-528d35007745',
         'student-profile', t.id, 'ASSIGNED', 'APPROVED', 'report-e2e', CURRENT_TIMESTAMP
    FROM thesis.thesis_topic t
   WHERE t.round_id = '22d65ee6-f485-40ae-a66b-528d35007745'
     AND t.status = 'PUBLISHED'
   ORDER BY t.id LIMIT 1;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'Expected a published topic for the isolated report round';
  END IF;
  INSERT INTO thesis.thesis_group_member
      (id, group_id, round_id, student_id, member_order, is_leader)
  VALUES
      ('a471fb89-7ba4-48cc-95b5-8514a43f8aa2',
       'a471fb89-7ba4-48cc-95b5-8514a43f8aa1',
       '22d65ee6-f485-40ae-a66b-528d35007745', 'student-profile', 1, TRUE),
      ('a471fb89-7ba4-48cc-95b5-8514a43f8aa3',
       'a471fb89-7ba4-48cc-95b5-8514a43f8aa1',
       '22d65ee6-f485-40ae-a66b-528d35007745', 'report-e2e-member-2', 2, FALSE),
      ('a471fb89-7ba4-48cc-95b5-8514a43f8aa4',
       'a471fb89-7ba4-48cc-95b5-8514a43f8aa1',
       '22d65ee6-f485-40ae-a66b-528d35007745', 'report-e2e-member-3', 3, FALSE);
  UPDATE thesis.thesis_registration_round
     SET status = 'REGISTRATION_CLOSED',
         registration_end = CURRENT_TIMESTAMP - INTERVAL '1 hour',
         gvpb_deadline = CURRENT_TIMESTAMP + INTERVAL '7 days'
   WHERE id = '22d65ee6-f485-40ae-a66b-528d35007745';
END $$;
`;

// Only enabled by the withdrawal browser journey. Re-opens the demo
// semester's withdrawal window ([startDate, startDate + 14d], Bangkok civil
// time) and adds one OPEN-but-stale semester with a seeded enrollment so the
// closed-window refusal can be asserted on a real row. Idempotent.
export const withdrawalWindowSql = `
DO $$
BEGIN
  UPDATE academic."Semester"
     SET "startDate" = CURRENT_TIMESTAMP - INTERVAL '3 days'
   WHERE "id" = 'semester-demo' AND "status" = 'OPEN';
  IF NOT FOUND THEN
    RAISE EXCEPTION 'Expected an open semester-demo for the withdrawal journey';
  END IF;

  INSERT INTO academic."Semester"
      ("id", "name", "nameEn", "nameVi", "type", "academicYearId",
       "startDate", "endDate", "status")
  VALUES ('wd2-sem-late', 'Học kỳ bổ sung 2026', 'Supplementary Term 2026',
          'Học kỳ bổ sung 2026', 'FIRST', 'academic-year-demo',
          CURRENT_TIMESTAMP - INTERVAL '30 days',
          CURRENT_TIMESTAMP + INTERVAL '60 days', 'OPEN')
  ON CONFLICT ("id") DO NOTHING;

  INSERT INTO academic."Course"
      ("id", "code", "name", "nameEn", "nameVi", "credits", "departmentId", "semesterId")
  VALUES ('wd2-course-late', 'WD101', 'Học phần kỳ trễ', 'Late Term Course',
          'Học phần kỳ trễ', 3, 'department-demo', 'wd2-sem-late')
  ON CONFLICT ("id") DO NOTHING;

  INSERT INTO academic."Section"
      ("id", "sectionNumber", "courseId", "semesterId", "lecturerId",
       "classroomId", "capacity", "enrolledCount", "status")
  VALUES ('wd2-sec-late', 'WD101-01', 'wd2-course-late', 'wd2-sem-late',
          'lecturer-profile', 'classroom-a101', 40, 1, 'OPEN')
  ON CONFLICT ("id") DO NOTHING;

  INSERT INTO academic."Enrollment"
      ("id", "studentId", "sectionId", "semesterId", "status", "enrolledAt",
       "gradeStatus", "courseId", "roundId", "creditsSnapshot", "version")
  VALUES ('wd2-enr-late', 'student-profile', 'wd2-sec-late', 'wd2-sem-late',
          'ENROLLED', CURRENT_TIMESTAMP, 'NOT_GRADED', 'wd2-course-late',
          NULL, 3, 0)
  ON CONFLICT ("id") DO NOTHING;
END $$;
`;

// A dedicated, demo-only published grade for the appeal/review browser journey.
// It uses the standard demo student and lecturer accounts but does not mutate
// any of their existing course records.
export const gradeAppealSql = `
INSERT INTO academic."Course"
    ("id", "code", "name", "nameEn", "nameVi", "credits", "departmentId", "semesterId")
VALUES ('course-grade-appeal-e2e', 'APPEAL1E2E', 'Điểm phúc khảo mẫu',
        'Grade appeal demo', 'Điểm phúc khảo mẫu', 3, 'department-demo', 'semester-demo')
ON CONFLICT ("id") DO UPDATE SET
    "code" = EXCLUDED."code", "name" = EXCLUDED."name", "nameEn" = EXCLUDED."nameEn",
    "nameVi" = EXCLUDED."nameVi", "credits" = EXCLUDED."credits",
    "departmentId" = EXCLUDED."departmentId", "semesterId" = EXCLUDED."semesterId";

INSERT INTO academic."Section"
    ("id", "sectionNumber", "courseId", "semesterId", "lecturerId", "classroomId",
     "capacity", "enrolledCount", "status")
VALUES ('section-grade-appeal-e2e', 'APPEAL1E2E-01', 'course-grade-appeal-e2e',
        'semester-demo', 'lecturer-profile', NULL, 40, 1, 'OPEN')
ON CONFLICT ("id") DO UPDATE SET
    "sectionNumber" = EXCLUDED."sectionNumber", "courseId" = EXCLUDED."courseId",
    "semesterId" = EXCLUDED."semesterId", "lecturerId" = EXCLUDED."lecturerId",
    "capacity" = EXCLUDED."capacity", "enrolledCount" = EXCLUDED."enrolledCount",
    "status" = EXCLUDED."status";

INSERT INTO academic."GradeItem"
    ("id", "sectionId", "name", "type", "maxScore", "weight", "gradedAt")
VALUES ('appeal-e2e-process', 'section-grade-appeal-e2e', 'Process', 'PROCESS', 10, 50, CURRENT_TIMESTAMP),
       ('appeal-e2e-final', 'section-grade-appeal-e2e', 'Final exam', 'FINAL', 10, 50, CURRENT_TIMESTAMP)
ON CONFLICT ("id") DO UPDATE SET
    "sectionId" = EXCLUDED."sectionId", "name" = EXCLUDED."name", "type" = EXCLUDED."type",
    "maxScore" = EXCLUDED."maxScore", "weight" = EXCLUDED."weight", "gradedAt" = EXCLUDED."gradedAt";

INSERT INTO academic."Enrollment"
    ("id", "studentId", "sectionId", "semesterId", "status", "enrolledAt", "gradeStatus",
     "finalGrade", "letterGrade", "courseId", "roundId", "creditsSnapshot", "version", "gradePublishedAt")
VALUES ('enrollment-grade-appeal-e2e', 'student-profile', 'section-grade-appeal-e2e',
        'semester-demo', 'COMPLETED', CURRENT_TIMESTAMP, 'PUBLISHED', 8.00, 'B+',
        'course-grade-appeal-e2e', NULL, 3, 0, CURRENT_TIMESTAMP)
ON CONFLICT ("id") DO UPDATE SET
    "studentId" = EXCLUDED."studentId", "sectionId" = EXCLUDED."sectionId",
    "semesterId" = EXCLUDED."semesterId", "status" = EXCLUDED."status",
    "gradeStatus" = EXCLUDED."gradeStatus", "finalGrade" = EXCLUDED."finalGrade",
    "letterGrade" = EXCLUDED."letterGrade", "courseId" = EXCLUDED."courseId",
    "roundId" = EXCLUDED."roundId", "creditsSnapshot" = EXCLUDED."creditsSnapshot",
    "version" = EXCLUDED."version", "gradePublishedAt" = EXCLUDED."gradePublishedAt";

INSERT INTO academic."StudentGrade" ("id", "enrollmentId", "gradeItemId", "score")
VALUES ('appeal-e2e-student-process', 'enrollment-grade-appeal-e2e', 'appeal-e2e-process', 8.00),
       ('appeal-e2e-student-final', 'enrollment-grade-appeal-e2e', 'appeal-e2e-final', 8.00)
ON CONFLICT ("id") DO UPDATE SET
    "enrollmentId" = EXCLUDED."enrollmentId", "gradeItemId" = EXCLUDED."gradeItemId",
    "score" = EXCLUDED."score";
`;

export async function seedWithdrawalE2e(projectName, compose) {
  if (!/^campuscore-course-e2e-[a-z0-9-]+$/i.test(projectName)) {
    throw new Error('Refusing withdrawal fixture outside a disposable E2E project');
  }
  await compose([
    'exec', '-T', 'postgres', 'sh', '-c',
    'exec psql --no-psqlrc --set ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" --command "$1"',
    'course-e2e-withdrawal-fixture', withdrawalWindowSql,
  ]);
}

export async function seedGradeAppealE2e(projectName, compose) {
  if (!/^campuscore-course-e2e-[a-z0-9-]+$/i.test(projectName)) {
    throw new Error('Refusing grade appeal fixture outside a disposable E2E project');
  }
  await compose([
    'exec', '-T', 'postgres', 'sh', '-c',
    'exec psql --no-psqlrc --set ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" --command "$1"',
    'course-e2e-grade-appeal-fixture', gradeAppealSql,
  ]);
}

export async function seedCourseE2e(projectName, compose) {
  if (!/^campuscore-course-e2e-[a-z0-9-]+$/i.test(projectName)) {
    throw new Error('Refusing registration fixture outside a disposable E2E project');
  }
  await compose([
    'exec', '-T', 'postgres', 'sh', '-c',
    'exec psql --no-psqlrc --set ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" --command "$1"',
    'course-e2e-fixture', registrationWindowSql,
  ]);
}

export async function seedReportE2e(projectName, compose) {
  if (!/^campuscore-course-e2e-[a-z0-9-]+$/i.test(projectName)) {
    throw new Error('Refusing report fixture outside a disposable E2E project');
  }
  await compose([
    'exec', '-T', 'postgres', 'sh', '-c',
    'exec psql --no-psqlrc --set ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" --command "$1"',
    'course-e2e-report-fixture', reportWindowSql,
  ]);
}
