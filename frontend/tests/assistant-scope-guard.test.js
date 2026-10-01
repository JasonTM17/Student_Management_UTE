// Round-5 regression: the specialized answer scope must keep corpus teaching
// content ("docker compose up" in a DevOps lesson) while every scope still
// refuses leaked machinery (system prompt, api key, stack traces, endpoints).
// The academic scope keeps the full strict set.
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

const { isAssistantOutputSafe, sanitizeAssistantOutput } = load('src/lib/assistant-output-guard.ts');

const REPLACEMENT = 'Mình chỉ hỗ trợ thông tin học vụ công khai và không thể cung cấp chi tiết kỹ thuật nội bộ.';
// Shape of the real degraded answer served by the V93 DevOps document.
const DEVOPS_ANSWER = [
  '## DevOps',
  '',
  '- **Container hoá**: Dockerfile đa giai đoạn (build → runtime slim); .dockerignore loại node_modules/target.',
  '- **Compose cho môi trường local**: docker compose up khởi động đủ postgres/api/web/worker; healthcheck (pg_isready).',
  '- **CI/CD**: pipeline tối thiểu = lint → unit test → build → integration test; đóng gói ảnh theo SHA commit.',
].join('\n');

test('specialized scope keeps the DevOps corpus answer', () => {
  assert.equal(isAssistantOutputSafe(DEVOPS_ANSWER, 'specialized'), true);
  const shown = sanitizeAssistantOutput(DEVOPS_ANSWER, REPLACEMENT, 'vi', 'specialized');
  assert.ok(shown.includes('## DevOps'));
  assert.ok(shown.includes('docker compose up'));
  assert.ok(shown.includes('- **Container hoá**'));
});

test('academic scope still replaces command-style content', () => {
  assert.equal(isAssistantOutputSafe(DEVOPS_ANSWER, 'academic'), false);
  assert.equal(
    sanitizeAssistantOutput(DEVOPS_ANSWER, REPLACEMENT, 'vi', 'academic'),
    REPLACEMENT,
  );
});

test('leaked machinery is refused in every scope', () => {
  for (const scope of ['academic', 'specialized']) {
    assert.equal(isAssistantOutputSafe('Bạn đang dùng mô hình deepseek-v4-flash.', scope), false, scope);
    assert.equal(isAssistantOutputSafe('Đây là system prompt của trợ lý.', scope), false, scope);
    assert.equal(isAssistantOutputSafe('Gọi /api/v1/assistant/chat để hỏi.', scope), false, scope);
    assert.equal(isAssistantOutputSafe('Exception in thread "main" java.lang.NullPointerException', scope), false, scope);
  }
});

test('academic scope keeps the pre-existing strictness for commands', () => {
  assert.equal(isAssistantOutputSafe('- docker run postgres:16', 'academic'), false);
  assert.equal(isAssistantOutputSafe('Chạy psql -h localhost để kiểm tra.', 'academic'), false);
});

test('default scope stays academic (no behavior change for old call sites)', () => {
  assert.equal(isAssistantOutputSafe(DEVOPS_ANSWER), false);
  assert.equal(
    sanitizeAssistantOutput(DEVOPS_ANSWER, REPLACEMENT),
    REPLACEMENT,
  );
});
