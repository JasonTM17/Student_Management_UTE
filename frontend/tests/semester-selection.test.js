const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const ts = require('typescript');

const source = fs.readFileSync(path.join(__dirname, '../src/lib/semesters.ts'), 'utf8');
const js = ts.transpileModule(source, {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
}).outputText;
const mod = { exports: {} };
new Function('module', 'exports', 'require', js)(mod, mod.exports, require);
const { pickPreferredSemesterId } = mod.exports;
const semester = (id, status) => ({ id, status });

test('OPEN semester beats a newer DRAFT semester instead of hiding existing courses', () => {
  assert.equal(pickPreferredSemesterId([
    semester('future-draft-2094', 'DRAFT'),
    semester('current-open-2026', 'OPEN'),
  ]), 'current-open-2026');
});

test('OPEN semester beats a CLOSED first row', () => {
  assert.equal(pickPreferredSemesterId([
    semester('closed', 'CLOSED'), semester('open', 'OPEN'),
  ]), 'open');
});

test('existing preferred statuses retain their priority over OPEN and each other', () => {
  const priorities = ['IN_PROGRESS', 'ADD_DROP_OPEN', 'REGISTRATION_OPEN', 'ACTIVE'];
  for (let i = 0; i < priorities.length; i += 1) {
    const lower = priorities.slice(i).reverse().map(status => semester(status, status));
    assert.equal(pickPreferredSemesterId([semester('open', 'OPEN'), ...lower]), priorities[i]);
  }
});

test('first matching OPEN semester follows the existing stable catalog order', () => {
  const semesters = [semester('draft', 'DRAFT'), semester('first-open', 'OPEN'), semester('second-open', 'OPEN')];
  const original = JSON.stringify(semesters);
  assert.equal(pickPreferredSemesterId(semesters), 'first-open');
  assert.equal(JSON.stringify(semesters), original);
});

test('catalogs without a preferred status retain the existing first-row fallback', () => {
  assert.equal(pickPreferredSemesterId([semester('closed', 'CLOSED'), semester('draft', 'DRAFT')]), 'closed');
});

test('empty catalogs retain the empty selection', () => {
  assert.equal(pickPreferredSemesterId([]), '');
});
