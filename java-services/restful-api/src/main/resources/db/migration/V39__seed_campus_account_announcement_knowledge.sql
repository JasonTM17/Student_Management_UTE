-- Completes the requirement specification's final paragraph: besides thesis
-- features, the system supports user account management, official school and
-- faculty announcements, permission handling, and data reporting. The
-- assistant needs grounded guidance for these so the last requirement line of
-- the specification is answerable. Same governance shape as V20/V38.

INSERT INTO assistant.knowledge_document
    (id, slug, locale, title, content, source, priority, domain)
SELECT md5(seed.slug || '-document')::uuid, seed.slug, seed.locale, seed.title, seed.content,
       'campuscore-campus-services-guide', seed.priority, 'GENERAL_FAQ'
FROM (VALUES
    ('campus-accounts-notifications-vi', 'vi', 'Tài khoản người dùng và thông báo chính thức',
     'Ngoài các chức năng liên quan đến đề tài, hệ thống còn hỗ trợ quản lý tài khoản người dùng và đăng tải các thông báo chính thức của nhà trường và khoa để cung cấp thông tin kịp thời cho người sử dụng. Sinh viên và giảng viên quản lý hồ sơ, mật khẩu và thông báo của mình trong mục Cài đặt hồ sơ và Thông báo trên bảng điều khiển; khi cần cấp lại tài khoản hoặc điều chỉnh quyền truy cập, hãy liên hệ Phòng học vụ hoặc Trưởng khoa phụ trách.',
     30),
    ('campus-accounts-notifications-en', 'en', 'User accounts and official announcements',
     'Beyond thesis features, the system supports user account management and the publication of official notices from the university and the faculty so users receive timely information. Students and lecturers manage profiles, passwords, and notifications in the Settings and Announcements areas of the dashboard; for account reissue or access changes, contact the Academic Office or the faculty head.',
     30),
    ('campus-permissions-reports-vi', 'vi', 'Phân quyền và báo cáo dữ liệu của khoa',
     'Mỗi tài khoản chỉ nhìn thấy dữ liệu thuộc phạm vi quyền của mình: sinh viên thấy hồ sơ, điểm và đồ án của chính mình; giảng viên thấy lớp được phân công, đề tài hướng dẫn và bảng chấm điểm được giao; Trưởng khoa tạo đợt đăng ký, phân công giảng viên hướng dẫn và phản biện, thành lập hội đồng, chấm điểm và công bố kết quả cho toàn khoa; quản trị viên vận hành toàn hệ thống. Báo cáo dữ liệu được xuất trực tiếp từ các trang danh sách (đề tài, nhóm, kết quả) trong khu quản trị của khoa.',
     32),
    ('campus-permissions-reports-en', 'en', 'Permissions and faculty data reports',
     'Each account only sees data within its permission scope: students see their own records, grades, and thesis; lecturers see assigned classes, supervised topics, and their grading sheets; the faculty head creates registration rounds, assigns supervisors and reviewers, forms defense councils, grades, and publishes results for the faculty; administrators operate the whole system. Reports can be exported directly from the faculty admin list pages (topics, groups, results).',
     32)
) AS seed(slug, locale, title, content, priority)
WHERE NOT EXISTS (SELECT 1 FROM assistant.knowledge_document existing WHERE existing.slug = seed.slug);

INSERT INTO assistant.knowledge_document_revision
    (id, document_id, version, state, locale, slug, title, content, source, priority, created_by, reviewed_by, published_at, domain)
SELECT md5(d.id::text || '-revision-1')::uuid, d.id, 1, 'PUBLISHED', d.locale, d.slug, d.title, d.content,
       d.source, d.priority, 'system-seed', 'system-seed', CURRENT_TIMESTAMP, d.domain
FROM assistant.knowledge_document d
WHERE d.source = 'campuscore-campus-services-guide'
  AND NOT EXISTS (SELECT 1 FROM assistant.knowledge_document_revision r WHERE r.document_id = d.id AND r.version = 1);

WITH canonical AS (
    SELECT d.id::text AS source_id, r.version, COALESCE(r.domain, d.domain, 'THESIS') AS domain,
           d.slug, d.locale, d.title, d.content, d.source, d.priority
    FROM assistant.knowledge_document d
    JOIN assistant.knowledge_document_revision r ON r.document_id = d.id AND r.state = 'PUBLISHED'
    WHERE d.active = TRUE AND d.visibility = 'PUBLIC'
), summary AS (
    SELECT encode(digest(COALESCE(string_agg(concat_ws('|', source_id, domain, slug, locale, title, content, source, priority::text, version::text), E'\n' ORDER BY source_id), ''), 'sha256'), 'hex') AS corpus_hash,
           COUNT(*)::integer AS row_count,
           COALESCE(jsonb_agg(jsonb_build_object('sourceId', source_id, 'domain', domain, 'slug', slug, 'locale', locale) ORDER BY source_id), '[]'::jsonb) AS documents
    FROM canonical
)
INSERT INTO assistant.knowledge_release (id, corpus_version, corpus_hash, row_count, source, status, manifest, created_by, activated_at, previous_release_id)
SELECT '00000000-0000-0000-0000-000000000039'::uuid, 'local-demo-v39', corpus_hash, row_count, 'MANUAL', 'PUBLISHED',
       jsonb_build_object('schemaVersion', 1, 'corpusVersion', 'local-demo-v39', 'rowCount', row_count, 'sha256', corpus_hash, 'documents', documents),
       'system-migration', CURRENT_TIMESTAMP,
       (SELECT active_release_id FROM assistant.knowledge_runtime_state WHERE singleton = TRUE)
FROM summary
WHERE NOT EXISTS (SELECT 1 FROM assistant.knowledge_release WHERE id = '00000000-0000-0000-0000-000000000039'::uuid);

INSERT INTO assistant.knowledge_runtime_document
    (release_id, source_id, revision_id, version, domain, slug, locale, title, content, source, priority, active, visibility, published_at)
SELECT '00000000-0000-0000-0000-000000000039'::uuid, d.id::text, r.id, r.version,
       COALESCE(r.domain, d.domain, 'THESIS'), d.slug, d.locale, d.title, d.content, d.source, d.priority,
       TRUE, 'PUBLIC', COALESCE(r.published_at, CURRENT_TIMESTAMP)
FROM assistant.knowledge_document d
JOIN assistant.knowledge_document_revision r ON r.document_id = d.id AND r.state = 'PUBLISHED'
WHERE d.active = TRUE AND d.visibility = 'PUBLIC'
  AND NOT EXISTS (
      SELECT 1 FROM assistant.knowledge_runtime_document p
      WHERE p.release_id = '00000000-0000-0000-0000-000000000039'::uuid AND p.source_id = d.id::text
  );

INSERT INTO assistant.knowledge_runtime_state (singleton, active_release_id)
VALUES (TRUE, '00000000-0000-0000-0000-000000000039'::uuid)
ON CONFLICT (singleton) DO UPDATE
SET active_release_id = EXCLUDED.active_release_id, updated_at = CURRENT_TIMESTAMP;
