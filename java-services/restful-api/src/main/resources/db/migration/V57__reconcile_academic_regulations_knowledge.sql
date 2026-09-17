-- V57: Reconcile the academic regulations knowledge corpus to one canonical
-- threshold set. Fixes three defect classes found in the seeded documents:
--   CB-P1-1  Scholarship tier "Khá" stated as GPA 2.5/4.0 (7.0/10); 2.5/4.0 is
--            6.25/10. The sibling tiers (3.2 -> 8.0, 3.6 -> 9.0) prove the
--            table converts linearly (score/10 * 4), so the value that maps to
--            7.0/10 is 2.8/4.0. Both locales and the V43 scholarship criteria
--            document are aligned to GPA 2.80 (7.0/10).
--   CB-P1-2  "cảnh báo học vụ mức 3" / "Level 3" is gated on but never defined.
--            The institution uses a two-level warning scheme under credit-based
--            training (mirroring Thong tu 08/2021 style consecutive-warning
--            expulsion), so every mức-3 reference is removed and "buộc thôi
--            học" is expressed as two consecutive mức 2 warnings or exceeding
--            the maximum study duration.
--   CB-P2-1  Thesis eligibility ">= 75-80% credits + no level 2/3 warning"
--            (V40) versus ">= 110 credits, CPA >= 2.00" (V43). Canonical set:
--            >= 110 accumulated credits AND CPA >= 2.00/4.0 AND no active
--            Level 2 warning AND no disciplinary sanction AND completed
--            prerequisites, core specialized courses, and the specialized
--            internship — stated identically in every document.
--   CB-P2-2  Exit TOEIC "450-550 by major" (V40) versus "500 Engineering /
--            600 Economics" (V43). The specific, dedicated benchmark document
--            wins: TOEIC 500 (Engineering and Technology) / 600 (Economics and
--            High-Quality programs).
-- Follows the V54 revision/release pattern: update document content, archive
-- superseded published revisions, publish versioned revisions, then cut and
-- activate a fresh immutable release so every change is release-traceable.
-- V40, V41 and V43 are already applied and are never edited here.

-- 1. Academic probation + thesis eligibility (from V40): drop the undefined
--    mức 3, express expulsion as consecutive Level 2 warnings, and adopt the
--    canonical thesis eligibility set.
UPDATE assistant.knowledge_document
SET content = 'Quy định về các mức cảnh báo học vụ và điều kiện nhận đồ án tốt nghiệp: 1. Các mức cảnh báo học vụ: Cảnh báo mức 1: Điểm trung bình học kỳ (ĐTBHK) dưới 1.00 (với học kỳ đầu tiên) hoặc dưới 1.20 (với các học kỳ tiếp theo). Cảnh báo mức 2: ĐTBHK dưới 1.40 hoặc điểm trung bình tích lũy giảm liên tiếp. Buộc thôi học: Bị cảnh báo học vụ mức 2 trong hai học kỳ chính liên tiếp, hoặc vượt quá khung thời gian đào tạo tối đa. 2. Điều kiện thực hiện và bảo vệ đồ án/khóa luận tốt nghiệp: Điều kiện tiên quyết: Sinh viên tích lũy tối thiểu 110 tín chỉ, điểm trung bình tích lũy CPA từ 2.00/4.0 trở lên, không bị cảnh báo học vụ mức 2 đang trong hiệu lực, không bị kỷ luật học vụ, và đã hoàn thành các học phần tiên quyết, học phần chuyên ngành cốt lõi cùng học phần thực tập chuyên ngành.'
WHERE slug = 'academic-probation-and-thesis-eligibility-vi' AND locale = 'vi';

UPDATE assistant.knowledge_document
SET content = 'Regulations on academic probation levels and thesis eligibility: 1. Probation levels: Level 1 warning: Semester GPA below 1.00 (first term) or below 1.20 (subsequent terms). Level 2 warning: Semester GPA below 1.40 or a consecutive drop in cumulative GPA. Academic expulsion: Two consecutive Level 2 warnings in regular semesters, or exceeding the maximum permissible study duration. 2. Graduation thesis eligibility: Prerequisites: Students must have accumulated at least 110 credits with a cumulative GPA (CPA) of at least 2.00/4.0, must not be under an active Level 2 academic warning, must not be under disciplinary sanction, and must have passed all prerequisite courses, core specialized courses, and the specialized internship.'
WHERE slug = 'academic-probation-and-thesis-eligibility-en' AND locale = 'en';

-- 2. Merit scholarships + graduation requirements (from V40): correct the Khá
--    tier conversion to 2.8/4.0 (7.0/10) and adopt the canonical exit TOEIC
--    thresholds from the dedicated benchmark document.
UPDATE assistant.knowledge_document
SET content = 'Tiêu chuẩn xét học bổng khuyến khích học tập và điều kiện tốt nghiệp: 1. Học bổng khuyến khích học tập (KKHT): Xét theo từng học kỳ cho sinh viên đăng ký tối thiểu 14 tín chỉ, không có môn thi lại (không có điểm F). Học bổng loại Khá: GPA từ 2.8/4.0 (7.0/10) và Điểm rèn luyện (ĐRL) từ 70 trở lên. Học bổng loại Giỏi: GPA từ 3.2/4.0 (8.0/10) và ĐRL từ 80 trở lên. Học bổng loại Xuất sắc: GPA từ 3.6/4.0 (9.0/10) và ĐRL từ 90 trở lên. 2. Điều kiện tốt nghiệp: Tích lũy 100% số tín chỉ của chương trình đào tạo; GPA tích lũy toàn khóa đạt từ 2.0/4.0 trở lên; ĐRL tích lũy toàn khóa đạt từ Trung bình (50 điểm) trở lên; đạt Chuẩn đầu ra Ngoại ngữ (TOEIC từ 500 điểm với khối ngành kỹ thuật và công nghệ, từ 600 điểm với khối ngành kinh tế và chương trình chất lượng cao) và Chuẩn Tin học (MOS/IC3); có chứng chỉ Giáo dục Quốc phòng - An ninh và Giáo dục Thể chất.'
WHERE slug = 'scholarships-graduation-requirements-vi' AND locale = 'vi';

UPDATE assistant.knowledge_document
SET content = 'Criteria for merit scholarships and graduation eligibility: 1. Academic merit scholarships: Evaluated per semester for students taking at least 14 credits with no failed grades (no grade F). Fair scholarship: GPA >= 2.8/4.0 (7.0/10) and conduct score >= 70. Good scholarship: GPA >= 3.2/4.0 (8.0/10) and conduct score >= 80. Excellent scholarship: GPA >= 3.6/4.0 (9.0/10) and conduct score >= 90. 2. Graduation requirements: Accumulate 100% of curriculum credits; cumulative GPA >= 2.0/4.0; cumulative conduct score >= 50; certified foreign language proficiency (TOEIC 500 for Engineering and Technology majors, TOEIC 600 for Economics and High-Quality programs) and IT proficiency (MOS/IC3); valid National Defense and Physical Education certificates.'
WHERE slug = 'scholarships-graduation-requirements-en' AND locale = 'en';

-- 3. Leave of absence / result deferment (from V41): the eligibility gate
--    referenced the undefined "mức 2 hoặc mức 3"; it now names only the active
--    Level 2 warning, consistent with the two-level scheme above.
UPDATE assistant.knowledge_document
SET content = 'Quy định về việc tạm ngừng học tập và bảo lưu kết quả học tập tại CampusUTE: 1. Điều kiện bảo lưu: Sinh viên được quyền xin tạm ngừng học tập và bảo lưu điểm số nếu thỏa mãn các điều kiện: Đã học ít nhất một học kỳ chính tại trường; không bị cảnh báo học vụ mức 2 đang trong hiệu lực; không đang trong thời gian bị kỷ luật từ khiển trách trở lên; và có lý do chính đáng (sức khỏe cần điều trị dài ngày, nghĩa vụ quân sự, hoàn cảnh gia đình đặc biệt). 2. Thời gian bảo lưu: Thời gian tạm ngừng học tập mỗi lần không quá 2 học kỳ chính và tổng thời gian tạm dừng không vượt quá khung thời gian đào tạo tối đa của chương trình. Sinh viên tham gia nghĩa vụ quân sự được bảo lưu toàn bộ thời gian phục vụ quân ngũ. 3. Thủ tục quay lại học tập: Ít nhất 2 tuần trước khi bắt đầu học kỳ mới, sinh viên phải nộp đơn xin trở lại học tập tại Phòng Đào tạo để được kích hoạt lại tài khoản và đăng ký lớp học phần.'
WHERE slug = 'leave-of-absence-and-deferment-vi' AND locale = 'vi';

UPDATE assistant.knowledge_document
SET content = 'Rules on academic leave of absence and result deferment at CampusUTE: 1. Eligibility: Students may request temporary absence with preserved academic results if they have completed at least one semester; are not under an active Level 2 academic warning; have no disciplinary reprimands; and hold valid reasons (medical treatment, military service, exceptional family hardship). 2. Duration: Leave is granted for up to 2 regular semesters per request, not exceeding maximum permissible program duration. Military service leave is preserved for the full active duty term. 3. Resumption of studies: Submit a study resumption request to the Academic Affairs Office at least 2 weeks before the new term starts.'
WHERE slug = 'leave-of-absence-and-deferment-en' AND locale = 'en';

-- 4. Thesis eligibility and defense (from V43): keep the defense protocol
--    sections, but state the eligibility prerequisites identically to the V40
--    probation document so both documents answer the question the same way.
UPDATE assistant.knowledge_document
SET content = 'Điều kiện học phần Khóa luận tốt nghiệp và đồ án cử nhân/kỹ sư: 1. Điều kiện tiên quyết: Sinh viên tích lũy tối thiểu 110 tín chỉ, điểm trung bình tích lũy CPA từ 2.00/4.0 trở lên, không bị cảnh báo học vụ mức 2 đang trong hiệu lực, không bị kỷ luật học vụ, và đã hoàn thành các học phần tiên quyết, học phần chuyên ngành cốt lõi cùng học phần thực tập chuyên ngành. 2. Số lượng thành viên: Mỗi đề tài thực hiện theo nhóm từ 1 đến tối đa 3 sinh viên dưới sự hướng dẫn của tối đa 2 giảng viên. 3. Đánh giá hội đồng: Khóa luận được đánh giá qua 3 cột điểm độc lập: Giảng viên hướng dẫn (30%), Giảng viên phản biện (20%) và Hội đồng bảo vệ trực tiếp (50%). Điểm tổng kết đạt từ 5.5/10 (điểm C) trở lên được công nhận đạt học phần tốt nghiệp.'
WHERE slug = 'graduation-thesis-eligibility-defense-vi' AND locale = 'vi';

UPDATE assistant.knowledge_document
SET content = 'Regulations regarding graduation thesis eligibility and defense proceedings: 1. Prerequisites: Students must have accumulated at least 110 credits with a cumulative GPA (CPA) of at least 2.00/4.0, must not be under an active Level 2 academic warning, must not be under disciplinary sanction, and must have passed all prerequisite courses, core specialized courses, and the specialized internship. 2. Group allocation: Capstone topics are undertaken by teams of 1 to 3 students under supervision of up to 2 faculty mentors. 3. Council scoring: Final grade comprises Supervisor evaluation (30%), Reviewer appraisal (20%), and Oral Defense Council deliberation (50%). A minimum composite score of 5.5/10 (Letter grade C) is mandatory for graduation credit conferral.'
WHERE slug = 'graduation-thesis-eligibility-defense-en' AND locale = 'en';

-- 5. Merit scholarship criteria (from V43): align the Khá / Good tier with the
--    corrected conversion so no document anywhere in the corpus still offers
--    the contradictory 2.50 threshold.
UPDATE assistant.knowledge_document
SET content = 'Chính sách học bổng học tập dành cho sinh viên chính quy: 1. Học bổng khuyến khích học tập (HBKKHT): Xét theo từng học kỳ dựa trên điểm trung bình học kỳ (GPA) và điểm rèn luyện (ĐRL). Mức Xuất sắc (GPA >= 3.60 tương đương 9.0/10 và ĐRL >= 90): 120% định mức học phí; Mức Giỏi (GPA >= 3.20 tương đương 8.0/10 và ĐRL >= 80): 100% học phí; Mức Khá (GPA >= 2.80 tương đương 7.0/10 và ĐRL >= 70): 80% học phí. 2. Học bổng tài trợ doanh nghiệp: Các tập đoàn Samsung, Intel, Bosch, VNPT, FPT tài trợ học bổng thường niên từ 20 đến 50 triệu đồng/suất cho sinh viên có thành tích xuất sắc trong học thuật và NCKH.'
WHERE slug = 'academic-scholarship-criteria-vi' AND locale = 'vi';

UPDATE assistant.knowledge_document
SET content = 'Scholarship grant policies for full-time undergraduates: 1. University Merit Scholarships: Evaluated per semester based on semester GPA and Conduct Points (DRL). Excellent tier (GPA >= 3.60, equivalent to 9.0/10, & DRL >= 90): 120% tuition quota; Very Good tier (GPA >= 3.20, equivalent to 8.0/10, & DRL >= 80): 100% tuition quota; Good tier (GPA >= 2.80, equivalent to 7.0/10, & DRL >= 70): 80% tuition quota. 2. Corporate endowment grants: Samsung, Intel, Bosch, VNPT, and FPT sponsor annual grants of 20 to 50 million VND per recipient for outstanding scholastic and research leadership.'
WHERE slug = 'academic-scholarship-criteria-en' AND locale = 'en';

-- 6. Archive the superseded published revisions of exactly the documents above.
UPDATE assistant.knowledge_document_revision r
SET state = 'ARCHIVED'
WHERE r.state = 'PUBLISHED'
  AND r.document_id IN (
      SELECT d.id FROM assistant.knowledge_document d
      WHERE d.slug IN (
          'academic-probation-and-thesis-eligibility-vi',
          'academic-probation-and-thesis-eligibility-en',
          'scholarships-graduation-requirements-vi',
          'scholarships-graduation-requirements-en',
          'leave-of-absence-and-deferment-vi',
          'leave-of-absence-and-deferment-en',
          'graduation-thesis-eligibility-defense-vi',
          'graduation-thesis-eligibility-defense-en',
          'academic-scholarship-criteria-vi',
          'academic-scholarship-criteria-en'));

-- 7. Publish new versioned revisions for every revised document.
WITH target AS (
    SELECT d.id, d.locale, d.slug, d.title, d.source, d.priority, d.domain, d.content,
           COALESCE(MAX(r.version), 0) + 1 AS next_version
    FROM assistant.knowledge_document d
    LEFT JOIN assistant.knowledge_document_revision r ON r.document_id = d.id
    WHERE d.active = TRUE AND d.visibility = 'PUBLIC'
      AND d.slug IN (
          'academic-probation-and-thesis-eligibility-vi',
          'academic-probation-and-thesis-eligibility-en',
          'scholarships-graduation-requirements-vi',
          'scholarships-graduation-requirements-en',
          'leave-of-absence-and-deferment-vi',
          'leave-of-absence-and-deferment-en',
          'graduation-thesis-eligibility-defense-vi',
          'graduation-thesis-eligibility-defense-en',
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
    WHERE existing.document_id = t.id
      AND existing.state = 'PUBLISHED'
      AND existing.content = t.content
      AND existing.title = t.title);

-- 8. Canonical summary and the new immutable release.
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
SELECT '00000000-0000-0000-0000-000000000057'::uuid,
       'academic-regulations-reconcile-v57', corpus_hash, row_count, 'MANUAL', 'PUBLISHED',
       jsonb_build_object(
           'schemaVersion', 1,
           'corpusVersion', 'academic-regulations-reconcile-v57',
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
    WHERE id = '00000000-0000-0000-0000-000000000057'::uuid
);

-- 9. Project every public document into the new release snapshot.
INSERT INTO assistant.knowledge_runtime_document
    (release_id, source_id, revision_id, version, domain, slug, locale, title, content, source,
     priority, active, visibility, published_at)
SELECT '00000000-0000-0000-0000-000000000057'::uuid,
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
      WHERE existing.release_id = '00000000-0000-0000-0000-000000000057'::uuid
        AND existing.source_id = d.id::text
  );

-- 10. Switch the active runtime release.
INSERT INTO assistant.knowledge_runtime_state (singleton, active_release_id)
VALUES (TRUE, '00000000-0000-0000-0000-000000000057'::uuid)
ON CONFLICT (singleton) DO UPDATE
SET active_release_id = EXCLUDED.active_release_id,
    updated_at = CURRENT_TIMESTAMP;
