// Regression tests for the production chatbot audit (run dwfrun-94cb7693):
// the client copy guard must repair the three provider-artifact families that
// reached students' screens — mid-word capital glue, the grade-table colon,
// and corpus meta-voice — and must never touch identifiers, clock times or
// URLs that merely look similar.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const ts = require('typescript');

const root = path.resolve(__dirname, '..');

function load(relativePath) {
  const source = fs.readFileSync(path.join(root, relativePath), 'utf8');
  const output = ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
  }).outputText;
  const moduleRecord = { exports: {} };
  Function('module', 'exports', output)(moduleRecord, moduleRecord.exports);
  return moduleRecord.exports;
}

const { normalizeAssistantCopy, separateGluedWords } = load('src/lib/assistant-output-guard.ts');

test('capital glue is separated on the Vietnamese word boundary', () => {
  assert.equal(
    normalizeAssistantCopy('Khoa Xây dựngRiêng Khoa Công nghệ Thông tin', 'vi'),
    'Khoa Xây dựng Riêng Khoa Công nghệ Thông tin',
  );
  assert.ok(
    normalizeAssistantCopy('# Thủ tục xin hoãn thiSinh viên vắng thi', 'vi')
      .includes('# Thủ tục xin hoãn thi\n\nSinh viên vắng thi'),
  );
});

test('separateGluedWords leaves identifier and URL tokens alone', () => {
  assert.equal(separateGluedWords('SE013'), 'SE013');
  assert.equal(separateGluedWords('https://campusute.io.vn/dashboard'), 'https://campusute.io.vn/dashboard');
});

test('grade-table colon gains a space while clock times stay intact', () => {
  assert.equal(normalizeAssistantCopy('B+:3.5 và C+:2.5', 'vi'), 'B+: 3.5 và C+: 2.5');
  assert.equal(normalizeAssistantCopy('Mở cửa từ 07:00 đến 19:00', 'vi'), 'Mở cửa từ 07:00 đến 19:00');
});

test('corpus meta-voice is rewritten into the portal voice', () => {
  assert.equal(
    normalizeAssistantCopy('Học phí theo tín chỉ hiện chưa có trong thông tin được công bố ở đây.', 'vi'),
    'Học phí theo tín chỉ hiện chưa được công bố.',
  );
  assert.ok(
    normalizeAssistantCopy('Thời gian mở cửa chưa được quy định trong thông tin hiện có.', 'vi')
      .includes('chưa được quy định.'),
  );
  assert.ok(
    normalizeAssistantCopy('Tôi chỉ có thể giúp về các nội dung học vụ đã đăng ký.', 'vi')
      .startsWith('Mình chỉ hỗ trợ'),
  );
});
