#!/usr/bin/env node
// Thesis demo-data package runner (Windows-safe, no shell required).
//
//   node run.mjs <dry-run|apply|verify|rollback|counts>
//
// Connection comes from the environment only — never from repo files:
//   THESIS_DEMO_DB_URL  full postgres URL, OR
//   PGHOST PGPORT PGDATABASE PGUSER PGPASSWORD  (standard libpq vars)
// psql is resolved from PSQL_BIN env var or PATH.
// Prints row counts only; no credentials or personal data are ever echoed.

import { spawnSync } from 'node:child_process';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const action = process.argv[2];
const psql = process.env.PSQL_BIN || 'psql';

const FILES = {
  'dry-run': { file: 'apply.sql', vars: { dry_run: '1' } },
  'apply':   { file: 'apply.sql', vars: { dry_run: '0' } },
  'verify':  { file: 'verify.sql', vars: {} },
  'rollback':{ file: 'rollback.sql', vars: {} },
  'manifest':{ file: 'manifest.sql', vars: {} },
};

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

function runPsql(file, vars) {
  const args = [...connectionArgs(), '-v', 'ON_ERROR_STOP=1', '-P', 'pager=off'];
  for (const [k, v] of Object.entries(vars)) args.push('-v', `${k}=${v}`);
  args.push('-f', join(here, file));
  const res = spawnSync(psql, args, { stdio: 'inherit', windowsHide: true });
  if (res.error) {
    console.error(`error: could not run ${psql}: ${res.error.message}`);
    process.exit(2);
  }
  return res.status ?? 1;
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
  const res = spawnSync(psql, [...connectionArgs(), '-P', 'pager=off', '-c', sql], { stdio: 'inherit', windowsHide: true });
  process.exit(res.status ?? 1);
}

const spec = FILES[action];
if (!spec) {
  console.error('usage: node run.mjs <dry-run|apply|verify|rollback|manifest|counts>');
  process.exit(2);
}
process.exit(runPsql(spec.file, spec.vars));
