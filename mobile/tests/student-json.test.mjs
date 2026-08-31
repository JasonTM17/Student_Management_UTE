import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

const mobileRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

test('student live JSON uses new registration contract and profile APIs', () => {
  const client = fs.readFileSync(path.join(mobileRoot, 'src/api/client.ts'), 'utf8');
  const screens = fs.readFileSync(path.join(mobileRoot, 'src/screens/student/StudentScreens.tsx'), 'utf8');
  const operations = fs.readFileSync(path.join(mobileRoot, 'src/screens/operations/OperationsScreens.tsx'), 'utf8');

  assert.match(client, /Idempotency-Key/);
  assert.match(client, /\/me\/enrollments/);
  assert.match(client, /\/me\/registration\/sections/);
  assert.match(client, /\/enrollments\/my\/transcript/);
  assert.match(client, /\/announcements\/my/);
  assert.match(client, /\/auth\/profile/);
  assert.match(client, /oldPassword/);
  assert.match(screens, /campusApi\.announcements/);
  assert.match(screens, /campusApi\.updateProfile/);
  assert.match(screens, /campusApi\.changePassword/);
  assert.doesNotMatch(operations, /campusApi\./);

  const registrationStart = screens.indexOf('export function RegistrationScreen');
  const registrationEnd = screens.indexOf('export function', registrationStart + 1);
  const registrationScreen = registrationEnd === -1
    ? screens.slice(registrationStart)
    : screens.slice(registrationStart, registrationEnd);
  assert.match(registrationScreen, /campusApi\.registrationSections/);
  assert.match(registrationScreen, /campusApi\.registrationRounds/);
  assert.doesNotMatch(registrationScreen, /campusApi\.sections\(/);

  const gradesStart = screens.indexOf('export function GradesScreen');
  const gradesEnd = screens.indexOf('export function', gradesStart + 1);
  const gradesScreen = gradesEnd === -1
    ? screens.slice(gradesStart)
    : screens.slice(gradesStart, gradesEnd);
  assert.match(gradesScreen, /campusApi\.grades\(\)/);
  assert.doesNotMatch(gradesScreen, /await campusApi\.transcript\(\);/);
});

test('student screens key enrollment lists by semester and guard profile saves', () => {
  const screens = fs.readFileSync(path.join(mobileRoot, 'src/screens/student/StudentScreens.tsx'), 'utf8');

  const screenSlice = (name) => {
    const start = screens.indexOf(`export function ${name}`);
    const end = screens.indexOf('export function', start + 1);
    return end === -1 ? screens.slice(start) : screens.slice(start, end);
  };
  const dashboard = screenSlice('StudentDashboardScreen');
  const schedule = screenSlice('ScheduleScreen');
  const courses = screenSlice('CoursesScreen');
  const grades = screenSlice('GradesScreen');
  const profile = screenSlice('ProfileScreen');

  // Retaken courses repeat the same code across semesters, so list keys must
  // include the semester (or the unique grade record id) to stay unique.
  assert.match(dashboard, /key=\{`\$\{item\.semesterId \?\? 'na'\}-\$\{item\.code\}`\}/);
  assert.match(courses, /key=\{`\$\{course\.semesterId \?\? 'na'\}-\$\{course\.code\}`\}/);
  assert.match(grades, /key=\{grade\.id\}/);
  assert.doesNotMatch(screens, /key=\{item\.code\}|key=\{course\.code\}|key=\{grade\.code\}/);
  // Dashboard and courses speak for active enrollments, not dropped history.
  assert.match(dashboard, /liveEnrollments\.filter\(\(item\) => item\.status === 'ENROLLED'\)/);
  assert.match(dashboard, /String\(activeEnrollments\.length\)/);
  assert.match(courses, /liveEnrollments\s*\.filter\(\(item\) => item\.status === 'ENROLLED'\)/);
  // Schedule times must build with the same separator the card parses.
  assert.match(schedule, /slot\.startTime\} – \$\{slot\.endTime\}/);
  // Profile and password actions clear stale messages, validate input, and
  // disable their buttons while a save is in flight.
  assert.match(profile, /const \[isSavingProfile, setIsSavingProfile\] = useState\(false\)/);
  assert.match(profile, /const \[isSavingPassword, setIsSavingPassword\] = useState\(false\)/);
  assert.match(profile, /disabled=\{isSavingProfile\}/);
  assert.match(profile, /disabled=\{isSavingPassword\}/);
  assert.match(profile, /newPassword\.length < 8/);
  assert.match(profile, /setProfileMessage\(null\)/);
});

test('student screens translate service errors into student-friendly copy', () => {
  const screens = fs.readFileSync(path.join(mobileRoot, 'src/screens/student/StudentScreens.tsx'), 'utf8');

  assert.match(screens, /studentFriendlyErrorMessage/);
  assert.match(screens, /friendlyErrorMessageByCode/);
  assert.match(screens, /Registration is not open right now\./);
  assert.doesNotMatch(screens, /ApiClientError \? nextError\.message/);
  assert.doesNotMatch(screens, /nextError instanceof ApiClientError \? nextError\.message/);
  assert.doesNotMatch(screens, /Java API contract/);
});
