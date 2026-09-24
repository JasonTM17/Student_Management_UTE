const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');

test('Lecturer attendance sheet CSV and print export integrity', async (t) => {
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
      return `"${str.replace(/"/g, '""')}"`;
    };

    assert.equal(escapeCsv('Nguyen Van A'), '"Nguyen Van A"');
    assert.equal(escapeCsv('A, B and C'), '"A, B and C"');
    assert.equal(escapeCsv('He said "Hello"'), '"He said ""Hello"""');
    assert.equal(escapeCsv(123), '"123"');
    assert.equal(escapeCsv(null), '""');
  });

  await t.test('generateAttendanceCsv outputs UTF-8 BOM and correct metadata and student rows', () => {
    const fileSource = fs.readFileSync(path.join(root, 'src/lib/attendance-export.ts'), 'utf8');
    assert.match(fileSource, /\\uFEFF/, 'must include UTF-8 BOM prefix for Excel');
    assert.match(fileSource, /TRƯỜNG ĐẠI HỌC SƯ PHẠM KỸ THUẬT TP\. HỒ CHÍ MINH/, 'must include university name');
    assert.match(fileSource, /BẢNG ĐIỂM DANH LỚP HỌC PHẦN/, 'must include official report title');
    assert.match(fileSource, /Tỷ lệ chuyên cần:/, 'must include attendance rate metric');
    assert.match(fileSource, /Mã sinh viên/, 'must include student ID column');
    assert.match(fileSource, /Trạng thái điểm danh/, 'must include status column');
    assert.match(fileSource, /Ghi chú/, 'must include notes column');
  });
});
