-- Chatbot audit round 2 reconciliation (workflow dwfrun-96e38df4, findings
-- kien-thuc-1 / kien-thuc-2):
--
-- kien-thuc-2: the corpus shipped two contradictory deferral rules — V41's
-- exam-regulations-and-absenteeism (petition within 7 working days of the
-- exam) and V43's exam-deferral-re-evaluation-rules (3 working days plus a
-- district-hospital certificate plus a no-extra-fee promise). Retrieval
-- ranked the V43 entry first, so students were quoted the 3-day rule. The
-- deferral section below is realigned to V41's canonical wording; the
-- re-evaluation (phúc khảo) section is unique to V43 and is kept as-is.
--
-- kien-thuc-1: "Điểm A quy đổi ra mấy điểm 4?" was answered with a denial —
-- the conversion table exists in V41's curriculum-gpa-weighting document, but
-- its title carries no "quy đổi" wording, so the retrieval window missed it.
-- That document gets a conversion-bearing title, and a dedicated high-priority
-- FAQ document now owns the conversion table verbatim.

-- 1. Realign the deferral section of exam-deferral-re-evaluation-rules with
--    V41's canonical rule; keep the re-evaluation section verbatim.
UPDATE assistant.knowledge_document
SET content = 'Quy định về hoãn thi và chấm phúc khảo bài thi cuối kỳ: 1. Thủ tục hoãn thi: Sinh viên vắng thi vì lý do bất khả kháng (ốm đau, tai nạn, việc tang gia đình) phải nộp Đơn xin hoãn thi kèm bệnh án hoặc giấy xác nhận y tế hợp lệ cho Phòng Đào tạo trong vòng 7 ngày làm việc kể từ ngày thi để được bố trí thi bù ở đợt thi gần nhất. Sinh viên vắng thi không có lý do chính đáng nhận điểm 0 học phần. 2. Phúc khảo bài thi: Trong vòng 07 ngày làm việc kể từ ngày công bố điểm thi trên hệ thống, sinh viên có quyền nộp đơn phúc khảo tại Phòng Khảo thí. Bài thi được hai giảng viên chấm độc lập đối chiếu ma trận đáp án và công bố điểm chính thức sau 10 ngày.'
WHERE slug = 'exam-deferral-re-evaluation-rules-vi' AND locale = 'vi';

UPDATE assistant.knowledge_document
SET content = 'Final exam deferral and grade re-evaluation rules: 1. Exam deferral: Students absent for force majeure reasons (illness, accidents, bereavement) must submit a deferral application with valid medical documentation to the Academic Affairs Office within 7 business days of the exam date to be scheduled for a makeup exam in the nearest session. Unexcused absentees receive a score of 0 for the course. 2. Grade appeals: Within 7 business days of grade publication, students may petition for regrading at the Testing Office. Two independent examiners regrade against the rubric, with finalized scores published within 10 days.'
WHERE slug = 'exam-deferral-re-evaluation-rules-en' AND locale = 'en';

-- 2. Give the GPA/credit document a conversion-bearing title so letter-grade
--    conversion questions retrieve it.
UPDATE assistant.knowledge_document
SET title = 'Cơ cấu điểm học phần, quy đổi điểm chữ A B+ B C+ C D+ D F sang thang điểm 4 và cách tính GPA'
WHERE slug = 'curriculum-gpa-weighting-and-credit-system-vi' AND locale = 'vi';

UPDATE assistant.knowledge_document
SET title = 'Course grading structure, letter grade to 4.0 scale conversion (A B+ B C+ C D+ D F), and GPA calculation'
WHERE slug = 'curriculum-gpa-weighting-and-credit-system-en' AND locale = 'en';

-- 3. Dedicated high-priority FAQ owning the conversion table verbatim.
INSERT INTO assistant.knowledge_document
    (id, slug, locale, title, content, source, priority, domain)
SELECT md5(seed.slug || '-document')::uuid, seed.slug, seed.locale, seed.title, seed.content,
       'campuscore-campus-faq-guide', seed.priority, seed.domain
FROM (VALUES
    ('gpa-letter-conversion-faq-vi', 'vi', 'Bảng quy đổi điểm chữ sang thang điểm 4', 'Bảng quy đổi điểm chữ sang thang điểm 4 của học phần: Điểm A quy đổi 4.0 (khoảng điểm 10 là 8.5-10.0); điểm B+ quy đổi 3.5 (8.0-8.4); điểm B quy đổi 3.0 (7.0-7.9); điểm C+ quy đổi 2.5 (6.5-6.9); điểm C quy đổi 2.0 (5.5-6.4); điểm D+ quy đổi 1.5 (5.0-5.4); điểm D quy đổi 1.0 (4.0-4.9); điểm F quy đổi 0.0 - không đạt (dưới 4.0). Ví dụ: hỏi Điểm A mấy điểm 4 thì đáp án là 4.0. Điểm tổng kết học phần nằm trong khoảng 5.5-6.4 tương ứng điểm C và 2.0 thang 4. GPA tính bằng tổng (điểm thang 4 nhân số tín chỉ) chia tổng số tín chỉ.', 85, 'POLICY'),
    ('gpa-letter-conversion-faq-en', 'en', 'Letter grade to 4.0 scale conversion table', 'Letter-to-4.0-scale conversion table for a course: grade A converts to 4.0 (10-point range 8.5-10.0); B+ converts to 3.5 (8.0-8.4); B converts to 3.0 (7.0-7.9); C+ converts to 2.5 (6.5-6.9); C converts to 2.0 (5.5-6.4); D+ converts to 1.5 (5.0-5.4); D converts to 1.0 (4.0-4.9); F converts to 0.0 - failed (below 4.0). Example: asked what an A is on the 4.0 scale, the answer is 4.0. GPA is the credit-weighted sum of 4.0-scale points divided by total credits.', 85, 'POLICY')
) AS seed(slug, locale, title, content, priority, domain)
WHERE NOT EXISTS (SELECT 1 FROM assistant.knowledge_document existing WHERE existing.slug = seed.slug);

INSERT INTO assistant.knowledge_document_revision
    (id, document_id, version, state, locale, slug, title, content, source, priority, created_by, reviewed_by, published_at, domain)
SELECT md5(d.id::text || '-revision-1')::uuid, d.id, 1, 'PUBLISHED', d.locale, d.slug, d.title, d.content,
       d.source, d.priority, 'system-migration', 'system-migration', CURRENT_TIMESTAMP, d.domain
FROM assistant.knowledge_document d
WHERE d.source = 'campuscore-campus-faq-guide'
  AND d.slug IN ('gpa-letter-conversion-faq-vi', 'gpa-letter-conversion-faq-en')
  AND NOT EXISTS (SELECT 1 FROM assistant.knowledge_document_revision r WHERE r.document_id = d.id AND r.version = 1);

-- 4. Archive the superseded published revisions of the three revised documents.
UPDATE assistant.knowledge_document_revision r
SET state = 'ARCHIVED'
WHERE r.state = 'PUBLISHED'
  AND r.document_id IN (
      SELECT d.id FROM assistant.knowledge_document d
      WHERE d.slug IN (
          'exam-deferral-re-evaluation-rules-vi',
          'exam-deferral-re-evaluation-rules-en',
          'curriculum-gpa-weighting-and-credit-system-vi',
          'curriculum-gpa-weighting-and-credit-system-en'));

-- 5. Publish new versioned revisions carrying the corrected content/titles.
WITH target AS (
    SELECT d.id, d.locale, d.slug, d.title, d.source, d.priority, d.domain, d.content,
           COALESCE(MAX(r.version), 0) + 1 AS next_version
    FROM assistant.knowledge_document d
    LEFT JOIN assistant.knowledge_document_revision r ON r.document_id = d.id
    WHERE d.active = TRUE AND d.visibility = 'PUBLIC'
      AND d.slug IN (
          'exam-deferral-re-evaluation-rules-vi',
          'exam-deferral-re-evaluation-rules-en',
          'curriculum-gpa-weighting-and-credit-system-vi',
          'curriculum-gpa-weighting-and-credit-system-en')
    GROUP BY d.id, d.locale, d.slug, d.title, d.source, d.priority, d.domain, d.content
)
INSERT INTO assistant.knowledge_document_revision
    (id, document_id, version, state, locale, slug, title, content, source, priority,
     created_by, reviewed_by, published_at, domain)
SELECT md5(t.id::text || '-revision-' || t.next_version::text)::uuid,
       t.id, t.next_version, 'PUBLISHED', t.locale, t.slug, t.title, t.content, t.source,
       t.priority, 'system-migration', 'system-migration', CURRENT_TIMESTAMP, t.domain
FROM target t
WHERE NOT EXISTS (
    SELECT 1
    FROM assistant.knowledge_document_revision existing
    WHERE existing.document_id = t.id AND existing.version = t.next_version);

-- 6. New immutable release projecting the reconciled corpus.
WITH canonical AS (
    SELECT d.id::text AS source_id, r.version, COALESCE(r.domain, d.domain, 'POLICY') AS domain,
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
INSERT INTO assistant.knowledge_release
    (id, corpus_version, corpus_hash, row_count, source, status, manifest, created_by, activated_at, previous_release_id)
SELECT '00000000-0000-0000-0000-000000000088'::uuid,
       'chatbot-audit-reconciliation-v88', corpus_hash, row_count, 'MANUAL', 'PUBLISHED',
       jsonb_build_object('schemaVersion', 1, 'corpusVersion', 'chatbot-audit-reconciliation-v88', 'rowCount', row_count, 'sha256', corpus_hash, 'documents', documents),
       'system-migration', CURRENT_TIMESTAMP,
       (SELECT active_release_id FROM assistant.knowledge_runtime_state WHERE singleton = TRUE)
FROM summary
WHERE NOT EXISTS (SELECT 1 FROM assistant.knowledge_release WHERE id = '00000000-0000-0000-0000-000000000088'::uuid);

-- 7. Project every public document into the new snapshot.
INSERT INTO assistant.knowledge_runtime_document
    (release_id, source_id, revision_id, version, domain, slug, locale, title, content, source, priority, active, visibility, published_at)
SELECT '00000000-0000-0000-0000-000000000088'::uuid, d.id::text, r.id, r.version,
       COALESCE(r.domain, d.domain, 'POLICY'), d.slug, d.locale, d.title, d.content, d.source, d.priority,
       TRUE, 'PUBLIC', COALESCE(r.published_at, CURRENT_TIMESTAMP)
FROM assistant.knowledge_document d
JOIN assistant.knowledge_document_revision r ON r.document_id = d.id AND r.state = 'PUBLISHED'
WHERE d.active = TRUE AND d.visibility = 'PUBLIC'
  AND NOT EXISTS (
      SELECT 1 FROM assistant.knowledge_runtime_document p
      WHERE p.release_id = '00000000-0000-0000-0000-000000000088'::uuid AND p.source_id = d.id::text);

-- 8. Switch the active runtime release.
INSERT INTO assistant.knowledge_runtime_state (singleton, active_release_id)
VALUES (TRUE, '00000000-0000-0000-0000-000000000088'::uuid)
ON CONFLICT (singleton) DO UPDATE
SET active_release_id = EXCLUDED.active_release_id, updated_at = CURRENT_TIMESTAMP;
