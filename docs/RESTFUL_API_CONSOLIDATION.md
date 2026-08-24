# RESTful API consolidation decision

Trạng thái: **course runtime đã thu gọn, còn chờ terminal verification và
merge vào `main`**.

CampusCore vận hành một Java Spring Boot RESTful API duy nhất. API sở hữu
toàn bộ `/api/v1`; không còn service sibling, gateway hoặc runtime adapter.
Next.js và Expo dùng chung OpenAPI contract, còn PostgreSQL local là nguồn dữ
liệu duy nhất.

Phạm vi gồm auth, people, academic catalog, registration rounds, sections,
enrollment, grades, schedules, announcements, notifications, thesis core và
assistant lexical RAG với tùy chọn DeepSeek server-only. Finance, analytics,
support, realtime, client-side/provider-unbounded AI, vector search, Redis,
RabbitMQ, MinIO, Nginx và Kubernetes là non-goal của đồ án.

Flyway là schema authority duy nhất. V13-V18 là migration forward-only, có
preflight duplicate/invalid-data stop và rehearsal từ V10/V12; không
down-migrate hoặc tự động sửa dữ liệu mơ hồ. Assistant chỉ truy vấn knowledge
curated và academic-catalog projection công khai, giới hạn top-k, có locale
fallback, citation snapshot và reason code cho no-match, privacy, provider
hoặc database outage.

Mọi claim release phải dựa trên một SHA sạch và được Advisor, Kongming, Wukong,
exact-head reviewer và Stitch kiểm tra lại. Đây là local/course demo
reproducible, không phải production cutover.
