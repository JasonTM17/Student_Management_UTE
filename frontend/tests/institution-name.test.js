const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const ts = require('typescript');

function loadExport(file) {
  const source = fs.readFileSync(path.join(__dirname, '../src/lib', file), 'utf8');
  const js = ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
  }).outputText;
  const mod = { exports: {} };
  new Function('module', 'exports', 'require', js)(mod, mod.exports, require);
  return mod.exports;
}

const { generateGradeCsv } = loadExport('grade-export.ts');
const { generateAttendanceCsv } = loadExport('attendance-export.ts');
const grade = (locale) => generateGradeCsv({ sectionNumber: 'Audit 01', students: [], locale });
const attendance = (locale) => generateAttendanceCsv({
  sectionNumber: 'Audit 01', date: '2026-10-18', students: [], locale,
  metrics: { total: 0, present: 0, absent: 0, late: 0, excused: 0, rate: 0 },
});

for (const [name, generate] of [['grade', grade], ['attendance', attendance]]) {
  test(`${name} CSV uses the requested Vietnamese institution name`, () => {
    const csv = generate('vi');
    assert.ok(csv.startsWith('\uFEFF'));
    assert.equal(csv.slice(1).split(/\r?\n/)[0],
      '"TRƯỜNG ĐẠI HỌC CÔNG NGHỆ KỸ THUẬT TP. HỒ CHÍ MINH — CAMPUSUTE"');
    assert.doesNotMatch(csv, /SƯ PHẠM/iu);
  });
  test(`${name} CSV retains its existing English institution and valid format`, () => {
    assert.equal(generate('en').slice(1).split(/\r?\n/)[0],
      '"HO CHI MINH CITY UNIVERSITY OF TECHNOLOGY AND ENGINEERING — CAMPUSUTE"');
  });
}
