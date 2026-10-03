-- V97: activate the course-prerequisite feature end to end.
--
-- The enforcement contract in RegistrationService.assertPrereqAndCoreq matches
-- kind='PREREQ'/'COREQ' exactly, but the only seeded rows (V43) spell the kind
-- 'PREREQUISITE' — 38 rows of dead data that never gated a registration, while
-- the chatbot corpus (V40) already promises the portal "tự động chặn". This
-- migration (1) normalizes the seeded kinds to the enforced contract,
-- (2) completes the prerequisite web so advanced courses across departments
-- chain from the foundations the demo transcripts already cover, and
-- (3) publishes a bilingual knowledge document generated from the live
-- CourseRequirement rows so the assistant answers per-course questions from
-- data that cannot drift from the table itself.
--
-- Safety rule for (2): every NEW required course is SE401..SE412 — exactly the
-- courses the demo transcripts (V25/V27) already completed — so demo journeys
-- keep registering their planned sections. Re-runnable: the kind UPDATE is
-- idempotent, the inserts are slug/unique-guarded, and the release chain
-- follows the V87/V95 NOT EXISTS protocol.

-- ---------------------------------------------------------------------------
-- 1. Normalize the requirement kinds to the enforced contract
-- ---------------------------------------------------------------------------
UPDATE academic."CourseRequirement"
SET "kind" = 'PREREQ'
WHERE "kind" = 'PREREQUISITE';

-- ---------------------------------------------------------------------------
-- 2. Complete the prerequisite web (kind='PREREQ', minLetterGrade='D')
--    required courses are restricted to SE401..SE412 (demo-completed set)
-- ---------------------------------------------------------------------------
INSERT INTO academic."CourseRequirement" ("id", "courseId", "requiredCourseId", "kind", "minLetterGrade")
SELECT pair.id, target."id", required."id", 'PREREQ', 'D'
FROM (VALUES
    ('req-101', 'SE013', 'SE402'), -- Web nâng cao (React/Node) sau Phát triển ứng dụng web
    ('req-102', 'SE015', 'SE410'), -- Di động đa nền tảng sau Phát triển ứng dụng di động
    ('req-103', 'SE017', 'SE407'), -- DevOps CI/CD sau DevOps và triển khai liên tục
    ('req-104', 'SE021', 'SE401'), -- Modern frameworks sau Lập trình Java nâng cao
    ('req-105', 'SE408', 'SE402'), -- Điện toán đám mây sau Phát triển ứng dụng web
    ('req-106', 'SE409', 'SE402'), -- An toàn thông tin sau Phát triển ứng dụng web
    ('req-107', 'SE410', 'SE402'), -- Phát triển ứng dụng di động sau Web
    ('req-108', 'SE412', 'SE405'), -- Đồ án chuyên ngành sau Kiến trúc phần mềm
    ('req-109', 'SE421', 'SE405'), -- Kiến trúc nâng cao sau Kiến trúc phần mềm
    ('req-110', 'SE422', 'SE406'), -- Kiểm thử BSTT sau Kiểm thử và ĐBCL
    ('req-111', 'SE423', 'SE407'), -- DevOps hệ thống sau DevOps CI/CD
    ('req-112', 'SE424', 'SE404'), -- Phân tích thiết kế HTTT sau CSDL nâng cao
    ('req-113', 'AI032', 'SE404'), -- Hệ thống gợi ý sau CSDL nâng cao
    ('req-114', 'AI401', 'SE411'), -- Học sâu sau Nhập môn AI
    ('req-115', 'AI402', 'SE411'),
    ('req-116', 'AI403', 'SE411'),
    ('req-117', 'AI404', 'SE411'),
    ('req-118', 'IS045', 'SE402'), -- Phân tích thiết kế HTTT sau Web
    ('req-119', 'IS401', 'SE402'), -- HTTT doanh nghiệp sau Web
    ('req-120', 'IS402', 'SE404'), -- Phân tích dữ liệu KD sau CSDL nâng cao
    ('req-121', 'IS403', 'SE404'), -- Quản trị dữ liệu sau CSDL nâng cao
    ('req-122', 'NET041', 'SE403'), -- Mật mã học sau CSTL & giải thuật
    ('req-123', 'NET042', 'SE409'), -- An ninh Linux/server sau An toàn thông tin
    ('req-124', 'NS401', 'SE409'),
    ('req-125', 'NS402', 'SE403'),
    ('req-126', 'NS403', 'SE409'),
    ('req-127', 'NS404', 'SE409'),
    ('req-128', 'LS404', 'SE402'), -- TMĐT logistics sau Web
    ('req-129', 'INT094', 'SE403'), -- Blockchain sau CSTL & giải thuật
    ('req-130', 'INT096', 'SE402'), -- RPA sau Web
    ('req-131', 'INT098', 'SE409')  -- Quản trị an ninh mạng sau An toàn thông tin
) AS pair(id, target_code, required_code)
JOIN academic."Course" target ON target."code" = pair.target_code
JOIN academic."Course" required ON required."code" = pair.required_code
ON CONFLICT ("courseId", "requiredCourseId", "kind") DO NOTHING;

-- ---------------------------------------------------------------------------
-- 3. Knowledge release: a bilingual prerequisite map GENERATED from the table
-- ---------------------------------------------------------------------------
INSERT INTO assistant.knowledge_document
    (id, slug, locale, title, content, source, priority, domain)
SELECT md5(seed.slug || '-document')::uuid, seed.slug, seed.locale, seed.title, seed.body,
       'campuscore-prerequisite-map', 20, 'ACADEMIC_CATALOG'
FROM (
    SELECT 'catalog-prerequisite-map-vi' AS slug, 'vi' AS locale,
           'Bản đồ học phần tiên quyết toàn trường' AS title,
           E'## Học phần tiên quyết là gì\n\n- Học phần tiên quyết là môn bạn phải **hoàn thành và đạt** (không tính F/W) trước khi được đăng ký môn phía sau; cổng tự động chặn đăng ký khi còn thiếu.\n- Bản đồ dưới đây liệt kê đầy đủ theo dữ liệu đăng ký của trường; hỏi theo mã môn (ví dụ: "tiên quyết của SE421") sẽ ra đúng danh sách.\n\n## Danh sách chi tiết\n\n' ||
           COALESCE((
               SELECT string_agg(
                   '- **' || t.code || '** — ' || COALESCE(t."nameVi", t.name) || E'\n  - Học phần tiên quyết: '
                   || r.code || ' — ' || COALESCE(r."nameVi", r.name),
                   E'\n' ORDER BY t.code)
           FROM academic."CourseRequirement" req
           JOIN academic."Course" t ON t."id" = req."courseId"
           JOIN academic."Course" r ON r."id" = req."requiredCourseId"
           WHERE req."kind" = 'PREREQ'))
           || E'\n\n## Lưu ý\n\n- Ngưỡng điểm tối thiểu hiện áp dụng mức Đạt (D); môn chưa có điểm công bố chưa được tính là đã hoàn thành.\n- Học phần song hành (học cùng kỳ) và quy định học trước chi tiết xem ở mục quy chế đăng ký học phần.'
    UNION ALL
    SELECT 'catalog-prerequisite-map-en' AS slug, 'en' AS locale,
           'Campus-wide course prerequisite map' AS title,
           E'## What a prerequisite is\n\n- A prerequisite is a course you must **complete with a passing grade** (F/W do not count) before registering the follow-up course; the portal blocks the registration automatically when it is missing.\n- The map below lists every chain from the registration data; ask by course code (for example "prerequisites of SE421") for the exact list.\n\n## Detailed list\n\n' ||
           COALESCE((
               SELECT string_agg(
                   '- **' || t.code || '** — ' || COALESCE(t."nameEn", t.name) || E'\n  - Prerequisite: '
                   || r.code || ' — ' || COALESCE(r."nameEn", r.name),
                   E'\n' ORDER BY t.code)
           FROM academic."CourseRequirement" req
           JOIN academic."Course" t ON t."id" = req."courseId"
           JOIN academic."Course" r ON r."id" = req."requiredCourseId"
           WHERE req."kind" = 'PREREQ'))
           || E'\n\n## Notes\n\n- The current minimum threshold is a pass (D); a course without a published grade does not count as completed yet.\n- Corequisite pairs and the detailed prior-learning rules live in the registration regulations topic.'
) AS seed(slug, locale, title, body)
WHERE NOT EXISTS (SELECT 1 FROM assistant.knowledge_document existing WHERE existing.slug = seed.slug);

INSERT INTO assistant.knowledge_document_revision
    (id, document_id, version, state, locale, slug, title, content, source, priority, created_by, reviewed_by, published_at, domain)
SELECT md5(d.id::text || '-revision-1')::uuid, d.id, 1, 'PUBLISHED', d.locale, d.slug, d.title, d.content,
       d.source, d.priority, 'system-seed', 'system-seed', CURRENT_TIMESTAMP, d.domain
FROM assistant.knowledge_document d
WHERE d.source = 'campuscore-prerequisite-map'
  AND NOT EXISTS (SELECT 1 FROM assistant.knowledge_document_revision r WHERE r.document_id = d.id AND r.version = 1);

WITH canonical AS (
    SELECT d.id::text AS source_id, r.version, COALESCE(r.domain, d.domain, 'THESIS') AS domain,
           d.slug, d.locale, d.title, d.content, d.source, d.priority
    FROM assistant.knowledge_document d
    JOIN assistant.knowledge_document_revision r ON r.document_id = d.id AND r.state = 'PUBLISHED'
    WHERE d.active = TRUE AND d.visibility = 'PUBLIC'
), summary AS (
    SELECT encode(thesis.digest(COALESCE(string_agg(concat_ws('|', source_id, domain, slug, locale, title, content, source, priority::text, version::text), E'\n' ORDER BY source_id), ''), 'sha256'), 'hex') AS corpus_hash,
           COUNT(*)::integer AS row_count,
           COALESCE(jsonb_agg(jsonb_build_object('sourceId', source_id, 'domain', domain, 'slug', slug, 'locale', locale) ORDER BY source_id), '[]'::jsonb) AS documents
    FROM canonical
)
INSERT INTO assistant.knowledge_release (id, corpus_version, corpus_hash, row_count, source, status, manifest, created_by, activated_at, previous_release_id)
SELECT '00000000-0000-0000-0000-000000000097'::uuid, 'local-demo-v97', corpus_hash, row_count, 'MANUAL', 'PUBLISHED',
       jsonb_build_object('schemaVersion', 1, 'corpusVersion', 'local-demo-v97', 'rowCount', row_count, 'sha256', corpus_hash, 'documents', documents),
       'system-migration', CURRENT_TIMESTAMP,
       (SELECT active_release_id FROM assistant.knowledge_runtime_state WHERE singleton = TRUE)
FROM summary
WHERE NOT EXISTS (SELECT 1 FROM assistant.knowledge_release WHERE id = '00000000-0000-0000-0000-000000000097'::uuid);

INSERT INTO assistant.knowledge_runtime_document
    (release_id, source_id, revision_id, version, domain, slug, locale, title, content, source, priority, active, visibility, published_at)
SELECT '00000000-0000-0000-0000-000000000097'::uuid, d.id::text, r.id, r.version,
       COALESCE(r.domain, d.domain, 'THESIS'), d.slug, d.locale, d.title, d.content, d.source, d.priority,
       TRUE, 'PUBLIC', COALESCE(r.published_at, CURRENT_TIMESTAMP)
FROM assistant.knowledge_document d
JOIN assistant.knowledge_document_revision r ON r.document_id = d.id AND r.state = 'PUBLISHED'
WHERE d.active = TRUE AND d.visibility = 'PUBLIC'
  AND NOT EXISTS (
      SELECT 1 FROM assistant.knowledge_runtime_document p
      WHERE p.release_id = '00000000-0000-0000-0000-000000000097'::uuid AND p.source_id = d.id::text
  );

INSERT INTO assistant.knowledge_runtime_state (singleton, active_release_id)
VALUES (TRUE, '00000000-0000-0000-0000-000000000097'::uuid)
ON CONFLICT (singleton) DO UPDATE
SET active_release_id = EXCLUDED.active_release_id, updated_at = CURRENT_TIMESTAMP;
