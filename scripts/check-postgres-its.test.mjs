// Unit tests for scripts/check-postgres-its.mjs — the gate that makes silent
// Postgres IT skips impossible in CI. Fixture reports live in temp dirs; no
// real Maven run is needed to prove the verdict logic.
import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

import { expectedItClasses, evaluateReports, main } from './check-postgres-its.mjs';

function suiteXml({ tests = 1, skipped = 0, failures = 0, errors = 0 } = {}) {
  return `<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="x" tests="${tests}" skipped="${skipped}" failures="${failures}" errors="${errors}" time="0.1">
  <testcase classname="x" name="t" time="0.1"/>
</testsuite>`;
}

function fixture({ classes = [], reports = new Map() } = {}) {
  const root = mkdtempSync(join(tmpdir(), 'pg-it-guard-'));
  const sourceDir = join(root, 'src', 'io', 'campuscore', 'restfulapi', 'it');
  const reportsDir = join(root, 'reports');
  mkdirSync(sourceDir, { recursive: true });
  mkdirSync(reportsDir, { recursive: true });
  for (const name of classes) {
    writeFileSync(join(sourceDir, `${name}.java`),
      `package io.campuscore.restfulapi.it;\nclass ${name} {}\n`);
  }
  for (const [fqcn, xml] of reports) {
    writeFileSync(join(reportsDir, `TEST-${fqcn}.xml`), xml);
  }
  return { root, sourceDir, reportsDir };
}

test('expectedItClasses maps *IT.java files to FQCNs and ignores unit tests', () => {
  const { sourceDir } = fixture({ classes: ['SeatRacePostgresIT'] });
  writeFileSync(join(sourceDir, 'SomeUnitTest.java'), 'class SomeUnitTest {}');
  assert.deepEqual(expectedItClasses(sourceDir), ['io.campuscore.restfulapi.it.SeatRacePostgresIT']);
});

test('every class executed produces no problems', () => {
  const cls = 'io.campuscore.restfulapi.it.SeatRacePostgresIT';
  const { sourceDir, reportsDir } = fixture({
    classes: ['SeatRacePostgresIT'],
    reports: new Map([[cls, suiteXml({ tests: 3 })]]),
  });
  const { problems, results } = evaluateReports(reportsDir, expectedItClasses(sourceDir));
  assert.deepEqual(problems, []);
  assert.equal(results.length, 1);
  assert.equal(results[0].tests, 3);
});

test('a class with no failsafe report is a violation', () => {
  const { sourceDir, reportsDir } = fixture({ classes: ['SkippedIT'] });
  const { problems } = evaluateReports(reportsDir, expectedItClasses(sourceDir));
  assert.equal(problems.length, 1);
  assert.match(problems[0], /never ran/);
});

test('a fully skipped class is a violation', () => {
  const cls = 'io.campuscore.restfulapi.it.SkippedIT';
  const { sourceDir, reportsDir } = fixture({
    classes: ['SkippedIT'],
    reports: new Map([[cls, suiteXml({ tests: 4, skipped: 4 })]]),
  });
  const { problems } = evaluateReports(reportsDir, expectedItClasses(sourceDir));
  assert.equal(problems.length, 1);
  assert.match(problems[0], /4 test\(s\) skipped/);
});

test('failures and errors are reported', () => {
  const cls = 'io.campuscore.restfulapi.it.RedIT';
  const { sourceDir, reportsDir } = fixture({
    classes: ['RedIT'],
    reports: new Map([[cls, suiteXml({ tests: 2, failures: 1, errors: 1 })]]),
  });
  const { problems } = evaluateReports(reportsDir, expectedItClasses(sourceDir));
  assert.equal(problems.length, 1);
  assert.match(problems[0], /1 failure\(s\), 1 error\(s\)/);
});

test('a missing reports directory fails instead of passing vacuously', () => {
  const { sourceDir } = fixture({ classes: ['SomeIT'] });
  const { problems } = evaluateReports(join(sourceDir, 'no-such-dir'), expectedItClasses(sourceDir));
  assert.equal(problems.length, 1);
  assert.match(problems[0], /reports directory missing/);
});

test('an empty test tree is a violation, never a pass', () => {
  const { sourceDir, reportsDir } = fixture();
  const { problems } = evaluateReports(reportsDir, expectedItClasses(sourceDir));
  assert.ok(problems.some(p => /empty suite/.test(p)));
});

test('main is a no-op when the gate env var is absent', () => {
  const { sourceDir, reportsDir } = fixture();
  const code = main({}, [reportsDir, sourceDir], '/');
  assert.equal(code, 0);
});

test('main fails the job when the env is set and an IT skipped', () => {
  const cls = 'io.campuscore.restfulapi.it.QuietIT';
  const { root, sourceDir, reportsDir } = fixture({
    classes: ['QuietIT'],
    reports: new Map([[cls, suiteXml({ tests: 2, skipped: 2 })]]),
  });
  const code = main(
    { CAMPUSCORE_E2E_POSTGRES_URL: 'jdbc:postgresql://127.0.0.1:5432/whatever' },
    [reportsDir, sourceDir],
    root);
  assert.equal(code, 1);
});

test('main passes when every IT executed', () => {
  const cls = 'io.campuscore.restfulapi.it.LoudIT';
  const { root, sourceDir, reportsDir } = fixture({
    classes: ['LoudIT'],
    reports: new Map([[cls, suiteXml({ tests: 2 })]]),
  });
  const code = main(
    { CAMPUSCORE_E2E_POSTGRES_URL: 'jdbc:postgresql://127.0.0.1:5432/whatever' },
    [reportsDir, sourceDir],
    root);
  assert.equal(code, 0);
});
