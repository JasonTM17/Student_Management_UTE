# Hủy nhóm khóa luận và lịch sử thành viên

Khi nhóm chuyển sang `CANCELLED`, hệ thống giữ nguyên mã nhóm, mã thành viên và danh sách thành viên cũ. Nhóm đã hủy không thể mở lại hoặc chỉnh sửa danh sách thành viên. Sinh viên có thể tạo hoặc tham gia nhóm mới khi đợt đăng ký còn cho phép; kiểm tra một nhóm đang hoạt động vẫn áp dụng.

Flyway `V91__release_canceled_thesis_memberships.sql` bổ sung `active_participation` và thay ràng buộc duy nhất toàn bộ lịch sử bằng chỉ mục duy nhất `(round_id, student_id)` cho các dòng đang tham gia. PostgreSQL tự suy ra dấu tham gia từ trạng thái nhóm, giải phóng dấu này trong cùng giao dịch hủy nhóm và khóa nhóm trước thao tác thành viên. Khi đổi nhóm, các nhóm được khóa theo thứ tự ID cố định. Các ràng buộc số lượng thành viên, trưởng nhóm và đợt đăng ký của V65 tiếp tục áp dụng.

API khóa hồ sơ sinh viên trước kiểm tra và tạo/thêm thành viên để tuần tự hóa quyền tham gia giữa các đợt đang hoạt động. Chỉ mục DB có phạm vi một đợt; thao tác SQL trực tiếp giữa nhiều đợt không thay thế các kiểm tra nghiệp vụ của API.

Nâng cấp bằng cơ chế Flyway hiện có. Sao lưu DB theo quy trình vận hành trước khi áp dụng ở môi trường thật. Không sửa checksum, xóa lịch sử Flyway hoặc chạy `clean` để bỏ qua migration. Sau khi đã có thành viên tham gia lại, khôi phục ràng buộc cũ sẽ xung đột với lịch sử được giữ; sửa lỗi migration đã áp dụng bằng migration tiếp theo.

Bản tương thích H2 dùng cột sinh nullable được tạo từ dấu tham gia và ràng buộc duy nhất; kiểm thử PostgreSQL kiểm chứng trigger và lịch khóa thật. Không suy luận độ an toàn đồng thời của PostgreSQL từ H2.

## Hủy và khôi phục đợt đăng ký

Hủy đợt chỉ đổi trạng thái dòng đợt sang `CANCELLED`: nhóm, thành viên, đề tài, báo cáo, hội đồng và điểm giữ nguyên làm lịch sử. Flyway `V139__thesis_round_cancelled_from_status.sql` thêm `cancelled_from_status` để ghi lại trạng thái ngay trước khi hủy; `POST /api/v1/thesis/rounds/{id}/restore` (cùng vai trò được phép hủy) đưa đợt về đúng trạng thái đó, hoặc về `targetStatus` hợp lệ do quản trị viên chọn trong số các trạng thái còn sống. Cả hủy và khôi phục đều tăng `version` và ghi dòng audit.

Đợt bị hủy trước khi có cột này mang `cancelled_from_status = NULL` và được khôi phục về `DRAFT` — trạng thái duy nhất không tự bịa tiến trình; quản trị viên đẩy đợt đi tiếp bằng chuỗi chuyển trạng thái thường. Khôi phục bị từ chối `409 ROUND_RESTORE_MEMBER_CONFLICT` khi có sinh viên của đợt đã giữ suất đang hiệu lực ở đợt khác, và `409 ROUND_RESTORE_FROZEN_HISTORY` khi đợt chứa nhóm lịch sử đã đóng băng theo V113. Đợt `CANCELLED` không tính là suất tham gia: sinh viên của đợt đó được tự do tham gia đợt khác, và trang tiến độ báo rõ đợt đã bị hủy thay vì trạng thái tham gia sai.
