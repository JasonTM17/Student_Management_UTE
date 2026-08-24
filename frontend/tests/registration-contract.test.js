const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const assert = require('node:assert/strict');

const root = path.resolve(__dirname, '..');
const read = (file) => fs.readFileSync(path.join(root, file), 'utf8');

test('registration client uses canonical owner-scoped APIs and idempotency', () => {
  const source = read('src/features/registration/api.ts');
  assert.match(source, /\/registration\/rounds/);
  assert.match(source, /\/me\/registration\/eligibility/);
  assert.match(source, /\/me\/enrollments\/validate/);
  assert.match(source, /\{ roundId, sectionId \}/);
  assert.match(source, /'Idempotency-Key'/);
  assert.match(source, /'enrollment' in response\.data/);
  assert.match(source, /\/admin\/registration\/rounds/);
});

test('registration workspace exposes canonical and compatibility routes', () => {
  const workspace = read('src/features/registration/RegistrationWorkspace.tsx');
  const alias = read('src/app/dashboard/register/page.tsx');
  assert.match(workspace, /registrationApi\.validate\(roundId, selected\)/);
  assert.match(workspace, /registrationStart/);
  assert.match(workspace, /serverOffset/);
  assert.match(workspace, /useConfirmationDialog/);
  assert.match(alias, /router\.replace\(href\('\/dashboard\/registration'\)\)/);
  assert.ok(fs.existsSync(path.join(root, 'src/app/admin/registration-rounds/page.tsx')));
});

test('assistant launcher carries status, privacy and safe-area semantics', () => {
  const source = read('src/components/assistant/AssistantPanel.tsx');
  assert.match(source, /aria-expanded=\{open\}/);
  assert.match(source, /safe-area-inset-bottom/);
  assert.match(source, /Không nhập mã sinh viên/);
  assert.match(source, /state\.error \? 'bg-destructive'/);
});
