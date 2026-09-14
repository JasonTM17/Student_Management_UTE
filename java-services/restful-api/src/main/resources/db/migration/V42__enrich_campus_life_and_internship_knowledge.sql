-- Campus life, graduation internship, thesis formatting, library, IT accounts, and research knowledge:
-- 1. Graduation internship regulations and enterprise collaboration (quy chế thực tập tốt nghiệp và liên kết doanh nghiệp)
-- 2. Thesis formatting, hardcopy submission, and defense protocol (quy chuẩn trình bày, nộp quyển và báo cáo bảo vệ KLTN)
-- 3. Central library regulations and digital learning databases (quy định mượn trả tài liệu và học liệu số)
-- 4. Student dormitory regulations and residential discipline (nội quy ký túc xá và an ninh trật tự)
-- 5. Campus IT services, student email, portal credentials and Wi-Fi (dịch vụ công nghệ thông tin, email trường, Wi-Fi)
-- 6. Student scientific research awards and academic competitions (nghiên cứu khoa học sinh viên và khen thưởng học thuật)
-- Follows the V20/V38/V39/V40/V41 immutable release projection and runtime state switch pattern.

INSERT INTO assistant.knowledge_document
    (id, slug, locale, title, content, source, priority, domain)
SELECT md5(seed.slug || '-document')::uuid, seed.slug, seed.locale, seed.title, seed.content,
       'campuscore-campus-life-enrichment', seed.priority, seed.domain
FROM (VALUES
    ('internship-enterprise-regulations-vi', 'vi',
     'Quy chế thực tập tốt nghiệp và liên kết doanh nghiệp',
     'Quy định về học phần Thực tập tốt nghiệp (TTTN) và hợp tác doanh nghiệp tại trường: 1. Điều kiện tham gia thực tập: Sinh viên phải tích lũy tối thiểu 100 tín chỉ, hoàn thành các học phần cơ sở ngành và chuyên ngành cốt lõi, không đang trong thời gian bị kỷ luật. 2. Thời lượng và địa điểm: Thời gian thực tập tối thiểu từ 8 đến 12 tuần làm việc thực tế tại các doanh nghiệp, viện nghiên cứu hoặc cơ quan đối tác có lĩnh vực hoạt động phù hợp chuyên ngành đào tạo. Sinh viên có thể tự liên hệ địa điểm thực tập (phải được Khoa phê duyệt) hoặc đăng ký theo danh sách đối tác do Nhà trường điều phối. 3. Đánh giá kết quả: Sinh viên phải duy trì sổ nhật ký thực tập, có nhận xét bằng văn bản kèm dấu mộc xác nhận của đơn vị tiếp nhận thực tập và nộp Báo cáo thực tập tốt nghiệp cho Giảng viên hướng dẫn. Điểm đánh giá gồm Điểm của cơ sở thực tập (40%) và Điểm đánh giá báo cáo của Giảng viên phụ trách (60%).',
     20, 'POLICY'),

    ('internship-enterprise-regulations-en', 'en',
     'Graduation internship regulations and enterprise collaboration',
     'Policies on graduation internships and enterprise partnerships: 1. Eligibility: Students must have accumulated at least 100 credits, completed foundational and core specialized courses, and have no active disciplinary actions. 2. Duration and placement: Internships span 8 to 12 weeks of active full-time work at vetted enterprises, research institutes, or accredited partner organizations in relevant fields. Students may arrange self-selected placements (subject to department approval) or choose university-partnered allocations. 3. Evaluation: Interns must log weekly activities, submit an official performance appraisal signed and sealed by the host company, and present a comprehensive Internship Report. Final grading weights Host Enterprise appraisal (40%) and Academic Supervisor evaluation (60%).',
     20, 'POLICY'),

    ('thesis-formatting-and-submission-guide-vi', 'vi',
     'Quy chuẩn trình bày, nộp quyển và bảo vệ khóa luận tốt nghiệp',
     'Hướng dẫn chi tiết về hình thức trình bày và thủ tục nộp quyển khóa luận tốt nghiệp: 1. Quy cách định dạng: Bản in trên giấy trắng khổ A4 một mặt, bìa cứng màu xanh dương in chữ nhũ vàng kèm bìa phụ; phông chữ Times New Roman cỡ 13, giãn dòng 1.5 lines, lề trái 3.5 cm, lề phải 2.0 cm, lề trên 2.5 cm, lề dưới 2.5 cm. Đánh số trang ở giữa góc dưới. Cấu trúc chuẩn gồm: Trang bìa, Trang phụ bìa, Lời cam đoan, Lời cảm ơn, Nhận xét của GVHD, Nhận xét của GVPB, Mục lục, Danh mục hình vẽ/bảng biểu, Danh mục từ viết tắt, Nội dung các chương, Kết luận & Hướng phát triển, và Tài liệu tham khảo (định dạng chuẩn IEEE). 2. Hồ sơ nộp bảo vệ: Nhóm sinh viên nộp 03 quyển in bìa mềm cho Khoa trước ngày bảo vệ 07 ngày làm việc, kèm bản mềm (PDF) và mã nguồn nộp trên cổng học vụ. 3. Quy trình hội đồng bảo vệ: Mỗi đề tài có tối đa 20 phút thuyết trình và 15 phút trả lời chất vấn của hội đồng. Sau bảo vệ, sinh viên chỉnh sửa theo góp ý của hội đồng và nộp lại 01 quyển bìa cứng mạ vàng lưu thư viện.',
     15, 'THESIS'),

    ('thesis-formatting-and-submission-guide-en', 'en',
     'Thesis formatting, submission standards, and defense protocol',
     'Detailed formatting specifications and submission procedures for graduation theses: 1. Layout requirements: Standard single-sided A4 white paper, hard blue cover with gold embossed lettering; Times New Roman font size 13, line spacing 1.5 lines, margins (Left 3.5cm, Right 2.0cm, Top 2.5cm, Bottom 2.5cm). Page numbers centered at footer. Required sequence: Title page, Sub-cover, Declaration, Acknowledgements, Supervisor review, Reviewer evaluation, Table of contents, List of figures/tables, Abbreviations, Chapter bodies, Conclusion, and References formatted in IEEE style. 2. Defense dossier: Student groups submit 3 soft-bound copies to the Department 7 business days prior to defense, alongside PDF and source code on the portal. 3. Council proceedings: 20-minute presentation followed by 15-minute Q&A. Following defense, students incorporate revisions and deposit 1 gold-stamped hardbound volume to the University Library.',
     15, 'THESIS'),

    ('library-services-and-digital-resources-vi', 'vi',
     'Quy định Thư viện trung tâm và tài nguyên học liệu số',
     'Quy chế khai thác tài nguyên và dịch vụ tại Thư viện trung tâm: 1. Thẻ thư viện: Thẻ sinh viên tích hợp thẻ thư viện, có hiệu lực trong suốt thời gian đào tạo chính khóa. Sinh viên phải xuất trình thẻ tại cổng kiểm soát an ninh và quầy mượn trả sách. 2. Chính sách mượn tài liệu in: Sinh viên được mượn tối đa 05 đầu sách giáo trình/tham khảo về nhà trong thời hạn 14 ngày; được phép gia hạn trực tuyến 01 lần thêm 07 ngày nếu sách không có người khác đặt trước. Trả sách quá hạn chịu mức phí phạt theo quy định. 3. Cơ sở dữ liệu điện tử: Sinh viên được cấp quyền truy cập miễn phí từ xa vào các hệ thống học liệu số uy tín (IEEE Xplore, ScienceDirect, SpringerLink, Thư viện số Luận văn - Luận án và Giáo trình điện tử của Nhà trường) thông qua tài khoản email sinh viên hoặc mạng VPN nội bộ. 4. Không gian học tập: Thư viện cung cấp phòng đọc đa năng, phòng thảo luận nhóm (đặt lịch trước qua ứng dụng) và khu tự học mở cửa 24/7 trong các tuần thi.',
     26, 'POLICY'),

    ('library-services-and-digital-resources-en', 'en',
     'Central library rules and digital learning resources',
     'Operating regulations and digital services at the Central University Library: 1. Library identification: Student smart cards act as library access cards throughout enrolled study programs. Present cards at automated turnstiles and circulation desks. 2. Printed circulation rules: Undergraduates may borrow up to 5 textbooks/reference works for 14 days; renewable online once for an additional 7 days if no reservation holds exist. Overdue items incur statutory per-day late fees. 3. Digital research repositories: Enrolled students receive free off-campus remote access to international scholarly databases (IEEE Xplore, ScienceDirect, SpringerLink, University digital thesis archive, and e-curricula) via university single sign-on credentials or VPN. 4. Study spaces: Includes multimedia reading rooms, bookable group discussion hubs, and a 24/7 silent study hall during midterm and final exam seasons.',
     26, 'POLICY'),

    ('dormitory-regulations-and-security-vi', 'vi',
     'Quy chế ký túc xá và an ninh trật tự nội trú',
     'Nội quy lưu trú và quản lý đời sống tại Ký túc xá sinh viên: 1. Đối tượng ưu tiên xét duyệt: Sinh viên năm nhất trúng tuyển, sinh viên diện chính sách xã hội (con thương binh, liệt sĩ, hộ nghèo, mồ côi), sinh viên vùng sâu vùng xa, và sinh viên có thành tích học tập/rèn luyện xuất sắc. 2. Thời gian biểu và an ninh: Cửa ra vào Ký túc xá mở cửa từ 05h00 và đóng cửa vào lúc 23h00 hàng ngày. Sinh viên về muộn vì lý do học tập, nghiên cứu phải có giấy xác nhận hoặc đăng ký trước với Ban quản lý KTX. 3. Nội quy phòng ở: Nghiêm cấm tàng trữ vũ khí, chất cháy nổ, ma túy, rượu bia; nghiêm cấm sử dụng bếp gas, bàn ủi công suất lớn hoặc thiết bị sinh nhiệt cao gây nguy cơ hỏa hoạn. Sinh viên có trách nhiệm giữ gìn vệ sinh chung, tiết kiệm điện nước và tham gia các hoạt động tự quản phòng ở.',
     28, 'POLICY'),

    ('dormitory-regulations-and-security-en', 'en',
     'Dormitory regulations and residential hall security',
     'Residential governance and student conduct policies for university dormitories: 1. Priority admissions: Incoming freshmen, social welfare beneficiaries (veteran dependents, impoverished households, orphans), students from remote mountainous provinces, and high-achieving academic scholars. 2. Curfew and security: Main dormitory gates operate from 05:00 to 23:00 daily. Late returns due to lab research or official university duties require prior registration or departmental endorsement. 3. Room conduct: Strictly prohibits weapons, flammable substances, narcotics, alcoholic beverages, and high-wattage cooking appliances (gas stoves, immersion heaters). Residents must maintain hygiene standards, conserve utilities, and participate in hall self-governance inspections.',
     28, 'POLICY'),

    ('campus-it-services-and-wifi-vi', 'vi',
     'Dịch vụ Công nghệ thông tin, Email sinh viên và mạng Wi-Fi',
     'Hướng dẫn sử dụng hệ sinh thái số và tài khoản công nghệ tại trường: 1. Tài khoản định danh duy nhất (SSO): Mỗi sinh viên khi nhập học được cấp tài khoản định danh bao gồm Mã số sinh viên (MSSV) và mật khẩu khởi tạo, sử dụng để đăng nhập Cổng thông tin đào tạo, ứng dụng di động CampusCore và các dịch vụ học vụ. 2. Hộp thư điện tử sinh viên: Email chính thức có định dạng [mssv@student.hcmute.edu.vn], tích hợp bộ công cụ văn phòng Microsoft 365 bản quyền (Word, Excel, PowerPoint, Teams, OneDrive 1TB lưu trữ). Mọi thông báo chính thức về học vụ, học phí và điểm số đều gửi qua email này. 3. Mạng không dây Wi-Fi: Mạng không dây phủ sóng toàn bộ khuôn viên trường, ký túc xá và giảng đường; sinh viên kết nối với tên mạng Eduroam hoặc HCMUTE-Student bằng tài khoản định danh sinh viên. 4. Hỗ trợ kỹ thuật: Khi quên mật khẩu hoặc gặp sự cố kỹ thuật, sinh viên sử dụng chức năng Quên mật khẩu trực tuyến qua mã xác thực OTP gửi về số điện thoại chính chủ hoặc liên hệ Trung tâm CNTT.',
     25, 'GENERAL_FAQ'),

    ('campus-it-services-and-wifi-en', 'en',
     'Campus IT services, student email, credentials, and Wi-Fi networks',
     'Guidelines on utilizing university digital ecosystem resources: 1. Single Sign-On (SSO): Incoming students receive a unique identifier (Student ID) and initial password grants access to the portal, CampusCore mobile client, and academic applications. 2. University email account: Official student mailbox [studentId@student.hcmute.edu.vn] includes a licensed Microsoft 365 educational suite (Word, Excel, Teams, 1TB OneDrive cloud storage). All official administrative decrees and academic notices are dispatched to this address. 3. Campus Wi-Fi connectivity: Wireless coverage blankets all academic halls, libraries, and dorms via Eduroam and HCMUTE-Student networks using SSO credentials. 4. Technical support: Self-service password resets are facilitated through SMS/email OTP verification or in-person service at the IT Information Center.',
     25, 'GENERAL_FAQ'),

    ('student-scientific-research-and-awards-vi', 'vi',
     'Quy định nghiên cứu khoa học sinh viên và khen thưởng học thuật',
     'Chính sách khuyến khích hoạt động Nghiên cứu khoa học (NCKH) trong sinh viên: 1. Đăng ký đề tài NCKH: Hàng năm vào đầu học kỳ 1, Phòng Khoa học Công nghệ phát động đăng ký đề tài NCKH sinh viên. Sinh viên có thể chủ trì đề tài cá nhân hoặc nhóm tối đa 05 sinh viên, dưới sự hướng dẫn của 01 giảng viên có chuyên môn. 2. Hỗ trợ kinh phí: Các đề tài được Hội đồng khoa học xét duyệt sẽ được cấp kinh phí nghiên cứu từ Quỹ Phát triển Khoa học Công nghệ của Trường. 3. Quyền lợi và khen thưởng: Sinh viên hoàn thành nghiệm thu đề tài NCKH được: (a) Cộng điểm rèn luyện tiêu chí 1 (từ 5 đến 10 điểm); (b) Xét tặng Giải thưởng Sinh viên NCKH cấp Trường và đề cử tham dự các giải thưởng uy tín cấp Quốc gia (Eurékha, Sinh viên NCKH cấp Bộ GD&ĐT); (c) Được ưu tiên cộng điểm khi xét hồ sơ học bổng doanh nghiệp, học bổng sau đại học và điều kiện nhận Đồ án tốt nghiệp; (d) Công trình có bài báo công bố trên tạp chí khoa học uy tín (Scopus/WoS) được khen thưởng bằng tiền mặt theo quy chế của Nhà trường.',
     18, 'POLICY'),

    ('student-scientific-research-and-awards-en', 'en',
     'Student scientific research policies and academic achievement awards',
     'Incentive structures fostering undergraduate scientific research activities: 1. Project proposal: Annually at the start of Fall semester, the Science & Technology Office opens student research submissions. Projects may be led by individual students or teams up to 5 members supervised by a faculty mentor. 2. Research funding: Approved proposals receive operational stipends disbursed from the University Science Development Endowment. 3. Privileges and merits: Students completing evaluated research gain: (a) Conduct score increments in academic criterion 1 (5 to 10 points); (b) Eligibility for University Research Laurels and nominations to national honors (Euréka, Ministry Awards); (c) Preferential standing in scholarship evaluations and thesis registration prerequisites; (d) Direct financial bonuses for papers indexed in peer-reviewed Scopus/WoS journals.',
     18, 'POLICY')
) AS seed(slug, locale, title, content, priority, domain)
WHERE NOT EXISTS (SELECT 1 FROM assistant.knowledge_document existing WHERE existing.slug = seed.slug);

INSERT INTO assistant.knowledge_document_revision
    (id, document_id, version, state, locale, slug, title, content, source, priority, created_by, reviewed_by, published_at, domain)
SELECT md5(d.id::text || '-revision-1')::uuid, d.id, 1, 'PUBLISHED', d.locale, d.slug, d.title, d.content,
       d.source, d.priority, 'system-seed', 'system-seed', CURRENT_TIMESTAMP, d.domain
FROM assistant.knowledge_document d
WHERE d.source = 'campuscore-campus-life-enrichment'
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
SELECT '00000000-0000-0000-0000-000000000042'::uuid, 'local-demo-v42', corpus_hash, row_count, 'MANUAL', 'PUBLISHED',
       jsonb_build_object('schemaVersion', 1, 'corpusVersion', 'local-demo-v42', 'rowCount', row_count, 'sha256', corpus_hash, 'documents', documents),
       'system-migration', CURRENT_TIMESTAMP,
       (SELECT active_release_id FROM assistant.knowledge_runtime_state WHERE singleton = TRUE)
FROM summary
WHERE NOT EXISTS (SELECT 1 FROM assistant.knowledge_release WHERE id = '00000000-0000-0000-0000-000000000042'::uuid);

INSERT INTO assistant.knowledge_runtime_document
    (release_id, source_id, revision_id, version, domain, slug, locale, title, content, source, priority, active, visibility, published_at)
SELECT '00000000-0000-0000-0000-000000000042'::uuid, d.id::text, r.id, r.version,
       COALESCE(r.domain, d.domain, 'POLICY'), d.slug, d.locale, d.title, d.content, d.source, d.priority,
       TRUE, 'PUBLIC', COALESCE(r.published_at, CURRENT_TIMESTAMP)
FROM assistant.knowledge_document d
JOIN assistant.knowledge_document_revision r ON r.document_id = d.id AND r.state = 'PUBLISHED'
WHERE d.active = TRUE AND d.visibility = 'PUBLIC'
  AND NOT EXISTS (
      SELECT 1 FROM assistant.knowledge_runtime_document p
      WHERE p.release_id = '00000000-0000-0000-0000-000000000042'::uuid AND p.source_id = d.id::text
  );

INSERT INTO assistant.knowledge_runtime_state (singleton, active_release_id)
VALUES (TRUE, '00000000-0000-0000-0000-000000000042'::uuid)
ON CONFLICT (singleton) DO UPDATE
SET active_release_id = EXCLUDED.active_release_id, updated_at = CURRENT_TIMESTAMP;
