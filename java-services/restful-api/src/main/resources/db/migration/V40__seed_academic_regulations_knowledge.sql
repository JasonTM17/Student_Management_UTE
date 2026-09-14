-- Comprehensive academic regulations and policy knowledge for the CampusCore assistant:
-- 1. Prerequisite vs Prior vs Corequisite courses (điều kiện tiên quyết, học trước, song hành)
-- 2. Grade F handling, course retakes, and grade improvement (xử lý điểm F, học lại, cải thiện)
-- 3. Semester tuition payment deadlines, procedures, and unpaid fee handling (thời hạn đóng học phí)
-- 4. Academic probation levels and graduation thesis eligibility (cảnh báo học vụ và làm đồ án)
-- 5. Course withdrawal deadlines and semester credit limits (rút học phần và giới hạn tín chỉ)
-- 6. Academic scholarships and graduation exit requirements (học bổng KKHT và chuẩn đầu ra)
-- Follows the V20/V38/V39 immutable release projection and runtime state switch pattern.

INSERT INTO assistant.knowledge_document
    (id, slug, locale, title, content, source, priority, domain)
SELECT md5(seed.slug || '-document')::uuid, seed.slug, seed.locale, seed.title, seed.content,
       'campuscore-academic-regulations', seed.priority, seed.domain
FROM (VALUES
    ('prerequisite-prior-corequisite-vi', 'vi',
     'Học phần tiên quyết, học phần học trước và học phần song hành',
     'Quy chế đào tạo theo hệ thống tín chỉ phân biệt rõ 3 loại học phần điều kiện: 1. Học phần tiên quyết (Prerequisite): Là học phần sinh viên bắt buộc phải học và thi đạt (điểm tổng kết từ D hoặc 4.0/10 trở lên) mới đủ điều kiện đăng ký học phần tiếp theo. Nếu chưa tích lũy môn tiên quyết, hệ thống đăng ký học phần sẽ tự động chặn. 2. Học phần học trước (Prior course): Là học phần sinh viên bắt buộc phải đăng ký học trước và có điểm quá trình, điểm thi kết thúc học phần (không bắt buộc phải qua môn, kể cả nhận điểm F) thì vẫn được phép đăng ký học phần tiếp theo. 3. Học phần song hành (Corequisite): Là học phần cho phép sinh viên đăng ký học cùng lúc trong cùng một học kỳ hoặc đã học ở các kỳ trước đó. Sinh viên cần tra cứu sơ đồ cây môn học trên Cổng đào tạo CampusCore trước mỗi đợt đăng ký tín chỉ.',
     20, 'REGISTRATION'),

    ('prerequisite-prior-corequisite-en', 'en',
     'Prerequisite, prior, and corequisite courses',
     'Credit-based academic regulations define three types of course prerequisites: 1. Prerequisite: A course that a student must complete and pass with a grade of D (4.0/10) or higher before enrolling in the subsequent course. If not passed, registration is blocked. 2. Prior course: A course that a student must have registered and received grades for (passing is not required, even grade F is eligible) before taking the next course. 3. Corequisite: A course that can be taken concurrently in the same semester or completed in an earlier term. Review the curriculum tree on the CampusCore Portal before registration.',
     20, 'REGISTRATION'),

    ('grade-f-retake-improvement-vi', 'vi',
     'Quy định xử lý điểm F, học lại và học cải thiện điểm',
     'Quy chế đào tạo quy định chi tiết về việc xử lý điểm học phần chưa đạt và học cải thiện: 1. Bị điểm F (Học lại): Sinh viên có điểm tổng kết học phần dưới 4.0 (thang điểm 10) hoặc điểm chữ F bị tính là không đạt. Đối với học phần bắt buộc, sinh viên bắt buộc phải đăng ký học lại môn đó trong các học kỳ tiếp theo cho đến khi đạt điểm D trở lên. Đối với học phần tự chọn, sinh viên có thể đăng ký học lại chính môn đó hoặc chọn một học phần tự chọn khác tương đương trong cùng khối kiến thức để thay thế. 2. Học cải thiện điểm: Sinh viên đạt điểm D hoặc D+ được quyền đăng ký học cải thiện để nâng cao điểm trung bình tích lũy GPA. Các môn có điểm từ C trở lên không được phép học cải thiện. 3. Quy tắc tính GPA: Điểm của lần học sau cùng (hoặc điểm cao nhất) sẽ được cập nhật vào bảng điểm tích lũy và thay thế trọng số của lần học trước.',
     22, 'POLICY'),

    ('grade-f-retake-improvement-en', 'en',
     'Regulations on F grade, course retakes, and grade improvement',
     'Academic regulations on failed courses and grade improvement: 1. Grade F (Course Retake): A final course score below 4.0 or letter grade F is considered a failure. For mandatory courses, students must re-register and retake the course until achieving grade D or higher. For electives, students may retake the course or choose another elective in the same subject area. 2. Grade Improvement: Students who received grade D or D+ may re-enroll to improve their cumulative GPA. Courses with grade C or higher cannot be repeated for improvement. 3. GPA calculation: The latest (or highest) grade is recorded on the cumulative transcript and replaces the previous attempt.',
     22, 'POLICY'),

    ('tuition-payment-deadline-rules-vi', 'vi',
     'Quy định và thời hạn đóng học phí học kỳ',
     'Quy định về thời hạn, phương thức nộp học phí và xử lý công nợ: 1. Thời hạn đóng học phí: Học phí mỗi học kỳ chính được thông báo công khai trên Cổng đào tạo và sinh viên phải hoàn thành nghĩa vụ học phí trong vòng 4 tuần đầu tiên kể từ ngày bắt đầu học kỳ. 2. Phương thức nộp học phí: Sinh viên nộp qua cổng thanh toán trực tuyến của nhà trường, chuyển khoản định danh ngân hàng (VietQR) với cú pháp quy định, hoặc nộp trực tiếp tại Phòng Kế hoạch - Tài chính. 3. Gia hạn học phí: Sinh viên có hoàn cảnh đặc biệt khó khăn có thể nộp đơn xin gia hạn nộp học phí (kèm minh chứng) trước hạn chót; thời gian gia hạn tối đa không quá 4 tuần tiếp theo. 4. Xử lý nợ học phí: Sinh viên không đóng học phí đúng hạn và không có đơn gia hạn hợp lệ sẽ bị tạm hủy đăng ký lớp học phần, không có tên trong danh sách thi kết thúc học phần và bị khóa quyền đăng ký học phần của học kỳ kế tiếp.',
     22, 'POLICY'),

    ('tuition-payment-deadline-rules-en', 'en',
     'Semester tuition payment deadlines and financial rules',
     'Tuition deadlines, payment methods, and debt settlement: 1. Tuition deadline: Tuition for each regular semester must be settled within the first 4 weeks of the semester as announced on the portal. 2. Payment methods: Online payment gateway, designated bank transfer (VietQR), or in person at the Financial Affairs Office. 3. Tuition deferral: Students facing extreme financial difficulties may submit a formal deferral request with documentation before the deadline; maximum extension is 4 additional weeks. 4. Consequences of unpaid tuition: Students failing to pay or defer on time will have their course registration suspended, will be omitted from exam rosters, and will be blocked from enrolling in the next semester.',
     22, 'POLICY'),

    ('academic-probation-and-thesis-eligibility-vi', 'vi',
     'Cảnh báo học vụ các mức và điều kiện bảo vệ khóa luận tốt nghiệp',
     'Quy định về các mức cảnh báo học vụ và điều kiện nhận đồ án tốt nghiệp: 1. Các mức cảnh báo học vụ: Cảnh báo mức 1: Điểm trung bình học kỳ (ĐTBHK) dưới 1.00 (với học kỳ đầu tiên) hoặc dưới 1.20 (với các học kỳ tiếp theo). Cảnh báo mức 2: ĐTBHK dưới 1.40 hoặc ĐTB tích lũy giảm liên tiếp. Buộc thôi học: Bị cảnh báo học vụ mức 2 liên tiếp hai học kỳ chính, hoặc bị cảnh báo mức 3, hoặc vượt quá khung thời gian đào tạo tối đa. 2. Điều kiện thực hiện và bảo vệ đồ án/khóa luận tốt nghiệp: Sinh viên KHÔNG trong thời gian bị kỷ luật và KHÔNG bị cảnh báo học vụ mức 2 hoặc mức 3; đã tích lũy tối thiểu 75% đến 80% tổng số tín chỉ của chương trình đào tạo; và hoàn thành toàn bộ các học phần tiên quyết của đồ án cùng các học phần thực tập chuyên ngành.',
     20, 'POLICY'),

    ('academic-probation-and-thesis-eligibility-en', 'en',
     'Academic probation levels and graduation thesis eligibility',
     'Regulations on academic probation levels and thesis eligibility: 1. Probation levels: Level 1 warning: Semester GPA below 1.00 (first term) or below 1.20 (subsequent terms). Level 2 warning: Semester GPA below 1.40 or consecutive drop in cumulative GPA. Academic expulsion: Two consecutive Level 2 warnings, a Level 3 warning, or exceeding maximum permissible study duration. 2. Graduation thesis eligibility: Students must NOT be under active Level 2 or Level 3 academic probation or disciplinary sanction; must have accumulated at least 75% to 80% of total program credits; and must have passed all prerequisite courses and specialized internships.',
     20, 'POLICY'),

    ('withdrawal-credit-limits-vi', 'vi',
     'Quy định rút học phần và giới hạn tín chỉ mỗi học kỳ',
     'Quy chế về rút môn học và giới hạn khối lượng học tập: 1. Rút học phần: Sinh viên được nộp đơn rút môn học trong vòng 2 tuần đầu của học kỳ chính. Học phần được chấp thuận rút sẽ ghi nhận điểm chữ W (Withdrawn) trên bảng điểm và không tính vào điểm trung bình GPA. Rút môn sau thời hạn quy định sẽ không được xem xét và không được hoàn phí. 2. Giới hạn tín chỉ: Mỗi học kỳ chính, sinh viên được đăng ký tối thiểu 14 tín chỉ (trừ học kỳ cuối) và tối đa 24 tín chỉ (sinh viên có điểm GPA loại Khá, Giỏi có thể làm đơn xin đăng ký tối đa 28 tín chỉ). Học kỳ phụ (học kỳ hè) được đăng ký tối đa 8 đến 10 tín chỉ.',
     25, 'REGISTRATION'),

    ('withdrawal-credit-limits-en', 'en',
     'Course withdrawal and semester credit limits',
     'Rules on course withdrawal and credit workload: 1. Course withdrawal: Students may request course withdrawal during the first 2 weeks of a regular semester. Approved courses receive grade W and are excluded from GPA calculation. Late withdrawal requests are rejected with no refund. 2. Credit limits: Each regular semester requires a minimum of 14 credits (except final semester) and a maximum of 24 credits (expandable up to 28 credits for students with good/excellent GPA). Summer terms allow a maximum of 8 to 10 credits.',
     25, 'REGISTRATION'),

    ('scholarships-graduation-requirements-vi', 'vi',
     'Tiêu chuẩn học bổng khuyến khích học tập và chuẩn đầu ra tốt nghiệp',
     'Tiêu chuẩn xét học bổng khuyến khích học tập và điều kiện tốt nghiệp: 1. Học bổng khuyến khích học tập (KKHT): Xét theo từng học kỳ cho sinh viên đăng ký tối thiểu 14 tín chỉ, không có môn thi lại (không có điểm F). Học bổng loại Khá: GPA từ 2.5/4.0 (7.0/10) và Điểm rèn luyện (ĐRL) từ 70 trở lên. Học bổng loại Giỏi: GPA từ 3.2/4.0 (8.0/10) và ĐRL từ 80 trở lên. Học bổng loại Xuất sắc: GPA từ 3.6/4.0 (9.0/10) và ĐRL từ 90 trở lên. 2. Điều kiện tốt nghiệp: Tích lũy 100% số tín chỉ của chương trình đào tạo; GPA tích lũy toàn khóa đạt từ 2.0/4.0 trở lên; ĐRL tích lũy toàn khóa đạt từ Trung bình (50 điểm) trở lên; đạt Chuẩn đầu ra Ngoại ngữ (TOEIC từ 450-550 tùy ngành) và Chuẩn Tin học (MOS/IC3); có chứng chỉ Giáo dục Quốc phòng - An ninh và Giáo dục Thể chất.',
     22, 'POLICY'),

    ('scholarships-graduation-requirements-en', 'en',
     'Scholarship standards and graduation exit requirements',
     'Criteria for merit scholarships and graduation eligibility: 1. Academic merit scholarships: Evaluated per semester for students taking at least 14 credits with no failed grades (no grade F). Fair scholarship: GPA >= 2.5/4.0 (7.0/10) and conduct score >= 70. Good scholarship: GPA >= 3.2/4.0 (8.0/10) and conduct score >= 80. Excellent scholarship: GPA >= 3.6/4.0 (9.0/10) and conduct score >= 90. 2. Graduation requirements: Accumulate 100% of curriculum credits; cumulative GPA >= 2.0/4.0; cumulative conduct score >= 50; certified foreign language proficiency (TOEIC 450-550) and IT proficiency (MOS/IC3); valid National Defense and Physical Education certificates.',
     22, 'POLICY')
) AS seed(slug, locale, title, content, priority, domain)
WHERE NOT EXISTS (SELECT 1 FROM assistant.knowledge_document existing WHERE existing.slug = seed.slug);

INSERT INTO assistant.knowledge_document_revision
    (id, document_id, version, state, locale, slug, title, content, source, priority, created_by, reviewed_by, published_at, domain)
SELECT md5(d.id::text || '-revision-1')::uuid, d.id, 1, 'PUBLISHED', d.locale, d.slug, d.title, d.content,
       d.source, d.priority, 'system-seed', 'system-seed', CURRENT_TIMESTAMP, d.domain
FROM assistant.knowledge_document d
WHERE d.source = 'campuscore-academic-regulations'
  AND NOT EXISTS (SELECT 1 FROM assistant.knowledge_document_revision r WHERE r.document_id = d.id AND r.version = 1);

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
INSERT INTO assistant.knowledge_release (id, corpus_version, corpus_hash, row_count, source, status, manifest, created_by, activated_at, previous_release_id)
SELECT '00000000-0000-0000-0000-000000000040'::uuid, 'local-demo-v40', corpus_hash, row_count, 'MANUAL', 'PUBLISHED',
       jsonb_build_object('schemaVersion', 1, 'corpusVersion', 'local-demo-v40', 'rowCount', row_count, 'sha256', corpus_hash, 'documents', documents),
       'system-migration', CURRENT_TIMESTAMP,
       (SELECT active_release_id FROM assistant.knowledge_runtime_state WHERE singleton = TRUE)
FROM summary
WHERE NOT EXISTS (SELECT 1 FROM assistant.knowledge_release WHERE id = '00000000-0000-0000-0000-000000000040'::uuid);

INSERT INTO assistant.knowledge_runtime_document
    (release_id, source_id, revision_id, version, domain, slug, locale, title, content, source, priority, active, visibility, published_at)
SELECT '00000000-0000-0000-0000-000000000040'::uuid, d.id::text, r.id, r.version,
       COALESCE(r.domain, d.domain, 'POLICY'), d.slug, d.locale, d.title, d.content, d.source, d.priority,
       TRUE, 'PUBLIC', COALESCE(r.published_at, CURRENT_TIMESTAMP)
FROM assistant.knowledge_document d
JOIN assistant.knowledge_document_revision r ON r.document_id = d.id AND r.state = 'PUBLISHED'
WHERE d.active = TRUE AND d.visibility = 'PUBLIC'
  AND NOT EXISTS (
      SELECT 1 FROM assistant.knowledge_runtime_document p
      WHERE p.release_id = '00000000-0000-0000-0000-000000000040'::uuid AND p.source_id = d.id::text
  );

INSERT INTO assistant.knowledge_runtime_state (singleton, active_release_id)
VALUES (TRUE, '00000000-0000-0000-0000-000000000040'::uuid)
ON CONFLICT (singleton) DO UPDATE
SET active_release_id = EXCLUDED.active_release_id, updated_at = CURRENT_TIMESTAMP;
