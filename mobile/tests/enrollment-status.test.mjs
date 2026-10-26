import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);
const ts = require('typescript');

const mobileRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

function read(relativePath) {
  return fs.readFileSync(path.join(mobileRoot, relativePath), 'utf8');
}

function transpile(relativePath) {
  return ts.transpileModule(read(relativePath), {
    compilerOptions: {
      module: ts.ModuleKind.CommonJS,
      target: ts.ScriptTarget.ES2020,
      esModuleInterop: true,
    },
    fileName: path.basename(relativePath),
  }).outputText;
}

function loadStatusModule() {
  const module = { exports: {} };
  new Function(
    'exports',
    'require',
    'module',
    'process',
    transpile('src/lib/enrollment-status.ts'),
  )(module.exports, () => ({}), module, { env: {} });
  return module.exports;
}

function screenSlice(name) {
  const screens = read('src/screens/student/StudentScreens.tsx');
  const start = screens.indexOf(`export function ${name}`);
  const end = screens.indexOf('export function', start + 1);
  return end === -1 ? screens.slice(start) : screens.slice(start, end);
}

const row = (status, sectionStatus = 'OPEN') => ({ status, section: { status: sectionStatus } });

test('isActiveEnrollment mirrors the seat-counting set and drops cancelled sections', () => {
  const { isActiveEnrollment } = loadStatusModule();

  assert.equal(isActiveEnrollment(row('ENROLLED')), true);
  assert.equal(isActiveEnrollment(row('PENDING')), true);
  assert.equal(isActiveEnrollment(row('CONFIRMED')), true);
  assert.equal(isActiveEnrollment(row('COMPLETED')), false);
  assert.equal(isActiveEnrollment(row('DROPPED')), false);
  assert.equal(isActiveEnrollment(row('WITHDRAWN')), false);
  assert.equal(isActiveEnrollment(row('CANCELLED')), false);
  assert.equal(isActiveEnrollment(row('ENROLLED', 'CANCELLED')), false);
  assert.equal(isActiveEnrollment(null), false);
  assert.equal(isActiveEnrollment(row(undefined)), false);
});

test('isScheduledEnrollment keeps completed history but excludes dropped rows', () => {
  const { isScheduledEnrollment } = loadStatusModule();

  assert.equal(isScheduledEnrollment(row('ENROLLED')), true);
  assert.equal(isScheduledEnrollment(row('PENDING')), true);
  assert.equal(isScheduledEnrollment(row('CONFIRMED')), true);
  assert.equal(isScheduledEnrollment(row('COMPLETED')), true);
  assert.equal(isScheduledEnrollment(row('DROPPED')), false);
  assert.equal(isScheduledEnrollment(row('WITHDRAWN')), false);
  assert.equal(isScheduledEnrollment(row('COMPLETED', 'CANCELLED')), false);
});

test('isDroppableEnrollment matches the backend drop gate', () => {
  const { isDroppableEnrollment } = loadStatusModule();

  assert.equal(isDroppableEnrollment(row('ENROLLED')), true);
  assert.equal(isDroppableEnrollment(row('PENDING')), true);
  assert.equal(isDroppableEnrollment(row('CONFIRMED')), false);
  assert.equal(isDroppableEnrollment(row('DROPPED')), false);
  assert.equal(isDroppableEnrollment(row('WITHDRAWN')), false);
});

test('dashboard and courses count the full active set, not ENROLLED only', () => {
  const dashboard = screenSlice('StudentDashboardScreen');
  const courses = screenSlice('CoursesScreen');

  // ENROLLED-only predicates hide PENDING/CONFIRMED registrations that the
  // backend already counts against seats and term credits.
  assert.match(dashboard, /liveEnrollments\.filter\(isActiveEnrollment\)/);
  assert.match(courses, /liveEnrollments\s*\r?\n?\s*\.filter\(isActiveEnrollment\)/);
  assert.doesNotMatch(dashboard, /item\.status === 'ENROLLED'/);
  assert.doesNotMatch(courses, /item\.status === 'ENROLLED'/);
});

test('schedule hides dropped and withdrawn history instead of every non-cancelled row', () => {
  const schedule = screenSlice('ScheduleScreen');

  // A no-filter feed renders DROPPED/WITHDRAWN enrollments as real sessions.
  assert.match(schedule, /liveEnrollments\s*\r?\n?\s*\.filter\(isScheduledEnrollment\)/);
  assert.doesNotMatch(schedule, /\.filter\(\(item\) => item\.section\?\.status !== 'CANCELLED'\)/);
});

test('registration counts and toggles only active selections', () => {
  const registration = screenSlice('RegistrationScreen');

  // `!== 'DROPPED'` treats WITHDRAWN/COMPLETED/CANCELLED history as a live
  // selection: it inflates the counter and offers a Drop button the backend
  // always rejects with ENROLLMENT_NOT_ACTIVE.
  assert.doesNotMatch(registration, /item\.status !== 'DROPPED'/);
  assert.match(registration, /enrollments\.filter\(isActiveEnrollment\)/);
  assert.match(registration, /enrollments\.find\(\(item\) => item\.sectionId === sectionId && isActiveEnrollmentStatus\(item\.status\)\)/);
  assert.match(registration, /enrollments\.some\(\(item\) => item\.sectionId === section\.id && isActiveEnrollmentStatus\(item\.status\)\)/);
});

test('isActiveEnrollmentStatus keeps cancelled-section rows enrolled so they stay droppable', () => {
  const { isActiveEnrollmentStatus } = loadStatusModule();

  assert.equal(isActiveEnrollmentStatus('ENROLLED'), true);
  assert.equal(isActiveEnrollmentStatus('PENDING'), true);
  assert.equal(isActiveEnrollmentStatus('CONFIRMED'), true);
  assert.equal(isActiveEnrollmentStatus('DROPPED'), false);
  assert.equal(isActiveEnrollmentStatus('WITHDRAWN'), false);
  assert.equal(isActiveEnrollmentStatus('COMPLETED'), false);
  assert.equal(isActiveEnrollmentStatus('CANCELLED'), false);
  assert.equal(isActiveEnrollmentStatus(undefined), false);
});
