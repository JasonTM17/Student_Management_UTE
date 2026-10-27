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
      ['gpa4Invalid', 'gpa10Invalid', 'conductInvalid', 'gradesRequired'],
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

test('toWireInt rounds form values to the integer the wire contract expects', async (t) => {
  const helpers = await getHelpers();
  await t.test('the PR #8 regression: "87,5" must leave as 88, not 87.5', () => {
    // parseNumericInput accepts the Vietnamese comma decimal, so the page
    // hands the raw 87.5 to the payload builder — which must round it.
    const parsed = helpers.parseNumericInput('87,5');
    assert.equal(parsed, 87.5);
    assert.equal(helpers.toWireInt(parsed), 88);
  });
  await t.test('half-down, negative, and already-int values round correctly', () => {
    assert.equal(helpers.toWireInt(86.4), 86);
    assert.equal(helpers.toWireInt(-2.5), -2);
    assert.equal(helpers.toWireInt(95), 95);
  });
  await t.test('null/empty input falls back (default 0) instead of sending null', () => {
    assert.equal(helpers.toWireInt(null), 0);
    assert.equal(helpers.toWireInt(undefined), 0);
    assert.equal(helpers.toWireInt(null, 1), 1);
  });
});

test('credits cells validate instead of silently clamping negatives to zero', async (t) => {
  const helpers = await getHelpers();
  await t.test('rowCreditsValid: empty allowed, negative and junk rejected', () => {
    assert.equal(helpers.rowCreditsValid(''), true);
    assert.equal(helpers.rowCreditsValid('   '), true);
    assert.equal(helpers.rowCreditsValid('3'), true);
    assert.equal(helpers.rowCreditsValid(0), true);
    assert.equal(helpers.rowCreditsValid('2,5'), true, 'decimals are legal input and round at payload time');
    assert.equal(helpers.rowCreditsValid(-2), false);
    assert.equal(helpers.rowCreditsValid('-2'), false);
    assert.equal(helpers.rowCreditsValid('-1,5'), false);
    assert.equal(helpers.rowCreditsValid('abc'), false);
  });

  await t.test('registrationFormIssues flags a negative-credits row', () => {
    assert.deepEqual(
      helpers.registrationFormIssues({
        to: 'sv@student.ute.edu.vn',
        studentName: 'Nguyen Van A',
        studentId: '22110001',
        courses: [{ code: 'SE013', name: 'Web', credits: '-2' }],
      }),
      ['creditsInvalid'],
    );
    assert.deepEqual(
      helpers.registrationFormIssues({
        to: 'sv@student.ute.edu.vn',
        studentName: 'Nguyen Van A',
        studentId: '22110001',
        courses: [{ code: 'SE013', name: 'Web', credits: '3' }],
      }),
      [],
    );
  });

  await t.test('gradeAlertFormIssues flags negative row credits separately from bad scores', () => {
    assert.deepEqual(
      helpers.gradeAlertFormIssues({
        to: 'sv@student.ute.edu.vn',
        studentName: 'Nguyen Van A',
        studentId: '22110001',
        gpa4: '',
        gpa10: '',
        conductScore: '87,5',
        grades: [{ courseCode: 'SE001', courseName: 'Intro', credits: '-3', score10: '8' }],
      }),
      ['creditsInvalid'],
      'conductScore "87,5" itself stays valid; the negative credits are the issue',
    );
    assert.deepEqual(
      helpers.gradeAlertFormIssues({
        to: 'sv@student.ute.edu.vn',
        studentName: 'Nguyen Van A',
        studentId: '22110001',
        gpa4: '',
        gpa10: '',
        conductScore: '',
        grades: [{ courseCode: 'SE001', courseName: 'Intro', credits: '3', score10: '8' }],
      }),
      [],
    );
  });
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
  // Field-level validation copy lives behind `attemptedSend`, which only
  // flips inside handleSend — so the send button must stay clickable while
  // issues exist, otherwise invalid input (e.g. GPA 9.99 > 4) leaves the
  // author with a permanently disabled button and no error text at all.
  assert.doesNotMatch(
    page,
    /disabled=\{isSending \|\| !readyToSend\}/,
    'send button must not be disabled by unmet issues; the click is what reveals them',
  );
  assert.match(page, /setAttemptedSend\(true\)/);
  // PR #8 follow-ups: int wire values and credits validation, not silent clamps.
  assert.match(page, /conductScore: toWireInt\(parseNumericInput\(gradeAlert\.conductScore\)\)/);
  assert.match(page, /rowCreditsValid/);

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

test('mail validators block payloads outside the MailDtos bounds', async (t) => {
  const helpers = await getHelpers();
  const validNotice = {
    to: 'sv@student.ute.edu.vn',
    recipientName: 'Nguyen Van A',
    category: 'Academic affairs',
    title: 'Registration notice',
    author: 'Faculty of Information Technology',
    content: 'Registration opens on Monday.',
    highlights: ['Registration opens Monday'],
    actionUrl: 'https://campus.example/registration',
    actionText: 'Open registration',
  };
  const validRegistration = {
    to: 'sv@student.ute.edu.vn',
    studentName: 'Nguyen Van A',
    studentId: '22110001',
    department: 'Faculty of Information Technology',
    semester: 'Semester 1, 2026-2027',
    courses: [{ code: 'SE013', name: 'Web Development', credits: 3, lecturer: 'Tran B', schedule: 'Mon 1-3' }],
    totalCredits: 3,
  };
  const validGradeAlert = {
    to: 'sv@student.ute.edu.vn',
    studentName: 'Nguyen Van A',
    studentId: '22110001',
    semester: 'Semester 1, 2026-2027',
    gpa4: '3.6',
    gpa10: '8.5',
    academicStanding: 'Good',
    conductScore: '90',
    conductRank: 'Very good',
    grades: [{ courseCode: 'SE013', courseName: 'Web Development', credits: 3, score10: '8.5', scoreLetter: 'A' }],
  };

  await t.test('notice content above 20,000 characters blocks sending', () => {
    assert.deepEqual(
      helpers.noticeFormIssues({ ...validNotice, content: 'x'.repeat(20_001) }),
      ['contentTooLong'],
    );
  });

  await t.test('notice title above 300 characters blocks sending', () => {
    assert.deepEqual(
      helpers.noticeFormIssues({ ...validNotice, title: 'x'.repeat(301) }),
      ['titleTooLong'],
    );
  });

  await t.test('more than 20 notice highlights blocks sending', () => {
    assert.deepEqual(
      helpers.noticeFormIssues({
        ...validNotice,
        highlights: Array.from({ length: 21 }, (_, index) => `Highlight ${index + 1}`),
      }),
      ['highlightsTooMany'],
    );
  });

  await t.test('a notice highlight above 500 characters blocks sending', () => {
    assert.deepEqual(
      helpers.noticeFormIssues({ ...validNotice, highlights: ['x'.repeat(501)] }),
      ['highlightTooLong'],
    );
  });

  await t.test('registration course credits above 30 block sending', () => {
    assert.deepEqual(
      helpers.registrationFormIssues({
        ...validRegistration,
        courses: [{ ...validRegistration.courses[0], credits: 31 }],
        totalCredits: 31,
      }),
      ['creditsInvalid'],
    );
  });

  await t.test('registration totals above 60 block sending', () => {
    const courses = [20, 20, 21].map((credits, index) => ({
      code: `SE00${index + 1}`,
      name: `Course ${index + 1}`,
      credits,
    }));
    assert.deepEqual(
      helpers.registrationFormIssues({ ...validRegistration, courses, totalCredits: 61 }),
      ['totalCreditsInvalid'],
    );
  });

  await t.test('GPA 4.5 returns a field-specific blocking issue', () => {
    assert.deepEqual(
      helpers.gradeAlertFormIssues({ ...validGradeAlert, gpa4: '4.5' }),
      ['gpa4Invalid'],
    );
  });

  await t.test('grade values and row counts stay inside DTO bounds', () => {
    assert.deepEqual(
      helpers.gradeAlertFormIssues({
        ...validGradeAlert,
        grades: [{ ...validGradeAlert.grades[0], credits: 31 }],
      }),
      ['creditsInvalid'],
    );
    assert.deepEqual(
      helpers.gradeAlertFormIssues({
        ...validGradeAlert,
        grades: [{ ...validGradeAlert.grades[0], score10: '10.1' }],
      }),
      ['gradeRowsInvalid'],
    );
    assert.deepEqual(
      helpers.gradeAlertFormIssues({
        ...validGradeAlert,
        grades: Array.from({ length: 61 }, (_, index) => ({
          courseCode: `SE${index}`,
          courseName: `Course ${index}`,
          credits: 1,
          score10: 8,
        })),
      }),
      ['gradesTooMany'],
    );
  });

  await t.test('the validators accept typical notice, registration, and grade payloads', () => {
    assert.deepEqual(helpers.noticeFormIssues(validNotice), []);
    assert.deepEqual(helpers.registrationFormIssues(validRegistration), []);
    assert.deepEqual(helpers.gradeAlertFormIssues(validGradeAlert), []);
  });

  await t.test('the standalone test-email intro message limit is exposed and validated', () => {
    assert.deepEqual(
      helpers.testEmailFormIssues({ introMessage: 'x'.repeat(20_001) }),
      ['introMessageTooLong'],
    );
  });

  await t.test('all optional notice strings stay within their DTO bounds', () => {
    const cases = [
      ['to', `${'a'.repeat(250)}@example.com`, 'emailTooLong'],
      ['recipientName', 'x'.repeat(256), 'recipientNameTooLong'],
      ['category', 'x'.repeat(256), 'categoryTooLong'],
      ['author', 'x'.repeat(256), 'authorTooLong'],
      ['actionUrl', 'x'.repeat(2_049), 'actionUrlTooLong'],
      ['actionText', 'x'.repeat(501), 'actionTextTooLong'],
    ];
    for (const [field, value, issue] of cases) {
      assert.ok(
        helpers.noticeFormIssues({ ...validNotice, [field]: value }).includes(issue),
        `${field} must return ${issue}`,
      );
    }
  });

  await t.test('registration identity, text, row, and course-string bounds are enforced', () => {
    const cases = [
      [{ studentName: 'x'.repeat(256) }, 'studentNameTooLong'],
      [{ studentId: 'x'.repeat(256) }, 'studentIdTooLong'],
      [{ department: 'x'.repeat(256) }, 'departmentTooLong'],
      [{ semester: 'x'.repeat(256) }, 'semesterTooLong'],
      [{ courses: [{ code: 'x'.repeat(256), name: 'Course', credits: 3 }] }, 'courseCodeTooLong'],
      [{ courses: [{ code: 'SE013', name: 'x'.repeat(256), credits: 3 }] }, 'courseNameTooLong'],
      [{ courses: [{ code: 'SE013', name: 'Course', credits: 3, lecturer: 'x'.repeat(256) }] }, 'lecturerTooLong'],
      [{ courses: [{ code: 'SE013', name: 'Course', credits: 3, schedule: 'x'.repeat(256) }] }, 'scheduleTooLong'],
      [{ courses: Array.from({ length: 31 }, (_, index) => ({ code: `SE${index}`, name: `Course ${index}`, credits: 1 })), totalCredits: 31 }, 'coursesTooMany'],
    ];
    for (const [patch, issue] of cases) {
      assert.ok(
        helpers.registrationFormIssues({ ...validRegistration, ...patch }).includes(issue),
        `${issue} must be returned for the matching registration field`,
      );
    }
  });

  await t.test('grade identity, summary, and row strings match the DTO bounds', () => {
    const cases = [
      [{ studentName: 'x'.repeat(256) }, 'studentNameTooLong'],
      [{ studentId: 'x'.repeat(256) }, 'studentIdTooLong'],
      [{ semester: 'x'.repeat(256) }, 'semesterTooLong'],
      [{ academicStanding: 'x'.repeat(101) }, 'academicStandingTooLong'],
      [{ conductRank: 'x'.repeat(101) }, 'conductRankTooLong'],
      [{ gpa10: '10.1' }, 'gpa10Invalid'],
      [{ conductScore: '101' }, 'conductInvalid'],
      [{ grades: [{ ...validGradeAlert.grades[0], courseCode: 'x'.repeat(256) }] }, 'courseCodeTooLong'],
      [{ grades: [{ ...validGradeAlert.grades[0], courseName: 'x'.repeat(256) }] }, 'courseNameTooLong'],
      [{ grades: [{ ...validGradeAlert.grades[0], scoreLetter: 'x'.repeat(101) }] }, 'scoreLetterTooLong'],
    ];
    for (const [patch, issue] of cases) {
      assert.ok(
        helpers.gradeAlertFormIssues({ ...validGradeAlert, ...patch }).includes(issue),
        `${issue} must be returned for the matching grade field`,
      );
    }
  });
});

test('lecturer mail controls expose backend text limits and localized counters', () => {
  const page = read('src/app/dashboard/lecturer/mail/page.tsx');
  for (const limit of [
    'notice.email',
    'notice.recipientName',
    'notice.title',
    'notice.content',
    'notice.highlight',
    'notice.actionUrl',
    'notice.actionText',
    'common.studentName',
    'common.studentId',
    'registration.courseCode',
    'registration.courseName',
    'registration.lecturer',
    'registration.schedule',
    'gradeAlert.semester',
    'gradeAlert.courseCode',
    'gradeAlert.courseName',
    'gradeAlert.conductRank',
  ]) {
    assert.ok(page.includes(`maxLength={MAIL_COMPOSE_LIMITS.${limit}}`), `${limit} must have a maxLength attribute`);
  }

  const messages = read('src/i18n/messages.ts');
  const viStart = messages.indexOf('export const vi');
  const en = messages.slice(0, viStart);
  const vi = messages.slice(viStart);
  assert.match(en, /charCount: '\{count\} \/ \{max\} characters'/);
  assert.match(vi, /charCount: '\{count\} \/ \{max\} ký tự'/);
  for (const issue of [
    'contentTooLong',
    'highlightsTooMany',
    'titleTooLong',
    'creditsInvalid',
    'totalCreditsInvalid',
    'gpa4Invalid',
  ]) {
    assert.match(en, new RegExp(`${issue}:`), `${issue} must be localized in English`);
    assert.match(vi, new RegExp(`${issue}:`), `${issue} must be localized in Vietnamese`);
  }
});

test('recipient summary rail label never leaks a raw {max} placeholder', () => {
  const page = read('src/app/dashboard/lecturer/mail/page.tsx');
  // Regression for the recipient-summary rail: reusing the {count}/{max}
  // hint template with only {count} stripped rendered the literal "{max}"
  // text as the row label. The rail must use its own static label key.
  assert.ok(
    !page.includes("RailRow label={copy.fields.charCount"),
    'rail label must not reuse the charCount hint template',
  );
  assert.ok(
    page.includes('RailRow label={copy.fields.contentLength}'),
    'expected a dedicated contentLength rail label',
  );

  const messages = read('src/i18n/messages.ts');
  const viStart = messages.indexOf('export const vi');
  const en = messages.slice(0, viStart);
  const vi = messages.slice(viStart);
  assert.match(en, /contentLength: '[^']+'/, 'contentLength must be localized in English');
  assert.match(vi, /contentLength: '[^']+'/, 'contentLength must be localized in Vietnamese');
});
