const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const ts = require('typescript');

const root = path.resolve(__dirname, '..');

// Same transpile harness the ui primitive tests use: compile the TypeScript
// module and run it with its @/ aliases pointed at the real sources.
function load(relativePath) {
  const source = fs.readFileSync(path.join(root, relativePath), 'utf8');
  const output = ts.transpileModule(source, {
    compilerOptions: {
      module: ts.ModuleKind.CommonJS,
      target: ts.ScriptTarget.ES2020,
      esModuleInterop: true,
    },
    fileName: relativePath,
  }).outputText;
  const record = { exports: {} };
  const resolve = (name) =>
    name === '@/lib/registration-browse'
      ? load('src/lib/registration-browse.ts')
      : require(name);
  Function('module', 'exports', 'require', output)(record, record.exports, resolve);
  return record.exports;
}

const { foldSearchText, filterSearchOptions } = load('src/lib/search-text.ts');

const people = [
  { value: 'l1', label: 'Nguyễn Văn An' },
  { value: 'l2', label: 'Trần Thị Bích' },
  { value: 'l3', label: 'Đặng Minh Đức' },
  { value: 'l4', label: 'An Nguyễn' },
  { value: 'l5', label: 'Lê Hoàng' },
];

test('foldSearchText strips Vietnamese diacritics and maps đ/Đ to d', () => {
  assert.equal(foldSearchText('Nguyễn'), 'nguyen');
  assert.equal(foldSearchText('Đặng Minh Đức'), 'dang minh duc');
  assert.equal(foldSearchText('  Điện  Toán '), 'dien toan');
  assert.equal(foldSearchText('TRẦN'), 'tran');
});

test('an unmarked query matches marked names (nguyen -> Nguyễn)', () => {
  const hits = filterSearchOptions(people, 'nguyen').map((o) => o.value);
  assert.deepEqual(hits.sort(), ['l1', 'l4']);
});

test('đ is matched by plain d both ways', () => {
  assert.deepEqual(filterSearchOptions(people, 'dang').map((o) => o.value), ['l3']);
  assert.deepEqual(filterSearchOptions(people, 'Đức').map((o) => o.value), ['l3']);
  assert.deepEqual(filterSearchOptions(people, 'duc').map((o) => o.value), ['l3']);
});

test('matching is case-insensitive', () => {
  assert.deepEqual(filterSearchOptions(people, 'TRAN').map((o) => o.value), ['l2']);
});

test('every query token must appear somewhere in the label', () => {
  assert.deepEqual(filterSearchOptions(people, 'nguyen an').map((o) => o.value).sort(), ['l1', 'l4']);
  assert.deepEqual(filterSearchOptions(people, 'minh duc').map((o) => o.value), ['l3']);
  assert.deepEqual(filterSearchOptions(people, 'nguyen hoang'), []);
});

test('label-prefix matches rank before word-start and mid-word matches', () => {
  const ordered = filterSearchOptions(people, 'nguyen').map((o) => o.value);
  // 'Nguyễn Văn An' starts with the token; 'An Nguyễn' matches at a word start.
  assert.equal(ordered[0], 'l1');
  assert.equal(ordered[1], 'l4');
  const midWord = filterSearchOptions(
    [{ value: 'a', label: 'Nguyen' }, { value: 'b', label: 'Xnguyen' }],
    'nguyen',
  ).map((o) => o.value);
  assert.deepEqual(midWord, ['a', 'b']);
});

test('an empty or whitespace query returns every option in source order', () => {
  assert.deepEqual(filterSearchOptions(people, '').map((o) => o.value), ['l1', 'l2', 'l3', 'l4', 'l5']);
  assert.deepEqual(filterSearchOptions(people, '   ').map((o) => o.value), ['l1', 'l2', 'l3', 'l4', 'l5']);
});

test('extra keywords join the haystack (employee id, email)', () => {
  const options = [
    { value: 'x', label: 'Nguyễn Văn An', keywords: 'GV001 an.nguyen@ute.edu.vn' },
    { value: 'y', label: 'Trần Thị Bích', keywords: 'GV002' },
  ];
  assert.deepEqual(filterSearchOptions(options, 'gv002').map((o) => o.value), ['y']);
  assert.deepEqual(filterSearchOptions(options, 'ute.edu').map((o) => o.value), ['x']);
});
