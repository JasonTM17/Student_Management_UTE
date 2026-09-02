-- Production CampusCore source of truth.
--
-- Supabase owns authoring, review, and immutable releases. The Java service
-- reads only a published release and projects it into its private PostgreSQL
-- runtime tables. Browser roles have no grants on this schema; the only Data
-- API principal is the server-side service_role key held by rag-service.

create schema if not exists assistant;
create extension if not exists pgcrypto;

alter table assistant.knowledge_document
    add column if not exists domain text not null default 'THESIS',
    add column if not exists archived_at timestamptz,
    add column if not exists archived_by text;

alter table assistant.knowledge_document
    drop constraint if exists assistant_knowledge_domain_valid;
alter table assistant.knowledge_document
    add constraint assistant_knowledge_domain_valid check (
        domain in ('THESIS', 'REGISTRATION', 'ACADEMIC_CATALOG', 'ANNOUNCEMENT', 'POLICY', 'GENERAL_FAQ')
    );

-- The revision table is intentionally parallel to the Java governance schema.
-- Existing authoring rows are imported as immutable system-published revision 1
-- so a first release can be created without losing the legacy corpus.
create table if not exists assistant.knowledge_document_revision (
    id uuid primary key default gen_random_uuid(),
    document_id uuid not null references assistant.knowledge_document(id) on delete cascade,
    version integer not null,
    state text not null,
    domain text not null default 'THESIS',
    locale text not null,
    slug text not null,
    title text not null,
    content text not null,
    source text not null,
    priority smallint not null default 100,
    created_by text not null,
    reviewed_by text,
    created_at timestamptz not null default now(),
    published_at timestamptz,
    unique (document_id, version)
);

alter table assistant.knowledge_document_revision
    add column if not exists domain text not null default 'THESIS';
alter table assistant.knowledge_document_revision
    drop constraint if exists assistant_revision_state_valid;
alter table assistant.knowledge_document_revision
    add constraint assistant_revision_state_valid
        check (state in ('DRAFT', 'PENDING_REVIEW', 'PUBLISHED', 'ARCHIVED'));
alter table assistant.knowledge_document_revision
    drop constraint if exists assistant_revision_domain_valid;
alter table assistant.knowledge_document_revision
    add constraint assistant_revision_domain_valid
        check (domain in ('THESIS', 'REGISTRATION', 'ACADEMIC_CATALOG', 'ANNOUNCEMENT', 'POLICY', 'GENERAL_FAQ'));
alter table assistant.knowledge_document_revision
    drop constraint if exists assistant_revision_locale_valid;
alter table assistant.knowledge_document_revision
    add constraint assistant_revision_locale_valid check (locale in ('vi', 'en', 'both'));
alter table assistant.knowledge_document_revision
    drop constraint if exists assistant_revision_priority_valid;
alter table assistant.knowledge_document_revision
    add constraint assistant_revision_priority_valid check (priority between 1 and 1000);
create index if not exists assistant_revision_latest_idx
    on assistant.knowledge_document_revision (document_id, version desc);
create index if not exists assistant_revision_state_idx
    on assistant.knowledge_document_revision (state, domain, locale, priority);
create unique index if not exists assistant_revision_one_published_idx
    on assistant.knowledge_document_revision (document_id) where state = 'PUBLISHED';

create table if not exists assistant.knowledge_document_audit (
    id uuid primary key default gen_random_uuid(),
    revision_id uuid not null references assistant.knowledge_document_revision(id) on delete cascade,
    action text not null,
    actor_id text not null,
    note text,
    created_at timestamptz not null default now()
);
create index if not exists assistant_knowledge_audit_revision_idx
    on assistant.knowledge_document_audit (revision_id, created_at desc);

insert into assistant.knowledge_document_revision
    (id, document_id, version, state, domain, locale, slug, title, content, source, priority,
     created_by, reviewed_by, created_at, published_at)
select gen_random_uuid(), d.id, 1, 'PUBLISHED', coalesce(nullif(d.domain, ''), 'THESIS'),
       d.locale, d.slug, d.title, d.content, d.source, d.priority,
       'system-migration', 'system-migration', d.created_at, coalesce(d.updated_at, now())
from assistant.knowledge_document d
where not exists (
    select 1 from assistant.knowledge_document_revision r
    where r.document_id = d.id and r.version = 1
);

-- Release tables are immutable snapshots. A release may be staged by an
-- operator, but only the publication function can move it to PUBLISHED.
create table if not exists assistant.knowledge_release (
    id uuid primary key default gen_random_uuid(),
    corpus_version text not null,
    corpus_hash varchar(64) not null,
    row_count integer not null check (row_count >= 0),
    status text not null default 'DRAFT',
    manifest jsonb not null default '{}'::jsonb,
    created_by text not null,
    reviewed_by text,
    created_at timestamptz not null default now(),
    reviewed_at timestamptz,
    published_at timestamptz,
    constraint assistant_release_hash_valid check (corpus_hash ~ '^[0-9a-f]{64}$'),
    constraint assistant_release_status_valid check (status in ('DRAFT', 'PENDING_REVIEW', 'PUBLISHED', 'ARCHIVED'))
);
alter table assistant.knowledge_release
    add column if not exists reviewed_by text,
    add column if not exists reviewed_at timestamptz,
    add column if not exists published_at timestamptz;
create unique index if not exists assistant_release_hash_unique on assistant.knowledge_release(corpus_hash);
create unique index if not exists assistant_release_one_published
    on assistant.knowledge_release((status)) where status = 'PUBLISHED';

create table if not exists assistant.knowledge_release_document (
    release_id uuid not null references assistant.knowledge_release(id) on delete cascade,
    source_id text not null,
    revision_id uuid,
    version integer not null default 0 check (version >= 0),
    domain text not null,
    slug text not null,
    title text not null,
    content text not null,
    locale text not null,
    source text not null,
    active boolean not null default true,
    visibility text not null default 'PUBLIC',
    priority smallint not null default 100,
    published_at timestamptz not null default now(),
    primary key (release_id, source_id),
    constraint assistant_release_document_domain_valid check (
        domain in ('THESIS', 'REGISTRATION', 'ACADEMIC_CATALOG', 'ANNOUNCEMENT', 'POLICY', 'GENERAL_FAQ')
    ),
    constraint assistant_release_document_locale_valid check (locale in ('vi', 'en', 'both')),
    constraint assistant_release_document_visibility_valid check (visibility = 'PUBLIC'),
    constraint assistant_release_document_priority_valid check (priority between 1 and 1000)
);
alter table assistant.knowledge_release_document
    add column if not exists revision_id uuid,
    add column if not exists version integer not null default 0,
    add column if not exists domain text not null default 'THESIS',
    add column if not exists title text not null default '',
    add column if not exists content text not null default '',
    add column if not exists source text not null default '',
    add column if not exists active boolean not null default true,
    add column if not exists visibility text not null default 'PUBLIC',
    add column if not exists priority smallint not null default 100,
    add column if not exists published_at timestamptz not null default now();
create unique index if not exists assistant_release_document_slug_unique
    on assistant.knowledge_release_document(release_id, slug);

-- Public knowledge is deliberately conservative. Email addresses, tokens,
-- credentials, and staff-only material are not allowed into a release.
create or replace function assistant.validate_knowledge_payload(p_payload jsonb)
returns void
language plpgsql
set search_path = assistant, public
as $$
declare
    v_domain text := upper(btrim(coalesce(p_payload ->> 'domain', 'THESIS')));
    v_locale text := lower(btrim(coalesce(p_payload ->> 'locale', '')));
    v_slug text := btrim(coalesce(p_payload ->> 'slug', ''));
    v_title text := btrim(coalesce(p_payload ->> 'title', ''));
    v_content text := btrim(coalesce(p_payload ->> 'content', ''));
    v_source text := btrim(coalesce(p_payload ->> 'source', ''));
    v_priority integer := coalesce((p_payload ->> 'priority')::integer, 100);
    v_visibility text := upper(btrim(coalesce(p_payload ->> 'visibility', 'PUBLIC')));
begin
    if v_slug = '' or v_title = '' or v_content = '' or v_source = '' then
        raise exception 'knowledge_required' using errcode = 'P0001';
    end if;
    if length(v_slug) > 180 or length(v_title) > 500 or length(v_content) > 50000 or length(v_source) > 240 then
        raise exception 'knowledge_length_invalid' using errcode = 'P0001';
    end if;
    if v_slug !~ '^[a-z0-9][a-z0-9-]*$' then
        raise exception 'knowledge_slug_invalid' using errcode = 'P0001';
    end if;
    if v_domain not in ('THESIS', 'REGISTRATION', 'ACADEMIC_CATALOG', 'ANNOUNCEMENT', 'POLICY', 'GENERAL_FAQ') then
        raise exception 'knowledge_domain_invalid' using errcode = 'P0001';
    end if;
    if v_locale not in ('vi', 'en', 'both') then
        raise exception 'knowledge_locale_invalid' using errcode = 'P0001';
    end if;
    if v_visibility <> 'PUBLIC' or v_priority < 1 or v_priority > 1000 then
        raise exception 'knowledge_visibility_or_priority_invalid' using errcode = 'P0001';
    end if;
    if concat_ws(' ', v_slug, v_title, v_content, v_source) ~* '[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}'
       or concat_ws(' ', v_slug, v_title, v_content, v_source) ~* '(bearer[[:space:]]+[A-Za-z0-9._-]+|api[_ -]?key|service[_ -]?role|password|secret)' then
        raise exception 'knowledge_privacy_rejected' using errcode = 'P0001';
    end if;
end;
$$;

-- One RPC is the only authoring surface. Keeping transitions in a single
-- transaction means a failed stage or pointer switch cannot partially publish.
create or replace function assistant.knowledge_admin(
    p_action text,
    p_document_id uuid default null,
    p_payload jsonb default '{}'::jsonb,
    p_actor text default null
)
returns jsonb
language plpgsql
set search_path = assistant, public
as $$
declare
    v_action text := upper(btrim(coalesce(p_action, '')));
    v_actor text := nullif(btrim(coalesce(p_actor, '')), '');
    v_payload jsonb := coalesce(p_payload, '{}'::jsonb);
    v_doc uuid;
    v_revision uuid;
    v_version integer;
    v_created_by text;
    v_next_version integer;
    v_release uuid;
    v_existing_release uuid;
    v_existing_corpus_version text;
    v_hash varchar(64);
    v_corpus_version text;
    v_row_count integer;
    v_documents jsonb;
begin
    if v_actor is null or length(v_actor) > 120 then
        raise exception 'knowledge_actor_required' using errcode = 'P0001';
    end if;

    if v_action = 'LIST' or v_action = 'GET' then
        if v_action = 'GET' and p_document_id is null then
            raise exception 'knowledge_not_found' using errcode = 'P0001';
        end if;
        if v_action = 'GET' then
            select jsonb_build_object(
                'documentId', d.id, 'revisionId', latest.id,
                'version', coalesce(latest.version, 0),
                'state', case when d.active = false then 'ARCHIVED' else coalesce(latest.state, 'UNVERSIONED') end,
                'domain', coalesce(latest.domain, d.domain, 'THESIS'),
                'locale', coalesce(latest.locale, d.locale), 'slug', coalesce(latest.slug, d.slug),
                'title', coalesce(latest.title, d.title), 'content', coalesce(latest.content, d.content),
                'source', coalesce(latest.source, d.source), 'priority', coalesce(latest.priority, d.priority),
                'createdBy', latest.created_by, 'reviewedBy', latest.reviewed_by,
                'createdAt', latest.created_at, 'publishedAt', latest.published_at
            ) into v_documents
            from assistant.knowledge_document d
            left join lateral (
                select r.* from assistant.knowledge_document_revision r
                where r.document_id = d.id order by r.version desc limit 1
            ) latest on true
            where d.id = p_document_id;
            if v_documents is null then raise exception 'knowledge_not_found' using errcode = 'P0001'; end if;
            return v_documents;
        end if;

        select coalesce(jsonb_agg(row_value order by slug), '[]'::jsonb) into v_documents
        from (
            select jsonb_build_object(
                'documentId', d.id, 'revisionId', latest.id,
                'version', coalesce(latest.version, 0),
                'state', case when d.active = false then 'ARCHIVED' else coalesce(latest.state, 'UNVERSIONED') end,
                'domain', coalesce(latest.domain, d.domain, 'THESIS'),
                'locale', coalesce(latest.locale, d.locale), 'slug', coalesce(latest.slug, d.slug),
                'title', coalesce(latest.title, d.title), 'content', coalesce(latest.content, d.content),
                'source', coalesce(latest.source, d.source), 'priority', coalesce(latest.priority, d.priority),
                'createdBy', latest.created_by, 'reviewedBy', latest.reviewed_by,
                'createdAt', latest.created_at, 'publishedAt', latest.published_at
            ) as row_value,
            coalesce(latest.slug, d.slug) as slug
            from assistant.knowledge_document d
            left join lateral (
                select r.* from assistant.knowledge_document_revision r
                where r.document_id = d.id order by r.version desc limit 1
            ) latest on true
            where (nullif(btrim(v_payload ->> 'domain'), '') is null
                   or coalesce(latest.domain, d.domain, 'THESIS') = upper(btrim(v_payload ->> 'domain')))
              and (nullif(btrim(v_payload ->> 'state'), '') is null
                   or (case when d.active = false then 'ARCHIVED' else coalesce(latest.state, 'UNVERSIONED') end)
                       = upper(btrim(v_payload ->> 'state')))
        ) listed;
        return v_documents;
    end if;

    if v_action in ('CREATE', 'UPDATE') then
        perform assistant.validate_knowledge_payload(v_payload);
    end if;

    if v_action = 'CREATE' then
        v_doc := gen_random_uuid();
        v_revision := gen_random_uuid();
        insert into assistant.knowledge_document
            (id, slug, title, content, locale, source, domain, priority, active, visibility, created_at, updated_at)
        values (v_doc, btrim(v_payload ->> 'slug'), btrim(v_payload ->> 'title'), btrim(v_payload ->> 'content'),
                lower(btrim(v_payload ->> 'locale')), btrim(v_payload ->> 'source'),
                upper(btrim(coalesce(v_payload ->> 'domain', 'THESIS'))), coalesce((v_payload ->> 'priority')::smallint, 100),
                true, 'PUBLIC', now(), now());
        insert into assistant.knowledge_document_revision
            (id, document_id, version, state, domain, locale, slug, title, content, source, priority, created_by)
        values (v_revision, v_doc, 1, 'DRAFT', upper(btrim(coalesce(v_payload ->> 'domain', 'THESIS'))),
                lower(btrim(v_payload ->> 'locale')), btrim(v_payload ->> 'slug'), btrim(v_payload ->> 'title'),
                btrim(v_payload ->> 'content'), btrim(v_payload ->> 'source'), coalesce((v_payload ->> 'priority')::smallint, 100), v_actor);
        insert into assistant.knowledge_document_audit(revision_id, action, actor_id) values (v_revision, 'CREATE', v_actor);
        return jsonb_build_object('documentId', v_doc, 'revisionId', v_revision, 'version', 1, 'state', 'DRAFT');
    end if;

    if p_document_id is null then raise exception 'knowledge_not_found' using errcode = 'P0001'; end if;

    if v_action = 'UPDATE' then
        perform 1 from assistant.knowledge_document where id = p_document_id for update;
        if not found then raise exception 'knowledge_not_found' using errcode = 'P0001'; end if;
        select r.id, r.version into v_revision, v_version
        from assistant.knowledge_document_revision r
        where r.document_id = p_document_id and r.state = 'DRAFT' and r.created_by = v_actor
        order by r.version desc limit 1 for update;
        if v_revision is null then
            select coalesce(max(r.version), 0) + 1 into v_next_version
            from assistant.knowledge_document_revision r where r.document_id = p_document_id;
            v_revision := gen_random_uuid(); v_version := v_next_version;
            insert into assistant.knowledge_document_revision
                (id, document_id, version, state, domain, locale, slug, title, content, source, priority, created_by)
            values (v_revision, p_document_id, v_version, 'DRAFT', upper(btrim(coalesce(v_payload ->> 'domain', 'THESIS'))),
                    lower(btrim(v_payload ->> 'locale')), btrim(v_payload ->> 'slug'), btrim(v_payload ->> 'title'),
                    btrim(v_payload ->> 'content'), btrim(v_payload ->> 'source'), coalesce((v_payload ->> 'priority')::smallint, 100), v_actor);
        else
            update assistant.knowledge_document_revision
               set domain = upper(btrim(coalesce(v_payload ->> 'domain', 'THESIS'))),
                   locale = lower(btrim(v_payload ->> 'locale')), slug = btrim(v_payload ->> 'slug'),
                   title = btrim(v_payload ->> 'title'), content = btrim(v_payload ->> 'content'),
                   source = btrim(v_payload ->> 'source'), priority = coalesce((v_payload ->> 'priority')::smallint, 100)
             where id = v_revision and state = 'DRAFT';
        end if;
        insert into assistant.knowledge_document_audit(revision_id, action, actor_id) values (v_revision, 'UPDATE', v_actor);
        return jsonb_build_object('documentId', p_document_id, 'revisionId', v_revision, 'version', v_version, 'state', 'DRAFT');
    end if;

    if v_action = 'SUBMIT' then
        select r.id, r.version into v_revision, v_version
        from assistant.knowledge_document_revision r
        where r.document_id = p_document_id and r.state = 'DRAFT' and r.created_by = v_actor
        order by r.version desc limit 1 for update;
        if v_revision is null then raise exception 'knowledge_state_conflict' using errcode = 'P0001'; end if;
        update assistant.knowledge_document_revision set state = 'PENDING_REVIEW' where id = v_revision and state = 'DRAFT';
        insert into assistant.knowledge_document_audit(revision_id, action, actor_id) values (v_revision, 'SUBMIT', v_actor);
        return jsonb_build_object('documentId', p_document_id, 'revisionId', v_revision, 'version', v_version, 'state', 'PENDING_REVIEW');
    end if;

    if v_action = 'PUBLISH' then
        select r.id, r.version, r.created_by,
               jsonb_build_object('domain', r.domain, 'locale', r.locale, 'slug', r.slug, 'title', r.title,
                   'content', r.content, 'source', r.source, 'priority', r.priority, 'visibility', 'PUBLIC')
          into v_revision, v_version, v_created_by, v_payload
        from assistant.knowledge_document_revision r
        where r.document_id = p_document_id and r.state = 'PENDING_REVIEW' and r.created_by <> v_actor
        order by r.version desc limit 1 for update;
        if v_revision is null then raise exception 'knowledge_second_review_required' using errcode = 'P0001'; end if;
        perform assistant.validate_knowledge_payload(v_payload);
        update assistant.knowledge_document_revision
           set state = 'ARCHIVED' where document_id = p_document_id and state = 'PUBLISHED';
        update assistant.knowledge_document_revision
           set state = 'PUBLISHED', reviewed_by = v_actor, published_at = now()
         where id = v_revision and state = 'PENDING_REVIEW';
        update assistant.knowledge_document
           set domain = v_payload ->> 'domain', locale = v_payload ->> 'locale', slug = v_payload ->> 'slug',
               title = v_payload ->> 'title', content = v_payload ->> 'content', source = v_payload ->> 'source',
               priority = (v_payload ->> 'priority')::smallint, active = true, visibility = 'PUBLIC',
               archived_at = null, archived_by = null, updated_at = now()
         where id = p_document_id;
        insert into assistant.knowledge_document_audit(revision_id, action, actor_id) values (v_revision, 'PUBLISH', v_actor);

        -- Canonical bytes are exactly the bytes validated by the Java sync
        -- service. Sorting by source_id makes the hash independent of query
        -- order and the manifest auditable.
        with canonical as (
            select d.id::text source_id, r.id::text revision_id, r.version,
                   r.domain, r.slug, r.locale, r.title, r.content, r.source, r.priority,
                   true as active, 'PUBLIC'::text as visibility, r.published_at
            from assistant.knowledge_document d
            join assistant.knowledge_document_revision r on r.document_id = d.id and r.state = 'PUBLISHED'
            where d.active = true and d.visibility = 'PUBLIC'
        ), summary as (
            select count(*)::integer as row_count,
                   encode(extensions.digest(coalesce(string_agg(
                       concat_ws('|', source_id, revision_id, version::text, domain, slug, locale, title, content,
                                 source, priority::text, active::text, visibility), E'\n' order by source_id), ''), 'sha256'), 'hex') as corpus_hash,
                   coalesce(jsonb_agg(jsonb_build_object('sourceId', source_id, 'domain', domain, 'slug', slug, 'locale', locale) order by source_id), '[]'::jsonb) as documents
            from canonical
        )
        select row_count, corpus_hash, documents into v_row_count, v_hash, v_documents from summary;
        v_release := gen_random_uuid();
        v_corpus_version := 'supabase-' || to_char(clock_timestamp(), 'YYYYMMDDHH24MISSMS') || '-' || substr(v_release::text, 1, 8);

        select id, corpus_version into v_existing_release, v_existing_corpus_version
        from assistant.knowledge_release where corpus_hash = v_hash limit 1;
        if v_existing_release is not null then
            update assistant.knowledge_release set status = 'ARCHIVED' where status = 'PUBLISHED' and id <> v_existing_release;
            update assistant.knowledge_release
               set status = 'PUBLISHED', reviewed_by = v_actor, reviewed_at = now(), published_at = now()
             where id = v_existing_release;
            return jsonb_build_object('documentId', p_document_id, 'revisionId', v_revision, 'version', v_version,
                'state', 'PUBLISHED', 'releaseId', v_existing_release, 'corpusVersion', v_existing_corpus_version,
                'corpusHash', v_hash, 'rowCount', v_row_count);
        end if;

        update assistant.knowledge_release set status = 'ARCHIVED' where status = 'PUBLISHED';
        insert into assistant.knowledge_release
            (id, corpus_version, corpus_hash, row_count, status, manifest, created_by, reviewed_by, reviewed_at, published_at)
        values (v_release, v_corpus_version, v_hash, v_row_count, 'PUBLISHED',
                jsonb_build_object('schemaVersion', 1, 'corpusVersion', v_corpus_version,
                    'rowCount', v_row_count, 'sha256', v_hash, 'documents', v_documents),
                v_created_by, v_actor, now(), now());
        insert into assistant.knowledge_release_document
            (release_id, source_id, revision_id, version, domain, slug, title, content, locale, source, priority, active, visibility, published_at)
        select v_release, d.id::text, r.id, r.version, r.domain, r.slug, r.title, r.content, r.locale, r.source,
               r.priority, true, 'PUBLIC', coalesce(r.published_at, now())
        from assistant.knowledge_document d
        join assistant.knowledge_document_revision r on r.document_id = d.id and r.state = 'PUBLISHED'
        where d.active = true and d.visibility = 'PUBLIC';
        return jsonb_build_object('documentId', p_document_id, 'revisionId', v_revision, 'version', v_version,
            'state', 'PUBLISHED', 'releaseId', v_release, 'corpusVersion', v_corpus_version,
            'corpusHash', v_hash, 'rowCount', v_row_count);
    end if;

    if v_action = 'ARCHIVE' then
        perform 1 from assistant.knowledge_document where id = p_document_id for update;
        if not found then raise exception 'knowledge_not_found' using errcode = 'P0001'; end if;
        update assistant.knowledge_document set active = false, archived_at = now(), archived_by = v_actor, updated_at = now()
         where id = p_document_id;
        select r.id, r.version into v_revision, v_version
        from assistant.knowledge_document_revision r where r.document_id = p_document_id order by r.version desc limit 1;
        if v_revision is not null then
            update assistant.knowledge_document_revision set state = 'ARCHIVED' where id = v_revision and state <> 'ARCHIVED';
            insert into assistant.knowledge_document_audit(revision_id, action, actor_id) values (v_revision, 'ARCHIVE', v_actor);
        end if;
        return jsonb_build_object('documentId', p_document_id, 'revisionId', v_revision, 'version', coalesce(v_version, 0), 'state', 'ARCHIVED');
    end if;

    raise exception 'knowledge_action_invalid' using errcode = 'P0001';
end;
$$;

-- Operator-level release staging remains available for a prebuilt manifest,
-- but publication still requires an explicit second actor.
drop function if exists assistant.publish_knowledge_release(uuid);
create or replace function assistant.publish_knowledge_release(p_release_id uuid, p_actor text default null)
returns jsonb
language plpgsql
set search_path = assistant, public
as $$
declare
    v_release assistant.knowledge_release%rowtype;
    v_actor text := nullif(btrim(coalesce(p_actor, '')), '');
    v_count integer;
begin
    if v_actor is null or length(v_actor) > 120 then raise exception 'knowledge_actor_required' using errcode = 'P0001'; end if;
    select * into v_release from assistant.knowledge_release where id = p_release_id for update;
    if not found then raise exception 'release_not_found' using errcode = 'P0001'; end if;
    if v_release.status <> 'PENDING_REVIEW' then raise exception 'release_not_pending_review' using errcode = 'P0001'; end if;
    if v_actor = v_release.created_by then raise exception 'second_admin_required' using errcode = 'P0001'; end if;
    select count(*) into v_count from assistant.knowledge_release_document where release_id = p_release_id;
    if v_count <> v_release.row_count then raise exception 'release_row_count_mismatch' using errcode = 'P0001'; end if;
    update assistant.knowledge_release set status = 'ARCHIVED' where status = 'PUBLISHED' and id <> p_release_id;
    update assistant.knowledge_release
       set status = 'PUBLISHED', reviewed_by = v_actor, reviewed_at = now(), published_at = now()
     where id = p_release_id;
    return jsonb_build_object('id', p_release_id, 'status', 'PUBLISHED', 'row_count', v_count);
end;
$$;

-- Explicit grants are required on current and new Supabase projects. No
-- browser role can read authoring or release rows, even if RLS is changed.
alter table assistant.knowledge_document enable row level security;
alter table assistant.knowledge_document_revision enable row level security;
alter table assistant.knowledge_document_audit enable row level security;
alter table assistant.knowledge_release enable row level security;
alter table assistant.knowledge_release_document enable row level security;
revoke all on schema assistant from anon, authenticated;
revoke all on all tables in schema assistant from anon, authenticated;
revoke all on function assistant.validate_knowledge_payload(jsonb) from public, anon, authenticated;
revoke all on function assistant.knowledge_admin(text, uuid, jsonb, text) from public, anon, authenticated;
revoke all on function assistant.publish_knowledge_release(uuid, text) from public, anon, authenticated;
alter default privileges in schema assistant revoke all on tables from anon, authenticated;
alter default privileges in schema assistant revoke all on sequences from anon, authenticated;
alter default privileges in schema assistant revoke execute on functions from public, anon, authenticated;
grant usage on schema assistant to service_role;
grant select, insert, update, delete on all tables in schema assistant to service_role;
alter default privileges in schema assistant grant select, insert, update, delete on tables to service_role;
alter default privileges in schema assistant grant usage, select on sequences to service_role;
alter default privileges in schema assistant grant execute on functions to service_role;
grant execute on function assistant.validate_knowledge_payload(jsonb) to service_role;
grant execute on function assistant.knowledge_admin(text, uuid, jsonb, text) to service_role;
grant execute on function assistant.publish_knowledge_release(uuid, text) to service_role;
