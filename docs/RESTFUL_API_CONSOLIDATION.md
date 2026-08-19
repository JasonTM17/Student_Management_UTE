# RESTful API consolidation decision

> Trạng thái: **định hướng đã chốt, chưa cutover**.

CampusCore sẽ giảm độ phức tạp backend cho phạm vi môn học bằng một ứng dụng
Java Spring Boot RESTful API duy nhất. Ứng dụng này là modular monolith: code
được chia package theo domain để dễ duy trì, nhưng không vận hành thành nhiều
backend container.

## Mô hình đích

- `restful-api`: một JAR/container Java 21 duy nhất, chứa auth, academic,
  people, enrollment/grades, finance, engagement, notification, analytics,
  thesis và chatbot adapter.
- `frontend`: Next.js giữ Stitch design system và gọi `/api/v1`.
- `mobile`: Expo/React Native dùng chung API; responsive web không được tính là
  native mobile app.
- PostgreSQL: một cluster và một migration owner. Logical schema theo domain
  được giữ trong wave đầu để tránh merge dữ liệu mạo hiểm.

Chatbot không phải một microservice bắt buộc. Nó là module server-side với mock
provider mặc định, provider thật tùy chọn, timeout/rate limit/fallback/redaction
và không có quyền truy cập DB trực tiếp.

## Vì sao chưa xoá microservices

Topology hiện tại còn các hợp đồng cần bảo toàn: cookie/CSRF/JWT, Socket.IO,
Prisma migrations, file storage, payment/idempotency, queue/cache và các schema
PostgreSQL. Vì vậy service cũ được giữ làm rollback source trong từng wave.

Thứ tự chuyển đổi là: freeze contract → Java shell → thesis/engagement →
notification REST polling → academic/people/enrollment → analytics → finance →
auth cuối cùng → canary/rollback → retirement. Mỗi wave chỉ có một canonical
writer.

## Phạm vi demo tối giản

Profile môn học nên khởi động với API, frontend và PostgreSQL. Redis, MinIO,
observability và provider AI chỉ bật bằng profile khi flow cụ thể cần chúng.
Không vì thế mà xóa cấu hình legacy; profile mới phải được kiểm chứng độc lập
trước khi thay thế topology cũ.

Chi tiết Outcome Contract, acceptance gates và rollback nằm tại
`plans/20260819-restful-api-consolidation/plan.md`.
