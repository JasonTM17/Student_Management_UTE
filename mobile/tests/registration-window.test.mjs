import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);
const ts = require('typescript');

const mobileRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

function read(relativePath) {
  return fs.readFileSync(path.join(mobileRoot, relativePath), 'utf8');
}

function transpile(relativePath) {
  return ts.transpileModule(read(relativePath), {
    compilerOptions: {
      module: ts.ModuleKind.CommonJS,
      target: ts.ScriptTarget.ES2020,
      esModuleInterop: true,
    },
    fileName: path.basename(relativePath),
  }).outputText;
}

function loadClientModule() {
  const module = { exports: {} };
  const stubRequire = (id) =>
    id === 'react-native' ? { Platform: { OS: 'android' } } : require(id);
  new Function('exports', 'require', 'module', 'process', transpile('src/api/client.ts'))(
    module.exports,
    stubRequire,
    module,
    { env: {} },
  );
  return module.exports;
}

function loadRegistrationModule(clientModule) {
  const module = { exports: {} };
  new Function(
    'exports',
    'require',
    'module',
    'process',
    transpile('src/screens/student/registration-window.ts'),
  )(module.exports, () => clientModule, module, { env: {} });
  return module.exports;
}

const openRound = { id: 'round-1', status: 'OPEN', kind: 'REGISTRATION' };
const section = { id: 'sec-1', sectionNumber: '01', courseCode: 'CS101', courseName: 'Intro', capacity: 40, enrolledCount: 10, remainingSeats: 30, status: 'OPEN' };

test('describeSectionSlot renders the wire schedule the card used to hide', () => {
  const { describeSectionSlot } = loadRegistrationModule(loadClientModule());

  assert.equal(
    describeSectionSlot({
      schedules: [{ dayOfWeek: 2, startTime: '07:00', endTime: '09:30', room: 'A 101', lecturer: 'Minh Nguyen' }],
    }),
    'Day 2 · 07:00 – 09:30 · A 101',
  );
  assert.equal(
    describeSectionSlot({
      schedules: [
        { dayOfWeek: 3, startTime: '13:00', endTime: '15:00', room: 'B 202' },
        { dayOfWeek: 6, startTime: '07:00', endTime: '09:00', room: 'B 202' },
      ],
    }),
    'Day 3 · 13:00 – 15:00 · B 202 +1 more',
  );
  assert.equal(describeSectionSlot({ schedules: [] }), 'Schedule pending');
  assert.equal(describeSectionSlot({}), 'Schedule pending');
  assert.equal(
    describeSectionSlot({ schedules: [{ dayOfWeek: 5 }] }),
    'Day 5',
  );
});

test('registration cards show the served schedule instead of a pending placeholder', () => {
  const screens = fs.readFileSync(path.join(mobileRoot, 'src/screens/student/StudentScreens.tsx'), 'utf8');
  const start = screens.indexOf('export function RegistrationScreen');
  const end = screens.indexOf('export function', start + 1);
  const screen = end === -1 ? screens.slice(start) : screens.slice(start, end);

  assert.match(screen, /slot: describeSectionSlot\(section\)/);
  assert.doesNotMatch(screen, /slot: 'Schedule pending'/);
});

test('an open round with a served catalog reports the window as open', async () => {
  const { loadRegistrationCatalog } = loadRegistrationModule(loadClientModule());

  const result = await loadRegistrationCatalog({
    registrationRounds: async () => [openRound],
    registrationSections: async () => [section],
  });

  assert.equal(result.roundOpen, true);
  assert.equal(result.sections.length, 1);
});

test('an OPEN-status round outside its window cannot trap the student: the catalog refusal closes the gate', async () => {
  const clientModule = loadClientModule();
  const { ApiClientError } = clientModule;
  const { loadRegistrationCatalog } = loadRegistrationModule(clientModule);

  const result = await loadRegistrationCatalog({
    registrationRounds: async () => [openRound],
    registrationSections: async () => {
      throw new ApiClientError('No open registration window', 409, 'req', 'WINDOW_CLOSED');
    },
  });

  assert.equal(result.roundOpen, false);
  assert.deepEqual(result.sections, []);
});

test('cohort and missing-round refusals also close the gate', async () => {
  const clientModule = loadClientModule();
  const { ApiClientError } = clientModule;
  const { loadRegistrationCatalog } = loadRegistrationModule(clientModule);

  for (const code of ['COHORT_INELIGIBLE', 'ROUND_NOT_FOUND']) {
    const result = await loadRegistrationCatalog({
      registrationRounds: async () => [openRound],
      registrationSections: async () => {
        throw new ApiClientError('refused', 409, 'req', code);
      },
    });
    assert.equal(result.roundOpen, false, code);
  }
});

test('unexpected catalog failures still surface as a retryable error, not a silent closed gate', async () => {
  const clientModule = loadClientModule();
  const { ApiClientError } = clientModule;
  const { loadRegistrationCatalog } = loadRegistrationModule(clientModule);

  await assert.rejects(
    loadRegistrationCatalog({
      registrationRounds: async () => [openRound],
      registrationSections: async () => {
        throw new ApiClientError('boom', 500, 'req', 'HTTP_500');
      },
    }),
    (error) => error.code === 'HTTP_500',
  );
});
