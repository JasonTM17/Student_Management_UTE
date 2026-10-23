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

test('pooled connections retained past a minute must be keepalive-verified', () => {
  const idleTimeoutMs = defaultFor('idle-timeout');
  const keepaliveMs = defaultFor('keepalive-time');
  // The pool holds idle connections for up to idle-timeout. Hosted Postgres
  // front-ends (Neon/Supabase proxies, Render NAT) silently drop idle TCP
  // sessions well inside that window, and Hikari only discovers the corpse on
  // borrow — a multi-second stall masked as "pool latency". keepaliveTime
  // exists exactly to defeat that; it must be enabled, at least Hikari's 30s
  // floor, and short enough to probe each connection before it retires.
  assert.ok(
    keepaliveMs >= 30_000,
    `keepalive ${keepaliveMs}ms is below Hikari's 30s minimum (or disabled at 0)`,
  );
  assert.ok(
    keepaliveMs < idleTimeoutMs,
    `keepalive ${keepaliveMs}ms must fire before the ${idleTimeoutMs}ms idle retirement`,
  );
});
