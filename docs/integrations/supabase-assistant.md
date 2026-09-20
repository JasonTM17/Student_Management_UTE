# Optional Supabase assistant authoring

Supabase is an optional authoring mirror for the curated thesis assistant. The
Java application continues to read `assistant.knowledge_document` from the
local PostgreSQL database through Flyway. Supabase is not a runtime dependency,
does not receive public chatbot traffic, and is not a production cutover. The
active authoring project must be verified before any import/export; a Supabase
project containing a different application's tables is not evidence that the
CampusCore assistant corpus is present.

## Specialized corpus (Trợ lý chuyên sâu)

`supabase/seed/assistant-specialized-knowledge.json` is the bilingual
professional-domain corpus (software engineering expert Q&A: OOP/SOLID, SQL,
testing, Git, REST, architecture, React, DevOps, security, RAG, clean code,
careers). The Supabase authoring table has no `domain` column by design; when
the corpus is published into the local runtime through
`V71__specialized_domain_knowledge.sql`, every row is assigned
`domain = 'SPECIALIZED'`, which the assistant's specialized scope
(`ChatRequest.scope = 'specialized'`) narrows retrieval to.

Validate the corpus locally (no network, no keys):

```powershell
node scripts/supabase/assistant-knowledge.mjs validate `
  --file .\supabase\seed\assistant-specialized-knowledge.json --expected-count 24
```

The JSON corpus is the single source of truth. After editing it, regenerate
the Flyway seed (the generator reads the JSON and emits
`V71__specialized_domain_knowledge.sql`; it never touches the process
environment):

```powershell
node scripts/generate-specialized-knowledge.mjs
```

## Schema and access

Apply `supabase/migrations/20260823073842_assistant_knowledge_authoring.sql`
only after the controller verifies the intended Supabase project. The migration
is idempotent against the existing `assistant.knowledge_document` table and
creates no `public.assistant_knowledge_documents` duplicate. RLS and advisor
results must be checked again against that exact project after a migration; this
file remains the reproducible source for another project.

The migration ensures `assistant.knowledge_document` has the required `id`,
`slug`, `title`, `content`, `locale`, `source`, `active`, `visibility`,
`updated_at`, and `priority` columns, a globally unique `slug` key,
active/locale/visibility/priority indexes, and a generated `tsvector` plus GIN
index for lexical authoring search. It adds no seed rows and does not create a
second corpus authority.

RLS is enabled. `anon` has no table grant or policy. Authenticated users need
`auth.jwt() -> 'app_metadata' ->> 'role'` equal to `ADMIN` or `SUPER_ADMIN`
for select/insert/update/delete after applying the admin-only policy migration.
Authorization must be stored in trusted
`app_metadata`, never editable user metadata. The importer uses
`SUPABASE_SERVICE_ROLE_KEY`, which bypasses RLS and must remain server-only.
Never place it in frontend code, `NEXT_PUBLIC_*`, a committed file, shell
history, or logs.

Because the table is in the custom `assistant` schema, the project must expose
that schema through the Supabase Data API before REST import/export can run.
The utility sends `Accept-Profile: assistant` and `Content-Profile: assistant`.
The controller must verify that project setting separately; the Data API
exposure setting was not changed automatically.

## Validate and import

The dependency-free utility is
`scripts/supabase/assistant-knowledge.mjs`. It validates required fields,
UUIDs, locale/visibility values, globally unique slugs, and deterministic
ordering before it makes a request. It never prints environment values or
response bodies.

Validate a JSON corpus locally. The current local Flyway seed contains 10
documents, so the initial transfer can use the count invariant:

```powershell
node scripts/supabase/assistant-knowledge.mjs validate `
  --file .\supabase\seed\assistant-knowledge.json --expected-count 10
```

Run a no-network import check first:

```powershell
node scripts/supabase/assistant-knowledge.mjs import `
  --file .\supabase\seed\assistant-knowledge.json --dry-run
```

For an actual authoring import, set the values only in the server process
environment and use the service key. Upsert identity is deterministic on
`slug`; bilingual variants use distinct locale-qualified slugs and input rows
are sorted before batching.

```powershell
$env:SUPABASE_URL = 'https://<project-ref>.supabase.co'
$env:SUPABASE_SERVICE_ROLE_KEY = '<server-only-service-role-key>'
$env:SUPABASE_TABLE = 'assistant.knowledge_document'
node scripts/supabase/assistant-knowledge.mjs import `
  --file .\supabase\seed\assistant-knowledge.json
Remove-Item Env:SUPABASE_SERVICE_ROLE_KEY
```

The placeholder above is documentation only. Do not commit or paste a real
key into this repository.

## Export to local Flyway

Export requires the same server-only variables because there is intentionally
no public read policy. JSON is useful as the reviewed authoring artifact:

```powershell
node scripts/supabase/assistant-knowledge.mjs export `
  --out .\assistant-knowledge.json --format json
```

To produce a reviewable SQL seed for the local Java Flyway workflow:

```powershell
node scripts/supabase/assistant-knowledge.mjs export `
  --out .\assistant-knowledge-from-supabase.sql --format flyway-sql
```

Review the generated SQL, then the controller may place it in a new Java
Flyway migration after checking the current migration version and local schema.
The generated SQL targets `assistant.knowledge_document`, uses the local
`slug` conflict key, and preserves `id`, locale, content, source, priority,
active, visibility, and `updated_at`. This is a source-authoring workflow;
local PostgreSQL plus Java Flyway remains the runtime authority.

## Verification

The local validation command above is the seed-count and duplicate `slug`
invariant for the checked-in authoring fixture. It should pass before import and
after export. Do not record a remote row count, RLS result, or advisor verdict
unless the exact intended project has been identified and the read-only check
has just been run against it. An authenticated Data API request still requires
a real staff token and should only be run when explicitly authorized; no service
key is stored locally.
