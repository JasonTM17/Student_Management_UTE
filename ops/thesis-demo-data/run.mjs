#!/usr/bin/env node
// Thesis demo-data package runner (Windows-safe, no shell required).
//
//   node run.mjs <dry-run|apply|verify|rollback|manifest|recover|counts>
//
// Connection comes from the environment only — never from repo files:
//   THESIS_DEMO_DB_URL  full postgres URL, OR
//   PGHOST PGPORT PGDATABASE PGUSER PGPASSWORD  (standard libpq vars)
// psql is resolved from PSQL_BIN env var or PATH.
// Prints row counts only; no credentials or personal data are ever echoed.
//
// Every psql invocation uses -X + --no-psqlrc so a user startup file can
// never change session settings underneath the scripts' pinned SET LOCAL.
//
// Locking policy: the mutating scripts (apply/dry-run/rollback/recover) run
// under lock_timeout=300ms — deliberately below the default deadlock_timeout
// (1s) — so the package is ALWAYS the victim of any lock-ordering conflict
// and aborts cleanly instead of letting an application transaction lose a
// deadlock. When a run dies that way (55P03 lock_not_available, or a
// lock_timeout error), this runner retries the whole script up to
// THESIS_DEMO_MAX_ATTEMPTS times (default 8) with jitter between attempts.
// Each retry is a FRESH transaction — psql is a new process — so no lock is
// ever held while the runner waits.

import { spawnSync } from 'node:child_process';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const action = process.argv[2];
const psql = process.env.PSQL_BIN || 'psql';
const MAX_ATTEMPTS = Math.max(1, parseInt(process.env.THESIS_DEMO_MAX_ATTEMPTS || '8', 10) || 8);

const FILES = {
  'dry-run': { file: 'apply.sql', vars: { dry_run: '1' }, retryable: true },
  'apply':   { file: 'apply.sql', vars: { dry_run: '0' }, retryable: true },
  'verify':  { file: 'verify.sql', vars: {}, retryable: false },
  'rollback':{ file: 'rollback.sql', vars: {}, retryable: true },
  'manifest':{ file: 'manifest.sql', vars: {}, retryable: false },
  'recover': { file: 'recover-before-image.sql', vars: {}, retryable: true },
};

// lock-window failures the runner may retry; anything else fails once.
const RETRYABLE = /could not lock|lock_not_available|55P03|deadlock detected|40P01|canceling statement due to lock timeout/i;

function connectionArgs() {
  if (process.env.THESIS_DEMO_DB_URL) return [process.env.THESIS_DEMO_DB_URL];
  const { PGHOST, PGPORT, PGDATABASE, PGUSER } = process.env;
  if (!PGDATABASE) {
    console.error('error: set THESIS_DEMO_DB_URL or PGHOST/PGDATABASE/PGUSER/PGPASSWORD');
    process.exit(2);
  }
  const args = [];
  if (PGHOST) args.push('-h', PGHOST);
  if (PGPORT) args.push('-p', PGPORT);
  if (PGUSER) args.push('-U', PGUSER);
  args.push('-d', PGDATABASE);
  return args;
}

function sleepMs(ms) {
  Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0, ms);
}

function runPsql(file, vars, retryable) {
  const base = [...connectionArgs(), '-X', '--no-psqlrc', '-v', 'ON_ERROR_STOP=1', '-P', 'pager=off'];
  for (const [k, v] of Object.entries(vars)) base.push('-v', `${k}=${v}`);
  base.push('-f', join(here, file));
  const attempts = retryable ? MAX_ATTEMPTS : 1;
  for (let attempt = 1; attempt <= attempts; attempt++) {
    const res = spawnSync(psql, base, { encoding: 'utf8', windowsHide: true });
    if (res.stdout) process.stdout.write(res.stdout);
    if (res.stderr) process.stderr.write(res.stderr);
    if (res.error) {
      console.error(`error: could not run ${psql}: ${res.error.message}`);
      process.exit(2);
    }
    const status = res.status ?? 1;
    const retry = retryable && status !== 0 && attempt < attempts
      && RETRYABLE.test(`${res.stdout || ''}\n${res.stderr || ''}`);
    if (!retry) return status;
    const wait = 250 + Math.floor(Math.random() * 500);
    console.error(`run.mjs: attempt ${attempt}/${attempts} lost the lock window — retrying in ${wait}ms (fresh transaction, no locks held)`);
    sleepMs(wait);
  }
  return 1;
}

if (action === 'counts') {
  const sql = `SELECT 'topics' AS k, count(*) AS n FROM thesis.thesis_topic WHERE id::text LIKE 'ed1a0000-0000-4000-8000-%'
UNION ALL SELECT 'groups', count(*) FROM thesis.thesis_group WHERE id::text LIKE 'ed1a0000-0000-4000-8000-%'
UNION ALL SELECT 'members', count(*) FROM thesis.thesis_group_member WHERE id::text LIKE 'ed1a0000-0000-4000-8000-%'
UNION ALL SELECT 'councils', count(*) FROM thesis.thesis_council WHERE id::text LIKE 'ed1a0000-0000-4000-8000-%'
UNION ALL SELECT 'scores', count(*) FROM thesis.thesis_topic_score WHERE id::text LIKE 'ed1a0000-0000-4000-8000-%'
UNION ALL SELECT 'reports', count(*) FROM thesis.thesis_group_report WHERE id::text LIKE 'ed1a0000-0000-4000-8000-%'
UNION ALL SELECT 'notifications', count(*) FROM notifications.notification WHERE id LIKE 'demo-th-%'
UNION ALL SELECT 'audit_rows', count(*) FROM campuscore_audit."AdminAudit" WHERE id LIKE 'demo-th-%'
ORDER BY 1;`;
  const res = spawnSync(psql, [...connectionArgs(), '-X', '--no-psqlrc', '-P', 'pager=off', '-c', sql], { stdio: 'inherit', windowsHide: true });
  process.exit(res.status ?? 1);
}

const spec = FILES[action];
if (!spec) {
  console.error('usage: node run.mjs <dry-run|apply|verify|rollback|manifest|recover|counts>');
  process.exit(2);
}
process.exit(runPsql(spec.file, spec.vars, spec.retryable));
