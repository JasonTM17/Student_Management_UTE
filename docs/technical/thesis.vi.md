# 3. Kỹ thuật đồ án và khóa luận

[English](thesis.en.md) · [Mục lục](README.vi.md) · [Tiếp: AI và truyền thông](assistant-engagement.vi.md)

## T19. Tách service nhưng giữ transaction ở facade

**Bài toán.** Một service ghi quá lớn làm khó kiểm chứng vòng đời, phân quyền và thứ tự khóa. Chia file mà vô tình mở transaction mới ở từng helper có thể làm một nửa nghiệp vụ commit trước khi phần sau lỗi.

`ThesisMutationService` hiện là facade công khai giữ API cho controller và caller liên module. Các method `@Transactional` mở đơn vị giao dịch rồi ủy quyền cho năm trách nhiệm: `ThesisRoundLifecycleService`, `ThesisPublicationService`, `ThesisTopicMutationService`, `ThesisGroupMembershipService`, `ThesisGroupGovernanceService`. Repository mutation/read được chia theo miền dữ liệu; `ThesisMutationSupport` gom các thao tác hỗ trợ dùng chung.

**Luồng.** Controller → facade mở transaction → service trách nhiệm kiểm luật/phạm vi → repository lấy khóa/ghi → audit cùng transaction → commit → notification ngoài phần commit cốt lõi. Việc tách lớp không tự tạo microservice và không đổi hợp đồng public. Khi đọc code mới, facade cho biết “thao tác nào”, service con cho biết “luật nào”, repository cho biết “SQL nào”.

**Nguồn:** [facade và JavaDoc phân trách nhiệm](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/service/ThesisMutationService.java), [mutation support](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/service/ThesisMutationSupport.java), [repositories](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/repository/).

## T20. State machine theo loại đợt và lịch server

Vòng đời chính: `DRAFT → PROPOSAL_OPEN → PROPOSALS_PUBLISHED → REGISTRATION_OPEN → REGISTRATION_CLOSED → RESULTS_PUBLISHED → CLOSED`. `CANCELLED` có đường hủy/khôi phục riêng; không phải mọi trạng thái đều được tự do chuyển qua lại. Preview và các điều kiện nghiệp vụ quyết định chuyển tiếp nào hợp lệ.

Bốn loại đợt là `MON_HOC`, `NCKH`, `TLCN`, `KLTN`. TLCN/KLTN có phản biện và mốc báo cáo; ngày bảo vệ là yêu cầu riêng của KLTN. UI đọc capability để hiển thị thao tác phù hợp, API vẫn trả mã xung đột ổn định nếu caller gọi thao tác không áp dụng. Không hiển thị “miễn phản biện” như tính năng mặc định: tất cả loại đợt undergraduate hiện phát hành đều tắt capability đó; cấu hình override là ngoại lệ có chủ ý.

Thời gian server/database là thẩm quyền cho cửa sổ ghi. Một đợt được khôi phục về mở đăng ký vẫn phải nằm trong lịch cho phép; đổi trạng thái không mặc nhiên đổi deadline. Đóng đăng ký, công bố, hủy, restore và delete có hậu quả khác nhau; delete cần điều kiện hẹp để không xóa lịch sử đã có phụ thuộc.

**Nguồn:** [RoundStatus](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/model/RoundStatus.java), [RoundType](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/model/RoundType.java), [lifecycle owner](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/service/ThesisRoundLifecycleService.java), [capabilities](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/service/ThesisGovernanceCapabilities.java), [restore races](../../java-services/restful-api/src/test/java/io/campuscore/restfulapi/thesis/ThesisRoundRestoreRacePostgresIT.java).

## T21. Đề tài, GVHD và phân quyền khoa/bộ môn

Đề tài có vòng đề xuất–thẩm định–công bố, bộ môn quản lý và các GVHD được gán tường minh. Người được chọn hướng dẫn phải có tài khoản/profile active hợp lệ; chỉ có tên trong danh bạ không chứng minh đủ điều kiện nhận phân công.

Admin quản trị toàn trường; trưởng khoa/bộ môn theo phạm vi học thuật hiện tại. Scope dựa trên profile giảng viên gắn đúng account và quan hệ khoa/bộ môn của đối tượng. Một trưởng khoa đồng thời là giảng viên có thể có phân công GVHD/hội đồng/phản biện hợp lệ ngoài khoa, nhưng phân công đó không biến mọi quyền quản trị thành toàn cục.

Đề tài bị rút được ẩn khỏi danh mục công khai cho sinh viên không liên quan. Thành viên nhóm có lịch sử gắn với đề tài vẫn cần đọc được thông tin phù hợp; ẩn khỏi catalog không có nghĩa xóa mọi quyền truy cập lịch sử. Gán nhóm sang đề tài khác phải kiểm cả nhóm nguồn và đề tài đích, tránh vượt khoa bằng cách thay liên kết.

**Nguồn:** [topic mutation](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/service/ThesisTopicMutationService.java), [topic reads](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/service/ThesisTopicService.java), [supervisors](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/service/ThesisSupervisorService.java), [dean scope](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/scope/ThesisDeanScope.java), [visibility tests](../../java-services/restful-api/src/test/java/io/campuscore/restfulapi/thesis/ThesisTopicVisibilityTest.java).

## T22. Nhóm, điều kiện học thuật và ngoại lệ có audit

Nhóm nháp được bổ sung dần thành viên; kiểm kích thước chặt ở bước duyệt. Read model trả `minGroupSize`/`maxGroupSize` theo đợt, với cấu hình nghiệp vụ hiện thường 3–5 thành viên và đúng một trưởng nhóm. Khóa/constraint DB bổ sung cho service để không có hai trưởng nhóm hoặc thành viên hoạt động trái luật khi request cạnh tranh.

Điều kiện đăng ký KLTN dựa trên transcript chuẩn: mặc định ít nhất 110 tín chỉ tích lũy và CPA 2.0/4.0. Loại đợt chịu kiểm tra (mặc định `KLTN`) và chế độ `ENFORCE`/`ADVISORY` (mặc định `ADVISORY`) theo cấu hình. ENFORCE chặn mutation không đạt; ADVISORY vẫn tính/cảnh báo. Thiếu dữ liệu điểm chính thức phải được biểu diễn thành không đủ bằng chứng, không cấp đủ điều kiện từ số liệu mẫu. Chưa có mô hình đầy đủ cho mọi điều kiện cảnh báo/kỷ luật trong quy chế nên không được tuyên bố đã tự kiểm hết quy chế.

Phê duyệt bởi người quản lý thay GVHD cần lý do; ngoại lệ kích thước/roster có quyền hẹp và audit. Thu hồi phê duyệt trả APPROVED về PENDING trong cửa sổ cho phép, giữ roster/đề tài, xóa dấu phê duyệt. Nếu đã có báo cáo, điểm, phản biện, hội đồng hoặc kết quả, `GROUP_WITHDRAWAL_BLOCKED` trả counts để giải thích; không mở lại quyết định bằng cách bỏ qua dữ liệu downstream. Nhóm đã hủy giữ lịch sử và không được coi như nhóm active có thể mở lại tùy ý.

**Nguồn:** [membership service](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/service/ThesisGroupMembershipService.java), [group governance](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/service/ThesisGroupGovernanceService.java), [eligibility owner](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/service/ThesisEligibilityService.java), [governance contract](../integrations/thesis-group-governance.md), [cancellation history](../integrations/thesis-cancellation-history.md).

## T23. Báo cáo là artifact: đọc khác sửa, metadata khác byte

Trưởng nhóm nộp Word/PDF hoặc đường liên kết qua API; policy kiểm file/định dạng/kích thước, giới hạn hiện 20 MiB. Quyền nộp khác quyền tải: thành viên, GVHD, người chấm và cán bộ trong scope có các quyền đọc theo nhiệm vụ. Metadata, kho lưu trữ đợt và alias tải phải dùng cùng ranh giới quyền; endpoint khác không được trở thành đường vòng.

**Luồng upload.** Kiểm authority/cửa sổ → xác thực file → ghi object/byte theo provider → liên kết metadata trong transaction → commit → dọn object cũ. Storage ngoài DB không tham gia rollback SQL: chỉ xóa object cũ sau commit; dọn object mới khi xác nhận rollback; nếu kết quả giao dịch chưa rõ, giữ object để đối soát. Một object dư có thể thu hồi, còn row trỏ tới byte đã xóa là mất dữ liệu.

Cập nhật metadata không được làm mất file/link khi caller không truyền trường đó. Chuyển provider chỉ đổi nơi upload mới, không tự di chuyển object cũ. Tên file, ngày nộp và storage key không chứng minh file còn tải được. Khôi phục yêu cầu byte gốc khớp metadata/checksum và kiểm tải bằng đúng quyền; không xóa row chỉ để UI hết báo lỗi.

**Nguồn:** [ThesisReportService](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/service/ThesisReportService.java), [file policy](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/storage/ReportFilePolicy.java), [storage and recovery](../integrations/thesis-report-storage.md), [rollback/concurrency tests](../../java-services/restful-api/src/test/java/io/campuscore/restfulapi/thesis/ThesisReportConcurrencyTest.java).

## T24. Phản biện và gia hạn không mở nhầm cửa sổ nộp

Phản biện (GVPB) là phân công riêng; chỉ người được gán hợp lệ được nhập điểm trong phase/window tương ứng. `ThesisReviewOutcome` định nghĩa ngưỡng 5.0 cùng các outcome `REVIEW_FAILED`, `WITHDRAWN`, `GRADED`, để finalize, preview, công bố và màn hình sinh viên không tự suy luận khác nhau.

Điểm phản biện thấp khi cửa sổ còn mở là kết quả có thể thay đổi, nên không được công bố toàn đợt như thể kết quả đã ổn định. Với MON_HOC/NCKH, điều khiển phản biện không áp dụng và API trả `ROUND_HAS_NO_COUNTER_REVIEW` ở các thao tác tương ứng.

**Gia hạn mới.** Trong trạng thái cho phép, gia hạn GVPB cần mốc tương lai và lý do. Nếu hạn mới vượt ngày báo cáo/bảo vệ, service đẩy các ngày phụ thuộc về sau để giữ thứ tự lịch. Khi hạn nộp báo cáo trước đó kế thừa hạn GVPB, service ghim `report_deadline` về hạn cũ trước khi gia hạn; nhờ vậy chỉ mở lại chấm phản biện, không âm thầm cho sửa báo cáo. Mọi thay đổi nằm trong một giao dịch với audit before/after. Gia hạn nộp báo cáo là thao tác khác.

**Nguồn:** [lifecycle deadline operations](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/service/ThesisRoundLifecycleService.java), [review outcome](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/service/ThesisReviewOutcome.java), [council/reviewer gates](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/service/ThesisCouncilService.java), [schedule tests](../../java-services/restful-api/src/test/java/io/campuscore/restfulapi/thesis/ThesisRoundRegistrationScheduleTest.java).

## T25. Hội đồng, xung đột lợi ích và chốt điểm

Hội đồng có 3–5 giảng viên, đúng một chủ tịch và một thư ký. Gán đề tài, thành viên, phản biện và chấm điểm có các điều kiện riêng; giữ vai trò trưởng khoa không thay thế một phân công chấm cần thiết. GVHD không được tự chấm đề tài mình hướng dẫn (`SUPERVISOR_CANNOT_GRADE`).

Mỗi thành viên đủ điều kiện ghi điểm thành phần của mình. Read model cho biết ai đã chấm, phần còn thiếu và breakdown phù hợp quyền. Chủ tịch chốt khi các điều kiện hoàn tất; kết quả bảo vệ hiện là trung bình bằng trọng số của các thành viên đủ điều kiện, tính decimal và làm tròn rõ ràng. Không gán công thức GVHD/GVPB/hội đồng 20/30/50 nếu source không dùng công thức đó.

Finalize khóa và chuyển trạng thái có điều kiện, không chỉ disable nút ở browser. Thao tác reopen/reassign/withdraw có lý do và gate riêng để không tạo điểm mồ côi hoặc thay roster sau khi kết quả bị khóa. Finalize nội bộ chưa phải quyền sinh viên xem kết quả; quyền xem còn phụ thuộc công bố đợt.

**Nguồn:** [ThesisCouncilService](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/service/ThesisCouncilService.java), [score breakdown tests](../../java-services/restful-api/src/test/java/io/campuscore/restfulapi/thesis/ThesisScoreBreakdownTest.java), [PostgreSQL score checks](../../java-services/restful-api/src/test/java/io/campuscore/restfulapi/thesis/ThesisScoreBreakdownPostgresIT.java).

## T26. Preview hậu quả, công bố và phục hồi có kiểm soát

Preview công bố trả số nhóm được duyệt, đề tài được tính, thiếu điểm, bị loại và cửa sổ phản biện chưa đóng. Write path kiểm lại sau khi lấy khóa round: preview không giữ chỗ và không phải vé bảo đảm dữ liệu chưa thay đổi.

Chưa có nhóm/điểm hợp lệ trả `RESULTS_NOT_READY`; thiếu điểm trả `SCORES_INCOMPLETE`; cửa sổ phản biện còn mở trả `REVIEW_WINDOW_OPEN`. Khi tất cả nhóm bị loại, phải xác nhận chủ ý `confirmAllExcluded`, ghi audit. Đề tài bị rút hoặc phản biện không đạt được xử lý theo cùng outcome owner, không biến thành hàng điểm rỗng.

Commit chuyển đợt sang RESULTS_PUBLISHED rồi fan-out thông báo theo cơ chế sau commit. Retraction, close/reopen và hủy/restore có điều kiện, grace window và audit; không sửa trực tiếp status trong DB để bỏ qua invariant. Đọc dashboard trưởng khoa dùng truy vấn tổng hợp theo khoa, khối lượng GVHD/bộ môn và tiến trình; số tổng quan không cấp thêm quyền mutation.

**Nguồn:** [publication service](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/service/ThesisPublicationService.java), [publication repository](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/repository/ThesisPublicationRepository.java), [publication race tests](../../java-services/restful-api/src/test/java/io/campuscore/restfulapi/thesis/ThesisPublicationRacePostgresIT.java), [dean overview](../../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/service/ThesisDeanOverviewService.java).
