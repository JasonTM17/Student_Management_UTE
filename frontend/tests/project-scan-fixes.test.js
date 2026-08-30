const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');

function read(relativePath) {
  return fs.readFileSync(path.join(root, relativePath), 'utf8');
}

test('lecturer grading guard tracks records the lecturer actually edited', () => {
  const page = read('src/app/dashboard/lecturer/grades/[id]/page.tsx');

  assert.match(
    page,
    /const \[editedIds, setEditedIds\] = useState<Set<string>>\(new Set\(\)\)/,
  );
  assert.match(
    page,
    /hasCompletedGrade\(\s*applyScoreDraft\(grades\.get\(enrollment\.id\), scoreDrafts\[enrollment\.id\]\),\s*\)/,
  );
  assert.match(page, /markEdited\(/);
  // Seeding every roster row with 0 made the old guard always pass.
  assert.doesNotMatch(page, /finalGrade \?\? 0/);
  assert.doesNotMatch(page, /Number\(event\.target\.value\) \|\| 0/);
});

test('lecturer grade saves submit only edited rows, not the seeded roster', () => {
  const page = read('src/app/dashboard/lecturer/grades/[id]/page.tsx');

  assert.match(page, /\.filter\(\(enrollment\) => editedIds\.has\(enrollment\.id\)\)/);
  assert.match(page, /applyScoreDraft\(/);
  assert.match(page, /updates\.length === 0/);
  assert.doesNotMatch(page, /Array\.from\(grades\.values\(\)\)/);
  assert.match(read('src/lib/api.ts'), /finalGrade: number \| null/);
});

test('grade score inputs keep decimal drafts until they are committed', () => {
  const page = read('src/app/dashboard/lecturer/grades/[id]/page.tsx');

  assert.match(page, /scoreDrafts/);
  assert.match(page, /value=\{\s*scoreDrafts\[enrollment\.id\] \?\?/);
  assert.match(page, /onBlur=\{\(\) => commitScoreDraft\(enrollment\.id\)\}/);
  assert.match(page, /step="0\.1"/);
});

test('semester switching cannot let stale responses overwrite newer grades', () => {
  const pages = [
    read('src/app/dashboard/grades/page.tsx'),
    read('src/app/dashboard/transcript/page.tsx'),
  ];

  for (const page of pages) {
    assert.match(page, /loadGeneration = useRef\(0\)/);
    assert.match(page, /const generation = \+\+loadGeneration\.current/);
    assert.match(page, /if \(generation !== loadGeneration\.current\) return;/);
    assert.match(page, /if \(generation === loadGeneration\.current\) \{/);
  }
});

test('admin user search commits one query per submit instead of per keystroke', () => {
  const page = read('src/app/admin/users/page.tsx');

  assert.match(page, /const \[searchInput, setSearchInput\] = useState\(''\)/);
  assert.match(page, /value=\{searchInput\}/);
  assert.match(page, /setSearchInput\(e\.target\.value\)/);
  assert.match(page, /setSearch\(searchInput\)/);
  assert.match(page, /setPage\(1\)/);
  assert.doesNotMatch(page, /value=\{search\}/);
  assert.doesNotMatch(page, /onChange=\{\(e\) => setSearch\(e\.target\.value\)\}/);
});
