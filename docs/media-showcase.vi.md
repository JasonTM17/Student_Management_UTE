# Ảnh giao diện và GIF hiện tại

[English](media-showcase.en.md) · [Tiếng Việt](media-showcase.vi.md) · [Tài liệu kỹ thuật](technical/README.vi.md)

## Nguồn chụp

Ảnh được render thật từ build local theo mã nguồn hiện tại, trong project Compose `campuscore-docs` với database seed riêng. Giao diện hiển thị thương hiệu CampusUTE; tên codebase là CampusCore.

- Source: `fb9b60af3b0ad2f854047036c1cc5bb19466ead3`
- Next build: `N4g1GZ8EeONCvY1oewd5t`
- API image: `sha256:af7b5525ef900b4ecd47c55020c2186ac5747ed750015b366a184edb4bd6b661`
- Bốn role đăng nhập thật và xác minh `/api/v1/auth/me` trả 200 với email đúng.
- PNG 1440×1000; GIF rộng 1200 px, chuỗi màn hình giữ khoảng 4 giây, không phải video thao tác liên tục.
- DeepSeek và gửi SMTP bên ngoài tắt; rate limit giữ bật.

[Manifest và checksum đầy đủ](assets/showcase/provenance.json) · [Cách tái tạo](technical/operations-verification.vi.md)

Hash trong manifest khớp cả 77 tài sản nhị phân. Hash/kích thước của hai SVG
thuộc bản gốc Windows CRLF; blob SVG được Git chuẩn hóa LF khác ở mức byte.
Khôi phục CRLF khớp hash đã ghi; đây là khác biệt xuống dòng, không phải lượt chụp mới.

## Giới hạn được quan sát

Bản seed có một metadata báo cáo nhóm trả 404 ở cả hai locale; bộ ảnh không xác nhận upload, tải hay khôi phục tài liệu. Danh sách phúc khảo và lịch sử batch CTĐT có thể trống vì chưa tạo hồ sơ. Job reconcile cảnh báo drift sĩ số seed; không tự sửa dữ liệu để chụp. Trợ lý được hỏi câu kỹ thuật trong workspace chuyên sâu, với provider ngoài tắt.

Ảnh chứng minh giao diện và các request trong manifest; không chứng minh mọi thao tác ghi, email giao thành công, RLS/race test toàn bộ, chạy mobile thật hoặc production. Một lỗi npm audit ở dependency được ghi trong manifest, không được coi là đã sửa bởi cập nhật tài liệu.

Trang email giảng viên hiện còn hiển thị placeholder `{max}` trong phần tóm tắt người nhận. Ảnh giữ nguyên lỗi copy có sẵn; cập nhật này không sửa mã giao diện.

## GIF

| Hành trình | Ảnh động |
|---|---|
| Student | [student-tour-vi.gif](assets/showcase/student-tour-vi.gif) |
| Lecturer / Admin | [staff-tour-vi.gif](assets/showcase/staff-tour-vi.gif) |
| Thesis governance | [thesis-governance-tour-vi.gif](assets/showcase/thesis-governance-tour-vi.gif) |
| VI / EN | [language-tour.gif](assets/showcase/language-tour.gif) |

## Toàn bộ màn hình

| Màn hình | Tiếng Việt | English |
|---|---|---|
| admin announcements | [PNG](assets/showcase/admin-announcements-vi.png) | [PNG](assets/showcase/admin-announcements-en.png) |
| admin appeals | [PNG](assets/showcase/admin-appeals-vi.png) | [PNG](assets/showcase/admin-appeals-en.png) |
| admin audit | [PNG](assets/showcase/admin-audit-vi.png) | [PNG](assets/showcase/admin-audit-en.png) |
| admin curriculum defaults | [PNG](assets/showcase/admin-curriculum-defaults-vi.png) | [PNG](assets/showcase/admin-curriculum-defaults-en.png) |
| admin dashboard | [PNG](assets/showcase/admin-dashboard-vi.png) | [PNG](assets/showcase/admin-dashboard-en.png) |
| admin knowledge | [PNG](assets/showcase/admin-knowledge-vi.png) | [PNG](assets/showcase/admin-knowledge-en.png) |
| admin sections | [PNG](assets/showcase/admin-sections-vi.png) | [PNG](assets/showcase/admin-sections-en.png) |
| admin thesis | [PNG](assets/showcase/admin-thesis-vi.png) | [PNG](assets/showcase/admin-thesis-en.png) |
| admin users | [PNG](assets/showcase/admin-users-vi.png) | [PNG](assets/showcase/admin-users-en.png) |
| dean dashboard | [PNG](assets/showcase/dean-dashboard-vi.png) | [PNG](assets/showcase/dean-dashboard-en.png) |
| dean thesis | [PNG](assets/showcase/dean-thesis-vi.png) | [PNG](assets/showcase/dean-thesis-en.png) |
| lecturer announcements | [PNG](assets/showcase/lecturer-announcements-vi.png) | [PNG](assets/showcase/lecturer-announcements-en.png) |
| lecturer attendance | [PNG](assets/showcase/lecturer-attendance-vi.png) | [PNG](assets/showcase/lecturer-attendance-en.png) |
| lecturer dashboard | [PNG](assets/showcase/lecturer-dashboard-vi.png) | [PNG](assets/showcase/lecturer-dashboard-en.png) |
| lecturer grades | [PNG](assets/showcase/lecturer-grades-vi.png) | [PNG](assets/showcase/lecturer-grades-en.png) |
| lecturer mail | [PNG](assets/showcase/lecturer-mail-vi.png) | [PNG](assets/showcase/lecturer-mail-en.png) |
| lecturer schedule | [PNG](assets/showcase/lecturer-schedule-vi.png) | [PNG](assets/showcase/lecturer-schedule-en.png) |
| lecturer thesis | [PNG](assets/showcase/lecturer-thesis-vi.png) | [PNG](assets/showcase/lecturer-thesis-en.png) |
| public home | [PNG](assets/showcase/public-home-vi.png) | [PNG](assets/showcase/public-home-en.png) |
| public login | [PNG](assets/showcase/public-login-vi.png) | [PNG](assets/showcase/public-login-en.png) |
| public recovery | [PNG](assets/showcase/public-recovery-vi.png) | [PNG](assets/showcase/public-recovery-en.png) |
| student assistant | [PNG](assets/showcase/student-assistant-vi.png) | [PNG](assets/showcase/student-assistant-en.png) |
| student assistant answer | [PNG](assets/showcase/student-assistant-answer-vi.png) | [PNG](assets/showcase/student-assistant-answer-en.png) |
| student attendance | [PNG](assets/showcase/student-attendance-vi.png) | [PNG](assets/showcase/student-attendance-en.png) |
| student conduct | [PNG](assets/showcase/student-conduct-vi.png) | [PNG](assets/showcase/student-conduct-en.png) |
| student curriculum | [PNG](assets/showcase/student-curriculum-vi.png) | [PNG](assets/showcase/student-curriculum-en.png) |
| student dashboard | [PNG](assets/showcase/student-dashboard-vi.png) | [PNG](assets/showcase/student-dashboard-en.png) |
| student enrollments | [PNG](assets/showcase/student-enrollments-vi.png) | [PNG](assets/showcase/student-enrollments-en.png) |
| student grades | [PNG](assets/showcase/student-grades-vi.png) | [PNG](assets/showcase/student-grades-en.png) |
| student notifications | [PNG](assets/showcase/student-notifications-vi.png) | [PNG](assets/showcase/student-notifications-en.png) |
| student registration | [PNG](assets/showcase/student-registration-vi.png) | [PNG](assets/showcase/student-registration-en.png) |
| student schedule | [PNG](assets/showcase/student-schedule-vi.png) | [PNG](assets/showcase/student-schedule-en.png) |
| student thesis | [PNG](assets/showcase/student-thesis-vi.png) | [PNG](assets/showcase/student-thesis-en.png) |
| student transcript | [PNG](assets/showcase/student-transcript-vi.png) | [PNG](assets/showcase/student-transcript-en.png) |

## Sơ đồ hiện tại

Sơ đồ kiến trúc là Mermaid, nằm trong [README](../README.md#architecture) và [ARCHITECTURE.md](ARCHITECTURE.md).
