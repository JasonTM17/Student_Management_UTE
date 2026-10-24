# Hủy nhóm khóa luận và lịch sử thành viên

Khi nhóm chuyển sang `CANCELLED`, hệ thống giữ nguyên mã nhóm, mã thành viên và danh sách thành viên cũ. Nhóm đã hủy không thể mở lại hoặc chỉnh sửa danh sách thành viên. Sinh viên có thể tạo hoặc tham gia nhóm mới khi đợt đăng ký còn cho phép; kiểm tra một nhóm đang hoạt động vẫn áp dụng.

Flyway `V91__release_canceled_thesis_memberships.sql` bổ sung `active_participation` và thay ràng buộc duy nhất toàn bộ lịch sử bằng chỉ mục duy nhất `(round_id, student_id)` cho các dòng đang tham gia. PostgreSQL tự suy ra dấu tham gia từ trạng thái nhóm, giải phóng dấu này trong cùng giao dịch hủy nhóm và khóa nhóm trước thao tác thành viên. Khi đổi nhóm, các nhóm được khóa theo thứ tự ID cố định. Các ràng buộc số lượng thành viên, trưởng nhóm và đợt đăng ký của V65 tiếp tục áp dụng.

API khóa hồ sơ sinh viên trước kiểm tra và tạo/thêm thành viên để tuần tự hóa quyền tham gia giữa các đợt đang hoạt động. Chỉ mục DB có phạm vi một đợt; thao tác SQL trực tiếp giữa nhiều đợt không thay thế các kiểm tra nghiệp vụ của API.

Nâng cấp bằng cơ chế Flyway hiện có. Sao lưu DB theo quy trình vận hành trước khi áp dụng ở môi trường thật. Không sửa checksum, xóa lịch sử Flyway hoặc chạy `clean` để bỏ qua migration. Sau khi đã có thành viên tham gia lại, khôi phục ràng buộc cũ sẽ xung đột với lịch sử được giữ; sửa lỗi migration đã áp dụng bằng migration tiếp theo.

Bản tương thích H2 dùng cột sinh nullable được tạo từ dấu tham gia và ràng buộc duy nhất; kiểm thử PostgreSQL kiểm chứng trigger và lịch khóa thật. Không suy luận độ an toàn đồng thời của PostgreSQL từ H2.

## Hủy và khôi phục đợt đăng ký

Hủy đợt chỉ đổi trạng thái dòng đợt sang `CANCELLED`: nhóm, thành viên, đề tài, báo cáo, hội đồng và điểm giữ nguyên làm lịch sử. Flyway `V139__thesis_round_cancelled_from_status.sql` thêm `cancelled_from_status` để ghi lại trạng thái ngay trước khi hủy; `POST /api/v1/thesis/rounds/{id}/restore` (cùng vai trò được phép hủy) đưa đợt về đúng trạng thái đó. Cả hủy và khôi phục đều tăng `version` và ghi dòng audit; dòng `THESIS_ROUND_RESTORED` ghi thêm `target_source` (`RECORDED`, `EXPLICIT` hoặc `LEGACY_DEFAULT`).

Khôi phục không được nhảy pha vòng đời: có dấu ghi thì `targetStatus` chỉ được bằng dấu đó, trừ trường hợp dấu là `REGISTRATION_OPEN` — lúc đó `REGISTRATION_CLOSED` được chấp nhận vì cửa sổ đăng ký có thể đã qua trong lúc đợt bị hủy. Mọi mục tiêu lùi pha hoặc nhảy cóc đều bị `409 ROUND_RESTORE_TARGET_INVALID`; `CANCELLED`, `RESULTS_PUBLISHED`, `CLOSED` không bao giờ là mục tiêu hợp lệ. Khôi phục về `REGISTRATION_OPEN` còn chịu kiểm tra cửa sổ đăng ký (`REGISTRATION_WINDOW_CLOSED`).

Đợt bị hủy trước khi có cột này mang `cancelled_from_status = NULL`. Nếu đợt không còn nhóm sống, `DRAFT` là mặc định an toàn vì không tự bịa tiến trình. Nếu còn nhóm sống, khôi phục không-mục-tiêu bị `409 ROUND_RESTORE_TARGET_REQUIRED`, và mục tiêu tường minh phải từ `REGISTRATION_OPEN` trở đi — nhóm sống chứng minh đợt đã mở đăng ký; giao diện quản trị mở bộ chọn mục tiêu đúng lúc này. Khôi phục bị từ chối `409 ROUND_RESTORE_MEMBER_CONFLICT` khi có sinh viên của đợt đã giữ suất đang hiệu lực ở đợt khác, và `409 ROUND_RESTORE_FROZEN_HISTORY` khi đợt chứa nhóm lịch sử đã đóng băng theo V113. Đợt `CANCELLED` không tính là suất tham gia: sinh viên của đợt đó được tự do tham gia đợt khác, và trang tiến độ báo rõ đợt đã bị hủy thay vì trạng thái tham gia sai.

Khôi phục khóa dòng đợt trước, rồi khóa các dòng `Student` của thành viên đang tham gia theo thứ tự ID, sau đó mới đếm xung đột suất — đúng thứ tự đợt→sinh viên mà tạo/thêm nhóm đi theo, nên không vòng khóa chết; một đăng ký đang chạy hoặc kết thúc trước (và bị đếm) hoặc chờ đến khi đợt sống lại rồi nhìn thấy trạng thái mới.

## Xóa đợt tạo nhầm

`DELETE /api/v1/thesis/rounds/{id}` (thân `{ "reason": "..." }`, cùng vai trò được phép hủy) xóa hẳn một đợt tạo nhầm. Chỉ đợt RỖNG mới được xóa: trạng thái phải là `DRAFT` hoặc `CANCELLED` (`409 ROUND_NOT_DELETABLE` cho mọi trạng thái khác) và mọi bảng tham chiếu `round_id` — đề tài, nhóm, thành viên, báo cáo, hội đồng — phải bằng không (`409 ROUND_HAS_DATA` kèm `details` đếm theo loại). Đợt đã có dữ liệu vẫn đi đường hủy/khôi phục, vì hủy giữ lại lịch sử.

Xóa khóa dòng đợt `FOR UPDATE` trước khi đếm, cùng thứ tự khóa đợt-trước với các mutation đợt khác: một tạo dữ liệu đồng thời hoặc commit trước (và bị đếm) hoặc chờ khóa rồi gặp `ROUND_NOT_FOUND`, không bao giờ để lại dòng mồ côi. Lý do là bắt buộc như mọi thao tác quản trị; dòng audit `THESIS_ROUND_DELETED` ghi trong cùng giao dịch trước khi xóa với snapshot tên/trạng thái và số liệu đã kiểm tra. Đợt không tồn tại trả `404 ROUND_NOT_FOUND`. Mô hình đọc trả `deletion` (cờ `deletable` + đếm theo loại) ngay trong `GET /api/v1/thesis/rounds` để thẻ đợt hiển thị đúng mà không cần gọi thêm.
