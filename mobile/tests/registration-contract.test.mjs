import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const read = (file) => fs.readFileSync(path.join(root, file), 'utf8');

test('mobile registration uses the canonical Java routes and DTO shapes', () => {
  const client = read('src/api/client.ts');
  assert.match(client, /rounds: '\/registration\/rounds'/);
  assert.match(client, /eligibility: '\/me\/registration\/eligibility'/);
  assert.match(client, /summary: '\/me\/registration\/summary'/);
  assert.match(client, /enrollments: '\/me\/enrollments'/);
  assert.match(client, /validate: '\/me\/enrollments\/validate'/);
  assert.match(client, /registrationEnroll: async \(sectionId: string, roundId: string, idempotencyKey: string\)/);
  assert.match(client, /\{ sectionId, roundId \}/);
  assert.match(client, /'Idempotency-Key': idempotencyKey/);
  assert.match(client, /registrationDrop: \(enrollmentId: string, idempotencyKey: string\)/);
  assert.match(client, /normalizeRegistrationEnrollment/);
  assert.match(client, /apiClient\.post<EnrollmentMutationResponse>\(\s*apiRoutes\.registration\.enrollments/);
  assert.match(client, /return normalizeRegistrationMutation\(/);
  assert.match(client, /requestBytes\(path: string/);
});

test('mobile keeps retry identity and distinguishes cache reads from disabled mutations', () => {
  const client = read('src/api/client.ts');
  const screen = read('src/screens/student/StudentScreens.tsx');
  assert.match(client, /export const createIdempotencyKey = createAssistantClientRequestId/);
  assert.match(client, /registrationSnapshot/);
  assert.match(client, /NETWORK_OFFLINE/);
  assert.match(client, /SESSION_EXPIRED/);
  assert.match(client, /FORBIDDEN/);
  assert.match(screen, /retryKeys/);
  assert.match(screen, /nextError\.status === 0 \|\| nextError\.retryable/);
  assert.match(screen, /Offline read-only mode/);
  assert.match(screen, /disabled until you reconnect/);
  assert.match(screen, /if \(isPreview\) \{ setPreviewSelected/);
});

test('registration renders all schedule slots, eligibility violations and slip checksum', () => {
  const screen = read('src/screens/student/StudentScreens.tsx');
  assert.match(screen, /section\.schedules\?\.map/);
  assert.match(screen, /section\.violations\?\.length/);
  assert.match(screen, /registrationSlip\(round\.id\)/);
  assert.match(screen, /X-Registration-Slip-Hash/);
  assert.match(screen, /Share\.share/);
});
