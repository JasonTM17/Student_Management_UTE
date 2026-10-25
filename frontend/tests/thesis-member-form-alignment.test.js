'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const read = (relative) => fs.readFileSync(path.join(root, relative), 'utf8');

function slice(source, start, end) {
  const i = source.indexOf(start);
  assert.ok(i >= 0, `marker found: ${start.slice(0, 60)}`);
  const j = end ? source.indexOf(end, i) : source.length;
  assert.ok(j > i, `end marker found after start`);
  return source.slice(i, j);
}

// Returns the source of every JSX opening tag `<Name ...>` (self-closing or
// not). `>` inside `{...}` expressions (arrow functions) is skipped by tracking
// brace depth, so a `=>` inside a prop never terminates the tag early.
function tags(source, name) {
  const out = [];
  const needle = `<${name}`;
  let i = 0;
  while ((i = source.indexOf(needle, i)) >= 0) {
    let depth = 0;
    let j = i + needle.length;
    for (; j < source.length; j += 1) {
      const ch = source[j];
      if (ch === '{') depth += 1;
      else if (ch === '}') depth -= 1;
      else if (ch === '>' && depth === 0) { j += 1; break; }
    }
    out.push(source.slice(i, j));
    i = j;
  }
  return out;
}

const component = read('src/components/dashboard/thesis/SupervisedGroupMembers.tsx');
// The add-member block is the space-y-2 stack below the roster <ul>: search row,
// result rows, and the external-member row down to the shared error line.
const block = slice(component, 'className="space-y-2"', '{error ?');

test('every input and button in the add-member block shares the h-9 control height', () => {
  const controls = [...tags(block, 'Input'), ...tags(block, 'Button')];
  assert.equal(controls.length, 6, '3 inputs + 3 buttons (search, per-result add, external add)');
  for (const control of controls) {
    assert.match(control, /\bh-9\b/, `control must carry h-9: ${control.slice(0, 90)}`);
  }
  assert.doesNotMatch(block, /\bh-(7|8|10|11)\b/, 'no off-size control heights in the block');
});

test('the search row keeps the input fluid and the search button a fixed column', () => {
  const row = slice(block, '<div className="flex gap-2">', '</div>');
  assert.match(tags(row, 'Input')[0], /\bh-9\b/);
  const [searchButton] = tags(row, 'Button');
  assert.match(searchButton, /\bh-9\b/);
  assert.match(searchButton, /\bshrink-0\b/, 'search button must not shrink under the fluid input');
});

test('each result row pins its add button to a fixed right column', () => {
  const row = block.match(/<li\s+key=\{student\.studentId\}\s+className="([^"]+)"/);
  assert.ok(row, 'result row found');
  assert.match(row[1], /\bjustify-between\b/, 'name left, action right');
  const addButton = tags(block, 'Button')[1];
  assert.match(addButton, /\bh-9\b/);
  assert.match(addButton, /\bshrink-0\b/, 'result add button keeps its column width');
});

test('the external-member row is a responsive grid without ragged width caps', () => {
  assert.match(
    block,
    /className="grid gap-2 border-t border-border\/60 pt-2 sm:grid-cols-\[1fr_1fr_auto\]"/,
    'external row: stacked full-width on mobile, 1fr 1fr auto from sm',
  );
  assert.doesNotMatch(block, /flex-wrap/, 'no wrapping flex row that leaves a ragged edge');
  assert.doesNotMatch(block, /max-w-\[/, 'no max-w caps on the external inputs');
});

test('the external add button keeps full size and a real disabled style', () => {
  const externalButton = tags(block, 'Button').pop();
  assert.match(externalButton, /\bh-9\b/, 'disabled state keeps the shared control height');
  assert.match(externalButton, /disabled=\{isPending \|\| !externalName\.trim\(\)\}/);
  // The shared button variant already renders disabled:pointer-events-none +
  // disabled:opacity-50; a hand-rolled opacity class on top is redundant.
  assert.doesNotMatch(externalButton, /'opacity-50'/, 'no extra opacity patch on the button');
});
