# RESTful API consolidation decision

Trạng thái: **course runtime đã thu gọn; mọi claim release vẫn phải gắn với
một exact SHA sạch và CI tương ứng**.

CampusCore vận hành một Java Spring Boot RESTful API công khai duy nhất. API sở
hữu toàn bộ `/api/v1`; Next.js và Expo dùng chung OpenAPI contract, còn
PostgreSQL local là nguồn dữ liệu duy nhất. Riêng assistant chạy trong
`rag-service` nội bộ và REST API chuyển tiếp qua contract `/internal/rag/**`
được bảo vệ bằng service token; route này không phải public API.

Phạm vi gồm auth, people, academic catalog, sections, enrollment, grades,
schedules, announcements, notifications, thesis core và assistant lexical RAG.
Finance, analytics, support, realtime, external AI, Redis, RabbitMQ, MinIO,
Nginx và Kubernetes là non-goal của đồ án.

Schema mới được Flyway sở hữu trên database fresh. Không đọc schema legacy và
không migrate dữ liệu cũ. Assistant chỉ truy vấn knowledge corpus curated,
giới hạn top-k, có locale fallback, citation và reason code cho no-match hoặc
database outage.

Mọi claim release phải dựa trên một SHA sạch và được Advisor, Kongming, Wukong,
exact-head reviewer và Stitch kiểm tra lại. Đây là local/course demo
reproducible, không phải production cutover.
