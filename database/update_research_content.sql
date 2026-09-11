-- Update publishers from 'Phòng Khoa Học Công Nghệ & HTQT' to 'Khoa Công nghệ Thông tin & Phòng Đào Tạo'
UPDATE engagement."Announcement"
SET "publishedBy" = 'Khoa Công nghệ Thông tin & Phòng Đào Tạo'
WHERE "publishedBy" ILIKE '%Khoa Học Công Nghệ%' OR "publishedBy" ILIKE '%HTQT%';

UPDATE engagement."Announcement"
SET content = 'Căn cứ Kế hoạch hoạt động Khoa học và Công nghệ năm học 2026-2027, Ban Giám hiệu Trường Đại học Công nghệ Kỹ thuật TP.HCM (HCM-UTE) phối hợp cùng Khoa Công nghệ Thông tin và Phòng Đào tạo chính thức phát động **Giải thưởng Nghiên cứu Khoa học Sinh viên HCM-UTE năm học 2026-2027** với các nội dung trọng tâm sau:

### 1. Mục đích và Ý nghĩa
- Thúc đẩy phong trào nghiên cứu khoa học, tinh thần khởi nghiệp đổi mới sáng tạo trong toàn thể sinh viên Nhà trường.
- Phát hiện, ươm mầm các ý tưởng khoa học xuất sắc, định hướng phát triển thành đề tài cấp Bộ, bài báo công bố quốc tế (ISI/Scopus) và sản phẩm công nghệ ứng dụng.

### 2. Lĩnh vực nghiên cứu ưu tiên
- **Trí tuệ nhân tạo & Dữ liệu lớn (AI / Big Data)**: Xử lý dữ liệu lớn, Machine Learning, Computer Vision, Hệ thống thông minh và IoT.
- **Vi mạch & Bán dẫn (Semiconductor)**: Thiết kế vi mạch số, công nghệ kiểm thử và đóng gói chip.
- **Robot & Tự động hóa**: Cánh tay robot công nghiệp, phương tiện tự hành AGV, số hóa điều khiển thông minh.
- **Năng lượng tái tạo & Chuyển đổi xanh**: Công nghệ pin thế hệ mới, tiết kiệm năng lượng, giảm phát thải Carbon.

### 3. Cơ cấu giải thưởng và Quyền lợi
- **Tổng kinh phí giải thưởng và hỗ trợ NCKH**: **500.000.000 VNĐ**.
- **01 Giải Nhất Toàn trường**: 30.000.000 VNĐ + Bằng khen của Hiệu trưởng PGS. TS. Lê Hiếu Giang + Học bổng tham dự Hội nghị Khoa học Quốc tế.
- **03 Giải Nhì**: 15.000.000 VNĐ/giải + Giấy khen của Hiệu trưởng.
- **05 Giải Ba**: 8.000.000 VNĐ/giải + Giấy khen của Hiệu trưởng.
- **10 Giải Khuyến khích**: 3.000.000 VNĐ/giải.
- Tất cả thành viên nhóm tham gia nghiệm thu đạt yêu cầu được **cộng 10 - 15 điểm rèn luyện (ĐRL)** và ưu tiên xét học bổng khuyến khích học tập.

### 4. Thời hạn và Hình thức đăng ký
- **Thời gian tiếp nhận hồ sơ đề cương**: Từ ngày thông báo đến hết ngày **30/10/2026**.
- **Hình thức nộp hồ sơ**: Nhóm sinh viên nộp Thuyết minh đề tài (theo mẫu R-01/HCM-UTE) trực tiếp tại Văn phòng Khoa quản lý và đồng thời số hóa nộp qua Cổng quản lý NCKH trực tuyến.
- Mọi thắc mắc vui lòng liên hệ: Văn phòng Khoa Công nghệ Thông tin & Phòng Đào tạo (Tòa nhà Trung tâm, HCM-UTE).'
WHERE id = 'announcement-v32-ute-research';