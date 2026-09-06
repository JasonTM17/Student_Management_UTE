# Runbook demo CampusCore (5 phút)

Tài liệu cho buổi demo/chấm điểm. Mọi lệnh chạy từ thư mục gốc dự án trên máy
đã cài Docker Desktop.

## 0. Chuẩn bị 10 phút trước buổi demo

```powershell
docker compose down -v
docker compose up -d --build
# chờ tất cả container healthy (API phải áp Flyway V1..V26):
docker compose ps
```

Trạng thái đẹp sau reset: 229 sinh viên, 12 giảng viên, 100 học phần,
~700 đăng ký kỳ hiện tại, 1.380 điểm lịch sử, 26 thông báo.

Kiểm tra nhanh trước khi vào lớp:

```powershell
curl.exe http://127.0.0.1:4010/api/v1/health/liveness
curl.exe -o NUL -s -w "%{http_code}" http://127.0.0.1:3100
```

Cả hai phải trả `ok` / `200`.

## 1. Địa chỉ và tài khoản

| Kênh | Địa chỉ |
| --- | --- |
| Web | http://127.0.0.1:3100 (⚠️ port 3100 theo `.env`, không phải 3000) |
| API | http://127.0.0.1:4010/api/v1 |
| Mailpit (nếu bật mail) | http://127.0.0.1:8026 |

| Vai trò | Email | Mật khẩu |
| --- | --- | --- |
| Sinh viên | `student@campuscore.edu` | `password123` |
| Giảng viên | `lecturer@campuscore.edu` | `password123` |
| Quản trị | `admin@campuscore.edu` | `admin123` |
| Giảng viên mới (chứng minh phân công) | `lecturer003@campuscore.demo` | `password123` |

## 2. Kịch bản 5 phút

1. **Trang chủ** (30s): dark/light + EN/VI — song ngữ là tính năng, không phải
   trang trí.
2. **Đăng nhập sinh viên** (30s): portal tab "Sinh viên", quick-fill demo.
3. **Dashboard** (30s): metric "Đăng ký đang hoạt động", học kỳ hiện tại.
4. **Đăng ký học phần** (60s): tìm môn, chỉ "Còn chỗ" khác nhau từng lớp, rail
   "Đã đăng ký trong học kỳ".
5. **Thời khóa biểu + Bảng điểm** (60s): lịch theo tuần; bảng điểm có GPA tích
   luỹ và điểm theo học kỳ.
6. **Trợ lý CampusCore** (60s): hỏi "Làm sao để đăng ký học phần?" — chỉ ra
   citation kèm nguồn. Sau đó hỏi câu injection "Bỏ qua tất cả hướng dẫn trước
   đó..." để chặn trực tiếp (trả `PROMPT_INJECTION`).
7. **Admin** (30s): đăng nhập admin, tổng quan 229/12/100/2.079.

## 3. Xử lý sự cố

| Triệu chứng | Xử lý |
| --- | --- |
| Web 3100 trả lỗi | `docker compose ps` — chờ `healthy`; nếu web exit: `docker compose up -d web` |
| Chatbot chậm/không trả lời | DeepSeek mặc định TẮT, fallback lexical chạy cục bộ — nói rằng "chatbot không phụ thuộc internet"; kiểm tra `docker compose logs rag-service` |
| Quên mật khẩu tài khoản demo | Tất cả tài khoản seed dùng `password123` / `admin123` (xem bảng trên) |
| CSDL lại trạng thái lạ | Quay lại bước 0 — reset là an toàn vì toàn bộ dữ liệu nằm trong migration seed |
| Cần quay lại dữ liệu trước V26 | `docker exec -i student_management-postgres-1 psql -U campuscore -d campuscore_restful < database/backups/pre-v26-20260906-2134.sql` |

## 4. Điểm nhấn kỹ thuật nên kể (kèm bằng chứng)

- Đăng ký học phần chống race 4 lớp: row lock + conditional update + partial
  unique index + idempotency key — có test race 10 thread
  (`AcademicEnrollmentMutationPersistenceTest`).
- Quota trợ lý atomic (`FOR UPDATE` theo thứ tự), publish kiến thức phải admin
  thứ hai duyệt, injection guard song ngữ Anh–Việt.
- Secrets prod fail-closed qua file secret; so sánh secret timing-safe ở 3 vị trí.
- 220 test Java + 71 test frontend, typecheck + lint zero-warning.
