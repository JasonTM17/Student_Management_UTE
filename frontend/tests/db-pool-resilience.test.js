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

test('pool keepalive stays disabled or probes before idle retirement', () => {
  const idleTimeoutMs = defaultFor('idle-timeout');
  const keepaliveMs = defaultFor('keepalive-time');

  // Neon suspends a free-tier compute after ~5 minutes without query
  // activity, and every keepalive probe counts as activity. The deployment
  // budget therefore prefers keepalive-time 0 (disabled) so the database may
  // sleep between requests: Hikari's borrow-time validation already covers
  // the odd connection the proxy drops while pooled. If keepalive is ever
  // enabled it must fire before idle-timeout retires the connection, or it
  // is dead weight. The idle-timeout value is a separate decision: open but
  // idle sockets do not count as query activity, so suspension still happens
  // once the pool goes quiet — at 60s the pool drains early, at 300s it holds
  // sockets until roughly the same moment the compute suspends
  // (idle-timeout + ~30s borrow margin vs the 300s Neon window).
  console.info(
    `note: idle-timeout ${idleTimeoutMs}ms + ~30s vs Neon autosuspend ~300s; ` +
      `keepalive ${keepaliveMs}ms ${keepaliveMs === 0 ? '(disabled, DB may sleep)' : '(enabled)'}`,
  );
  assert.ok(
    keepaliveMs === 0 || keepaliveMs < idleTimeoutMs,
    `keepalive ${keepaliveMs}ms must be 0 (disabled) or below the ${idleTimeoutMs}ms idle retirement`,
  );
});
