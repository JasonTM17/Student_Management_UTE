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
// A mutant is 'killed' when the selftest reports at least one FAIL (the suite
// notices the weakened guard). 'SURVIVED' means the suite passed on mutated
// code — a test gap, printed for fixing. Counts-only output.

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
    [`  FOR r IN SELECT sch, tbl, col FROM _fk_refs LOOP
    EXECUTE format(
      'INSERT INTO _blocking SELECT %L, count(*) FROM %I.%I c
        WHERE c.%I::text LIKE ''ed1a0000-0000-4000-8000-%%''
          AND c.id::text NOT LIKE ''ed1a0000-0000-4000-8000-%%''',
      r.tbl || '.' || r.col, r.sch, r.tbl, r.col);
  END LOOP;`, '']] },
  { name: 'rb.differs_abort_removed', file: 'rollback.sql', patches: [
    ['IF EXISTS (SELECT 1 FROM _hash_report WHERE differs > 0 OR extra > 0) THEN', 'IF false THEN']] },
  { name: 'rb.image_required_removed', file: 'rollback.sql', patches: [
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
    { encoding: 'utf8', env: process.env, windowsHide: true });
  return { status: r.status ?? 1, out: r.stdout || '', err: r.stderr || '' };
}

const templateDb = process.env.PGDATABASE;
if (!templateDb) { console.error('set PGDATABASE to the scratch baseline template'); process.exit(2); }
const filter = process.env.MUTANT_FILTER;
const mutants = filter ? MUTANTS.filter(m => m.name.includes(filter)) : MUTANTS;

const results = [];
for (const m of mutants) {
  const tmp = mkdtempSync(join(tmpdir(), 'enr4-mut-'));
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
    if (!ok) { results.push([m.name, 'PATCH-MISS']); continue; }
    const mk = adminSql('postgres', `DROP DATABASE IF EXISTS ${db}`);
    const ct = adminSql('postgres', `CREATE DATABASE ${db} TEMPLATE ${templateDb}`);
    if (mk.status !== 0 || ct.status !== 0) { results.push([m.name, 'DB-SETUP-FAIL']); continue; }
    const r = spawnSync('node', [join(tmp, 'selftest.mjs')],
      { encoding: 'utf8', timeout: 600000, env: { ...process.env, PGDATABASE: db, SELFTEST_STOP_ON_FAIL: '1' }, windowsHide: true });
    const failed = (r.stdout || '').includes('FAIL');
    results.push([m.name, failed ? 'killed' : 'SURVIVED']);
  } finally {
    adminSql('postgres', `DROP DATABASE IF EXISTS ${db}`);
    rmSync(tmp, { recursive: true, force: true });
  }
}
let killed = 0;
for (const [name, verdict] of results) {
  console.log(`${verdict} ${name}`);
  if (verdict === 'killed') killed++;
}
console.log(`---\n${killed}/${results.length} mutants killed`);
process.exit(killed === results.length ? 0 : 1);
