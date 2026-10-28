# RESTful API consolidation decision

Trạng thái: **course runtime đã thu gọn; mọi claim release vẫn phải gắn với
một exact SHA sạch và CI tương ứng**.

CampusCore vận hành một Java Spring Boot RESTful API công khai duy nhất. API sở
hữu toàn bộ `/api/v1`; Next.js và Expo dùng chung OpenAPI contract, còn
PostgreSQL local là nguồn dữ liệu của course Compose. Riêng assistant chạy trong
`rag-service` nội bộ và REST API chuyển tiếp qua contract `/internal/rag/**`
được bảo vệ bằng service token; route này không phải public API.

Phạm vi gồm auth, people, academic catalog, sections, enrollment, grades,
schedules, announcements, notifications, thesis core và assistant lexical RAG.
Production Compose tự host dùng Supabase knowledge authority qua sidecar;
`render.yaml` chạy API đơn lẻ, SQL authority theo mặc định, Flyway và DeepSeek
được bật trong blueprint. Đây là cấu hình, chưa chứng minh trạng thái live.
Các dịch vụ độc lập finance, analytics, support, realtime, Redis, RabbitMQ,
MinIO, Nginx và Kubernetes là non-goal; dashboard thống kê học vụ vẫn có trong monolith.

Schema mới được Flyway sở hữu trên database fresh. Không đọc schema legacy và
không migrate dữ liệu cũ. Nhánh RAG học vụ truy vấn knowledge corpus curated,
giới hạn top-k, có locale fallback, citation và reason code cho no-match hoặc
database outage. Personal-context và general-answer có nhánh riêng theo
[hợp đồng assistant](integrations/deepseek-assistant.md).

Mọi claim release phải dựa trên một SHA sạch và được Advisor, Kongming, Wukong,
exact-head reviewer và Stitch kiểm tra lại. Đây là local/course demo
reproducible, không phải production cutover.
