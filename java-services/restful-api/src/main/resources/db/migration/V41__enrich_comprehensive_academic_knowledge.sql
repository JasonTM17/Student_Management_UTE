-- Comprehensive academic regulations and campus life knowledge enrichment for CampusCore assistant:
-- 1. Student conduct points and training score evaluation regulations (quy chế đánh giá điểm rèn luyện ĐRL)
-- 2. End-of-course examination regulations and absenteeism policy (quy chế thi kết thúc học phần và hoãn thi)
-- 3. Regulations on temporary absence, result deferment and academic withdrawal (quy chế bảo lưu kết quả học tập)
-- 4. Course component weights, GPA calculation and credit system (cơ cấu điểm học phần, cách tính GPA, CPA)
-- 5. One-stop student affairs services, certificates and health insurance (dịch vụ một cửa sinh viên, cấp giấy tờ, BHYT)
-- 6. Grade appeal and exam re-evaluation procedure (quy trình phúc khảo điểm thi học phần)
-- Follows the V20/V38/V39/V40 immutable release projection and runtime state switch pattern.

INSERT INTO assistant.knowledge_document
    (id, slug, locale, title, content, source, priority, domain)
SELECT md5(seed.slug || '-document')::uuid, seed.slug, seed.locale, seed.title, seed.content,
       'campuscore-academic-enrichment', seed.priority, seed.domain
FROM (VALUES
    ('student-conduct-score-handbook-vi', 'vi',
     'Quy chế đánh giá Điểm rèn luyện sinh viên',
     'Quy chế đánh giá kết quả rèn luyện của người học được thực hiện theo thang điểm 100 với 5 tiêu chí chính: 1. Ý thức tham gia học tập (tối đa 20 điểm): đi học đầy đủ, đúng giờ, không vi phạm quy chế thi cử, tham gia nghiên cứu khoa học và câu lạc bộ học thuật. 2. Ý thức chấp hành pháp luật và quy chế của nhà trường (tối đa 25 điểm): chấp hành nội quy giảng đường, không nợ học phí, đóng BHYT đúng hạn. 3. Ý thức tham gia hoạt động chính trị, xã hội, văn hóa, thể thao (tối đa 20 điểm): tham gia chiến dịch tình nguyện Mùa hè xanh, Tiếp sức mùa thi, hiến máu nhân đạo, hoạt động Đoàn - Hội. 4. Ý thức công dân và quan hệ cộng đồng (tối đa 25 điểm): tinh thần tương thân tương ái, giữ gìn trật tự an ninh, quan hệ tốt với bạn bè và cộng đồng địa phương. 5. Ý thức và kết quả tham gia phụ trách lớp, đoàn thể, thành tích đặc biệt (tối đa 10 điểm): Ban cán sự lớp, BCH Đoàn - Hội, đạt giải thưởng cấp Trường trở lên. Phân loại rèn luyện: Xuất sắc (90-100), Tốt (80-89), Khá (65-79), Trung bình (50-64), Yếu (35-49), Kém (<35). Điểm rèn luyện là căn cứ xét học bổng, khen thưởng và điều kiện xét tốt nghiệp ra trường.',
     22, 'POLICY'),

    ('student-conduct-score-handbook-en', 'en',
     'Student conduct and training points evaluation regulations',
     'Regulations on evaluating student training and conduct points are based on a 100-point scale across 5 criteria: 1. Academic awareness (up to 20 pts): punctual attendance, no examination violations, academic clubs and research. 2. Compliance with laws and university regulations (up to 25 pts): classroom discipline, timely tuition payment, health insurance. 3. Social, cultural, and athletic participation (up to 20 pts): volunteer campaigns, blood donation, youth union activities. 4. Civic awareness and community relations (up to 25 pts): community responsibility, public order. 5. Leadership and special achievements (up to 10 pts): class monitor, student union officer, institutional awards. Classification: Excellent (90-100), Good (80-89), Fair (65-79), Average (50-64), Weak (35-49), Poor (<35). Conduct scores are required for scholarships and graduation.',
     22, 'POLICY'),

    ('exam-regulations-and-absenteeism-vi', 'vi',
     'Quy chế thi kết thúc học phần và xử lý vắng thi',
     'Quy chế tổ chức thi và đánh giá học phần: 1. Điều kiện dự thi: Sinh viên phải tham gia lớp học tối thiểu 80% số tiết học lý thuyết và 100% các buổi thực hành/thí nghiệm; nếu vắng quá 20% số tiết học phần sẽ bị cấm thi và nhận điểm F (phải đăng ký học lại). 2. Quy định phòng thi: Sinh viên phải xuất trình thẻ sinh viên (hoặc CCCD kèm giấy xác nhận) khi vào phòng thi; không mang điện thoại di động, đồng hồ thông minh và thiết bị truyền tin vào khu vực thi. 3. Xử lý vắng thi: Sinh viên vắng thi không có lý do chính đáng sẽ nhận điểm 0 thi kết thúc học phần. Trường hợp vắng thi vì lý do bất khả kháng (ốm đau, tai nạn, việc tang gia đình), sinh viên phải nộp đơn xin hoãn thi kèm bệnh án/giấy xác nhận y tế hợp lệ cho Phòng Đào tạo trong vòng 7 ngày làm việc kể từ ngày thi để được bố trí thi bù ở đợt thi gần nhất.',
     22, 'POLICY'),

    ('exam-regulations-and-absenteeism-en', 'en',
     'End-of-course examination regulations and absenteeism policy',
     'Examination organization and evaluation rules: 1. Exam eligibility: Students must attend at least 80% of theoretical lectures and 100% of laboratory sessions; exceeding 20% absences results in an exam ban, letter grade F, and mandatory retake. 2. Examination room protocol: Present student ID or national identity card; mobile phones and smartwatches are strictly prohibited. 3. Absence handling: Unexcused absences receive a score of 0. For force majeure absences (illness, accidents, bereavement), students must submit an exam deferral application with medical proof to the Academic Affairs Office within 7 business days to be scheduled for a makeup exam.',
     22, 'POLICY'),

    ('leave-of-absence-and-deferment-vi', 'vi',
     'Quy chế tạm dừng học tập, bảo lưu kết quả và thôi học',
     'Quy định về việc tạm ngừng học tập và bảo lưu kết quả học tập tại CampusCore: 1. Điều kiện bảo lưu: Sinh viên được quyền xin tạm ngừng học tập và bảo lưu điểm số nếu thỏa mãn các điều kiện: Đã học ít nhất một học kỳ chính tại trường; không bị cảnh báo học vụ mức 2 hoặc mức 3; không đang trong thời gian bị kỷ luật từ khiển trách trở lên; và có lý do chính đáng (sức khỏe cần điều trị dài ngày, nghĩa vụ quân sự, hoàn cảnh gia đình đặc biệt). 2. Thời gian bảo lưu: Thời gian tạm ngừng học tập mỗi lần không quá 2 học kỳ chính và tổng thời gian tạm dừng không vượt quá khung thời gian đào tạo tối đa của chương trình. Sinh viên tham gia nghĩa vụ quân sự được bảo lưu toàn bộ thời gian phục vụ quân ngũ. 3. Thủ tục quay lại học tập: Ít nhất 2 tuần trước khi bắt đầu học kỳ mới, sinh viên phải nộp đơn xin trở lại học tập tại Phòng Đào tạo để được kích hoạt lại tài khoản và đăng ký lớp học phần.',
     24, 'POLICY'),

    ('leave-of-absence-and-deferment-en', 'en',
     'Regulations on temporary absence, result deferment and academic withdrawal',
     'Rules on academic leave of absence and result deferment at CampusCore: 1. Eligibility: Students may request temporary absence with preserved academic results if they have completed at least one semester; are not under Level 2/3 academic warning; have no disciplinary reprimands; and hold valid reasons (medical treatment, military service, exceptional family hardship). 2. Duration: Leave is granted for up to 2 regular semesters per request, not exceeding maximum permissible program duration. Military service leave is preserved for the full active duty term. 3. Resumption of studies: Submit a study resumption request to the Academic Affairs Office at least 2 weeks before the new term starts.',
     24, 'POLICY'),

    ('curriculum-gpa-weighting-and-credit-system-vi', 'vi',
     'Cơ cấu điểm học phần, cách tính GPA và hệ thống tín chỉ',
     'Quy chuẩn đánh giá và tính điểm học phần theo hệ thống tín chỉ: 1. Cơ cấu điểm học phần: Điểm tổng kết học phần là điểm trung bình có trọng số gồm Điểm quá trình (chiếm 50%, bao gồm điểm danh, kiểm tra định kỳ, bài tập lớn, thuyết trình) và Điểm thi kết thúc học phần (chiếm 50%). Điểm học phần được chấm theo thang điểm 10, làm tròn đến một chữ số thập phân. 2. Quy đổi thang điểm chữ và thang điểm 4: Điểm 8.5-10.0 tương ứng loại A (thang 4: 4.0); 8.0-8.4 loại B+ (3.5); 7.0-7.9 loại B (3.0); 6.5-6.9 loại C+ (2.5); 5.5-6.4 loại C (2.0); 5.0-5.4 loại D+ (1.5); 4.0-4.9 loại D (1.0); dưới 4.0 là loại F (0.0 - không đạt). 3. Điểm trung bình GPA và CPA: Điểm trung bình học kỳ (GPA) và điểm trung bình tích lũy (CPA) được tính bằng tổng tích số giữa điểm số thang 4 của từng môn với số tín chỉ của môn đó, chia cho tổng số tín chỉ đã tích lũy.',
     20, 'ACADEMIC_CATALOG'),

    ('curriculum-gpa-weighting-and-credit-system-en', 'en',
     'Course component weights, GPA calculation and credit system',
     'Credit-based academic grading and GPA standards: 1. Course grading breakdown: Final course scores are computed from In-term Assessment (50%, including attendance, quizzes, assignments) and Final Exam (50%). Courses are marked on a 10-point scale rounded to one decimal place. 2. Letter grade and 4.0 conversions: 8.5-10.0 = A (4.0); 8.0-8.4 = B+ (3.5); 7.0-7.9 = B (3.0); 6.5-6.9 = C+ (2.5); 5.5-6.4 = C (2.0); 5.0-5.4 = D+ (1.5); 4.0-4.9 = D (1.0); below 4.0 = F (0.0 - fail). 3. GPA & CPA calculation: Semester GPA and Cumulative GPA (CPA) are computed as the sum of products of 4.0 grade points and course credits divided by total attempted credits.',
     20, 'ACADEMIC_CATALOG'),

    ('student-affairs-services-and-certificates-vi', 'vi',
     'Dịch vụ hành chính một cửa sinh viên, cấp giấy tờ và BHYT',
     'Hướng dẫn thực hiện các thủ tục hành chính tại Bộ phận Một cửa - Phòng Công tác Sinh viên: 1. Cấp giấy tờ học vụ: Sinh viên yêu cầu Giấy xác nhận đang là sinh viên, Giấy xác nhận vay vốn ngân hàng chính sách xã hội, Bảng điểm tạm thời thông qua Cổng dịch vụ sinh viên trực tuyến hoặc trực tiếp tại quầy một cửa, thời gian trả kết quả từ 1 đến 2 ngày làm việc. 2. Cấp lại thẻ sinh viên: Sinh viên bị mất thẻ hoặc hư hỏng thẻ làm thủ tục cấp lại trực tuyến, lệ phí theo quy định và nhận thẻ mới sau 5-7 ngày làm việc. 3. Bảo hiểm y tế (BHYT) bắt buộc: 100% sinh viên chính quy có trách nhiệm tham gia BHYT theo Luật BHYT Việt Nam. Mức đóng và thời hạn nộp được thông báo vào đầu năm học; thẻ BHYT có giá trị khám chữa bệnh tại các cơ sở y tế theo quy định của Bảo hiểm xã hội. 4. Chế độ chính sách và trợ cấp xã hội: Sinh viên thuộc đối tượng con thương binh, liệt sĩ, hộ nghèo, dân tộc thiểu số nộp hồ sơ xét miễn giảm học phí và trợ cấp xã hội vào tháng đầu tiên của mỗi học kỳ.',
     25, 'POLICY'),

    ('student-affairs-services-and-certificates-en', 'en',
     'One-stop student affairs services, certificates and health insurance',
     'Administrative procedures at the Student Affairs One-Stop Service Center: 1. Student certificates: Request enrollment verification letters, bank student loan letters, and unofficial transcripts via the student portal; processing time is 1-2 business days. 2. Student ID replacement: Re-issuance requests for lost or damaged student cards can be submitted online; new cards are issued within 5-7 business days. 3. Mandatory Health Insurance (BHYT): All enrolled students must maintain annual statutory health insurance coverage under Vietnam Social Security regulations. 4. Tuition fee reduction and welfare subsidies: Students eligible for state welfare (war invalids/martyrs children, low-income households, ethnic minorities) must submit verification dossiers within the first month of the term.',
     25, 'POLICY'),

    ('academic-appeals-and-re-evaluation-vi', 'vi',
     'Quy trình khiếu nại và phúc khảo điểm thi học phần',
     'Quy trình tiếp nhận và giải quyết phúc khảo điểm thi: 1. Thời hạn nộp đơn: Trong vòng 7 ngày làm việc kể từ ngày Phòng Đào tạo công bố điểm thi chính thức trên Cổng đào tạo, sinh viên có quyền làm đơn xin phúc khảo điểm bài thi nếu có thắc mắc về kết quả. 2. Địa điểm và hình thức nộp: Nộp đơn phúc khảo trực tuyến trên cổng học vụ hoặc trực tiếp tại Phòng Khảo thí và Đảm bảo chất lượng. Mỗi học phần phúc khảo có mức lệ phí theo quy định của nhà trường (hoàn trả nếu điểm tăng). 3. Hội đồng phúc khảo: Trưởng khoa/bộ môn thành lập tổ chấm phúc khảo gồm 2 giảng viên độc lập (không trùng với người chấm đợt đầu). Nếu điểm phúc khảo chênh lệch từ 0.5 điểm trở lên so với điểm ban đầu (hoặc làm thay đổi điểm chữ), điểm bài thi sẽ được điều chỉnh chính thức và cập nhật lại vào hệ thống quản lý đào tạo.',
     24, 'POLICY'),

    ('academic-appeals-and-re-evaluation-en', 'en',
     'Grade appeal and exam re-evaluation procedure',
     'Procedures for exam grade reviews and regrading appeals: 1. Submission deadline: Within 7 business days from the official grade publication date on the portal, students may submit an appeal request if they contest their exam marks. 2. Submission method: Submit online through the academic portal or in person at the Testing and Quality Assurance Office with the applicable appeal fee (refunded if grade increases). 3. Review committee: Department heads assign 2 independent examiners to re-grade the exam. If the re-evaluated score differs by 0.5 points or more (or changes the letter grade), the score is formally adjusted and updated in the student record system.',
     24, 'POLICY')
) AS seed(slug, locale, title, content, priority, domain)
WHERE NOT EXISTS (SELECT 1 FROM assistant.knowledge_document existing WHERE existing.slug = seed.slug);

INSERT INTO assistant.knowledge_document_revision
    (id, document_id, version, state, locale, slug, title, content, source, priority, created_by, reviewed_by, published_at, domain)
SELECT md5(d.id::text || '-revision-1')::uuid, d.id, 1, 'PUBLISHED', d.locale, d.slug, d.title, d.content,
       d.source, d.priority, 'system-seed', 'system-seed', CURRENT_TIMESTAMP, d.domain
FROM assistant.knowledge_document d
WHERE d.source = 'campuscore-academic-enrichment'
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
SELECT '00000000-0000-0000-0000-000000000041'::uuid, 'local-demo-v41', corpus_hash, row_count, 'MANUAL', 'PUBLISHED',
       jsonb_build_object('schemaVersion', 1, 'corpusVersion', 'local-demo-v41', 'rowCount', row_count, 'sha256', corpus_hash, 'documents', documents),
       'system-migration', CURRENT_TIMESTAMP,
       (SELECT active_release_id FROM assistant.knowledge_runtime_state WHERE singleton = TRUE)
FROM summary
WHERE NOT EXISTS (SELECT 1 FROM assistant.knowledge_release WHERE id = '00000000-0000-0000-0000-000000000041'::uuid);

INSERT INTO assistant.knowledge_runtime_document
    (release_id, source_id, revision_id, version, domain, slug, locale, title, content, source, priority, active, visibility, published_at)
SELECT '00000000-0000-0000-0000-000000000041'::uuid, d.id::text, r.id, r.version,
       COALESCE(r.domain, d.domain, 'POLICY'), d.slug, d.locale, d.title, d.content, d.source, d.priority,
       TRUE, 'PUBLIC', COALESCE(r.published_at, CURRENT_TIMESTAMP)
FROM assistant.knowledge_document d
JOIN assistant.knowledge_document_revision r ON r.document_id = d.id AND r.state = 'PUBLISHED'
WHERE d.active = TRUE AND d.visibility = 'PUBLIC'
  AND NOT EXISTS (
      SELECT 1 FROM assistant.knowledge_runtime_document p
      WHERE p.release_id = '00000000-0000-0000-0000-000000000041'::uuid AND p.source_id = d.id::text
  );

INSERT INTO assistant.knowledge_runtime_state (singleton, active_release_id)
VALUES (TRUE, '00000000-0000-0000-0000-000000000041'::uuid)
ON CONFLICT (singleton) DO UPDATE
SET active_release_id = EXCLUDED.active_release_id, updated_at = CURRENT_TIMESTAMP;
