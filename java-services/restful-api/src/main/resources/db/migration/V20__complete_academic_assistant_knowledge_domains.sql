INSERT INTO assistant.knowledge_document
    (id, slug, locale, title, content, source, priority, domain)
SELECT md5(seed.slug || '-document')::uuid, seed.slug, seed.locale, seed.title, seed.content,
       'campuscore-demo-academic-guide', 65, seed.domain
FROM (VALUES
    ('schedule-seats-vi', 'vi', 'Lịch học và số chỗ', 'Lịch học thuộc từng lớp học phần, gồm thứ, giờ bắt đầu, giờ kết thúc và phòng. Số chỗ còn lại được tính từ sức chứa trừ số đã đăng ký; hãy đọc cả cảnh báo trùng lịch trước khi bấm Đăng ký.', 'ACADEMIC_CATALOG'),
    ('schedule-seats-en', 'en', 'Schedules and remaining seats', 'Each section has a weekday, start time, end time, and room. Remaining seats equal capacity minus enrolled students; review schedule conflict warnings before selecting Register.', 'ACADEMIC_CATALOG'),
    ('grades-transcript-vi', 'vi', 'Điểm số và bảng điểm', 'Trang Điểm hiển thị kết quả đã công bố. Bảng điểm tích lũy các môn đã hoàn tất hoặc đã được công bố; nếu chưa có kết quả, trạng thái trống là chính xác và sẽ tự có dữ liệu sau khi giảng viên công bố điểm.', 'POLICY'),
    ('grades-transcript-en', 'en', 'Grades and transcript', 'The Grades page shows published results. The Transcript accumulates completed or published courses; when no result is available, the empty state is truthful and data appears after an instructor publishes grades.', 'POLICY'),
    ('announcements-vi', 'vi', 'Thông báo học vụ', 'Trang Thông báo tập hợp cập nhật theo học kỳ, lớp học phần và thông tin chung. Hãy ưu tiên thông báo mới, đọc hạn hiệu lực và đối chiếu với lịch đăng ký trước khi thực hiện thao tác.', 'ANNOUNCEMENT'),
    ('announcements-en', 'en', 'Academic announcements', 'The Announcements page brings together semester, section, and global updates. Prioritize recent notices, check their validity period, and compare them with the registration calendar before taking action.', 'ANNOUNCEMENT')
) AS seed(slug, locale, title, content, domain)
WHERE NOT EXISTS (SELECT 1 FROM assistant.knowledge_document existing WHERE existing.slug = seed.slug);

INSERT INTO assistant.knowledge_document_revision
    (id, document_id, version, state, locale, slug, title, content, source, priority, created_by, reviewed_by, published_at, domain)
SELECT md5(d.id::text || '-revision-1')::uuid, d.id, 1, 'PUBLISHED', d.locale, d.slug, d.title, d.content,
       d.source, d.priority, 'system-seed', 'system-seed', CURRENT_TIMESTAMP, d.domain
FROM assistant.knowledge_document d
WHERE d.source = 'campuscore-demo-academic-guide'
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
SELECT '00000000-0000-0000-0000-000000000020'::uuid, 'local-demo-v20', corpus_hash, row_count, 'MANUAL', 'PUBLISHED',
       jsonb_build_object('schemaVersion', 1, 'corpusVersion', 'local-demo-v20', 'rowCount', row_count, 'sha256', corpus_hash, 'documents', documents),
       'system-migration', CURRENT_TIMESTAMP,
       (SELECT active_release_id FROM assistant.knowledge_runtime_state WHERE singleton = TRUE)
FROM summary
WHERE NOT EXISTS (SELECT 1 FROM assistant.knowledge_release WHERE id = '00000000-0000-0000-0000-000000000020'::uuid);

INSERT INTO assistant.knowledge_runtime_document
    (release_id, source_id, revision_id, version, domain, slug, locale, title, content, source, priority, active, visibility, published_at)
SELECT '00000000-0000-0000-0000-000000000020'::uuid, d.id::text, r.id, r.version,
       COALESCE(r.domain, d.domain, 'THESIS'), d.slug, d.locale, d.title, d.content, d.source, d.priority,
       TRUE, 'PUBLIC', COALESCE(r.published_at, CURRENT_TIMESTAMP)
FROM assistant.knowledge_document d
JOIN assistant.knowledge_document_revision r ON r.document_id = d.id AND r.state = 'PUBLISHED'
WHERE d.active = TRUE AND d.visibility = 'PUBLIC'
  AND NOT EXISTS (
      SELECT 1 FROM assistant.knowledge_runtime_document p
      WHERE p.release_id = '00000000-0000-0000-0000-000000000020'::uuid AND p.source_id = d.id::text
  );

INSERT INTO assistant.knowledge_runtime_state (singleton, active_release_id)
VALUES (TRUE, '00000000-0000-0000-0000-000000000020'::uuid)
ON CONFLICT (singleton) DO UPDATE
SET active_release_id = EXCLUDED.active_release_id, updated_at = CURRENT_TIMESTAMP;
