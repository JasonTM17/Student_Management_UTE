import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const repoRoot = path.resolve(__dirname, '..');

const excludedDirectoryNames = new Set([
  '.cache',
  '.git',
  '.next',
  '.turbo',
  '.agents',
  '.agentkit',
  '.claude',
  '.codex',
  'backups',
  'plans',
  'target',
  'tinymce',
  'audits',
  'build',
  'coverage',
  'dist',
  'logs',
  'node_modules',
  'playwright-report',
  'reports',
  'test-results',
  'tmp',
]);

const textLikeFilePattern =
  /\.(?:cjs|css|env|example|html|js|json|jsx|md|mdx|mjs|mts|scss|sh|sql|test\.js|toml|ts|tsx|txt|yml|yaml)$/iu;

const mojibakeMarkers = [
  { label: 'replacement character', pattern: /\uFFFD/u },
  { label: 'latin-1 UTF-8 lead C3', pattern: /\u00c3/u },
  { label: 'latin-1 UTF-8 lead C2', pattern: /\u00c2/u },
  { label: 'latin-1 UTF-8 lead C4', pattern: /\u00c4/u },
  { label: 'latin-1 UTF-8 lead C6', pattern: /\u00c6/u },
  { label: 'Vietnamese UTF-8 bytes decoded as Latin-1', pattern: /\u00e1[\u00ba\u00bb]/u },
  { label: 'smart punctuation mojibake', pattern: /\u00e2\u20ac/u },
];

const localizedKeyPattern =
  /\b(?:body|copy|cta|description|label|message|name|semester|subject|text|title)(?:En|Vi)?\b|['"]vi['"]\s*:/iu;
const suspiciousQuestionMarkPattern = /\p{L}\?\p{L}/u;

function main() {
  const errors = [];

  for (const filePath of walkRepo(repoRoot)) {
    scanFile(filePath, errors);
  }

  if (errors.length > 0) {
    for (const error of errors) {
      console.error(`- ${error}`);
    }
    process.exit(1);
  }

  console.log('Text encoding guard passed.');
}

function scanFile(filePath, errors) {
  const relativePath = path.relative(repoRoot, filePath).replace(/\\/gu, '/');
  const lines = fs.readFileSync(filePath, 'utf8').split(/\r?\n/u);

  lines.forEach((line, index) => {
    for (const { label, pattern } of mojibakeMarkers) {
      if (pattern.test(line)) {
        errors.push(
          `${relativePath}:${index + 1} contains likely mojibake marker: ${label}`,
        );
      }
    }

    if (
      localizedKeyPattern.test(line) &&
      suspiciousQuestionMarkPattern.test(line) &&
      !isAllowedQuestionMarkLine(line)
    ) {
      errors.push(
        `${relativePath}:${index + 1} contains a suspicious question mark inside localized text`,
      );
    }
  });
}

function isAllowedQuestionMarkLine(line) {
  return (
    /\?\?|\?:|\?\.|https?:\/\//u.test(line) ||
    /password\?|forgot|question|query|searchParams|URLSearchParams/iu.test(line)
  );
}

function walkRepo(currentDir) {
  const entries = fs.readdirSync(currentDir, { withFileTypes: true });
  const files = [];

  for (const entry of entries) {
    if (excludedDirectoryNames.has(entry.name)) {
      continue;
    }

    const fullPath = path.join(currentDir, entry.name);
    if (entry.isDirectory()) {
      files.push(...walkRepo(fullPath));
      continue;
    }

    if (isTextLikeFile(fullPath) || entry.name === 'Dockerfile') {
      files.push(fullPath);
    }
  }

  return files;
}

function isTextLikeFile(filePath) {
  return textLikeFilePattern.test(filePath);
}

main();
