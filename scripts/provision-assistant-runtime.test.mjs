// Unit tests for scripts/provision-assistant-runtime.mjs — the fail-closed
// validation and URL parsing. The wire-protocol paths are exercised against a
// real PostgreSQL by the Compose bootstrap (docker-compose.yml) on every CI
// compose run; this file keeps those runs honest about the preconditions.
import test from 'node:test';
import assert from 'node:assert/strict';

import { parseJdbcUrl, provision } from './provision-assistant-runtime.mjs';

test('parseJdbcUrl reads host, port, and database from the Spring URL', () => {
  assert.deepEqual(
    parseJdbcUrl('jdbc:postgresql://db.ref.supabase.co:6543/postgres?pgbouncer=true'),
    { host: 'db.ref.supabase.co', port: 6543, database: 'postgres' });
  assert.deepEqual(
    parseJdbcUrl('jdbc:postgresql://postgres:5432/campuscore_restful_e2e?currentSchema=thesis'),
    { host: 'postgres', port: 5432, database: 'campuscore_restful_e2e' });
  assert.deepEqual(
    parseJdbcUrl('jdbc:postgresql://127.0.0.1/campuscore'),
    { host: '127.0.0.1', port: 5432, database: 'campuscore' });
});

test('parseJdbcUrl rejects non-PostgreSQL and database-less URLs', () => {
  assert.throws(() => parseJdbcUrl('jdbc:mysql://host/db'), /jdbc:postgresql/);
  assert.throws(() => parseJdbcUrl('jdbc:postgresql://host-without-db'), /database/);
});

test('provision fails closed without ASSISTANT_DB_PASSWORD', async () => {
  await assert.rejects(
    provision({ SPRING_DATASOURCE_URL: 'jdbc:postgresql://h/db', SPRING_DATASOURCE_USERNAME: 'u', SPRING_DATASOURCE_PASSWORD: 'p' }),
    /ASSISTANT_DB_PASSWORD is required/);
});

test('provision fails closed on a too-short ASSISTANT_DB_PASSWORD', async () => {
  await assert.rejects(
    provision({ ASSISTANT_DB_PASSWORD: 'only-15-charss', SPRING_DATASOURCE_URL: 'jdbc:postgresql://h/db', SPRING_DATASOURCE_USERNAME: 'u', SPRING_DATASOURCE_PASSWORD: 'p' }),
    /at least 16 characters/);
});

test('provision fails closed without a primary database connection', async () => {
  await assert.rejects(
    provision({ ASSISTANT_DB_PASSWORD: 'long-enough-password-123456' }),
    /SPRING_DATASOURCE_URL is required/);
});
