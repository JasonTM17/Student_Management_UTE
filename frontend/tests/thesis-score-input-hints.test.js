const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');

const scoreInputs = [
  'src/features/thesis/components/CouncilDefenseWorkspace.tsx',
  'src/features/thesis/components/GvpbReviewPanel.tsx',
];

// A numeric placeholder like "8.5" reads as a pre-filled score. The allowed
// range already lives in the accessible label ("Defense score (0 - 10)") and
// the input min/max/step attributes, so no placeholder hint is needed.
for (const file of scoreInputs) {
  test(`${file} score input carries no numeric placeholder`, () => {
    const source = fs.readFileSync(path.join(root, file), 'utf8');
    assert.doesNotMatch(source, /placeholder="\d/, `${file} must not use a numeric placeholder on the score input`);
  });

  test(`${file} score input keeps its accessible label and range attributes`, () => {
    const source = fs.readFileSync(path.join(root, file), 'utf8');
    assert.match(source, /scoreInputLabel/, `${file} must keep the labelled score input`);
    assert.match(source, /min="0"/, `${file} must keep the 0 minimum`);
    assert.match(source, /max="10"/, `${file} must keep the 10 maximum`);
    assert.match(source, /step="0\.1"/, `${file} must keep the 0.1 step`);
  });
}

test('score input label states the allowed range in both locales', () => {
  const messages = fs.readFileSync(path.join(root, 'src/i18n/messages.ts'), 'utf8');
  assert.match(messages, /scoreInputLabel: 'Defense score \(0 - 10\)'/, 'English label must state the 0-10 range');
  assert.match(messages, /scoreInputLabel: 'Điểm bảo vệ \(0 - 10\)'/, 'Vietnamese label must state the 0-10 range');
});
