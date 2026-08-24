import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const files = ['README.md', 'README.en.md', 'README.vi.md', 'docs/ARCHITECTURE.md', 'docs/RELEASE.md'];

const errors = [];
for (const relative of files) {
  const full = path.join(root, relative);
  if (!fs.existsSync(full)) {
    errors.push(`${relative} is missing`);
    continue;
  }

  const source = fs.readFileSync(full, 'utf8');
  // Match the characteristic multi-character sequences produced when UTF-8
  // bytes are decoded as Latin-1/Windows-1252.  A bare Vietnamese letter is
  // valid text, so it must not be treated as corruption on its own.
  if (/\uFFFD|(?:\u00C3|\u00C2)[\u0080-\u00BF]|\u00E2[\u0080-\u00BF\u20AC\u2122\u0153\u017E]/u.test(source)) {
    errors.push(`${relative} contains likely mojibake`);
  }
}

if (errors.length > 0) {
  errors.forEach((error) => console.error(`- ${error}`));
  process.exit(1);
}

console.log('Documentation hygiene guard passed.');
