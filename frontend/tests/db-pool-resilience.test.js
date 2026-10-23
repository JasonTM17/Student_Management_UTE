const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..', '..');
const applicationYml = fs.readFileSync(
  path.join(root, 'java-services/restful-api/src/main/resources/application.yml'),
  'utf8',
);

function defaultFor(yamlKey) {
  const match = applicationYml.match(new RegExp(`${yamlKey}:\\s*\\$\\{[^:}]+:(\\d+)`));
  assert.ok(match, `${yamlKey} must carry a numeric default`);
  return Number(match[1]);
}

test('primary pool retires idle connections inside the Neon autosuspend window', () => {
  const idleTimeoutMs = defaultFor('idle-timeout');
  const keepaliveMs = defaultFor('keepalive-time');

  // Neon suspends a free-tier compute after ~5 minutes without query
  // activity; every probe or borrow counts as activity. The recorded owner
  // decision keeps the database asleep between requests (~180 CU-h/month
  // continuously awake vs a 100 CU-h free allowance), so the pool must
  // retire its idle connections comfortably inside that window — leaving
  // ~30s of borrow-and-validate margin — and must not ping them while
  // pooled. If keepalive is ever enabled it must fire before idle-timeout
  // retires the connection, or it is dead weight.
  console.info(
    `note: idle-timeout ${idleTimeoutMs}ms + ~30s borrow margin vs Neon autosuspend ~300s; ` +
      `keepalive ${keepaliveMs}ms ${keepaliveMs === 0 ? '(disabled, DB may sleep)' : '(enabled)'}`,
  );
  assert.ok(
    idleTimeoutMs + 30_000 < 300_000,
    `idle-timeout ${idleTimeoutMs}ms + 30s borrow margin must stay below the ~300s Neon autosuspend window`,
  );
  assert.ok(
    keepaliveMs === 0 || keepaliveMs < idleTimeoutMs,
    `keepalive ${keepaliveMs}ms must be 0 (disabled) or below the ${idleTimeoutMs}ms idle retirement`,
  );
});
