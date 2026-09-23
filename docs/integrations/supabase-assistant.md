# Assistant knowledge authority and Supabase

The assistant answers from the active published release in the Java application's PostgreSQL `assistant` schema. `knowledge_runtime_state.active_release_id` selects an immutable snapshot. The authoring table alone is not served. Public chatbot requests never query Supabase directly.

`assistant.knowledge.authority-mode` selects who may change that active release:

- `sql`: the Java two-admin review flow publishes or archives a document and promotes the matching local snapshot in the same database transaction.
- `supabase`: the two-admin authority flow publishes or archives a release in Supabase. Java then synchronizes that release to the local runtime. A failed projection is reported as pending or degraded; an authoring acknowledgement is not proof that the chatbot serves the change.

Do not edit `assistant.knowledge_document` directly with a service key. Direct upsert bypasses the two-admin review and release pointer. The import utility rejects that governed table. Use the admin workflow for changes to live knowledge.

## Schema and rollout

For a new Supabase project, apply the assistant authoring and release migrations in timestamp order. Existing projects should apply the new additive migrations:

- `supabase/migrations/20260923180000_specialized_knowledge_domain.sql` admits `SPECIALIZED` in authoring, revision, and release constraints and the public knowledge validator.
- `supabase/migrations/20260923181500_archive_knowledge_release.sql` creates a published tombstone release when an active document is archived. Java synchronization can then remove that document from retrieval.

Apply these before enabling the corresponding Java version in `supabase` authority mode. Verify the target project and migration state before any remote change. No remote Supabase migration or production cutover is performed by the local tests in this repository.

The `assistant` schema must be exposed to the Supabase Data API for the authority gateway. Keep service credentials on the server. RLS, service grants, and project settings require verification against the actual target project.

## Specialized corpus

`supabase/seed/assistant-specialized-knowledge.json` contains 24 Vietnamese and English software engineering documents. They use `domain = 'SPECIALIZED'` in the Java runtime and the remote release projection. The local initial seed is `V71__specialized_domain_knowledge.sql`. An applied Flyway migration is immutable; the generator refuses to overwrite V71. The wording correction for one public safety lesson is in additive `V78__repair_specialized_public_knowledge_guard.sql`, which also promotes a matching local release when the local SQL authority owns the pointer.

Validate the source offline:

```powershell
node scripts/supabase/assistant-knowledge.mjs validate `
  --file .\supabase\seed\assistant-specialized-knowledge.json --expected-count 24
node --test scripts/supabase/assistant-knowledge.test.mjs
```

The validation checks count, identifiers, locales, and duplicate slugs. Java's `V71SpecializedKnowledgeMigrationTest` additionally checks all public seed content against the assistant input guard.

## Verification boundary

The SQL fixture in `supabase/tests/` exercises the two new migrations on a disposable PostgreSQL schema, including a safe specialized lesson, a rejected credential assignment, and an archived release snapshot. It is not an authenticated Supabase project test. Java's PostgreSQL test exercises local release promotion and rollback in a disposable database. A live remote project still needs an authorized migration, two-admin publish and archive walkthrough, successful Java synchronization, and a chatbot retrieval check before release acceptance.
