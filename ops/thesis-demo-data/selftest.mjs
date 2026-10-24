#!/usr/bin/env node
// Thesis demo-data self-test: rerunnable regression suite for the safety
// properties of apply.sql / rollback.sql / recover-before-image.sql.
//
//   node selftest.mjs
//
// Connection comes from the environment only (same variables as run.mjs:
// THESIS_DEMO_DB_URL or PGHOST/PGPORT/PGDATABASE/PGUSER/PGPASSWORD, PSQL_BIN).
// Point it at a DISPOSABLE scratch database restored from the production
// fixture — never at a live database: the suite writes marker rows, simulates
// application writes, deletes audit rows and runs several apply/rollback
// cycles. Prints one PASS/FAIL line per check plus counts; exits non-zero on
// any FAIL. Requires the database to start at baseline (no marker rows).

import { spawn, spawnSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { once } from 'node:events';
import { setTimeout as delay } from 'node:timers/promises';

const here = dirname(fileURLToPath(import.meta.url));
const psql = process.env.PSQL_BIN || 'psql';
const results = [];

function connArgs() {
  if (process.env.THESIS_DEMO_DB_URL) return [process.env.THESIS_DEMO_DB_URL];
  const { PGHOST, PGPORT, PGDATABASE, PGUSER } = process.env;
  if (!PGDATABASE) { console.error('set THESIS_DEMO_DB_URL or PG* vars'); process.exit(2); }
  const a = [];
  if (PGHOST) a.push('-h', PGHOST);
  if (PGPORT) a.push('-p', PGPORT);
  if (PGUSER) a.push('-U', PGUSER);
  a.push('-d', PGDATABASE);
  return a;
}

function sql(script, env = {}) {
  const stmts = Array.isArray(script) ? script : [script];
  const args = [...connArgs(), '-X', '-v', 'ON_ERROR_STOP=1', '-P', 'pager=off', '-A', '-t'];
  for (const s of stmts) args.push('-c', s);
  const r = spawnSync(psql, args,
    { encoding: 'utf8', env: { ...process.env, ...env }, windowsHide: true });
  const res = { status: r.status ?? 1, out: r.stdout || '', err: r.stderr || '' };
  if (res.err.trim()) {
    lastErr = res.err.split('\n').filter(l => /ERROR|FATAL|EXCEPTION/i.test(l)).slice(-2).join(' ~ ');
    if (res.status !== 0) console.log(`  !! sql exit=${res.status} :: ${lastErr}`);
  }
  return res;
}
let lastErr = '';
function runFile(file, vars = {}, env = {}) {
  const args = [...connArgs(), '-X', '-v', 'ON_ERROR_STOP=1', '-P', 'pager=off'];
  for (const [k, v] of Object.entries(vars)) args.push('-v', `${k}=${v}`);
  args.push('-f', join(here, file));
  const r = spawnSync(psql, args, { encoding: 'utf8', env: { ...process.env, ...env }, windowsHide: true });
  const res = { status: r.status ?? 1, out: r.stdout || '', err: r.stderr || '' };
  lastErr = (res.err + '\n' + res.out).split('\n').filter(l => /ERROR|EXCEPTION|guard|refus|warn/i.test(l)).slice(-2).join(' ~ ');
  if (res.status !== 0) console.log(`  !! ${file} exit=${res.status} :: ${lastErr || res.err.trim().split('\n')[0] || 'no-error-text'}`);
  return res;
}
// maintenance-db connection for CREATE/DROP DATABASE (the SQL_ASCII check
// builds a scratch clone with a flipped server_encoding).
function adminSql(script) {
  const { PGHOST, PGPORT, PGUSER } = process.env;
  const a = [];
  if (PGHOST) a.push('-h', PGHOST);
  if (PGPORT) a.push('-p', PGPORT);
  if (PGUSER) a.push('-U', PGUSER);
  a.push('-d', 'postgres');
  const r = spawnSync(psql, [...a, '-X', '-v', 'ON_ERROR_STOP=1', '-A', '-t', '-c', script],
    { encoding: 'utf8', env: { ...process.env, PGCLIENTENCODING: 'UTF8' }, windowsHide: true });
  return { status: r.status ?? 1, out: r.stdout || '', err: r.stderr || '' };
}
// runFile variant that runs the script on a DIFFERENT database (for the
// SQL_ASCII clone) — same -v/-f mechanics as runFile.
function runFileOn(db, file, vars = {}) {
  const { PGHOST, PGPORT, PGUSER } = process.env;
  const a = [];
  if (PGHOST) a.push('-h', PGHOST);
  if (PGPORT) a.push('-p', PGPORT);
  if (PGUSER) a.push('-U', PGUSER);
  a.push('-d', db);
  const args = [...a, '-X', '-v', 'ON_ERROR_STOP=1', '-P', 'pager=off'];
  for (const [k, v] of Object.entries(vars)) args.push('-v', `${k}=${v}`);
  args.push('-f', join(here, file));
  const r = spawnSync(psql, args, { encoding: 'utf8', env: { ...process.env, PGCLIENTENCODING: 'UTF8' }, windowsHide: true });
  return { status: r.status ?? 1, out: r.stdout || '', err: r.stderr || '' };
}
function record(name, ok, detail = '') {
  results.push([name, ok]);
  console.log(`${ok ? 'PASS' : 'FAIL'} ${name}${detail ? ' — ' + detail : ''}${!ok && lastErr ? '  <<' + lastErr + '>>' : ''}`);
  if (!ok && process.env.SELFTEST_STOP_ON_FAIL) {
    console.log('---'); console.log('stopped at first FAIL (SELFTEST_STOP_ON_FAIL)');
    process.exit(1);
  }
}

const HASH_SQL = [`SET TimeZone='UTC'`, `SET DateStyle='ISO,MDY'`, `SET bytea_output='hex'`, `SET extra_float_digits=0`,
`SELECT 'audit', md5(coalesce(string_agg(t.h, '' ORDER BY t.h), '')) FROM (SELECT md5(t::text) h FROM campuscore_audit."AdminAudit" t) t
UNION ALL SELECT 'notification', md5(coalesce(string_agg(t.h,'' ORDER BY t.h),'')) FROM (SELECT md5(t::text) h FROM notifications.notification t) t
UNION ALL SELECT 'thesis_council', md5(coalesce(string_agg(t.h,'' ORDER BY t.h),'')) FROM (SELECT md5(t::text) h FROM thesis.thesis_council t) t
UNION ALL SELECT 'thesis_council_member', md5(coalesce(string_agg(t.h,'' ORDER BY t.h),'')) FROM (SELECT md5(t::text) h FROM thesis.thesis_council_member t) t
UNION ALL SELECT 'thesis_council_topic', md5(coalesce(string_agg(t.h,'' ORDER BY t.h),'')) FROM (SELECT md5(t::text) h FROM thesis.thesis_council_topic t) t
UNION ALL SELECT 'thesis_group', md5(coalesce(string_agg(t.h,'' ORDER BY t.h),'')) FROM (SELECT md5(t::text) h FROM thesis.thesis_group t) t
UNION ALL SELECT 'thesis_group_member', md5(coalesce(string_agg(t.h,'' ORDER BY t.h),'')) FROM (SELECT md5(t::text) h FROM thesis.thesis_group_member t) t
UNION ALL SELECT 'thesis_group_report', md5(coalesce(string_agg(t.h,'' ORDER BY t.h),'')) FROM (SELECT md5(t::text) h FROM thesis.thesis_group_report t) t
UNION ALL SELECT 'thesis_registration_round', md5(coalesce(string_agg(t.h,'' ORDER BY t.h),'')) FROM (SELECT md5(t::text) h FROM thesis.thesis_registration_round t) t
UNION ALL SELECT 'thesis_topic', md5(coalesce(string_agg(t.h,'' ORDER BY t.h),'')) FROM (SELECT md5(t::text) h FROM thesis.thesis_topic t) t
UNION ALL SELECT 'thesis_topic_score', md5(coalesce(string_agg(t.h,'' ORDER BY t.h),'')) FROM (SELECT md5(t::text) h FROM thesis.thesis_topic_score t) t
UNION ALL SELECT 'thesis_topic_supervisor', md5(coalesce(string_agg(t.h,'' ORDER BY t.h),'')) FROM (SELECT md5(t::text) h FROM thesis.thesis_topic_supervisor t) t
ORDER BY 1;`];
const tableHashes = () => Object.fromEntries(
  sql(HASH_SQL).out.split('\n').filter(l => l.includes('|'))
    .map(l => l.split('|').slice(0, 2).map(s => s.trim())));
const sameHashes = (a, b) => Object.keys(a).length === Object.keys(b).length && Object.keys(a).every(k => a[k] === b[k]);

const MARKER_TOTAL = `SELECT
 (SELECT count(*) FROM thesis.thesis_topic WHERE id::text LIKE 'ed1a0000-0000-4000-8000-%') +
 (SELECT count(*) FROM thesis.thesis_topic_supervisor WHERE id::text LIKE 'ed1a0000-0000-4000-8000-%') +
 (SELECT count(*) FROM thesis.thesis_group WHERE id::text LIKE 'ed1a0000-0000-4000-8000-%') +
 (SELECT count(*) FROM thesis.thesis_group_member WHERE id::text LIKE 'ed1a0000-0000-4000-8000-%') +
 (SELECT count(*) FROM thesis.thesis_council WHERE id::text LIKE 'ed1a0000-0000-4000-8000-%') +
 (SELECT count(*) FROM thesis.thesis_council_member WHERE id::text LIKE 'ed1a0000-0000-4000-8000-%') +
 (SELECT count(*) FROM thesis.thesis_council_topic WHERE id::text LIKE 'ed1a0000-0000-4000-8000-%') +
 (SELECT count(*) FROM thesis.thesis_topic_score WHERE id::text LIKE 'ed1a0000-0000-4000-8000-%') +
 (SELECT count(*) FROM thesis.thesis_group_report WHERE id::text LIKE 'ed1a0000-0000-4000-8000-%') +
 (SELECT count(*) FROM notifications.notification WHERE id LIKE 'demo-th-%') +
 (SELECT count(*) FROM campuscore_audit."AdminAudit" WHERE id LIKE 'demo-th-%')`;
const markerTotal = () => parseInt(sql(MARKER_TOTAL).out.trim() || '-1', 10);

const MARKER_EXPECTED = 347; // 17+18+20+64+4+16+16+62+17 marker rows + 107 notifications + 6 audit
const GUARDED_ROWS = [`SET TimeZone='UTC'`, `SET DateStyle='ISO,MDY'`,
`SELECT md5(string_agg(t::text, '' ORDER BY t::text)) FROM (
  SELECT id, version, updated_at, results_published_at, status FROM thesis.thesis_registration_round
   WHERE id IN ('b35e9ff1-1eb8-4e73-812e-04e1a66041f1','be1aa853-f153-4f2e-a5cb-ed1b6c3c1bf1','bad8eba0-2d12-4414-8aab-7424174b3b97')) t;`];
const TOPIC_ROWS = [`SET TimeZone='UTC'`, `SET DateStyle='ISO,MDY'`,
`SELECT md5(string_agg(t::text, '' ORDER BY t::text)) FROM (
  SELECT id, version, updated_at, gvpb_lecturer_id, gvpb_waived_reason, gvpb_waived_by, gvpb_waived_at
  FROM thesis.thesis_topic WHERE id IN ('018f4a7c-3b12-4d90-a5e1-7c82b9045001','6f64670b-cd6b-4e1b-ae9d-0b497f2472a8',
    'f580eadf-fffb-4db2-a7d8-423e83934dc2','67c74df5-5c7f-4e74-aa02-d51af7207fe9')) t;`];

async function main() {
  // ---- precondition: baseline state ----
  const m0 = markerTotal();
  record('precondition.no_markers_at_start', m0 === 0, `markers=${m0}`);
  if (m0 !== 0) return finish();

  const base = tableHashes();
  record('baseline.hashes_captured', Object.keys(base).length === 12, `${Object.keys(base).length} tables`);

  // ---- dry-run leaves zero residue ----
  let r = runFile('apply.sql', { dry_run: '1' });
  record('dry_run.exit0', r.status === 0, `exit=${r.status}`);
  record('dry_run.zero_residue', sameHashes(tableHashes(), base));

  // ---- non-UTF-8 SERVER must abort apply (mutant ap.encoding_abort_removed):
  // a scratch clone with pg_database.encoding flipped to SQL_ASCII — the
  // script's own client_encoding pin cannot change server_encoding, so only
  // the encoding guard stands between UTF-8 literals and garbled storage. ----
  const selfDb = process.env.PGDATABASE
    || (() => { try { return decodeURIComponent(new URL(process.env.THESIS_DEMO_DB_URL).pathname.split('/').pop()); } catch { return ''; } })();
  const saDb = `${selfDb}_sa`;
  adminSql(`DROP DATABASE IF EXISTS ${saDb}`);
  const mkSa = selfDb ? adminSql(`CREATE DATABASE ${saDb} TEMPLATE ${selfDb}`) : { status: -1 };
  adminSql(`UPDATE pg_database SET encoding = 0 WHERE datname = '${saDb}'`);
  const saEnc = spawnSync(psql, [...(process.env.PGHOST ? ['-h', process.env.PGHOST] : []),
    ...(process.env.PGUSER ? ['-U', process.env.PGUSER] : []), '-d', saDb,
    '-X', '-A', '-t', '-c', 'SHOW server_encoding'],
    { encoding: 'utf8', env: { ...process.env, PGCLIENTENCODING: 'UTF8' }, windowsHide: true });
  const saEncoding = (saEnc.stdout || '').trim();
  const saRes = mkSa.status === 0 && saEncoding === 'SQL_ASCII'
    ? runFileOn(saDb, 'apply.sql', { dry_run: '0' })
    : { status: -1, out: '', err: 'setup' };
  adminSql(`DROP DATABASE IF EXISTS ${saDb}`);
  record('f3.sqlascii_apply_refused',
         saRes.status !== 0 && /encoding|UTF8/i.test(saRes.out + saRes.err),
         `exit=${saRes.status} server_encoding=${saEncoding || '?'}`);

  // ---- apply under hostile session formats (N5) ----
  r = runFile('apply.sql', { dry_run: '0' }, { PGOPTIONS: '-c DateStyle=German -c TimeZone=Asia/Tokyo' });
  record('apply.german_session.exit0', r.status === 0, `exit=${r.status}`);
  record('apply.markers_written', markerTotal() === MARKER_EXPECTED, `markers=${markerTotal()}`);

  // ---- verify: every check PASS ----
  r = runFile('verify.sql');
  const fails = (r.out.match(/FAIL/g) || []).length;
  const passes = (r.out.match(/PASS/g) || []).length;
  record('verify.no_fail', r.status === 0 && fails === 0, `PASS=${passes} FAIL=${fails} exit=${r.status}`);

  // ---- re-apply is a strict no-op (N3) ----
  const gBefore = sql(GUARDED_ROWS).out.trim() + '|' + sql(TOPIC_ROWS).out.trim();
  r = runFile('apply.sql', { dry_run: '0' });
  const gAfter = sql(GUARDED_ROWS).out.trim() + '|' + sql(TOPIC_ROWS).out.trim();
  record('reapply.no_op_message', r.out.includes('already applied'), `exit=${r.status}`);
  record('reapply.zero_writes', gBefore === gAfter);

  // ---- re-apply after app retract + un-waive must not restore either (N3) ----
  sql(`UPDATE thesis.thesis_registration_round SET status='REGISTRATION_CLOSED', results_published_at=NULL,
       updated_at=now(), version=version+1 WHERE id='b35e9ff1-1eb8-4e73-812e-04e1a66041f1';
       UPDATE thesis.thesis_topic SET gvpb_waived_reason=NULL, gvpb_waived_by=NULL, gvpb_waived_at=NULL,
       updated_at=now(), version=version+1 WHERE id='67c74df5-5c7f-4e74-aa02-d51af7207fe9';`);
  const gRetracted = sql(GUARDED_ROWS).out.trim() + '|' + sql(TOPIC_ROWS).out.trim();
  r = runFile('apply.sql', { dry_run: '0' });
  const gStill = sql(GUARDED_ROWS).out.trim() + '|' + sql(TOPIC_ROWS).out.trim();
  const stampGone = sql(`SELECT count(*) FROM thesis.thesis_registration_round
    WHERE id='b35e9ff1-1eb8-4e73-812e-04e1a66041f1' AND results_published_at IS NULL`).out.trim();
  const waiverGone = sql(`SELECT count(*) FROM thesis.thesis_topic
    WHERE id='67c74df5-5c7f-4e74-aa02-d51af7207fe9' AND gvpb_waived_reason IS NULL`).out.trim();
  record('reapply_after_retract.no_op', r.out.includes('already applied') && gRetracted === gStill,
         `stamp_null=${stampGone} waiver_null=${waiverGone} exit=${r.status}`);
  // un-drift: put the guarded columns back to their recorded applied values so
  // later phases start from the applied state. Values come FROM the before-image
  // itself (no literals — locale/encoding proof).
  const fix = sql(`DO $$
    DECLARE r record;
    BEGIN
      FOR r IN SELECT (e->>'tbl') AS tbl, (e->>'id') AS rid, (e->>'col') AS col, (e->>'applied') AS app,
                      format_type(a.atttypid, a.atttypmod) AS typ
               FROM campuscore_audit."AdminAudit" au,
                    jsonb_array_elements(au."beforeState"::jsonb->'cols') e
               JOIN pg_attribute a ON a.attrelid = ('thesis.' || (e->>'tbl'))::regclass AND a.attname = (e->>'col')
               WHERE au.id = 'demo-th-audit-000' LOOP
        EXECUTE format('UPDATE thesis.%I SET %I = $1::%s WHERE id::text = $2', r.tbl, r.col, r.typ)
          USING r.app, r.rid;
      END LOOP;
    END $$;
    UPDATE thesis.thesis_registration_round SET status='RESULTS_PUBLISHED' WHERE id='b35e9ff1-1eb8-4e73-812e-04e1a66041f1';`);
  record('retract.un_drift_restored_applied_state', fix.status === 0 &&
         sql(GUARDED_ROWS).out.trim() + '|' + sql(TOPIC_ROWS).out.trim() === gBefore, `exit=${fix.status}`);

  // ---- N1 race: app inserts a referencing row, sleeps, commits; rollback 1s in ----
  const appSql = `BEGIN;
    INSERT INTO thesis.thesis_topic_score (id, topic_id, council_id, lecturer_id, component, score, graded_at, created_at)
    VALUES (gen_random_uuid(), '6f64670b-cd6b-4e1b-ae9d-0b497f2472a8', 'ed1a0000-0000-4000-8000-000500000004',
            'lecturer-profile-002', 'DEFENSE', 9.00, now(), now());
    SELECT pg_sleep(3); COMMIT;`;
  const app = spawn(psql, [...connArgs(), '-X', '-v', 'ON_ERROR_STOP=1', '-P', 'pager=off', '-c', appSql],
    { env: process.env, windowsHide: true });
  const appDone = once(app, 'exit'); // register before any await — 'exit' is not replayed
  await delay(1000);
  const t0 = Date.now();
  r = runFile('rollback.sql');
  const raceMs = Date.now() - t0;
  const [appCode] = await appDone;
  const appRow = sql(`SELECT count(*) FROM thesis.thesis_topic_score WHERE council_id='ed1a0000-0000-4000-8000-000500000004'
                      AND id::text NOT LIKE 'ed1a0000-%'`).out.trim();
  record('race.rollback_aborted_or_refused', r.status !== 0 && /reference marker rows|aborted/.test(r.out + r.err),
         `rollback_exit=${r.status} waited=${raceMs}ms app_exit=${appCode}`);
  record('race.app_row_survives', appRow === '1', `nonmarker_scores_on_council04=${appRow}`);
  sql(`DELETE FROM thesis.thesis_topic_score WHERE council_id='ed1a0000-0000-4000-8000-000500000004'
       AND id::text NOT LIKE 'ed1a0000-%'`);
  record('race.markers_intact', markerTotal() === MARKER_EXPECTED, `markers=${markerTotal()}`);

  // ---- N1b: rollback first, app write arrives during the lock window ----
  const rb = spawn(psql, [...connArgs(), '-X', '-v', 'ON_ERROR_STOP=1', '-P', 'pager=off', '-f', join(here, 'rollback.sql')],
    { env: process.env, windowsHide: true });
  const rbDone = once(rb, 'exit');
  await delay(400);
  const app2Sql = `INSERT INTO thesis.thesis_topic_score (id, topic_id, council_id, lecturer_id, component, score, graded_at, created_at)
    VALUES (gen_random_uuid(), '6f64670b-cd6b-4e1b-ae9d-0b497f2472a8', 'ed1a0000-0000-4000-8000-000500000004',
            'lecturer-profile-002', 'DEFENSE', 9.00, now(), now())`;
  const r2 = sql(app2Sql);
  const [rbCode] = await rbDone;
  const appRow2 = sql(`SELECT count(*) FROM thesis.thesis_topic_score WHERE council_id='ed1a0000-0000-4000-8000-000500000004'`).out.trim();
  record('race2.app_insert_rejected', r2.status !== 0 && appRow2 === '0',
         `app_exit=${r2.status} rollback_exit=${rbCode} leftover=${appRow2}`);
  record('race2.rollback_committed', rbCode === 0 && markerTotal() === 0, `markers=${markerTotal()}`);

  // re-apply for the next phases
  r = runFile('apply.sql', { dry_run: '0' });
  record('reapply_for_phase2.exit0', r.status === 0 && markerTotal() === MARKER_EXPECTED, `exit=${r.status}`);

  // ---- N4a: volatile bookkeeping edit (updated_at) must NOT block rollback (E1) ----
  const editId = sql(`SELECT id::text FROM thesis.thesis_group_report WHERE id::text LIKE 'ed1a0000-%' ORDER BY id LIMIT 1`).out.trim();
  const topicId = sql(`SELECT id::text FROM thesis.thesis_topic WHERE id::text LIKE 'ed1a0000-%' ORDER BY id LIMIT 1`).out.trim();
  const v = sql(`UPDATE thesis.thesis_group_report SET updated_at = now() + interval '1 hour' WHERE id::text='${editId}';
       UPDATE thesis.thesis_topic SET version = version + 7, updated_at = now() + interval '1 hour' WHERE id::text='${topicId}'`);
  record('n4.volatile_update_executed', v.status === 0, `exit=${v.status}`);
  r = runFile('rollback.sql');
  record('n4.volatile_edit_tolerated', r.status === 0 && markerTotal() === 0,
         `exit=${r.status}`);

  // ---- N4b: content edit of a hashed column must still abort (fail-closed) ----
  r = runFile('apply.sql', { dry_run: '0' });
  const origName = sql(`SELECT file_name FROM thesis.thesis_group_report WHERE id::text='${editId}'`).out.trim();
  sql(`UPDATE thesis.thesis_group_report SET file_name='tampered-by-app.pdf' WHERE id::text='${editId}'`);
  r = runFile('rollback.sql');
  record('n4.content_edit_aborts', r.status !== 0 && /differ/.test(r.out + r.err) && markerTotal() === MARKER_EXPECTED,
         `exit=${r.status}`);
  sql(`UPDATE thesis.thesis_group_report SET file_name=$X$${origName}$X$ WHERE id::text='${editId}'`);
  r = runFile('rollback.sql');
  record('n4.reverted_edit_allows_rollback', r.status === 0 && markerTotal() === 0, `exit=${r.status}`);

  // ---- N2a: single-copy loss — comment copy carries the image ----
  r = runFile('apply.sql', { dry_run: '0' });
  sql(`DELETE FROM campuscore_audit."AdminAudit" WHERE id='demo-th-audit-000'`);
  r = runFile('rollback.sql');
  record('n2.comment_copy_rollback', r.status === 0 && markerTotal() === 0 && sameHashes(tableHashes(), base),
         `exit=${r.status} source=${/comment/.test(r.out) ? 'comment' : '?'}`);

  // ---- N2b: both copies gone — rollback refuses, recovery script unblocks ----
  r = runFile('apply.sql', { dry_run: '0' });
  sql(`DELETE FROM campuscore_audit."AdminAudit" WHERE id='demo-th-audit-000';
       COMMENT ON TABLE thesis.thesis_topic IS NULL;`);
  r = runFile('rollback.sql');
  record('n2.both_gone_aborts', r.status !== 0 && /before-image/.test(r.out + r.err) && markerTotal() === MARKER_EXPECTED - 1,
         `exit=${r.status} markers=${markerTotal()}`);
  r = runFile('recover-before-image.sql');
  record('n2.recover_script', r.status === 0, `exit=${r.status}`);
  r = runFile('rollback.sql');
  record('n2.post_recovery_rollback', r.status === 0 && markerTotal() === 0, `exit=${r.status}`);
  record('n2.baseline_restored_after_recovery', sameHashes(tableHashes(), base));

  // ---- N5: rollback under hostile DateStyle + timezone restores identically ----
  r = runFile('apply.sql', { dry_run: '0' });
  r = runFile('rollback.sql', {}, { PGOPTIONS: '-c DateStyle=SQL,DMY -c TimeZone=Pacific/Kiritimati' });
  record('n5.rollback_sql_dmy_kiritimati', r.status === 0, `exit=${r.status}`);
  record('n5.baseline_hashes_identical', sameHashes(tableHashes(), base));

  // ---- F-counterexamples: non-marker refs abort rollback (nothing deleted) ----
  r = runFile('apply.sql', { dry_run: '0' });
  sql(`INSERT INTO thesis.thesis_topic_score (id, topic_id, council_id, lecturer_id, component, score, graded_at, created_at)
       VALUES (gen_random_uuid(),'6f64670b-cd6b-4e1b-ae9d-0b497f2472a8','ed1a0000-0000-4000-8000-000500000004',
               'lecturer-profile-002','DEFENSE',9.00,now(),now());
       INSERT INTO thesis.thesis_group_member (id, group_id, round_id, student_id, member_order, is_leader, is_external, active_participation, created_at)
       VALUES (gen_random_uuid(),'ed1a0000-0000-4000-8000-000300000020','22d65ee6-f485-40ae-a66b-528d35007745',
               'student-profile-002',5,FALSE,FALSE,TRUE,now());
       INSERT INTO thesis.thesis_council_topic (id, council_id, topic_id, assigned_by, created_at)
       VALUES (gen_random_uuid(),'ed1a0000-0000-4000-8000-000500000004','005e2c8b-ee97-42ae-ae7f-91b74d482072','admin-user',now());`);
  r = runFile('rollback.sql');
  const refsLeft = sql(`SELECT
    (SELECT count(*) FROM thesis.thesis_topic_score WHERE council_id='ed1a0000-0000-4000-8000-000500000004' AND id::text NOT LIKE 'ed1a0000-%') +
    (SELECT count(*) FROM thesis.thesis_group_member WHERE group_id='ed1a0000-0000-4000-8000-000300000020' AND id::text NOT LIKE 'ed1a0000-%') +
    (SELECT count(*) FROM thesis.thesis_council_topic WHERE council_id='ed1a0000-0000-4000-8000-000500000004' AND id::text NOT LIKE 'ed1a0000-%')`).out.trim();
  record('f.refs_abort_rollback', r.status !== 0 && /reference marker rows/.test(r.out + r.err) && markerTotal() === MARKER_EXPECTED && refsLeft === '3',
         `exit=${r.status} refs=${refsLeft}`);
  sql(`DELETE FROM thesis.thesis_topic_score WHERE council_id='ed1a0000-0000-4000-8000-000500000004' AND id::text NOT LIKE 'ed1a0000-%';
       DELETE FROM thesis.thesis_group_member WHERE group_id='ed1a0000-0000-4000-8000-000300000020' AND id::text NOT LIKE 'ed1a0000-%';
       DELETE FROM thesis.thesis_council_topic WHERE council_id='ed1a0000-0000-4000-8000-000500000004' AND id::text NOT LIKE 'ed1a0000-%';`);
  r = runFile('rollback.sql');
  record('f.clean_rollback_after_refs_removed', r.status === 0 && markerTotal() === 0, `exit=${r.status}`);

  // ---- F-counterexample: pre-existing reviewer aborts apply, zero markers ----
  sql(`UPDATE thesis.thesis_topic SET gvpb_lecturer_id='lecturer-profile-003' WHERE id='6f64670b-cd6b-4e1b-ae9d-0b497f2472a8'`);
  r = runFile('apply.sql', { dry_run: '0' });
  record('f.preexisting_reviewer_aborts_apply', r.status !== 0 && markerTotal() === 0, `exit=${r.status} markers=${markerTotal()}`);
  sql(`UPDATE thesis.thesis_topic SET gvpb_lecturer_id=NULL WHERE id='6f64670b-cd6b-4e1b-ae9d-0b497f2472a8'`);

  // waiver / council-assignment / foreign-comment preflights on the same
  // guarded baseline topics (each maps to a dedicated mutant in mutants.mjs)
  sql(`UPDATE thesis.thesis_topic SET gvpb_waived_reason='probe' WHERE id='67c74df5-5c7f-4e74-aa02-d51af7207fe9'`);
  r = runFile('apply.sql', { dry_run: '0' });
  record('f.preexisting_waiver_aborts_apply', r.status !== 0 && /waiver/.test(r.out + r.err) && markerTotal() === 0, `exit=${r.status}`);
  sql(`UPDATE thesis.thesis_topic SET gvpb_waived_reason=NULL WHERE id='67c74df5-5c7f-4e74-aa02-d51af7207fe9'`);
  sql(`INSERT INTO thesis.thesis_council_topic (id, council_id, topic_id, assigned_by, created_at)
       VALUES (gen_random_uuid(),'7ea8f499-949e-42c2-b11e-02be39b8ec52','018f4a7c-3b12-4d90-a5e1-7c82b9045001','admin-user',now())`);
  r = runFile('apply.sql', { dry_run: '0' });
  record('f.existing_council_assignment_aborts', r.status !== 0 && /council assignment/.test(r.out + r.err) && markerTotal() === 0, `exit=${r.status}`);
  sql(`DELETE FROM thesis.thesis_council_topic WHERE council_id='7ea8f499-949e-42c2-b11e-02be39b8ec52' AND topic_id='018f4a7c-3b12-4d90-a5e1-7c82b9045001'`);
  sql(`COMMENT ON TABLE thesis.thesis_topic IS 'operator note - not the package'`);
  r = runFile('apply.sql', { dry_run: '0' });
  record('f.foreign_comment_aborts_apply', r.status !== 0 && /non-package comment/.test(r.out + r.err) && markerTotal() === 0, `exit=${r.status}`);
  sql(`COMMENT ON TABLE thesis.thesis_topic IS NULL`);

  // ---- P1 dean-retract (ENR3 E1): the app retracts round be1aa853 and the
  // notification recall deletes the round's 16 marker notifications. The old
  // package stuck BOTH ways ('partial state' on apply, '16 differ' on
  // rollback). Now: re-apply is a no-op 'already applied' and rollback
  // tolerates the app-deleted rows while still restoring what remains. ----
  r = runFile('apply.sql', { dry_run: '0' });
  record('p1.apply', r.status === 0 && markerTotal() === MARKER_EXPECTED, `exit=${r.status}`);
  const beSnap = sql(`SELECT version||'|'||(to_jsonb(updated_at)#>>'{}')||'|'||coalesce(to_jsonb(results_published_at)#>>'{}','~')||'|'||status
                      FROM thesis.thesis_registration_round WHERE id='be1aa853-f153-4f2e-a5cb-ed1b6c3c1bf1'`).out.trim();
  const nSkey = sql(`SELECT count(*) FROM notifications.notification WHERE id LIKE 'demo-th-%' AND source_key='THESIS_RESULTS:be1aa853-f153-4f2e-a5cb-ed1b6c3c1bf1'`).out.trim();
  const sim = sql(`UPDATE thesis.thesis_registration_round SET status='REGISTRATION_CLOSED', results_published_at=NULL,
       updated_at=now(), version=version+1 WHERE id='be1aa853-f153-4f2e-a5cb-ed1b6c3c1bf1';
       DELETE FROM notifications.notification WHERE id LIKE 'demo-th-%' AND source_key='THESIS_RESULTS:be1aa853-f153-4f2e-a5cb-ed1b6c3c1bf1';`);
  record('p1.retract_simulated', sim.status === 0 && nSkey === '16' && markerTotal() === MARKER_EXPECTED - 16,
         `exit=${sim.status} skey_notifications=${nSkey} markers=${markerTotal()}`);
  r = runFile('apply.sql', { dry_run: '0' });
  record('p1.reapply_is_noop_not_partial', r.status === 0 && /already applied/.test(r.out) && /removed/.test(r.out) && markerTotal() === MARKER_EXPECTED - 16,
         `exit=${r.status}`);
  r = runFile('rollback.sql');
  record('p1.rollback_tolerates_app_deleted', r.status === 0 && /already removed by the application/.test(r.out + r.err) && markerTotal() === 0,
         `exit=${r.status}`);
  // restore the retracted round to its pre-test state (the app-owned change
  // rollback correctly left alone: skipped_drift on the guarded columns)
  const [bv, bu, bp, bs] = beSnap.split('|');
  sql(`UPDATE thesis.thesis_registration_round SET version=${bv}, updated_at='${bu}'::timestamptz,
       results_published_at=${bp === '~' ? 'NULL' : `'${bp}'::timestamptz`}, status='${bs}'
       WHERE id='be1aa853-f153-4f2e-a5cb-ed1b6c3c1bf1'`);

  // ---- E1b: markAllRead touches only is_read/read_at/updated_at — excluded
  // bookkeeping columns — so a full sweep of the demo notifications must not
  // block rollback either. ----
  r = runFile('apply.sql', { dry_run: '0' });
  sql(`UPDATE notifications.notification SET is_read=TRUE, read_at=now(), updated_at=now() WHERE id LIKE 'demo-th-%'`);
  r = runFile('rollback.sql');
  record('e1.markallread_tolerated', r.status === 0 && markerTotal() === 0, `exit=${r.status}`);

  // ---- E1c: a later migration ADDs a column — hashed column list is
  // explicit, so whole-row shape changes no longer change any hash (ENR3 P2). ----
  r = runFile('apply.sql', { dry_run: '0' });
  const alt = sql(`ALTER TABLE thesis.thesis_group_member ADD COLUMN enr4_probe_col text`);
  r = runFile('rollback.sql');
  const unalt = sql(`ALTER TABLE thesis.thesis_group_member DROP COLUMN enr4_probe_col`);
  record('e1.add_column_migration_tolerated', alt.status === 0 && r.status === 0 && markerTotal() === 0 && unalt.status === 0,
         `alter=${alt.status} rollback=${r.status} unalter=${unalt.status}`);

  // ---- E3: bytea_output=escape session must not produce false drift (P3). ----
  r = runFile('apply.sql', { dry_run: '0' });
  r = runFile('rollback.sql', {}, { PGOPTIONS: '-c bytea_output=escape' });
  record('e3.bytea_escape_rollback', r.status === 0 && markerTotal() === 0, `exit=${r.status}`);

  // ---- P11 (ENR3 E1): image lost, app re-uploads a marker report, recovery
  // rebuilds the image from recorded expected content — NOT live rows — so
  // rollback REFUSES on the edited row and the re-uploaded file survives. ----
  r = runFile('apply.sql', { dry_run: '0' });
  const repId = sql(`SELECT id::text FROM thesis.thesis_group_report WHERE id::text LIKE 'ed1a0000-%' ORDER BY id LIMIT 1`).out.trim();
  const repSnap = sql(`SELECT encode(file_data,'hex')||'|'||file_name||'|'||file_size||'|'||file_sha256
                       FROM thesis.thesis_group_report WHERE id::text='${repId}'`).out.trim();
  sql(`UPDATE thesis.thesis_group_report SET file_data='\\x6170702d75706c6f6164'::bytea,
         file_name='app-reupload.pdf', file_size=9,
         file_sha256=encode(sha256('\\x6170702d75706c6f6164'::bytea),'hex'), updated_at=now()
       WHERE id::text='${repId}';
       DELETE FROM campuscore_audit."AdminAudit" WHERE id='demo-th-audit-000';
       COMMENT ON TABLE thesis.thesis_topic IS NULL;`);
  r = runFile('recover-before-image.sql');
  record('p11.recover_from_recorded_facts', r.status === 0 && /differ/.test(r.out + r.err), `exit=${r.status}`);
  r = runFile('rollback.sql');
  const fileLeft = sql(`SELECT count(*) FROM thesis.thesis_group_report WHERE id::text='${repId}' AND file_name='app-reupload.pdf'`).out.trim();
  record('p11.rollback_refuses_keeps_reupload', r.status !== 0 && /differ/.test(r.out + r.err) && fileLeft === '1',
         `exit=${r.status} reuploaded_file_present=${fileLeft}`);
  const [rfd, rfn, rfs, rfsum] = repSnap.split('|');
  sql(`UPDATE thesis.thesis_group_report SET file_data='\\x${rfd}'::bytea, file_name='${rfn}',
         file_size=${rfs}, file_sha256='${rfsum}' WHERE id::text='${repId}'`);
  r = runFile('rollback.sql');
  record('p11.clean_rollback_after_revert', r.status === 0 && markerTotal() === 0, `exit=${r.status}`);

  // ---- M4: the two image copies disagree -> rollback MUST abort (mutant:
  // removing the disagree check lets a tampered copy through). ----
  r = runFile('apply.sql', { dry_run: '0' });
  sql(`DO $$ DECLARE img text; BEGIN
         SELECT "beforeState" INTO img FROM campuscore_audit."AdminAudit" WHERE id='demo-th-audit-000';
         img := (img::jsonb || '{"tampered":1}'::jsonb)::text;
         EXECUTE format('COMMENT ON TABLE thesis.thesis_topic IS %L',
           'thesis-demo-data v3 md5:' || md5(img) || E'\\n' || img);
       END $$;`);
  r = runFile('rollback.sql');
  record('m4.disagreeing_copies_abort', r.status !== 0 && /disagree/.test(r.out + r.err) && markerTotal() === MARKER_EXPECTED,
         `exit=${r.status}`);
  sql(`DO $$ DECLARE img text; BEGIN
         SELECT "beforeState" INTO img FROM campuscore_audit."AdminAudit" WHERE id='demo-th-audit-000';
         EXECUTE format('COMMENT ON TABLE thesis.thesis_topic IS %L',
           'thesis-demo-data v3 md5:' || md5(img) || E'\\n' || img);
       END $$;`);
  r = runFile('rollback.sql');
  record('m4.rollback_after_comment_restored', r.status === 0 && markerTotal() === 0, `exit=${r.status}`);

  // ---- M5: comment copy with a bad checksum must be ignored even when it is
  // the only copy left (mutant: removing the checksum verification lets a
  // tampered comment be trusted). ----
  r = runFile('apply.sql', { dry_run: '0' });
  sql(`DO $$ DECLARE img text; BEGIN
         SELECT "beforeState" INTO img FROM campuscore_audit."AdminAudit" WHERE id='demo-th-audit-000';
         DELETE FROM campuscore_audit."AdminAudit" WHERE id='demo-th-audit-000';
         EXECUTE format('COMMENT ON TABLE thesis.thesis_topic IS %L',
           'thesis-demo-data v3 md5:00000000000000000000000000000000' || E'\\n' || img);
       END $$;`);
  r = runFile('rollback.sql');
  record('m5.bad_checksum_comment_rejected', r.status !== 0 && /before-image/.test(r.out + r.err) && markerTotal() === MARKER_EXPECTED - 1,
         `exit=${r.status} markers=${markerTotal()}`);
  sql(`COMMENT ON TABLE thesis.thesis_topic IS NULL;`);
  r = runFile('recover-before-image.sql');
  record('m5.recover_after_fake_comment_cleared', r.status === 0, `exit=${r.status}`);
  r = runFile('rollback.sql');
  record('m5.rollback_after_recovery', r.status === 0 && markerTotal() === 0, `exit=${r.status}`);

  // ---- M9: a non-marker reviewer (GVPB) score on a guarded baseline topic
  // must abort rollback (mutant: removing the guarded_gvpb preflight lets the
  // app's score be orphaned by the gvpb_lecturer_id restore). ----
  r = runFile('apply.sql', { dry_run: '0' });
  sql(`INSERT INTO thesis.thesis_topic_score (id, topic_id, council_id, lecturer_id, component, score, graded_at, created_at)
       VALUES (gen_random_uuid(),'018f4a7c-3b12-4d90-a5e1-7c82b9045001',NULL,'lecturer-profile-002','GVPB',9.00,now(),now())`);
  r = runFile('rollback.sql');
  record('m9.reviewer_score_preflight_aborts', r.status !== 0 && /guarded_gvpb/.test(r.out + r.err) && markerTotal() === MARKER_EXPECTED,
         `exit=${r.status}`);
  sql(`DELETE FROM thesis.thesis_topic_score WHERE id::text NOT LIKE 'ed1a0000-%'
       AND component='GVPB' AND topic_id='018f4a7c-3b12-4d90-a5e1-7c82b9045001'`);
  r = runFile('rollback.sql');
  record('m9.rollback_after_score_removed', r.status === 0 && markerTotal() === 0, `exit=${r.status}`);

  // ---- foreign partial: a marker-id row the image never wrote makes apply
  // refuse with the exact recovery instruction (never a blind re-apply). ----
  r = runFile('apply.sql', { dry_run: '0' });
  sql(`INSERT INTO thesis.thesis_council_member (id, council_id, lecturer_id, member_role, created_at)
       VALUES ('ed1a0000-0000-4000-8000-deadbeef0001'::uuid,'ed1a0000-0000-4000-8000-000500000001','lecturer-profile-002','MEMBER',now())`);
  r = runFile('apply.sql', { dry_run: '0' });
  record('e1.foreign_partial_refused', r.status !== 0 && /foreign partial/.test(r.out + r.err), `exit=${r.status}`);
  sql(`DELETE FROM thesis.thesis_council_member WHERE id='ed1a0000-0000-4000-8000-deadbeef0001'`);
  r = runFile('rollback.sql');
  record('e1.rollback_after_foreign_row_removed', r.status === 0 && markerTotal() === 0, `exit=${r.status}`);

  // ---- baseline drift: a touched baseline round makes apply refuse (guard
  // removal must not let it through). Runs at baseline — zero markers. ----
  sql(`UPDATE thesis.thesis_registration_round SET status='REGISTRATION_CLOSED' WHERE id='b35e9ff1-1eb8-4e73-812e-04e1a66041f1'`);
  r = runFile('apply.sql', { dry_run: '0' });
  record('e1.baseline_drift_refused', r.status !== 0 && /recon baseline/.test(r.out + r.err) && markerTotal() === 0, `exit=${r.status}`);
  sql(`UPDATE thesis.thesis_registration_round SET status='RESULTS_PUBLISHED' WHERE id='b35e9ff1-1eb8-4e73-812e-04e1a66041f1'`);

  // ---- flyway floor: schema below the required baseline must refuse. ----
  sql(`CREATE TABLE _fw_bak_enr4 AS SELECT * FROM thesis.flyway_schema_history WHERE version::int >= 140;
       DELETE FROM thesis.flyway_schema_history WHERE version::int >= 140;`);
  r = runFile('apply.sql', { dry_run: '0' });
  record('e1.flyway_below_142_refused', r.status !== 0 && /flyway/.test(r.out + r.err) && markerTotal() === 0, `exit=${r.status}`);
  sql(`INSERT INTO thesis.flyway_schema_history SELECT * FROM _fw_bak_enr4; DROP TABLE _fw_bak_enr4;`);

  // ---- real-account reference: a marker row pointing at a known real account
  // id aborts apply immediately (unconditional guard, runs before the state
  // machine). ----
  r = runFile('apply.sql', { dry_run: '0' });
  sql(`INSERT INTO notifications.notification (id, user_id, title, message, type, link, is_read, created_at, updated_at, source_key)
       VALUES ('demo-th-foreign-0001','16a1b2be-6556-4d47-83d9-e9a07daf109d','probe','probe','INFO','/',FALSE,now(),now(),NULL)`);
  r = runFile('apply.sql', { dry_run: '0' });
  record('e1.real_account_ref_aborts', r.status !== 0 && /real account/.test(r.out + r.err), `exit=${r.status}`);
  sql(`DELETE FROM notifications.notification WHERE id='demo-th-foreign-0001'`);
  r = runFile('rollback.sql');
  record('e1.rollback_after_real_ref_removed', r.status === 0 && markerTotal() === 0, `exit=${r.status}`);

  // ---- recover refuses while a usable copy exists and on a bare baseline. ----
  r = runFile('apply.sql', { dry_run: '0' });
  r = runFile('recover-before-image.sql');
  record('rv.refuses_with_copy_present', r.status !== 0 && /still exists/.test(r.out + r.err), `exit=${r.status}`);
  r = runFile('rollback.sql');
  record('rv.rollback_still_clean', r.status === 0 && markerTotal() === 0, `exit=${r.status}`);
  r = runFile('recover-before-image.sql');
  record('rv.refuses_without_markers', r.status !== 0 && /no marker rows/.test(r.out + r.err), `exit=${r.status}`);

  // ---- E2/P4 deadlock schedule (ENR3): app tx writes thesis -> sleeps ->
  // writes audit last; the package must be the lock victim, never the app.
  // Part 1: a bare rollback.sql (no runner retry) must fail cleanly while the
  // app transaction completes. ----
  const p4AppSql = (tag) => `BEGIN;
    SET application_name='enr6_p4';
    UPDATE thesis.thesis_registration_round SET updated_at=now() WHERE id='b35e9ff1-1eb8-4e73-812e-04e1a66041f1';
    SELECT pg_sleep(1.6);
    INSERT INTO campuscore_audit."AdminAudit"(id,"actorId","actorLabel",action,"entityType","entityId",summary,"beforeState","afterState","createdAt")
    VALUES ('enr4-p4-app-audit-${tag}','admin-user',NULL,'THESIS_ROUND_UPDATE','THESIS_ROUND','b35e9ff1-1eb8-4e73-812e-04e1a66041f1','p4 probe',NULL,NULL,now());
    COMMIT;`;
  const p4Snap = sql(`SELECT version||'|'||(to_jsonb(updated_at)#>>'{}')||'|'||status
                      FROM thesis.thesis_registration_round WHERE id='b35e9ff1-1eb8-4e73-812e-04e1a66041f1'`).out.trim();
  r = runFile('apply.sql', { dry_run: '0' });
  const p4app = spawn(psql, [...connArgs(), '-X', '-v', 'ON_ERROR_STOP=1', '-P', 'pager=off', '-c', p4AppSql('a')],
    { env: process.env, windowsHide: true });
  const p4done = once(p4app, 'exit');
  await delay(300);
  const e2rb = await spawnWatch('rollback.sql', {});
  const [p4code] = await p4done;
  record('e2.package_is_lock_victim_not_app',
         e2rb.status !== 0 && p4code === 0 && !e2rb.sawPending && markerTotal() === MARKER_EXPECTED,
         `rollback_exit=${e2rb.status} app_exit=${p4code} queued=${e2rb.sawPending}`);

  // Part 1b: the same schedule against apply — the package must be the lock
  // victim on the apply side too, not only on rollback (kills the mutant
  // that stretches apply's lock wait).
  const p4appA = spawn(psql, [...connArgs(), '-X', '-v', 'ON_ERROR_STOP=1', '-P', 'pager=off', '-c', p4AppSql('a2')],
    { env: process.env, windowsHide: true });
  const p4doneA = once(p4appA, 'exit');
  await delay(300);
  const e2ap = await spawnWatch('apply.sql', { dry_run: '0' });
  const [p4codeA] = await p4doneA;
  record('e2.apply_side_lock_victim',
         e2ap.status !== 0 && p4codeA === 0 && !e2ap.sawPending && markerTotal() === MARKER_EXPECTED,
         `apply_exit=${e2ap.status} app_exit=${p4codeA} queued=${e2ap.sawPending}`);

  // Part 2: same schedule again, this time through run.mjs — the runner's
  // bounded retry (fresh transaction per attempt) must ride out the app's
  // lock window and complete the rollback.
  const p4app2 = spawn(psql, [...connArgs(), '-X', '-v', 'ON_ERROR_STOP=1', '-P', 'pager=off', '-c', p4AppSql('b')],
    { env: process.env, windowsHide: true });
  const p4done2 = once(p4app2, 'exit');
  await delay(300);
  const rm = spawnSync('node', [join(here, 'run.mjs'), 'rollback'], { encoding: 'utf8', env: process.env, windowsHide: true });
  const [p4code2] = await p4done2;
  record('e2.runner_retries_completes', rm.status === 0 && p4code2 === 0 && markerTotal() === 0,
         `runner_exit=${rm.status} app_exit=${p4code2}`);
  sql(`DELETE FROM campuscore_audit."AdminAudit" WHERE id LIKE 'enr4-p4-%'`);
  const [pv, pu, ps] = p4Snap.split('|');
  sql(`UPDATE thesis.thesis_registration_round SET version=${pv}, updated_at='${pu}'::timestamptz, status='${ps}'
       WHERE id='b35e9ff1-1eb8-4e73-812e-04e1a66041f1'`);

  // ---- F3 (DL1): the reviewer's deadlock schedule — four staggered app
  // holders on council_member / council_topic / thesis_group / group_member
  // releasing at +0.15/+0.42/+0.69/+0.96 s, plus app transaction A that holds
  // the round and requests thesis_council at +0.10 s. Every package lock is
  // NOWAIT, so the package gives up instantly instead of closing the
  // wait-for cycle — the application transaction must NEVER be the victim.
  r = runFile('apply.sql', { dry_run: '0' });
  const dl1Snap = sql(`SELECT version||'|'||(to_jsonb(updated_at)#>>'{}')||'|'||status
                       FROM thesis.thesis_registration_round WHERE id='b35e9ff1-1eb8-4e73-812e-04e1a66041f1'`).out.trim();
  let dl1AppLost = 0, dl1Pending = 0;
  const dl1PkgExit = [];
  for (let i = 0; i < 3; i++) {
    const holders = ['thesis.thesis_council_member|0.15', 'thesis.thesis_council_topic|0.42',
                     'thesis.thesis_group|0.69', 'thesis.thesis_group_member|0.96']
      .map(spec => {
        const [t, s] = spec.split('|');
        const p = spawn(psql, [...connArgs(), '-X', '-v', 'ON_ERROR_STOP=1', '-P', 'pager=off', '-c',
          `BEGIN; SET application_name='enr6_dl1'; LOCK TABLE ${t} IN ROW EXCLUSIVE MODE; SELECT pg_sleep(${s}); COMMIT;`],
          { env: process.env, windowsHide: true });
        return once(p, 'exit');
      });
    const appA = spawn(psql, [...connArgs(), '-X', '-v', 'ON_ERROR_STOP=1', '-P', 'pager=off', '-c',
      `BEGIN; SET application_name='enr6_dl1';
       UPDATE thesis.thesis_registration_round SET updated_at=now() WHERE id='b35e9ff1-1eb8-4e73-812e-04e1a66041f1';
       SELECT pg_sleep(0.10);
       LOCK TABLE thesis.thesis_council IN ROW EXCLUSIVE MODE;
       COMMIT;`],
      { env: process.env, windowsHide: true });
    const appADone = once(appA, 'exit');
    await delay(60);
    const rb3 = await spawnWatch('rollback.sql', {});
    const [aCode] = await appADone;
    await Promise.all(holders);
    dl1PkgExit.push(rb3.status);
    if (rb3.sawPending) dl1Pending++;
    if (aCode !== 0) dl1AppLost++;
    if (markerTotal() === 0) runFile('apply.sql', { dry_run: '0' });
  }
  const [dv, du, ds] = dl1Snap.split('|');
  sql(`UPDATE thesis.thesis_registration_round SET version=${dv}, updated_at='${du}'::timestamptz, status='${ds}'
       WHERE id='b35e9ff1-1eb8-4e73-812e-04e1a66041f1'`);
  record('f3.dl1_app_never_deadlock_victim', dl1AppLost === 0 && dl1PkgExit.every(s => s !== 0) && dl1Pending === 0,
         `runs=3 app_lost=${dl1AppLost} pkg_exits=${dl1PkgExit.join(',')} queued=${dl1Pending}`);

  // ---- F3 (DL2): the retest's row-level deadlock schedule, permanent check.
  // Helper transactions hold FOR UPDATE row locks on three rounds and two
  // baseline topics (released ~0.6 s in); app transaction A holds FOR UPDATE
  // on a fourth baseline topic, sleeps, then writes a thesis_topic_score.
  // Under the retested build A was chosen as the deadlock victim while the
  // package committed. With every lock NOWAIT the package must exit retryably
  // (a 'locked' error) on all 5 runs, A must survive 5/5, and the package
  // must write nothing. ----
  const dl2Spawn = (stmts) => {
    const p = spawn(psql, [...connArgs(), '-X', '-v', 'ON_ERROR_STOP=1', '-P', 'pager=off', '-c', stmts],
      { env: process.env, windowsHide: true });
    return { p, done: once(p, 'exit') };
  };
  const dl2Holders = () => [
    ...['b35e9ff1-1eb8-4e73-812e-04e1a66041f1','be1aa853-f153-4f2e-a5cb-ed1b6c3c1bf1','bad8eba0-2d12-4414-8aab-7424174b3b97']
      .map(id => `SELECT 1 FROM thesis.thesis_registration_round WHERE id='${id}' FOR UPDATE`),
    ...['018f4a7c-3b12-4d90-a5e1-7c82b9045001','33622854-38c4-4c3f-a7eb-bab11d2ff421']
      .map(id => `SELECT 1 FROM thesis.thesis_topic WHERE id='${id}' FOR UPDATE`)]
    .map(s => dl2Spawn(`BEGIN; SET application_name='enr6_dl2'; ${s}; SELECT pg_sleep(0.6); COMMIT;`));
  const dl2AppA = () => dl2Spawn(`BEGIN; SET application_name='enr6_dl2';
     SELECT 1 FROM thesis.thesis_topic WHERE id='67c74df5-5c7f-4e74-aa02-d51af7207fe9' FOR UPDATE;
     SELECT pg_sleep(0.5);
     INSERT INTO thesis.thesis_topic_score (id, topic_id, council_id, lecturer_id, component, score, graded_at, created_at)
       VALUES (gen_random_uuid(),'67c74df5-5c7f-4e74-aa02-d51af7207fe9','7ea8f499-949e-42c2-b11e-02be39b8ec52',
               'lecturer-profile-002','DEFENSE',9.00,now(),now());
     COMMIT;`);
  const dl2Cleanup = () => sql(`DELETE FROM thesis.thesis_topic_score
    WHERE topic_id='67c74df5-5c7f-4e74-aa02-d51af7207fe9' AND council_id='7ea8f499-949e-42c2-b11e-02be39b8ec52'
      AND id::text NOT LIKE 'ed1a0000-%'`);
  // poll pg_locks until `want` sessions tagged `appName` each hold a granted
  // lock matching `where` — a fixed sleep would race psql startup on a loaded
  // box. Row locks (FOR UPDATE/KEY SHARE) show up as locktype='transactionid'
  // (the holder's own xid); a LOCK TABLE … ROW EXCLUSIVE shows as a granted
  // relation lock in mode RowExclusiveLock.
  async function waitForLocks(appName, want, where, ms = 3000) {
    for (let t = 0; t < ms; t += 40) {
      const n = parseInt(sql(`SELECT count(DISTINCT l.pid) FROM pg_locks l
        JOIN pg_stat_activity a ON a.pid = l.pid
        WHERE a.application_name='${appName}' AND l.granted AND ${where}`).out.trim() || '0', 10);
      if (n >= want) return true;
      await delay(40);
    }
    return false;
  }
  // run a package script via spawn while a dedicated BACKEND polls pg_locks
  // at ~100 Hz and RAISE NOTICEs every ungranted lock it sees (stderr is
  // unbuffered — sightings stream out live and survive a kill; a table
  // insert would roll back when we terminate the watcher). NOWAIT never
  // queues, so the real scripts never register a pending lock; a mutant
  // that drops NOWAIT must queue for the full lock_timeout (300 ms ≈ 30
  // samples), which cannot be missed. That is the deterministic
  // discriminator: a lock_timeout-canceled wait raises the same 55P03
  // 'locked' error text as NOWAIT, so exit code and message alone cannot
  // tell a queuing mutant from the real thing — an observed queue can.
  // Sessions tagged enr6_* (holders/app A/the watcher itself) are excluded
  // so only the package's own queue counts.
  async function spawnWatch(file, vars = {}) {
    // The poll loop is UNBOUNDED — coverage must last as long as the package
    // runs; the loop is exited either by a seen queue or by the terminate/
    // SIGKILL below once the package exits clean. backend_type pins the
    // observation to client sessions so e.g. an autovacuum worker's queue in
    // this database cannot masquerade as the package's.
    const watcher = spawn(psql, [...connArgs(), '-X', '-v', 'ON_ERROR_STOP=1', '-P', 'pager=off', '-c',
      `SET application_name='enr6_pw';
       DO $$ BEGIN
         RAISE NOTICE 'watcher_ready';
         LOOP
           IF EXISTS (SELECT 1 FROM pg_locks l JOIN pg_stat_activity a ON a.pid = l.pid
                      WHERE NOT l.granted AND a.datname = current_database()
                        AND a.backend_type = 'client backend'
                        AND (a.application_name IS NULL OR a.application_name NOT LIKE 'enr6\\_%')) THEN
             RAISE NOTICE 'pending_lock_seen'; EXIT;
           END IF;
           PERFORM pg_sleep(0.01);
         END LOOP; END $$;`],
      { env: process.env, windowsHide: true });
    const wDone = once(watcher, 'exit');
    let wErr = '';
    watcher.stderr.on('data', d => { wErr += d; });
    // the watcher announces itself before its first poll — wait for that so
    // the package can never queue in the startup gap. If it never arrives the
    // run is UNVERIFIABLE: report sawPending so no lock check can pass on a
    // blind observation (honest failure, not a silent pass).
    let ready = /watcher_ready/.test(wErr);
    for (let t = 0; t < 3000 && !ready; t++) { await delay(10); ready = /watcher_ready/.test(wErr); }
    if (!ready) {
      sql(`SELECT pg_terminate_backend(pid) FROM pg_stat_activity
           WHERE application_name='enr6_pw' AND pid<>pg_backend_pid()`);
      watcher.kill('SIGKILL');
      await wDone;
      return { status: 1, out: '', err: 'pending-lock watcher never became ready', sawPending: true };
    }
    const args = [...connArgs(), '-X', '-v', 'ON_ERROR_STOP=1', '-P', 'pager=off'];
    for (const [k, v] of Object.entries(vars)) args.push('-v', `${k}=${v}`);
    args.push('-f', join(here, file));
    const pkg = spawn(psql, args, { env: process.env, windowsHide: true });
    const done = once(pkg, 'exit');
    let out = '', err = '';
    pkg.stdout.on('data', d => { out += d; });
    pkg.stderr.on('data', d => { err += d; });
    const [code] = await done;
    if (!/pending_lock_seen/.test(wErr)) {
      sql(`SELECT pg_terminate_backend(pid) FROM pg_stat_activity
           WHERE application_name='enr6_pw' AND pid<>pg_backend_pid()`);
      watcher.kill('SIGKILL');
    }
    // read wErr only AFTER the watcher exits — its stderr stream is then
    // closed, so a just-delivered 'pending_lock_seen' can never be missed.
    await wDone;
    return { status: code ?? 1, out, err, sawPending: /pending_lock_seen/.test(wErr) };
  }
  async function dl2Schedule(file, vars) {
    const holders = dl2Holders();
    const appA = dl2AppA();
    await waitForLocks('enr6_dl2', 6, `l.locktype='transactionid'`); // 5 holders + app A
    const res = await spawnWatch(file, vars);
    const [aCode] = await appA.done;
    await Promise.all(holders.map(h => h.done));
    return { res, aCode };
  }

  if (markerTotal() !== MARKER_EXPECTED) runFile('apply.sql', { dry_run: '0' });
  let dl2rAppLost = 0, dl2rPkgBad = 0, dl2rWrote = 0, dl2rPending = 0;
  for (let i = 0; i < 5; i++) {
    const { res, aCode } = await dl2Schedule('rollback.sql');
    if (aCode !== 0) dl2rAppLost++;
    if (res.status === 0 || !/locked/i.test(res.out + res.err) || res.sawPending) dl2rPkgBad++;
    if (res.sawPending) dl2rPending++;
    if (markerTotal() !== MARKER_EXPECTED) dl2rWrote++;
    dl2Cleanup();
  }
  record('f3.dl2_rollback_app_survives_pkg_retryable',
         dl2rAppLost === 0 && dl2rPkgBad === 0 && dl2rWrote === 0 && markerTotal() === MARKER_EXPECTED,
         `runs=5 app_lost=${dl2rAppLost} pkg_bad=${dl2rPkgBad} writes=${dl2rWrote} queued=${dl2rPending}`);

  // ---- heldThenRelease: deterministically kills NOWAIT-removal mutants.
  // The holder keeps its lock for the whole run (30 s); the package runs
  // under spawnWatch, which reports whether any lock request ever queued.
  // NOWAIT real code aborts instantly and NEVER registers a pending lock —
  // a mutant that queues sees its pending lock in pg_locks (up to the 300 ms
  // lock_timeout) no matter how the wait ends, so sawPending alone kills it.
  // The holder is released by pg_terminate_backend on its application_name
  // tag — killing the psql client alone would leave the backend asleep for
  // the rest of pg_sleep and leak a lock holder into the next check. ----
  async function heldThenRelease(file, vars, holdStmt, readyWhere) {
    const holder = spawn(psql, [...connArgs(), '-X', '-v', 'ON_ERROR_STOP=1', '-P', 'pager=off', '-c',
      `BEGIN; SET application_name='enr6_htr'; ${holdStmt}; SELECT pg_sleep(30); COMMIT;`],
      { env: process.env, windowsHide: true });
    const hDone = once(holder, 'exit');
    await waitForLocks('enr6_htr', 1, readyWhere); // run only once the lock is held
    const res = await spawnWatch(file, vars);
    // terminate the holder's BACKEND by its application_name tag — parsing a
    // pid out of psql stdout races with chunking, and killing only the psql
    // client leaves the backend asleep for the rest of pg_sleep holding locks
    sql(`SELECT pg_terminate_backend(pid) FROM pg_stat_activity
         WHERE application_name='enr6_htr' AND pid<>pg_backend_pid()`);
    holder.kill('SIGKILL');
    await hDone;
    await delay(50); // let the terminated backend release its locks
    return res;
  }

  // rollback row-phase NOWAIT: FOR KEY SHARE on a marker topic (ROW SHARE
  // table lock is compatible, so the script reaches its row-lock phase)
  let rbRowBad = 0;
  for (let i = 0; i < 2; i++) {
    const res = await heldThenRelease('rollback.sql', {},
      `SELECT 1 FROM thesis.thesis_topic WHERE id='ed1a0000-0000-4000-8000-000100000001' FOR KEY SHARE`,
      `l.locktype='transactionid'`);
    if (res.status === 0 || !/locked/i.test(res.out + res.err) || res.sawPending) rbRowBad++;
  }
  record('f3.dl2_rowphase_rollback_nowait', rbRowBad === 0 && markerTotal() === MARKER_EXPECTED,
         `bad=${rbRowBad} markers=${markerTotal()}`);

  // rollback table-phase NOWAIT: an application write's table lock
  // (ROW EXCLUSIVE — what INSERT/UPDATE/DELETE take) conflicts with the
  // script's SHARE ROW EXCLUSIVE NOWAIT lock
  let rbTabBad = 0;
  for (let i = 0; i < 2; i++) {
    const res = await heldThenRelease('rollback.sql', {},
      `LOCK TABLE thesis.thesis_topic IN ROW EXCLUSIVE MODE`,
      `l.locktype='relation' AND l.mode='RowExclusiveLock'`);
    if (res.status === 0 || !/locked/i.test(res.out + res.err) || res.sawPending) rbTabBad++;
  }
  record('f3.short_hold_rollback_nowait', rbTabBad === 0 && markerTotal() === MARKER_EXPECTED,
         `bad=${rbTabBad} markers=${markerTotal()}`);

  r = runFile('rollback.sql'); // back to baseline for the apply-side schedule
  let dl2aAppLost = 0, dl2aPkgBad = 0, dl2aWrote = 0, dl2aPending = 0;
  for (let i = 0; i < 5; i++) {
    const { res, aCode } = await dl2Schedule('apply.sql', { dry_run: '0' });
    if (aCode !== 0) dl2aAppLost++;
    if (res.status === 0 || !/locked/i.test(res.out + res.err) || res.sawPending) dl2aPkgBad++;
    if (res.sawPending) dl2aPending++;
    if (markerTotal() !== 0) dl2aWrote++;
    dl2Cleanup();
  }
  record('f3.dl2_apply_app_survives_pkg_retryable',
         dl2aAppLost === 0 && dl2aPkgBad === 0 && dl2aWrote === 0 && markerTotal() === 0,
         `runs=5 app_lost=${dl2aAppLost} pkg_bad=${dl2aPkgBad} writes=${dl2aWrote} queued=${dl2aPending}`);

  // apply row-phase NOWAIT: FOR KEY SHARE on a baseline topic the apply
  // pre-locks — compatible table mode, conflicts only at the row lock
  let apRowBad = 0;
  for (let i = 0; i < 2; i++) {
    const res = await heldThenRelease('apply.sql', { dry_run: '0' },
      `SELECT 1 FROM thesis.thesis_topic WHERE id='67c74df5-5c7f-4e74-aa02-d51af7207fe9' FOR KEY SHARE`,
      `l.locktype='transactionid'`);
    if (res.status === 0 || !/locked/i.test(res.out + res.err) || res.sawPending) apRowBad++;
  }
  record('f3.dl2_rowphase_apply_nowait', apRowBad === 0 && markerTotal() === 0,
         `bad=${apRowBad} markers=${markerTotal()}`);

  // apply table-phase NOWAIT: an application write's table lock
  // (ROW EXCLUSIVE) meets the table lock NOWAIT
  let apTabBad = 0;
  for (let i = 0; i < 2; i++) {
    const res = await heldThenRelease('apply.sql', { dry_run: '0' },
      `LOCK TABLE thesis.thesis_topic IN ROW EXCLUSIVE MODE`,
      `l.locktype='relation' AND l.mode='RowExclusiveLock'`);
    if (res.status === 0 || !/locked/i.test(res.out + res.err) || res.sawPending) apTabBad++;
  }
  record('f3.short_hold_apply_nowait', apTabBad === 0 && markerTotal() === 0,
         `bad=${apTabBad} markers=${markerTotal()}`);

  // ---- statement-wait cap pinned at 300 ms (mutant
  // ap.lock_timeout_300ms_to_5s): every mutating script must pin
  // lock_timeout <= 300 ms so an implicit row/FK wait can never outlast
  // deadlock_timeout and turn the package into a long waiter. Parse the
  // literal from each script — stretching or removing it must fail here. ----
  const ltNames = ['apply.sql', 'rollback.sql', 'recover-before-image.sql'];
  const ltCheck = ltNames.map(f => {
    const src = readFileSync(join(here, f), 'utf8');
    const vals = [...src.matchAll(/SET\s+LOCAL\s+lock_timeout\s*=\s*'(\d+)\s*(ms|s)'/gi)]
      .map(m => parseInt(m[1], 10) * (m[2].toLowerCase() === 's' ? 1000 : 1));
    return vals.length > 0 && vals.every(v => v <= 300);
  });
  record('f3.lock_timeout_capped_300ms', ltCheck.every(Boolean),
         ltNames.map((f, i) => `${f}=${ltCheck[i] ? 'ok' : 'bad'}`).join(','));

  // ---- guarded-drift restore (mutant rb.unguarded_restore_overwrites_drift):
  // a column the application changed after apply must be SKIPPED, never
  // overwritten back to the apply-time value. ----
  // Snapshot at baseline: if a previous scenario left markers on, roll back
  // first so driftBase holds before-values, not applied ones.
  if (markerTotal() > 0) runFile('rollback.sql');
  const driftBase = sql(`SELECT version||'|'||(to_jsonb(updated_at)#>>'{}')||'|'||coalesce(to_jsonb(results_published_at)#>>'{}','~')||'|'||status
                          FROM thesis.thesis_registration_round WHERE id='b35e9ff1-1eb8-4e73-812e-04e1a66041f1'`).out.trim();
  runFile('apply.sql', { dry_run: '0' });
  sql(`UPDATE thesis.thesis_registration_round SET status='REGISTRATION_CLOSED',
       results_published_at='2099-01-01T00:00:00Z'::timestamptz, updated_at='2099-01-01T00:00:00Z'::timestamptz,
       version=version+9 WHERE id='b35e9ff1-1eb8-4e73-812e-04e1a66041f1'`);
  r = runFile('rollback.sql');
  const driftLeft = sql(`SELECT count(*) FROM thesis.thesis_registration_round
    WHERE id='b35e9ff1-1eb8-4e73-812e-04e1a66041f1' AND status='REGISTRATION_CLOSED'
      AND results_published_at='2099-01-01T00:00:00Z'::timestamptz AND updated_at='2099-01-01T00:00:00Z'::timestamptz`).out.trim();
  record('e1.guarded_drift_preserved', r.status === 0 && markerTotal() === 0 && driftLeft === '1',
         `exit=${r.status} drifted_cols_kept=${driftLeft}`);
  const [dbv, dbu, dbp, dbs] = driftBase.split('|');
  sql(`UPDATE thesis.thesis_registration_round SET version=${dbv}, updated_at='${dbu}'::timestamptz,
       results_published_at=${dbp === '~' ? 'NULL' : `'${dbp}'::timestamptz`}, status='${dbs}'
       WHERE id='b35e9ff1-1eb8-4e73-812e-04e1a66041f1'`);

  // ---- F4: a future child table with ON DELETE CASCADE into a package
  // table changes the reviewed nine-key FK inventory — rollback must REFUSE
  // before any cascade can silently delete the application's row. ----
  r = runFile('apply.sql', { dry_run: '0' });
  const repId2 = sql(`SELECT id::text FROM thesis.thesis_group_report WHERE id::text LIKE 'ed1a0000-%' ORDER BY id LIMIT 1`).out.trim();
  sql(`CREATE TABLE thesis.enr5_fk_probe (id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
        report_id uuid NOT NULL REFERENCES thesis.thesis_group_report(id) ON DELETE CASCADE);
       INSERT INTO thesis.enr5_fk_probe (report_id) VALUES ('${repId2}')`);
  r = runFile('rollback.sql');
  const probeLeft = sql(`SELECT count(*) FROM thesis.enr5_fk_probe`).out.trim();
  record('f4.future_cascade_child_refused',
         r.status !== 0 && /enr5_fk_probe|foreign-key inventory/.test(r.out + r.err)
           && probeLeft === '1' && markerTotal() === MARKER_EXPECTED,
         `exit=${r.status} probe_rows=${probeLeft} markers=${markerTotal()}`);
  sql(`DROP TABLE thesis.enr5_fk_probe`);
  r = runFile('rollback.sql');
  record('f4.rollback_after_probe_dropped', r.status === 0 && markerTotal() === 0, `exit=${r.status}`);

  // ---- F4b: an empty NON-cascade FK into a package table is inventory drift
  // only — zero rows means the cascade/blocking counts can never catch it.
  // This is the dedicated killer for rb.fk_inventory_abort_removed. ----
  r = runFile('apply.sql', { dry_run: '0' });
  sql(`CREATE TABLE thesis.enr6_fk_probe (id uuid PRIMARY KEY,
        report_id uuid REFERENCES thesis.thesis_group_report(id) ON DELETE SET NULL)`);
  r = runFile('rollback.sql');
  record('f4.inventory_only_drift_refused',
         r.status !== 0 && /foreign-key inventory/.test(r.out + r.err) && markerTotal() === MARKER_EXPECTED,
         `exit=${r.status} markers=${markerTotal()}`);
  sql(`DROP TABLE thesis.enr6_fk_probe`);
  r = runFile('rollback.sql');
  record('f4.rollback_after_probe2_dropped', r.status === 0 && markerTotal() === 0, `exit=${r.status}`);

  // ---- F4c: an unreviewed DELETE trigger on a package table must refuse —
  // a trigger-based cascade sits outside the FK inventory. ----
  r = runFile('apply.sql', { dry_run: '0' });
  sql(`CREATE OR REPLACE FUNCTION thesis.enr6_probe_trigger_fn() RETURNS trigger LANGUAGE plpgsql AS 'BEGIN RETURN NULL; END';
       CREATE TRIGGER enr6_probe_after_delete AFTER DELETE ON thesis.thesis_topic_score
         FOR EACH ROW EXECUTE FUNCTION thesis.enr6_probe_trigger_fn()`);
  r = runFile('rollback.sql');
  record('f4.unreviewed_delete_trigger_refused',
         r.status !== 0 && /DELETE-capable trigger|trigger.*beyond the reviewed set/.test(r.out + r.err) && markerTotal() === MARKER_EXPECTED,
         `exit=${r.status} markers=${markerTotal()}`);
  sql(`DROP TRIGGER enr6_probe_after_delete ON thesis.thesis_topic_score;
       DROP FUNCTION thesis.enr6_probe_trigger_fn()`);
  r = runFile('rollback.sql');
  record('f4.rollback_after_trigger_dropped', r.status === 0 && markerTotal() === 0, `exit=${r.status}`);

  // ---- F2: a LATIN1 client session must never produce mojibake — the
  // script's pinned client_encoding either aborts the run or writes correct
  // UTF-8 that the byte-level verify check confirms. ----
  r = runFile('apply.sql', { dry_run: '0' }, { PGCLIENTENCODING: 'LATIN1' });
  const latinHex = sql(`SELECT count(*) FROM notifications.notification WHERE id='demo-th-notif-0001'
     AND encode(convert_to(title,'UTF8'),'hex')='4be1babf74207175e1baa3206b68c3b361206c75e1baad6e20c491c3a32063c3b46e672062e1bb91'`).out.trim();
  record('e4.latin1_client_encoding_safe',
         (r.status !== 0 && markerTotal() === 0) || (r.status === 0 && latinHex === '1' && markerTotal() === MARKER_EXPECTED),
         `exit=${r.status} utf8_bytes_match=${latinHex} markers=${markerTotal()}`);
  if (markerTotal() > 0) {
    r = runFile('rollback.sql');
    record('e4.latin1_cycle_rollback', r.status === 0 && markerTotal() === 0, `exit=${r.status}`);
  }

  // ---- F5: a lock holder at SHARE UPDATE EXCLUSIVE level (the lock mode
  // autovacuum/ANALYZE holds) must be reported by the runner's blocked-by
  // diagnosis while it retries, then the run completes once it releases.
  // The holder is a plain client backend — with no real autovacuum WORKER
  // holding a package-table lock the diagnosis must say 'another session',
  // never autovacuum (the old classifier matched the permanent launcher). ----
  r = runFile('apply.sql', { dry_run: '0' });
  const avHolder = spawn(psql, [...connArgs(), '-X', '-v', 'ON_ERROR_STOP=1', '-P', 'pager=off', '-c',
    `BEGIN; LOCK TABLE thesis.thesis_topic IN SHARE UPDATE EXCLUSIVE MODE; SELECT pg_sleep(3); COMMIT;`],
    { env: process.env, windowsHide: true });
  const avDone = once(avHolder, 'exit');
  await delay(300);
  const avm = spawnSync('node', [join(here, 'run.mjs'), 'rollback'],
    { encoding: 'utf8', env: { ...process.env, THESIS_DEMO_MAX_ATTEMPTS: '16' }, windowsHide: true });
  const [avCode] = await avDone;
  record('f5.lock_holder_reported_and_retried',
         avm.status === 0 && avCode === 0 && /blocked by/i.test(avm.stderr || '') && markerTotal() === 0,
         `runner_exit=${avm.status} blocked_msg=${/blocked by/i.test(avm.stderr || '')}`);
  record('f5.sue_holder_not_autovacuum_attributed',
         !/blocked by autovacuum/.test(avm.stderr || '') && /blocked by another session \(counts: [1-9]/.test(avm.stderr || ''),
         `stderr_says=${(avm.stderr || '').match(/blocked by[^\n]*/)?.[0] || '(none)'}`);

  // ---- F5b (N2): a plain application write (UPDATE -> ROW EXCLUSIVE) and no
  // vacuum anywhere must be reported as 'another session' — never autovacuum.
  // (The script's own error text mentions autovacuum, so the assertion targets
  // the 'blocked by …' line only. The probe's updated_at bump is restored.) ----
  r = runFile('apply.sql', { dry_run: '0' });
  const appWTopic = '005e2c8b-ee97-42ae-ae7f-91b74d482072';
  const appWSave = sql(`SELECT updated_at::text FROM thesis.thesis_topic WHERE id='${appWTopic}'`).out.trim();
  const appW = spawn(psql, [...connArgs(), '-X', '-v', 'ON_ERROR_STOP=1', '-P', 'pager=off', '-c',
    `BEGIN; UPDATE thesis.thesis_topic SET updated_at=now() WHERE id='${appWTopic}'; SELECT pg_sleep(3); COMMIT;`],
    { env: process.env, windowsHide: true });
  const appWDone = once(appW, 'exit');
  await delay(300);
  const awm = spawnSync('node', [join(here, 'run.mjs'), 'rollback'],
    { encoding: 'utf8', env: { ...process.env, THESIS_DEMO_MAX_ATTEMPTS: '16' }, windowsHide: true });
  const [awCode] = await appWDone;
  sql(`UPDATE thesis.thesis_topic SET updated_at='${appWSave}'::timestamptz WHERE id='${appWTopic}'`);
  record('f5.app_write_reported_as_other_session',
         awm.status === 0 && awCode === 0
           && /blocked by another session \(counts: [1-9]/.test(awm.stderr || '')
           && !/blocked by autovacuum/.test(awm.stderr || '') && markerTotal() === 0,
         `runner_exit=${awm.status} stderr_says=${(awm.stderr || '').match(/blocked by[^\n]*/)?.[0] || '(none)'}`);

  // ---- final clean cycle under default session + end state ----
  r = runFile('apply.sql', { dry_run: '0' });
  const vf = runFile('verify.sql');
  record('final.verify_no_fail', (vf.out.match(/FAIL/g) || []).length === 0, `PASS=${(vf.out.match(/PASS/g) || []).length}`);
  r = runFile('rollback.sql');
  record('final.rollback_committed', r.status === 0 && markerTotal() === 0, `exit=${r.status}`);
  record('final.baseline_hashes_identical', sameHashes(tableHashes(), base));
  const commentLeft = sql(`SELECT coalesce(obj_description('thesis.thesis_topic'::regclass) LIKE 'thesis-demo-data%', FALSE)::int`).out.trim();
  record('final.no_comment_residue', commentLeft === '0', `comment_flag=${commentLeft}`);

  finish();
}
function finish() {
  const bad = results.filter(([, ok]) => !ok);
  console.log(`---\n${results.length - bad.length}/${results.length} checks PASS, ${bad.length} FAIL`);
  process.exit(bad.length ? 1 : 0);
}
main().catch(e => { console.error('selftest error:', e.message); finish(); });
