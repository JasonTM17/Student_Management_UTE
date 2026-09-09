# CampusCore — Cổng Thông Tin Đào Tạo & Quản Lý Học Vụ Đại Học Song Ngữ

> **Không gian học vụ số chuẩn mực cho định danh, đào tạo tín chỉ, quản lý điểm, khóa luận tốt nghiệp và trợ lý AI thông minh.**
> Kiến trúc hướng dịch vụ: một Spring Boot Java REST API trung tâm, một Next.js Web Portal, một PostgreSQL quản lý bằng Flyway và một RAG Sidecar riêng tư chỉ chạy trong mạng nội bộ.

[![Java 21](https://img.shields.io/badge/Java-21-orange.svg?style=flat-square&logo=openjdk)](https://openjdk.org/)
[![Spring Boot 3.5](https://img.shields.io/badge/Spring%20Boot-3.5-brightgreen.svg?style=flat-square&logo=springboot)](https://spring.io/projects/spring-boot)
[![Next.js 15](https://img.shields.io/badge/Next.js-15-black.svg?style=flat-square&logo=next.js)](https://nextjs.org/)
[![PostgreSQL 15](https://img.shields.io/badge/PostgreSQL-15-blue.svg?style=flat-square&logo=postgresql)](https://www.postgresql.org/)
[![Flyway](https://img.shields.io/badge/Flyway-Migrations-red.svg?style=flat-square)](https://flywaydb.org/)
[![Docker Compose](https://img.shields.io/badge/Docker-Compose%20Ready-2496ED.svg?style=flat-square&logo=docker)](https://www.docker.com/)

---

## Mục lục

1. [Tổng quan dự án](#tổng-quan-dự-án)
2. [Ảnh giao diện thực tế và Video GIF trải nghiệm](#ảnh-giao-diện-thực-tế-và-video-gif-trải-nghiệm)
   - [Trang chủ & Cổng đăng nhập phân luồng](#1-trang-chủ--cổng-đăng-nhập-phân-luồng)
   - [Hành trình Sinh viên (Student Role)](#2-hành-trình-sinh-viên-student-role)
   - [Hành trình Giảng viên (Lecturer Role)](#3-hành-trình-giảng-viên-lecturer-role)
   - [Hành trình Quản trị viên (Admin Role)](#4-hành-trình-quản-trị-viên-admin-role)
   - [GIFs minh họa luồng hoạt động](#5-gifs-minh-họa-luồng-hoạt-động)
3. [Điểm nhấn kỹ thuật cốt lõi](#điểm-nhấn-kỹ-thuật-cốt-lõi)
   - [Cơ chế chống Race Condition đăng ký học phần 4 lớp](#1-cơ-chế-chống-race-condition-đăng-ký-học-phần-4-lớp)
   - [Trợ lý AI CampusCore RAG song ngữ & Quota Atomic](#2-trợ-lý-ai-campuscore-rag-song-ngữ--quota-atomic)
   - [Quy trình kiểm duyệt tri thức 2 Quản trị viên](#3-quy-trình-kiểm-duyệt-tri-thức-2-quản-trị-viên-two-admin-governance)
   - [Cơ chế bảo mật Fail-Closed & Timing-Safe Secrets](#4-cơ-chế-bảo-mật-fail-closed--timing-safe-secrets)
4. [Kiến trúc hệ thống & Sơ đồ kỹ thuật](#kiến-trúc-hệ-thống--sơ-đồ-kỹ-thuật)
   - [Sơ đồ kiến trúc tổng thể (System Architecture)](#sơ-đồ-kiến-trúc-tổng-thể)
   - [Sơ đồ thực thể quan hệ cơ sở dữ liệu (Database ERD)](#sơ-đồ-thực-thể-quan-hệ-cơ-sở-dữ-liệu-erd)
   - [Sơ đồ tuần tự đăng ký học phần chống Race Condition](#sơ-đồ-tuần-tự-đăng-ký-học-phần)
   - [Sơ đồ luồng xử lý câu hỏi của Trợ lý AI (RAG Pipeline)](#sơ-đồ-luồng-xử-lý-của-trợ-lý-ai)
   - [Sơ đồ máy trạng thái kiểm duyệt tri thức (2-Admin Workflow)](#sơ-đồ-máy-trạng-thái-kiểm-duyệt-tri-thức)
   - [Ma trận phân quyền vai trò (Role-Based Access Control)](#ma-trận-phân-quyền-vai-trò)
5. [Ngăn xếp công nghệ (Technology Stack)](#ngăn-xếp-công-nghệ)
6. [Hướng dẫn cài đặt & Chạy ứng dụng](#hướng-dẫn-cài-đặt--chạy-ứng-dụng)
7. [Tài khoản demo kiểm thử](#tài-khoản-demo-kiểm-thử)
8. [Đặc tả RESTful API](#đặc-tả-restful-api)
9. [Kiểm thử và đảm bảo chất lượng](#kiểm-thử-và-đảm-bảo-chất-lượng)
10. [Tài liệu tham chiếu liên quan](#tài-liệu-tham-chiếu-liên-quan)

---

## Tổng quan dự án

**CampusCore** là hệ thống quản lý học vụ đại học cấp độ doanh nghiệp (Enterprise Academic Information System) đáp ứng đồng thời nhu cầu của **Sinh viên**, **Giảng viên** và **Cán bộ Quản trị đào tạo**.

Dự án lấy cảm hứng từ nhận diện và quy trình đào tạo tín chỉ của **Trường Đại học Sư phạm Kỹ thuật TP.HCM (HCMUTE)**, mang đến một không gian học vụ song ngữ (Tiếng Việt & Tiếng Anh) hiện đại, đồng thời giải quyết các thách thức kỹ thuật phức tạp trong môi trường học đường: **tắc nghẽn đăng ký học phần giờ cao điểm**, **an toàn dữ liệu điểm thi**, **quản lý vòng đời khóa luận tốt nghiệp** và **hỗ trợ học vụ tự động thông minh bằng mô hình Retrieval-Augmented Generation (RAG)**.

```
Next.js Web Portal (:3000) ────┐
                               ├───► Spring Boot REST API (:4010) ───► PostgreSQL (:5432)
Expo Mobile App (Thử nghiệm) ──┘         │ (mạng nội bộ /internal/rag)
                                         ▼
                                  rag-service Sidecar (:4011) ───► (Tùy chọn) DeepSeek API
```

> Cổng ở sơ đồ trên là **giá trị mặc định của Docker Compose**. Toàn bộ cổng host đều ghi đè được qua biến môi trường trong `.env` (xem [Hướng dẫn cài đặt](#hướng-dẫn-cài-đặt--chạy-ứng-dụng)).

---

## Ảnh giao diện thực tế và Video GIF trải nghiệm

Toàn bộ hình ảnh và ảnh động dưới đây được chụp và ghi hình trực tiếp từ hệ thống đang chạy trên môi trường local.

### 1. Trang chủ & Cổng đăng nhập phân luồng

| Trang chủ tiếng Việt (Default) | Trang chủ tiếng Anh (English) |
| --- | --- |
| ![Trang chủ tiếng Việt](docs/assets/screenshots/campuscore-home-vi-live.png) | ![Trang chủ tiếng Anh](docs/assets/screenshots/campuscore-home-en-live.png) |

| Cổng đăng nhập 3 Portal (Sinh viên / Giảng viên / Quản trị) | Giao diện đăng ký tài khoản sinh viên |
| --- | --- |
| ![Cổng đăng nhập phân luồng](docs/assets/screenshots/campuscore-login-portals.png) | ![Đăng ký tài khoản](docs/assets/screenshots/campuscore-register-live.png) |

---

### 2. Hành trình Sinh viên (Student Role)

Tài khoản demo: `student@campuscore.edu` | Mật khẩu: `password123`

| Bảng điều khiển sinh viên (Dashboard) | Đăng ký học phần (Course Registration) |
| --- | --- |
| ![Student Dashboard](docs/assets/screenshots/student-01-dashboard.png) | ![Student Registration](docs/assets/screenshots/student-02-registration.png) |
| *Xem tiến độ học tập, lịch học hôm nay và thông báo mới* | *Bộ lọc khoa/ngành, kiểm tra sức chứa và đăng ký chống race* |

| Học phần đã đăng ký (My Courses) | Thời khóa biểu tuần (Weekly Schedule) |
| --- | --- |
| ![Student My Courses](docs/assets/screenshots/student-03-my-courses.png) | ![Student Schedule](docs/assets/screenshots/student-04-schedule.png) |
| *Quản lý môn đã đăng ký, số tín chỉ và thao tác rút môn* | *Thời khóa biểu trực quan theo lưới thứ, tiết và phòng học* |

| Kết quả học tập & Điểm số (Grades) | Bảng điểm tích lũy toàn khóa (Transcript) |
| --- | --- |
| ![Student Grades](docs/assets/screenshots/student-05-grades.png) | ![Student Transcript](docs/assets/screenshots/student-06-transcript.png) |
| *Điểm quá trình, điểm thi kết thúc, điểm tổng kết và điểm chữ* | *Bảng điểm tích lũy học tập toàn diện & biểu đồ GPA hệ 4 / 10* |

| Trợ lý học vụ AI (CampusCore Assistant) | Quản lý Khóa luận tốt nghiệp (Thesis Portal) |
| --- | --- |
| ![Student Assistant Chat](docs/assets/screenshots/student-07-assistant-chat.png) | ![Student Thesis](docs/assets/screenshots/student-08-thesis.png) |
| *Trợ lý RAG giải đáp quy chế, lịch thi kèm trích dẫn tài liệu thật* | *Theo dõi đợt bảo vệ, đăng ký đề tài và thành lập nhóm* |

---

### 3. Hành trình Giảng viên (Lecturer Role)

Tài khoản demo: `lecturer@campuscore.edu` | Mật khẩu: `password123`

| Bảng điều khiển giảng viên (Lecturer Dashboard) | Lịch giảng dạy trong tuần (Teaching Schedule) |
| --- | --- |
| ![Lecturer Dashboard](docs/assets/screenshots/lecturer-01-dashboard.png) | ![Lecturer Schedule](docs/assets/screenshots/lecturer-02-schedule.png) |
| *Thống kê khối lượng giảng dạy, lớp học phần và sĩ số* | *Thời khóa biểu các lớp được phân công kèm thông tin phòng học* |

| Quản lý danh sách lớp & Điểm (Grades Management) | Giao diện nhập điểm học phần (Grade Entry) |
| --- | --- |
| ![Lecturer Grades](docs/assets/screenshots/lecturer-03-grades-management.png) | ![Lecturer Grade Entry](docs/assets/screenshots/lecturer-04-grade-entry.png) |
| *Theo dõi tiến độ nộp điểm và trạng thái khóa sổ* | *Bảng tính nhập điểm quá trình, điểm thi và lưu an toàn* |

---

### 4. Hành trình Quản trị viên (Admin Role)

Tài khoản demo: `admin@campuscore.edu` | Mật khẩu: `admin123`

| Bảng điều khiển quản trị (Admin Dashboard) | Quản lý người dùng & Phân quyền (Users Management) |
| --- | --- |
| ![Admin Dashboard](docs/assets/screenshots/admin-01-dashboard.png) | ![Admin Users](docs/assets/screenshots/admin-02-users.png) |
| *Chỉ số vận hành hệ thống, tình trạng server và phím tắt quản lý* | *Quản trị tài khoản, cấp quyền Sinh viên / Giảng viên / Admin* |

| Quản lý Lớp học phần (Section Management) | Quản trị tri thức RAG 2 người (Assistant Knowledge) |
| --- | --- |
| ![Admin Sections](docs/assets/screenshots/admin-03-sections.png) | ![Admin Assistant Knowledge](docs/assets/screenshots/admin-05-assistant-knowledge.png) |
| *Mở lớp, thiết lập sĩ số tối đa, phân công giảng viên và phòng* | *Quy trình duyệt bản nháp 2 Admin trước khi xuất bản ra RAG* |

---

### 5. GIFs minh họa luồng hoạt động

#### A. Trải nghiệm toàn diện của Sinh viên (Student Tour)
Hành trình từ lúc Đăng nhập sinh viên → Xem Dashboard → Đăng ký học phần tín chỉ → Xem Thời khóa biểu tuần → Kiểm tra Bảng điểm tích lũy → Tương tác trực tiếp với Trợ lý AI CampusCore:
![CampusCore Student Tour](docs/assets/campuscore-student-tour.gif)

#### B. Trải nghiệm Giảng viên & Quản trị viên (Lecturer & Admin Tour)
Hành trình Giảng viên quản lý lịch dạy, vào điểm học phần → Chuyển sang Quản trị viên điều hành người dùng, mở lớp học phần và kiểm duyệt xuất bản tri thức RAG 2 người:
![CampusCore Lecturer and Admin Tour](docs/assets/campuscore-lecturer-admin-tour.gif)

#### C. Chuyển đổi ngôn ngữ song ngữ tức thời (Bilingual Language Switch)
Hệ thống hỗ trợ chuyển đổi mượt mà giữa Tiếng Việt và Tiếng Anh trên toàn bộ giao diện:
![CampusCore Language Tour](docs/assets/campuscore-language-tour-live.gif)

---

## Điểm nhấn kỹ thuật cốt lõi

### 1. Cơ chế chống Race Condition đăng ký học phần 4 lớp

Hiện tượng "overselling" (ghi nhận số lượng sinh viên đăng ký vượt quá sức chứa phòng học) là sự cố kinh điển trong các đợt đăng ký tín chỉ khi hàng ngàn sinh viên bấm nút đồng thời. CampusCore giải quyết bằng mô hình phòng thủ theo chiều sâu **4 lớp độc lập**:

```
[Request từ Client]
         │ (Idempotency Key duy nhất: UUIDv4)
         ▼
 ┌──────────────────────────────────────────────────────────────┐
 │ Lớp 4: Idempotency Guard (Chống lặp lệnh mạng / Double Click) │
 └──────────────────────────────┬───────────────────────────────┘
                                │
                                ▼
 ┌──────────────────────────────────────────────────────────────┐
 │ Lớp 1: Row Lock PBI `SELECT ... FOR UPDATE` trên Section      │
 └──────────────────────────────┬───────────────────────────────┘
                                │ (Tuần tự hóa luồng trong DB)
                                ▼
 ┌──────────────────────────────────────────────────────────────┐
 │ Lớp 2: Conditional Update `enrolledCount < capacity`         │
 └──────────────────────────────┬───────────────────────────────┘
                                │ (Đảm bảo số đếm không bao giờ vượt ngưỡng)
                                ▼
 ┌──────────────────────────────────────────────────────────────┐
 │ Lớp 3: Partial Unique Index trên PostgreSQL                  │
 │ CREATE UNIQUE INDEX ... WHERE status IN (...)                │
 └──────────────────────────────┬───────────────────────────────┘
                                │ (Chặn tuyệt đối 1 sinh viên có 2 bản ghi cùng lớp)
                                ▼
                      [Giao dịch hoàn tất]
```

- **Lớp 1 (Pessimistic Row Lock)**: Khi bắt đầu giao dịch, câu lệnh `SELECT * FROM academic."Section" WHERE "id" = :id FOR UPDATE` lập tức khóa bản ghi của lớp học phần, buộc các luồng khác cùng truy cập lớp này phải chờ, ngăn chặn hiện tượng Dirty Read.
- **Lớp 2 (Atomic Conditional Update)**: Số lượng sinh viên được tăng bằng câu lệnh `UPDATE academic."Section" SET "enrolledCount" = "enrolledCount" + 1 WHERE "id" = :id AND "enrolledCount" < "capacity";`. Nếu lớp đã đầy, câu lệnh tác động 0 dòng và giao dịch lập tức Rollback.
- **Lớp 3 (Partial Unique Index)**: Chỉ mục riêng phần `academic_enrollment_active_student_section_uq` trên `academic."Enrollment" ("studentId", "sectionId") WHERE "status" IN ('ENROLLED', 'PENDING', 'CONFIRMED')` chặn tuyệt đối trường hợp một sinh viên có hai bản ghi đang hiệu lực trong cùng một lớp, kể cả khi cố tình gửi nhiều request song song.
- **Lớp 4 (Idempotency Key)**: Web Portal luôn gửi kèm header `Idempotency-Key` (UUIDv4) cho từng thao tác đăng ký/rút môn; hệ thống lưu vết khóa để nhận diện và bỏ qua gói tin gửi lại do retry mạng.
- **Chứng minh kiểm thử**: `AcademicEnrollmentMutationPersistenceTest` kích hoạt 10 thread đồng thời tranh chấp vị trí cuối cùng của lớp học phần (xem [Kiểm thử](#kiểm-thử-và-đảm-bảo-chất-lượng) để tự chạy lại).

---

### 2. Trợ lý AI CampusCore RAG song ngữ & Quota Atomic

- **Kiến trúc RAG bảo mật**: Trợ lý AI được đóng gói trong một sidecar riêng biệt (`rag-service`, cổng nội bộ 4011, chỉ `expose` trong mạng Docker Compose, không publish ra host). Không có bất kỳ endpoint AI công khai nào ra bên ngoài internet.
- **Bộ lọc Prompt-Injection song ngữ (`AssistantInputGuard`)**: Tự động rà quét và chặn đứng các mẫu câu tấn công bẻ khóa (jailbreak), ghi đè hướng dẫn hệ thống bằng cả tiếng Anh và tiếng Việt (ví dụ: *"ignore previous instructions"*, *"bỏ qua hướng dẫn trước đó"*).
- **Hạn ngạch Atomic Quota (20 yêu cầu/người/ngày)**: Sử dụng kỹ thuật Compare-And-Swap (CAS) nguyên tử trên cơ sở dữ liệu, ngăn việc mở nhiều tab gửi request đồng thời để vượt hạn ngạch.
- **Truy hồi Lexical Kết hợp LLM Fallback**:
  - Mặc định: Tra cứu từ vựng xếp hạng BM25 / Full-text search trên bảng snapshot của PostgreSQL. Trả lời nhanh kèm số hiệu trích dẫn văn bản quy chế chính xác `[1]`, `[2]`.
  - Nâng cao (Tùy chọn): Nếu câu hỏi mang tính suy luận phức tạp và cờ `DEEPSEEK_ENABLED=true`, sidecar sẽ gửi ngữ cảnh đã trích xuất đến model `deepseek-v4-flash` để tổng hợp câu trả lời tự nhiên. Nếu thiếu key hoặc nhà cung cấp timeout, hệ thống tự động fallback an toàn về Lexical mà không gây gián đoạn cho người dùng.

---

### 3. Quy trình kiểm duyệt tri thức 2 Quản trị viên (Two-Admin Governance)

Để đảm bảo các quy chế đào tạo, học phí, lịch thi do Trợ lý AI cung cấp có độ tin cậy cao, CampusCore triển khai cơ chế kiểm duyệt **Four-Eyes Principle**:
1. **Admin 1** soạn thảo văn bản quy chế mới hoặc sửa đổi nội dung hiện có: bản ghi ở trạng thái `DRAFT` hoặc `PENDING_REVIEW`.
2. **Admin 2** đăng nhập vào Cổng Quản trị, đọc bản nháp và phê duyệt `PUBLISH`:
   - Truy vấn xuất bản chỉ nhận bản nháp do admin khác tạo (`created_by <> actor`). Nếu bản nháp đang chờ duyệt là của chính mình, API trả về lỗi `409 Conflict` với mã `KNOWLEDGE_SECOND_REVIEW_REQUIRED` ("A different admin must publish this revision").
   - Lúc xuất bản, hệ thống kích hoạt **Privacy Gate** tự động rà quét để loại bỏ dữ liệu nhạy cảm (email cá nhân, số điện thoại, mật khẩu, nội dung nội bộ).
   - Bản ghi được đẩy nguyên tử (Atomic Promotion) vào Snapshot đang hoạt động của RAG.

---

### 4. Cơ chế bảo mật Fail-Closed & Timing-Safe Secrets

- **Bảo mật Fail-Closed & Secrets an toàn**: Các cấu hình bảo mật quan trọng (JWT secret, refresh secret, health check key) được kiểm tra khi khởi động; thiếu cấu hình bắt buộc thì hệ thống từ chối khởi động (fail-closed) thay vì chạy ở trạng thái mặc định không an toàn. Giá trị mẫu trong `.env.example` chỉ dùng cho máy local và cần thay trước khi triển khai thật.
- **So sánh thời gian an toàn (Timing-Safe Comparison)**: Xác thực `X-Health-Key`, CSRF Token và RAG Service Bearer Token bằng hàm `MessageDigest.isEqual()` để chống tấn công Timing Attack.
- **Phân tách Cookie & Token**:
  - Web Client: HTTP-only, SameSite=Strict Cookies kèm CSRF Protection để chống XSS và CSRF.
  - Mobile Client: chuẩn Bearer Access Token & Refresh Token trong Authorization Header.

---

## Kiến trúc hệ thống & Sơ đồ kỹ thuật

### Sơ đồ kiến trúc tổng thể

```mermaid
flowchart TD
    subgraph CLIENTS["TẦNG GIAO DIỆN & TRUY CẬP"]
        Browser["Trình duyệt người dùng\n(Desktop & Mobile Responsive)"]
        Web["Next.js 15 Web Portal\n(:3000 | Cookie HttpOnly + CSRF)"]
        Mobile["Expo React Native App\n(Thử nghiệm | Bearer JWT)"]
    end

    subgraph BACKEND["TẦNG DỊCH VỤ JAVA (APPLICATION SERVICES)"]
        REST["Spring Boot 3.5 REST API\n(:4010 | /api/v1/)\n• Auth & RBAC Security Filter\n• Registration Engine chống race 4 lớp\n• Gradebook & Transcript Service\n• Thesis Lifecycle Manager\n• Assistant Gateway & Quota Guard"]
        RAG["Private rag-service Sidecar\n(:4011 | chỉ expose nội bộ)\n• Prompt-Injection Guard song ngữ\n• Lexical ranking BM25\n• Snapshot runtime reader\n• Thực thi Flyway migration trong cụm compose"]
    end

    subgraph DATA["TẦNG CƠ SỞ DỮ LIỆU"]
        PG[("PostgreSQL 15\n(:5432 host mặc định | DB campuscore_restful)\nSchema: campuscore_auth · academic · thesis ·\nassistant · engagement · notifications")]
    end

    subgraph EXTERNAL["DỊCH VỤ NGOÀI & TIỆN ÍCH"]
        Mailpit["Mailpit Local SMTP\n(:8025 UI / :1025 SMTP)"]
        DeepSeek["DeepSeek API\ndeepseek-v4-flash (Tùy chọn)"]
    end

    Browser --> Web
    Web -->|"proxy cùng nguồn /api/v1"| REST
    Mobile -->|"RESTFUL/EXPO_PUBLIC_API_URL"| REST

    REST --> PG
    REST -->|"Bearer token nội bộ"| RAG
    RAG --> PG
    RAG -.->|"DEEPSEEK_ENABLED=true"| DeepSeek
    REST -.->|"MAIL_ENABLED=true"| Mailpit
```

> Trong cụm Docker Compose, container `rag-service` là bên thực thi Flyway migration (`restful-api` chạy với `FLYWAY_ENABLED=false` để tránh hai tiến trình đụng độ). Khi chạy JAR trực tiếp ngoài compose, Flyway mặc định bật theo `application.yml`. Danh mục migration: `java-services/restful-api/src/main/resources/db/migration/`.

---

### Sơ đồ thực thể quan hệ cơ sở dữ liệu (ERD)

```mermaid
erDiagram
    USER ||--o{ REFRESH_TOKEN : "sở hữu"
    USER ||--o{ AUDIT_LOG : "ghi vết"
    USER ||--|| STUDENT : "liên kết 1-1"
    USER ||--|| LECTURER : "liên kết 1-1"

    DEPARTMENT ||--o{ MAJOR : "quản lý"
    DEPARTMENT ||--o{ LECTURER : "thuộc khoa"
    MAJOR ||--o{ STUDENT : "chuyên ngành"

    COURSE ||--o{ SECTION : "mở lớp"
    SEMESTER ||--o{ SECTION : "thuộc học kỳ"
    CLASSROOM ||--o{ SECTION : "xếp phòng"
    LECTURER ||--o{ SECTION : "giảng dạy"

    STUDENT ||--o{ ENROLLMENT : "đăng ký"
    SECTION ||--o{ ENROLLMENT : "tiếp nhận"
    ENROLLMENT ||--|| GRADE : "đánh giá kết quả"

    THESIS_ROUND ||--o{ THESIS_TOPIC : "công bố"
    LECTURER ||--o{ THESIS_TOPIC : "hướng dẫn"
    THESIS_TOPIC ||--o{ THESIS_GROUP : "phân công"
    STUDENT ||--o{ THESIS_GROUP_MEMBER : "tham gia"
    THESIS_GROUP ||--o{ THESIS_GROUP_MEMBER : "thành viên"

    KNOWLEDGE_REVISION ||--|| KNOWLEDGE_SNAPSHOT : "xuất bản thành"
    USER ||--o{ KNOWLEDGE_REVISION : "tác giả / người duyệt"

    ANNOUNCEMENT ||--o{ NOTIFICATION : "phát sinh"
    USER ||--o{ NOTIFICATION : "nhận thông báo"
```

---

### Sơ đồ tuần tự đăng ký học phần

```mermaid
sequenceDiagram
    autonumber
    actor SinhVien as Sinh viên (Browser)
    participant Web as Next.js Web Portal
    participant API as Spring Boot REST API
    participant DB as PostgreSQL (ACID)

    SinhVien->>Web: Nhấn "Đăng ký" lớp học phần
    Web->>Web: Sinh Idempotency-Key (UUIDv4)
    Web->>API: POST /api/v1/me/enrollments (Section ID, Key)

    activate API
    API->>API: Kiểm tra Header & Phân quyền Role (STUDENT)
    API->>DB: BEGIN TRANSACTION (Isolation: Read Committed)

    API->>DB: Lớp 1: SELECT * FROM section WHERE id = :id FOR UPDATE
    Note over DB: Khóa dòng Section (Pessimistic Row Lock), các request khác phải chờ

    API->>DB: Lớp 2: UPDATE section SET "enrolledCount" = "enrolledCount" + 1 WHERE "id" = :id AND "enrolledCount" < "capacity"
    alt Lớp đầy (enrolled >= capacity)
        DB-->>API: 0 dòng bị ảnh hưởng
        API->>DB: ROLLBACK
        API-->>Web: HTTP 409 Conflict (SECTION_FULL)
        Web-->>SinhVien: Thông báo: Lớp đã đủ sĩ số!
    else Còn chỗ trống (enrolled < capacity)
        DB-->>API: 1 dòng cập nhật thành công
        API->>DB: Lớp 3: INSERT INTO enrollment (studentId, sectionId, status) VALUES (..., 'ENROLLED')
        alt Sinh viên đã đăng ký trước đó
            DB-->>API: Vi phạm Unique Constraint (academic_enrollment_active_student_section_uq)
            API->>DB: ROLLBACK
            API-->>Web: HTTP 409 Conflict (ALREADY_ENROLLED)
            Web-->>SinhVien: Thông báo: Bạn đã đăng ký môn này rồi!
        else Đăng ký hợp lệ
            DB-->>API: Insert thành công
            API->>DB: COMMIT TRANSACTION
            API-->>Web: HTTP 201 Created (Enrollment)
            Web-->>SinhVien: Cập nhật TKB và hiện thông báo thành công!
        end
    end
    deactivate API
```

---

### Sơ đồ luồng xử lý của Trợ lý AI

```mermaid
sequenceDiagram
    autonumber
    actor User as Sinh viên / Giảng viên
    participant Web as Web Chat Drawer
    participant REST as REST API Gateway (:4010)
    participant Sidecar as rag-service Sidecar (:4011)
    participant PG as PostgreSQL Snapshot
    participant DeepSeek as DeepSeek API (External)

    User->>Web: Gửi câu hỏi: "Thời hạn đóng học phí HK2 là khi nào?"
    Web->>REST: POST /api/v1/assistant/chat (Cookie Session)

    activate REST
    REST->>REST: Kiểm tra JWT Token & Quyền truy cập
    REST->>REST: AssistantInputGuard: Quét Prompt-Injection song ngữ (VI/EN)

    alt Phát hiện câu lệnh tấn công (Jailbreak / Prompt Leak)
        REST-->>Web: HTTP 400 Bad Request (PROMPT_INJECTION_DETECTED)
    else Nội dung an toàn
        REST->>PG: CAS Quota Check (Tối đa 20 lượt/ngày)
        alt Đã sử dụng hết hạn ngạch
            REST-->>Web: HTTP 429 Too Many Requests (QUOTA_EXCEEDED)
        else Còn hạn ngạch
            REST->>Sidecar: POST /internal/rag/assistant (Question, Service Token)
            activate Sidecar
            Sidecar->>PG: Tìm kiếm Lexical BM25 trên active_snapshot
            PG-->>Sidecar: Trả về đoạn trích quy chế có độ tương quan cao

            alt Trích xuất đạt độ tin cậy cao
                Sidecar->>Sidecar: Định dạng câu trả lời kèm Trích dẫn [1], [2]
                Sidecar-->>REST: 200 OK (Answer + Citations)
            else Câu hỏi suy luận phức tạp & DEEPSEEK_ENABLED=true
                Sidecar->>DeepSeek: Gửi Context rút gọn + Câu hỏi (Model Flash)
                DeepSeek-->>Sidecar: Kết quả phản hồi
                Sidecar->>Sidecar: Kiểm tra chéo trích dẫn với snapshot
                Sidecar-->>REST: 200 OK (Synthesized Answer + Citations)
            end
            deactivate Sidecar

            REST-->>Web: Trả về kết quả hiển thị cho người dùng
        end
    end
    deactivate REST
```

---

### Sơ đồ máy trạng thái kiểm duyệt tri thức

```mermaid
stateDiagram-v2
    [*] --> DRAFT: Admin 1 tạo bản thảo kiến thức mới
    DRAFT --> PENDING_REVIEW: Admin 1 gửi yêu cầu duyệt

    state PENDING_REVIEW {
        [*] --> CheckingAuthority: Kiểm tra Admin phê duyệt
        CheckingAuthority --> RejectedSameAdmin: Người duyệt == Người tạo
        CheckingAuthority --> RunningPrivacyGate: Người duyệt != Người tạo
        RunningPrivacyGate --> GateFailed: Phát hiện dữ liệu PII / Bí mật
        RunningPrivacyGate --> GatePassed: Dữ liệu sạch & chuẩn mực
    }

    RejectedSameAdmin --> PENDING_REVIEW: Báo lỗi 409 KNOWLEDGE_SECOND_REVIEW_REQUIRED (Cần Admin khác)
    GateFailed --> DRAFT: Trả về sửa đổi bản thảo
    GatePassed --> PUBLISHED: Chấp thuận xuất bản

    PUBLISHED --> ATOMIC_PROMOTION: Cập nhật Snapshot cơ sở dữ liệu
    ATOMIC_PROMOTION --> LIVE_RAG: Trợ lý AI nạp kiến thức mới ngay lập tức
    LIVE_RAG --> [*]
```

---

### Ma trận phân quyền vai trò

```mermaid
flowchart LR
    subgraph ROLES["VAI TRÒ NGƯỜI DÙNG"]
        Guest["Khách vãng lai (GUEST)"]
        Student["Sinh viên (STUDENT)"]
        Lecturer["Giảng viên (LECTURER)"]
        Admin["Quản trị viên (ADMIN)"]
    end

    subgraph FEATURES["CÁC CHỨC NĂNG HỆ THỐNG"]
        F1["Trang chủ, Tra cứu môn học công khai, Đăng ký tài khoản"]
        F2["Đăng ký học phần (Anti-Race), Lịch học tuần, Xem điểm & Bảng điểm"]
        F3["Trợ lý AI CampusCore (Hỏi đáp quy chế đào tạo, trích dẫn)"]
        F4["Khu vực Khóa luận tốt nghiệp (Đăng ký đề tài, nhóm sinh viên)"]
        F5["Lịch giảng dạy, Nhập điểm sinh viên, Quản lý lớp phụ trách"]
        F6["Quản lý người dùng, Mở lớp học phần, Xếp phòng, Đăng thông báo"]
        F7["Kiểm duyệt tri thức RAG 2 người, Cấu hình diện mạo hệ thống"]
    end

    Guest --> F1
    Student --> F1 & F2 & F3 & F4
    Lecturer --> F1 & F3 & F4 & F5
    Admin --> F1 & F6 & F7
```

---

## Ngăn xếp công nghệ

| Phân tầng | Công nghệ chính | Phiên bản | Vai trò & Mục đích sử dụng |
| --- | --- | --- | --- |
| **Backend API** | Java / Spring Boot | 21 / 3.5.16 | RESTful API trung tâm, Spring Security, Validation, JPA/Hibernate |
| **Frontend Web** | Next.js (App Router) | 15.5 | Cổng thông tin tương tác, Server Components, proxy cùng nguồn `/api/v1` |
| **Thư viện UI** | Tailwind CSS, Radix UI, TinyMCE | 3.4 / Radix / 8 | Hệ thống thiết kế nhận diện HCMUTE, bảng biểu dữ liệu, soạn thảo rich-text |
| **Biên dịch & Gói** | Maven / Node.js | 3.9.12 / 20 | Build tool cho Java backend và Node runtime cho frontend |
| **Cơ sở dữ liệu** | PostgreSQL | 15 (postgres:15-alpine) | Giao dịch ACID, Partial Index, Full-text Search tsvector |
| **Quản lý Schema** | Flyway | kèm Spring Boot | Versioned migration trong `java-services/restful-api/src/main/resources/db/migration/` |
| **RAG Sidecar** | Spring Boot (chế độ sidecar) | 3.5.16 | Dịch vụ AI nội bộ cổng 4011, xếp hạng Lexical, Prompt-injection guard |
| **Mô hình AI** | DeepSeek | deepseek-v4-flash | LLM tùy chọn cho câu hỏi suy luận, tắt được hoàn toàn qua `DEEPSEEK_ENABLED` |
| **Kiểm thử Email** | Mailpit | v1.21 | SMTP server ảo giả lập gửi nhận email thông báo trên môi trường dev |
| **Kiểm thử & QA** | JUnit 5, Mockito, node:test, Playwright | Latest | Kiểm thử đơn vị backend, smoke test frontend, e2e Playwright |
| **Ảo hóa / Deploy** | Docker & Docker Compose | Compose v2 | Đóng gói 5 container: postgres, mailpit, rag-service, restful-api, web |

---

## Hướng dẫn cài đặt & Chạy ứng dụng

### Yêu cầu tiên quyết

- **Docker Desktop** (bản 24.0 trở lên) đã được cài đặt và đang chạy.
- Máy tính còn trống ít nhất 4 GB RAM và các cổng: `3000` (web), `4010` (API), `5432` (PostgreSQL), `8025` (Mailpit UI).

### Các bước khởi động hệ thống

```powershell
# 1. Clone mã nguồn đồ án
git clone https://github.com/JasonTM17/Student_Management_UTE.git
cd Student_Management_UTE

# 2. Tạo file môi trường local từ mẫu và chỉnh các secret trước khi chạy
Copy-Item .env.example .env

# 3. Khởi động toàn bộ cụm dịch vụ qua Docker Compose
docker compose up -d --build postgres mailpit rag-service restful-api web

# 4. Kiểm tra trạng thái hoạt động của các container
docker compose ps

# 5. Kiểm tra sức khỏe API qua liveness và readiness probe
#    Lưu ý: thay <HEALTH_READINESS_KEY> bằng đúng giá trị HEALTH_READINESS_KEY trong .env
curl.exe http://127.0.0.1:4010/api/v1/health/liveness
curl.exe -H "X-Health-Key: <HEALTH_READINESS_KEY>" http://127.0.0.1:4010/api/v1/health/readiness

# 6. Kiểm tra hợp đồng tài liệu OpenAPI
curl.exe http://127.0.0.1:4010/v3/api-docs
```

### Cổng dịch vụ & cấu hình ghi đè

Giá trị dưới đây là **mặc định của `docker-compose.yml`**. Mọi cổng host đều đổi được qua biến môi trường tương ứng trong `.env` (định nghĩa nằm trong `docker-compose.yml`, mẫu biến nằm trong `.env.example`):

| Dịch vụ | Cổng mặc định (host) | Biến ghi đè trong `.env` |
| --- | --- | --- |
| Web Portal (Next.js) | `3000` | `FRONTEND_HOST_PORT` |
| RESTful API | `4010` | `RESTFUL_API_HOST_PORT` |
| PostgreSQL | `5432` | `POSTGRES_HOST_PORT` |
| Mailpit UI / SMTP | `8025` / `1025` | `MAILPIT_UI_HOST_PORT` / `MAILPIT_SMTP_HOST_PORT` |
| rag-service | không publish (nội bộ 4011) | — |

### Địa chỉ truy cập các phân hệ

- **Cổng thông tin Web**: `http://127.0.0.1:3000`
- **Swagger / OpenAPI**: `http://127.0.0.1:4010/swagger-ui.html`
- **Mailpit Email Dashboard**: `http://127.0.0.1:8025`
- **Cơ sở dữ liệu PostgreSQL**: `127.0.0.1:5432` | Database: `campuscore_restful` | User: `campuscore`

---

## Tài khoản demo kiểm thử

Cơ sở dữ liệu seed sẵn các tài khoản tương ứng với các nhóm vai trò trong hệ thống:

| Vai trò (Role) | Email đăng nhập | Mật khẩu mặc định | Quyền hạn và phạm vi truy cập |
| --- | --- | --- | --- |
| **Sinh viên (Student)** | `student@campuscore.edu` | `password123` | Đăng ký học phần, xem TKB, tra cứu điểm/bảng điểm, hỏi Trợ lý AI, đăng ký đề tài khóa luận |
| **Giảng viên (Lecturer)** | `lecturer@campuscore.edu` | `password123` | Xem lịch dạy, chấm/nhập điểm học phần, đề xuất và hướng dẫn các nhóm khóa luận |
| **Quản trị viên (Admin)** | `admin@campuscore.edu` | `admin123` | Quản trị người dùng, danh mục khoa/ngành/phòng học, mở lớp học phần, soạn thảo tri thức RAG |
| **Quản trị viên thứ 2** | `admin002@campuscore.demo` | `admin123` | Tài khoản Quản trị độc lập dùng để duyệt chéo tri thức RAG theo nguyên tắc Four-Eyes |

Ngoài ra, dữ liệu demo còn seed sẵn một danh bạ giảng viên phụ (`lecturer002@campuscore.demo` đến `lecturer012@campuscore.demo`) để danh sách lớp, hội đồng và lịch dạy phản ánh môi trường trường học thực tế.

---

## Đặc tả RESTful API

Toàn bộ API dùng tiền tố chung `/api/v1` và được nhóm thành các tài nguyên chính. **Danh mục đầy đủ và chính xác nhất** (kèm schema request/response) được công bố máy đọc tại `GET /v3/api-docs` và giao diện Swagger UI tại `/swagger-ui.html`:

| Nhóm tài nguyên | Tiền tố đường dẫn | Mô tả chức năng nghiệp vụ |
| --- | --- | --- |
| Xác thực & phiên | `/api/v1/auth` | Đăng ký, đăng nhập phân luồng portal, làm mới token, đăng xuất |
| Hồ sơ cá nhân | `/api/v1/me` | Thông tin cá nhân, điều kiện đăng ký, tóm tắt đợt đăng ký, phiếu đăng ký học |
| Đăng ký học phần | `/api/v1/me/enrollments` | Đăng ký môn (chống race 4 lớp, bắt buộc `Idempotency-Key`), rút môn, danh sách môn đã đăng ký |
| Danh mục & tra cứu | `/api/v1/sections`, `/api/v1/schedules`, `/api/v1/attendance`, `/api/v1/lecturers` | Tra cứu lớp học phần, thời khóa biểu, điểm danh, giảng viên |
| Quản trị danh mục | `/api/v1/departments`, `/api/v1/courses`, `/api/v1/classrooms`, `/api/v1/semesters`, `/api/v1/academic-years` | Quản trị viên tạo/cập nhật cây danh mục đào tạo và mở lớp học phần |
| Quản trị người dùng | `/api/v1/users` | Quản trị tài khoản, cấp quyền Sinh viên / Giảng viên / Admin |
| Khóa luận tốt nghiệp | `/api/v1/thesis`, `/api/v1/thesis/rounds`, `/api/v1/thesis/topics`, `/api/v1/thesis/groups` | Đợt bảo vệ, đề xuất đề tài, nhóm sinh viên, chấm điểm và theo dõi tiến độ |
| Trợ lý AI CampusCore | `/api/v1/assistant` | Hỏi đáp RAG (`/chat`), stream SSE (`/chat/stream`), phản hồi chất lượng (`/messages/{id}/feedback`) |
| Kiểm duyệt tri thức RAG | xem Swagger (nhóm assistant-knowledge) | Tạo bản thảo, duyệt bản nháp 2 Admin, kích hoạt Privacy Gate |
| Bảng tin & thông báo | `/api/v1/announcements`, `/api/v1/notifications` | Đọc bảng tin đào tạo, hộp thư cảnh báo học vụ cá nhân |
| Sức khỏe hệ thống | `/api/v1/health` | Liveness và Readiness Probe với `X-Health-Key` an toàn |

---

## Kiểm thử và đảm bảo chất lượng

```powershell
# Chạy toàn bộ kiểm thử tự động của Java Backend
mvn -q -f java-services/pom.xml verify

# Chạy kiểm thử frontend (node:test)
npm test --prefix frontend

# Kiểm tra kiểu dữ liệu TypeScript
npm run typecheck --prefix frontend

# Kiểm tra chất lượng code theo ESLint
npm run lint --prefix frontend

# Build thử nghiệm gói sản phẩm Frontend
npm run build --prefix frontend

# Chạy riêng bài kiểm thử đa luồng chống race condition
mvn test -f java-services/pom.xml -Dtest=AcademicEnrollmentMutationPersistenceTest
```

### Ma trận kiểm định an toàn

- **Race Condition Concurrency Test**: `AcademicEnrollmentMutationPersistenceTest` kích hoạt các thread độc lập tranh chấp vị trí cuối cùng trong lớp học phần. Chỉ đúng một thread thành công, các thread còn lại nhận lỗi 409 Conflict, sĩ số lớp không bao giờ vượt ngưỡng.
- **Bilingual Injection Test**: Bộ kiểm thử của `AssistantInputGuard` phủ các biến thể tấn công prompt injection bằng cả tiếng Anh và tiếng Việt; câu hỏi vi phạm bị phát hiện và từ chối ở tầng cổng API.
- **Two-Admin Governance Test**: Luồng Admin tự duyệt bài viết do chính mình tạo ra sẽ nhận lỗi `409 Conflict` (`KNOWLEDGE_SECOND_REVIEW_REQUIRED`) đúng như thiết kế.

---

## Tài liệu tham chiếu liên quan

- [Kiến trúc chi tiết hệ thống (docs/ARCHITECTURE.md)](docs/ARCHITECTURE.md): Ranh giới runtime, concurrency invariants và non-goals.
- [Kịch bản Demo chi tiết (docs/DEMO_RUNBOOK.md)](docs/DEMO_RUNBOOK.md): Hướng dẫn từng bước cho buổi báo cáo và thẩm định đồ án.
- [Quy trình phát hành (docs/RELEASE.md)](docs/RELEASE.md): Đóng gói container và tiêu chuẩn nghiệm thu.
- [Triển khai môi trường thật (docs/deployment.md)](docs/deployment.md) và [PRODUCTION_RUNBOOK.md](docs/PRODUCTION_RUNBOOK.md).
- [Tích hợp Trợ lý AI DeepSeek (docs/integrations/deepseek-assistant.md)](docs/integrations/deepseek-assistant.md): Cấu hình bảo mật và cơ chế fallback.
- [Bản tài liệu Tiếng Anh (README.en.md)](README.en.md).

---

*Đồ án Quản lý Sinh viên & Học vụ CampusCore — Khoa Công nghệ Thông tin, HCMUTE.*
