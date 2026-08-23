-- Optional authoring support for the existing assistant.knowledge_document
-- corpus. This migration creates no second public corpus authority and adds
-- no seed rows. Java Flyway/local PostgreSQL remains the runtime authority.
create schema if not exists assistant;

create table if not exists assistant.knowledge_document (
    id uuid primary key default gen_random_uuid(),
    slug text not null,
    title text not null,
    content text not null,
    locale text not null,
    source text not null,
    active boolean not null default true,
    visibility text not null default 'PUBLIC',
    priority smallint not null default 100,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    search_tsv tsvector generated always as (
        to_tsvector(
            'simple',
            coalesce(title, '') || ' ' || coalesce(content, '') || ' ' || coalesce(source, '')
        )
    ) stored,
    constraint assistant_knowledge_locale_valid
        check (locale in ('vi', 'en', 'both')),
    constraint assistant_knowledge_visibility_valid
        check (visibility in ('PUBLIC', 'STAFF')),
    constraint assistant_knowledge_priority_valid
        check (priority between 1 and 1000),
    constraint assistant_knowledge_slug_locale_unique
        unique (slug, locale)
);

-- Existing controller-created rows may predate the authoring columns. Add only
-- missing columns so this remains safe to run against that same table.
alter table assistant.knowledge_document
    add column if not exists active boolean not null default true;

alter table assistant.knowledge_document
    add column if not exists visibility text not null default 'PUBLIC';

alter table assistant.knowledge_document
    add column if not exists priority smallint not null default 100;

alter table assistant.knowledge_document
    add column if not exists updated_at timestamptz not null default now();

alter table assistant.knowledge_document
    add column if not exists search_tsv tsvector generated always as (
        to_tsvector(
            'simple',
            coalesce(title, '') || ' ' || coalesce(content, '') || ' ' || coalesce(source, '')
        )
    ) stored;

do $$
begin
    if not exists (
        select 1
        from pg_constraint
        where conrelid = 'assistant.knowledge_document'::regclass
          and conname = 'assistant_knowledge_locale_valid'
    ) then
        alter table assistant.knowledge_document
            add constraint assistant_knowledge_locale_valid
            check (locale in ('vi', 'en', 'both'));
    end if;

    if not exists (
        select 1
        from pg_constraint
        where conrelid = 'assistant.knowledge_document'::regclass
          and conname = 'assistant_knowledge_visibility_valid'
    ) then
        alter table assistant.knowledge_document
            add constraint assistant_knowledge_visibility_valid
            check (visibility in ('PUBLIC', 'STAFF'));
    end if;

    if not exists (
        select 1
        from pg_constraint
        where conrelid = 'assistant.knowledge_document'::regclass
          and conname = 'assistant_knowledge_priority_valid'
    ) then
        alter table assistant.knowledge_document
            add constraint assistant_knowledge_priority_valid
            check (priority between 1 and 1000);
    end if;

    if not exists (
        select 1
        from pg_constraint
        where conrelid = 'assistant.knowledge_document'::regclass
          and conname = 'assistant_knowledge_slug_locale_unique'
    ) then
        alter table assistant.knowledge_document
            add constraint assistant_knowledge_slug_locale_unique
            unique (slug, locale);
    end if;
end
$$;

create index if not exists assistant_knowledge_active_locale_idx
    on assistant.knowledge_document (active, locale, visibility, priority, updated_at desc);

create index if not exists assistant_knowledge_search_idx
    on assistant.knowledge_document using gin (search_tsv);

alter table assistant.knowledge_document enable row level security;

-- No client role gets an implicit path. The authenticated policy below is
-- limited to trusted app_metadata roles; the import utility uses a server key.
revoke all on table assistant.knowledge_document from anon, authenticated;
grant usage on schema assistant to authenticated;
grant select, insert, update, delete on table assistant.knowledge_document to authenticated;

drop policy if exists "Assistant staff can read authoring rows" on assistant.knowledge_document;
drop policy if exists "Assistant staff can create authoring rows" on assistant.knowledge_document;
drop policy if exists "Assistant staff can update authoring rows" on assistant.knowledge_document;
drop policy if exists "Assistant staff can delete authoring rows" on assistant.knowledge_document;

create policy "Assistant staff can read authoring rows"
    on assistant.knowledge_document
    for select
    to authenticated
    using (
        upper(coalesce(((select auth.jwt()) -> 'app_metadata' ->> 'role'), '')) in
            ('ADMIN', 'SUPER_ADMIN', 'LECTURER')
        or coalesce(((select auth.jwt()) -> 'app_metadata' -> 'roles'), '[]'::jsonb) ?|
            array['ADMIN', 'SUPER_ADMIN', 'LECTURER']
    );

create policy "Assistant staff can create authoring rows"
    on assistant.knowledge_document
    for insert
    to authenticated
    with check (
        upper(coalesce(((select auth.jwt()) -> 'app_metadata' ->> 'role'), '')) in
            ('ADMIN', 'SUPER_ADMIN', 'LECTURER')
        or coalesce(((select auth.jwt()) -> 'app_metadata' -> 'roles'), '[]'::jsonb) ?|
            array['ADMIN', 'SUPER_ADMIN', 'LECTURER']
    );

create policy "Assistant staff can update authoring rows"
    on assistant.knowledge_document
    for update
    to authenticated
    using (
        upper(coalesce(((select auth.jwt()) -> 'app_metadata' ->> 'role'), '')) in
            ('ADMIN', 'SUPER_ADMIN', 'LECTURER')
        or coalesce(((select auth.jwt()) -> 'app_metadata' -> 'roles'), '[]'::jsonb) ?|
            array['ADMIN', 'SUPER_ADMIN', 'LECTURER']
    )
    with check (
        upper(coalesce(((select auth.jwt()) -> 'app_metadata' ->> 'role'), '')) in
            ('ADMIN', 'SUPER_ADMIN', 'LECTURER')
        or coalesce(((select auth.jwt()) -> 'app_metadata' -> 'roles'), '[]'::jsonb) ?|
            array['ADMIN', 'SUPER_ADMIN', 'LECTURER']
    );

create policy "Assistant staff can delete authoring rows"
    on assistant.knowledge_document
    for delete
    to authenticated
    using (
        upper(coalesce(((select auth.jwt()) -> 'app_metadata' ->> 'role'), '')) in
            ('ADMIN', 'SUPER_ADMIN', 'LECTURER')
        or coalesce(((select auth.jwt()) -> 'app_metadata' -> 'roles'), '[]'::jsonb) ?|
            array['ADMIN', 'SUPER_ADMIN', 'LECTURER']
    );
