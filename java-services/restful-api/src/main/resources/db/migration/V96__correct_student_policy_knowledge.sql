-- Append-only factual recovery of V95. Preserve its checksum and immutable
-- history. These are local demonstration/portal-help corrections, not a new
-- institutional policy or evidence of a two-person administrative review.
SELECT active_release_id FROM assistant.knowledge_runtime_state
 WHERE singleton = TRUE FOR UPDATE;

CREATE TEMP TABLE v96_seed_correction (slug text PRIMARY KEY, old_hash text,
                                     title text, content text) ON COMMIT DROP;
INSERT INTO v96_seed_correction (slug, old_hash, title, content) VALUES
    ('campus-announcements-guide-vi', 'ddac70f19cf7c7afe6cccaeb7f027876', 'Thông báo: tìm và đọc nội dung', '## Thông báo

Mở mục Thông báo, dùng tìm kiếm và bộ lọc để tìm bài viết, rồi mở chi tiết để đọc nội dung và tài liệu đính kèm. Việc tạo, sửa và phát hành bài viết phụ thuộc vai trò và quyền phía máy chủ. Khi không thấy một thông báo cần thiết, kiểm tra bộ lọc, phiên đăng nhập và hỏi đơn vị phát hành; không suy ra rằng hạn đăng ký đã thay đổi.'),
    ('campus-announcements-guide-en', 'b1adbf4aad166d30fe2305c18886b252', 'Announcements: finding and reading notices', '## Announcements

Open Announcements, use search and filters, then open a notice to read its content and attachments. Creating, editing and publishing depend on the role and server permissions. If an expected notice is missing, check filters and your session and contact the issuing office; a missing notice does not establish a changed deadline.'),
    ('policy-major-transfer-vi', 'a8866a33f9a08d0095e6c1463da1c212', 'Chuyển ngành: xác nhận điều kiện theo chương trình', '## Chuyển ngành

Điều kiện chuyển ngành phụ thuộc quy chế, khóa tuyển sinh, chương trình và thông báo áp dụng. Dữ liệu CampusUTE chưa xác nhận điều kiện của từng sinh viên; không thể kết luận đủ điều kiện chỉ vì đã học xong một học kỳ. Liên hệ Phòng Đào tạo để xác nhận điều kiện, thời hạn, hồ sơ và học phần được công nhận. Tra cứu thông báo chính thức tại https://sao.hcmute.edu.vn/ ; cổng hiện không thay thế quyết định chuyển ngành.'),
    ('policy-major-transfer-en', '388d777e2fc6a2bb3666b80f88bb9996', 'Major transfer: confirm the applicable eligibility rules', '## Major transfer

Eligibility depends on the regulation, admission cohort, program and applicable notice. CampusUTE does not establish individual eligibility; completing one semester alone does not prove eligibility. Ask Academic Affairs to confirm conditions, deadlines, required documents and recognized courses. Consult official notices at https://sao.hcmute.edu.vn/ ; this portal does not replace a transfer decision.'),
    ('reg-credit-transfer-vi', '4cd554f75552939fcf8713c08b8e4df0', 'Công nhận tín chỉ và chuyển đổi học phần', '## Công nhận tín chỉ

Việc công nhận học phần phải được đơn vị có thẩm quyền xét từ bảng điểm, đề cương và quy chế áp dụng. CampusUTE chưa có dữ liệu quyết định công nhận từng hồ sơ; không dùng một tỷ lệ tương đương tự suy đoán để cam kết miễn học phần. Chuẩn bị bảng điểm và đề cương, hỏi Phòng Đào tạo về hồ sơ và kết quả được duyệt. Sau khi có quyết định, đối chiếu học phần được ghi nhận trên bảng điểm.'),
    ('reg-credit-transfer-en', 'a32a48e8b8cd8d44a23c992c6cc50cf6', 'Credit recognition and course transfer', '## Credit recognition

An authorized office assesses transcripts, syllabi and the applicable regulation. CampusUTE does not contain an individual credit-recognition decision; do not promise an exemption using a guessed equivalence ratio. Prepare the transcript and syllabi and ask Academic Affairs about the application and approved result. After approval, check the courses actually recorded on the transcript.'),
    ('policy-graduation-classification-vi', '553e10e376b657e59022b64b4b71550b', 'Xếp loại tốt nghiệp và điểm hiển thị trên cổng', '## Xếp loại tốt nghiệp

Bảng điểm hiển thị kết quả học tập và điểm trung bình theo dữ liệu hệ thống. Điểm hiển thị không phải quyết định công nhận tốt nghiệp hay xếp loại văn bằng. Điều kiện, cách xếp loại và ảnh hưởng của kỷ luật phải theo quy chế áp dụng cho khóa học và quyết định của trường. Liên hệ Phòng Đào tạo để xác nhận kết quả chính thức; không suy ra xếp loại tốt nghiệp chỉ từ điểm rèn luyện.'),
    ('policy-graduation-classification-en', 'aaaba1fd87e204d45731e2a0f4709f55', 'Graduation classification and portal grades', '## Graduation classification

The transcript displays recorded academic results and averages. Those values are not a graduation decision or an official diploma classification. Conditions, classification rules and disciplinary consequences depend on the applicable cohort regulation and university decision. Ask Academic Affairs for the official result; do not infer diploma classification from conduct scores alone.'),
    ('policy-max-study-duration-vi', 'b2335a3153bc4fdcf8357c9a482dfa05', 'Thời gian đào tạo tối đa: xác nhận theo khóa học', '## Thời gian đào tạo tối đa

Thời hạn phụ thuộc chương trình, khóa tuyển sinh và quy chế áp dụng, kể cả cách xử lý thời gian bảo lưu. Dữ liệu CampusUTE chưa xác nhận thời hạn tối đa cho từng hồ sơ; không mặc định mọi chương trình bốn năm đều có giới hạn sáu năm. Hỏi Phòng Đào tạo về thời hạn của bạn, học phần còn thiếu và thủ tục được phép thực hiện trước hạn. Không thể cam kết gia hạn hoặc quyền đăng ký học phần khi chưa có quyết định.'),
    ('policy-max-study-duration-en', 'a456aa129c89f9eabf0e9f0b8638deb3', 'Maximum study duration: confirm the cohort rule', '## Maximum study duration

The limit depends on the program, admission cohort and applicable regulation, including how suspended study is counted. CampusUTE does not establish each student’s maximum duration; do not assume every four-year program has a six-year limit. Ask Academic Affairs about your deadline, outstanding courses and available procedures. An extension or registration entitlement requires the relevant decision.'),
    ('catalog-attendance-policy-vi', '6e83f3b7205af89e16654e432ac2fd41', 'Điểm danh: phân biệt tỷ lệ đi học và nghỉ học', '## Điểm danh và chuyên cần

Giảng viên ghi nhận điểm danh; sinh viên đối chiếu trạng thái của lớp học phần và phản ánh sai lệch với giảng viên. Trong dữ liệu quy chế minh họa hiện có của CampusUTE, điều kiện dự thi lý thuyết là tham dự tối thiểu 80%; vắng quá 20% dẫn đến cấm thi. Đây là hai tỷ lệ khác nhau, không phải vắng quá 80%. Dữ liệu minh họa yêu cầu tham dự đủ các buổi thực hành. Trọng số điểm và quy định thực tế phải đối chiếu đề cương, quy chế của khóa học; cổng không tự quyết định miễn trừ.'),
    ('catalog-attendance-policy-en', '32752eb11d1733255cc1aba87abd1554', 'Attendance: distinguish attendance and absence rates', '## Attendance and participation

Lecturers record attendance; students check their course status and report discrepancies to the lecturer. In CampusUTE’s existing demonstration regulation, theory-exam eligibility requires at least80% attendance; more than20% absence results in an exam ban. These are different rates, not an80% absence threshold. The demonstration data requires all practical sessions. Check the syllabus and applicable cohort regulation for actual rules and grade weights; the portal does not decide exemptions.'),
    ('reg-summer-term-vi', '90280cc30f2514f9e6a7c8165b11b92e', 'Học kỳ phụ và đợt đăng ký học phần', '## Học kỳ phụ

Học kỳ phụ có đợt đăng ký, danh sách lớp và hạn mức riêng. Xem mục Đăng ký học phần và thông báo của Phòng Đào tạo để biết đợt đang mở, điều kiện và lớp được phép chọn. Máy chủ kiểm tra điều kiện đăng ký; chatbot không mở đợt, miễn điều kiện hay cấp quyền vượt hạn mức. Học phí và thời hạn thực tế cần xác nhận từ thông báo chính thức.'),
    ('reg-summer-term-en', '6c704d50728f2e2373368f10db58b0b1', 'Summer term and course-registration windows', '## Summer term

The summer term has its own registration window, available sections and limits. Check Course registration and Academic Affairs notices for the open window, conditions and available classes. The server checks registration eligibility; the assistant cannot open a window, waive requirements or authorize extra credits. Confirm actual fees and deadlines from official notices.'),
    ('faq-notification-center-vi', 'b65894dbfece8f862158fe8cb329bad4', 'Chuông thông báo và trạng thái đã đọc', '## Thông báo cá nhân

Chuông thông báo hiển thị số chưa đọc và danh sách thông báo được phép xem. Mở một mục để xem chi tiết; chỉ cập nhật trạng thái đã đọc khi máy chủ xác nhận. Nếu thao tác thất bại, danh sách và số chưa đọc cần được khôi phục; làm mới trang để đối chiếu. Nội dung thông báo không tự thay thế quyết định học vụ. Khi thiếu thông báo quan trọng, xác nhận kết quả với đơn vị phụ trách.'),
    ('faq-notification-center-en', 'db374ca781ee113a2fee497a53324c2b', 'Notification bell and read status', '## Personal notifications

The bell displays the unread count and notices you are authorized to view. Open a notice to read its details; read status depends on server confirmation. After a failed write, the list and unread count must be restored; refresh to reconcile. A notice does not itself replace an academic decision. Confirm an important missing result with the responsible office.'),
    ('faq-student-certificates-vi', '427fef5051fe3112315306458f8841df', 'Giấy xác nhận sinh viên: liên hệ nơi cấp', '## Giấy xác nhận sinh viên

CampusUTE hiện chưa có màn hình gửi và theo dõi yêu cầu giấy xác nhận sinh viên trực tuyến. Không hướng dẫn đến một trang Chứng thực sinh viên chưa tồn tại và không cam kết thời gian cấp giấy. Hỏi Phòng Đào tạo hoặc bộ phận tiếp nhận của trường về loại giấy, hồ sơ, phí, nơi nhận và kênh nộp chính thức. Bảng điểm xem trên cổng không tự trở thành bản giấy được chứng thực.'),
    ('faq-student-certificates-en', 'a546130557f95c0884a8cee15f54ea7b', 'Student certificates: contact the issuing office', '## Student certificates

CampusUTE currently has no screen for submitting and tracking online student-certificate requests. Do not direct students to a nonexistent Student Certificates page or promise an issuance time. Ask Academic Affairs or the university service desk about the document, required information, fees, collection and official application channel. The portal transcript is not automatically a certified document.'),
    ('policy-tuition-refund-vi', '3725f996458969410cd356d240342dec', 'Hoàn học phí: xác nhận từ bộ phận tài chính', '## Hoàn học phí

CampusUTE chưa hiển thị biểu phí, số tiền đủ điều kiện hoàn hoặc trạng thái hồ sơ hoàn phí. Rút học phần trên cổng không tự chứng minh được hoàn đầy đủ, và không có căn cứ để cam kết hoàn toàn bộ trong hai tuần đầu. Liên hệ bộ phận tài chính để xác nhận quy định áp dụng, hạn nộp, hồ sơ và số tiền. Giữ biên lai cùng thông tin học phần; quyết định hoàn thuộc đơn vị có thẩm quyền.'),
    ('policy-tuition-refund-en', '7435b0ec4445c42548388c408be247ac', 'Tuition refunds: confirm with the finance office', '## Tuition refund

CampusUTE does not display fee schedules, eligible refund amounts or refund-application status. Withdrawing a course in the portal does not prove entitlement to a full refund; there is no verified basis here for promising a full refund during the first two weeks. Ask the finance office about the applicable rules, deadline, documents and amount. Keep the receipt and course details; the authorized office decides the refund.'),
    ('faq-student-card-vi', '072e07463db2d02b60499e10b9fd58fb', 'Thẻ sinh viên: xác nhận thủ tục cấp lại', '## Thẻ sinh viên

Khi mất hoặc hỏng thẻ, liên hệ bộ phận phát hành hoặc Công tác Sinh viên để xác nhận thủ tục cấp lại và cách bảo vệ quyền sử dụng thư viện, ra vào nếu có. CampusUTE hiện chưa có màn hình gửi yêu cầu cấp lại thẻ. Thời gian, phí và chức năng thẻ phải theo thông báo của đơn vị phát hành; không cam kết mốc xử lý cho từng hồ sơ từ dữ liệu minh họa.'),
    ('faq-student-card-en', 'f75f3dbc2a62e6fd14fc43a943798d80', 'Student cards: confirm reissue procedures', '## Student card

For a lost or damaged card, contact the issuing office or Student Affairs about reissue and protecting any library or access privileges. CampusUTE currently has no card-reissue request screen. Processing time, fees and card functions depend on the issuing office’s notice; demonstration data does not establish a deadline for an individual application.'),
    ('policy-discipline-appeal-vi', '3e7a5c5886818a4eea36cd84dfbe4630', 'Kỷ luật và khiếu nại: đọc quyết định áp dụng', '## Kỷ luật và khiếu nại

Hình thức xử lý, quyền khiếu nại, thời hạn và ảnh hưởng đến kết quả học vụ phải theo quy chế áp dụng và quyết định được ban hành. CampusUTE chưa chứa quyết định kỷ luật của từng sinh viên; chatbot không kết luận chế tài hoặc cam kết bảo lưu quyền trong lúc khiếu nại. Đọc quyết định, giữ tài liệu liên quan và hỏi đơn vị ban hành về cơ quan tiếp nhận, thủ tục và hạn khiếu nại.'),
    ('policy-discipline-appeal-en', '0babb11ff1a0c2f9a2f74e6c3b46fee9', 'Discipline and appeals: consult the applicable decision', '## Discipline and appeals

Sanctions, appeal rights, deadlines and academic consequences depend on the applicable regulation and issued decision. CampusUTE does not contain an individual disciplinary decision; the assistant cannot determine a sanction or promise preserved rights during an appeal. Read the decision, keep supporting records and ask the issuing office about the receiving authority, procedure and deadline.');

-- Wait for in-flight edits before taking the content/hash snapshot. Holding
-- document and revision1 locks also fences FK-backed later revision inserts.
SELECT d.id FROM assistant.knowledge_document d
JOIN v96_seed_correction c ON c.slug = d.slug
JOIN assistant.knowledge_document_revision r ON r.document_id = d.id AND r.version = 1
FOR UPDATE OF d, r;

-- A later edit, draft or published revision belongs to its author. Only the
-- exact unchanged original seed, including revision1, may be corrected here.
CREATE TEMP TABLE v96_changed_document ON COMMIT DROP AS
SELECT d.id, c.title, c.content
FROM assistant.knowledge_document d
JOIN v96_seed_correction c ON c.slug = d.slug
JOIN assistant.knowledge_document_revision r ON r.document_id = d.id AND r.version = 1
WHERE d.source = 'campuscore-student-policy-completion'
  AND d.active = TRUE AND d.visibility = 'PUBLIC' AND r.state = 'PUBLISHED'
  AND r.created_by = 'system-seed' AND r.reviewed_by = 'system-seed'
  AND md5(d.title || E'\n' || d.content) = c.old_hash
  AND md5(r.title || E'\n' || r.content) = c.old_hash
  AND NOT EXISTS (SELECT 1 FROM assistant.knowledge_document_revision later
                   WHERE later.document_id = d.id AND later.version > 1);

UPDATE assistant.knowledge_document d
   SET title = c.title, content = c.content,
       source = 'campuscore-student-policy-corrected-v96', updated_at = CURRENT_TIMESTAMP
  FROM v96_changed_document c WHERE c.id = d.id;

-- The established governance schema allows exactly one published revision per
-- document. Archive its authoring state; retain all old content and immutable
-- V95 projection/citations before publishing the corrective revision.
UPDATE assistant.knowledge_document_revision r SET state = 'ARCHIVED'
  FROM v96_changed_document c WHERE r.document_id = c.id AND r.version = 1;

INSERT INTO assistant.knowledge_document_revision
    (id, document_id, version, state, domain, locale, slug, title, content,
     source, priority, created_by, reviewed_by, published_at)
SELECT md5(d.id::text || '-student-policy-correction-v96')::uuid, d.id, 2,
       'PUBLISHED', d.domain, d.locale, d.slug, d.title, d.content, d.source,
       d.priority, 'system-migration', 'system-migration', CURRENT_TIMESTAMP
FROM assistant.knowledge_document d JOIN v96_changed_document c ON c.id = d.id;

-- V95 overwrote external authority. Restore exactly its previous published
-- foreign snapshot when V95 is still active. Never promote local text over an
-- already-active foreign or newer governed snapshot.
UPDATE assistant.knowledge_runtime_state s
   SET active_release_id = previous.id, updated_at = CURRENT_TIMESTAMP
  FROM assistant.knowledge_release current_release
  JOIN assistant.knowledge_release previous ON previous.id = current_release.previous_release_id
 WHERE s.singleton = TRUE AND s.active_release_id = current_release.id
   AND current_release.id = '00000000-0000-0000-0000-000000000095'::uuid
   AND previous.status = 'PUBLISHED' AND previous.source NOT IN ('MANUAL', 'LEGACY');

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM assistant.knowledge_runtime_state s
        JOIN assistant.knowledge_release current_release ON current_release.id = s.active_release_id
        JOIN assistant.knowledge_release previous ON previous.id = current_release.previous_release_id
        WHERE s.singleton = TRUE
          AND current_release.id = '00000000-0000-0000-0000-000000000095'::uuid
          AND previous.source NOT IN ('MANUAL', 'LEGACY')
    ) THEN
        RAISE EXCEPTION 'V96 cannot recover an inactive foreign authority snapshot';
    END IF;
END;
$$;

-- Project only the active immutable localV95 snapshot, replacing only guarded
-- corrected rows with revision2. Unrelated rows and later human revisions are
-- not silently republished. One base row per source guarantees no duplicates.
CREATE TEMP TABLE v96_projected_runtime ON COMMIT DROP AS
SELECT p.source_id, COALESCE(r.id, p.revision_id) AS revision_id,
       COALESCE(r.version, p.version) AS version,
       COALESCE(r.domain, p.domain) AS domain, COALESCE(r.slug, p.slug) AS slug,
       COALESCE(r.locale, p.locale) AS locale, COALESCE(r.title, p.title) AS title,
       COALESCE(r.content, p.content) AS content, COALESCE(r.source, p.source) AS source,
       COALESCE(r.priority, p.priority) AS priority, p.active, p.visibility,
       COALESCE(r.published_at, p.published_at) AS published_at
FROM assistant.knowledge_runtime_state s
JOIN assistant.knowledge_release current_release ON current_release.id = s.active_release_id
JOIN assistant.knowledge_runtime_document p ON p.release_id = current_release.id
LEFT JOIN v96_changed_document c ON c.id::text = p.source_id
LEFT JOIN assistant.knowledge_document_revision r ON r.document_id = c.id AND r.version = 2
WHERE s.singleton = TRUE AND current_release.status = 'PUBLISHED'
  AND current_release.source IN ('MANUAL', 'LEGACY')
  AND current_release.id = '00000000-0000-0000-0000-000000000095'::uuid
  AND EXISTS (SELECT 1 FROM v96_changed_document);

CREATE TEMP TABLE v96_summary ON COMMIT DROP AS
SELECT COUNT(*)::integer AS row_count,
       encode(thesis.digest(COALESCE(string_agg(
           concat_ws('|', source_id, COALESCE(revision_id::text, ''), version::text,
                     domain, slug, locale, title, content, source, priority::text,
                     active::text, visibility), E'\n' ORDER BY source_id), ''), 'sha256'), 'hex') AS corpus_hash,
       COALESCE(jsonb_agg(jsonb_build_object('sourceId', source_id, 'domain', domain,
           'slug', slug, 'locale', locale) ORDER BY source_id), '[]'::jsonb) AS documents
FROM v96_projected_runtime;

INSERT INTO assistant.knowledge_release
    (id, corpus_version, corpus_hash, row_count, source, status, manifest,
     created_by, activated_at, previous_release_id)
SELECT '00000000-0000-0000-0000-000000000096'::uuid, 'local-demo-v96', summary.corpus_hash,
       summary.row_count, 'MANUAL', 'PUBLISHED',
       jsonb_build_object('schemaVersion', 1, 'corpusVersion', 'local-demo-v96',
           'rowCount', summary.row_count, 'sha256', summary.corpus_hash, 'documents', summary.documents),
       'system-migration', CURRENT_TIMESTAMP, s.active_release_id
FROM v96_summary summary CROSS JOIN assistant.knowledge_runtime_state s
WHERE s.singleton = TRUE AND EXISTS (SELECT 1 FROM v96_projected_runtime);

INSERT INTO assistant.knowledge_runtime_document
    (release_id, source_id, revision_id, version, domain, slug, locale, title,
     content, source, priority, active, visibility, published_at)
SELECT '00000000-0000-0000-0000-000000000096'::uuid, source_id, revision_id, version,
       domain, slug, locale, title, content, source, priority, active, visibility, published_at
FROM v96_projected_runtime;

UPDATE assistant.knowledge_runtime_state s
   SET active_release_id = next_release.id, updated_at = CURRENT_TIMESTAMP
  FROM assistant.knowledge_release next_release
 WHERE s.singleton = TRUE AND next_release.id = '00000000-0000-0000-0000-000000000096'::uuid
   AND s.active_release_id = '00000000-0000-0000-0000-000000000095'::uuid
   AND EXISTS (SELECT 1 FROM v96_projected_runtime);
