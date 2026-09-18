-- Flyway Migration V63: Enrich Assistant Knowledge with Past Theses Repository & Comprehensive Campus Portal Directory
-- Expands the curated assistant corpus with:
-- 1. Exemplary past thesis topics across all faculties (FIT, FEEE, FME, FCE, FOE) with final scores, methodologies, and supervisor guidelines.
-- 2. Digital thesis & project repository guidelines, citation ethics, and full-text retrieval standards.
-- 3. Comprehensive directory of CampusUTE web portal services (Course Registration, Schedules, Grades, Conduct Points, Certificates, Finance, Thesis, Profile).
-- Replaces active release with immutable snapshot '00000000-0000-0000-0000-000000000063'.

SET search_path = thesis, assistant, public;

-- 1. Insert new documents into assistant.knowledge_document
INSERT INTO assistant.knowledge_document
    (id, slug, locale, title, content, source, priority, domain)
SELECT md5(seed.slug || '-document')::uuid, seed.slug, seed.locale, seed.title, seed.content,
       'campuscore-thesis-repository', seed.priority, seed.domain
FROM (VALUES
    ('past-thesis-repository-and-reference-topics-vi', 'vi',
     'Kho đề tài khóa luận tốt nghiệp tiêu biểu của sinh viên các khóa trước và tài liệu tham khảo',
     'Kho lưu trữ đề tài khóa luận tốt nghiệp (KLTN) và đồ án chuyên ngành của các khóa sinh viên trước tại Trường Đại học Sư phạm Kỹ thuật TP.HCM (HCM-UTE): 1. Danh mục đề tài tiêu biểu đạt điểm Xuất sắc và Giỏi (8.0 - 9.5/10) theo từng chuyên ngành:
• Khoa Công nghệ Thông tin (FIT):
  - Trí tuệ nhân tạo (AI Lab): "Hệ thống AI Camera phát hiện sớm đám cháy và khói phục vụ an toàn khu dân cư thông minh" (Điểm: 9.2 - Xuất sắc; GVHD: TS. Trần Văn Minh; ứng dụng Computer Vision, YOLOv8 và Edge AI); "Hệ thống tổng hợp và tóm tắt biên bản hội họp tự động bằng mô hình Whisper và LLM" (Điểm: 9.0; xử lý tiếng Việt chuyên sâu và RAG); "Nghiên cứu ứng dụng Transformer trong Giám sát Thông minh" (Điểm: 8.8).
  - Kỹ thuật Phần mềm (Software Engineering): "Nền tảng kiểm thử bảo mật tự động hợp đồng thông minh Smart Contract trên Blockchain" (Điểm: 8.8 - Giỏi; phân tích lỗ hổng reentrancy, overflow trên mạng Ethereum); "Nghiên cứu kiến trúc vi dịch vụ và xử lý đồng thời trong hệ thống quản lý đào tạo" (Điểm: 8.17; Spring Cloud, Kafka, Docker & Kubernetes); "Xây dựng hệ thống thi và đánh giá kết quả học tập trực tuyến" (Điểm: 8.0).
  - Hệ thống Thông tin & Khoa học Dữ liệu (MIS & Data Science): "Phân tích dữ liệu học tập của sinh viên phục vụ cảnh báo sớm kết quả học tập" (Điểm: 8.5; Machine Learning dự báo nguy cơ học vụ); "Hệ thống quản lý điểm rèn luyện sinh viên theo chuẩn đầu ra" (Điểm: 8.3).
• Khoa Điện - Điện tử (FEEE): "Thiết kế chip gia tốc AI chuyên dụng (NPU) trên nền tảng kiến trúc tập lệnh mở RISC-V" (Điểm: 9.5 - Giải Nhất NCKH Sinh viên cấp Trường; thiết kế Verilog/VHDL, tổng hợp trên FPGA Xilinx); "Thiết kế mạch đo thông số môi trường hiển thị OLED truyền dữ liệu qua BLE" (Điểm: 8.6; vi điều khiển ESP32, chuẩn truyền thông công nghiệp).
• Khoa Cơ khí Chế tạo máy (FME Robotics): "Nghiên cứu chế tạo thiết bị đo khúc xạ mắt tự động ứng dụng xử lý ảnh và trí tuệ nhân tạo" (Điểm: 9.0 - Xuất sắc); "Mô phỏng và điều khiển cánh tay robot mini ứng dụng Arduino và ESP32" (Điểm: 8.7; giải thuật động học thuận, nghịch và điều khiển PID).
• Khoa Cơ khí Động lực (Automotive): "Mô phỏng khí động học và giảm thiểu lực cản xe điện bằng phần mềm CFD OpenFOAM" (Điểm: 8.9 - Giỏi; tối ưu hóa kết cấu vỏ xe và nâng cao hiệu suất pin).
• Khoa Xây dựng (Civil): "Giải pháp quan trắc lún và chuyển vị nhà cao tầng thời gian thực bằng cảm biến MEMS" (Điểm: 8.8; tích hợp cảm biến gia tốc và vi sai độ võng).
• Khoa Kinh tế (Logistics): "Hệ thống tự động lập kế hoạch và phân bổ nguồn lực container tại cảng biển thông minh" (Điểm: 8.7; giải thuật quy hoạch tuyến tính và tối ưu hóa chi phí bốc dỡ).
2. Cách khai thác kho tài liệu: Toàn bộ báo cáo toàn văn (file PDF), mã nguồn GitHub và slide bảo vệ được lưu trữ số hóa tại mục Kho lưu trữ luận án & báo cáo (/dashboard/thesis?tab=repository).
3. Lời khuyên chọn đề tài: Sinh viên nên kế thừa và mở rộng từ các đề tài khóa trước, chú trọng tính ứng dụng thực tiễn, tính mới trong giải thuật và trao đổi kỹ với Giảng viên hướng dẫn.',
     15, 'THESIS'),

    ('past-thesis-repository-and-reference-topics-en', 'en',
     'Exemplary past graduation theses repository and reference projects catalog',
     'Institutional repository of exemplary graduation theses and capstone projects from previous student cohorts at HCM-UTE: 1. Distinguished high-scoring theses (8.0 - 9.5/10) by faculty:
• Faculty of Information Technology (FIT):
  - AI & Machine Learning: "Smart AI Camera System for Early Fire and Smoke Detection in Residential Areas" (Score: 9.2/10 - Excellent; YOLOv8, Computer Vision & Edge AI); "Automated Meeting Minutes Transcription and Summarization using Whisper and LLMs" (Score: 9.0/10; Vietnamese NLP & RAG architecture); "Application of Transformers in Intelligent Video Surveillance" (Score: 8.8/10).
  - Software Engineering: "Automated Security Vulnerability Assessment Platform for Smart Contracts on Blockchain" (Score: 8.8/10; Ethereum static analysis); "Microservices Architecture and High-Concurrency Processing for Academic Management Platforms" (Score: 8.17/10; Spring Cloud, Kafka, Docker & Kubernetes); "Online Examination and Student Assessment Management System" (Score: 8.0/10).
  - MIS & Data Science: "Student Academic Analytics and Early Academic Warning System" (Score: 8.5/10; Predictive ML modeling); "Outcome-based Student Conduct and Training Points Management System" (Score: 8.3/10).
• Faculty of Electrical and Electronics Engineering (FEEE): "Design and Implementation of Dedicated Neural Processing Unit (NPU) on Open-source RISC-V Architecture" (Score: 9.5/10 - First Prize University Student Research Award; Verilog/VHDL, FPGA synthesis); "BLE-enabled Environmental Monitoring System with OLED Display" (Score: 8.6/10; ESP32).
• Faculty of Mechanical Engineering (FME Robotics): "Automated Eye Refraction Measurement Device using Image Processing and Artificial Intelligence" (Score: 9.0/10 - Excellent); "Simulation and Control of Mini Robotic Arm based on Arduino and ESP32" (Score: 8.7/10; Kinematics and PID control).
• Faculty of Automotive Engineering: "Aerodynamic Simulation and Drag Reduction for Electric Vehicles using CFD OpenFOAM" (Score: 8.9/10; Shell topology optimization).
• Faculty of Civil Engineering: "Real-time High-rise Settlement and Displacement Monitoring using MEMS Sensors" (Score: 8.8/10).
• Faculty of Economics & Logistics: "Automated Container Resource Scheduling for Smart Maritime Ports" (Score: 8.7/10; Linear programming optimization).
2. Repository Access: Full-text reports (PDF), GitHub repositories, and presentation slides are archived in the Thesis Repository at /dashboard/thesis?tab=repository.
3. Topic Selection Advice: Students are encouraged to build upon previous projects, emphasize algorithmic novelty, and align with faculty supervisor research areas.',
     15, 'THESIS'),

    ('thesis-repository-and-digital-archive-guide-vi', 'vi',
     'Cẩm nang tra cứu và khai thác Kho lưu trữ số hóa Khóa luận tốt nghiệp',
     'Quy định và hướng dẫn tra cứu Kho lưu trữ số hóa Khóa luận tốt nghiệp tại Trường Đại học Sư phạm Kỹ thuật TP.HCM (HCM-UTE): 1. Thành phần kho tài liệu: Mỗi đề tài hoàn thành được lưu trữ gồm: Báo cáo toàn văn (định dạng PDF chuẩn theo quy cách của Trường), Tóm tắt đề tài (Abstract song ngữ Việt - Anh), Slide thuyết trình bảo vệ trước Hội đồng, và Đường dẫn mã nguồn (GitHub/GitLab) hoặc bản vẽ thiết kế kỹ thuật. 2. Quyền truy cập: Cán bộ giảng viên và sinh viên chính quy được quyền tra cứu, tham khảo trực tuyến tại phân hệ Kho lưu trữ (/dashboard/thesis?tab=repository). 3. Quy chuẩn liêm chính học thuật và chống đạo văn: Sinh viên được quyền kế thừa và trích dẫn các nghiên cứu khóa trước nhưng bắt buộc phải dẫn nguồn rõ ràng theo chuẩn IEEE hoặc APA; báo cáo khóa luận phải được kiểm tra qua hệ thống kiểm tra trùng lặp (Turnitin/DoIT) với tỷ lệ tương đồng không vượt quá 20%. 4. Hỗ trợ học thuật: Sinh viên có thể liên hệ Giảng viên hướng dẫn hoặc Bộ môn chuyên trách để được cấp quyền truy cập các bộ dữ liệu thực nghiệm (Dataset) và mô hình nguồn mở của các đề tài xuất sắc.',
     18, 'THESIS'),

    ('thesis-repository-and-digital-archive-guide-en', 'en',
     'Digital thesis repository guidelines and academic reference standards',
     'Regulations and search guidelines for the Institutional Thesis Repository at HCM-UTE: 1. Archived Artifacts: Each completed capstone project contains: Full-text technical report (standardized PDF format), Bilingual Executive Summary (Vietnamese - English), Council Defense Presentation Slides, and Source Code URL (GitHub/GitLab) or engineering blueprints. 2. Access Privileges: Enrolled students and faculty members can browse and review past reports directly in the digital repository at /dashboard/thesis?tab=repository. 3. Academic Integrity and Plagiarism Standards: Citing previous works is encouraged provided references follow IEEE/APA format; thesis reports must undergo similarity checking (Turnitin/DoIT) with a maximum similarity index under 20%. 4. Research Support: Students can contact supervisors or department heads to request access to benchmark datasets and open-source models produced by prior high-scoring research groups.',
     18, 'THESIS'),

    ('campus-portal-comprehensive-service-directory-vi', 'vi',
     'Cẩm nang tra cứu và sử dụng toàn diện các dịch vụ trên Cổng thông tin CampusUTE',
     'Hướng dẫn toàn diện các chức năng nghiệp vụ trên Cổng thông tin CampusUTE (Trường Đại học Sư phạm Kỹ thuật TP.HCM):
1. Đăng ký học phần (/dashboard/register): Đăng ký môn học theo đợt, chọn lớp học phần, điều chỉnh môn học (Add/Drop) trong 2 tuần đầu kỳ, rút học phần trước tuần 6. Quy định tín chỉ: tiêu chuẩn 14 - 28 tín chỉ/học kỳ chính; sinh viên có nhu cầu đăng ký đến 30 tín chỉ cần nộp đơn nâng hạn mức gửi Phòng Đào tạo phê duyệt.
2. Thời khóa biểu cá nhân (/dashboard/schedule): Xem lịch học, lịch giảng dạy theo dạng lưới tuần và danh sách, hiển thị chính xác phòng học (ví dụ: A1-201, Tòa nhà Trung tâm), ca học và giảng viên phụ trách.
3. Tra cứu Điểm & GPA (/dashboard/grades): Xem điểm quá trình (50%), điểm thi kết thúc học phần (50%), điểm chữ (A+, A, B+, B, C+, C, D+, D, F), GPA thang 10 và thang 4.0; nộp đơn phúc khảo hoặc hoãn thi trong 7 ngày làm việc kể từ khi công bố điểm.
4. Đánh giá Điểm rèn luyện (/dashboard/conduct): Tự chấm và theo dõi Điểm rèn luyện (ĐRL) theo 5 tiêu chí Thông tư 16/2015/TT-BGDĐT, xếp loại Xuất sắc (90-100), Tốt (80-89), Khá (65-79), Trung bình (50-64), Yếu (<50).
5. Dịch vụ Một cửa Cấp giấy tờ sinh viên điện tử (/dashboard/certificates): Cấp trực tuyến có mã xác thực QR và in định dạng A4 chuẩn nhà nước: Giấy xác nhận tạm hoãn nghĩa vụ quân sự (Nghị định 13/2016/NĐ-CP), Giấy xác nhận vay vốn Ngân hàng CSXH (Quyết định 157/2007/QĐ-TTg), Giấy làm vé xe buýt sinh viên, Giấy xác nhận người phụ thuộc giảm thuế TNCN, Giấy giới thiệu thực tập tốt nghiệp tại doanh nghiệp.
6. Khóa luận Tốt nghiệp (/dashboard/thesis): Theo dõi đợt KLTN, đề xuất đề tài (giảng viên), đăng ký nhóm sinh viên (tối thiểu 2 người), nộp báo cáo số hóa, nhận điểm phản biện GVPB, lịch bảo vệ hội đồng và tra cứu Kho đề tài khóa trước (/dashboard/thesis?tab=repository).
7. Học phí & Thanh toán (/dashboard/finance): Tra cứu biểu phí, thanh toán trực tuyến qua mã VietQR định danh, chính sách miễn giảm học phí cho đối tượng chính sách và nộp đơn xin gia hạn nộp học phí.
8. Hồ sơ cá nhân (/dashboard/profile): Cập nhật thông tin liên lạc cá nhân, số điện thoại, ảnh thẻ số; họ tên và mã số sinh viên (MSSV) được bảo vệ bất biến theo hồ sơ gốc của Phòng Đào tạo.',
     12, 'GENERAL_FAQ'),

    ('campus-portal-comprehensive-service-directory-en', 'en',
     'Comprehensive CampusUTE portal navigation and student services handbook',
     'Comprehensive user guide to academic and administrative workflows on the CampusUTE Portal (HCM-UTE):
1. Course Registration (/dashboard/register): Browse section schedules, enroll in courses, adjust add/drop during first 2 weeks, and withdraw before week 6. Credit load: 14 to 28 credits per regular semester; students requiring up to 30 credits must submit a credit limit application for Academic Office approval.
2. Timetable & Schedule (/dashboard/schedule): View personalized weekly schedule grid, classroom locations (e.g. A1-201, Central Building), meeting periods, and instructor assignments.
3. Academic Grades & GPA (/dashboard/grades): Access continuous assessment scores (50%), final exam scores (50%), letter grades (A+ to F), 10-point GPA and 4.0 GPA; submit exam deferral or regrade appeals within 7 working days.
4. Conduct Points & Training Evaluation (/dashboard/conduct): Submit self-evaluations across 5 criteria (Circular 16/2015/TT-BGDDT); track conduct tiers (Excellent, Good, Fair, Average, Weak).
5. One-Stop Student Certificates Portal (/dashboard/certificates): Instant A4 PDF issuance with verification QR code: Military Service Deferment (Decree 13/2016/ND-CP), Student Education Loan (Decision 157/2007/QD-TTg), Student Public Transit Bus Pass, Family Tax Exemption, and Enterprise Internship Introduction.
6. Thesis & Capstone Management (/dashboard/thesis): Manage thesis rounds, lecturer proposals, student group formation (min 2 members), milestone report uploads, reviewer scores, council defense schedules, and the digital thesis repository (/dashboard/thesis?tab=repository).
7. Tuition & Financial Services (/dashboard/finance): Review semester invoice, pay via VietQR instant gateway, explore tuition exemption programs, and submit tuition fee extension requests.
8. Personal Profile (/dashboard/profile): Update phone, address, and digital avatar; official name and Student ID (MSSV) remain permanently immutable per Academic Office registry records.',
     12, 'GENERAL_FAQ')
) AS seed(slug, locale, title, content, priority, domain)
ON CONFLICT (slug) DO UPDATE
SET title = EXCLUDED.title,
    content = EXCLUDED.content,
    priority = EXCLUDED.priority,
    domain = EXCLUDED.domain,
    updated_at = CURRENT_TIMESTAMP;

-- 2. Archive superseded published revisions of these documents
UPDATE assistant.knowledge_document_revision r
SET state = 'ARCHIVED'
WHERE r.state = 'PUBLISHED'
  AND r.document_id IN (
      SELECT d.id FROM assistant.knowledge_document d
      WHERE d.slug IN (
          'past-thesis-repository-and-reference-topics-vi',
          'past-thesis-repository-and-reference-topics-en',
          'thesis-repository-and-digital-archive-guide-vi',
          'thesis-repository-and-digital-archive-guide-en',
          'campus-portal-comprehensive-service-directory-vi',
          'campus-portal-comprehensive-service-directory-en'));

-- 3. Publish new versioned revisions for the documents
WITH target AS (
    SELECT d.id, d.locale, d.slug, d.title, d.source, d.priority, d.domain, d.content,
           COALESCE(MAX(r.version), 0) + 1 AS next_version
    FROM assistant.knowledge_document d
    LEFT JOIN assistant.knowledge_document_revision r ON r.document_id = d.id
    WHERE d.active = TRUE AND d.visibility = 'PUBLIC'
      AND d.slug IN (
          'past-thesis-repository-and-reference-topics-vi',
          'past-thesis-repository-and-reference-topics-en',
          'thesis-repository-and-digital-archive-guide-vi',
          'thesis-repository-and-digital-archive-guide-en',
          'campus-portal-comprehensive-service-directory-vi',
          'campus-portal-comprehensive-service-directory-en')
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

-- 4. Compute release summary and create release '00000000-0000-0000-0000-000000000063'
WITH canonical AS (
    SELECT d.id::text AS source_id, r.version, COALESCE(r.domain, d.domain, 'POLICY') AS domain,
           d.slug, d.locale, d.title, d.content, d.source, d.priority
    FROM assistant.knowledge_document d
    JOIN assistant.knowledge_document_revision r
      ON r.document_id = d.id
     AND r.state = 'PUBLISHED'
    WHERE d.active = TRUE
      AND d.visibility = 'PUBLIC'
),
summary AS (
    SELECT
        encode(
            thesis.digest(
                coalesce(
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
SELECT '00000000-0000-0000-0000-000000000063'::uuid,
       'thesis-past-theses-and-portal-directory-v63', corpus_hash, row_count, 'MANUAL', 'PUBLISHED',
       jsonb_build_object(
           'schemaVersion', 1,
           'corpusVersion', 'thesis-past-theses-and-portal-directory-v63',
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
    WHERE id = '00000000-0000-0000-0000-000000000063'::uuid
);

-- 5. Project all published public documents into release '00000000-0000-0000-0000-000000000063'
INSERT INTO assistant.knowledge_runtime_document
    (release_id, source_id, revision_id, version, domain, slug, locale, title, content, source,
     priority, active, visibility, published_at)
SELECT '00000000-0000-0000-0000-000000000063'::uuid,
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
      WHERE existing.release_id = '00000000-0000-0000-0000-000000000063'::uuid
        AND existing.source_id = d.id::text
  );

-- 6. Switch the runtime state to active release v63
UPDATE assistant.knowledge_runtime_state
SET active_release_id = '00000000-0000-0000-0000-000000000063'::uuid,
    updated_at = CURRENT_TIMESTAMP
WHERE singleton = TRUE;
