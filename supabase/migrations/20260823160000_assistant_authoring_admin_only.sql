-- Authoring is administrative work; the Java API remains the runtime authority.
drop policy if exists "Assistant staff can read authoring rows" on assistant.knowledge_document;
drop policy if exists "Assistant staff can create authoring rows" on assistant.knowledge_document;
drop policy if exists "Assistant staff can update authoring rows" on assistant.knowledge_document;
drop policy if exists "Assistant staff can delete authoring rows" on assistant.knowledge_document;
drop policy if exists "assistant_knowledge_staff_select" on assistant.knowledge_document;
drop policy if exists "assistant_knowledge_staff_insert" on assistant.knowledge_document;
drop policy if exists "assistant_knowledge_staff_update" on assistant.knowledge_document;
drop policy if exists "assistant_knowledge_staff_delete" on assistant.knowledge_document;

create policy "Assistant admins can read authoring rows"
    on assistant.knowledge_document for select to authenticated
    using (
        upper(coalesce(((select auth.jwt()) -> 'app_metadata' ->> 'role'), '')) in ('ADMIN', 'SUPER_ADMIN')
        or coalesce(((select auth.jwt()) -> 'app_metadata' -> 'roles'), '[]'::jsonb) ?|
            array['ADMIN', 'SUPER_ADMIN']
    );

create policy "Assistant admins can create authoring rows"
    on assistant.knowledge_document for insert to authenticated
    with check (
        upper(coalesce(((select auth.jwt()) -> 'app_metadata' ->> 'role'), '')) in ('ADMIN', 'SUPER_ADMIN')
        or coalesce(((select auth.jwt()) -> 'app_metadata' -> 'roles'), '[]'::jsonb) ?|
            array['ADMIN', 'SUPER_ADMIN']
    );

create policy "Assistant admins can update authoring rows"
    on assistant.knowledge_document for update to authenticated
    using (
        upper(coalesce(((select auth.jwt()) -> 'app_metadata' ->> 'role'), '')) in ('ADMIN', 'SUPER_ADMIN')
        or coalesce(((select auth.jwt()) -> 'app_metadata' -> 'roles'), '[]'::jsonb) ?|
            array['ADMIN', 'SUPER_ADMIN']
    )
    with check (
        upper(coalesce(((select auth.jwt()) -> 'app_metadata' ->> 'role'), '')) in ('ADMIN', 'SUPER_ADMIN')
        or coalesce(((select auth.jwt()) -> 'app_metadata' -> 'roles'), '[]'::jsonb) ?|
            array['ADMIN', 'SUPER_ADMIN']
    );

create policy "Assistant admins can delete authoring rows"
    on assistant.knowledge_document for delete to authenticated
    using (
        upper(coalesce(((select auth.jwt()) -> 'app_metadata' ->> 'role'), '')) in ('ADMIN', 'SUPER_ADMIN')
        or coalesce(((select auth.jwt()) -> 'app_metadata' -> 'roles'), '[]'::jsonb) ?|
            array['ADMIN', 'SUPER_ADMIN']
    );
