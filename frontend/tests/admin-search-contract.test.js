const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');

/**
 * The six catalog admin pages used to fetch one 20-row page and then filter that
 * array in the browser, so the search box silently searched "the current page"
 * and reported zero results for anything else. Their server routes made the
 * second half of the failure: an unexpected `search` parameter was rejected with
 * HTTP 400 by the query allow-list, so no client could do it properly.
 *
 * These assertions pin the shape that fixes both halves, following the same
 * submit-commits-one-query contract already proven for the users page in
 * project-scan-fixes.test.js: a controlled input, a committed term, `search`
 * handed to the API, the fetch keyed on page + search, and no surviving
 * in-memory narrowing of the fetched rows.
 */
const SEARCH_PAGES = [
  { file: 'src/app/admin/academic-years/page.tsx', api: 'academicYearsApi' },
  { file: 'src/app/admin/classrooms/page.tsx', api: 'classroomsApi' },
  { file: 'src/app/admin/courses/page.tsx', api: 'coursesApi' },
  { file: 'src/app/admin/departments/page.tsx', api: 'departmentsApi' },
  { file: 'src/app/admin/lecturers/page.tsx', api: 'lecturersApi' },
  { file: 'src/app/admin/semesters/page.tsx', api: 'adminSemestersApi' },
];

function read(relativePath) {
  return fs.readFileSync(path.join(root, relativePath), 'utf8');
}

test('every catalog admin page commits its search term to its own API call', () => {
  for (const { file, api } of SEARCH_PAGES) {
    const page = read(file);
    const label = file;

    // The typed value and the committed value are separate states, so typing
    // cannot fire a request per keystroke.
    assert.match(
      page,
      /const \[searchInput, setSearchInput\] = useState\(''\)/,
      `${label} must keep a controlled searchInput state`,
    );
    assert.match(page, /value=\{searchInput\}/, `${label} must bind the input to searchInput`);
    assert.match(
      page,
      /onChange=\{\(event\) => setSearchInput\(event\.target\.value\)\}/,
      `${label} must only update searchInput while typing`,
    );
    assert.doesNotMatch(page, /value=\{search\}/, `${label} must not bind the input to the committed term`);
    assert.doesNotMatch(
      page,
      /onChange=\{\(event\) => setSearch\(event\.target\.value\)\}/,
      `${label} must not commit the term on every keystroke`,
    );

    // Submitting commits the term and returns to the first page.
    assert.match(
      page,
      /setSearch\(searchInput\.trim\(\)\)/,
      `${label} must commit searchInput on submit`,
    );
    assert.match(page, /setPage\(1\)/, `${label} must reset to page 1 on submit`);

    // The server does the filtering.
    assert.match(
      page,
      /search: search \|\| undefined/,
      `${label} must pass search to its list endpoint`,
    );
    assert.match(
      page,
      new RegExp(`${api}\\.getAll\\(query\\)`),
      `${label} must send the query object holding page, limit and search`,
    );
    assert.match(
      page,
      /\}, \[[^\]]*\bpage\b[^\]]*\bsearch\b[^\]]*\]\);/,
      `${label} must re-issue the request when the page or the search term changes`,
    );
  }
});

test('no catalog admin page narrows the fetched page in memory with the search term', () => {
  for (const { file } of SEARCH_PAGES) {
    const page = read(file);
    const label = file;

    assert.doesNotMatch(
      page,
      /const filtered\w+ = search\b/,
      `${label} still derives a filtered array from the search term`,
    );
    assert.doesNotMatch(
      page,
      /\.includes\(search/,
      `${label} still filters fetched rows against the raw search term`,
    );
    assert.doesNotMatch(
      page,
      /\bsearch\.toLowerCase\(\)/,
      `${label} still compares rows to a lower-cased search term in the browser`,
    );
    assert.doesNotMatch(
      page,
      /if \(search\) \{/,
      `${label} still keeps a second search filter that would silently AND with the server`,
    );
  }
});
