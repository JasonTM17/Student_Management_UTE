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
  return { status: r.status ?? 1, out: r.stdout || '', err: r.stderr || '' };
}
function runFile(file, vars = {}, env = {}) {
  const args = [...connArgs(), '-X', '-v', 'ON_ERROR_STOP=1', '-P', 'pager=off'];
  for (const [k, v] of Object.entries(vars)) args.push('-v', `${k}=${v}`);
  args.push('-f', join(here, file));
  const r = spawnSync(psql, args, { encoding: 'utf8', env: { ...process.env, ...env }, windowsHide: true });
  return { status: r.status ?? 1, out: r.stdout || '', err: r.stderr || '' };
}
function record(name, ok, detail = '') {
  results.push([name, ok]);
  console.log(`${ok ? 'PASS' : 'FAIL'} ${name}${detail ? ' — ' + detail : ''}`);
}

const HASH_SQL = [`SET TimeZone='UTC'`, `SET DateStyle='ISO,MDY'`,
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
  await delay(1000);
  const t0 = Date.now();
  r = runFile('rollback.sql');
  const raceMs = Date.now() - t0;
  const [appCode] = await once(app, 'exit');
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
  await delay(400);
  const app2Sql = `INSERT INTO thesis.thesis_topic_score (id, topic_id, council_id, lecturer_id, component, score, graded_at, created_at)
    VALUES (gen_random_uuid(), '6f64670b-cd6b-4e1b-ae9d-0b497f2472a8', 'ed1a0000-0000-4000-8000-000500000004',
            'lecturer-profile-002', 'DEFENSE', 9.00, now(), now())`;
  const r2 = sql(app2Sql);
  const [rbCode] = await once(rb, 'exit');
  const appRow2 = sql(`SELECT count(*) FROM thesis.thesis_topic_score WHERE council_id='ed1a0000-0000-4000-8000-000500000004'`).out.trim();
  record('race2.app_insert_rejected', r2.status !== 0 && appRow2 === '0',
         `app_exit=${r2.status} rollback_exit=${rbCode} leftover=${appRow2}`);
  record('race2.rollback_committed', rbCode === 0 && markerTotal() === 0, `markers=${markerTotal()}`);

  // re-apply for the next phases
  r = runFile('apply.sql', { dry_run: '0' });
  record('reapply_for_phase2.exit0', r.status === 0 && markerTotal() === MARKER_EXPECTED, `exit=${r.status}`);

  // ---- N4: in-place edit of a marker row must abort rollback ----
  const editId = sql(`SELECT id::text FROM thesis.thesis_group_report WHERE id::text LIKE 'ed1a0000-%' ORDER BY id LIMIT 1`).out.trim();
  const origUpd = sql(`SELECT to_jsonb(updated_at)#>>'{}' FROM thesis.thesis_group_report WHERE id::text='${editId}'`).out.trim();
  sql(`UPDATE thesis.thesis_group_report SET updated_at = now() + interval '1 hour' WHERE id::text='${editId}'`);
  r = runFile('rollback.sql');
  record('n4.report_edit_aborts', r.status !== 0 && /content hash|differ/.test(r.out + r.err) && markerTotal() === MARKER_EXPECTED,
         `exit=${r.status}`);
  sql(`UPDATE thesis.thesis_group_report SET updated_at='${origUpd}'::timestamptz WHERE id::text='${editId}'`);
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

  // ---- final clean cycle under default session + end state ----
  r = runFile('apply.sql', { dry_run: '0' });
  const v = runFile('verify.sql');
  record('final.verify_no_fail', (v.out.match(/FAIL/g) || []).length === 0, `PASS=${(v.out.match(/PASS/g) || []).length}`);
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
