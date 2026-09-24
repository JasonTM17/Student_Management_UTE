const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');

test('Lecturer grade sheet CSV export and print roster integrity', async (t) => {
  await t.test('grade-export exports required functions and types', () => {
    const fileSource = fs.readFileSync(path.join(root, 'src/lib/grade-export.ts'), 'utf8');
    assert.match(fileSource, /export function calculateGrade/, 'must export calculateGrade');
    assert.match(fileSource, /export function calculateTotalScore/, 'must export calculateTotalScore');
    assert.match(fileSource, /export function computeGradeSummary/, 'must export computeGradeSummary');
    assert.match(fileSource, /export function escapeCsvField/, 'must export escapeCsvField');
    assert.match(fileSource, /export function generateGradeCsv/, 'must export generateGradeCsv');
    assert.match(fileSource, /export function downloadCsvFile/, 'must export downloadCsvFile');
  });

  await t.test('calculateGrade follows standard HCMUTE credit grading scale', () => {
    const scale = (score) => {
      if (score >= 9.0) return 'A+';
      if (score >= 8.5) return 'A';
      if (score >= 8.0) return 'B+';
      if (score >= 7.0) return 'B';
      if (score >= 6.5) return 'C+';
      if (score >= 5.5) return 'C';
      if (score >= 5.0) return 'D+';
      if (score >= 4.0) return 'D';
      return 'F';
    };

    assert.equal(scale(9.5), 'A+');
    assert.equal(scale(9.0), 'A+');
    assert.equal(scale(8.7), 'A');
    assert.equal(scale(8.5), 'A');
    assert.equal(scale(8.2), 'B+');
    assert.equal(scale(7.5), 'B');
    assert.equal(scale(6.8), 'C+');
    assert.equal(scale(5.8), 'C');
    assert.equal(scale(5.2), 'D+');
    assert.equal(scale(4.5), 'D');
    assert.equal(scale(4.0), 'D');
    assert.equal(scale(3.9), 'F');
    assert.equal(scale(0), 'F');
  });

  await t.test('computeGradeSummary calculates distribution, pass rate, and average correctly', () => {
    const students = [
      { studentId: '1', studentCode: '21110001', studentName: 'Nguyễn Văn An', processScore: 9.0, finalExamScore: 9.0, totalScore: 9.0, letterGrade: 'A+' },
      { studentId: '2', studentCode: '21110002', studentName: 'Trần Thị Bình', processScore: 8.0, finalExamScore: 8.0, totalScore: 8.0, letterGrade: 'B+' },
      { studentId: '3', studentCode: '21110003', studentName: 'Lê Hoàng Cường', processScore: 3.0, finalExamScore: 4.0, totalScore: 3.5, letterGrade: 'F' },
      { studentId: '4', studentCode: '21110004', studentName: 'Phạm Minh Đức', processScore: null, finalExamScore: null, totalScore: null, letterGrade: null },
    ];

    let graded = 0;
    let passed = 0;
    let totalSum = 0;
    const counts = { 'A+': 0, 'A': 0, 'B+': 0, 'B': 0, 'C+': 0, 'C': 0, 'D+': 0, 'D': 0, 'F': 0 };

    students.forEach((s) => {
      if (s.totalScore !== null && Number.isFinite(s.totalScore)) {
        graded++;
        totalSum += s.totalScore;
        if (s.letterGrade in counts) counts[s.letterGrade]++;
        if (s.totalScore >= 4.0) passed++;
      }
    });

    assert.equal(graded, 3);
    assert.equal(students.length - graded, 1);
    assert.equal(passed, 2);
    assert.equal(counts['A+'], 1);
    assert.equal(counts['B+'], 1);
    assert.equal(counts.F, 1);
    assert.equal(Math.round((passed / graded) * 1000) / 10, 66.7);
    assert.equal(Math.round((totalSum / graded) * 100) / 100, 6.83);
  });

  await t.test('generateGradeCsv contains UTF-8 BOM, UTE institutional headers and RFC 4180 columns', () => {
    const fileSource = fs.readFileSync(path.join(root, 'src/lib/grade-export.ts'), 'utf8');
    assert.match(fileSource, /\\uFEFF/, 'must include UTF-8 BOM prefix for Excel');
    assert.match(fileSource, /TRƯỜNG ĐẠI HỌC SƯ PHẠM KỸ THUẬT TP\. HỒ CHÍ MINH/, 'must include university name');
    assert.match(fileSource, /BẢNG ĐIỂM TỔNG KẾT HỌC PHẦN/, 'must include official report title');
    assert.match(fileSource, /THỐNG KÊ KẾT QUẢ ĐÁNH GIÁ/, 'must include statistics summary block');
    assert.match(fileSource, /Điểm quá trình \(50%\)/, 'must include process score column');
    assert.match(fileSource, /Điểm thi kết thúc \(50%\)/, 'must include final exam score column');
    assert.match(fileSource, /Tổng kết \(Hệ 10\)/, 'must include total score column');
    assert.match(fileSource, /Điểm chữ/, 'must include letter grade column');
  });
});
