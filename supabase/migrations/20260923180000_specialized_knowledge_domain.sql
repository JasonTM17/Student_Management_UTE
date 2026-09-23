-- Keep Supabase authoring, release snapshots, and the Java runtime projection
-- on the same domain contract. This migration is additive to deployed schemas.
alter table assistant.knowledge_document
    drop constraint if exists assistant_knowledge_domain_valid;
alter table assistant.knowledge_document
    add constraint assistant_knowledge_domain_valid check (
        domain in ('THESIS', 'REGISTRATION', 'ACADEMIC_CATALOG', 'ANNOUNCEMENT', 'POLICY', 'GENERAL_FAQ', 'SPECIALIZED')
    );

alter table assistant.knowledge_document_revision
    drop constraint if exists assistant_revision_domain_valid;
alter table assistant.knowledge_document_revision
    add constraint assistant_revision_domain_valid check (
        domain in ('THESIS', 'REGISTRATION', 'ACADEMIC_CATALOG', 'ANNOUNCEMENT', 'POLICY', 'GENERAL_FAQ', 'SPECIALIZED')
    );

alter table assistant.knowledge_release_document
    drop constraint if exists assistant_release_document_domain_valid;
alter table assistant.knowledge_release_document
    add constraint assistant_release_document_domain_valid check (
        domain in ('THESIS', 'REGISTRATION', 'ACADEMIC_CATALOG', 'ANNOUNCEMENT', 'POLICY', 'GENERAL_FAQ', 'SPECIALIZED')
    );

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
    if v_domain not in ('THESIS', 'REGISTRATION', 'ACADEMIC_CATALOG', 'ANNOUNCEMENT', 'POLICY', 'GENERAL_FAQ', 'SPECIALIZED') then
        raise exception 'knowledge_domain_invalid' using errcode = 'P0001';
    end if;
    if v_locale not in ('vi', 'en', 'both') then
        raise exception 'knowledge_locale_invalid' using errcode = 'P0001';
    end if;
    if v_visibility <> 'PUBLIC' or v_priority < 1 or v_priority > 1000 then
        raise exception 'knowledge_visibility_or_priority_invalid' using errcode = 'P0001';
    end if;
    if concat_ws(' ', v_slug, v_title, v_content, v_source) ~* '[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}'
       or concat_ws(' ', v_slug, v_title, v_content, v_source) ~* '(bearer[[:space:]]+[A-Za-z0-9._-]{12,}|sk-[A-Za-z0-9_-]{12,}|api[_ -]?key[[:space:]]*[:=]|service[_ -]?role[[:space:]]*[:=]|token[[:space:]]*[:=]|pass(word|wd)?[[:space:]]*[:=])' then
        raise exception 'knowledge_privacy_rejected' using errcode = 'P0001';
    end if;
end;
$$;
