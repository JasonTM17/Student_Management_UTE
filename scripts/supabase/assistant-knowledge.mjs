#!/usr/bin/env node

import { mkdir, readFile, writeFile } from 'node:fs/promises';
import path from 'node:path';

const DEFAULT_TABLE = 'assistant.knowledge_document';
const FIELDS = [
  'id',
  'slug',
  'title',
  'content',
  'locale',
  'source',
  'active',
  'visibility',
  'priority',
  'updated_at',
];
const LOCALES = new Set(['vi', 'en', 'both']);
const VISIBILITIES = new Set(['PUBLIC', 'STAFF']);
const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const SLUG_PATTERN = /^[a-z0-9][a-z0-9-]*$/;

function usage() {
  return `Usage:
  node scripts/supabase/assistant-knowledge.mjs validate --file <corpus.json> [--expected-count <n>]
  node scripts/supabase/assistant-knowledge.mjs import --file <corpus.json> [--dry-run]
  node scripts/supabase/assistant-knowledge.mjs export --out <file> [--format json|flyway-sql]

Environment for network commands (server-side only):
  SUPABASE_URL                  Project URL, for example https://<ref>.supabase.co
  SUPABASE_SERVICE_ROLE_KEY     Server-only key; never put it in client or source control
  SUPABASE_TABLE                Qualified table; defaults to assistant.knowledge_document
`;
}

function parseArgs(argv) {
  const [command, ...rest] = argv;
  const options = { command };
  for (let index = 0; index < rest.length; index += 1) {
    const token = rest[index];
    if (token === '--dry-run') {
      options.dryRun = true;
      continue;
    }
    if (!token.startsWith('--')) {
      throw new Error(`Unexpected argument: ${token}`);
    }
    const separator = token.indexOf('=');
    const key = token.slice(2, separator === -1 ? undefined : separator);
    const value = separator === -1 ? rest[++index] : token.slice(separator + 1);
    if (!value || value.startsWith('--')) {
      throw new Error(`Missing value for --${key}`);
    }
    options[key] = value;
  }
  return options;
}

function requireOption(options, name) {
  const value = options[name];
  if (!value) {
    throw new Error(`Missing required option --${name}`);
  }
  return value;
}

function readExpectedCount(options) {
  if (options['expected-count'] === undefined) {
    return undefined;
  }
  const count = Number(options['expected-count']);
  if (!Number.isInteger(count) || count < 0) {
    throw new Error('--expected-count must be a non-negative integer');
  }
  return count;
}

async function readCorpus(file) {
  let parsed;
  try {
    parsed = JSON.parse(await readFile(file, 'utf8'));
  } catch (error) {
    throw new Error(`Could not read JSON corpus: ${error instanceof Error ? error.message : 'invalid JSON'}`);
  }
  const documents = Array.isArray(parsed) ? parsed : parsed?.documents;
  if (!Array.isArray(documents)) {
    throw new Error('Corpus must be a JSON array or an object with a documents array');
  }
  return validateDocuments(documents);
}

function validateDocuments(documents) {
  const seenSlugs = new Set();
  const seenIds = new Set();
  const normalized = documents.map((document, index) => {
    if (!document || typeof document !== 'object' || Array.isArray(document)) {
      throw new Error(`Document ${index + 1} must be an object`);
    }
    const id = String(document.id ?? '');
    const slug = String(document.slug ?? '');
    const title = String(document.title ?? '');
    const content = String(document.content ?? '');
    const locale = String(document.locale ?? '');
    const source = String(document.source ?? '');
    const active = document.active ?? true;
    const visibility = String(document.visibility ?? 'PUBLIC').toUpperCase();
    const priority = document.priority ?? 100;
    const updatedAt = document.updated_at;

    if (!UUID_PATTERN.test(id)) throw new Error(`Document ${index + 1} has an invalid id`);
    if (!SLUG_PATTERN.test(slug) || slug.length > 160) throw new Error(`Document ${index + 1} has an invalid slug`);
    if (!title.trim() || title.length > 500) throw new Error(`Document ${index + 1} has an invalid title`);
    if (!content.trim()) throw new Error(`Document ${index + 1} has empty content`);
    if (!LOCALES.has(locale)) throw new Error(`Document ${index + 1} has an invalid locale`);
    if (!source.trim() || source.length > 200) throw new Error(`Document ${index + 1} has an invalid source`);
    if (typeof active !== 'boolean') throw new Error(`Document ${index + 1} has an invalid active flag`);
    if (!VISIBILITIES.has(visibility)) throw new Error(`Document ${index + 1} has an invalid visibility`);
    if (!Number.isInteger(priority) || priority < 1 || priority > 1000) throw new Error(`Document ${index + 1} has an invalid priority`);
    if (typeof updatedAt !== 'string' || !updatedAt.trim()) throw new Error(`Document ${index + 1} has an invalid updated_at`);
    if (Number.isNaN(Date.parse(updatedAt))) throw new Error(`Document ${index + 1} has an invalid updated_at`);

    if (seenSlugs.has(slug)) throw new Error(`Duplicate slug: ${slug}`);
    if (seenIds.has(id)) throw new Error(`Duplicate id: ${id}`);
    seenSlugs.add(slug);
    seenIds.add(id);
    return { id, slug, title, content, locale, source, active, visibility, priority, updated_at: new Date(updatedAt).toISOString() };
  });
  return normalized.sort((left, right) =>
    `${left.locale}\u0000${left.slug}\u0000${left.id}`.localeCompare(`${right.locale}\u0000${right.slug}\u0000${right.id}`));
}

function requireSupabaseConfig() {
  const baseUrl = process.env.SUPABASE_URL;
  const serviceRoleKey = process.env.SUPABASE_SERVICE_ROLE_KEY;
  if (!baseUrl || !serviceRoleKey) {
    throw new Error('SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY are required for network commands');
  }
  let projectUrl;
  try {
    projectUrl = new URL(baseUrl);
  } catch {
    throw new Error('SUPABASE_URL must be an absolute URL');
  }
  if (!['http:', 'https:'].includes(projectUrl.protocol)) {
    throw new Error('SUPABASE_URL must use http or https');
  }
  const table = process.env.SUPABASE_TABLE ?? DEFAULT_TABLE;
  const tableParts = table.split('.');
  if (tableParts.length !== 2 || tableParts.some((part) => !/^[a-z_][a-z0-9_]*$/.test(part))) {
    throw new Error('SUPABASE_TABLE must be a qualified lowercase schema.table name');
  }
  return {
    baseUrl: projectUrl.toString().replace(/\/$/, ''),
    serviceRoleKey,
    schema: tableParts[0],
    table: tableParts[1],
  };
}

async function supabaseRequest(config, method, url, body) {
  const response = await fetch(url, {
    method,
    headers: {
      apikey: config.serviceRoleKey,
      Authorization: `Bearer ${config.serviceRoleKey}`,
      ...(config.schema !== 'public' ? {
        'accept-profile': config.schema,
        'content-profile': config.schema,
      } : {}),
      ...(body ? { 'content-type': 'application/json' } : {}),
      ...(method === 'POST' ? { Prefer: 'resolution=merge-duplicates,return=minimal' } : {}),
    },
    ...(body ? { body: JSON.stringify(body) } : {}),
  });
  if (!response.ok) {
    throw new Error(`Supabase request failed with HTTP ${response.status}`);
  }
  return response;
}

async function importCorpus(options) {
  const documents = await readCorpus(requireOption(options, 'file'));
  if (options.dryRun) {
    console.log(`DRY_RUN valid: ${documents.length} document(s), no Supabase request made`);
    return;
  }
  const config = requireSupabaseConfig();
  const endpoint = `${config.baseUrl}/rest/v1/${config.table}?on_conflict=slug`;
  for (let index = 0; index < documents.length; index += 500) {
    await supabaseRequest(config, 'POST', endpoint, documents.slice(index, index + 500));
  }
  console.log(`Imported ${documents.length} document(s) into ${config.schema}.${config.table}`);
}

async function exportCorpus(options) {
  const config = requireSupabaseConfig();
  const documents = [];
  for (let offset = 0; ; offset += 1000) {
    const endpoint = new URL(`${config.baseUrl}/rest/v1/${config.table}`);
    endpoint.searchParams.set('select', FIELDS.join(','));
    endpoint.searchParams.set('order', 'slug.asc,id.asc');
    endpoint.searchParams.set('limit', '1000');
    endpoint.searchParams.set('offset', String(offset));
    const response = await supabaseRequest(config, 'GET', endpoint);
    const page = await response.json();
    if (!Array.isArray(page)) throw new Error('Supabase returned an unexpected corpus response');
    documents.push(...page);
    if (page.length < 1000) break;
  }
  const normalized = validateDocuments(documents);
  const format = options.format ?? 'json';
  const output = format === 'json' ? JSON.stringify(normalized, null, 2) + '\n' : format === 'flyway-sql' ? toFlywaySql(normalized) : undefined;
  if (output === undefined) throw new Error('--format must be json or flyway-sql');
  const outputFile = requireOption(options, 'out');
  await mkdir(path.dirname(path.resolve(outputFile)), { recursive: true });
  await writeFile(outputFile, output, 'utf8');
  console.log(`Exported ${normalized.length} document(s) as ${format}`);
}

function sqlString(value) {
  return `'${String(value).replaceAll("'", "''")}'`;
}

function toFlywaySql(documents) {
  const lines = [
    '-- Generated by scripts/supabase/assistant-knowledge.mjs.',
    '-- Review this file before adding it to the Java Flyway seed workflow.',
    '',
  ];
  for (const document of documents) {
    lines.push(
      `INSERT INTO assistant.knowledge_document (id, slug, locale, title, content, source, priority, active, visibility, updated_at)`,
      `VALUES (${sqlString(document.id)}::uuid, ${sqlString(document.slug)}, ${sqlString(document.locale)}, ${sqlString(document.title)}, ${sqlString(document.content)}, ${sqlString(document.source)}, ${document.priority}, ${document.active ? 'TRUE' : 'FALSE'}, ${sqlString(document.visibility)}, ${sqlString(document.updated_at)}::timestamptz)`,
      `ON CONFLICT (slug) DO UPDATE SET locale = EXCLUDED.locale, title = EXCLUDED.title, content = EXCLUDED.content, source = EXCLUDED.source, priority = EXCLUDED.priority, active = EXCLUDED.active, visibility = EXCLUDED.visibility, updated_at = EXCLUDED.updated_at;`,
      '',
    );
  }
  return lines.join('\n');
}

async function validateCorpus(options) {
  const documents = await readCorpus(requireOption(options, 'file'));
  const expectedCount = readExpectedCount(options);
  if (expectedCount !== undefined && documents.length !== expectedCount) {
    throw new Error(`Expected ${expectedCount} document(s), found ${documents.length}`);
  }
  console.log(`VALID: ${documents.length} document(s), no duplicate slugs`);
}

async function main() {
  const options = parseArgs(process.argv.slice(2));
  if (!['validate', 'import', 'export'].includes(options.command)) {
    console.error(usage());
    process.exitCode = 2;
    return;
  }
  if (options.command === 'validate') await validateCorpus(options);
  if (options.command === 'import') await importCorpus(options);
  if (options.command === 'export') await exportCorpus(options);
}

main().catch((error) => {
  console.error(`assistant knowledge command failed: ${error instanceof Error ? error.message : 'unknown error'}`);
  process.exitCode = 1;
});
