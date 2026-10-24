// Postgres integration-test execution gate.
//
// Every *IT.java under java-services/restful-api/src/test/java is a real
// PostgreSQL integration test. They only run under the Maven profile
// `assistant-postgres-it` and each class disables itself when its JDBC env
// variable is absent — which means a CI run can pass while every IT silently
// skipped. This script closes that hole: when the anchor env var is set it
// reads the failsafe JUnit reports and fails unless every *IT class on disk
// executed at least one test and nothing was skipped.
//
// Local `mvn test`/`verify` without the env vars is unaffected: with the
// anchor unset the script is a no-op, so only callers that opted into the
// Postgres suite are held to the contract.
//
//   node scripts/check-postgres-its.mjs [reportsDir] [testSourceDir]
//
//   reportsDir    default java-services/restful-api/target/failsafe-reports
//   testSourceDir default java-services/restful-api/src/test/java
import { readdirSync, readFileSync, existsSync } from 'node:fs';
import { join, resolve } from 'node:path';

const GATE_ENV = 'CAMPUSCORE_E2E_POSTGRES_URL';
const DEFAULT_REPORTS = 'java-services/restful-api/target/failsafe-reports';
const DEFAULT_SOURCES = 'java-services/restful-api/src/test/java';

/** Recursively collects `*IT.java` integration-test class names as FQCNs. */
export function expectedItClasses(sourceDir) {
  const classes = [];
  for (const entry of readdirSync(sourceDir, { withFileTypes: true })) {
    const path = join(sourceDir, entry.name);
    if (entry.isDirectory()) {
      classes.push(...expectedItClasses(path));
      continue;
    }
    if (!entry.name.endsWith('IT.java')) continue;
    const packageName = readFileSync(path, 'utf8').match(/^package\s+([\w.]+)\s*;/m);
    if (!packageName) {
      classes.push(path);
      continue;
    }
    classes.push(`${packageName[1]}.${entry.name.replace(/\.java$/, '')}`);
  }
  return classes;
}

/** Reads the numeric tests/skipped/failures/errors attributes of a JUnit report. */
export function readSuiteCounts(reportFile) {
  const xml = readFileSync(reportFile, 'utf8');
  const counts = { tests: 0, skipped: 0, failures: 0, errors: 0 };
  const tag = xml.match(/<testsuite\b[^>]*>/);
  if (!tag) return null;
  for (const match of tag[0].matchAll(/\b(tests|skipped|failures|errors)="(\d+)"/g)) {
    counts[match[1]] = Number(match[2]);
  }
  return counts;
}

/**
 * Compares the expected *IT classes against the failsafe reports.
 * Returns { problems, results } — an empty problems array means every class ran.
 */
export function evaluateReports(reportsDir, classes) {
  const problems = [];
  const results = [];
  if (classes.length === 0) {
    problems.push(`no *IT.java classes found; refusing to prove an empty suite`);
  }
  if (!existsSync(reportsDir)) {
    problems.push(`failsafe reports directory missing: ${reportsDir}`);
    return { problems, results };
  }
  for (const className of classes.sort()) {
    const reportFile = join(reportsDir, `TEST-${className}.xml`);
    if (!existsSync(reportFile)) {
      problems.push(`${className}: no failsafe report (class never ran)`);
      continue;
    }
    const counts = readSuiteCounts(reportFile);
    if (!counts) {
      problems.push(`${className}: unreadable JUnit report`);
      continue;
    }
    results.push({ className, ...counts });
    if (counts.tests === 0) {
      problems.push(`${className}: report shows 0 tests`);
    }
    if (counts.skipped > 0) {
      problems.push(`${className}: ${counts.skipped} test(s) skipped`);
    }
    if (counts.failures > 0 || counts.errors > 0) {
      problems.push(`${className}: ${counts.failures} failure(s), ${counts.errors} error(s)`);
    }
  }
  return { problems, results };
}

export function main(env = process.env, argv = process.argv.slice(2), cwd = process.cwd()) {
  if (!env[GATE_ENV]) {
    console.log(`${GATE_ENV} not set — Postgres ITs are not required here; nothing to check.`);
    return 0;
  }
  const reportsDir = resolve(cwd, argv[0] || DEFAULT_REPORTS);
  const sourceDir = resolve(cwd, argv[1] || DEFAULT_SOURCES);
  const classes = expectedItClasses(sourceDir);
  const { problems, results } = evaluateReports(reportsDir, classes);
  for (const result of results) {
    console.log(`  ${result.className}: tests=${result.tests} skipped=${result.skipped}`);
  }
  console.log(`checked ${classes.length} Postgres IT classes against ${reportsDir}`);
  if (problems.length > 0) {
    for (const problem of problems) console.error(`FAIL  ${problem}`);
    console.error('POSTGRES ITS INCOMPLETE — the suite silently skipped or failed classes.');
    return 1;
  }
  console.log('POSTGRES ITS PASS — every *IT class executed with zero skips.');
  return 0;
}

if (process.argv[1] && process.argv[1].replace(/\\/g, '/').endsWith('check-postgres-its.mjs')) {
  process.exitCode = main();
}
