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

---

## 4. Tối Ưu Hóa Nhận Diện Thương Hiệu & SEO

- **Logo chính thức**: Huy hiệu CampusUTE mang chuẩn phong cách HCMUTE (Xanh Hoàng gia `#002D62` kết hợp Vàng cam `#F59E0B`).
- **SEO & Social Preview**:
  - Schema.org JSON-LD: `CollegeOrUniversity` (HCMUTE), `WebApplication`, `WebSite`.
  - OpenGraph / Twitter Banner: Tự động render 1200x630 chuẩn nhận diện tại `/opengraph-image`.
  - Sitemap: Tự động cập nhật tại `/sitemap.xml`.
  - Robots: `/robots.txt` cho phép các trang công khai và bảo vệ các khu vực nội bộ.
