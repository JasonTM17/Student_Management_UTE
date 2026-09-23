-- An archive must publish a new immutable corpus without the archived source.
-- The Java projection reconciler can then activate the tombstone release.
-- The original RPC locks a document/revision before updating the release.
-- Wrap the complete RPC in one transaction-scoped lock so PUBLISH and ARCHIVE
-- serialize before either starts authoring changes or hashes the corpus.
alter function assistant.knowledge_admin(text, uuid, jsonb, text)
    rename to knowledge_admin_unlocked;

create function assistant.knowledge_admin(
    p_action text,
    p_document_id uuid default null,
    p_payload jsonb default '{}'::jsonb,
    p_actor text default null
)
returns jsonb
language plpgsql
security definer
set search_path = assistant, pg_temp
as $$
begin
    if upper(btrim(coalesce(p_action, ''))) in ('PUBLISH', 'ARCHIVE') then
        perform pg_advisory_xact_lock(64122023, 1);
    end if;
    return assistant.knowledge_admin_unlocked(p_action, p_document_id, p_payload, p_actor);
end;
$$;

revoke all on function assistant.knowledge_admin(text, uuid, jsonb, text)
    from public, anon, authenticated;
grant execute on function assistant.knowledge_admin(text, uuid, jsonb, text)
    to service_role;
-- The renamed implementation must not remain a callable RPC that can bypass
-- the transaction lock. The wrapper runs as the migration owner.
revoke all on function assistant.knowledge_admin_unlocked(text, uuid, jsonb, text)
    from public, anon, authenticated, service_role;

create or replace function assistant.publish_archive_knowledge_release()
returns trigger
language plpgsql
set search_path = assistant, public
as $$
declare
    v_release uuid;
    v_existing_release uuid;
    v_hash varchar(64);
    v_version text;
    v_row_count integer;
    v_documents jsonb;
begin
    -- All governed PUBLISH/ARCHIVE calls already hold the transaction lock.
    -- Keep the release table lock for staged-release activation and direct
    -- service-role maintenance that may update the published pointer.
    lock table assistant.knowledge_release in exclusive mode;

    with canonical as (
        select d.id::text source_id, r.id::text revision_id, r.version,
               r.domain, r.slug, r.locale, r.title, r.content, r.source, r.priority,
               true as active, 'PUBLIC'::text as visibility, r.published_at
        from assistant.knowledge_document d
        join assistant.knowledge_document_revision r
          on r.document_id = d.id and r.state = 'PUBLISHED'
        where d.active = true and d.visibility = 'PUBLIC'
    ), summary as (
        select count(*)::integer row_count,
               encode(extensions.digest(coalesce(string_agg(
                   concat_ws('|', source_id, revision_id, version::text, domain, slug, locale,
                             title, content, source, priority::text, active::text, visibility),
                   E'\n' order by source_id), ''), 'sha256'), 'hex') corpus_hash,
               coalesce(jsonb_agg(jsonb_build_object('sourceId', source_id, 'domain', domain,
                   'slug', slug, 'locale', locale) order by source_id), '[]'::jsonb) documents
        from canonical
    )
    select row_count, corpus_hash, documents into v_row_count, v_hash, v_documents from summary;

    select id into v_existing_release
      from assistant.knowledge_release where corpus_hash = v_hash limit 1;
    if v_existing_release is not null then
        if (select row_count from assistant.knowledge_release where id = v_existing_release) <> v_row_count then
            raise exception 'archive_release_row_count_mismatch' using errcode = 'P0001';
        end if;
        update assistant.knowledge_release set status = 'ARCHIVED'
         where status = 'PUBLISHED' and id <> v_existing_release;
        update assistant.knowledge_release
           set status = 'PUBLISHED', published_at = now()
         where id = v_existing_release and status <> 'PUBLISHED';
        return new;
    end if;

    v_release := gen_random_uuid();
    v_version := 'supabase-archive-' || to_char(clock_timestamp(), 'YYYYMMDDHH24MISSMS')
                 || '-' || substr(v_release::text, 1, 8);
    update assistant.knowledge_release set status = 'ARCHIVED' where status = 'PUBLISHED';
    insert into assistant.knowledge_release
        (id, corpus_version, corpus_hash, row_count, status, manifest, created_by,
         reviewed_by, reviewed_at, published_at)
    values (v_release, v_version, v_hash, v_row_count, 'PUBLISHED',
            jsonb_build_object('schemaVersion', 1, 'corpusVersion', v_version,
                'rowCount', v_row_count, 'sha256', v_hash, 'documents', v_documents,
                'archivedDocumentId', new.id),
            coalesce(new.archived_by, 'archive'), new.archived_by, now(), now());
    insert into assistant.knowledge_release_document
        (release_id, source_id, revision_id, version, domain, slug, title, content,
         locale, source, priority, active, visibility, published_at)
    select v_release, d.id::text, r.id, r.version, r.domain, r.slug, r.title, r.content,
           r.locale, r.source, r.priority, true, 'PUBLIC', coalesce(r.published_at, now())
    from assistant.knowledge_document d
    join assistant.knowledge_document_revision r
      on r.document_id = d.id and r.state = 'PUBLISHED'
    where d.active = true and d.visibility = 'PUBLIC';
    if (select count(*) from assistant.knowledge_release_document where release_id = v_release) <> v_row_count then
        raise exception 'archive_release_row_count_mismatch' using errcode = 'P0001';
    end if;
    return new;
end;
$$;

drop trigger if exists assistant_archive_release_on_document on assistant.knowledge_document;
create trigger assistant_archive_release_on_document
after update of active on assistant.knowledge_document
for each row when (old.active is true and new.active is false)
execute function assistant.publish_archive_knowledge_release();

revoke all on function assistant.publish_archive_knowledge_release() from public, anon, authenticated;
grant execute on function assistant.publish_archive_knowledge_release() to service_role;
