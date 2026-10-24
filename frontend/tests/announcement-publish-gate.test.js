const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const page = fs.readFileSync(path.join(root, 'src/app/admin/announcements/page.tsx'), 'utf8');

// D4: the publish control must stay disabled while title/body are empty and
// the required hint only appears once a field has been touched — the same
// contract the mail compose form already follows.
test('publish button is disabled until title and content are non-empty', () => {
  assert.match(page, /type="submit" disabled=\{busy \|\| !draft\.title\.trim\(\) \|\| !draft\.content\.trim\(\)\}/);
});

test('touched flags reset whenever a fresh draft is opened', () => {
  const resets = page.match(/setTouched\(\{ title: false, content: false \}\)/g) ?? [];
  assert.ok(resets.length >= 2, 'create and edit entry points both reset touched state');
  assert.match(page, /onBlur=\{\(\) => setTouched\(\(current\) => \(\{ \.\.\.current, title: true \}\)\)\}/);
  assert.match(page, /onBlur=\{\(\) => setTouched\(\(current\) => \(\{ \.\.\.current, content: true \}\)\)\}/);
});

test('required hints reuse the existing copy and only show on touched empties', () => {
  assert.match(page, /touched\.title && !draft\.title\.trim\(\) \? copy\.required/);
  assert.match(page, /touched\.content && !draft\.content\.trim\(\)/);
  // No new copy was invented for the gate: the hint uses copy.required.
  assert.doesNotMatch(page, /requiredHint|fieldIsRequired/i);
});

test('submit fires once per click because the control is disabled while busy', () => {
  assert.match(page, /disabled=\{busy \|\|/);
  const saveCalls = page.match(/void save\(\)/g) ?? [];
  assert.ok(saveCalls.length >= 1, 'form submit still reaches save()');
});
