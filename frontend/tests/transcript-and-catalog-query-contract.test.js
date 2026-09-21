const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const ts = require('typescript');

const root = path.resolve(__dirname, '..');

function read(relativePath) {
  return fs.readFileSync(path.join(root, relativePath), 'utf8');
}

const API_SOURCE = read('src/lib/api.ts');
const BACKEND = '../java-services/restful-api/src/main/java/io/campuscore/restfulapi';
const ENROLLMENT_CONTROLLER = read(`${BACKEND}/academic/web/AcademicEnrollmentReadController.java`);
const ACADEMIC_CONTROLLER = read(`${BACKEND}/academic/web/AcademicReadController.java`);

/**
 * Reads the query parameters a client member can put on the wire.
 *
 * Two shapes matter: a declared `params?: { a?: …; b?: … }` object type that the
 * member forwards unchanged, and a literal `{ params: { a, b } }` argument.
 * Anything else cannot reach the server as a query parameter, so it is not part
 * of the contract.
 */
function expressibleQueryParams(source, groupName, memberName) {
  const file = ts.createSourceFile('api.ts', source, ts.ScriptTarget.Latest, true);
  const names = new Set();
  let members = 0;

  const collect = (node) => {
    if (ts.isParameter(node) && node.name.getText() === 'params'
      && node.type && ts.isTypeLiteralNode(node.type)) {
      for (const member of node.type.members) {
        if (member.name) names.add(member.name.getText().replace('?', ''));
      }
    }
    if (ts.isPropertyAssignment(node) && node.name.getText() === 'params'
      && ts.isObjectLiteralExpression(node.initializer)) {
      for (const property of node.initializer.properties) {
        if (property.name) names.add(property.name.getText());
      }
    }
    ts.forEachChild(node, collect);
  };

  const withinGroup = (node) => {
    if (!ts.isVariableStatement(node) || !node.declarationList.declarations.length) return;
    const declaration = node.declarationList.declarations[0];
    if (!ts.isIdentifier(declaration.name) || declaration.name.getText() !== groupName) return;
    if (declaration.initializer && ts.isObjectLiteralExpression(declaration.initializer)) {
      for (const property of declaration.initializer.properties) {
        if (ts.isPropertyAssignment(property) && property.name.getText() === memberName) {
          members += 1;
          collect(property.initializer);
        }
      }
    }
  };

  ts.forEachChild(file, withinGroup);
  assert.equal(members, 1, `${groupName}.${memberName} is not declared exactly once`);
  return [...names].sort();
}

/** Reads the allow-list the given route accepts, straight from its controller. */
function serverAllowedQueryParams(controllerSource, route) {
  const marker = `@GetMapping("${route}")`;
  const start = controllerSource.indexOf(marker);
  assert.ok(start >= 0, `${marker} is not served by this controller`);
  const next = controllerSource.indexOf('@GetMapping(', start + marker.length);
  const handler = controllerSource.slice(start, next === -1 ? undefined : next);

  const match = handler.match(/requireAllowedQuery\(\s*queryParameters,\s*Set\.of\(([^)]*)\)/);
  assert.ok(match, `${route} declares no requireAllowedQuery allow-list`);
  return [...match[1].matchAll(/"([^"]+)"/g)].map((entry) => entry[1]).sort();
}

// ---------------------------------------------------------------------------
// DEEP-P3-1: GET /enrollments/my/transcript is the cumulative whole-programme
// transcript, so the route allow-lists no query parameter at all (Set.of()).
// The client still declared an optional `semesterId` and forwarded it, so every
// caller that honoured the published signature got HTTP 400. The transcript must
// not become per-semester; the client has to stop expressing a request the
// server refuses.
// ---------------------------------------------------------------------------

test('the transcript route really accepts no query parameter', () => {
  assert.deepEqual(
    serverAllowedQueryParams(ENROLLMENT_CONTROLLER, 'enrollments/my/transcript'),
    [],
  );
  // The service method takes only the student, which is what makes the returned
  // transcript cumulative over the whole programme rather than per-semester.
  const transcriptCall = ENROLLMENT_CONTROLLER.match(/findStudentTranscript\(([^)]*)\)/);
  assert.ok(transcriptCall, 'the controller never calls findStudentTranscript');
  assert.equal(transcriptCall[1].split(',').length, 1);
});

test('gradesApi.getMyTranscript cannot send a query parameter the route rejects', () => {
  assert.deepEqual(expressibleQueryParams(API_SOURCE, 'gradesApi', 'getMyTranscript'), []);
});

test('the pre-fix transcript signature really is what this guard rejects', () => {
  const before = `export const gradesApi = {
    getMyTranscript: async (semesterId?: string): Promise<StudentTranscript> => {
      const response = await api.get<StudentTranscript>('/enrollments/my/transcript',
        { params: { semesterId } });
      return response.data;
    },
  };`;
  // Same oracle, old source: the illegal parameter is expressible.
  assert.deepEqual(expressibleQueryParams(before, 'gradesApi', 'getMyTranscript'), ['semesterId']);
});

test('no caller passes an argument to getMyTranscript', () => {
  const offenders = [];
  const walk = (dir) => {
    for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
      if (entry.name === 'node_modules' || entry.name.startsWith('.')) continue;
      const full = path.join(dir, entry.name);
      if (entry.isDirectory()) {
        walk(full);
      } else if (/\.tsx?$/.test(entry.name)) {
        fs.readFileSync(full, 'utf8').split(/\r?\n/).forEach((line, index) => {
          if (/getMyTranscript\(\s*[^)\s]/.test(line)) {
            offenders.push(`${path.relative(root, full)}:${index + 1}`);
          }
        });
      }
    }
  };
  walk(path.join(root, 'src'));
  assert.deepEqual(offenders, []);
});

// ---------------------------------------------------------------------------
// DEEP-P3-2: GET /courses allow-listed only page/limit/search, while
// coursesApi.getAll advertised an optional `departmentId` that the route
// answered with HTTP 400. The admin page worked around the refusal by slicing
// the already-paginated 20-row page in the browser, so a department with more
// courses than one page could never be listed and the pager total described
// the unfiltered set. Both halves are now closed: the route answers
// departmentId with a bound SQL predicate shared by the page and the count, and
// the client expresses exactly the parameters the route accepts.
// ---------------------------------------------------------------------------

test('coursesApi.getAll stays inside the /courses query allow-list', () => {
  const allowed = serverAllowedQueryParams(ACADEMIC_CONTROLLER, 'courses');
  // The department filter is answered by the route, not merely tolerated.
  assert.ok(allowed.includes('departmentId'), `/courses refuses departmentId: ${allowed.join(', ')}`);
  assert.ok(allowed.includes('search'), `/courses refuses search: ${allowed.join(', ')}`);

  const expressible = expressibleQueryParams(API_SOURCE, 'coursesApi', 'getAll');
  assert.deepEqual(
    expressible.filter((name) => !allowed.includes(name)),
    [],
    `/courses allow-lists ${allowed.join(', ')} but the client can send ${expressible.join(', ')}`,
  );
  // Paging without being able to name the department puts the browser filter
  // back on the table, so the parameter has to be reachable from the client too.
  for (const name of ['page', 'limit', 'departmentId']) {
    assert.ok(expressible.includes(name), `coursesApi.getAll cannot express ${name}`);
  }
});

test('the admin courses page asks the server for a department instead of slicing the page', () => {
  const page = read('src/app/admin/courses/page.tsx');

  // The select feeds the request, not the fetched array.
  assert.match(
    page,
    /departmentId: departmentFilter \|\| undefined/,
    'the department must be sent as a query parameter like search already is',
  );
  assert.doesNotMatch(
    page,
    /course\.departmentId === departmentFilter/,
    'the page must not narrow the fetched rows by department in the browser',
  );
  assert.doesNotMatch(page, /const filteredCourses/, 'the client-side department array must be gone');

  // Because the server pages the filtered set, changing department has to
  // restart at page 1 and re-issue the request.
  assert.match(
    page,
    /setDepartmentFilter\(event\.target\.value\);\s*setPage\(1\);/,
    'choosing a department must return to page 1',
  );
  assert.match(
    page,
    /\}, \[[^\]]*\bdepartmentFilter\b[^\]]*\bpage\b[^\]]*\]\);/,
    'the fetch must re-run when the department or the page changes',
  );
});

test('the courses guard rejects what the route refuses and accepts what it answers', () => {
  const beforeFix = `export const coursesApi = {
    getAll: async (params?: { page?: number; limit?: number; departmentId?: string })
      : Promise<ApiResponse<Course[]>> => {
      const response = await api.get<ApiResponse<Course[]>>('/courses', { params });
      return response.data;
    },
  };`;
  // Same oracle, same source, the route as it was: departmentId was illegal.
  const preFixAllowList = ['limit', 'page', 'search'];
  assert.deepEqual(
    expressibleQueryParams(beforeFix, 'coursesApi', 'getAll')
      .filter((name) => !preFixAllowList.includes(name)),
    ['departmentId'],
  );
  // And it is clean today only because the server learned to answer it.
  const allowed = serverAllowedQueryParams(ACADEMIC_CONTROLLER, 'courses');
  assert.deepEqual(
    expressibleQueryParams(beforeFix, 'coursesApi', 'getAll')
      .filter((name) => !allowed.includes(name)),
    [],
  );

  // The subset check is not a no-op: a parameter the route still refuses fails.
  const overreaching = `export const coursesApi = {
    getAll: async (params?: { page?: number; limit?: number; departmentId?: string; facultyId?: string })
      : Promise<ApiResponse<Course[]>> => {
      const response = await api.get<ApiResponse<Course[]>>('/courses', { params });
      return response.data;
    },
  };`;
  assert.deepEqual(
    expressibleQueryParams(overreaching, 'coursesApi', 'getAll')
      .filter((name) => !allowed.includes(name)),
    ['facultyId'],
  );
});

test('gradesApi.getMyGrades still matches its own route allow-list', () => {
  assert.deepEqual(
    expressibleQueryParams(API_SOURCE, 'gradesApi', 'getMyGrades'),
    serverAllowedQueryParams(ENROLLMENT_CONTROLLER, 'enrollments/my/grades'),
  );
});
