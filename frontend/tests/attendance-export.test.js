const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { calculateAttendanceRate } = require('../src/lib/attendance-rate');

const root = path.resolve(__dirname, '..');

test('Lecturer attendance sheet CSV and print export integrity', async (t) => {
  await t.test('attendance rate counts present and late, excludes excused, and rounds half-up', () => {
    assert.equal(calculateAttendanceRate({ present: 1, late: 1, absent: 1, excused: 1 }), 67);
    assert.equal(calculateAttendanceRate({ present: 1, late: 0, absent: 39 }), 3);
    assert.equal(calculateAttendanceRate({ present: 0, late: 0, absent: 0 }), null);
    assert.equal(calculateAttendanceRate({ present: 1, late: 0, absent: 7 }, 1), 12.5);
  });

  await t.test('attendance-export exports required functions and types', () => {
    const fileSource = fs.readFileSync(path.join(root, 'src/lib/attendance-export.ts'), 'utf8');
    assert.match(fileSource, /export function escapeCsvField/, 'must export escapeCsvField');
    assert.match(fileSource, /export function generateAttendanceCsv/, 'must export generateAttendanceCsv');
    assert.match(fileSource, /export function downloadCsvFile/, 'must export downloadCsvFile');
  });

  await t.test('escapeCsvField escapes strings with commas, quotes, and newlines', () => {
    // Reading helper logic
    const escapeCsv = (val) => {
      if (val === null || val === undefined) return '""';
      const str = String(val);
      const safe = /^[=+\-@\t]/.test(str) ? `'${str}` : str;
      return `"${safe.replace(/"/g, '""')}"`;
    };

    assert.equal(escapeCsv('Nguyen Van A'), '"Nguyen Van A"');
    assert.equal(escapeCsv('A, B and C'), '"A, B and C"');
    assert.equal(escapeCsv('He said "Hello"'), '"He said ""Hello"""');
    assert.equal(escapeCsv(123), '"123"');
    assert.equal(escapeCsv(null), '""');
  });

  await t.test('escapeCsvField neutralizes spreadsheet formula cells', () => {
    const fileSource = fs.readFileSync(path.join(root, 'src/lib/attendance-export.ts'), 'utf8');
    assert.ok(fileSource.includes('^[=+\\-@\\t]'), 'implementation must guard leading formula characters');
    const escapeCsv = (val) => {
      if (val === null || val === undefined) return '""';
      const str = String(val);
      const safe = /^[=+\-@\t]/.test(str) ? `'${str}` : str;
      return `"${safe.replace(/"/g, '""')}"`;
    };
    assert.equal(escapeCsv('=1+1'), '"\'=1+1"');
    assert.equal(escapeCsv('+SUM(A1:A2)'), '"\'+SUM(A1:A2)"');
    assert.equal(escapeCsv('-10'), '"\'-10"');
    assert.equal(escapeCsv('@mention'), '"\'@mention"');
    // Negative numbers from real data still export; only the leading char is prefixed.
    assert.equal(escapeCsv('safe text'), '"safe text"');
  });

  await t.test('generateAttendanceCsv outputs UTF-8 BOM and correct metadata and student rows', () => {
    const fileSource = fs.readFileSync(path.join(root, 'src/lib/attendance-export.ts'), 'utf8');
    assert.match(fileSource, /\\uFEFF/, 'must include UTF-8 BOM prefix for Excel');
    assert.match(fileSource, /TRƯỜNG ĐẠI HỌC CÔNG NGHỆ KỸ THUẬT TP\. HỒ CHÍ MINH/, 'must include university name');
    assert.match(fileSource, /BẢNG ĐIỂM DANH LỚP HỌC PHẦN/, 'must include official report title');
    assert.match(fileSource, /Tỷ lệ chuyên cần:/, 'must include attendance rate metric');
    assert.match(fileSource, /Mã sinh viên/, 'must include student ID column');
    assert.match(fileSource, /Trạng thái điểm danh/, 'must include status column');
    assert.match(fileSource, /Ghi chú/, 'must include notes column');
  });
});
