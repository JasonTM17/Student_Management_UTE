UPDATE assistant.knowledge_document
SET slug = 'thesis-assistant-usage-en', title = 'How to use the thesis assistant',
    content = 'Describe the concrete problem in one short sentence: topic, group, defense schedule, or approval status. The clearer the question, the tighter the retrieval and the shorter the answer.',
    source = 'assistant-guidance', priority = 50, updated_at = CURRENT_TIMESTAMP
WHERE id = '11111111-1111-1111-1111-111111111105';

UPDATE assistant.knowledge_document
SET slug = 'thesis-defense-prep-en', title = 'Defense preparation',
    content = 'For defense preparation, track the room, time, scoring status, and any required feedback. If the schedule has not appeared yet, confirm it with the advisor or academic office before treating it as a system error.',
    source = 'course-spec', priority = 30, updated_at = CURRENT_TIMESTAMP
WHERE id = '11111111-1111-1111-1111-111111111103';

UPDATE assistant.knowledge_document
SET slug = 'thesis-group-registration-en', title = 'Register a thesis group',
    content = 'When registering a group, confirm the round is open, the member limit is respected, roles are clear, and the topic approval state is valid before submitting another request. If a student already belongs to another group in the same round, clear the old state before creating a new request.',
    source = 'course-spec', priority = 20, updated_at = CURRENT_TIMESTAMP
WHERE id = '11111111-1111-1111-1111-111111111102';

UPDATE assistant.knowledge_document
SET slug = 'thesis-progress-en', title = 'Track thesis progress',
    content = 'When checking progress, review the round, topic, group, members, and published result state. If the topic is not approved yet, resolve approval first before trying to submit other changes.',
    source = 'course-spec', priority = 40, updated_at = CURRENT_TIMESTAMP
WHERE id = '11111111-1111-1111-1111-111111111104';

UPDATE assistant.knowledge_document
SET slug = 'thesis-topic-selection-en', title = 'Choose a thesis topic',
    content = 'Choose a thesis topic using three checks: verifiable data, a semester-sized scope, and a suitable supervisor. Start from one research question, then define objectives, data, method, and acceptance criteria.',
    source = 'course-spec', priority = 10, updated_at = CURRENT_TIMESTAMP
WHERE id = '11111111-1111-1111-1111-111111111101';

UPDATE assistant.knowledge_document
SET slug = 'thesis-assistant-usage-vi', title = 'Cách dùng trợ lý luận văn',
    content = 'Hãy mô tả vấn đề cụ thể bằng một câu ngắn: đề tài, nhóm, lịch bảo vệ, hội đồng hoặc trạng thái duyệt. Câu hỏi càng rõ thì kết quả tra cứu càng sát và câu trả lời càng gọn.',
    source = 'assistant-guidance', priority = 50, updated_at = CURRENT_TIMESTAMP
WHERE id = '11111111-1111-1111-1111-111111111205';

UPDATE assistant.knowledge_document
SET slug = 'thesis-defense-prep-vi', title = 'Chuẩn bị bảo vệ',
    content = 'Để chuẩn bị bảo vệ, hãy theo dõi hội đồng, phòng, thời gian, trạng thái chấm điểm và các nhận xét cần hoàn tất. Nếu lịch chưa xuất hiện, xác nhận lại với cố vấn hoặc giáo vụ trước khi báo lỗi hệ thống.',
    source = 'course-spec', priority = 30, updated_at = CURRENT_TIMESTAMP
WHERE id = '11111111-1111-1111-1111-111111111203';

UPDATE assistant.knowledge_document
SET slug = 'thesis-group-registration-vi', title = 'Đăng ký nhóm luận văn',
    content = 'Khi đăng ký nhóm, hãy kiểm tra vòng đăng ký đang mở, số lượng thành viên tối đa, vai trò của từng thành viên và trạng thái duyệt đề tài trước khi gửi yêu cầu mới. Nếu một người đã thuộc nhóm khác trong cùng đợt, cần hủy trạng thái cũ trước khi tạo yêu cầu mới.',
    source = 'course-spec', priority = 20, updated_at = CURRENT_TIMESTAMP
WHERE id = '11111111-1111-1111-1111-111111111202';

UPDATE assistant.knowledge_document
SET slug = 'thesis-progress-vi', title = 'Theo dõi tiến độ luận văn',
    content = 'Khi cần kiểm tra tiến độ, hãy xem trạng thái round, topic, nhóm, thành viên và kết quả công bố. Nếu đề tài chưa được duyệt, ưu tiên giải quyết trạng thái duyệt trước khi cố nộp thêm thay đổi khác.',
    source = 'course-spec', priority = 40, updated_at = CURRENT_TIMESTAMP
WHERE id = '11111111-1111-1111-1111-111111111204';

UPDATE assistant.knowledge_document
SET slug = 'thesis-topic-selection-vi', title = 'Chọn đề tài luận văn',
    content = 'Chọn đề tài dựa trên ba tiêu chí: dữ liệu kiểm chứng được, phạm vi vừa sức trong một học kỳ, và có giảng viên hướng dẫn phù hợp. Bắt đầu từ một câu hỏi nghiên cứu, rồi chốt mục tiêu, dữ liệu, phương pháp và tiêu chí nghiệm thu.',
    source = 'course-spec', priority = 10, updated_at = CURRENT_TIMESTAMP
WHERE id = '11111111-1111-1111-1111-111111111201';
