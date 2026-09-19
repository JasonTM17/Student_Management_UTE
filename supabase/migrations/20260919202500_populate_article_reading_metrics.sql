-- Migration 20260919202500_populate_article_reading_metrics.sql
-- Ghi nhận dữ liệu đọc thực tế cho các bài viết tiêu biểu trên CampusUTE

INSERT INTO engagement."ArticleReadingMetric" (
    "id", "announcementId", "readerUserId", "ipAddressHash", "userAgentHash",
    "dwellTimeSeconds", "readPercentage", "firstReadAt", "lastReadAt"
) VALUES
(
    'metric-semi-01',
    'announcement-semiconductor-cleanroom',
    'student-user-105',
    'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855',
    '1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e4f5a6b7c8d9e0f1a2b',
    240, 100, NOW() - INTERVAL '3 days', NOW() - INTERVAL '3 days' + INTERVAL '4 minutes'
),
(
    'metric-semi-02',
    'announcement-semiconductor-cleanroom',
    'student-user-117',
    'a1b2c3d4e5f67890123456789abcdef0123456789abcdef0123456789abcdef0',
    '2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e4f5a6b7c8d9e0f1a2b3c',
    195, 85, NOW() - INTERVAL '2 days', NOW() - INTERVAL '2 days' + INTERVAL '3 minutes'
),
(
    'metric-robot-01',
    'announcement-robotics-iot-lab',
    'student-user-118',
    'c3d4e5f67890123456789abcdef0123456789abcdef0123456789abcdef0123',
    '3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e4f5a6b7c8d9e0f1a2b3c4d',
    310, 100, NOW() - INTERVAL '4 days', NOW() - INTERVAL '4 days' + INTERVAL '5 minutes'
),
(
    'metric-green-01',
    'announcement-green-summer-volunteer',
    'student-user-119',
    'd4e5f67890123456789abcdef0123456789abcdef0123456789abcdef01234',
    '4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e4f5a6b7c8d9e0f1a2b3c4d5e',
    180, 90, NOW() - INTERVAL '5 days', NOW() - INTERVAL '5 days' + INTERVAL '3 minutes'
),
(
    'metric-sports-01',
    'announcement-campus-sports-cup',
    'student-user-120',
    'e5f67890123456789abcdef0123456789abcdef0123456789abcdef012345',
    '5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e4f5a6b7c8d9e0f1a2b3c4d5e6f',
    150, 75, NOW() - INTERVAL '1 day', NOW() - INTERVAL '1 day' + INTERVAL '2 minutes'
),
(
    'metric-hackathon-01',
    'announcement-ai-hackathon-arena',
    'student-user-121',
    'f67890123456789abcdef0123456789abcdef0123456789abcdef0123456',
    '6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e4f5a6b7c8d9e0f1a2b3c4d5e6f7a',
    420, 100, NOW() - INTERVAL '6 days', NOW() - INTERVAL '6 days' + INTERVAL '7 minutes'
),
(
    'metric-library-01',
    'announcement-digital-library-hub',
    'student-user-122',
    '0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef',
    '7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e4f5a6b7c8d9e0f1a2b3c4d5e6f7a8b',
    210, 95, NOW() - INTERVAL '7 days', NOW() - INTERVAL '7 days' + INTERVAL '4 minutes'
),
(
    'metric-grad-01',
    'announcement-commencement-graduation',
    'student-user-123',
    '11223344556677889900aabbccddeeff11223344556677889900aabbccddeeff',
    '8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e4f5a6b7c8d9e0f1a2b3c4d5e6f7a8b9c',
    280, 100, NOW() - INTERVAL '8 days', NOW() - INTERVAL '8 days' + INTERVAL '5 minutes'
)
ON CONFLICT ("id") DO NOTHING;
