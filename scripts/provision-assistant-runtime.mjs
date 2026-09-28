#!/usr/bin/env node
// Idempotent provisioning of the Assistant RLS runtime role (Wave 1.1).
//
// WHY THIS EXISTS
// Flyway V84 deliberately creates `campuscore_assistant_runtime` NOLOGIN and
// never sets a password ("password provisioning is a separate secret-safe
// operation"). Before this script nobody owned that operation, so every fresh
// environment failed: CI Compose and the e2e stack died on V84 itself because
// the migration targets the Supabase-managed `postgres` role that a vanilla
// Compose bootstrap (POSTGRES_USER=campuscore) never creates, and even past
// that, the API could not open the assistant pool without a password.
//
// WHAT IT DOES (idempotent — safe to re-run any number of times)
//   1. Creates the bootstrap `postgres` role (NOLOGIN, no privileges) when it
//      is missing. Supabase always has it, so this is a Compose/e2e-only shim
//      that lets V84's `ALTER DEFAULT PRIVILEGES FOR ROLE postgres` run. The
//      role has no login and no privileges, so it adds no attack surface.
//   2. Creates `campuscore_assistant_runtime` with the V84 attribute contract
//      when missing, or leaves the existing role untouched otherwise.
//   3. Ensures LOGIN + the ASSISTANT_DB_PASSWORD password ONLY when needed:
//      if the role can already log in, the script first tries to connect with
//      ASSISTANT_DB_PASSWORD and leaves the credential untouched on success.
//      A password reset happens only when the role is NOLOGIN or the probe
//      connection fails (unknown password).
//
// INPUT (environment)
//   ASSISTANT_DB_PASSWORD       required; at least 16 characters; fail-closed.
//   SPRING_DATASOURCE_URL       jdbc:postgresql://host:port/db[?params]
//   SPRING_DATASOURCE_USERNAME  primary/admin login (Compose: campuscore)
//   SPRING_DATASOURCE_PASSWORD  primary/admin password
//   PGHOST/PGPORT/PGDATABASE/PGUSER/PGPASSWORD   optional low-level override.
//
// OUTPUT
//   Counts and state names only — the script never prints any secret, and
//   database error messages are printed without connection parameters.
//
// It speaks the PostgreSQL wire protocol directly (SCRAM-SHA-256, MD5, or
// cleartext auth) so it stays dependency-free like the rest of scripts/ — CI
// runs `node --test scripts/...` with no npm install.

import crypto from 'node:crypto';
import net from 'node:net';

const RUNTIME_ROLE = 'campuscore_assistant_runtime';
const MIN_PASSWORD_LENGTH = 16;
const CONNECT_TIMEOUT_MS = 10_000;

function fail(message) {
  process.stderr.write(`provision-assistant-runtime: ${message}\n`);
  process.exit(1);
}

function requireEnv(name, env) {
  const value = env[name];
  if (value == null || value.trim() === '') {
    throw new ProvisioningError(`${name} is required`);
  }
  return value;
}

// ---------------------------------------------------------------------------
// Connection target resolution
// ---------------------------------------------------------------------------

export function parseJdbcUrl(jdbcUrl) {
  const prefix = 'jdbc:postgresql://';
  if (!jdbcUrl.startsWith(prefix)) {
    throw new ProvisioningError(
      'SPRING_DATASOURCE_URL must start with jdbc:postgresql://host:port/database');
  }
  let rest = jdbcUrl.slice(prefix.length);
  const queryIndex = rest.indexOf('?');
  if (queryIndex >= 0) rest = rest.slice(0, queryIndex);
  const slashIndex = rest.indexOf('/');
  if (slashIndex < 0) throw new ProvisioningError('SPRING_DATASOURCE_URL must end with /database');
  const hostPort = rest.slice(0, slashIndex);
  const database = rest.slice(slashIndex + 1).replace(/\/+$/, '');
  if (!database) throw new ProvisioningError('SPRING_DATASOURCE_URL must name a database');
  const colonIndex = hostPort.lastIndexOf(':');
  let host = hostPort;
  let port = 5432;
  if (colonIndex > 0) {
    host = hostPort.slice(0, colonIndex);
    const maybePort = Number(hostPort.slice(colonIndex + 1));
    if (Number.isInteger(maybePort) && maybePort > 0 && maybePort < 65536) port = maybePort;
  }
  return { host, port, database };
}

function resolveTarget(env) {
  if (env.PGHOST && env.PGPORT && env.PGDATABASE && env.PGUSER) {
    return {
      host: env.PGHOST,
      port: Number(env.PGPORT),
      database: env.PGDATABASE,
      user: env.PGUSER,
      password: env.PGPASSWORD ?? '',
    };
  }
  const parsed = parseJdbcUrl(requireEnv('SPRING_DATASOURCE_URL', env));
  return {
    host: parsed.host,
    port: parsed.port,
    database: parsed.database,
    user: requireEnv('SPRING_DATASOURCE_USERNAME', env),
    password: requireEnv('SPRING_DATASOURCE_PASSWORD', env),
  };
}

function sqlLiteral(value) {
  return `'${String(value).replace(/'/g, "''")}'`;
}

// ---------------------------------------------------------------------------
// Minimal PostgreSQL wire client (extended-free, simple query protocol)
// ---------------------------------------------------------------------------

class PostgresError extends Error {
  constructor(fields) {
    super(`PostgreSQL ${fields.C ?? 'ERROR'}: ${fields.M ?? 'unknown error'}`);
    this.fields = fields;
  }
}

/** A provisioning precondition or database failure; the CLI turns it into exit 1. */
export class ProvisioningError extends Error { }

export class WireClient {
  constructor(target) {
    this.target = target;
    this.socket = null;
    this.buffer = Buffer.alloc(0);
    this.wake = null;
    this.failure = null;
    this.closed = false;
  }

  // Resolves each time new bytes arrive; the caller re-parses the buffer.
  #awaitData() {
    return new Promise((resolve, reject) => {
      this.wake = { resolve, reject };
    });
  }

  #notify() {
    const waiter = this.wake;
    this.wake = null;
    if (waiter) waiter.resolve();
  }

  async connect(authPassword) {
    const socket = net.createConnection({ host: this.target.host, port: this.target.port });
    socket.setTimeout(CONNECT_TIMEOUT_MS);
    this.socket = socket;
    await new Promise((resolve, reject) => {
      const onTimeout = () => {
        socket.destroy();
        reject(new Error(`connection to ${this.target.host}:${this.target.port} timed out`));
      };
      socket.once('timeout', onTimeout);
      socket.once('error', (error) => reject(new Error(`connection failed: ${error.message}`)));
      socket.once('connect', () => {
        socket.off('timeout', onTimeout);
        resolve();
      });
    });
    socket.on('data', (chunk) => {
      this.buffer = Buffer.concat([this.buffer, chunk]);
      this.#notify();
    });
    socket.on('error', (error) => {
      this.failure = new Error(`connection error: ${error.message}`);
      this.#notify();
    });
    socket.on('close', () => {
      this.closed = true;
      this.#notify();
    });

    this.#send(startupMessage(this.target.user, this.target.database));
    await this.#authenticate(authPassword);
  }

  #send(message) {
    this.socket.write(message);
  }

  // Pops the next meaningful message off the wire. Notices and parameter
  // status updates are noise and are skipped; anything else is returned in
  // order, so handshake and query phases stay strictly sequential.
  async #readMessage() {
    for (;;) {
      for (;;) {
        if (this.failure) throw this.failure;
        let complete = false;
        if (this.buffer.length >= 5) {
          const length = this.buffer.readUInt32BE(1);
          complete = length >= 4 && this.buffer.length >= 1 + length;
        }
        if (complete) break;
        if (this.closed) {
          throw new Error('connection closed while waiting for a database message');
        }
        await this.#awaitData();
      }
      const type = String.fromCharCode(this.buffer[0]);
      const length = this.buffer.readUInt32BE(1);
      const body = this.buffer.subarray(5, 1 + length);
      this.buffer = this.buffer.subarray(1 + length);
      if (type === 'E') throw new PostgresError(parseFields(body));
      if (type === 'N' || type === 'S') continue;
      return { type, body };
    }
  }

  async #authenticate(password) {
    for (;;) {
      const message = await this.#readMessage();
      if (message.type === 'Z') return; // ReadyForQuery: handshake complete
      if (message.type !== 'R') continue; // ParameterStatus / BackendKeyData noise
      const code = message.body.readUInt32BE(0);
      if (code === 0) continue; // authentication ok
      if (code === 3) {
        this.#send(passwordMessage(password));
        continue;
      }
      if (code === 5) {
        const salt = message.body.subarray(4, 8);
        const inner = crypto.createHash('md5').update(password + this.target.user).digest('hex');
        const digest = crypto.createHash('md5').update(inner).update(salt).digest('hex');
        this.#send(passwordMessage('md5' + digest));
        continue;
      }
      if (code === 10) {
        const mechanisms = message.body.subarray(4).toString('utf8').split('\0').filter(Boolean);
        if (!mechanisms.includes('SCRAM-SHA-256')) {
          throw new Error(`server offers no supported SASL mechanism (${mechanisms.join(', ')})`);
        }
        const clientNonceValue = clientNonce();
        this.#send(saslInitialMessage(`n,,n=,r=${clientNonceValue}`));
        const serverFirst = await this.#readMessage();
        if (serverFirst.type !== 'R' || serverFirst.body.readUInt32BE(0) !== 11) {
          throw new Error('unexpected SASL continuation from the server');
        }
        const serverFirstMessage = serverFirst.body.subarray(4).toString('utf8');
        const parts = Object.fromEntries(
          serverFirstMessage.split(',').map((piece) => [piece.slice(0, 1), piece.slice(2)]));
        const clientFirstBare = `n=,r=${clientNonceValue}`;
        const clientFinalWithoutProof = 'c=biws,r=' + parts.r;
        const authMessage = `${clientFirstBare},${serverFirstMessage},${clientFinalWithoutProof}`;
        const saltedPassword = crypto.pbkdf2Sync(
          password, Buffer.from(parts.s, 'base64'), Number(parts.i), 32, 'sha256');
        const clientKey = crypto.createHmac('sha256', saltedPassword).update('Client Key').digest();
        const storedKey = crypto.createHash('sha256').update(clientKey).digest();
        const clientSignature = crypto.createHmac('sha256', storedKey).update(authMessage).digest();
        const proof = Buffer.alloc(clientKey.length);
        for (let i = 0; i < clientKey.length; i += 1) proof[i] = clientKey[i] ^ clientSignature[i];
        this.#send(saslResponseMessage(`${clientFinalWithoutProof},p=${proof.toString('base64')}`));
        const serverFinal = await this.#readMessage();
        if (serverFinal.type !== 'R' || serverFinal.body.readUInt32BE(0) !== 12) {
          throw new Error('unexpected SASL final from the server');
        }
        continue;
      }
      throw new Error(`unsupported PostgreSQL authentication method (code ${code})`);
    }
  }

  // Runs one or more statements via the simple protocol; rejects on the first
  // server error. Returns the text rows of the LAST result sequence, which is
  // all this script needs from its SELECTs.
  async query(sql) {
    let rows = [];
    const payload = Buffer.from(sql, 'utf8');
    const head = Buffer.alloc(5);
    head[0] = 0x51; // 'Q' — simple Query
    head.writeUInt32BE(4 + payload.length + 1, 1);
    this.#send(Buffer.concat([head, payload, Buffer.from([0])]));
    for (;;) {
      const message = await this.#readMessage();
      if (message.type === 'T') rows = [];
      if (message.type === 'D') rows.push(parseDataRow(message.body));
      if (message.type === 'Z') return rows;
    }
  }

  close() {
    if (this.socket && !this.closed) {
      try { this.socket.end(Buffer.from([0x58, 0, 0, 0, 4])); } catch { /* already gone */ }
      this.socket.destroy();
    }
    this.socket = null;
  }
}

function clientNonce() {
  return crypto.randomBytes(18).toString('base64');
}

function startupMessage(user, database) {
  const payload = Buffer.from(`user\0${user}\0database\0${database}\0\0`, 'utf8');
  const head = Buffer.alloc(8);
  head.writeUInt32BE(8 + payload.length, 0);
  head.writeUInt32BE(196608, 4); // protocol 3.0
  return Buffer.concat([head, payload]);
}

function passwordMessage(password) {
  const body = Buffer.from(password, 'utf8');
  const head = Buffer.alloc(5);
  head[0] = 0x70; // 'p' — PasswordMessage
  head.writeUInt32BE(4 + body.length + 1, 1);
  return Buffer.concat([head, body, Buffer.from([0])]);
}

function saslInitialMessage(initial) {
  const mechanism = Buffer.from('SCRAM-SHA-256\0', 'utf8');
  const response = Buffer.from(initial, 'utf8');
  const body = Buffer.alloc(4);
  body.writeUInt32BE(response.length, 0);
  const head = Buffer.alloc(5);
  head[0] = 0x70;
  head.writeUInt32BE(4 + mechanism.length + 4 + response.length, 1);
  return Buffer.concat([head, mechanism, body, response]);
}

function saslResponseMessage(final) {
  const body = Buffer.from(final, 'utf8');
  const head = Buffer.alloc(5);
  head[0] = 0x70;
  head.writeUInt32BE(4 + body.length, 1);
  return Buffer.concat([head, body]);
}

function parseFields(body) {
  const fields = {};
  let offset = 0;
  for (;;) {
    if (offset >= body.length) break;
    const code = String.fromCharCode(body[offset]);
    if (code === '\0') break;
    const start = offset + 1;
    let end = body.indexOf(0, start);
    if (end < 0) end = body.length;
    fields[code] = body.subarray(start, end).toString('utf8');
    offset = end + 1;
  }
  return fields;
}

function parseDataRow(body) {
  const columnCount = body.readUInt16BE(0);
  const row = [];
  let offset = 2;
  for (let i = 0; i < columnCount; i += 1) {
    const length = body.readInt32BE(offset);
    offset += 4;
    if (length === -1) {
      row.push(null);
      continue;
    }
    row.push(body.subarray(offset, offset + length).toString('utf8'));
    offset += length;
  }
  return row;
}

// ---------------------------------------------------------------------------
// Provisioning steps
// ---------------------------------------------------------------------------

async function connectAs(target, password) {
  const client = new WireClient(target);
  await client.connect(password);
  await client.query('SELECT 1');
  return client;
}

export async function provision(env = process.env, log = defaultLog) {
  const assistantPassword = env.ASSISTANT_DB_PASSWORD;
  if (assistantPassword == null || assistantPassword.trim() === '') {
    throw new ProvisioningError('ASSISTANT_DB_PASSWORD is required');
  }
  if (assistantPassword.length < MIN_PASSWORD_LENGTH) {
    throw new ProvisioningError(
      `ASSISTANT_DB_PASSWORD must be at least ${MIN_PASSWORD_LENGTH} characters`);
  }
  log('ASSISTANT_DB_PASSWORD present: yes');

  const target = resolveTarget(env);
  const admin = await connectAs(target, target.password);
  try {
    const roles = await admin.query(
      `SELECT rolname, rolcanlogin FROM pg_roles WHERE rolname IN ('postgres', '${RUNTIME_ROLE}')`);
    const bootstrap = roles.find((row) => row[0] === 'postgres');
    const runtime = roles.find((row) => row[0] === RUNTIME_ROLE);

    if (!bootstrap) {
      await admin.query(
        'CREATE ROLE postgres NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOREPLICATION NOBYPASSRLS');
      log('bootstrap_role_postgres: created (NOLOGIN, no privileges)');
    } else {
      log('bootstrap_role_postgres: exists');
    }

    if (!runtime) {
      await admin.query(
        `CREATE ROLE ${RUNTIME_ROLE} LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT `
        + 'NOREPLICATION NOBYPASSRLS CONNECTION LIMIT 8 '
        + `PASSWORD ${sqlLiteral(assistantPassword)}`);
      log('runtime_role: created');
      log('runtime_role_login: enabled-now');
      log('runtime_role_password: provisioned');
      return;
    }

    if (runtime[1] !== 't') {
      await admin.query(
        `ALTER ROLE ${RUNTIME_ROLE} LOGIN PASSWORD ${sqlLiteral(assistantPassword)}`);
      log('runtime_role: existing');
      log('runtime_role_login: enabled-now');
      log('runtime_role_password: reset (role was NOLOGIN)');
      return;
    }

    // Login already enabled — probe with the supplied password so an operator
    // rotation elsewhere is never silently clobbered by a re-run.
    try {
      const probe = await connectAs({ ...target, user: RUNTIME_ROLE }, assistantPassword);
      probe.close();
      log('runtime_role: existing');
      log('runtime_role_login: enabled-already');
      log('runtime_role_password: verified (unchanged)');
    } catch {
      await admin.query(
        `ALTER ROLE ${RUNTIME_ROLE} LOGIN PASSWORD ${sqlLiteral(assistantPassword)}`);
      log('runtime_role: existing');
      log('runtime_role_login: enabled-already');
      log('runtime_role_password: reset (probe connection failed)');
    }
  } finally {
    admin.close();
  }
}

function defaultLog(line) {
  process.stdout.write(`${line}\n`);
}

// ---------------------------------------------------------------------------
// CLI entry point
// ---------------------------------------------------------------------------

const invokedDirectly = process.argv[1] != null
  && process.argv[1].replace(/\\/g, '/').endsWith('provision-assistant-runtime.mjs');

if (invokedDirectly) {
  provision().catch((error) => {
    if (error instanceof PostgresError) {
      fail(`database rejected provisioning (${error.fields.C}): ${error.fields.M ?? 'unknown error'}`);
    }
    fail(error instanceof ProvisioningError || error instanceof Error
      ? error.message
      : String(error));
  });
}
