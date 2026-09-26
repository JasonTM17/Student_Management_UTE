const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const ts = require('typescript');

const root = path.resolve(__dirname, '..');

/** Loads the shipped TypeScript helper module the same way frontend-smoke does. */
function loadBrowseModule() {
  const source = fs.readFileSync(
    path.join(root, 'src/lib/registration-browse.ts'),
    'utf8',
  );
  const output = ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
  }).outputText;
  const loadedModule = { exports: {} };
  Function('module', 'exports', output)(loadedModule, loadedModule.exports);
  return loadedModule.exports;
}

const {
  normalizeVietnameseText,
  matchesSectionSearch,
  matchesCurriculumFilter,
  seatTier,
  sortSectionGroups,
  countdownParts,
  formatCountdownClock,
  scheduleOverlaps,
  findScheduleConflict,
  resolveRegistrationActionMessage,
} = loadBrowseModule();

function section(overrides = {}) {
  return {
    id: 's1',
    sectionNumber: '01',
    courseId: 'c1',
    courseCode: 'SE101',
    courseName: 'Nhập môn Công nghệ Phần mềm',
    credits: 3,
    capacity: 60,
    enrolledCount: 10,
    remainingSeats: 50,
    status: 'OPEN',
    scheduleConflict: false,
    alreadyEnrolled: false,
    curriculumRelevance: 'MANDATORY',
    schedules: [],
    ...overrides,
  };
}

test('search normalization strips Vietnamese diacritics in both directions', () => {
  assert.equal(normalizeVietnameseText('Điện toán đám mây'), 'dien toan dam may');
  assert.equal(normalizeVietnameseText('Nhập môn CÔNG NGHỆ phần mềm'), 'nhap mon cong nghe phan mem');
  // Same normalized haystack for "Dien Toan" and "Điện Toán".
  assert.equal(
    normalizeVietnameseText('Dien Toan'),
    normalizeVietnameseText('Điện Toán'),
  );
});

test('the unified search matches code, section number, or name case/diacritics-insensitively', () => {
  const s = section({ courseCode: 'SE101', sectionNumber: 'SE101-01', courseName: 'Nhập môn Công nghệ Phần mềm' });
  assert.equal(matchesSectionSearch(s, ''), true, 'empty query matches everything');
  assert.equal(matchesSectionSearch(s, 'se101'), true);
  assert.equal(matchesSectionSearch(s, 'SE101-01'), true);
  assert.equal(matchesSectionSearch(s, 'cong nghe phan mem'), true, 'diacritics-free query hits Vietnamese name');
  assert.equal(matchesSectionSearch(s, 'Công nghệ'), true, 'diacritics-full query also hits');
  assert.equal(matchesSectionSearch(s, 'phần mềm'), true, 'accented query with a space still matches');
  assert.equal(matchesSectionSearch(s, 'khong thay'), false);
  const nameOnly = section({ courseCode: 'IT1063', sectionNumber: '02', courseName: 'Cơ sở dữ liệu' });
  assert.equal(matchesSectionSearch(nameOnly, 'co so du lieu'), true, 'name match works without code match');
  assert.equal(matchesSectionSearch(nameOnly, 'it1063 02'), true);
});

test('curriculum filter chips keep ALL and match the exact relevance enum', () => {
  const mandatory = section({ curriculumRelevance: 'MANDATORY' });
  const elective = section({ curriculumRelevance: 'ELECTIVE' });
  const outside = section({ curriculumRelevance: 'OUTSIDE' });
  assert.equal(matchesCurriculumFilter(mandatory, 'ALL'), true);
  assert.equal(matchesCurriculumFilter(outside, 'ALL'), true);
  assert.equal(matchesCurriculumFilter(mandatory, 'MANDATORY'), true);
  assert.equal(matchesCurriculumFilter(elective, 'MANDATORY'), false);
  assert.equal(matchesCurriculumFilter(outside, 'ELECTIVE'), false);
  assert.equal(matchesCurriculumFilter(outside, 'OUTSIDE'), true);
});

test('seat tiers: green above 30% of capacity, amber 1-30%, gray at zero', () => {
  assert.equal(seatTier({ remainingSeats: 40, capacity: 60 }), 'open', '40/60 = 67% is open');
  assert.equal(seatTier({ remainingSeats: 19, capacity: 60 }), 'open', '19/60 ≈ 31.7% stays above the 30% line');
  assert.equal(seatTier({ remainingSeats: 18, capacity: 60 }), 'low', '18/60 = 30% exactly falls into the amber band');
  assert.equal(seatTier({ remainingSeats: 1, capacity: 60 }), 'low');
  assert.equal(seatTier({ remainingSeats: 0, capacity: 60 }), 'full');
  assert.equal(seatTier({ remainingSeats: 0, capacity: 0 }), 'full', 'degenerate capacity stays honest');
  assert.equal(seatTier({ remainingSeats: 3, capacity: 0 }), 'low', 'unknown capacity with seats stays conservative');
});

test('sorting keeps sections grouped and orders by code, credits, or open seats', () => {
  const a1 = section({ id: 'a1', courseCode: 'SE101', sectionNumber: '02', remainingSeats: 1, credits: 3 });
  const a2 = section({ id: 'a2', courseCode: 'SE101', sectionNumber: '01', remainingSeats: 9, credits: 3 });
  const b1 = section({ id: 'b1', courseCode: 'IT1063', sectionNumber: '01', remainingSeats: 5, credits: 4 });
  const groups = [
    { courseId: 'c1', courseCode: 'SE101', courseName: 'A', credits: 3, sections: [a1, a2] },
    { courseId: 'c2', courseCode: 'IT1063', courseName: 'B', credits: 4, sections: [b1] },
  ];
  const byCode = sortSectionGroups(groups, 'code');
  assert.deepEqual(byCode.map((g) => g.courseCode), ['IT1063', 'SE101']);
  assert.deepEqual(byCode[1].sections.map((s) => s.sectionNumber), ['01', '02'], 'sections sort by number within a group');

  const byCredits = sortSectionGroups(groups, 'credits');
  assert.deepEqual(byCredits.map((g) => g.courseCode), ['IT1063', 'SE101'], '4 credits before 3');

  const seatHeavy = [
    { courseId: 'c1', courseCode: 'SE101', courseName: 'A', credits: 3, sections: [a1] },
    { courseId: 'c2', courseCode: 'IT1063', courseName: 'B', credits: 4, sections: [b1] },
  ];
  const bySeats = sortSectionGroups(seatHeavy, 'seats');
  assert.deepEqual(
    bySeats.map((g) => g.courseCode),
    ['IT1063', 'SE101'],
    '5 open seats before 1',
  );
  // The sorter never mutates the caller's arrays.
  assert.equal(groups[0].sections[0].sectionNumber, '02');
});

test('countdown parts and clock formatting produce whole-second output', () => {
  const fiveDaysMs = 5 * 86_400_000;
  const target = 1_000_000_000_000;
  const parts = countdownParts(target, target - fiveDaysMs - 12 * 3_600_000 - 34 * 60_000 - 5_000);
  assert.ok(parts);
  assert.equal(parts.days, 5);
  assert.equal(parts.hours, 12);
  assert.equal(parts.minutes, 34);
  assert.equal(parts.seconds, 5);
  assert.equal(formatCountdownClock(parts), '12:34:05');

  assert.deepEqual(countdownParts(target, target), null, 'at the deadline the countdown is gone');
  assert.deepEqual(countdownParts(target, target + 1), null, 'past the deadline the countdown is gone');
  assert.equal(countdownParts(target, target - 65_000)?.seconds, 5, 'sixty-five seconds left reads five whole seconds');
  const justUnder = countdownParts(target, target - 5_000);
  assert.ok(justUnder);
  assert.equal(formatCountdownClock(justUnder), '00:00:05');
});

test('schedule overlap uses the CampusCore day convention (1=Sunday..7=Saturday)', () => {
  const mondayMorning = { dayOfWeek: 2, startTime: '07:00', endTime: '09:30' };
  const mondayLate = { dayOfWeek: 2, startTime: '09:30', endTime: '12:00' };
  const mondayOverlap = { dayOfWeek: 2, startTime: '09:00', endTime: '10:00' };
  const sunday = { dayOfWeek: 1, startTime: '07:00', endTime: '09:30' };

  assert.equal(scheduleOverlaps(mondayMorning, mondayLate), false, 'touching windows do not overlap');
  assert.equal(scheduleOverlaps(mondayMorning, mondayOverlap), true);
  assert.equal(scheduleOverlaps(mondayMorning, sunday), false, 'Sunday(1) never collides with Monday(2)');
  assert.equal(scheduleOverlaps(mondayMorning, { ...sunday, dayOfWeek: 0 }), false, 'legacy 0 maps to Sunday, not Monday');

  const conflict = findScheduleConflict(
    [{ dayOfWeek: 3, startTime: '13:00', endTime: '15:00' }],
    [
      { dayOfWeek: 4, startTime: '07:00', endTime: '09:00' },
      { dayOfWeek: 3, startTime: '14:00', endTime: '16:00' },
    ],
  );
  assert.ok(conflict, 'the colliding candidate is reported');
  assert.equal(conflict.startTime, '13:00');
  assert.equal(findScheduleConflict([{ dayOfWeek: 6, startTime: '08:00', endTime: '10:00' }], []), null);
  assert.equal(findScheduleConflict(null, [{ dayOfWeek: 6, startTime: '08:00', endTime: '10:00' }]), null);
});

test('registration error codes map to the dedicated copy with an idempotency fallback', () => {
  const copy = {
    sectionFull: 'FULL',
    scheduleConflict: 'CONFLICT',
    creditCapExceeded: 'CAP',
    prerequisiteUnmet: 'PREREQ',
    corequisiteUnmet: 'COREQ',
    windowClosed: 'WINDOW',
    cohortIneligible: 'COHORT',
    idempotencyRetry: 'RETRY',
  };
  assert.equal(resolveRegistrationActionMessage('SECTION_FULL', copy), 'FULL');
  assert.equal(resolveRegistrationActionMessage('SCHEDULE_CONFLICT', copy), 'CONFLICT');
  assert.equal(resolveRegistrationActionMessage('CREDIT_CAP_EXCEEDED', copy), 'CAP');
  assert.equal(resolveRegistrationActionMessage('PREREQUISITE_UNMET', copy), 'PREREQ');
  assert.equal(resolveRegistrationActionMessage('COREQUISITE_UNMET', copy), 'COREQ');
  assert.equal(resolveRegistrationActionMessage('WINDOW_CLOSED', copy), 'WINDOW');
  assert.equal(resolveRegistrationActionMessage('COHORT_INELIGIBLE', copy), 'COHORT');
  assert.equal(resolveRegistrationActionMessage('IDEMPOTENCY_IN_PROGRESS', copy), 'RETRY');
  assert.equal(resolveRegistrationActionMessage('IDEMPOTENCY_EXPIRED', copy), 'RETRY');
  assert.equal(resolveRegistrationActionMessage('SOMETHING_ELSE', copy), null, 'unknown codes fall through');
  assert.equal(resolveRegistrationActionMessage(undefined, copy), null);
  assert.equal(resolveRegistrationActionMessage(null, copy), null);
});

test('the shipped page keeps the shared day mapping and error fallback wiring', () => {
  const page = fs.readFileSync(path.join(root, 'src/app/dashboard/register/page.tsx'), 'utf8');
  const grid = fs.readFileSync(path.join(root, 'src/components/schedule/weekly-grid.tsx'), 'utf8');
  // The register page and the timetable grid must agree on day labels: both go
  // through shortDayLabel, and no forked day-label table exists on the page.
  assert.match(page, /shortDayLabel\(locale, schedule\.dayOfWeek\)/);
  assert.doesNotMatch(page, /DAY_LABELS_VI|SHORT_DAY_LABELS/);
  assert.match(grid, /1: 'Chủ Nhật'/);
  // Error mapping falls back to the shared campus-error mapper.
  assert.match(page, /campusCodeMessage\(cause, messages\.common\.campusErrors\)/);
});
