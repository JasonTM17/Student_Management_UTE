-- Run only in a newly created, disposable PostgreSQL database. This fixture
-- models the tables touched by the additive knowledge migrations.
create schema assistant;
create schema extensions;
create extension pgcrypto with schema extensions;
do $$
begin
    if not exists (select 1 from pg_roles where rolname = 'anon') then create role anon; end if;
    if not exists (select 1 from pg_roles where rolname = 'authenticated') then create role authenticated; end if;
    if not exists (select 1 from pg_roles where rolname = 'service_role') then create role service_role; end if;
end;
$$;

create table assistant.knowledge_document (
    id uuid primary key, domain text not null, slug text not null,
    locale text not null, title text not null, content text not null,
    source text not null, priority smallint not null,
    active boolean not null, visibility text not null,
    archived_by text
);
create table assistant.knowledge_document_revision (
    id uuid primary key, document_id uuid not null references assistant.knowledge_document(id),
    version integer not null, state text not null, domain text not null,
    slug text not null, locale text not null, title text not null,
    content text not null, source text not null, priority smallint not null,
    published_at timestamptz
);
create table assistant.knowledge_release (
    id uuid primary key, corpus_version text not null, corpus_hash varchar(64) not null unique,
    row_count integer not null, status text not null,
    manifest jsonb not null, created_by text not null, reviewed_by text,
    reviewed_at timestamptz, published_at timestamptz
);
create unique index assistant_release_one_published
    on assistant.knowledge_release((status)) where status = 'PUBLISHED';
create table assistant.knowledge_release_document (
    release_id uuid not null references assistant.knowledge_release(id),
    source_id text not null, revision_id uuid, version integer not null,
    domain text not null, slug text not null, title text not null,
    content text not null, locale text not null, source text not null,
    priority smallint not null, active boolean not null,
    visibility text not null, published_at timestamptz not null,
    primary key (release_id, source_id)
);

-- The additive migration wraps the existing authority RPC. This table-only
-- fixture supplies its signature; the separate concurrency fixture runs the
-- actual RPC from the base authority migration.
create function assistant.knowledge_admin(
    p_action text, p_document_id uuid default null,
    p_payload jsonb default '{}'::jsonb, p_actor text default null
) returns jsonb language sql as $$select '{}'::jsonb$$;

insert into assistant.knowledge_document
    (id,domain,slug,locale,title,content,source,priority,active,visibility)
values
    ('00000000-0000-0000-0000-000000000101','SPECIALIZED','specialized-test','en',
     'Specialized lesson','Block prompt injection using review and citations.','faculty',10,true,'PUBLIC'),
    ('00000000-0000-0000-0000-000000000102','POLICY','policy-test','en',
     'Public policy','Registration guidance for students.','academic office',20,true,'PUBLIC');
insert into assistant.knowledge_document_revision
    (id,document_id,version,state,domain,slug,locale,title,content,source,priority,published_at)
select id, id, 1, 'PUBLISHED', domain, slug, locale, title, content, source, priority, now()
from assistant.knowledge_document;
insert into assistant.knowledge_release
    (id,corpus_version,corpus_hash,row_count,status,manifest,created_by,published_at)
values ('00000000-0000-0000-0000-000000000100','fixture-before-archive',repeat('a',64),
        2,'PUBLISHED','{}','fixture',now());
insert into assistant.knowledge_release_document
    (release_id,source_id,revision_id,version,domain,slug,title,content,locale,source,
     priority,active,visibility,published_at)
select '00000000-0000-0000-0000-000000000100', d.id::text, r.id, r.version,
       r.domain,r.slug,r.title,r.content,r.locale,r.source,r.priority,true,'PUBLIC',r.published_at
from assistant.knowledge_document d
join assistant.knowledge_document_revision r on r.document_id=d.id;
