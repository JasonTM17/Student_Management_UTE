-- V95: complete the student-facing knowledge corpus (chatbot-smoothness wave 2).
-- The retrieval audit (plans/261002-0930-chatbot-latency-uiux-audit) showed twelve
-- high-frequency student questions with zero or near-zero coverage: announcements
-- lifecycle, major transfer, credit transfer, graduation classification, maximum
-- study duration, attendance/ forfeiture threshold, summer term, the personal
-- notification center, online certificates, tuition refund on withdrawal, the
-- student card, and discipline/appeals. Content is deliberately conservative —
-- where the portal does not track a fact (refund rates, card fees, discipline
-- committees), the document says so and routes to the responsible office instead
-- of inventing specifics. Markdown mirrors V93: the lexical fast path serves the
-- top document verbatim, so structure is part of the answer. Protocol mirrors
-- V87/V90: slug-idempotent insert, version-1 PUBLISHED revisions, a new immutable
-- release, full runtime projection, then the activation switch.

INSERT INTO assistant.knowledge_document
    (id, slug, locale, title, content, source, priority, domain)
SELECT md5(seed.slug || '-document')::uuid, seed.slug, seed.locale, seed.title, seed.content,
       'campuscore-student-policy-completion', seed.priority, seed.domain
FROM (VALUES
    ('campus-announcements-guide-vi', 'vi', 'Thông báo học vụ: phân loại, vòng đời và cách theo dõi',
'## Thông báo học vụ

- **Phân loại**: thông báo toàn trường (mọi vai trò), thông báo cho giảng viên hoặc sinh viên, và thông báo lớp học phần của từng môn — người nhận chỉ thấy đúng phạm vi được gửi.
- **Vòng đời**: bản nháp → đăng công khai → hết hiệu lực; thông báo đã đăng có thể chỉnh sửa, ẩn hoặc xóa bởi người đăng và quản trị viên. Thứ tự hiển thị do quản trị viên sắp xếp, thông báo mới nổi lên đầu trang Thông báo.
- **Soạn và đăng**: giảng viên soạn thông báo lớp học phần, quản trị viên đăng thông báo toàn trường, đều qua Trình soạn thảo Học vụ với hình ảnh, bảng và mã nguồn được làm sạch tự động.
- **Theo dõi**: sinh viên xem thông báo ở trang Thông báo; thông báo cá nhân (duyệt hồ sơ, cảnh báo học vụ, hạn đăng ký) vào Trung tâm thông báo trên thanh điều hướng.', 12, 'ANNOUNCEMENT'),
    ('campus-announcements-guide-en', 'en', 'Announcements: categories, lifecycle and how to follow them',
'## Campus announcements

- **Categories**: campus-wide notices (all roles), lecturer- or student-targeted notices, and per-course class announcements — each recipient only sees notices within their scope.
- **Lifecycle**: draft → published → expired; a published notice can be edited, hidden or deleted by its author and administrators. Display order is arranged by administrators, and newer notices rise to the top of the Announcements page.
- **Composing and publishing**: lecturers compose class announcements and administrators publish campus-wide ones, both through the Academic Editor; images, tables and code are sanitized automatically.
- **Following**: students read announcements on the Announcements page; personal notices (application reviews, academic warnings, registration deadlines) arrive in the notification center in the navigation bar.', 12, 'ANNOUNCEMENT'),
    ('policy-major-transfer-vi', 'vi', 'Chuyển ngành: điều kiện, hồ sơ và thời gian xét',
'## Chuyển ngành

- **Điều kiện nguyên tắc**: đã hoàn thành học kỳ đầu tiên của chương trình hiện tại, tích lũy đủ số tín chỉ quy định với điểm trung bình đạt mức tối thiểu, không đang bị xem xét kỷ luật và không đang trong giai đoạn cảnh báo học vụ mức cao nhất.
- **Phạm vi**: xét chuyển giữa các ngành thuộc cùng khối hoặc ngành gần/tương đương; học phần đã đạt với nội dung tương đương được công nhận, học phần chênh lệch phải học bù theo chương trình đào tạo mới.
- **Hồ sơ và thời điểm**: đơn chuyển ngành kèm bảng điểm, nộp tại Phòng Đào tạo trong đợt đầu học kỳ; cổng chưa nhận đơn chuyển ngành trực tuyến.
- **Kết quả**: quyết định do Hiệu trưởng ký; sau khi chuyển, chương trình đào tạo và bản đồ học phần trên cổng cập nhật theo ngành mới ở học kỳ kế tiếp.', 15, 'POLICY'),
    ('policy-major-transfer-en', 'en', 'Major transfer: conditions, paperwork and review window',
'## Major transfer

- **Principle conditions**: completion of the first semester of the current program, the required accumulated credits with a minimum GPA, and no ongoing disciplinary review or highest-level academic warning.
- **Scope**: transfers are considered between majors in the same block or closely related majors; courses whose content is equivalent are recognized, and gaps must be made up under the new curriculum.
- **Paperwork and timing**: a transfer application with the academic transcript is submitted to the Academic Affairs office during the intake window at the start of a semester; the portal does not accept transfer applications online yet.
- **Outcome**: the decision is signed by the Rector; after the transfer, the curriculum and course map in the portal follow the new major from the next semester.', 15, 'POLICY'),
    ('reg-credit-transfer-vi', 'vi', 'Chuyển đổi tín chỉ và công nhận học phần đã học',
'## Chuyển đổi tín chỉ

- **Trường hợp xét**: sinh viên chuyển từ cơ sở đào tạo khác, sinh viên bảo lưu quay lại học, hoặc học viên liên thông nâng bậc — học phần đã đạt tại nơi trước được xét công nhận.
- **Nguyên tắc quy đổi**: nội dung học phần tương đương phần lớn chương trình (thường từ hai phần ba trở lên) mới được chuyển; điểm của học phần chuyển đổi được ghi nhận theo quy chế, không tính lại vào điểm trung bình ở một số đợt xét — kết quả cuối theo từng hồ sơ.
- **Hồ sơ**: bảng điểm gốc có xác nhận, đề cương/chương trình học phần, đơn xin công nhận — nộp tại Phòng Đào tạo trong đợt đầu kỳ.
- **Sau công nhận**: học phần được chuyển xuất hiện trong bảng điểm tích lũy; bạn kiểm tra tiến độ còn lại ở Trang tổng quan và mục Đăng ký học phần.', 15, 'REGISTRATION'),
    ('reg-credit-transfer-en', 'en', 'Credit transfer and recognition of completed courses',
'## Credit transfer

- **Who can apply**: students transferring from another institution, returning students resuming after a suspension, or bridging-program students — courses already passed at the previous institution are considered for recognition.
- **Conversion principle**: a course is transferable when its content substantially matches the target course (typically two thirds or more); how transferred grades are recorded follows the current regulations and each individual review.
- **Paperwork**: the certified original transcript, the course syllabus or program description, and a recognition request — submitted to the Academic Affairs office in the intake window at the start of the semester.
- **After recognition**: transferred courses appear in the cumulative transcript; check your remaining progress on the Dashboard and under Course registration.', 15, 'REGISTRATION'),
    ('policy-graduation-classification-vi', 'vi', 'Xếp loại tốt nghiệp theo điểm trung bình tích lũy',
'## Xếp loại tốt nghiệp

- **Căn cứ xếp loại**: điểm trung bình tích lũy (CPA) thang 4 tính đến khi xét tốt nghiệp, cùng với điểm rèn luyện và kết quả đồ án/khóa luận tốt nghiệp.
- **Ngưỡng tham khảo theo thang 4**: từ 3.60 trở lên là Xuất sắc; từ 3.20 đến dưới 3.60 là Giỏi; từ 2.50 đến dưới 3.20 là Khá; từ 2.00 đến dưới 2.50 là Trung bình — ngưỡng chi tiết và điều kiện kèm theo (chứng chỉ ngoại ngữ, tin học, thời gian đào tạo) theo quy chế hiện hành.
- **Yếu tố khác**: hồ sơ bị kỷ luật mức khiển trách trở lên trong thời gian xét hoặc đồ án không đạt có thể hạ xếp loại hoặc hoãn xét.
- **Công bố**: xếp loại tốt nghiệp được công bố cùng quyết định tốt nghiệp; bản sao bằng điểm cấp tại Phòng Đào tạo.', 16, 'POLICY'),
    ('policy-graduation-classification-en', 'en', 'Graduation classification by cumulative GPA',
'## Graduation classification

- **Basis**: the cumulative GPA on the 4.0 scale at the time of graduation assessment, together with the conduct score and the capstone or thesis result.
- **Reference thresholds on the 4.0 scale**: 3.60 and above is Excellent; 3.20 to below 3.60 is Very good; 2.50 to below 3.20 is Good; 2.00 to below 2.50 is Average — exact thresholds and attached conditions (language and IT certificates, study duration) follow the current regulations.
- **Other factors**: a disciplinary record at censure level or above during the review window, or a failed capstone, can lower the classification or defer the assessment.
- **Publication**: the classification is published with the graduation decision; a certified transcript copy is issued by the Academic Affairs office.', 16, 'POLICY'),
    ('policy-max-study-duration-vi', 'vi', 'Thời gian đào tạo tối đa và xử lý khi hết hạn',
'## Thời gian đào tạo tối đa

- **Khung thời gian**: theo quy chế đào tạo tín chỉ, thời gian tối đa thường bằng thời gian thiết kế của chương trình cộng thêm hai năm (ví dụ chương trình 4 năm tối đa 6 năm) — con số chính xác theo từng chương trình do trường quy định.
- **Học kỳ phụ**: sinh viên sắp hết thời hạn có thể đăng ký học kỳ phụ để hoàn tất học phần còn thiếu và kịp xét tốt nghiệp.
- **Gia hạn**: trường hợp đặc biệt (bảo lưu, lý do khách quan) làm đơn xin gia hạn thời gian học gửi Phòng Đào tạo trước khi hết hạn; thẩm quyền duyệt thuộc Hiệu trưởng.
- **Khi hết thời hạn**: sinh viên không hoàn thành chương trình trong thời gian tối đa bị xem xét thôi học theo quy chế; kết quả các học phần đã đạt được bảo lưu hồ sơ theo quy định.', 16, 'POLICY'),
    ('policy-max-study-duration-en', 'en', 'Maximum study duration and what happens at the limit',
'## Maximum study duration

- **Frame**: under the credit-based regulations the maximum duration is usually the program''s standard length plus two additional years (for example 6 years for a 4-year program) — the exact figure per program is set by the university.
- **Summer term**: students approaching the limit can register for a summer term to finish outstanding courses in time for the graduation assessment.
- **Extension**: for special cases (suspension, objective reasons) students submit a study-extension request to Academic Affairs before the deadline; approval authority rests with the Rector.
- **At the limit**: a student who has not completed the program within the maximum duration is considered for withdrawal under the regulations; results of passed courses are archived in the student record as prescribed.', 16, 'POLICY'),
    ('catalog-attendance-policy-vi', 'vi', 'Điểm danh và chuyên cần: ngưỡng cấm thi 80%',
'## Điểm danh và chuyên cần

- **Cách ghi nhận**: giảng viên điểm danh từng buổi học ngay trên cổng (trang Điểm danh); sinh viên xem trạng thái chuyên cần của từng lớp học phần trong chi tiết lớp.
- **Trọng số**: điểm chuyên cần là một phần của điểm quá trình, thường khoảng 10% cùng kiểm tra giữa kỳ và bài tập — trọng số cụ thể do giảng viên công bố trong đề cương học phần.
- **Ngưỡng cấm thi**: sinh viên vắng quá 80% số buổi lý thuyết của học phần không được dự thi cuối kỳ; học phần thực hành yêu cầu tham dự cao hơn (thường 100% số buổi thực hành).
- **Miễn trừ và khiếu nại**: vắng có lý do chính đáng (ốm đau, nhiệm vụ nhà trường) làm đơn xin miễn kèm chứng minh; sai lệch điểm danh phản ánh với giảng viên trong hai tuần kể từ buổi đó.', 14, 'ACADEMIC_CATALOG'),
    ('catalog-attendance-policy-en', 'en', 'Attendance and the 80% exam-forfeiture threshold',
'## Attendance and participation

- **Recording**: lecturers take attendance for each session directly in the portal (the Attendance page); students see their per-course attendance status in the class detail view.
- **Weight**: the participation score is part of the continuous assessment, typically around 10% alongside the midterm and assignments — the exact weight is published by the lecturer in the course syllabus.
- **Forfeiture threshold**: a student absent from more than 80% of a course''s theory sessions is barred from the final exam; practical courses require higher attendance (usually 100% of practical sessions).
- **Exemptions and appeals**: justified absences (illness, university duties) are handled by an exemption request with evidence; report attendance errors to the lecturer within two weeks of the session.', 14, 'ACADEMIC_CATALOG'),
    ('reg-summer-term-vi', 'vi', 'Học kỳ phụ: đối tượng được học và cách đăng ký',
'## Học kỳ phụ

- **Mục đích**: học kỳ phụ (học kỳ hè) giúp học lại hoặc cải thiện học phần chưa đạt, học bù chênh lệch chương trình, hoặc rút ngắn thời gian đào tạo.
- **Đối tượng**: sinh viên đang trong thời gian đào tạo chính thức; sinh viên bị cảnh báo học vụ ưu tiên đăng ký các học phần cần học lại để thoát cảnh báo.
- **Giới hạn**: khối lượng đăng ký học kỳ phụ thấp hơn học kỳ chính (theo hạn mức quy định cho từng đối tượng), không tính vào mức trần của học kỳ chính.
- **Đăng ký và học phí**: đăng ký qua mục Đăng ký học phần trong đợt riêng của học kỳ phụ; học phí tính theo tín chỉ đăng ký như học kỳ chính — lịch đợt đăng ký theo thông báo của Phòng Đào tạo.', 18, 'REGISTRATION'),
    ('reg-summer-term-en', 'en', 'Summer term: who can enroll and how',
'## Summer term

- **Purpose**: the summer term lets students retake or improve failed courses, make up curriculum gaps, or shorten their study time.
- **Eligibility**: students within their official study duration; students under academic warning should prioritize retaking the courses they need to clear the warning.
- **Limits**: the summer-term workload is lower than a regular semester (per the allowance for each group) and does not count against the regular-semester cap.
- **Registration and fees**: register under Course registration during the summer window; tuition is charged per registered credit like a regular semester — window dates follow the Academic Affairs announcements.', 18, 'REGISTRATION'),
    ('faq-notification-center-vi', 'vi', 'Trung tâm thông báo cá nhân: đã đọc, chưa đọc',
'## Trung tâm thông báo

- **Thông báo nào vào đây**: thông báo cá nhân của bạn — kết quả duyệt hồ sơ, cảnh báo học vụ, hạn chót đăng ký, phản hồi đơn từ chối/giới hạn tín chỉ, và thông báo lớp học phần bạn theo dõi. Thông báo toàn trường nằm ở trang Thông báo, không trộn vào đây.
- **Trạng thái**: mỗi thông báo có trạng thái đã đọc/chưa đọc; biểu tượng chuông hiển thị số chưa đọc, nhấp để mở danh sách.
- **Thao tác**: nhấp một thông báo để xem chi tiết và tự động đánh dấu đã đọc; có thể đánh dấu từng mục hoặc tất cả là đã đọc, xóa thông báo không cần thiết.
- **Không nhận được?**: kiểm tra lại đăng nhập và làm mới trang; nếu mất thông báo quan trọng (hạn đăng ký, duyệt hồ sơ), liên hệ bộ phận phụ trách để xác nhận kết quả qua kênh chính thức.', 15, 'GENERAL_FAQ'),
    ('faq-notification-center-en', 'en', 'Personal notification center: read and unread',
'## Notification center

- **What arrives here**: your personal notices — application results, academic warnings, registration deadlines, credit-limit review outcomes, and announcements from courses you follow. Campus-wide notices live on the Announcements page, not here.
- **Status**: every notice is read or unread; the bell icon shows the unread count — click it to open the list.
- **Actions**: click a notice to open it and mark it read; mark single items or everything as read, and delete notices you no longer need.
- **Not receiving them?**: re-check your session and refresh the page; if an important notice is missing (registration deadline, application review), contact the responsible office to confirm the outcome through an official channel.', 15, 'GENERAL_FAQ'),
    ('faq-student-certificates-vi', 'vi', 'Chứng thực sinh viên: yêu cầu giấy tờ trực tuyến',
'## Chứng thực sinh viên

- **Loại giấy tờ nhận trực tuyến**: xác nhận tình trạng sinh viên, bảng điểm tạm thời, xác nhận phục vụ vay vốn và các thủ tục hành chính phổ biến — gửi yêu cầu ngay trên cổng ở trang Chứng thực sinh viên.
- **Cách làm**: chọn loại giấy tờ, điền thông tin và mục đích sử dụng, gửi yêu cầu; hệ thống ghi nhận trạng thái từ lúc tiếp nhận đến khi hoàn thành.
- **Thời gian và phí**: xử lý thường trong vòng một đến hai ngày làm việc; lệ phí (nếu có) theo bảng phí của trường công bố — cổng chưa thu phí trực tuyến.
- **Nhận kết quả và xử lý từ chối**: nhận bản giấy tờ tại Phòng Đào tạo hoặc bộ phận một cửa; nếu thông tin sai hoặc bị từ chối, kiểm tra lại hồ sơ và liên hệ bộ phận tiếp nhận kèm mã yêu cầu.', 15, 'GENERAL_FAQ'),
    ('faq-student-certificates-en', 'en', 'Student certificates: requesting documents online',
'## Student certificates

- **Documents available online**: student-status confirmation, provisional transcript, loan-purpose confirmation and other common administrative documents — request them directly in the portal''s Student Certificates page.
- **How to**: pick the document type, fill in your details and the purpose, and submit; the system tracks the status from intake to completion.
- **Timing and fees**: processing usually takes one to two working days; any fees follow the university''s published fee schedule — the portal does not collect fees online.
- **Collection and rejections**: collect the issued document at the Academic Affairs office or the one-stop service desk; if details are wrong or the request is refused, review your record and contact the receiving office with the request code.', 15, 'GENERAL_FAQ'),
    ('policy-tuition-refund-vi', 'vi', 'Hoàn học phí khi rút học phần trong hạn',
'## Hoàn học phí

- **Nguyên tắc chung**: rút học phần trong thời hạn cho phép của học kỳ thì phần học phí của học phần đó được xét hoàn theo mốc thời gian rút — rút càng sớm, mức hoàn càng cao.
- **Mốc thời gian**: trong hai tuần đầu của học kỳ (đợt điều chỉnh đăng ký) rút học phần thường được hoàn đầy đủ; sau mốc này, mức hoàn giảm dần và các học phần hủy do lớp không mở được xếp hoàn toàn bộ.
- **Hồ sơ hoàn**: đơn xin hoàn học phí kèm biên lai đã nộp, nộp tại bộ phận tài chính — khoản hoàn hoàn về kênh đã nộp trong thời gian xử lý theo quy định.
- **Lưu ý**: cổng chưa hiển thị biểu phí và trạng thái hoàn phí; số tiền chính xác của từng trường hợp do bộ phận tài chính xác nhận. Lệ phí phúc khảo điểm hoàn lại khi kết quả phúc khảo tăng điểm.', 17, 'POLICY'),
    ('policy-tuition-refund-en', 'en', 'Tuition refund when withdrawing within the window',
'## Tuition refund

- **General principle**: withdrawing a course within the semester''s allowed window makes that course''s tuition eligible for a refund scaled by the withdrawal date — the earlier the withdrawal, the larger the refund.
- **Windows**: withdrawing during the first two weeks (the registration adjustment window) is usually fully refunded; after that point the refund share decreases, and courses cancelled because the class did not open are refunded in full.
- **Refund paperwork**: a refund request with the paid receipt, submitted to the finance office — refunds return through the original payment channel within the prescribed processing time.
- **Notes**: the portal does not display fee tables or refund status; the exact amount per case is confirmed by the finance office. Re-grade fees are refunded when the appeal raises the grade.', 17, 'POLICY'),
    ('faq-student-card-vi', 'vi', 'Thẻ sinh viên: cấp mới, cấp lại và chức năng',
'## Thẻ sinh viên

- **Cấp mới**: thẻ được phát hành lần đầu khi nhập học theo đợt của Phòng Công tác Sinh viên; sinh viên nhận thẻ kèm hướng dẫn kích hoạt tài khoản thư viện và cổng ra vào.
- **Chức năng**: thẻ dùng để xác minh danh dự trong phòng thi, mượn tài liệu thư viện, quản lý ra vào cổng và các dịch vụ nội bộ — thẻ không gắn chức năng thanh toán trừ khi trường thông báo riêng.
- **Cấp lại**: thẻ mất hoặc hư hỏng làm đơn cấp lại tại bộ phận một cửa; thường mất từ năm đến bảy ngày làm việc và có lệ phí cấp lại theo quy định — cổng chưa nhận đề nghị cấp lại trực tuyến.
- **Khóa thẻ**: khi mất thẻ, báo ngay để khóa quyền mượn sách và ra vào; thẻ tìm lại được mở khóa tại bộ phận phát hành.', 18, 'GENERAL_FAQ'),
    ('faq-student-card-en', 'en', 'Student card: issuance, reissue and functions',
'## Student card

- **First issuance**: the card is issued at enrollment in waves run by the Student Affairs office; students receive it together with activation instructions for the library account and gate access.
- **Functions**: the card verifies identity in exams, borrows library items, manages gate access and other internal services — it carries no payment function unless the university announces one.
- **Reissue**: a lost or damaged card requires a reissue request at the one-stop service desk; it usually takes five to seven working days with a reissue fee per the regulations — the portal does not accept reissue requests online.
- **Locking**: when a card is lost, report it immediately to suspend borrowing and gate rights; a recovered card is unlocked at the issuing office.', 18, 'GENERAL_FAQ'),
    ('policy-discipline-appeal-vi', 'vi', 'Kỷ luật sinh viên và thủ tục khiếu nại',
'## Kỷ luật sinh viên

- **Các hình thức kỷ luật**: nhắc nhở khắc phục hậu quả, khiển trách, cảnh cáo, đình chỉ học tập có thời hạn — mức độ áp dụng theo tính chất vi phạm và tiền án kỷ luật.
- **Vi phạm điển hình**: gian lận thi cử (nhìn bài, mang tài liệu, thi hộ), vi phạm nội quy phòng thi và khu vực học tập, hành vi xâm phạm tài sản và danh dự trong trường.
- **Hậu quả kèm theo**: kỷ luật mức khiển trách trở lên có thể ảnh hưởng xét học bổng, xét chuyển ngành, xét đồ án và xếp loại tốt nghiệp trong thời hạn ghi ở quyết định.
- **Khiếu nại**: sinh viên có quyền khiếu nại quyết định kỷ luật bằng đơn gửi Hội đồng kỷ luật trong thời hạn theo quy chế; trong thời gian xét khiếu nại, các quyền liên quan được bảo lưu theo quyết định tạm thời của nhà trường.', 16, 'POLICY'),
    ('policy-discipline-appeal-en', 'en', 'Student discipline and the appeal procedure',
'## Student discipline

- **Disciplinary levels**: remedial reminder, censure, warning, and suspension for a fixed period — the level applied follows the nature of the violation and the disciplinary history.
- **Typical violations**: exam cheating (copying, smuggled materials, impersonation), violation of exam-room and study-area rules, and acts against property or the dignity of others on campus.
- **Attached consequences**: discipline at censure level or above can affect scholarship review, major transfer, capstone eligibility and graduation classification during the period stated in the decision.
- **Appeals**: a student may appeal a disciplinary decision by petitioning the Discipline Council within the regulation window; while the appeal is pending, affected rights are preserved under the university''s interim decision.', 16, 'POLICY')
) AS seed(slug, locale, title, content, priority, domain)
WHERE NOT EXISTS (SELECT 1 FROM assistant.knowledge_document existing WHERE existing.slug = seed.slug);

INSERT INTO assistant.knowledge_document_revision
    (id, document_id, version, state, locale, slug, title, content, source, priority, created_by, reviewed_by, published_at, domain)
SELECT md5(d.id::text || '-revision-1')::uuid, d.id, 1, 'PUBLISHED', d.locale, d.slug, d.title, d.content,
       d.source, d.priority, 'system-seed', 'system-seed', CURRENT_TIMESTAMP, d.domain
FROM assistant.knowledge_document d
WHERE d.source = 'campuscore-student-policy-completion'
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
SELECT '00000000-0000-0000-0000-000000000095'::uuid, 'local-demo-v95', corpus_hash, row_count, 'MANUAL', 'PUBLISHED',
       jsonb_build_object('schemaVersion', 1, 'corpusVersion', 'local-demo-v95', 'rowCount', row_count, 'sha256', corpus_hash, 'documents', documents),
       'system-migration', CURRENT_TIMESTAMP,
       (SELECT active_release_id FROM assistant.knowledge_runtime_state WHERE singleton = TRUE)
FROM summary
WHERE NOT EXISTS (SELECT 1 FROM assistant.knowledge_release WHERE id = '00000000-0000-0000-0000-000000000095'::uuid);

INSERT INTO assistant.knowledge_runtime_document
    (release_id, source_id, revision_id, version, domain, slug, locale, title, content, source, priority, active, visibility, published_at)
SELECT '00000000-0000-0000-0000-000000000095'::uuid, d.id::text, r.id, r.version,
       COALESCE(r.domain, d.domain, 'THESIS'), d.slug, d.locale, d.title, d.content, d.source, d.priority,
       TRUE, 'PUBLIC', COALESCE(r.published_at, CURRENT_TIMESTAMP)
FROM assistant.knowledge_document d
JOIN assistant.knowledge_document_revision r ON r.document_id = d.id AND r.state = 'PUBLISHED'
WHERE d.active = TRUE AND d.visibility = 'PUBLIC'
  AND NOT EXISTS (
      SELECT 1 FROM assistant.knowledge_runtime_document p
      WHERE p.release_id = '00000000-0000-0000-0000-000000000095'::uuid AND p.source_id = d.id::text
  );

INSERT INTO assistant.knowledge_runtime_state (singleton, active_release_id)
VALUES (TRUE, '00000000-0000-0000-0000-000000000095'::uuid)
ON CONFLICT (singleton) DO UPDATE
SET active_release_id = EXCLUDED.active_release_id, updated_at = CURRENT_TIMESTAMP;
