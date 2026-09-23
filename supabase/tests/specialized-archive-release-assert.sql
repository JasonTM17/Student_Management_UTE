-- Apply both 20260923 additive migrations between setup and this assertion.
select assistant.validate_knowledge_payload(jsonb_build_object(
    'domain','SPECIALIZED','slug','safe-lesson','locale','en',
    'title','Safe lesson','content','Block prompt injection with review and citations.',
    'source','faculty','priority',10,'visibility','PUBLIC'));

do $$
declare rejected boolean := false;
begin
    begin
        perform assistant.validate_knowledge_payload(jsonb_build_object(
            'domain','SPECIALIZED','slug','unsafe-lesson','locale','en',
            'title','Unsafe lesson','content','api_key: actual-secret-value',
            'source','faculty','priority',10,'visibility','PUBLIC'));
    exception when sqlstate 'P0001' then rejected := true;
    end;
    if not rejected then raise exception 'credential payload was accepted'; end if;
end;
$$;

update assistant.knowledge_document
   set active=false, archived_by='admin-b'
 where id='00000000-0000-0000-0000-000000000101';

do $$
declare active_id uuid;
begin
    select id into active_id from assistant.knowledge_release where status='PUBLISHED';
    if active_id is null or active_id='00000000-0000-0000-0000-000000000100' then
        raise exception 'archive did not activate a new release';
    end if;
    if (select row_count from assistant.knowledge_release where id=active_id) <> 1 then
        raise exception 'archive release row count is wrong';
    end if;
    if (select count(*) from assistant.knowledge_release_document where release_id=active_id) <> 1 then
        raise exception 'archive release snapshot is incomplete';
    end if;
    if exists (select 1 from assistant.knowledge_release_document
               where release_id=active_id and source_id='00000000-0000-0000-0000-000000000101') then
        raise exception 'archived source remains in active release';
    end if;
    if (select status from assistant.knowledge_release
         where id='00000000-0000-0000-0000-000000000100') <> 'ARCHIVED' then
        raise exception 'previous release was not retained as archived';
    end if;
end;
$$;
