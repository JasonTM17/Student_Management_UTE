-- H2 twin of the PostgreSQL V123 institution-name harmonization.
-- The app-boot fixture creates engagement."Announcement" with only
-- (id, lecturerId), so expand it to the prod columns this migration touches
-- before applying the same copy correction.
ALTER TABLE engagement."Announcement" ADD COLUMN IF NOT EXISTS "title" VARCHAR(240);
ALTER TABLE engagement."Announcement" ADD COLUMN IF NOT EXISTS "content" TEXT;
ALTER TABLE engagement."Announcement" ADD COLUMN IF NOT EXISTS "publishedBy" VARCHAR(120);
ALTER TABLE engagement."Announcement" ADD COLUMN IF NOT EXISTS "updatedAt" TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE engagement."Announcement" ADD COLUMN IF NOT EXISTS "version" INTEGER NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS engagement."AnnouncementAudit" (
    "id" VARCHAR(120) PRIMARY KEY,
    "announcementId" VARCHAR(120) NOT NULL,
    "action" VARCHAR(32) NOT NULL,
    "actorId" VARCHAR(120) NOT NULL,
    "actorLabel" VARCHAR(240),
    "reason" VARCHAR(500) NOT NULL,
    "version" INTEGER NOT NULL,
    "beforeState" TEXT,
    "afterState" TEXT,
    "createdAt" TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE LOCAL TEMPORARY TABLE institution_name_v123 AS
SELECT a.id, a.version AS old_version,
       CAST(JSON_OBJECT('id':a.id,'title':a.title,'content':a.content,
            'publishedBy':a."publishedBy",'version':a.version) AS VARCHAR) AS before_state,
       REGEXP_REPLACE(a.title,p.pattern,'Trường Đại học Công nghệ Kỹ thuật TP.HCM','g') AS new_title,
       REGEXP_REPLACE(a.content,p.pattern,'Trường Đại học Công nghệ Kỹ thuật TP.HCM','g') AS new_content,
       REGEXP_REPLACE(a."publishedBy",p.pattern,'Trường Đại học Công nghệ Kỹ thuật TP.HCM','g') AS new_publisher
FROM engagement."Announcement" a
CROSS JOIN (VALUES ('(?iu)(trường\s+)?(đại\s+học|đh)\s+(sư\s+phạm|công\s+nghệ)\s+k[ỹĩy]\s+thuật\s+(tp[.]?\s*(hcm|hồ\s+chí\s+minh)|thành\s+phố\s+hồ\s+chí\s+minh)')) p(pattern);

DELETE FROM institution_name_v123 c
WHERE EXISTS (SELECT 1 FROM engagement."Announcement" a WHERE a.id=c.id
    AND a.title IS NOT DISTINCT FROM c.new_title
    AND a.content IS NOT DISTINCT FROM c.new_content
    AND a."publishedBy" IS NOT DISTINCT FROM c.new_publisher);

UPDATE engagement."Announcement" a
SET title=(SELECT new_title FROM institution_name_v123 c WHERE c.id=a.id),
    content=(SELECT new_content FROM institution_name_v123 c WHERE c.id=a.id),
    "publishedBy"=(SELECT new_publisher FROM institution_name_v123 c WHERE c.id=a.id),
    version=version+1,"updatedAt"=CURRENT_TIMESTAMP
WHERE EXISTS (SELECT 1 FROM institution_name_v123 c WHERE c.id=a.id);

INSERT INTO engagement."AnnouncementAudit"
    (id,"announcementId",action,"actorId","actorLabel",reason,version,"beforeState","afterState","createdAt")
SELECT 'institution-name-v123-'||CAST(RANDOM_UUID() AS VARCHAR),a.id,'UPDATED','system-migration',
       'Institution display-name correction',
       'Standardize current institution display name to Trường Đại học Công nghệ Kỹ thuật TP.HCM',
       a.version,c.before_state,
       CAST(JSON_OBJECT('id':a.id,'title':a.title,'content':a.content,
            'publishedBy':a."publishedBy",'version':a.version) AS VARCHAR),CURRENT_TIMESTAMP
FROM engagement."Announcement" a JOIN institution_name_v123 c ON c.id=a.id;
DROP TABLE institution_name_v123;
