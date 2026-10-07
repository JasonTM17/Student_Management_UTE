// Migration hygiene gate: catches numbering and naming drift before the
// heavier compose job spins the full stack. Cheap enough for every push.
//   - filenames must match  V<digits>__<description>.sql
//   - version numbers must be unique (a duplicate would make Flyway pick one
//     silently depending on scan order)
//   - versions must be strictly positive integers
//   - every new file's version must exceed the highest baseline version when
//     MIGRATION_BASELINE is provided (prevents "inserted in the middle"
//     migrations that Flyway would skip on out-of-order=false installs)
import { readdirSync } from 'node:fs';
import { join } from 'node:path';

const DIR = process.argv[2]
  || 'java-services/restful-api/src/main/resources/db/migration';
const BASELINE = Number(process.env.MIGRATION_BASELINE || 0);

const NAME = /^V(\d+)__[^_].+\.sql$/;
const files = readdirSync(join(process.cwd(), DIR)).filter(f => f.endsWith('.sql'));

let failures = 0;
const fail = msg => { failures += 1; console.error(`FAIL  ${msg}`); };

const seen = new Map();
for (const file of files) {
  const match = NAME.exec(file);
  if (!match) {
    fail(`bad migration filename: ${file}`);
    continue;
  }
  const version = Number(match[1]);
  if (!Number.isInteger(version) || version <= 0) {
    fail(`non-positive version in ${file}`);
    continue;
  }
  if (seen.has(version)) {
    fail(`duplicate migration version V${version}: ${seen.get(version)} and ${file}`);
  }
  seen.set(version, file);
  if (version <= BASELINE) {
    fail(`V${version} (${file}) is not newer than baseline V${BASELINE}`);
  }
}

console.log(`checked ${files.length} migration files in ${DIR}`);
if (failures === 0) {
  console.log('MIGRATION NAMES PASS');
} else {
  process.exitCode = 1;
}
