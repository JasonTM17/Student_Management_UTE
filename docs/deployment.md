# Hướng Dẫn & Nhật Ký Triển Khai (Deployment Guide)

## 1. Thông Tin Nền Tảng & URL Chính Thức

| Thành phần | Nền tảng | Domain sản xuất | Trạng thái |
|---|---|---|---|
| **Frontend Web** | Vercel | `https://campusute.io.vn` / `https://www.campusute.io.vn` | **LIVE (Ready)** |
| **Backend REST API** | Render | `https://campuscore-backend-p4em.onrender.com` | **LIVE (Ready)** |
| **Database** | Supabase PostgreSQL | `https://kbptwmwitojjjwvwckom.supabase.co` | **HEALTHY** |
| **Mã nguồn Git** | GitHub | `https://github.com/JasonTM17/Student_Management_UTE` | **UP-TO-DATE** |

---

## 2. Quy Trình Triển Khai Frontend (Vercel)

- **Project Name**: `campusute`
- **Lệnh triển khai Production**:
  ```bash
  cd frontend
  npx vercel --prod --yes
  ```
- **Biến môi trường thiết yếu**:
  - `NEXT_PUBLIC_SITE_URL`: `https://campusute.io.vn`
  - `BACKEND_INTERNAL_ORIGIN`: `https://campuscore-backend-p4em.onrender.com`
- **Tùy chỉnh Project Avatar trên Vercel**:
  - Truy cập: Vercel Dashboard -> `campusute` -> Settings -> General -> Project Avatar.
  - Tải lên file: `frontend/public/icon.png` (512x512 Logo CampusUTE).

---

## 3. Quy Trình Triển Khai Backend (Render)

- **Service Name**: `campuscore-backend`
- **Cơ chế**: Tự động build và deploy khi push code lên nhánh `main` của repository `JasonTM17/Student_Management_UTE`.
- **Health Check Endpoint**:
  ```http
  GET https://campuscore-backend-p4em.onrender.com/api/v1/health/liveness
  ```
  Phản hồi chuẩn: `{"status": "ok", "service": "restful-api"}`

### 3.1. Cấu hình Chatbot AI & Định Tuyến Phân Tầng (Tiered Difficulty Routing)

Hệ thống trợ lý học vụ AI (`ThesisAssistantService`) áp dụng kiến trúc định tuyến thông minh 2 tầng:

1. **Câu hỏi thông thường & Tra cứu quy chế (RAG Bot nội bộ)**:
   - Các câu hỏi sự kiện, điều kiện đồ án, học phí, biểu mẫu, tra cứu trực tiếp được giải đáp 100% bởi **RAG bot do chúng ta huấn luyện** (`curated-lexical-rag`) từ bảng `assistant.knowledge_release`.
   - Phản hồi siêu tốc, đính kèm chính xác trích dẫn văn bản/quy định UTE, tuyệt đối không tiêu tốn token hay quota gọi ra ngoài.

2. **Câu hỏi quá khó / Phân tích tổng hợp (Escalation DeepSeek V4 Flash)**:
   - `AssistantDifficultyRouter` tự động kiểm tra và nhận diện câu hỏi khó khi:
     - Câu hỏi dài (> 180 ký tự hoặc >= 12 từ logic).
     - Chứa từ khóa phức tạp / lập luận: `"so sánh"`, `"khác nhau"`, `"tại sao"`, `"vì sao"`, `"như thế nào"`, `"làm thế nào"`, `"hướng dẫn"`, `"các bước"`, `"ngoại lệ"`, `"trường hợp"`, `"nếu"`, `"compare"`, `"why"`, `"how"`, `"steps"`,...
     - Dữ liệu liên quan trải rộng trên $\ge$ 3 tài liệu hoặc $\ge$ 2 phân hệ tri thức độc lập.
   - Khi đó, hệ thống sẽ tự động gọi mô hình `deepseek-v4-flash`, nạp context trích xuất từ tài liệu học vụ nội bộ để mô hình tổng hợp phân tích đa chiều cho sinh viên.
   - **Cơ chế an toàn (Graceful Fallback)**: Khi chưa bật DeepSeek hoặc API Key hết hạn, hệ thống tự động fallback về câu trả lời RAG nội bộ (`degraded: true`), không bao giờ để xảy ra gián đoạn dịch vụ.

3. **Hướng dẫn thêm DeepSeek API Key vào Render Dashboard**:
   - **Bước 1**: Đăng nhập vào [Render Dashboard](https://dashboard.render.com).
   - **Bước 2**: Chọn Web Service `campuscore-backend`.
   - **Bước 3**: Chọn tab **Environment** ở cột điều hướng bên trái.
   - **Bước 4**: Thêm hoặc cập nhật các biến môi trường:
     - `DEEPSEEK_ENABLED`: `true`
     - `DEEPSEEK_API_KEY`: `<API Key bắt đầu bằng sk-...>` (Lấy từ [DeepSeek Platform](https://platform.deepseek.com))
     - `DEEPSEEK_MODEL`: `deepseek-v4-flash` *(Đã đặt mặc định chuẩn)*
     - `DEEPSEEK_BASE_URL`: `https://api.deepseek.com` *(Đã đặt mặc định chuẩn)*
   - **Bước 5**: Nhấn **Save Changes**. Render sẽ tự động redeploy phiên bản mới có kích hoạt kết nối DeepSeek.

---

## 4. Tối Ưu Hóa Nhận Diện Thương Hiệu & SEO

- **Logo chính thức**: Huy hiệu CampusUTE mang chuẩn phong cách HCMUTE (Xanh Hoàng gia `#002D62` kết hợp Vàng cam `#F59E0B`).
- **SEO & Social Preview**:
  - Schema.org JSON-LD: `CollegeOrUniversity` (HCMUTE), `WebApplication`, `WebSite`.
  - OpenGraph / Twitter Banner: Tự động render 1200x630 chuẩn nhận diện tại `/opengraph-image`.
  - Sitemap: Tự động cập nhật tại `/sitemap.xml`.
  - Robots: `/robots.txt` cho phép các trang công khai và bảo vệ các khu vực nội bộ.
