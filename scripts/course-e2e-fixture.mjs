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
