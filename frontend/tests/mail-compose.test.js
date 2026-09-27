'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const ts = require('typescript');

const root = path.resolve(__dirname, '..');

function read(relativePath) {
  return fs.readFileSync(path.join(root, relativePath), 'utf8');
}

function transpile(relativePath) {
  const source = read(relativePath);
  const { outputText } = ts.transpileModule(source, {
    compilerOptions: {
      module: ts.ModuleKind.ESNext,
      target: ts.ScriptTarget.ES2022,
    },
    fileName: relativePath,
  });
  return import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`);
}

let helpersPromise = null;

function getHelpers() {
  if (!helpersPromise) {
    helpersPromise = transpile('src/lib/mail-compose.ts');
  }
  return helpersPromise;
}

test('totalCredits auto-sums the registration course table', async (t) => {
  const helpers = await getHelpers();
  await t.test('adds numeric and numeric-string credits, skipping junk rows', () => {
    assert.equal(
      helpers.sumCourseCredits([
        { credits: 3 },
        { credits: '4' },
        { credits: '' },
        { credits: 'abc' },
        { credits: -2 },
      ]),
      7,
    );
  });

  await t.test('parses the Vietnamese decimal comma; credits round to whole ints per row', () => {
    // The DTO types credits as int, so each row rounds at parse time
    // (2.5 -> 3, 1.5 -> 2) instead of accumulating a fractional total.
    assert.equal(helpers.sumCourseCredits([{ credits: '2,5' }, { credits: '1,5' }]), 5);
  });

  await t.test('an empty table sums to 0 so totalCredits stays a number', () => {
    assert.equal(helpers.sumCourseCredits([]), 0);
  });
});

test('grade table computes the credit-weighted 10-scale average', async (t) => {
  const helpers = await getHelpers();
  await t.test('weights each score by its credits', () => {
    const average = helpers.creditWeightedAverage10([
      { credits: 3, score10: 9.0 },
      { credits: 4, score10: 8.0 },
      { credits: 3, score10: 7.0 },
    ]);
    // (27 + 32 + 21) / 10 = 8.0
    assert.equal(average, 8.0);
  });

  await t.test('skips rows without a valid score in [0, 10]', () => {
    const average = helpers.creditWeightedAverage10([
      { credits: 3, score10: 9.0 },
      { credits: 3, score10: '' },
      { credits: 3, score10: 12 },
      { credits: 3, score10: 'oops' },
    ]);
    assert.equal(average, 9.0);
  });

  await t.test('returns null when no row qualifies instead of a fake 0', () => {
    assert.equal(helpers.creditWeightedAverage10([]), null);
    assert.equal(helpers.creditWeightedAverage10([{ credits: 3, score10: '' }]), null);
  });

  await t.test('parses comma decimals and rounds to 2 decimals', () => {
    const average = helpers.creditWeightedAverage10([
      { credits: '1', score10: '8,555' },
      { credits: '1', score10: 9 },
    ]);
    assert.equal(average, 8.78);
  });
});

test('validation predicates gate each template form', async (t) => {
  const helpers = await getHelpers();
  await t.test('notice requires a valid email, title, and content', () => {
    assert.deepEqual(helpers.noticeFormIssues({ to: '', title: '', content: '' }), [
      'emailRequired',
      'titleRequired',
      'contentRequired',
    ]);
    assert.deepEqual(
      helpers.noticeFormIssues({ to: 'not-an-email', title: 'T', content: 'C' }),
      ['emailInvalid'],
    );
    assert.deepEqual(
      helpers.noticeFormIssues({ to: 'sv@student.ute.edu.vn', title: 'T', content: 'C' }),
      [],
    );
  });

  await t.test('registration requires the student identity and clean course rows', () => {
    assert.deepEqual(
      helpers.registrationFormIssues({
        to: 'sv@student.ute.edu.vn',
        studentName: 'Nguyen Van A',
        studentId: '22110001',
        courses: [],
      }),
      ['coursesRequired'],
    );
    assert.deepEqual(
      helpers.registrationFormIssues({
        to: 'sv@student.ute.edu.vn',
        studentName: '',
        studentId: '',
        courses: [{ code: 'SE013', name: '' }],
      }),
      ['studentNameRequired', 'studentIdRequired', 'courseRowsInvalid'],
    );
    assert.deepEqual(
      helpers.registrationFormIssues({
        to: 'sv@student.ute.edu.vn',
        studentName: 'Nguyen Van A',
        studentId: '22110001',
        courses: [{ code: 'SE013', name: 'Web' }],
      }),
      [],
    );
  });

  await t.test('grade alert validates GPA scale, conduct score, and score rows', () => {
    assert.deepEqual(
      helpers.gradeAlertFormIssues({
        to: 'sv@student.ute.edu.vn',
        studentName: 'Nguyen Van A',
        studentId: '22110001',
        gpa4: '4.5',
        gpa10: '11',
        conductScore: '120',
        grades: [],
      }),
      ['gpaInvalid', 'conductInvalid', 'gradesRequired'],
    );
    assert.deepEqual(
      helpers.gradeAlertFormIssues({
        to: 'sv@student.ute.edu.vn',
        studentName: 'Nguyen Van A',
        studentId: '22110001',
        gpa4: '',
        gpa10: '',
        conductScore: '',
        grades: [{ courseCode: 'SE001', courseName: 'Intro', credits: 3, score10: '9,5' }],
      }),
      [],
    );
    assert.deepEqual(
      helpers.gradeAlertFormIssues({
        to: 'sv@student.ute.edu.vn',
        studentName: 'Nguyen Van A',
        studentId: '22110001',
        gpa4: '',
        gpa10: '',
        conductScore: '',
        grades: [{ courseCode: 'SE001', courseName: 'Intro', credits: 3, score10: '15' }],
      }),
      ['gradeRowsInvalid'],
    );
  });
});

test('error mapping turns dispatch failures into stable copy keys', async (t) => {
  const helpers = await getHelpers();
  await t.test('429 and RATE_LIMIT_EXCEEDED map to the rate-limit copy', () => {
    assert.equal(helpers.classifyMailError({ response: { status: 429 } }), 'rateLimit');
    assert.equal(
      helpers.classifyMailError({ response: { status: 400, data: { code: 'RATE_LIMIT_EXCEEDED' } } }),
      'rateLimit',
    );
  });

  await t.test('502 and MAIL_DELIVERY_FAILED map to the delivery copy', () => {
    assert.equal(helpers.classifyMailError({ response: { status: 502 } }), 'deliveryFailed');
    assert.equal(
      helpers.classifyMailError({ response: { status: 500, data: { code: 'MAIL_DELIVERY_FAILED' } } }),
      'deliveryFailed',
    );
  });

  await t.test('400 maps to validation and missing response to network', () => {
    assert.equal(helpers.classifyMailError({ response: { status: 400 } }), 'validation');
    assert.equal(helpers.classifyMailError({ response: { status: 422 } }), 'validation');
    assert.equal(helpers.classifyMailError(new Error('boom')), 'network');
    assert.equal(helpers.classifyMailError('nonsense'), 'unknown');
  });
});

test('preview URL reuses the axios base and the backend template slugs', async (t) => {
  const helpers = await getHelpers();
  assert.equal(helpers.mailPreviewSlug('notice'), 'academic-announcement');
  assert.equal(helpers.mailPreviewSlug('registration'), 'course-registration');
  assert.equal(helpers.mailPreviewSlug('grade-alert'), 'grade-alert');

  assert.equal(
    helpers.mailPreviewUrl('/api/v1', 'notice'),
    '/api/v1/mail/preview/academic-announcement',
  );
  assert.equal(
    helpers.mailPreviewUrl('https://api.example.com/api/v1/', 'grade-alert'),
    'https://api.example.com/api/v1/mail/preview/grade-alert',
  );
});

test('optional text and highlight chips never leak blank or duplicate values', async () => {
  const helpers = await getHelpers();
  assert.equal(helpers.optionalText('  hello  '), 'hello');
  assert.equal(helpers.optionalText('   '), undefined);
  assert.deepEqual(helpers.parseHighlightLines('A\n\n B \nA\nC'), ['A', 'B', 'C']);
});

test('mail compose wiring is registered across api, layout, routes, and i18n', () => {
  const api = read('src/lib/api.ts');
  assert.match(api, /export const mailApi/);
  assert.match(api, /'\/mail\/notice'/);
  assert.match(api, /'\/mail\/registration'/);
  assert.match(api, /'\/mail\/grade-alert'/);
  assert.match(api, /MailDispatchResponse/);

  const layout = read('src/app/dashboard/layout.tsx');
  assert.match(layout, /'\/dashboard\/lecturer\/mail'/);
  assert.match(layout, /mailCompose/);

  const page = read('src/app/dashboard/lecturer/mail/page.tsx');
  assert.match(page, /mailApi\.sendNotice/);
  assert.match(page, /mailApi\.sendRegistration/);
  assert.match(page, /mailApi\.sendGradeAlert/);
  assert.match(page, /mailPreviewUrl\(API_BASE_URL, template\)/);
  assert.match(page, /classifyMailError/);

  assert.ok(
    fs.existsSync(path.join(root, 'src/app/[locale]/dashboard/lecturer/mail/page.tsx')),
    'the /en locale counterpart page must exist',
  );

  const messages = read('src/i18n/messages.ts');
  const enBlock = messages.slice(0, messages.indexOf('export const vi'));
  assert.match(enBlock, /mailCompose: \{/, 'the en dictionary must carry the mailCompose namespace');
  assert.match(messages.slice(messages.indexOf('export const vi')), /mailCompose: \{/,
    'the vi dictionary must carry the mailCompose namespace');
  assert.equal(
    (messages.match(/mailCompose: '/g) || []).length,
    2,
    'the sidebar label must exist for both en and vi',
  );
});
