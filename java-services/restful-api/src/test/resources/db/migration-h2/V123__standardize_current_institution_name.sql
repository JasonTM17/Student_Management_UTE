-- H2 equivalent of the mutable announcement copy correction. PostgreSQL's
-- single data-modifying CTE is qualified separately against real PostgreSQL.
CREATE LOCAL TEMPORARY TABLE institution_name_v122 AS
SELECT a.id, a.version AS old_version,
       CAST(JSON_OBJECT('id':a.id,'title':a.title,'content':a.content,
            'publishedBy':a."publishedBy",'version':a.version) AS VARCHAR) AS before_state,
       REGEXP_REPLACE(a.title,p.pattern,'Trường Đại học Công nghệ Kỹ thuật TP.HCM','g') AS new_title,
       REGEXP_REPLACE(a.content,p.pattern,'Trường Đại học Công nghệ Kỹ thuật TP.HCM','g') AS new_content,
       REGEXP_REPLACE(a."publishedBy",p.pattern,'Trường Đại học Công nghệ Kỹ thuật TP.HCM','g') AS new_publisher
FROM engagement."Announcement" a
CROSS JOIN (VALUES ('(?iu)(trường\s+)?(đại\s+học|đh)\s+(sư\s+phạm|công\s+nghệ)\s+k[ỹĩy]\s+thuật\s+(tp[.]?\s*(hcm|hồ\s+chí\s+minh)|thành\s+phố\s+hồ\s+chí\s+minh)')) p(pattern);

DELETE FROM institution_name_v122 c
WHERE EXISTS (SELECT 1 FROM engagement."Announcement" a WHERE a.id=c.id
    AND a.title IS NOT DISTINCT FROM c.new_title
    AND a.content IS NOT DISTINCT FROM c.new_content
    AND a."publishedBy" IS NOT DISTINCT FROM c.new_publisher);

UPDATE engagement."Announcement" a
SET title=(SELECT new_title FROM institution_name_v122 c WHERE c.id=a.id),
    content=(SELECT new_content FROM institution_name_v122 c WHERE c.id=a.id),
    "publishedBy"=(SELECT new_publisher FROM institution_name_v122 c WHERE c.id=a.id),
    version=version+1,"updatedAt"=CURRENT_TIMESTAMP
WHERE EXISTS (SELECT 1 FROM institution_name_v122 c WHERE c.id=a.id);

INSERT INTO engagement."AnnouncementAudit"
    (id,"announcementId",action,"actorId","actorLabel",reason,version,"beforeState","afterState","createdAt")
SELECT 'institution-name-v122-'||CAST(RANDOM_UUID() AS VARCHAR),a.id,'UPDATED','system-migration',
       'Institution display-name correction',
       'Standardize current institution display name to Trường Đại học Công nghệ Kỹ thuật TP.HCM',
       a.version,c.before_state,
       CAST(JSON_OBJECT('id':a.id,'title':a.title,'content':a.content,
            'publishedBy':a."publishedBy",'version':a.version) AS VARCHAR),CURRENT_TIMESTAMP
FROM engagement."Announcement" a JOIN institution_name_v122 c ON c.id=a.id;
DROP TABLE institution_name_v122;
