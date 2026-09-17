-- CB-P1-1 correction, second pass: express scholarship thresholds on ONE scale.
--
-- V57 fixed a genuine error — the scholarship tier "Khá" was documented as
-- "GPA từ 2.5/4.0 (7.0/10)" while 2.5/4.0 is 6.25/10 — but it fixed it by
-- assuming the two scales convert linearly (score / 10 * 4). That assumption is
-- wrong for this system, and an adversarial review demonstrated the consequence:
--
--   * This application converts a 10-point score to the 4.0 scale through the
--     letter bands in `backend letterGrade()` and `frontend/src/lib/grade-scale.ts`
--     (7.0-8.0 -> B -> 3.0; 8.0-8.5 -> B+ -> 3.5; 9.0-10 -> A+ -> 4.0).
--   * The corpus already states that band table, in V41's
--     `curriculum-gpa-weighting-and-credit-system`, unchanged by V57.
--   * So release 0057 shipped two documents giving two different 4.0 values for
--     the same 7.0/10 input (2.8 by V57's linear pairs, 3.0 by V41's bands) — and
--     V57's value is the one that disagrees with the transcript the student is
--     looking at, because the application computes 3.0.
--
-- Patching 2.8 -> 3.0 would only move the disagreement: a "/4.0 (y/10)" pair is
-- ambiguous whenever the mapping is banded rather than linear, since a 4.0
-- threshold can correspond to a whole range of 10-point scores. So the
-- threshold documents now state the threshold on the 10-point scale alone, with
-- the letter grade the application itself assigns, and the single conversion
-- table lives in the curriculum document that already owns it.
--
-- Forward-only: V57 is never edited, it is superseded.

-- 1. `scholarships-graduation-requirements` (vi) — thresholds on the 10-point scale.
UPDATE assistant.knowledge_document
SET content = 'Tiêu chuẩn xét học bổng khuyến khích học tập và điều kiện tốt nghiệp: 1. Học bổng khuyến khích học tập (KKHT): Xét theo từng học kỳ cho sinh viên đăng ký tối thiểu 14 tín chỉ, không có môn thi lại (không có điểm F). Học bổng loại Khá: điểm trung bình học kỳ (GPA) từ 7.0/10 (tương đương điểm B) và Điểm rèn luyện (ĐRL) từ 70 trở lên. Học bổng loại Giỏi: GPA từ 8.0/10 (tương đương điểm B+) và ĐRL từ 80 trở lên. Học bổng loại Xuất sắc: GPA từ 9.0/10 (tương đương điểm A+) và ĐRL từ 90 trở lên. 2. Quy đổi thang điểm: Việc quy đổi từ thang 10 sang thang 4.0 được thực hiện theo bảng quy đổi của nhà trường (xem tài liệu về cách tính GPA và hệ thống tín chỉ), không theo công thức chia tuyến tính. 3. Điều kiện tốt nghiệp: Tích lũy 100% số tín chỉ của chương trình đào tạo; GPA tích lũy toàn khóa đạt từ 2.0/4.0 trở lên; ĐRL tích lũy toàn khóa đạt từ Trung bình (50 điểm) trở lên; đạt Chuẩn đầu ra Ngoại ngữ (TOEIC từ 500 điểm với khối ngành kỹ thuật và công nghệ, từ 600 điểm với khối ngành kinh tế và chương trình chất lượng cao) và Chuẩn Tin học (MOS/IC3); có chứng chỉ Giáo dục Quốc phòng - An ninh và Giáo dục Thể chất.'
WHERE slug = 'scholarships-graduation-requirements-vi' AND locale = 'vi';

-- 2. `scholarships-graduation-requirements` (en)
UPDATE assistant.knowledge_document
SET content = 'Criteria for merit scholarships and graduation eligibility: 1. Academic merit scholarships: Evaluated per semester for students taking at least 14 credits with no failed grades (no grade F). Fair scholarship: semester GPA >= 7.0/10 (equivalent to letter grade B) and conduct score >= 70. Good scholarship: GPA >= 8.0/10 (equivalent to letter grade B+) and conduct score >= 80. Excellent scholarship: GPA >= 9.0/10 (equivalent to letter grade A+) and conduct score >= 90. 2. Scale conversion: Converting from the 10-point scale to the 4.0 scale follows the university conversion table (see the document on GPA calculation and the credit system), not a linear division. 3. Graduation requirements: Accumulate 100% of curriculum credits; cumulative GPA >= 2.0/4.0; cumulative conduct score >= 50; certified foreign language proficiency (TOEIC 500 for Engineering and Technology majors, TOEIC 600 for Economics and High-Quality programs) and IT proficiency (MOS/IC3); valid National Defense and Physical Education certificates.'
WHERE slug = 'scholarships-graduation-requirements-en' AND locale = 'en';

-- 3. `academic-scholarship-criteria` (vi)
UPDATE assistant.knowledge_document
SET content = 'Chính sách học bổng học tập dành cho sinh viên chính quy: 1. Học bổng khuyến khích học tập (HBKKHT): Xét theo từng học kỳ dựa trên điểm trung bình học kỳ (GPA) và điểm rèn luyện (ĐRL). Mức Xuất sắc (GPA từ 9.0/10, tương đương điểm A+, và ĐRL từ 90): 120% định mức học phí; Mức Giỏi (GPA từ 8.0/10, tương đương điểm B+, và ĐRL từ 80): 100% học phí; Mức Khá (GPA từ 7.0/10, tương đương điểm B, và ĐRL từ 70): 80% học phí. 2. Quy đổi thang điểm: GPA thang 4.0 được quy đổi từ thang 10 theo bảng quy đổi của nhà trường, không theo công thức chia tuyến tính. 3. Học bổng tài trợ doanh nghiệp: Các tập đoàn Samsung, Intel, Bosch, VNPT, FPT tài trợ học bổng thường niên từ 20 đến 50 triệu đồng/suất cho sinh viên có thành tích xuất sắc trong học thuật và NCKH.'
WHERE slug = 'academic-scholarship-criteria-vi' AND locale = 'vi';

-- 4. `academic-scholarship-criteria` (en)
UPDATE assistant.knowledge_document
SET content = 'Scholarship grant policies for full-time undergraduates: 1. University Merit Scholarships: Evaluated per semester based on semester GPA and Conduct Points (DRL). Excellent tier (GPA >= 9.0/10, equivalent to letter grade A+, and DRL >= 90): 120% tuition quota; Very Good tier (GPA >= 8.0/10, equivalent to letter grade B+, and DRL >= 80): 100% tuition quota; Good tier (GPA >= 7.0/10, equivalent to letter grade B, and DRL >= 70): 80% tuition quota. 2. Scale conversion: The 4.0-scale GPA is converted from the 10-point scale using the university conversion table, not a linear division. 3. Corporate endowment grants: Samsung, Intel, Bosch, VNPT, and FPT sponsor annual grants of 20 to 50 million VND per recipient for outstanding scholastic and research leadership.'
WHERE slug = 'academic-scholarship-criteria-en' AND locale = 'en';

-- 5. Archive the superseded published revisions of exactly the documents above.
UPDATE assistant.knowledge_document_revision r
SET state = 'ARCHIVED'
WHERE r.state = 'PUBLISHED'
  AND r.document_id IN (
      SELECT d.id FROM assistant.knowledge_document d
      WHERE d.slug IN (
          'scholarships-graduation-requirements-vi',
          'scholarships-graduation-requirements-en',
          'academic-scholarship-criteria-vi',
          'academic-scholarship-criteria-en'));

-- 6. Publish new versioned revisions for every revised document.
WITH target AS (
    SELECT d.id, d.locale, d.slug, d.title, d.source, d.priority, d.domain, d.content,
           COALESCE(MAX(r.version), 0) + 1 AS next_version
    FROM assistant.knowledge_document d
    LEFT JOIN assistant.knowledge_document_revision r ON r.document_id = d.id
    WHERE d.active = TRUE AND d.visibility = 'PUBLIC'
      AND d.slug IN (
          'scholarships-graduation-requirements-vi',
          'scholarships-graduation-requirements-en',
          'academic-scholarship-criteria-vi',
          'academic-scholarship-criteria-en')
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

-- 7. Canonical summary and the new immutable release, mirroring V57's mechanism so
--    the assistant serves the corrected set rather than the superseded one.
WITH canonical AS (
    SELECT d.id::text AS source_id, r.version, COALESCE(r.domain, d.domain, 'POLICY') AS domain,
           d.slug, d.locale, d.title, d.content, d.source, d.priority
    FROM assistant.knowledge_document d
    JOIN assistant.knowledge_document_revision r
      ON r.document_id = d.id
     AND r.state = 'PUBLISHED'
    WHERE d.active = TRUE
      AND d.visibility = 'PUBLIC'
), summary AS (
    SELECT encode(
               digest(
                   COALESCE(
                       string_agg(
                           concat_ws('|', source_id, domain, slug, locale, title, content, source,
                                     priority::text, version::text),
                           E'\n' ORDER BY source_id
                       ),
                       ''
                   ),
                   'sha256'
               ),
               'hex'
           ) AS corpus_hash,
           COUNT(*)::integer AS row_count,
           COALESCE(
               jsonb_agg(
                   jsonb_build_object('sourceId', source_id, 'domain', domain, 'slug', slug, 'locale', locale)
                   ORDER BY source_id
               ),
               '[]'::jsonb
           ) AS documents
    FROM canonical
)
INSERT INTO assistant.knowledge_release
    (id, corpus_version, corpus_hash, row_count, source, status, manifest, created_by, activated_at, previous_release_id)
SELECT '00000000-0000-0000-0000-000000000059'::uuid,
       'academic-regulations-band-conversion-v59', corpus_hash, row_count, 'MANUAL', 'PUBLISHED',
       jsonb_build_object(
           'schemaVersion', 1,
           'corpusVersion', 'academic-regulations-band-conversion-v59',
           'rowCount', row_count,
           'sha256', corpus_hash,
           'documents', documents
       ),
       'system-migration', CURRENT_TIMESTAMP,
       (SELECT active_release_id
        FROM assistant.knowledge_runtime_state
        WHERE singleton = TRUE)
FROM summary
WHERE NOT EXISTS (
    SELECT 1
    FROM assistant.knowledge_release
    WHERE id = '00000000-0000-0000-0000-000000000059'::uuid
);

-- 8. Project every public document into the new release snapshot.
INSERT INTO assistant.knowledge_runtime_document
    (release_id, source_id, revision_id, version, domain, slug, locale, title, content, source,
     priority, active, visibility, published_at)
SELECT '00000000-0000-0000-0000-000000000059'::uuid,
       d.id::text, r.id, r.version, COALESCE(r.domain, d.domain, 'POLICY'), d.slug, d.locale,
       d.title, d.content, d.source, d.priority, TRUE, 'PUBLIC',
       COALESCE(r.published_at, CURRENT_TIMESTAMP)
FROM assistant.knowledge_document d
JOIN assistant.knowledge_document_revision r
  ON r.document_id = d.id
 AND r.state = 'PUBLISHED'
WHERE d.active = TRUE
  AND d.visibility = 'PUBLIC'
  AND NOT EXISTS (
      SELECT 1
      FROM assistant.knowledge_runtime_document existing
      WHERE existing.release_id = '00000000-0000-0000-0000-000000000059'::uuid
        AND existing.source_id = d.id::text
  );

-- 9. Switch the active runtime release.
INSERT INTO assistant.knowledge_runtime_state (singleton, active_release_id)
VALUES (TRUE, '00000000-0000-0000-0000-000000000059'::uuid)
ON CONFLICT (singleton) DO UPDATE
SET active_release_id = EXCLUDED.active_release_id,
    updated_at = CURRENT_TIMESTAMP;
