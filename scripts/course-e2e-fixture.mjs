// Only used after the E2E runner has created its collision-checked stack.
// Real registration deadlines and Flyway migration checksums stay untouched.
export const registrationWindowSql = `
DO $$
DECLARE updated integer;
BEGIN
  UPDATE academic."RegistrationRound"
  SET "windowStart" = CURRENT_TIMESTAMP - INTERVAL '1 day',
      "windowEnd" = CURRENT_TIMESTAMP + INTERVAL '1 day'
  WHERE "id" IN ('round-registration-demo', 'round-add-drop-demo')
    AND "semesterId" = 'semester-demo';
  GET DIAGNOSTICS updated = ROW_COUNT;
  IF updated <> 2 THEN
    RAISE EXCEPTION 'Expected exactly two demo registration rounds';
  END IF;
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
