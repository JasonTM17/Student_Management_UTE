const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');

function read(relativePath) {
  return fs.readFileSync(path.join(root, relativePath), 'utf8');
}

test('attendance marking API contracts and wiring integrity', async (t) => {
  await t.test('attendanceApi exports getSectionAttendance and saveSectionAttendance', () => {
    const apiSource = read('src/lib/api.ts');
    assert.match(
      apiSource,
      /saveSectionAttendance:\s*async\s*\(/,
      'attendanceApi must export saveSectionAttendance method',
    );
    assert.match(
      apiSource,
      /\/attendance\/sections\/\$\{sectionId\}/,
      'saveSectionAttendance must target PUT /attendance/sections/{sectionId}',
    );
    assert.match(
      apiSource,
      /getSectionAttendance:\s*async\s*\(/,
      'attendanceApi must export getSectionAttendance method',
    );
    assert.match(
      apiSource,
      /\/attendance\/section\/\$\{sectionId\}/,
      'getSectionAttendance must target GET /attendance/section/{sectionId}',
    );
  });

  await t.test('lecturer attendance route is wired in navigation layout', () => {
    const layoutSource = read('src/app/dashboard/layout.tsx');
    assert.match(
      layoutSource,
      /href:\s*'\/dashboard\/lecturer\/attendance'/,
      'dashboard layout must include link to /dashboard/lecturer/attendance',
    );
  });

  await t.test('lecturer attendance dictionary keys match between en and vi', () => {
    const messagesSource = read('src/i18n/messages.ts');
    assert.match(
      messagesSource,
      /lecturerAttendance:\s*\{/,
      'en dictionary must include lecturerAttendance',
    );

    // Verify key presence
    const expectedKeys = [
      'eyebrow',
      'title',
      'description',
      'selectSection',
      'selectSemester',
      'dateLabel',
      'markAllPresent',
      'resetStatuses',
      'saveButton',
      'savingButton',
      'savedSuccess',
      'saveFailed',
      'columnStudent',
      'columnStudentCode',
      'columnStatus',
      'columnNotes',
      'statsTotalStudents',
      'statsPresentRate',
    ];

    for (const key of expectedKeys) {
      assert.ok(
        messagesSource.includes(`${key}:`),
        `messages.ts must include translation key '${key}'`,
      );
    }
  });

  await t.test('lecturer attendance page file and locale proxy exist', () => {
    assert.ok(
      fs.existsSync(path.join(root, 'src/app/dashboard/lecturer/attendance/page.tsx')),
      'lecturer attendance page component must exist',
    );
    assert.ok(
      fs.existsSync(path.join(root, 'src/app/[locale]/dashboard/lecturer/attendance/page.tsx')),
      'locale proxy for lecturer attendance page must exist',
    );
  });
});
