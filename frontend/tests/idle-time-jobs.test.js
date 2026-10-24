const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { readJavaSource } = require('./helpers/java-source');

const root = path.resolve(__dirname, '..', '..');

function readSource(rel) {
  return fs.readFileSync(path.join(root, rel), 'utf8');
}

const applicationYml = readSource('java-services/restful-api/src/main/resources/application.yml');
const renderYaml = readSource('render.yaml');
const keepAliveScheduler = readJavaSource('DatabaseKeepAliveScheduler');
const announcementJob = readJavaSource('ScheduledAnnouncementNotifyJob');

// Every scheduled tick that issues SQL while the app is otherwise idle
// counts as query activity on the hosted free-tier Postgres and delays or
// defeats its ~5-minute autosuspend. These assertions pin the defaults that
// keep the database asleep between requests; an operator can still trade
// idle savings for latency by overriding the env vars on demo day.

test('database keep-alive probe defaults to disabled', () => {
  assert.match(
    keepAliveScheduler,
    /matchIfMissing\s*=\s*false/,
    'DatabaseKeepAliveScheduler must not start unless explicitly enabled',
  );
  assert.match(
    applicationYml,
    /keepalive:\s*\n(?:\s+#.*\n)*\s+enabled:\s*\$\{APP_KEEPALIVE_ENABLED:false\}/,
    'application.yml must default app.keepalive.enabled to false',
  );
  assert.match(
    renderYaml,
    /key:\s*APP_KEEPALIVE_ENABLED\s*\n\s*value:\s*"false"/,
    'render.yaml must pin APP_KEEPALIVE_ENABLED=false for production',
  );
});

test('scheduled-announcement refire cadence clears the autosuspend window', () => {
  const match = announcementJob.match(
    /engagement\.announcement-notify\.delay-ms:(\d+)\}/,
  );
  assert.ok(match, 'announcement-notify delay-ms must carry a numeric default');
  const delayMs = Number(match[1]);
  console.info(
    `note: announcement refire every ${delayMs}ms; scheduled announcements may notify up to one interval late`,
  );
  assert.ok(
    delayMs >= 300_000,
    `announcement refire delay ${delayMs}ms must be at least the ~300s Neon autosuspend window`,
  );
});
