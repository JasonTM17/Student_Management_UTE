# Kỹ thuật dự án CampusCore

[English](README.en.md) · [Tổng quan dự án](../../README.md) · [Mục lục tài liệu](../README.md)

Bộ tài liệu này giải thích kỹ thuật đang có trong mã nguồn: bài toán, luồng xử lý, dữ liệu, ranh giới bảo vệ, đánh đổi và cách kiểm tra. Mỗi chương có bản Việt/Anh tương ứng và liên kết tới lớp hoặc kiểm thử sở hữu hành vi. Các thông số là giá trị trong cấu hình/mã nguồn được dẫn, có thể khác giữa các môi trường.

| Chương | Nội dung |
|---|---|
| [1. Kiến trúc, frontend và bảo mật](architecture-security.vi.md) | Modular monolith, JDBC/JPA, BFF, cache, i18n, JWT, CSRF, refresh rotation, 2FA, phân quyền và rate limit |
| [2. Nghiệp vụ học vụ](academic.vi.md) | Đăng ký đồng thời, idempotency, CTĐT, giới hạn tín chỉ, rút môn, bảng điểm, phúc khảo, điểm danh, rèn luyện và xuất dữ liệu |
| [3. Đồ án và khóa luận](thesis.vi.md) | Tách service giữ transaction, vòng đời đợt, phân quyền khoa/bộ môn, nhóm, điều kiện đăng ký, báo cáo, phản biện, hội đồng và công bố |
| [4. AI và truyền thông học vụ](assistant-engagement.vi.md) | RAG lexical, ngữ cảnh cá nhân, SSE, quota/lease/CAS, RLS, kiểm duyệt hai người, TinyMCE, thông báo, email và audit |
| [5. Mobile, dữ liệu và vận hành](operations-verification.vi.md) | Expo/Bearer, Flyway, lưu trữ, Docker, readiness, backup, CI, các mức kiểm thử và tái tạo ảnh/GIF |

## Cách đọc

Đọc chương 1 trước để xác định ai chịu trách nhiệm cho từng ranh giới. Sau đó chọn chương nghiệp vụ. Khi tìm hiểu một thao tác ghi, lần theo **giao diện → proxy → controller → service → repository/SQL → commit → thông báo**; khi gặp nhiều vai trò, kiểm tra cả quyền trên route và phạm vi dữ liệu trong service.

Đây là tài liệu về khả năng triển khai trong repository. Sự tồn tại của một test không chứng minh test đã chạy; ảnh local không chứng minh production; cấu hình DeepSeek, SMTP hay Supabase không chứng minh dịch vụ thật đã được xác nhận. [Bộ ảnh hiện tại](../media-showcase.vi.md) ghi rõ nguồn chụp và giới hạn.
