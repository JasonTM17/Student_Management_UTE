-- Purpose: seed a realistic, varied notification stream for the demo student
-- and announcement center. Every row is synthetic, idempotent and safe to
-- re-run after a local database rebuild.

INSERT INTO engagement."Announcement"
    ("id", "title", "content", "priority", "targetRoles", "targetYears", "isGlobal",
     "publishAt", "expiresAt", "publishedBy", "semesterId", "semesterName", "courseCode", "courseName")
SELECT seed."id", seed."title", seed."content", seed."priority", seed."targetRoles", seed."targetYears", seed."isGlobal",
       CURRENT_TIMESTAMP - seed."age", CURRENT_TIMESTAMP + seed."ttl", 'admin-user',
       'semester-demo', 'Học kỳ 1 năm học 2026-2027', seed."courseCode", seed."courseName"
FROM (VALUES
    ('announcement-registration-window', 'Mở đợt đăng ký học phần học kỳ 1', 'Sinh viên kiểm tra điều kiện tiên quyết, lịch học và số chỗ trước khi xác nhận đăng ký.', 'HIGH', ARRAY['STUDENT']::TEXT[], ARRAY[2]::INTEGER[], FALSE, INTERVAL '2 hours', INTERVAL '120 days', 'SE401', 'Lập trình Java nâng cao'),
    ('announcement-add-drop-window', 'Thời gian điều chỉnh học phần', 'Đợt thêm, bớt học phần cho phép điều chỉnh đăng ký trong thời gian quy định của học kỳ.', 'HIGH', ARRAY['STUDENT']::TEXT[], ARRAY[]::INTEGER[], FALSE, INTERVAL '1 day', INTERVAL '90 days', NULL, NULL),
    ('announcement-schedule-published', 'Thời khóa biểu đã được công bố', 'Thời khóa biểu mới gồm phòng, ca học và giảng viên. Hãy mở trang TKB để kiểm tra các buổi trùng lịch.', 'NORMAL', ARRAY['STUDENT','LECTURER']::TEXT[], ARRAY[]::INTEGER[], TRUE, INTERVAL '2 days', INTERVAL '120 days', NULL, NULL),
    ('announcement-classroom-a101', 'Cập nhật phòng học A101', 'Phòng A101 đã được gắn cho các lớp thực hành Java; vui lòng đến trước giờ học 10 phút.', 'NORMAL', ARRAY['STUDENT']::TEXT[], ARRAY[]::INTEGER[], TRUE, INTERVAL '3 days', INTERVAL '60 days', 'SE401', 'Lập trình Java nâng cao'),
    ('announcement-course-descriptions', 'Bổ sung mô tả 100 học phần', 'Danh mục học phần đã được làm giàu với mục tiêu, nội dung, tín chỉ và kỹ năng đầu ra để hỗ trợ chọn môn.', 'NORMAL', ARRAY['STUDENT']::TEXT[], ARRAY[]::INTEGER[], TRUE, INTERVAL '4 days', INTERVAL '180 days', NULL, NULL),
    ('announcement-prerequisite-check', 'Nhắc kiểm tra học phần tiên quyết', 'Một số học phần yêu cầu hoàn thành môn tiên quyết. Trợ lý CampusCore có thể giải thích điều kiện theo từng mã môn.', 'NORMAL', ARRAY['STUDENT']::TEXT[], ARRAY[]::INTEGER[], TRUE, INTERVAL '5 days', INTERVAL '120 days', NULL, NULL),
    ('announcement-exam-preparation', 'Lịch kiểm tra giữa kỳ dự kiến', 'Giảng viên sẽ cập nhật lịch kiểm tra trên lớp học phần; sinh viên theo dõi thông báo để chuẩn bị đúng hạn.', 'NORMAL', ARRAY['STUDENT','LECTURER']::TEXT[], ARRAY[]::INTEGER[], TRUE, INTERVAL '6 days', INTERVAL '100 days', NULL, NULL),
    ('announcement-grade-policy', 'Quy định nhập điểm và phúc khảo', 'Điểm thành phần hiển thị theo từng đầu điểm. Nếu có sai lệch, gửi yêu cầu phúc khảo trong thời hạn công bố.', 'NORMAL', ARRAY['STUDENT']::TEXT[], ARRAY[]::INTEGER[], TRUE, INTERVAL '7 days', INTERVAL '180 days', NULL, NULL),
    ('announcement-library-resources', 'Tài nguyên học tập trực tuyến', 'Thư viện số đã bổ sung giáo trình và tài liệu tham khảo cho các nhóm lập trình, cơ sở dữ liệu và kiểm thử.', 'LOW', ARRAY['STUDENT']::TEXT[], ARRAY[]::INTEGER[], TRUE, INTERVAL '8 days', INTERVAL '180 days', NULL, NULL),
    ('announcement-assistant-citations', 'Trợ lý AI hiển thị nguồn trích dẫn', 'Câu trả lời học vụ hiện kèm nguồn dữ liệu và phiên bản phát hành để sinh viên kiểm tra thông tin trước khi thực hiện.', 'NORMAL', ARRAY['STUDENT','LECTURER']::TEXT[], ARRAY[]::INTEGER[], TRUE, INTERVAL '9 days', INTERVAL '180 days', NULL, NULL),
    ('announcement-maintenance-window', 'Bảo trì cổng học vụ định kỳ', 'Hệ thống có thể chậm trong khung giờ bảo trì. Các phiếu đăng ký đã xác nhận vẫn được lưu an toàn trong CSDL.', 'LOW', ARRAY['STUDENT','LECTURER']::TEXT[], ARRAY[]::INTEGER[], TRUE, INTERVAL '10 days', INTERVAL '30 days', NULL, NULL),
    ('announcement-support-channel', 'Kênh hỗ trợ học vụ', 'Khi cần hỗ trợ, mở trung tâm thông báo và gửi mã học phần cùng mô tả lỗi để được xử lý nhanh hơn.', 'LOW', ARRAY['STUDENT','LECTURER']::TEXT[], ARRAY[]::INTEGER[], TRUE, INTERVAL '11 days', INTERVAL '180 days', NULL, NULL)
) AS seed("id", "title", "content", "priority", "targetRoles", "targetYears", "isGlobal", "age", "ttl", "courseCode", "courseName")
WHERE NOT EXISTS (SELECT 1 FROM engagement."Announcement" existing WHERE existing."id" = seed."id");

INSERT INTO notifications.notification
    (id, user_id, title, message, type, link, is_read, read_at, created_at, updated_at)
SELECT seed.id, 'student-user', seed.title, seed.message, seed.type, seed.link, seed.is_read,
       CASE WHEN seed.is_read THEN CURRENT_TIMESTAMP - INTERVAL '1 hour' ELSE NULL END,
       CURRENT_TIMESTAMP - seed.age, CURRENT_TIMESTAMP - seed.age
FROM (VALUES
    ('notification-registration-open', 'Đợt đăng ký học phần đã mở', 'Bạn có thể xem các lớp học phần và số chỗ còn lại trong trang đăng ký.', 'REGISTRATION', '/vi/dashboard/register', FALSE, INTERVAL '1 hour'),
    ('notification-schedule-ready', 'Thời khóa biểu đã sẵn sàng', 'Lịch học tuần của bạn đã được cập nhật với phòng và ca học mới.', 'SCHEDULE', '/vi/dashboard/schedule', FALSE, INTERVAL '3 hours'),
    ('notification-course-data', 'Danh mục học phần vừa được cập nhật', '100 học phần đã có mô tả chi tiết để bạn tra cứu trước khi chọn môn.', 'ACADEMIC', '/vi/dashboard/register', FALSE, INTERVAL '1 day'),
    ('notification-prerequisite-reminder', 'Nhắc kiểm tra tiên quyết', 'Hãy kiểm tra điều kiện tiên quyết trước khi gửi yêu cầu đăng ký.', 'REMINDER', '/vi/dashboard/register', TRUE, INTERVAL '2 days'),
    ('notification-assistant-update', 'Trợ lý CampusCore đã cập nhật', 'Trợ lý hiện trả lời kèm nguồn kiến thức và phiên bản dữ liệu.', 'ASSISTANT', '/vi/dashboard', TRUE, INTERVAL '3 days'),
    ('notification-grade-policy', 'Quy định điểm đã đăng', 'Bạn có thể xem hướng dẫn điểm thành phần và phúc khảo trong trung tâm thông báo.', 'ACADEMIC', '/vi/dashboard/announcements', TRUE, INTERVAL '4 days'),
    ('notification-classroom-change', 'Cập nhật phòng học', 'Một số lớp đã được gắn phòng A101; kiểm tra lại TKB trước khi đến lớp.', 'SCHEDULE', '/vi/dashboard/schedule', FALSE, INTERVAL '5 days'),
    ('notification-support', 'Kênh hỗ trợ học vụ', 'Gửi mã học phần và mô tả lỗi khi cần hỗ trợ để được phản hồi nhanh.', 'SUPPORT', '/vi/dashboard/announcements', TRUE, INTERVAL '6 days')
) AS seed(id, title, message, type, link, is_read, age)
WHERE NOT EXISTS (SELECT 1 FROM notifications.notification existing WHERE existing.id = seed.id);
