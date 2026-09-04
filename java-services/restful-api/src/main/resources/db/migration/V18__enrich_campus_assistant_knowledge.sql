-- Governed bilingual knowledge additions for the local release projection.
-- Documents are public, synthetic and immutable after publication.

INSERT INTO assistant.knowledge_document
    (id, slug, locale, title, content, source, priority, domain)
SELECT md5(seed.slug || '-document')::uuid, seed.slug, seed.locale, seed.title, seed.content,
       'campuscore-demo-catalog', seed.priority, seed.domain
FROM (VALUES
    ('registration-window-vi', 'vi', 'Đợt đăng ký học phần', 'CampusCore hiển thị các lớp thuộc đợt REGISTRATION đang mở. Khi đợt đăng ký chính kết thúc nhưng đợt ADD_DROP còn mở, sinh viên vẫn có thể tra cứu danh mục và thực hiện thao tác được hệ thống cho phép. Hãy kiểm tra thời gian, trạng thái lớp, số chỗ và cảnh báo trùng lịch trước khi xác nhận.', 80, 'REGISTRATION'),
    ('registration-window-en', 'en', 'Course registration windows', 'CampusCore lists sections from the active REGISTRATION window. When the main window has ended but ADD_DROP remains open, students can still browse the catalog and use operations allowed by the system. Check dates, section status, remaining seats, and schedule conflicts before confirming.', 80, 'REGISTRATION'),
    ('catalog-course-fields-vi', 'vi', 'Thông tin cần biết về học phần', 'Mỗi học phần trong danh mục gồm mã, tên, số tín chỉ, lớp học phần, số chỗ còn lại và lịch học. Mô tả học phần giải thích mục tiêu và phạm vi nội dung; dữ liệu lịch và số chỗ là căn cứ để chọn lớp phù hợp.', 90, 'ACADEMIC_CATALOG'),
    ('catalog-course-fields-en', 'en', 'Course catalog fields', 'Each catalog item includes a code, title, credit value, section, remaining seats, and meeting schedule. The course description explains objectives and scope; schedule and seat data help students choose a suitable section.', 90, 'ACADEMIC_CATALOG'),
    ('catalog-enriched-se403-se412-vi', 'vi', 'Danh mục học phần công nghệ phần mềm', 'Danh mục demo học kỳ 1 có các học phần SE403 Cấu trúc dữ liệu và giải thuật, SE404 Cơ sở dữ liệu nâng cao, SE405 Kiến trúc phần mềm, SE406 Kiểm thử và đảm bảo chất lượng, SE407 DevOps và triển khai liên tục, SE408 Điện toán đám mây, SE409 An toàn thông tin ứng dụng, SE410 Phát triển ứng dụng di động, SE411 Nhập môn trí tuệ nhân tạo và SE412 Đồ án chuyên ngành. Các lớp đều có mô tả song ngữ, tín chỉ, phòng và lịch mẫu.', 70, 'ACADEMIC_CATALOG'),
    ('catalog-enriched-se403-se412-en', 'en', 'Software engineering course catalog', 'The demo semester includes SE403 Data Structures and Algorithms, SE404 Advanced Database Systems, SE405 Software Architecture, SE406 Software Testing and Quality Assurance, SE407 DevOps and Continuous Delivery, SE408 Cloud Computing, SE409 Application Security, SE410 Mobile Application Development, SE411 Introduction to Artificial Intelligence, and SE412 Software Engineering Capstone. Each section has bilingual descriptions, credits, a room, and a sample schedule.', 70, 'ACADEMIC_CATALOG'),
    ('assistant-citation-policy-vi', 'vi', 'Nguyên tắc trả lời có nguồn', 'Trợ lý CampusCore chỉ dùng các tài liệu PUBLIC đã được phát hành trong kho tri thức. Câu trả lời về học phần, đăng ký và chính sách phải nêu nguồn hoặc slug tài liệu; nếu dữ liệu không đủ, trợ lý cần nói rõ giới hạn và hướng dẫn người dùng kiểm tra trên cổng học vụ.', 60, 'GENERAL_FAQ'),
    ('assistant-citation-policy-en', 'en', 'Grounded answer policy', 'The CampusCore assistant uses only PUBLIC documents in the activated knowledge release. Answers about courses, registration, and policies should identify a source or document slug; when evidence is insufficient, the assistant must state the limitation and direct the user to verify in the academic portal.', 60, 'GENERAL_FAQ')
) AS seed(slug, locale, title, content, priority, domain)
WHERE NOT EXISTS (SELECT 1 FROM assistant.knowledge_document existing WHERE existing.slug = seed.slug);

INSERT INTO assistant.knowledge_document_revision
    (id, document_id, version, state, locale, slug, title, content, source, priority, created_by, reviewed_by, published_at, domain)
SELECT md5(d.id::text || '-revision-1')::uuid, d.id, 1, 'PUBLISHED', d.locale, d.slug, d.title, d.content,
       d.source, d.priority, 'system-seed', 'system-seed', CURRENT_TIMESTAMP, d.domain
FROM assistant.knowledge_document d
WHERE d.source = 'campuscore-demo-catalog'
  AND NOT EXISTS (SELECT 1 FROM assistant.knowledge_document_revision r WHERE r.document_id = d.id AND r.version = 1);

WITH canonical AS (
    SELECT d.id::text AS source_id, r.id AS revision_id, r.version, COALESCE(r.domain, d.domain, 'THESIS') AS domain,
           COALESCE(r.slug, d.slug) AS slug, COALESCE(r.locale, d.locale) AS locale,
           COALESCE(r.title, d.title) AS title, COALESCE(r.content, d.content) AS content,
           COALESCE(r.source, d.source) AS source, COALESCE(r.priority, d.priority) AS priority,
           r.published_at
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
SELECT '00000000-0000-0000-0000-000000000018'::uuid, 'local-demo-v18', corpus_hash, row_count, 'MANUAL', 'PUBLISHED',
       jsonb_build_object('schemaVersion', 1, 'corpusVersion', 'local-demo-v18', 'rowCount', row_count, 'sha256', corpus_hash, 'documents', documents),
       'system-migration', CURRENT_TIMESTAMP,
       (SELECT active_release_id FROM assistant.knowledge_runtime_state WHERE singleton = TRUE)
FROM summary
WHERE NOT EXISTS (SELECT 1 FROM assistant.knowledge_release WHERE id = '00000000-0000-0000-0000-000000000018'::uuid);

INSERT INTO assistant.knowledge_runtime_document
    (release_id, source_id, revision_id, version, domain, slug, locale, title, content, source, priority, active, visibility, published_at)
SELECT '00000000-0000-0000-0000-000000000018'::uuid, d.id::text, r.id, r.version,
       COALESCE(r.domain, d.domain, 'THESIS'), d.slug, d.locale, d.title, d.content, d.source, d.priority,
       TRUE, 'PUBLIC', COALESCE(r.published_at, CURRENT_TIMESTAMP)
FROM assistant.knowledge_document d
JOIN assistant.knowledge_document_revision r ON r.document_id = d.id AND r.state = 'PUBLISHED'
WHERE d.active = TRUE AND d.visibility = 'PUBLIC'
  AND NOT EXISTS (
      SELECT 1 FROM assistant.knowledge_runtime_document p
      WHERE p.release_id = '00000000-0000-0000-0000-000000000018'::uuid AND p.source_id = d.id::text
  );

INSERT INTO assistant.knowledge_runtime_state (singleton, active_release_id)
VALUES (TRUE, '00000000-0000-0000-0000-000000000018'::uuid)
ON CONFLICT (singleton) DO UPDATE
SET active_release_id = EXCLUDED.active_release_id, updated_at = CURRENT_TIMESTAMP;
