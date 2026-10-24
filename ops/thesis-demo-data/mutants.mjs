#!/usr/bin/env node
// Thesis demo-data mutation harness (E4): proves the selftest suite actually
// kills weakened guards, not just exercises happy paths.
//
//   PGDATABASE=<baseline-template-db> node mutants.mjs
//
// PGDATABASE must name a SCRATCH template database restored to the documented
// baseline (flyway 142, no marker rows) — the harness never writes to it; for
// every mutant it clones it with CREATE DATABASE ... TEMPLATE, applies the
// mutant to a scratch COPY of this package, runs selftest.mjs inside the copy
// against the clone, then drops the clone.
//
// Kill detection is STRUCTURED, not text-sniffing: a mutant is 'killed' when
// the selftest exits non-zero AND reports at least one real `FAIL ` detail
// line (or the parsed totals line reports FAIL > 0). Summary text such as
// '66/66 checks PASS, 0 FAIL' or 'FAIL=0' can never count as a kill.
// 'SURVIVED' means the suite passed on mutated code — a test gap, printed
// for fixing. 'control.identity_noop' replaces a line by itself and MUST
// survive; if the harness 'kills' it, the oracle is broken and this script
// exits non-zero. Counts-only output.
//
// Verdicts: killed | SURVIVED | EQUIVALENT | PATCH-MISS | INCONCLUSIVE.
// The harness exits non-zero when the control is killed, any non-equivalent
// mutant is not killed, or any run is PATCH-MISS/DB-SETUP-FAIL/INCONCLUSIVE.

import { spawnSync } from 'node:child_process';
import { mkdtempSync, cpSync, readFileSync, writeFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const psql = process.env.PSQL_BIN || 'psql';

// sed-style patch table: one guard removal per guard in apply/rollback/recover.
// find = exact literal in the file, replace = what the mutant writes instead.
const MUTANTS = [
  // identity control: patching a comment line by itself must come back
  // SURVIVED — if the oracle reports this as killed, the oracle is broken.
  { name: 'control.identity_noop', file: 'apply.sql', control: true, patches: [
    ['-- topics (17)', '-- topics (17)']] },
  { name: 'rb.disagree_abort_removed', file: 'rollback.sql', patches: [
    ['IF v_img IS NOT NULL AND v_copy IS NOT NULL AND v_img::text <> v_copy::text THEN', 'IF false THEN']] },
  { name: 'rb.checksum_removed', file: 'rollback.sql', patches: [
    ['md5(v_payload) = v_decl', 'true']] },
  { name: 'rb.gvpb_preflight_removed', file: 'rollback.sql', patches: [
    [`  INSERT INTO _blocking
  SELECT 'thesis_topic_score.guarded_gvpb', count(*)
  FROM thesis.thesis_topic_score s
  WHERE s.component = 'GVPB'
    AND s.topic_id IN ('018f4a7c-3b12-4d90-a5e1-7c82b9045001',
                       '6f64670b-cd6b-4e1b-ae9d-0b497f2472a8',
                       'f580eadf-fffb-4db2-a7d8-423e83934dc2')
    AND s.id::text NOT LIKE 'ed1a0000-0000-4000-8000-%';`, '']] },
  { name: 'rb.fk_preflight_removed', file: 'rollback.sql', patches: [
    ['WHILE n > 0 LOOP', 'WHILE false LOOP'],
    ['FOR r IN SELECT sch, tbl, col, psch, ptbl, pcol FROM _fk_refs WHERE del <> \'c\' LOOP',
     'FOR r IN SELECT sch, tbl, col, psch, ptbl, pcol FROM _fk_refs WHERE false LOOP']] },
  { name: 'rb.differs_abort_removed', file: 'rollback.sql', patches: [
    ['IF EXISTS (SELECT 1 FROM _hash_report WHERE differs > 0 OR extra > 0) THEN', 'IF false THEN']] },
  { name: 'rb.image_required_removed', file: 'rollback.sql', equivalent:
    'the hashcols guard still aborts a missing/foreign image: with v_img NULL every ' +
    'table list compares "[]" IS DISTINCT FROM the script literals, so the run ' +
    'refuses before anything is deleted — the envelope check is defense-in-depth.',
    patches: [
    [`  IF v_img IS NULL
     OR coalesce(v_img->>'format', '') <> '3'
     OR jsonb_typeof(v_img->'cols') IS DISTINCT FROM 'array'
     OR jsonb_typeof(v_img->'rows') IS DISTINCT FROM 'object'
     OR jsonb_typeof(v_img->'hashcols') IS DISTINCT FROM 'object' THEN`, '  IF false THEN']] },
  { name: 'ap.foreign_partial_refuse_removed', file: 'apply.sql', patches: [
    ["IF v_state = 'foreign_partial' THEN", 'IF false THEN']] },
  { name: 'ap.flyway_floor_removed', file: 'apply.sql', patches: [
    ['IF v_max IS NULL OR v_max < 142 THEN', 'IF false THEN']] },
  { name: 'ap.real_account_guard_removed', file: 'apply.sql', patches: [
    ['WHERE r.ref IN (', 'WHERE false AND r.ref IN (']] },
  { name: 'ap.baseline_round_check_removed', file: 'apply.sql', patches: [
    [`    IF NOT EXISTS (SELECT 1 FROM thesis.thesis_registration_round WHERE id = 'b35e9ff1-1eb8-4e73-812e-04e1a66041f1' AND status = 'RESULTS_PUBLISHED')
       OR (SELECT count(*) FROM thesis.thesis_topic WHERE round_id = 'b35e9ff1-1eb8-4e73-812e-04e1a66041f1') > 2 THEN`, '    IF false THEN']] },
  { name: 'ap.guarded_gvpb_preflight_removed', file: 'apply.sql', patches: [
    [`    IF EXISTS (SELECT 1 FROM thesis.thesis_topic
               WHERE id IN ('018f4a7c-3b12-4d90-a5e1-7c82b9045001',
                            '6f64670b-cd6b-4e1b-ae9d-0b497f2472a8',
                            'f580eadf-fffb-4db2-a7d8-423e83934dc2')
                 AND gvpb_lecturer_id IS NOT NULL) THEN`, '    IF false THEN']] },
  { name: 'ap.waiver_preflight_removed', file: 'apply.sql', patches: [
    [`    IF EXISTS (SELECT 1 FROM thesis.thesis_topic
               WHERE id = '67c74df5-5c7f-4e74-aa02-d51af7207fe9'
                 AND gvpb_waived_reason IS NOT NULL) THEN`, '    IF false THEN']] },
  { name: 'ap.council_assignment_preflight_removed', file: 'apply.sql', patches: [
    [`    IF EXISTS (SELECT 1 FROM thesis.thesis_council_topic WHERE topic_id IN (`, '    IF false AND EXISTS (SELECT 1 FROM thesis.thesis_council_topic WHERE topic_id IN (']] },
  { name: 'ap.comment_guard_removed', file: 'apply.sql', patches: [
    [`    IF obj_description('thesis.thesis_topic'::regclass) IS NOT NULL
       AND obj_description('thesis.thesis_topic'::regclass) NOT LIKE 'thesis-demo-data v3 %' THEN`, '    IF false THEN']] },
  // reviewer-requested mutant (a): a longer lock wait makes the package queue
  // behind an application write — the e2.apply_side_lock_victim check must
  // catch apply succeeding where it should have lost instantly.
  { name: 'ap.lock_timeout_300ms_to_5s', file: 'apply.sql', patches: [
    [`SET LOCAL lock_timeout = '300ms';`, `SET LOCAL lock_timeout = '5s';`]] },
  // reviewer-requested mutant (b): restoring guarded columns without the
  // still-unchanged condition overwrites columns the application drifted —
  // the e1.guarded_drift_preserved check must catch it.
  { name: 'rb.unguarded_restore_overwrites_drift', file: 'rollback.sql', patches: [
    ['WHERE id::text = $2 AND %I IS NOT DISTINCT FROM $3::%s', 'WHERE id::text = $2']] },
  { name: 'rv.copy_present_refuse_removed', file: 'recover-before-image.sql', patches: [
    ['IF v_has_img THEN', 'IF false THEN']] },
  { name: 'rv.no_markers_refuse_removed', file: 'recover-before-image.sql', patches: [
    ['IF v_markers = 0 THEN', 'IF false THEN']] },
];

const PACKAGE_FILES = ['apply.sql', 'rollback.sql', 'recover-before-image.sql',
  'verify.sql', 'manifest.sql', 'run.mjs', 'selftest.mjs', 'expected-image.sql'];

function connArgs(db) {
  const a = [];
  if (process.env.PGHOST) a.push('-h', process.env.PGHOST);
  if (process.env.PGPORT) a.push('-p', process.env.PGPORT);
  if (process.env.PGUSER) a.push('-U', process.env.PGUSER);
  a.push('-d', db);
  return a;
}
function adminSql(db, script) {
  const r = spawnSync(psql, [...connArgs(db), '-X', '-v', 'ON_ERROR_STOP=1', '-A', '-t', '-c', script],
    { encoding: 'utf8', env: { ...process.env, PGCLIENTENCODING: 'UTF8' }, windowsHide: true });
  return { status: r.status ?? 1, out: r.stdout || '', err: r.stderr || '' };
}

const templateDb = process.env.PGDATABASE;
if (!templateDb) { console.error('set PGDATABASE to the scratch baseline template'); process.exit(2); }
const filter = process.env.MUTANT_FILTER;
const mutants = filter ? MUTANTS.filter(m => m.name.includes(filter)) : MUTANTS;

// structured verdict: a kill needs a non-zero exit AND at least one real
// 'FAIL ' detail line or a parsed totals line with FAIL > 0. Summary text
// ('0 FAIL', 'FAIL=0') is never evidence on its own.
function judge(res) {
  const out = `${res.stdout || ''}\n${res.stderr || ''}`;
  const failLines = (out.match(/^FAIL /gm) || []).length;
  const totals = out.match(/(\d+)\/(\d+) checks PASS, (\d+) FAIL/);
  const failTotal = totals ? parseInt(totals[3], 10) : null;
  if (res.status === 0) {
    if (failLines > 0 || (failTotal !== null && failTotal > 0) || failTotal === null) return 'INCONCLUSIVE';
    return 'SURVIVED';
  }
  if (failLines > 0 || (failTotal !== null && failTotal > 0)) return 'killed';
  // the suite died before reporting (e.g. psql missing, package raised a
  // hard error in setup) — the mutant visibly broke the run: still a kill.
  return 'killed';
}

const results = [];
for (const m of mutants) {
  const tmp = mkdtempSync(join(tmpdir(), 'enr5-mut-'));
  const db = `${templateDb}_x`;
  try {
    for (const f of PACKAGE_FILES) cpSync(join(here, f), join(tmp, f));
    let ok = true;
    for (const p of m.patches) {
      const path = join(tmp, m.file);
      const src = readFileSync(path, 'utf8');
      const nl = src.includes('\r\n') ? '\r\n' : '\n'; // match the file's own newline
      const find = p[0].replace(/\n/g, nl);
      if (!src.includes(find)) { console.error(`PATCH-MISS ${m.name} — literal not found in ${m.file}`); ok = false; break; }
      writeFileSync(path, src.replace(find, p[1].replace(/\n/g, nl)));
    }
    if (!ok) { results.push([m, 'PATCH-MISS']); continue; }
    const mk = adminSql('postgres', `DROP DATABASE IF EXISTS ${db}`);
    const ct = adminSql('postgres', `CREATE DATABASE ${db} TEMPLATE ${templateDb}`);
    if (mk.status !== 0 || ct.status !== 0) { results.push([m, 'DB-SETUP-FAIL']); continue; }
    const r = spawnSync('node', [join(tmp, 'selftest.mjs')],
      { encoding: 'utf8', timeout: 600000,
        env: { ...process.env, PGDATABASE: db, PGCLIENTENCODING: 'UTF8', SELFTEST_STOP_ON_FAIL: '1' },
        windowsHide: true });
    results.push([m, judge(r)]);
  } finally {
    adminSql('postgres', `DROP DATABASE IF EXISTS ${db}`);
    rmSync(tmp, { recursive: true, force: true });
  }
}
let bad = 0, killed = 0, survived = 0, equivalent = 0;
for (const [m, verdict] of results) {
  let v = verdict;
  if (verdict === 'SURVIVED' && m.equivalent) { v = 'EQUIVALENT'; equivalent++; }
  else if (verdict === 'killed') killed++;
  else if (verdict === 'SURVIVED') survived++;
  const expectSurvive = m.control || m.equivalent;
  const ok = v === 'killed' ? !expectSurvive
           : v === 'SURVIVED' || v === 'EQUIVALENT' ? !!expectSurvive
           : false; // PATCH-MISS / DB-SETUP-FAIL / INCONCLUSIVE never ok
  if (!ok) bad++;
  console.log(`${v} ${m.name}${ok ? '' : '   <<< unexpected'}${m.equivalent ? `\n    equivalent: ${m.equivalent}` : ''}`);
}
console.log(`---\n${killed} killed, ${survived} survived, ${equivalent} equivalent, ${bad} unexpected verdict(s)`);
process.exit(bad === 0 ? 0 : 1);
