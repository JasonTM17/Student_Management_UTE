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
// never change session settings underneath the scripts' pinned SET LOCAL,
// and runs with PGCLIENTENCODING=UTF8 so a shell with a different client
// encoding cannot silently transcode the UTF-8 payload into mojibake (the
// scripts additionally pin and verify the session encoding themselves).
//
// Locking policy: the mutating scripts (apply/dry-run/rollback/recover)
// acquire every table lock with NOWAIT and take the row locks their writes
// need up front with FOR UPDATE NOWAIT — the package never queues on a lock,
// so it can never be one edge of a wait-for cycle: no deadlock can form
// around it and the package is always the transaction that loses, instantly.
// When a run fails that way (55P03 lock_not_available or a lock_timeout
// error), this runner retries the whole script up to THESIS_DEMO_MAX_ATTEMPTS
// times (default 8) with jitter between attempts, reporting who is holding
// the conflicting locks — real autovacuum/ANALYZE workers vs other sessions.
// Each retry is a FRESH transaction — psql is a new process — so no lock is
// ever held while the runner waits. The application can still lose only if
// writes are left running against the window: pause them for the run.

import { spawnSync } from 'node:child_process';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const action = process.argv[2];
const psql = process.env.PSQL_BIN || 'psql';
const MAX_ATTEMPTS = Math.max(1, parseInt(process.env.THESIS_DEMO_MAX_ATTEMPTS || '8', 10) || 8);
// the package data is UTF-8; every psql child is pinned, never inherited.
const PSQL_ENV = { ...process.env, PGCLIENTENCODING: 'UTF8' };

const FILES = {
  'dry-run': { file: 'apply.sql', vars: { dry_run: '1' }, retryable: true },
  'apply':   { file: 'apply.sql', vars: { dry_run: '0' }, retryable: true },
  'verify':  { file: 'verify.sql', vars: {}, retryable: false },
  'rollback':{ file: 'rollback.sql', vars: {}, retryable: true },
  'manifest':{ file: 'manifest.sql', vars: {}, retryable: false },
  'recover': { file: 'recover-before-image.sql', vars: {}, retryable: true },
};

// lock-window failures the runner may retry; anything else fails once.
const RETRYABLE = /could not lock|could not obtain lock|lock_not_available|55P03|deadlock detected|40P01|canceling statement due to lock timeout|table is locked|a row the package must write is locked/i;

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

// counts-only diagnosis of who is holding the conflicting side of a failed
// lock attempt. The first count is REAL autovacuum/ANALYZE workers only —
// proven by a lock (granted or pending) on one of the eleven package tables;
// the permanent autovacuum launcher never holds one, so it can never be
// counted. The second count is other sessions with a granted write-class or
// conflicting lock on those tables (application writes included). Never
// prints query text, usernames or row data.
function lockBlockerCounts() {
  const sql = `SELECT
  coalesce((SELECT count(DISTINCT a.pid) FROM pg_locks l
     JOIN pg_class c ON c.oid = l.relation
     JOIN pg_namespace n ON n.oid = c.relnamespace
     JOIN pg_stat_activity a ON a.pid = l.pid
     WHERE (a.backend_type = 'autovacuum worker'
            OR (a.query ~* '\\m(vacuum|analyze)\\M' AND a.pid <> pg_backend_pid()))
       AND n.nspname || '.' || c.relname IN
         ('thesis.thesis_council','thesis.thesis_council_member','thesis.thesis_council_topic',
          'thesis.thesis_group','thesis.thesis_group_member','thesis.thesis_group_report',
          'thesis.thesis_registration_round','thesis.thesis_topic','thesis.thesis_topic_score',
          'thesis.thesis_topic_supervisor','notifications.notification','campuscore_audit.AdminAudit')), 0)
  || '|' ||
  coalesce((SELECT count(DISTINCT l.pid) FROM pg_locks l
     JOIN pg_class c ON c.oid = l.relation
     JOIN pg_namespace n ON n.oid = c.relnamespace
     WHERE l.granted AND l.pid <> pg_backend_pid()
       AND l.mode IN ('RowExclusiveLock','ShareUpdateExclusiveLock','ShareLock',
                      'ShareRowExclusiveLock','ExclusiveLock','AccessExclusiveLock')
       AND n.nspname || '.' || c.relname IN
         ('thesis.thesis_council','thesis.thesis_council_member','thesis.thesis_council_topic',
          'thesis.thesis_group','thesis.thesis_group_member','thesis.thesis_group_report',
          'thesis.thesis_registration_round','thesis.thesis_topic','thesis.thesis_topic_score',
          'thesis.thesis_topic_supervisor','notifications.notification','campuscore_audit.AdminAudit')), 0)`;
  const res = spawnSync(psql, [...connectionArgs(), '-X', '--no-psqlrc', '-A', '-t', '-c', sql],
    { encoding: 'utf8', env: PSQL_ENV, windowsHide: true });
  const m = (res.stdout || '').trim().match(/^(\d+)\|(\d+)$/);
  return m ? { av: parseInt(m[1], 10), holders: parseInt(m[2], 10) } : null;
}

function runPsql(file, vars, retryable) {
  const base = [...connectionArgs(), '-X', '--no-psqlrc', '-v', 'ON_ERROR_STOP=1', '-P', 'pager=off'];
  for (const [k, v] of Object.entries(vars)) base.push('-v', `${k}=${v}`);
  base.push('-f', join(here, file));
  const attempts = retryable ? MAX_ATTEMPTS : 1;
  for (let attempt = 1; attempt <= attempts; attempt++) {
    const res = spawnSync(psql, base, { encoding: 'utf8', env: PSQL_ENV, windowsHide: true });
    if (res.stdout) process.stdout.write(res.stdout);
    if (res.stderr) process.stderr.write(res.stderr);
    if (res.error) {
      console.error(`error: could not run ${psql}: ${res.error.message}`);
      process.exit(2);
    }
    const status = res.status ?? 1;
    const out = `${res.stdout || ''}\n${res.stderr || ''}`;
    const retry = retryable && status !== 0 && attempt < attempts && RETRYABLE.test(out);
    if (!retry) {
      if (retryable && status !== 0 && attempt >= attempts && RETRYABLE.test(out)) {
        console.error(`run.mjs: all ${attempts} attempts lost the lock window — before retrying, check pg_stat_activity and pg_locks for autovacuum workers (or other sessions) holding the package tables`);
      }
      return status;
    }
    const wait = 250 + Math.floor(Math.random() * 500);
    let reason = 'lost the lock window';
    const b = lockBlockerCounts();
    if (b && b.av > 0) reason = `blocked by autovacuum/analyze worker(s) (counts: ${b.av})`;
    else if (b && b.holders > 0) reason = `blocked by another session (counts: ${b.holders})`;
    console.error(`run.mjs: attempt ${attempt}/${attempts} ${reason}, retrying in ${wait}ms (fresh transaction, no locks held)`);
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
  const res = spawnSync(psql, [...connectionArgs(), '-X', '--no-psqlrc', '-P', 'pager=off', '-c', sql], { stdio: 'inherit', env: PSQL_ENV, windowsHide: true });
  process.exit(res.status ?? 1);
}

const spec = FILES[action];
if (!spec) {
  console.error('usage: node run.mjs <dry-run|apply|verify|rollback|manifest|recover|counts>');
  process.exit(2);
}
process.exit(runPsql(spec.file, spec.vars, spec.retryable));
