# RESTful API consolidation decision

Trạng thái: **course runtime đã thu gọn, còn chờ terminal verification và
merge vào `main`**.

CampusCore vận hành một Java Spring Boot RESTful API duy nhất. API sở hữu
toàn bộ `/api/v1`; không còn service sibling, gateway hoặc runtime adapter.
Next.js và Expo dùng chung OpenAPI contract, còn PostgreSQL local là nguồn dữ
liệu duy nhất.

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
