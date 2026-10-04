-- V100 restored the 1-3 member contract, but V67/V72 had republished the
-- assistant corpus and a global announcement asserting the earlier 3-4 rule.
-- Left as-is the grounded chatbot would cite documents that contradict the
-- code, the schema CHECK, and the portal copy.
--
-- This migration is the same class of correction as V96/V99:
--   1. Rewrites the stale sentences in the affected knowledge documents
--      (group size 3-4 -> 1-3, and a CampusCore -> CampusUTE brand scrub for
--      the pre-rename wording still embedded in the corpus). Every rewritten
--      document is stamped source='campuscore-thesis-spec-correction-v101' so
--      the revision step below touches exactly the rows this migration changed.
--   2. Archives the superseded PUBLISHED revision of each changed document and
--      publishes the corrected text as a new revision (governance: one
--      PUBLISHED revision per document).
--   3. Projects a new immutable release (local-demo-v101) that swaps in the
--      corrected rows and activates it, so retrieval actually serves the fix.
--      If the active release is a foreign authority snapshot (not MANUAL/
--      LEGACY) no release is cut — the documents are still corrected but the
--      served corpus is left to its owner.
--   4. Publishes a NEW correction announcement superseding the V72 notice and
--      records both events in the audit trails; the historical announcement
--      row stays untouched.

SELECT active_release_id FROM assistant.knowledge_runtime_state
 WHERE singleton = TRUE FOR UPDATE;

-- 1a. Group-size corrections, per document, guarded so re-runs are no-ops.
UPDATE assistant.knowledge_document
SET content = REPLACE(content,
        'từ 3 đến 4 thành viên, trong đó có một nhóm trưởng',
        'từ 1 đến 3 thành viên, trong đó có một nhóm trưởng'),
    source = 'campuscore-thesis-spec-correction-v101',
    updated_at = CURRENT_TIMESTAMP
WHERE slug = 'thesis-group-members-rules-vi'
  AND content LIKE '%từ 3 đến 4 thành viên, trong đó có một nhóm trưởng%';

UPDATE assistant.knowledge_document
SET content = REPLACE(content,
        '3 to 4 members, including one group leader',
        '1 to 3 members, including one group leader'),
    source = 'campuscore-thesis-spec-correction-v101',
    updated_at = CURRENT_TIMESTAMP
WHERE slug = 'thesis-group-members-rules-en'
  AND content LIKE '%3 to 4 members, including one group leader%';

UPDATE assistant.knowledge_document
SET content = REPLACE(content,
        'teams of 3 to 4 students',
        'teams of 1 to 3 students'),
    source = 'campuscore-thesis-spec-correction-v101',
    updated_at = CURRENT_TIMESTAMP
WHERE slug = 'graduation-thesis-eligibility-defense-en'
  AND content LIKE '%teams of 3 to 4 students%';

UPDATE assistant.knowledge_document
SET content = REPLACE(content,
        'nhóm từ 3 đến 4 sinh viên',
        'nhóm từ 1 đến 3 sinh viên'),
    source = 'campuscore-thesis-spec-correction-v101',
    updated_at = CURRENT_TIMESTAMP
WHERE slug = 'graduation-thesis-eligibility-defense-vi'
  AND content LIKE '%nhóm từ 3 đến 4 sinh viên%';

UPDATE assistant.knowledge_document
SET content = REPLACE(content,
        'groups of 3 to 4 members with exactly one group leader',
        'groups of 1 to 3 members with exactly one group leader'),
    source = 'campuscore-thesis-spec-correction-v101',
    updated_at = CURRENT_TIMESTAMP
WHERE slug = 'campus-portal-comprehensive-service-directory-en'
  AND content LIKE '%groups of 3 to 4 members%';

UPDATE assistant.knowledge_document
SET content = REPLACE(content,
        'nhóm từ 3 đến 4 thành viên, có đúng một nhóm trưởng',
        'nhóm từ 1 đến 3 thành viên, có đúng một nhóm trưởng'),
    source = 'campuscore-thesis-spec-correction-v101',
    updated_at = CURRENT_TIMESTAMP
WHERE slug = 'campus-portal-comprehensive-service-directory-vi'
  AND content LIKE '%nhóm từ 3 đến 4 thành viên%';

-- 1b. Brand scrub: the portal shipped as CampusUTE; corpus prose still said
--     CampusCore. Content-level replace only — document titles are clean.
UPDATE assistant.knowledge_document
SET content = REPLACE(content, 'CampusCore', 'CampusUTE'),
    source = 'campuscore-thesis-spec-correction-v101',
    updated_at = CURRENT_TIMESTAMP
WHERE content LIKE '%CampusCore%'
  AND source IS DISTINCT FROM 'campuscore-thesis-spec-correction-v101';

-- 2. Every document this migration rewrote gets a fresh published revision;
--    its superseded PUBLISHED revision is archived, never edited.
CREATE TEMP TABLE v101_changed_document ON COMMIT DROP AS
SELECT d.id
FROM assistant.knowledge_document d
WHERE d.source = 'campuscore-thesis-spec-correction-v101'
  AND d.active = TRUE;

UPDATE assistant.knowledge_document_revision r
SET state = 'ARCHIVED'
FROM v101_changed_document c
WHERE r.document_id = c.id AND r.state = 'PUBLISHED'
  AND (r.content LIKE '%3 đến 4%' OR r.content LIKE '%3 to 4%'
       OR r.content LIKE '%CampusCore%');

INSERT INTO assistant.knowledge_document_revision
    (id, document_id, version, state, domain, locale, slug, title, content,
     source, priority, created_by, reviewed_by, published_at)
SELECT md5(d.id::text || '-correction-v101')::uuid, d.id,
       COALESCE((SELECT MAX(x.version) FROM assistant.knowledge_document_revision x
                  WHERE x.document_id = d.id), 0) + 1,
       'PUBLISHED', d.domain, d.locale, d.slug, d.title, d.content,
       d.source, d.priority,
       'system-migration', 'system-migration', CURRENT_TIMESTAMP
FROM assistant.knowledge_document d
JOIN v101_changed_document c ON c.id = d.id
WHERE NOT EXISTS (
    SELECT 1 FROM assistant.knowledge_document_revision e
    WHERE e.document_id = d.id AND e.state = 'PUBLISHED');

-- 3. Project the active local release with the corrected rows swapped in, then
--    publish + activate it (V96 mechanics). source guard: only MANUAL/LEGACY
--    releases are rebuilt; foreign authority snapshots are left alone.
CREATE TEMP TABLE v101_projected_runtime ON COMMIT DROP AS
SELECT p.source_id, COALESCE(r.id, p.revision_id) AS revision_id,
       COALESCE(r.version, p.version) AS version,
       COALESCE(r.domain, p.domain) AS domain, COALESCE(r.slug, p.slug) AS slug,
       COALESCE(r.locale, p.locale) AS locale, COALESCE(r.title, p.title) AS title,
       COALESCE(r.content, p.content) AS content, COALESCE(r.source, p.source) AS source,
       COALESCE(r.priority, p.priority) AS priority, p.active, p.visibility,
       COALESCE(r.published_at, p.published_at) AS published_at
FROM assistant.knowledge_runtime_state s
JOIN assistant.knowledge_release current_release ON current_release.id = s.active_release_id
JOIN assistant.knowledge_runtime_document p ON p.release_id = current_release.id
LEFT JOIN v101_changed_document c ON c.id::text = p.source_id
LEFT JOIN assistant.knowledge_document_revision r
       ON r.document_id = c.id AND r.state = 'PUBLISHED'
WHERE s.singleton = TRUE AND current_release.status = 'PUBLISHED'
  AND current_release.source IN ('MANUAL', 'LEGACY');

CREATE TEMP TABLE v101_summary ON COMMIT DROP AS
SELECT COUNT(*)::integer AS row_count,
       encode(thesis.digest(COALESCE(string_agg(
           concat_ws('|', source_id, COALESCE(revision_id::text, ''), version::text,
                     domain, slug, locale, title, content, source, priority::text,
                     active::text, visibility), E'\n' ORDER BY source_id), ''), 'sha256'), 'hex') AS corpus_hash,
       COALESCE(jsonb_agg(jsonb_build_object('sourceId', source_id, 'domain', domain,
           'slug', slug, 'locale', locale) ORDER BY source_id), '[]'::jsonb) AS documents
FROM v101_projected_runtime;

INSERT INTO assistant.knowledge_release
    (id, corpus_version, corpus_hash, row_count, source, status, manifest,
     created_by, activated_at, previous_release_id)
SELECT '00000000-0000-0000-0000-000000000101'::uuid, 'local-demo-v101',
       summary.corpus_hash, summary.row_count, 'MANUAL', 'PUBLISHED',
       jsonb_build_object('schemaVersion', 1, 'corpusVersion', 'local-demo-v101',
           'rowCount', summary.row_count, 'sha256', summary.corpus_hash,
           'documents', summary.documents),
       'system-migration', CURRENT_TIMESTAMP, s.active_release_id
FROM v101_summary summary CROSS JOIN assistant.knowledge_runtime_state s
WHERE s.singleton = TRUE AND EXISTS (SELECT 1 FROM v101_projected_runtime)
  AND NOT EXISTS (SELECT 1 FROM assistant.knowledge_release e
                  WHERE e.id = '00000000-0000-0000-0000-000000000101'::uuid);

INSERT INTO assistant.knowledge_runtime_document
    (release_id, source_id, revision_id, version, domain, slug, locale, title,
     content, source, priority, active, visibility, published_at)
SELECT '00000000-0000-0000-0000-000000000101'::uuid, source_id, revision_id, version,
       domain, slug, locale, title, content, source, priority, active, visibility,
       published_at
FROM v101_projected_runtime
WHERE EXISTS (SELECT 1 FROM assistant.knowledge_release e
              WHERE e.id = '00000000-0000-0000-0000-000000000101'::uuid)
  AND NOT EXISTS (SELECT 1 FROM assistant.knowledge_runtime_document x
                  WHERE x.release_id = '00000000-0000-0000-0000-000000000101'::uuid);

UPDATE assistant.knowledge_runtime_state s
   SET active_release_id = next_release.id, updated_at = CURRENT_TIMESTAMP
  FROM assistant.knowledge_release next_release
 WHERE s.singleton = TRUE
   AND next_release.id = '00000000-0000-0000-0000-000000000101'::uuid
   AND EXISTS (SELECT 1 FROM v101_projected_runtime);

-- 4. Publish the superseding correction notice. The V72 announcement row stays
--    untouched — institutional corrections are appended, not rewritten.
INSERT INTO engagement."Announcement" (
    "id", "title", "slug", "summary", "content", "priority", "status", "categoryId",
    "coverImageUrl", "readingTimeMinutes", "viewCount", "uniqueReaderCount", "featuredOrder",
    "displayOrder", "isGlobal", "targetRoles", "targetYears", "publishAt", "expiresAt", "publishedBy",
    "semesterId", "semesterName", "createdAt", "updatedAt", "version"
)
SELECT 'announcement-thesis-group-size-correction-v101',
       'Đính chính quy định số lượng thành viên nhóm Khóa luận tốt nghiệp (KLTN): từ 1 đến 3 sinh viên',
       'dinh-chinh-quy-dinh-so-luong-thanh-vien-nhom-kltn-1-3',
       'Quy định chính thức hiện hành: mỗi nhóm Khóa luận tốt nghiệp / Đồ án tốt nghiệp gồm từ 1 đến 3 sinh viên với đúng một nhóm trưởng. Thông báo này thay thế nội dung đính chính trước đây nêu giới hạn từ 3 đến 4 sinh viên.',
       '<p><strong>Thông báo đính chính.</strong> Phòng Đào tạo và Khoa Công nghệ Thông tin thông báo quy định chính thức hiện hành về số lượng thành viên nhóm Khóa luận tốt nghiệp (KLTN) và Đồ án tốt nghiệp:</p>
<ul>
  <li><strong>Số lượng thành viên:</strong> mỗi nhóm gồm <strong>từ 1 đến 3 sinh viên</strong>.</li>
  <li><strong>Nhóm trưởng:</strong> mỗi nhóm có <strong>đúng một nhóm trưởng</strong>; nhóm trưởng là người khởi tạo đề tài, mời thành viên và nộp báo cáo thay cho nhóm.</li>
</ul>
<p>Quy định này <strong>thay thế</strong> thông báo đính chính trước đây nêu giới hạn <em>từ 3 đến 4 sinh viên</em>. Cổng thông tin CampusUTE áp dụng đúng quy định hiện hành: một nhóm có thể chỉ có 1 thành viên và không thể vượt quá 3 thành viên.</p>
<h3>Correction notice (English)</h3>
<p>Each capstone / graduation-thesis group consists of <strong>1 to 3 students</strong> with <strong>exactly one group leader</strong>. This notice supersedes the earlier correction that described a group as 3 to 4 students.</p>
<p>Mọi thắc mắc về quy định nhóm đề nghị liên hệ Văn phòng Khoa Công nghệ Thông tin hoặc Phòng Đào tạo.</p>',
       'HIGH',
       'PUBLISHED',
       (SELECT "id" FROM engagement."ArticleCategory" WHERE "code" = 'ACADEMIC_AFFAIRS'),
       NULL,
       3,
       0,
       0,
       NULL,
       NULL,
       TRUE,
       ARRAY['STUDENT', 'LECTURER', 'ADMIN']::text[],
       ARRAY[]::integer[],
       CURRENT_TIMESTAMP,
       NULL,
       'Phòng Đào tạo & Khoa Công nghệ Thông tin',
       'semester-demo',
       'Học kỳ 1 năm học 2026-2027',
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP,
       1
WHERE NOT EXISTS (
    SELECT 1 FROM engagement."Announcement"
    WHERE "id" = 'announcement-thesis-group-size-correction-v101'
);

-- 5. Governance trail rows (V15 announcement audit + V60 admin audit), both
--    idempotent.
INSERT INTO engagement."AnnouncementAudit" (
    "id", "announcementId", "action", "actorId", "actorLabel", "reason", "version",
    "beforeState", "afterState", "createdAt"
)
SELECT 'audit-announcement-thesis-group-size-correction-v101',
       'announcement-thesis-group-size-correction-v101',
       'CREATED',
       'system-migration',
       'Hệ thống – Di trú dữ liệu (V101)',
       'Công bố thông báo đính chính mới theo brief cập nhật của Khoa: nhóm KLTN từ 1 đến 3 sinh viên, đúng một nhóm trưởng; thay thế đính chính V72 (3 đến 4). Bản tin lịch sử không bị sửa đổi.',
       1,
       NULL,
       '{"status": "PUBLISHED", "isGlobal": true, "targetRoles": ["STUDENT", "LECTURER", "ADMIN"], "rule": "1-3 members, exactly one leader", "supersedes": "announcement-thesis-group-size-correction-v72"}',
       CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM engagement."AnnouncementAudit"
    WHERE "id" = 'audit-announcement-thesis-group-size-correction-v101'
)
  AND EXISTS (
    SELECT 1 FROM engagement."Announcement"
    WHERE "id" = 'announcement-thesis-group-size-correction-v101'
  );

INSERT INTO campuscore_audit."AdminAudit" (
    "id", "actorId", "actorLabel", "action", "entityType", "entityId", "summary",
    "beforeState", "afterState", "createdAt"
) VALUES (
    'audit-thesis-group-size-correction-v101',
    'system-migration',
    'Hệ thống – Di trú dữ liệu (V101)',
    'RECONCILE_THESIS_GROUP_SIZE_DOCUMENTS',
    'KnowledgeRelease',
    '00000000-0000-0000-0000-000000000101',
    'Đối soát quy định số lượng thành viên nhóm đồ án trong tri thức trợ lý theo V100: sửa 06 tài liệu còn ghi 3 đến 4 thành viên thành 1 đến 3, chuẩn hóa nhãn CampusCore thành CampusUTE trong corpus, công bố release local-demo-v101 và thông báo đính chính thay thế đính chính V72.',
    '{"activeRelease": "'
        || COALESCE((
               SELECT prev.corpus_version
               FROM assistant.knowledge_release prev
               JOIN assistant.knowledge_release cur ON cur.previous_release_id = prev.id
               WHERE cur.id = '00000000-0000-0000-0000-000000000101'::uuid
           ), 'unknown')
        || '", "supersededRule": "3-4 members"}',
    '{"activeRelease": "local-demo-v101", "rule": "1-3 members, exactly one leader", "correctedDocuments": 6, "brandScrub": "CampusCore->CampusUTE", "correctionAnnouncement": "announcement-thesis-group-size-correction-v101"}',
    CURRENT_TIMESTAMP
)
ON CONFLICT ("id") DO NOTHING;
