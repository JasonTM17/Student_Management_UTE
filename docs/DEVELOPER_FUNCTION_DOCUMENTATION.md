# Quy ước mô tả hàm

Mỗi hàm mới hoặc hàm được sửa trong backend, frontend và chatbot phải có mô tả ngắn ngay phía trên hàm.

Mô tả cần trả lời ba ý:

1. Hàm làm nhiệm vụ gì và phục vụ màn hình/API nào.
2. Dữ liệu đầu vào, đầu ra và trạng thái quan trọng.
3. Quy tắc nghiệp vụ hoặc tác dụng phụ cần giữ khi thay đổi.

Backend Java dùng Javadoc (`/** ... */`), frontend TypeScript dùng TSDoc (`/** ... */`), còn SQL migration dùng comment đầu file và comment trước mỗi khối ghi dữ liệu. Hàm private cũng phải được mô tả nếu có logic nghiệp vụ, truy vấn dữ liệu, chuyển trạng thái hoặc gọi dịch vụ bên ngoài.

Ví dụ:

```java
/** Reads the active registration phase for catalog and eligibility screens. */
private Map<String, Object> openReadRound(String semesterId) { ... }
```

Không đưa thông tin bí mật, token, email cá nhân hoặc dữ liệu sinh viên thật vào phần mô tả.

## Ràng buộc nghiệp vụ và nơi tra cứu

Phần mô tả hàm cần giữ lý do của quyết định; chi tiết thực thi và hợp đồng dữ liệu thuộc mã nguồn ở các đầu mối dưới đây.

- **Bộ lọc học kỳ khi nhập điểm:** lỗi tải danh mục không được khiến giảng viên hiểu rằng không có học kỳ hoặc không còn lớp phụ trách. Nơi tra cứu: [trang nhập điểm](../frontend/src/app/dashboard/lecturer/grades/page.tsx), `fetchSemesters` và vùng thử lại.
- **Báo cáo khóa luận:** chưa có báo cáo là trạng thái nghiệp vụ khác với không tải được báo cáo. Thử lại báo cáo phải giữ phần điểm đang nhập, còn dữ liệu cũ không được đại diện cho một báo cáo đã không còn tồn tại. Nơi tra cứu: [trang khóa luận](../frontend/src/app/dashboard/thesis/page.tsx) và [CouncilDefenseWorkspace](../frontend/src/features/thesis/components/CouncilDefenseWorkspace.tsx).
- **Phản hồi không có nội dung:** trạng thái chưa có báo cáo hợp lệ phải đi qua proxy mà không trở thành lỗi máy chủ. Nơi tra cứu: [proxy API cùng nguồn](../frontend/src/app/api/v1/[...path]/route.ts), `handle`.
- **Ngày điểm danh:** ngày học là một đơn vị nghiệp vụ, không phải một thời điểm tùy máy người dùng. Quy ước ngày UTC phải thống nhất giữa đọc, ghi và xóa; bản ghi cũ có giờ trong ngày vẫn thuộc ngày đó. Nơi tra cứu: [dịch vụ đọc](../java-services/restful-api/src/main/java/io/campuscore/restfulapi/academic/service/AcademicAttendanceReadService.java), [dịch vụ ghi](../java-services/restful-api/src/main/java/io/campuscore/restfulapi/academic/service/AcademicAttendanceWriteService.java) và các repository cùng phân hệ.
- **Đối tượng nhận thông báo:** liên kết chi tiết và bản xem trước trong hộp thư đều có thể tiết lộ nội dung riêng tư, nên phạm vi người nhận phải cùng chính sách với bảng tin. Nơi tra cứu: [AnnouncementReadService](../java-services/restful-api/src/main/java/io/campuscore/restfulapi/engagement/service/AnnouncementReadService.java) và [AnnouncementStudentNotifier](../java-services/restful-api/src/main/java/io/campuscore/restfulapi/engagement/service/AnnouncementStudentNotifier.java).
- **Thông báo sau công bố:** hộp thư không được khẳng định một sự kiện đã công bố khi giao dịch gốc còn có thể bị hoàn tác. Việc gửi là tác dụng phụ sau commit; lỗi gửi không được đảo ngược kết quả công bố. Nơi tra cứu: [AnnouncementWriteService](../java-services/restful-api/src/main/java/io/campuscore/restfulapi/engagement/service/AnnouncementWriteService.java), `notifyActiveStudents`, và [AcademicMutationService](../java-services/restful-api/src/main/java/io/campuscore/restfulapi/academic/service/AcademicMutationService.java), `publishGrades`.
- **Điểm chính thức:** lưu điểm và công bố phải có một thứ tự nhất quán để bản điểm đã công bố không bị lần lưu đồng thời ghi đè hoặc phát thông báo lặp. Nơi tra cứu: `updateGrades`, `publishGrades` và khóa lớp học phần trong [AcademicMutationService](../java-services/restful-api/src/main/java/io/campuscore/restfulapi/academic/service/AcademicMutationService.java).
- **Duyệt đề tài:** giảng viên tạo bản nháp; quyền sở hữu không cấp quyền tự công bố. [ThesisDepartmentScope](../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/scope/ThesisDepartmentScope.java) xác định bộ môn từ hồ sơ giảng viên đang hoạt động, gắn với subject hiện tại. [ThesisMutationService](../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/service/ThesisMutationService.java), `publishTopic`, kiểm tra quyền quản lý và giai đoạn đề xuất.
- **Lưu trữ đề tài:** `archiveTopic` khóa bản ghi đề tài và từ chối mọi lịch sử nhóm/hội đồng/chấm điểm hoặc phân công phản biện. Thao tác đổi trạng thái giữ nguyên dữ liệu và giảng viên hướng dẫn; lỗi `TOPIC_IN_USE` không làm mất lịch sử.
- **Kết quả khóa luận và thư:** `publishResults` gửi tác dụng phụ sau commit. [ThesisNotificationService](../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/service/ThesisNotificationService.java) tạo thông báo và gửi thư riêng cho từng sinh viên đủ điều kiện trong đúng đợt đã công bố. SMTP gửi best-effort; không có outbox bảo đảm gửi lại sau sự cố tiến trình.
