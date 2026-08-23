CREATE SCHEMA IF NOT EXISTS assistant;

CREATE TABLE IF NOT EXISTS assistant.knowledge_document (
    id UUID PRIMARY KEY,
    slug TEXT UNIQUE NOT NULL,
    locale TEXT NOT NULL,
    title TEXT NOT NULL,
    content TEXT NOT NULL,
    source TEXT NOT NULL,
    priority SMALLINT NOT NULL DEFAULT 100,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT assistant_knowledge_locale_valid CHECK (locale IN ('vi', 'en', 'both')),
    CONSTRAINT assistant_knowledge_priority_valid CHECK (priority BETWEEN 1 AND 1000)
);

CREATE INDEX IF NOT EXISTS assistant_knowledge_locale_priority_idx
    ON assistant.knowledge_document (locale, priority);

INSERT INTO assistant.knowledge_document (id, slug, locale, title, content, source, priority)
SELECT CAST('11111111-1111-1111-1111-111111111101' AS UUID), 'en-topic-selection', 'en',
       'Choose a thesis topic',
       'Choose a thesis topic by checking three things: verifiable data, a semester-sized scope, and an available supervisor. Start with one research question, then define objectives, data, method, and acceptance criteria.',
       'academic-office', 10
WHERE NOT EXISTS (SELECT 1 FROM assistant.knowledge_document WHERE slug = 'en-topic-selection');

INSERT INTO assistant.knowledge_document (id, slug, locale, title, content, source, priority)
SELECT CAST('11111111-1111-1111-1111-111111111102' AS UUID), 'en-group-registration', 'en',
       'Register a thesis group',
       'For thesis groups, confirm the registration round is open, the member limit is respected, roles are clear, and the selected topic is approved before submitting another action.',
       'academic-office', 20
WHERE NOT EXISTS (SELECT 1 FROM assistant.knowledge_document WHERE slug = 'en-group-registration');

INSERT INTO assistant.knowledge_document (id, slug, locale, title, content, source, priority)
SELECT CAST('11111111-1111-1111-1111-111111111103' AS UUID), 'en-topic-status', 'en',
       'Understand topic status',
       'A draft topic is still being prepared. A published topic can be selected by a student group. If the group limit is reached, choose another published topic or contact the academic office.',
       'academic-office', 30
WHERE NOT EXISTS (SELECT 1 FROM assistant.knowledge_document WHERE slug = 'en-topic-status');

INSERT INTO assistant.knowledge_document (id, slug, locale, title, content, source, priority)
SELECT CAST('11111111-1111-1111-1111-111111111104' AS UUID), 'en-progress-tracking', 'en',
       'Track thesis progress',
       'Use the thesis progress page to keep milestone dates, supervisor guidance, topic status, and group membership in one place. Update the group status after each approved milestone so the next step is visible.',
       'academic-office', 40
WHERE NOT EXISTS (SELECT 1 FROM assistant.knowledge_document WHERE slug = 'en-progress-tracking');

INSERT INTO assistant.knowledge_document (id, slug, locale, title, content, source, priority)
SELECT CAST('11111111-1111-1111-1111-111111111105' AS UUID), 'en-assistant-usage', 'en',
       'Use the thesis assistant',
       'Ask the assistant about thesis planning, topic selection, group registration, progress status, or the next academic action. Keep the question short and include your current status.',
       'academic-office', 50
WHERE NOT EXISTS (SELECT 1 FROM assistant.knowledge_document WHERE slug = 'en-assistant-usage');

INSERT INTO assistant.knowledge_document (id, slug, locale, title, content, source, priority)
SELECT CAST('11111111-1111-1111-1111-111111111201' AS UUID), 'vi-topic-selection', 'vi',
       'Chọn đề tài luận văn',
       'Bạn nên chọn đề tài theo 3 tiêu chí: dữ liệu có thể kiểm chứng, phạm vi vừa sức trong học kỳ, và có người hướng dẫn phù hợp. Hãy bắt đầu bằng một câu hỏi nghiên cứu, sau đó chia thành mục tiêu, dữ liệu, phương pháp và tiêu chí nghiệm thu.',
       'academic-office', 10
WHERE NOT EXISTS (SELECT 1 FROM assistant.knowledge_document WHERE slug = 'vi-topic-selection');

INSERT INTO assistant.knowledge_document (id, slug, locale, title, content, source, priority)
SELECT CAST('11111111-1111-1111-1111-111111111202' AS UUID), 'vi-group-registration', 'vi',
       'Đăng ký nhóm luận văn',
       'Với nhóm luận văn, hãy kiểm tra vòng đăng ký đang mở, số lượng thành viên tối đa, vai trò của từng bạn và trạng thái duyệt đề tài trước khi gửi yêu cầu mới.',
       'academic-office', 20
WHERE NOT EXISTS (SELECT 1 FROM assistant.knowledge_document WHERE slug = 'vi-group-registration');

INSERT INTO assistant.knowledge_document (id, slug, locale, title, content, source, priority)
SELECT CAST('11111111-1111-1111-1111-111111111203' AS UUID), 'vi-topic-status', 'vi',
       'Hiểu trạng thái đề tài',
       'Đề tài nháp vẫn đang được chuẩn bị. Đề tài đã công bố có thể được nhóm sinh viên lựa chọn. Nếu đã đủ số nhóm, hãy chọn đề tài khác hoặc liên hệ giáo vụ.',
       'academic-office', 30
WHERE NOT EXISTS (SELECT 1 FROM assistant.knowledge_document WHERE slug = 'vi-topic-status');

INSERT INTO assistant.knowledge_document (id, slug, locale, title, content, source, priority)
SELECT CAST('11111111-1111-1111-1111-111111111204' AS UUID), 'vi-progress-tracking', 'vi',
       'Theo dõi tiến độ luận văn',
       'Dùng trang tiến độ luận văn để gom mốc thời gian, hướng dẫn của giảng viên, trạng thái đề tài và thành viên nhóm. Cập nhật trạng thái sau mỗi mốc đã được xác nhận để thấy bước tiếp theo.',
       'academic-office', 40
WHERE NOT EXISTS (SELECT 1 FROM assistant.knowledge_document WHERE slug = 'vi-progress-tracking');

INSERT INTO assistant.knowledge_document (id, slug, locale, title, content, source, priority)
SELECT CAST('11111111-1111-1111-1111-111111111205' AS UUID), 'vi-assistant-usage', 'vi',
       'Dùng trợ lý luận văn',
       'Bạn có thể hỏi trợ lý về định hướng luận văn, chọn đề tài, đăng ký nhóm, trạng thái tiến độ hoặc bước học vụ kế tiếp. Câu hỏi nên ngắn và có trạng thái hiện tại.',
       'academic-office', 50
WHERE NOT EXISTS (SELECT 1 FROM assistant.knowledge_document WHERE slug = 'vi-assistant-usage');
