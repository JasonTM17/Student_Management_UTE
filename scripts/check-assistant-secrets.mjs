#!/usr/bin/env node

/**
 * Redacted, repository-scoped assistant secret hygiene gate.
 * It reports only a path, line and finding kind; matched credential material
 * is never printed. Ignored files (including local .env files) are excluded by
 * Git so developer credentials are not copied into CI output.
 */
import { execFileSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import path from 'node:path';

const root = process.cwd();
const ignoredDirectories = new Set([
  '.git',
  'node_modules',
  'target',
  '.next',
  '.expo',
  'dist',
  'build',
]);
const patterns = [
  ['provider-key', /\bsk-[A-Za-z0-9_-]{20,}\b/],
  ['jwt', /\beyJ[A-Za-z0-9_-]{20,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\b/],
  ['bearer-token', /\bBearer\s+[A-Za-z0-9._~+/=-]{24,}/i],
];

const files = execFileSync(
  'git',
  ['ls-files', '--cached', '--others', '--exclude-standard'],
  { cwd: root, encoding: 'utf8' },
)
  .split(/\r?\n/)
  .filter(Boolean)
  .filter((file) => !file.split(/[\\/]/).some((part) => ignoredDirectories.has(part)));

const findings = [];
for (const relative of files) {
  const absolute = path.join(root, relative);
  let text;
  try {
    text = readFileSync(absolute, 'utf8');
  } catch {
    continue;
  }
  const lines = text.split(/\r?\n/);
  lines.forEach((line, index) => {
    for (const [kind, pattern] of patterns) {
      if (pattern.test(line)) findings.push({ file: relative, line: index + 1, kind });
    }
  });
}

if (findings.length > 0) {
  for (const finding of findings) {
    console.error(`assistant-secret-check: ${finding.kind} at ${finding.file}:${finding.line}`);
  }
  process.exitCode = 1;
} else {
  console.log(`assistant-secret-check: PASS (${files.length} git-visible files scanned; values redacted)`);
}
