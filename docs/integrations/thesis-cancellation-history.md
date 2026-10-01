# Hủy nhóm khóa luận và lịch sử thành viên

Khi nhóm chuyển sang `CANCELLED`, hệ thống giữ nguyên mã nhóm, mã thành viên và danh sách thành viên cũ. Nhóm đã hủy không thể mở lại hoặc chỉnh sửa danh sách thành viên. Sinh viên có thể tạo hoặc tham gia nhóm mới khi đợt đăng ký còn cho phép; kiểm tra một nhóm đang hoạt động vẫn áp dụng.

Flyway `V91__release_canceled_thesis_memberships.sql` bổ sung `active_participation` và thay ràng buộc duy nhất toàn bộ lịch sử bằng chỉ mục duy nhất `(round_id, student_id)` cho các dòng đang tham gia. PostgreSQL tự suy ra dấu tham gia từ trạng thái nhóm, giải phóng dấu này trong cùng giao dịch hủy nhóm và khóa nhóm trước thao tác thành viên. Khi đổi nhóm, các nhóm được khóa theo thứ tự ID cố định. Các ràng buộc số lượng thành viên, trưởng nhóm và đợt đăng ký của V65 tiếp tục áp dụng.

API khóa hồ sơ sinh viên trước kiểm tra và tạo/thêm thành viên để tuần tự hóa quyền tham gia giữa các đợt đang hoạt động. Chỉ mục DB có phạm vi một đợt; thao tác SQL trực tiếp giữa nhiều đợt không thay thế các kiểm tra nghiệp vụ của API.

Nâng cấp bằng cơ chế Flyway hiện có. Sao lưu DB theo quy trình vận hành trước khi áp dụng ở môi trường thật. Không sửa checksum, xóa lịch sử Flyway hoặc chạy `clean` để bỏ qua migration. Sau khi đã có thành viên tham gia lại, khôi phục ràng buộc cũ sẽ xung đột với lịch sử được giữ; sửa lỗi migration đã áp dụng bằng migration tiếp theo.

Bản tương thích H2 dùng cột sinh nullable được tạo từ dấu tham gia và ràng buộc duy nhất; kiểm thử PostgreSQL kiểm chứng trigger và lịch khóa thật. Không suy luận độ an toàn đồng thời của PostgreSQL từ H2.
