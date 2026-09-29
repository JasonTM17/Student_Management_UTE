-- Chatbot "answer everything" knowledge fills (chatbot-smoothness wave):
-- the five questions the production battery showed falling through to the
-- fallback or paying the 4-10 s remote round-trip. Content is curated from
-- real seeded data (faculties/departments read from the live academic
-- catalog) plus deliberately conservative policy wording — where the portal
-- does not track a fact (tuition rates, dormitory allocation, exam rooms),
-- the document says so and routes to the responsible office instead of
-- inventing specifics.

INSERT INTO assistant.knowledge_document
    (id, slug, locale, title, content, source, priority, domain)
SELECT md5(seed.slug || '-document')::uuid, seed.slug, seed.locale, seed.title, seed.content,
       'campuscore-campus-faq-guide', seed.priority, seed.domain
FROM (VALUES
    ('campus-faculties-majors-vi', 'vi', 'Các khoa và ngành đào tạo của trường', 'Trường hiện có 6 khoa đào tạo: Khoa Công nghệ Thông tin, Khoa Cơ khí Chế tạo máy, Khoa Điện - Điện tử, Khoa Kinh tế, Khoa Ngoại ngữ và Khoa Xây dựng. Riêng Khoa Công nghệ Thông tin gồm các bộ môn: Công nghệ Phần mềm; Hệ thống Thông tin & Dữ liệu lớn; Mạng máy tính & An toàn thông tin; Trí tuệ Nhân tạo & Khoa học Dữ liệu. Bạn xem danh mục học phần theo khoa và bộ môn ở trang Môn học, còn chương trình đào tạo theo từng học kỳ ở mục Học kỳ của cổng học vụ.', 75, 'GENERAL_FAQ'),
    ('campus-faculties-majors-en', 'en', 'Faculties and majors of the university', 'The university currently has 6 faculties: Information Technology, Mechanical Engineering, Electrical - Electronics, Economics, Foreign Languages, and Civil Engineering. The Information Technology faculty includes these departments: Software Engineering; Information Systems & Big Data; Computer Networks & Information Security; Artificial Intelligence & Data Science. Browse the course catalog per faculty on the Courses page, and per-semester programs under Semesters in the portal.', 75, 'GENERAL_FAQ'),
    ('campus-exam-schedule-vi', 'vi', 'Lịch thi cuối kỳ và các kỳ thi', 'Lịch thi cuối kỳ và lịch thi học phần do Phòng Đào tạo cùng Phòng Khảo thí công bố cho từng học kỳ, thường vào giữa và cuối kỳ; cổng học vụ CampusCore chưa hiển thị lịch thi chi tiết từng môn. Trong đợt thi, thư viện và khu tự học mở cửa phục vụ ôn tập. Bạn theo dõi thông báo chính thức ở trang Thông báo và liên hệ Phòng Đào tạo khi cần xác nhận thời gian hoặc phòng thi cụ thể.', 75, 'GENERAL_FAQ'),
    ('campus-exam-schedule-en', 'en', 'Final exam and assessment schedule', 'Final and course exam timetables are announced by the Academic Affairs and Testing offices for each semester, typically mid-semester and at the end; the CampusCore portal does not list per-course exam schedules. During the exam period the library and study areas stay open for revision. Watch the Announcements page for official notices and contact Academic Affairs to confirm a specific exam time or room.', 75, 'GENERAL_FAQ'),
    ('campus-tuition-vi', 'vi', 'Học phí và chính sách miễn giảm', 'Mức học phí mỗi tín chỉ theo từng ngành và từng năm học do trường công bố trong thông báo của Phòng Đào tạo; cổng học vụ CampusCore chưa hiển thị biểu phí. Chính sách miễn giảm học phí dành cho sinh viên thuộc đối tượng chính sách, gia đình khó khăn, cùng học bổng khuyến khích học tập, được xét theo quy chế hiện hành. Bạn liên hệ Phòng Đào tạo hoặc Phòng Công tác Sinh viên để biết mức phí chính xác và hồ sơ xét miễn giảm.', 70, 'POLICY'),
    ('campus-tuition-en', 'en', 'Tuition fees and fee waivers', 'Per-credit tuition differs by program and academic year and is announced by the Academic Affairs office; the CampusCore portal does not display fee tables. Fee-waiver and reduction policies for eligible students, plus merit scholarships, are granted under the current regulations. Contact Academic Affairs or Student Affairs for exact rates and waiver paperwork.', 70, 'POLICY'),
    ('campus-graduation-conditions-vi', 'vi', 'Điều kiện xét tốt nghiệp', 'Về nguyên tắc, sinh viên được xét tốt nghiệp khi: (1) hoàn thành toàn bộ học phần bắt buộc và đủ số tín chỉ của chương trình đào tạo — bạn theo dõi tín chỉ còn lại ở Trang tổng quan hoặc mục Đăng ký học phần; (2) hoàn thành nội dung đồ án, khóa luận tốt nghiệp hoặc nghiên cứu khoa học theo quy chế — theo dõi ở trang Đồ án – Khóa luận; (3) kết quả học tập và điểm rèn luyện đạt mức theo quy định; (4) không đang trong thời gian bị xem xét kỷ luật. Điều kiện chi tiết theo từng chương trình do trường quy định, bạn nên xác nhận với Phòng Đào tạo trước kỳ xét cuối cùng.', 70, 'POLICY'),
    ('campus-graduation-conditions-en', 'en', 'Graduation requirements', 'In principle, students are eligible for graduation when they: (1) complete all mandatory courses and the total credits of their program — track your remaining credits on the Dashboard or Course registration pages; (2) complete their thesis, capstone or research project under the current regulations — tracked on the Thesis page; (3) meet the academic and conduct score thresholds; (4) are not under disciplinary review. Detailed per-program conditions are set by the university — confirm with Academic Affairs before your final assessment.', 70, 'POLICY'),
    ('campus-dormitory-vi', 'vi', 'Ký túc xá và đăng ký nội trú', 'Việc đăng ký chỗ ở ký túc xá do đơn vị ký túc xá và công tác sinh viên của trường tổ chức theo đợt, thường đầu mỗi năm học hoặc học kỳ, và không thực hiện trực tiếp trên cổng CampusCore. Quy chế nội trú quy định đối tượng ưu tiên và nghĩa vụ cư trú của sinh viên ở nội trú. Bạn liên hệ Phòng Công tác Sinh viên hoặc văn phòng ký túc xá để đăng ký, biết lịch nhận phòng và các đợt mở mới.', 65, 'GENERAL_FAQ'),
    ('campus-dormitory-en', 'en', 'Dormitory and on-campus housing', 'Dormitory registration is run by the university''s housing and student-affairs office in waves, usually at the start of each academic year or semester, and is not handled inside the CampusCore portal. Residence regulations define priority groups and resident obligations. Contact Student Affairs or the dormitory office to register and to learn room-allocation dates.', 65, 'GENERAL_FAQ')
) AS seed(slug, locale, title, content, priority, domain)
WHERE NOT EXISTS (SELECT 1 FROM assistant.knowledge_document existing WHERE existing.slug = seed.slug);

INSERT INTO assistant.knowledge_document_revision
    (id, document_id, version, state, locale, slug, title, content, source, priority, created_by, reviewed_by, published_at, domain)
SELECT md5(d.id::text || '-revision-1')::uuid, d.id, 1, 'PUBLISHED', d.locale, d.slug, d.title, d.content,
       d.source, d.priority, 'system-seed', 'system-seed', CURRENT_TIMESTAMP, d.domain
FROM assistant.knowledge_document d
WHERE d.source = 'campuscore-campus-faq-guide'
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
SELECT '00000000-0000-0000-0000-000000000087'::uuid, 'local-demo-v87', corpus_hash, row_count, 'MANUAL', 'PUBLISHED',
       jsonb_build_object('schemaVersion', 1, 'corpusVersion', 'local-demo-v87', 'rowCount', row_count, 'sha256', corpus_hash, 'documents', documents),
       'system-migration', CURRENT_TIMESTAMP,
       (SELECT active_release_id FROM assistant.knowledge_runtime_state WHERE singleton = TRUE)
FROM summary
WHERE NOT EXISTS (SELECT 1 FROM assistant.knowledge_release WHERE id = '00000000-0000-0000-0000-000000000087'::uuid);

INSERT INTO assistant.knowledge_runtime_document
    (release_id, source_id, revision_id, version, domain, slug, locale, title, content, source, priority, active, visibility, published_at)
SELECT '00000000-0000-0000-0000-000000000087'::uuid, d.id::text, r.id, r.version,
       COALESCE(r.domain, d.domain, 'THESIS'), d.slug, d.locale, d.title, d.content, d.source, d.priority,
       TRUE, 'PUBLIC', COALESCE(r.published_at, CURRENT_TIMESTAMP)
FROM assistant.knowledge_document d
JOIN assistant.knowledge_document_revision r ON r.document_id = d.id AND r.state = 'PUBLISHED'
WHERE d.active = TRUE AND d.visibility = 'PUBLIC'
  AND NOT EXISTS (
      SELECT 1 FROM assistant.knowledge_runtime_document p
      WHERE p.release_id = '00000000-0000-0000-0000-000000000087'::uuid AND p.source_id = d.id::text
  );

INSERT INTO assistant.knowledge_runtime_state (singleton, active_release_id)
VALUES (TRUE, '00000000-0000-0000-0000-000000000087'::uuid)
ON CONFLICT (singleton) DO UPDATE
SET active_release_id = EXCLUDED.active_release_id, updated_at = CURRENT_TIMESTAMP;
