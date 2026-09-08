# Runbook demo CampusCore (5 phút)

Tài liệu cho buổi demo/chấm điểm. Mọi lệnh chạy từ thư mục gốc dự án trên máy
đã cài Docker Desktop.

## 0. Chuẩn bị 10 phút trước buổi demo

```powershell
docker compose up -d --build postgres mailpit rag-service restful-api web
# chờ tất cả container healthy (API phải áp Flyway V1..V28):
docker compose ps
```

Lệnh trên giữ dữ liệu hiện có. Không chạy `docker compose down -v` trên stack
đang dùng: lệnh đó xóa volume PostgreSQL, bao gồm dữ liệu tự nhập ngoài seed.

Trạng thái dự kiến của database seed mới: 216 sinh viên demo (student101..300
cộng tài khoản student@), 12 giảng viên, 2 quản trị (admin@ và
admin002@campuscore.demo phục vụ duyệt tri thức 4 mắt), 100 học phần,
~700 đăng ký kỳ hiện tại, trên 5.000 điểm lịch sử (2 học kỳ đóng), 27 thông báo.

Kiểm tra nhanh trước khi vào lớp:

```powershell
curl.exe http://127.0.0.1:4010/api/v1/health/liveness
$demoWebAddress = docker compose port web 3000
docker compose port mailpit 8025
curl.exe -o NUL -s -w "%{http_code}" "http://$demoWebAddress"
```

Health phải trả `ok`, web phải trả `200`. Nếu `.env` đặt cổng khác, dùng
kết quả `docker compose port`, không dùng nhầm dịch vụ khác trên máy.

### Reset dữ liệu demo (tùy chọn, không phải bước khởi động)

Chỉ reset một stack demo dùng riêng khi đã xác nhận dữ liệu có thể bỏ và đã
backup phần cần giữ. Kiểm tra đúng Compose project và volume trước khi xóa;
không reset stack phát triển hoặc production để chuẩn bị buổi demo.
Để diễn tập với database mới, ưu tiên project riêng, cổng riêng không trùng
dịch vụ đang chạy. Xem [hướng dẫn backup/restore](PRODUCTION_RUNBOOK.md).

## 1. Địa chỉ và tài khoản

| Kênh | Địa chỉ |
| --- | --- |
| Web | http://127.0.0.1:3100 trong repo này (`.env` đặt `FRONTEND_HOST_PORT=3100`); xác nhận bằng `docker compose port web 3000` |
| API | http://127.0.0.1:4010/api/v1 |
| Mailpit (nếu bật mail) | http://127.0.0.1:8025 mặc định; `MAILPIT_UI_HOST_PORT` có thể đổi cổng |

| Vai trò | Email | Mật khẩu |
| --- | --- | --- |
| Sinh viên | `student@campuscore.edu` | `password123` |
| Sinh viên demo (bảng điểm lịch sử) | `student101..student104@campuscore.demo` | `password123` |
| Giảng viên | `lecturer@campuscore.edu` | `password123` |
| Giảng viên demo | `lecturer002..lecturer012@campuscore.demo` | `password123` |
| Giảng viên mới (chứng minh phân công) | `lecturer003@campuscore.demo` | `password123` |
| Quản trị | `admin@campuscore.edu` | `admin123` |
| Quản trị thứ hai (duyệt tri thức 4 mắt) | `admin002@campuscore.demo` | `admin123` |

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
7. **Admin** (30s): đăng nhập admin, tổng quan 230/12/100/3.376.

## 3. Xử lý sự cố

| Triệu chứng | Xử lý |
| --- | --- |
| Web trả lỗi | `docker compose port web 3000` để xác định đúng cổng; `docker compose ps`; nếu web exit: `docker compose up -d web` |
| Chatbot chậm/không trả lời | Xem `DEEPSEEK_ENABLED` trong `.env`: nếu TẮT, fallback lexical chạy cục bộ — nói rằng "chatbot không phụ thuộc internet"; nếu BẬT, chatbot cần mạng và key hợp lệ. Kiểm tra `docker compose logs rag-service` |
| Quên mật khẩu tài khoản demo | Tất cả tài khoản seed dùng `password123` / `admin123` (xem bảng trên) |
| CSDL có trạng thái khác seed | Kiểm tra dữ liệu đã nhập và phiên bản Flyway trước; backup dữ liệu cần giữ. Chỉ reset theo mục riêng ở trên khi xác nhận đúng stack demo |
| Cần khôi phục dữ liệu | Dùng backup đã xác minh theo runbook backup/restore; không giả định có sẵn bản backup hoặc tên container trên máy khác |

## 4. Điểm nhấn kỹ thuật nên kể (kèm bằng chứng)

- Đăng ký học phần chống race 4 lớp: row lock + conditional update + partial
  unique index + idempotency key — có test race 10 thread
  (`AcademicEnrollmentMutationPersistenceTest`).
- Quota trợ lý atomic (`FOR UPDATE` theo thứ tự), publish kiến thức phải admin
  thứ hai duyệt, injection guard song ngữ Anh–Việt.
- Secrets prod fail-closed qua file secret; so sánh secret timing-safe ở 3 vị trí.
- 220 test Java + 71 test frontend, typecheck + lint zero-warning.
- Luồng luận văn có "hàng rào" thời gian: đợt mở đăng ký nhưng hết cửa sổ là
  chặn (`REGISTRATION_WINDOW_CLOSED`); nhóm đã duyệt đóng cứng thành viên;
  đề tài nháp chỉ chủ sở hữu và admin nhìn thấy.
