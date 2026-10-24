'use strict';

const fs = require('node:fs');
const path = require('node:path');

// Repo root: frontend/tests/helpers -> frontend/tests -> frontend -> <repo>.
const JAVA_SOURCE_ROOT = path.resolve(
  __dirname,
  '..',
  '..',
  '..',
  'java-services',
  'restful-api',
  'src',
  'main',
  'java',
);

let fileIndex;

// Walks the backend source tree once and maps each simple class name to its
// file paths, so a contract test reads a Java source without pinning the
// package it happens to live in today.
function indexJavaSources() {
  if (fileIndex) {
    return fileIndex;
  }
  fileIndex = new Map();
  const pending = [JAVA_SOURCE_ROOT];
  while (pending.length > 0) {
    const dir = pending.pop();
    for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
      const full = path.join(dir, entry.name);
      if (entry.isDirectory()) {
        pending.push(full);
      } else if (entry.name.endsWith('.java')) {
        const className = entry.name.slice(0, -'.java'.length);
        const matches = fileIndex.get(className);
        if (matches) {
          matches.push(full);
        } else {
          fileIndex.set(className, [full]);
        }
      }
    }
  }
  return fileIndex;
}

function javaSourcePath(className) {
  const matches = indexJavaSources().get(className) || [];
  if (matches.length === 0) {
    throw new Error(
      `no Java source file ${className}.java under ${JAVA_SOURCE_ROOT}`,
    );
  }
  if (matches.length > 1) {
    const candidates = matches.map((candidate) => `  - ${candidate}`).join('\n');
    throw new Error(
      `Java class name ${className} is ambiguous; ${matches.length} files match:\n${candidates}`,
    );
  }
  return matches[0];
}

function readJavaSource(className) {
  return fs.readFileSync(javaSourcePath(className), 'utf8');
}

module.exports = { javaSourcePath, readJavaSource };
