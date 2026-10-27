# 2. Kỹ thuật nghiệp vụ học vụ

[English](academic.en.md) · [Mục lục](README.vi.md) · [Tiếp: khóa luận](thesis.vi.md)

## T11. Đăng ký học phần: khóa sinh viên và lớp học phần

**Bài toán.** Kiểm tra còn chỗ rồi insert bằng hai request không đồng bộ có thể vượt sĩ số; hai đăng ký khác lớp của cùng sinh viên cũng có thể cùng vượt hạn mức tín chỉ hoặc trùng lịch. Chỉ khóa lớp không giải quyết được cả hai loại tranh chấp.

**Luồng ghi hiện tại.** `AcademicMutationService.enroll` đi qua `RegistrationService`. Service nhận student ID từ danh tính đã xác thực, section ID và idempotency key. Trong transaction: nhận diện request lặp → khóa Student → khóa Section → kiểm học kỳ/đợt/cohort → đọc tập đăng ký active → kiểm trùng lớp/môn, tín chỉ, tiên quyết/song hành và lịch → tăng sĩ số có điều kiện → insert Enrollment → ghi EnrollmentEvent và hoàn tất idempotency. Các thao tác sửa trạng thái liên quan phải dùng thứ tự khóa tương thích.

Phép tăng chỉ tác động khi `enrolledCount < capacity` và lớp `OPEN`. Nếu ảnh hưởng 0 dòng, trả `409 SECTION_FULL`; lỗi ở bước sau phải rollback cả số đếm. Partial unique index chặn bản ghi active trùng ở DB, bổ sung cho kiểm tra ở service. Đây là kiểm soát cạnh tranh, không phải bằng chứng rằng `FOR UPDATE` tự nâng toàn bộ giao dịch lên serializable hoặc loại bỏ mọi hiện tượng đọc ở các bảng khác.

**Nguồn:** [RegistrationService](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/academic/registration/service/RegistrationService.java), [facade](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/academic/service/AcademicMutationService.java), [migration owner](../../java-services/restful-api/src/main/resources/db/migration/README.md), [enrollment persistence tests](../../java-services/restful-api/src/test/java/io/campuscore/restfulapi/academic/AcademicEnrollmentMutationPersistenceTest.java).

## T12. Idempotency, snapshot và phiếu đăng ký

**Đầu vào/đầu ra.** Key đại diện cho một ý định thao tác; request hash gắn key với payload. Cùng chủ sở hữu/key và cùng hash có thể trả lại enrollment đã xử lý; dùng key cũ với payload khác là xung đột, không được coi là yêu cầu mới. Client retry một thao tác phải giữ key; thao tác khác cần key khác.

Enrollment lưu snapshot như môn, số tín chỉ và đợt để dữ liệu lịch sử không phụ thuộc hoàn toàn vào danh mục đang sửa. EnrollmentEvent ghi hành động và actor. Phiếu đăng ký là PDF có SHA-256; đường đọc phiếu khóa/chụp lại tập đăng ký phù hợp để không phát hành phiếu cũ sau hủy lớp. Có checksum chứng minh sự nhất quán byte, không thay thế phân quyền tải.

Số đếm sĩ số là dữ liệu dẫn xuất. Job reconcile so với số enrollment active, cảnh báo drift; tự sửa mặc định tắt. Vì vậy dữ liệu seed hoặc thao tác quản trị ngoài luồng có thể tạo drift và cần xử lý vận hành, không thể dùng nhãn “4 lớp” để tuyên bố dữ liệu luôn đúng vô điều kiện.

**Nguồn:** [idempotency/slip implementation](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/academic/registration/service/RegistrationService.java), [PDF renderer](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/academic/registration/renderer/RegistrationPdfRenderer.java), [reconcile job](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/academic/job/EnrolledCountReconcileJob.java).

## T13. Danh mục đăng ký, lịch và kế hoạch CTĐT

Danh mục trả cả lịch, phòng, giảng viên, sức chứa và mức liên quan CTĐT. Service dùng truy vấn fan-out rồi nhóm các dòng lịch theo section, tránh một query lịch cho từng thẻ lớp. Đây là kỹ thuật khống chế N+1; ngân sách query nằm trong source, không phải số đo latency production.

Kế hoạch CTĐT ánh xạ năm/kỳ của sinh viên sang các môn bắt buộc; chọn lớp còn mở, còn chỗ và hợp lịch, trả lý do bỏ qua như đã học, thiếu tiên quyết, hết chỗ hoặc hết hạn mức. Đăng ký theo kế hoạch gọi lại đường ghi có khóa thông thường, không insert bypass luật.

Admin có **dry-run → apply** với xử lý theo lô, cursor tiếp tục và audit. Đơn vị giao dịch theo sinh viên giúp một trường hợp lỗi không biến toàn bộ danh sách thành giao dịch kéo dài. Key ổn định khiến chạy lại an toàn; dấu nguồn `CURRICULUM_DEFAULT` giúp giải thích môn được thêm từ đâu. Môn sinh viên đã chủ động rút không được tự thêm lại bởi lần áp dụng sau. Không có lịch nền tự chạy thay quyết định của Phòng Đào tạo.

**Nguồn:** [catalog/registration](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/academic/registration/service/RegistrationService.java), [curriculum service](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/academic/registration/service/CurriculumDefaultRegistrationService.java), [repository](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/academic/registration/repository/CurriculumDefaultRegistrationRepository.java), [concurrency checks](../../java-services/restful-api/src/test/java/io/campuscore/restfulapi/academic/CurriculumDefaultRegistrationRacePostgresIT.java).

## T14. Hạn mức tín chỉ và hai hình thức rút môn

Hạn mức hiệu lực được tính tại server theo sinh viên, đợt, học kỳ và cấu hình; không lấy con số trên dashboard làm quyền đăng ký. Mã hiện tại có chuẩn 28 tín chỉ, mức duyệt ngoại lệ 30, hạn mức hè và override demo riêng. Ngưỡng tối thiểu và điều kiện nộp phiếu là chính sách khác với trần được phép thêm môn; không dùng một con số cho mọi bước.

**Drop tự phục vụ** trong cửa sổ cho phép giải phóng chỗ và cập nhật tập môn hiện tại. **Đơn xin rút có duyệt** là hồ sơ `PENDING` để người có quyền phê duyệt/từ chối; khi duyệt, môn được ghi lịch sử điểm `W`. `W` hiện trong bảng điểm nhưng không tham gia GPA, tín chỉ đạt hoặc lựa chọn lần học tốt nhất. Phê duyệt cần khóa và kiểm lại trạng thái, tránh hai người duyệt cùng một đơn.

Hủy lớp vẫn cần bảo toàn lịch sử; mở lại không có nghĩa tự phục hồi mọi đăng ký đã được thay thế. Các read model và mobile dùng cùng định nghĩa enrollment active, tránh đếm cả bản ghi đã rút/hủy thành môn đang học.

**Nguồn:** [credit-limit policy](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/academic/registration/service/CreditLimitApplicationService.java), [withdrawal service](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/academic/registration/service/WithdrawalApplicationService.java), [status owner](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/academic/model/EnrollmentStatuses.java), [frontend status rules](../../frontend/src/lib/enrollment-status.ts).

## T15. Nhập điểm, công bố và bảng điểm tích lũy

**Đường ghi.** Giảng viên chỉ sửa lớp được phân công, hoặc quản trị theo authority. Service khóa dữ liệu liên quan, kiểm từng enrollment thuộc đúng section và tình trạng cho phép, rồi lưu điểm thành phần trong transaction. Quy tắc hiện hành là điểm quá trình và cuối kỳ cùng trọng số 50%; điểm tổng tính bằng `BigDecimal`, làm tròn rõ ràng. Điểm quá trình đơn lẻ có thể lưu nháp nhưng chưa tạo điểm tổng đầy đủ.

**Công bố.** Lưu nháp khác công bố: xuất bản kiểm tính đầy đủ và trạng thái; sinh viên không được xem nháp như điểm chính thức. Sau commit mới gửi các side effect thông báo; lỗi SMTP không được biến điểm đã commit thành “lưu thất bại”. Frontend hiển thị mã lỗi nghiệp vụ do API trả thay vì toast chung che nguyên nhân.

**Đường đọc.** Bảng điểm có điểm 10, điểm chữ và quy đổi 4.0 theo `LetterGrades`. GPA tích lũy chọn lần học tốt nhất của mỗi môn trong tập điểm được phép dùng, nhân trọng số tín chỉ rồi chia tổng tín chỉ tham gia GPA. Các khối học kỳ vẫn giữ lịch sử lần học. F không tạo tín chỉ đạt; W không tham gia phép chọn/GPA. Transcript summary cũng là nguồn dùng cho kiểm tra điều kiện khóa luận, tránh hai công thức GPA độc lập.

**Nguồn:** [grade writes](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/academic/service/AcademicMutationService.java), [transcript reducer](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/academic/service/AcademicEnrollmentReadService.java), [grade conversion](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/academic/model/LetterGrades.java), [grade UI](../../frontend/src/app/dashboard/lecturer/grades/page.tsx).

## T16. Phúc khảo và thu hồi điểm công bố

Sinh viên nộp phúc khảo cho enrollment của mình có điểm đã công bố và còn thời hạn. Deadline tính từ `gradePublishedAt` theo cửa sổ cấu hình, hiện mặc định 7 ngày. Không lấy thời gian mở trang hoặc múi giờ browser thay thời điểm server. Một enrollment chỉ có một lần nộp theo hợp đồng hiện tại; hủy chỉ áp dụng hồ sơ còn pending.

Giảng viên được phân công hoặc người có quyền fallback hợp lệ xử lý hồ sơ; sinh viên không thể duyệt đơn của mình. Chấp nhận có thể sửa điểm theo miền giá trị/độ chính xác cho phép; từ chối không được mang theo điểm sửa như thể đã chấp nhận. Service khóa, kiểm lại trạng thái, cập nhật kết quả và ghi audit/notification nhất quán.

Thu hồi công bố là thao tác riêng, có lý do và các chốt bảo vệ. Không được unpublish điểm đang có phúc khảo pending hoặc điểm đã sửa theo phúc khảo được chấp nhận để xóa dấu vết quyết định. Cạnh tranh giữa sửa điểm, công bố và phúc khảo cần PostgreSQL interleaving tests, không chỉ test UI.

**Nguồn:** [GradeAppealService](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/academic/service/GradeAppealService.java), [persistence tests](../../java-services/restful-api/src/test/java/io/campuscore/restfulapi/academic/GradeAppealPersistenceTest.java), [race tests](../../java-services/restful-api/src/test/java/io/campuscore/restfulapi/academic/GradeAppealRacePostgresIT.java), [deadline UI](../../frontend/src/lib/grade-appeal-deadline.ts).

## T17. Điểm danh, rèn luyện và tín hiệu cảnh báo

Điểm danh dùng ngày học ổn định và lịch lớp, kiểm giảng viên phụ trách, roster và cửa sổ sửa. Tổng tỷ lệ tính `(PRESENT + LATE) / (PRESENT + LATE + ABSENT)`; EXCUSED không nằm ở tử hoặc mẫu. Phần trăm làm tròn phục vụ hiển thị; quyết định `AT_RISK` so với ngưỡng chính xác, tránh 35/44 bị hiển thị 80% rồi được coi đã đạt 80%.

Khi chưa đủ số buổi, tín hiệu là `INSUFFICIENT_DATA`; đây là cảnh báo tư vấn trên read model, không tự động cấm thi, cấm đăng ký hoặc khóa nhập điểm. Muốn có biện pháp cưỡng chế cần một hợp đồng nghiệp vụ khác.

Điểm rèn luyện đọc dữ liệu theo học kỳ và hoạt động, trình bày năm nhóm tiêu chí cùng tổng/xếp loại. Giảng viên chỉ được xem sinh viên thuộc lớp mình phụ trách; sinh viên đọc hồ sơ của mình. Module đọc không nên được mô tả thành hệ thống xét kỷ luật hay quy trình phê duyệt điểm rèn luyện đầy đủ.

**Nguồn:** [attendance writes](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/academic/service/AcademicAttendanceWriteService.java), [rate formula](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/academic/service/AttendanceRateCalculator.java), [advisory signal](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/academic/service/AttendanceEligibility.java), [conduct reads](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/academic/service/AcademicConductService.java), [attendance day contract](../integrations/attendance-date-keys.md).

## T18. Nhập/xuất, danh mục và tìm kiếm

Parser bảng điểm nhận CSV, phân cách dấu chấm phẩy hoặc dữ liệu tab dán từ Excel. Nó ghép dòng bằng mã sinh viên/email, kiểm điểm, phát hiện trùng và trả các dòng bỏ qua kèm lý do. Bước xem trước chỉ điền form; người dùng vẫn lưu nháp/công bố qua API có phân quyền. Đây không phải trình đọc file `.xlsx` tổng quát.

Xuất điểm/điểm danh và lịch iCalendar là các định dạng riêng. iCalendar escape dấu phân cách, newline và tạo lịch lặp từ lịch học/giới hạn học kỳ. Chỉ enrollment active và lớp phù hợp mới nên tạo sự kiện. PDF phiếu đăng ký là đường backend khác với tải CSV hoặc in bảng điểm trên browser.

Danh mục năm học, học kỳ, khoa/ngành, môn, phòng, giảng viên và lớp là các thực thể có quan hệ. Những trường thay đổi ý nghĩa enrollment đang tồn tại có chốt khóa; UI giải thích vì sao khóa thay vì cho sửa rồi nhận lỗi muộn. Search có chuẩn hóa văn bản và truy vấn tham số hóa; không suy từ tên chức năng “tìm kiếm” rằng đã có search engine riêng.

**Nguồn:** [grade parser](../../frontend/src/lib/grade-sheet.ts), [grade export](../../frontend/src/lib/grade-export.ts), [attendance export](../../frontend/src/lib/attendance-export.ts), [calendar generator](../../frontend/src/lib/ical-generator.ts), [catalog mutations](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/academic/service/AdminCatalogMutationService.java), [section-lock regression](../../frontend/tests/section-enrollment-lock.test.js).

Giấy xác nhận sinh viên được dựng ở client từ danh tính và dữ liệu CTĐT/hồ sơ đã tải; thông tin không có được để trống thay vì bịa dữ liệu. Bản in này không tự chứng minh đã có chữ ký số hoặc một quy trình cấp giấy có giá trị pháp lý. **Nguồn:** [certificate screen](../../frontend/src/app/dashboard/certificates/page.tsx).
