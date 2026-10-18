-- Current display copy only. Keep immutable audits, knowledge releases,
-- issued registration-slip bytes and uploaded reports as historical evidence.
WITH name_pattern AS (
    SELECT '(trường[[:space:]]+)?(đại[[:space:]]+học|đh)[[:space:]]+(sư[[:space:]]+phạm|công[[:space:]]+nghệ)[[:space:]]+k[ỹĩy][[:space:]]+thuật[[:space:]]+(tp[.]?[[:space:]]*(hcm|hồ[[:space:]]+chí[[:space:]]+minh)|thành[[:space:]]+phố[[:space:]]+hồ[[:space:]]+chí[[:space:]]+minh)'::text AS value
), candidates AS MATERIALIZED (
    SELECT a.id, to_jsonb(a)::text AS before_state,
           regexp_replace(a.title, p.value, 'Trường Đại học Công nghệ kĩ thuật TP.HCM', 'gi') AS new_title,
           regexp_replace(a.content, p.value, 'Trường Đại học Công nghệ kĩ thuật TP.HCM', 'gi') AS new_content,
           regexp_replace(a."publishedBy", p.value, 'Trường Đại học Công nghệ kĩ thuật TP.HCM', 'gi') AS new_publisher
    FROM engagement."Announcement" a CROSS JOIN name_pattern p
    WHERE concat_ws(' ', a.title, a.content, a."publishedBy") ~* p.value
    FOR UPDATE OF a
), changed AS (
    UPDATE engagement."Announcement" a
    SET title=c.new_title, content=c.new_content, "publishedBy"=c.new_publisher,
        version=a.version+1, "updatedAt"=CURRENT_TIMESTAMP
    FROM candidates c
    WHERE a.id=c.id
      AND (a.title IS DISTINCT FROM c.new_title
        OR a.content IS DISTINCT FROM c.new_content
        OR a."publishedBy" IS DISTINCT FROM c.new_publisher)
    RETURNING a.id, a.version, c.before_state, to_jsonb(a)::text AS after_state
)
INSERT INTO engagement."AnnouncementAudit"
    (id,"announcementId",action,"actorId","actorLabel",reason,version,"beforeState","afterState","createdAt")
SELECT 'institution-name-v122-'||md5(id), id, 'UPDATED', 'system-migration',
       'Institution display-name correction',
       'Standardize current institution display name to Trường Đại học Công nghệ kĩ thuật TP.HCM',
       version,before_state,after_state,CURRENT_TIMESTAMP
FROM changed;
