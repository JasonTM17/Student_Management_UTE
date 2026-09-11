const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const ts = require('typescript');

const root = path.resolve(__dirname, '..');

function load(relativePath) {
  const source = fs.readFileSync(path.join(root, relativePath), 'utf8');
  const output = ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
  }).outputText;
  const moduleRecord = { exports: {} };
  Function('module', 'exports', output)(moduleRecord, moduleRecord.exports);
  return moduleRecord.exports;
}

test('assistant SSE parser handles split frames and ignores malformed data', () => {
  const { createAssistantSseParser } = load('src/lib/assistant-stream.ts');
  const events = [];
  const parser = createAssistantSseParser((event) => events.push(event));
  parser.push('data: {"type":"delta","text":"Hel');
  parser.push('lo"}\n\ndata: not-json\n\ndata: {"type":"done"}');
  parser.end();
  assert.deepEqual(events, [{ type: 'delta', text: 'Hello' }, { type: 'done' }]);
});

test('assistant SSE parser preserves Spring event names when payload omits discriminator', () => {
  const { createAssistantSseParser } = load('src/lib/assistant-stream.ts');
  const events = [];
  const parser = createAssistantSseParser((event) => events.push(event));
  parser.push('event: meta\ndata: {"conversationId":"c1"}\n\n');
  parser.push('event: done\ndata: {"messageId":"m1","degraded":true}\n\n');
  parser.end();
  assert.deepEqual(events, [
    { conversationId: 'c1', type: 'meta' },
    { messageId: 'm1', degraded: true, type: 'done' },
  ]);
});

test('assistant stream contract rejects out-of-order and duplicate delta sequences', () => {
  const { AssistantStreamOrder, parseAssistantStreamEvent } = load('src/lib/assistant-stream.ts');
  const order = new AssistantStreamOrder();
  order.accept(parseAssistantStreamEvent({ type: 'meta' }));
  order.accept(parseAssistantStreamEvent({ type: 'delta', sequence: 0, text: 'a' }));
  assert.throws(() => order.accept(parseAssistantStreamEvent({ type: 'delta', sequence: 0, text: 'b' })), /not increasing/);
  assert.throws(() => new AssistantStreamOrder().accept(parseAssistantStreamEvent({ type: 'done' })), /missing meta/);
});

test('assistant parser reports malformed JSON through opt-in invalid callback', () => {
  const { createAssistantSseParser } = load('src/lib/assistant-stream.ts');
  const invalid = [];
  const parser = createAssistantSseParser(() => undefined, { onInvalid: (error) => invalid.push(error.message) });
  parser.push('data: {bad-json}\n\n');
  assert.equal(invalid.length, 1);
});

test('assistant reducer owns streaming, citations, degraded, and retry states', () => {
  const reducerSource = fs.readFileSync(path.join(root, 'src/components/assistant/assistant-reducer.ts'), 'utf8');
  const panelSource = fs.readFileSync(path.join(root, 'src/components/assistant/AssistantPanel.tsx'), 'utf8');
  assert.match(reducerSource, /export function assistantReducer/);
  assert.match(reducerSource, /type: 'delta'/);
  assert.match(reducerSource, /type: 'citation'/);
  assert.match(reducerSource, /TURN_TERMINAL_RACE/);
  assert.match(reducerSource, /TURN_NOT_ACTIVE/);
  assert.match(panelSource, /TRANSIENT_TERMINAL_CODES/);
  assert.match(panelSource, /KNOWLEDGE_UNAVAILABLE/);
  assert.match(panelSource, /QUOTA_EXCEEDED/);
  assert.match(panelSource, /AbortController/);
  assert.match(panelSource, /Do not issue a JSON replay/);
});

test('assistant panel opens from dashboard entries and restores focus to its trigger', () => {
  const source = fs.readFileSync(path.join(root, 'src/components/assistant/AssistantPanel.tsx'), 'utf8');
  // The desktop floating launcher was removed so its pill no longer covers
  // table content; the header and sidebar launchers remain the entry points.
  assert.doesNotMatch(source, /data-assistant-launcher="campus-mark"/);
  assert.match(source, /open-campus-assistant/);
  assert.match(source, /triggerRef\.current = document\.activeElement/);
  assert.match(source, /triggerRef\.current\?\.focus\(\)/);
});

test('assistant history routes URI-encode owner-scoped identifiers', () => {
  const source = fs.readFileSync(path.join(root, 'src/lib/thesis-api.ts'), 'utf8');
  assert.match(source, /conversations\/\$\{encodeURIComponent\(conversationId\)\}\/messages/);
  assert.match(source, /delete\(`\/assistant\/conversations\/\$\{encodeURIComponent\(conversationId\)\}`\)/);
  assert.match(source, /post<AssistantReply>\('\/assistant\/chat'/);
  assert.doesNotMatch(source, /post<AssistantReply>\('\/thesis\/assistant\/chat'/);
});

test('assistant streaming hook encapsulates lifecycle, CAS recovery, and cleanup', () => {
  const hookSource = fs.readFileSync(
    path.join(root, 'src/components/assistant/useAssistantStream.ts'),
    'utf8',
  );
  assert.match(hookSource, /export function useAssistantStream/);
  assert.match(hookSource, /thesisApi\.streamChat/);
  assert.match(hookSource, /thesisApi\.cancelRequest/);
  assert.match(hookSource, /casResolvedRef/);
  assert.match(hookSource, /status === 409/);
  assert.match(hookSource, /TRANSIENT_TERMINAL_CODES/);
  assert.match(hookSource, /onReconcileHistory/);
  assert.match(hookSource, /isSendingRef/);
});

test('assistant guard blocks end the turn locally without a JSON replay', () => {
  const reducerSource = fs.readFileSync(path.join(root, 'src/components/assistant/assistant-reducer.ts'), 'utf8');
  const hookSource = fs.readFileSync(path.join(root, 'src/components/assistant/useAssistantStream.ts'), 'utf8');
  const messagesSource = fs.readFileSync(path.join(root, 'src/i18n/messages.ts'), 'utf8');
  assert.match(reducerSource, /GUARD_BLOCKED_CODES = new Set\(\['PROMPT_INJECTION'\]\)/);
  assert.match(hookSource, /GUARD_BLOCKED_CODES\.has/);
  // The localized blocked copy exists in both locales and the chip gets a
  // dedicated label instead of the generic degraded badge.
  assert.match(messagesSource, /blockedLabel: 'Blocked request'/);
  assert.match(messagesSource, /blockedLabel: 'Câu hỏi đã bị chặn'/);
  assert.match(messagesSource, /asks the assistant to ignore its instructions/);
  assert.match(messagesSource, /yêu cầu trợ lý bỏ qua hướng dẫn hệ thống/);
});

test('assistant stop-race and quota-retry regressions stay guarded', () => {
  const hookSource = fs.readFileSync(path.join(root, 'src/components/assistant/useAssistantStream.ts'), 'utf8');
  // A Stop click after the final done frame must not overwrite the answer.
  assert.match(hookSource, /casResolvedRef\.current \|\| sawDone\) return/);
  // Terminal fallback failures must clear the retry idempotency key.
  assert.match(hookSource, /kind === 'quota' \? 'QUOTA_EXCEEDED' : 'KNOWLEDGE_UNAVAILABLE'/);
  const fallback = hookSource.indexOf("kind === 'quota' ? 'QUOTA_EXCEEDED' : 'KNOWLEDGE_UNAVAILABLE'");
  const afterFallback = hookSource.slice(fallback, fallback + 500);
  assert.match(afterFallback, /retryRequestIdRef\.current = undefined/);
  assert.match(afterFallback, /retryConversationIdRef\.current = undefined/);
});

test('personalized student assistant query detection and unaccented day matching', () => {
  const source = fs.readFileSync(path.join(root, 'src/lib/assistant-student-resolver.ts'), 'utf8');
  assert.match(source, /export function isStudentAssistantQuery/);
  assert.match(source, /export function detectRequestedDay/);
  assert.match(source, /GRADES_REGEX/);
  assert.match(source, /TUITION_REGEX/);
  assert.match(source, /THESIS_REGEX/);
  assert.match(source, /CURRICULUM_REGEX/);
  assert.match(source, /PROFILE_REGEX/);
  assert.match(source, /TEACHING_REGEX/);

  // Extract functions for runtime assertion
  const output = ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
  }).outputText;
  const moduleRecord = { exports: {} };
  // Mock external API modules imported by assistant-student-resolver
  const fakeRequire = (moduleName) => {
    return {};
  };
  Function('module', 'exports', 'require', output)(moduleRecord, moduleRecord.exports, fakeRequire);
  const { isStudentAssistantQuery, detectRequestedDay } = moduleRecord.exports;

  // 1. Day detection (accented and unaccented)
  assert.equal(detectRequestedDay('Lịch thứ 2 của tôi là khi nào'), 2);
  assert.equal(detectRequestedDay('lich thu 2 cua toi la khi nao'), 2);
  assert.equal(detectRequestedDay('t2 co tiet khong'), 2);
  assert.equal(detectRequestedDay('monday classes'), 2);

  assert.equal(detectRequestedDay('thứ 3 học phòng nào'), 3);
  assert.equal(detectRequestedDay('thu 3 hoc o dau'), 3);
  assert.equal(detectRequestedDay('t3 co mon gi'), 3);

  assert.equal(detectRequestedDay('thứ 4 có học không'), 4);
  assert.equal(detectRequestedDay('thu tu co lop khong'), 4);
  assert.equal(detectRequestedDay('thu 4 co tiet khong'), 4);

  assert.equal(detectRequestedDay('thứ 5 học gì'), 5);
  assert.equal(detectRequestedDay('thu nam hoc gi'), 5);

  assert.equal(detectRequestedDay('thứ 6 có môn gì'), 6);
  assert.equal(detectRequestedDay('thu 6 co mon gi'), 6);

  assert.equal(detectRequestedDay('thứ 7 học mấy giờ'), 7);
  assert.equal(detectRequestedDay('thu bay hoc may gio'), 7);

  assert.equal(detectRequestedDay('chủ nhật có học không'), 1);
  assert.equal(detectRequestedDay('chu nhat co lop khong'), 1);
  assert.equal(detectRequestedDay('cn co lich khong'), 1);

  // 2. Query detection coverage
  assert.equal(isStudentAssistantQuery('lịch thứ 2 của tôi là khi nào'), true);
  assert.equal(isStudentAssistantQuery('lich thu 2 cua toi'), true);
  assert.equal(isStudentAssistantQuery('điểm của tôi thế nào'), true);
  assert.equal(isStudentAssistantQuery('diem gpa cua toi'), true);
  assert.equal(isStudentAssistantQuery('bảng điểm học kỳ'), true);
  assert.equal(isStudentAssistantQuery('học phí của tôi còn nợ không'), true);
  assert.equal(isStudentAssistantQuery('hoc phi ky nay bao nhieu'), true);
  assert.equal(isStudentAssistantQuery('đồ án tốt nghiệp của tôi'), true);
  assert.equal(isStudentAssistantQuery('do an tot nghiep'), true);
  assert.equal(isStudentAssistantQuery('thông tin sinh viên của tôi'), true);
  assert.equal(isStudentAssistantQuery('mssv cua toi la gi'), true);
  assert.equal(isStudentAssistantQuery('chương trình đào tạo của tôi'), true);
  assert.equal(isStudentAssistantQuery('lớp tôi đang dạy'), true);
  assert.equal(isStudentAssistantQuery('lop toi dang day'), true);
});

test('student assistant resolves schedules and materials from portal APIs', async () => {
  const source = fs.readFileSync(path.join(root, 'src/lib/assistant-student-resolver.ts'), 'utf8');
  const output = ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
  }).outputText;
  const calls = { enrollments: 0, announcements: 0 };
  const currentEnrollments = [
    {
      id: 'enrollment-se101',
      sectionId: 'section-se101-01',
      status: 'CONFIRMED',
      section: {
        sectionNumber: '01',
        course: {
          code: 'SE101',
          name: 'Software Engineering',
          nameVi: 'Kỹ thuật phần mềm',
        },
        schedules: [
          {
            dayOfWeek: 2,
            startTime: '07:00',
            endTime: '09:30',
            classroom: { roomNumber: 'A101' },
          },
        ],
      },
    },
    {
      id: 'dropped-course',
      sectionId: 'section-old',
      status: 'DROPPED',
      section: {
        sectionNumber: '99',
        course: { code: 'OLD101', name: 'Old course' },
        schedules: [{ dayOfWeek: 2, startTime: '13:00', endTime: '15:00' }],
      },
    },
  ];
  const fakeRequire = (moduleName) => {
    if (moduleName === '@/lib/api') {
      return {
        authApi: { me: async () => ({ id: 'student-user', roles: ['STUDENT'] }) },
        enrollmentsApi: {
          getMyEnrollments: async () => {
            calls.enrollments += 1;
            return currentEnrollments;
          },
        },
        announcementsApi: {
          getMy: async () => {
            calls.announcements += 1;
            return {
              data: [
                {
                  id: 'notice-slide-week-2',
                  title: 'Slide tuần 2',
                  content: '<p>Tải slide và bài giảng trong thông báo lớp.</p>',
                  priority: 'NORMAL',
                  createdAt: '2026-09-09T00:00:00Z',
                  isGlobal: false,
                  sectionId: 'section-se101-01',
                  courseCode: 'SE101',
                },
                {
                  id: 'notice-unrelated',
                  title: 'Sinh hoạt lớp',
                  content: 'Không phải tài liệu môn học.',
                  priority: 'NORMAL',
                  createdAt: '2026-09-09T00:00:00Z',
                  sectionId: 'section-other',
                  courseCode: 'OTHER',
                },
              ],
            };
          },
        },
        sectionsApi: { getMySchedule: async () => [] },
        curriculumApi: {},
        gradesApi: {},
        registrationApi: {},
      };
    }
    return {};
  };
  const moduleRecord = { exports: {} };
  Function('module', 'exports', 'require', output)(moduleRecord, moduleRecord.exports, fakeRequire);
  const { resolveStudentAssistantQuery } = moduleRecord.exports;

  const schedule = await resolveStudentAssistantQuery('lịch thứ 2 của tôi ở phòng nào', 'vi');
  assert.match(schedule.answer, /SE101 - Kỹ thuật phần mềm/);
  assert.match(schedule.answer, /07:00 - 09:30/);
  assert.match(schedule.answer, /A101/);
  assert.doesNotMatch(schedule.answer, /OLD101/);

  const materials = await resolveStudentAssistantQuery('Tài liệu môn học và slide ở đâu?', 'vi');
  assert.match(materials.answer, /SE101 - Kỹ thuật phần mềm/);
  assert.match(materials.answer, /Slide tuần 2 \(SE101\)/);
  assert.match(materials.answer, /Tải slide và bài giảng/);
  assert.doesNotMatch(materials.answer, /<p>/);
  assert.equal(materials.citation.source, 'academic-records');
  assert.equal(materials.citation.slug, 'materials-from-enrollments-announcements');
  assert.ok(calls.enrollments >= 2);
  assert.equal(calls.announcements, 1);
});
