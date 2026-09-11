-- V32__enrich_big_data_campus_ecosystem.sql
-- Purpose: Expand the academic ecosystem into a comprehensive campus database ("Big Data").
-- Adds 5 major faculties, 8 specialized departments, 12 modern classrooms/labs/halls,
-- 13 distinguished faculty professors/lecturers across engineering & sciences,
-- 25 advanced thesis research topics, 4 specialized defense councils, and high-impact announcements.
-- Safe, idempotent, synthetic demo rows.

-- ------------------------------------------------------------------ 1. FACULTIES
INSERT INTO academic."Faculty" ("id", "name", "nameEn", "nameVi", "code", "description")
SELECT seed.id, seed.name, seed.name_en, seed.name_vi, seed.code, seed.description
FROM (VALUES
    ('faculty-feee', 'Khoa Điện - Điện tử', 'Faculty of Electrical & Electronics Engineering', 'Khoa Điện - Điện tử', 'FEEE', 'Khoa Điện - Điện tử ĐH Công nghệ Kỹ thuật TP.HCM'),
    ('faculty-fme', 'Khoa Cơ khí Chế tạo máy', 'Faculty of Mechanical Engineering', 'Khoa Cơ khí Chế tạo máy', 'FME', 'Khoa Cơ khí Chế tạo máy ĐH Công nghệ Kỹ thuật TP.HCM'),
    ('faculty-foe', 'Khoa Kinh tế', 'Faculty of Economics', 'Khoa Kinh tế', 'FOE', 'Khoa Kinh tế & Quản lý ĐH Công nghệ Kỹ thuật TP.HCM'),
    ('faculty-ffl', 'Khoa Ngoại ngữ', 'Faculty of Foreign Languages', 'Khoa Ngoại ngữ', 'FFL', 'Khoa Ngoại ngữ ĐH Công nghệ Kỹ thuật TP.HCM'),
    ('faculty-fce', 'Khoa Xây dựng', 'Faculty of Civil Engineering', 'Khoa Xây dựng', 'FCE', 'Khoa Xây dựng ĐH Công nghệ Kỹ thuật TP.HCM')
) AS seed(id, name, name_en, name_vi, code, description)
WHERE NOT EXISTS (
    SELECT 1 FROM academic."Faculty" existing WHERE existing."id" = seed.id
);

-- ------------------------------------------------------------------ 2. DEPARTMENTS
INSERT INTO academic."Department" ("id", "name", "nameEn", "nameVi", "code", "facultyId", "chair", "building", "email", "phone")
SELECT seed.id, seed.name, seed.name_en, seed.name_vi, seed.code, seed.faculty_id, seed.chair, seed.building, seed.email, seed.phone
FROM (VALUES
    ('department-feee-auto', 'Kỹ thuật Tự động hóa', 'Automation Engineering', 'Kỹ thuật Tự động hóa', 'AUTO', 'faculty-feee', 'PGS.TS. Trịnh Quốc Tuấn', 'Tòa nhà B1', 'tudonghoa@ute.demo.edu.vn', '028-3722-1201'),
    ('department-feee-telecom', 'Điện tử Viễn thông & IoT', 'Electronics & Telecommunications', 'Điện tử Viễn thông & IoT', 'ETEC', 'faculty-feee', 'TS. Lê Hoàng Nam', 'Tòa nhà B1', 'vienthong@ute.demo.edu.vn', '028-3722-1202'),
    ('department-fme-robotics', 'Cơ điện tử & Kỹ thuật Robot', 'Mechatronics & Robotics', 'Cơ điện tử & Kỹ thuật Robot', 'ROBOT', 'faculty-fme', 'PGS.TS. Nguyễn Văn Hùng', 'Tòa nhà C2', 'codientu@ute.demo.edu.vn', '028-3722-1301'),
    ('department-fme-automotive', 'Kỹ thuật Ô tô & Động lực', 'Automotive Engineering', 'Kỹ thuật Ô tô & Động lực', 'AUTO-ENG', 'faculty-fme', 'TS. Võ Minh Khang', 'Xưởng Động lực E2', 'oto@ute.demo.edu.vn', '028-3722-1302'),
    ('department-foe-mis', 'Hệ thống thông tin quản lý & TMĐT', 'Management Information Systems', 'HTTT Quản lý & TMĐT', 'MIS', 'faculty-foe', 'TS. Phan Thị Diệu Linh', 'Tòa nhà Trung tâm A1', 'htttql@ute.demo.edu.vn', '028-3722-1401'),
    ('department-foe-finance', 'Tài chính - Ngân hàng & FinTech', 'Finance & FinTech', 'Tài chính & FinTech', 'FIN', 'faculty-foe', 'ThS. Nguyễn Ngọc Tân', 'Tòa nhà Trung tâm A1', 'taichinh@ute.demo.edu.vn', '028-3722-1402'),
    ('department-ffl-business', 'Tiếng Anh Thương mại & Kỹ thuật', 'Business & Technical English', 'Tiếng Anh TM & KT', 'BTE', 'faculty-ffl', 'ThS. Vũ Thanh Mai', 'Tòa nhà Ngoại ngữ F3', 'tienganh@ute.demo.edu.vn', '028-3722-1501'),
    ('department-fce-civil', 'Kỹ thuật Xây dựng Công trình', 'Civil Engineering', 'Kỹ thuật Xây dựng', 'CIVIL', 'faculty-fce', 'TS. Đoàn Văn Long', 'Tòa nhà Xây dựng D1', 'xaydung@ute.demo.edu.vn', '028-3722-1601')
) AS seed(id, name, name_en, name_vi, code, faculty_id, chair, building, email, phone)
WHERE NOT EXISTS (
    SELECT 1 FROM academic."Department" existing WHERE existing."id" = seed.id
);

-- ------------------------------------------------------------------ 3. CLASSROOMS & LABS
INSERT INTO academic."Classroom" ("id", "building", "roomNumber", "capacity", "type")
SELECT seed.id, seed.building, seed.room_number, seed.capacity, seed.type
FROM (VALUES
    ('classroom-a1-201', 'A1', '201', 150, 'LECTURE'),
    ('classroom-a1-302', 'A1', '302', 120, 'LECTURE'),
    ('classroom-a1-405', 'A1', '405', 180, 'AUDITORIUM'),
    ('classroom-b2-101', 'B2', '101', 45, 'LAB'),
    ('classroom-b2-105', 'B2', '105', 50, 'LAB'),
    ('classroom-c1-301', 'C1', '301', 60, 'LAB'),
    ('classroom-c1-305', 'C1', '305', 60, 'LAB'),
    ('classroom-d3-201', 'D3', '201', 50, 'LAB'),
    ('classroom-e3-202', 'E3', '202', 40, 'WORKSHOP'),
    ('classroom-e3-204', 'E3', '204', 50, 'WORKSHOP'),
    ('classroom-f4-102', 'F4', '102', 80, 'SEMINAR'),
    ('classroom-f4-105', 'F4', '105', 80, 'SEMINAR')
) AS seed(id, building, room_number, capacity, type)
WHERE NOT EXISTS (
    SELECT 1 FROM academic."Classroom" existing WHERE existing."id" = seed.id
);

-- ------------------------------------------------------------------ 4. PROFESSORS & LECTURERS
INSERT INTO campuscore_auth."User" ("id", "email", "password", "firstName", "lastName", "status", "emailVerified")
SELECT seed.user_id, seed.email,
       (SELECT "password" FROM campuscore_auth."User" WHERE "id" = 'lecturer-user'),
       seed.first_name, seed.last_name, 'ACTIVE', TRUE
FROM (VALUES
    ('lecturer-user-013', 'tuan.tq@campuscore.demo', 'Trịnh Quốc', 'PGS.TS. Tuấn', 'ACTIVE'),
    ('lecturer-user-014', 'nam.lh@campuscore.demo', 'Lê Hoàng', 'TS. Nam', 'ACTIVE'),
    ('lecturer-user-015', 'hang.dtt@campuscore.demo', 'Đỗ Thị Thu', 'ThS. Hằng', 'ACTIVE'),
    ('lecturer-user-016', 'hung.nv@campuscore.demo', 'Nguyễn Văn', 'PGS.TS. Hùng', 'ACTIVE'),
    ('lecturer-user-017', 'khang.vm@campuscore.demo', 'Võ Minh', 'TS. Khang', 'ACTIVE'),
    ('lecturer-user-018', 'trong.td@campuscore.demo', 'Trần Đình', 'ThS. Trọng', 'ACTIVE'),
    ('lecturer-user-019', 'linh.ptd@campuscore.demo', 'Phan Thị Diệu', 'TS. Linh', 'ACTIVE'),
    ('lecturer-user-020', 'tan.nn@campuscore.demo', 'Nguyễn Ngọc', 'ThS. Tân', 'ACTIVE'),
    ('lecturer-user-021', 'mai.vt@campuscore.demo', 'Vũ Thanh', 'ThS. Mai', 'ACTIVE'),
    ('lecturer-user-022', 'long.dv@campuscore.demo', 'Đoàn Văn', 'TS. Long', 'ACTIVE'),
    ('lecturer-user-023', 'sang.ht@campuscore.demo', 'Huỳnh Thanh', 'PGS.TS. Sang', 'ACTIVE'),
    ('lecturer-user-024', 'thong.cm@campuscore.demo', 'Cao Minh', 'TS. Thông', 'ACTIVE'),
    ('lecturer-user-025', 'nguyet.lta@campuscore.demo', 'Lê Thị Ánh', 'ThS. Nguyệt', 'ACTIVE')
) AS seed(user_id, email, last_name, first_name, status)
WHERE NOT EXISTS (
    SELECT 1 FROM campuscore_auth."User" existing WHERE existing."id" = seed.user_id
);

INSERT INTO academic."Lecturer" ("id", "userId", "departmentId", "employeeId", "title", "specialization")
SELECT seed.profile_id, seed.user_id, seed.dept_id, seed.employee_id, seed.title, seed.specialization
FROM (VALUES
    ('lecturer-profile-013', 'lecturer-user-013', 'department-feee-auto', 'LEC-DEMO-013', 'PGS.TS.', 'Điều khiển tự động và Hệ thống nhúng công nghiệp'),
    ('lecturer-profile-014', 'lecturer-user-014', 'department-feee-telecom', 'LEC-DEMO-014', 'TS.', 'Truyền thông không dây 5G/6G và Mạng cảm biến IoT'),
    ('lecturer-profile-015', 'lecturer-user-015', 'department-feee-auto', 'LEC-DEMO-015', 'ThS.', 'PLC, SCADA và Tự động hóa quá trình sản xuất'),
    ('lecturer-profile-016', 'lecturer-user-016', 'department-fme-robotics', 'LEC-DEMO-016', 'PGS.TS.', 'Robot công nghiệp và Cơ điện tử thông minh'),
    ('lecturer-profile-017', 'lecturer-user-017', 'department-fme-automotive', 'LEC-DEMO-017', 'TS.', 'Hệ thống truyền lực xe điện và Xe tự hành'),
    ('lecturer-profile-018', 'lecturer-user-018', 'department-fme-robotics', 'LEC-DEMO-018', 'ThS.', 'Thiết kế CAD/CAM/CAE và Công nghệ CNC nâng cao'),
    ('lecturer-profile-019', 'lecturer-user-019', 'department-foe-mis', 'LEC-DEMO-019', 'TS.', 'Hệ thống ERP, Khai phá dữ liệu kinh doanh và TMĐT'),
    ('lecturer-profile-020', 'lecturer-user-020', 'department-foe-finance', 'LEC-DEMO-020', 'ThS.', 'FinTech, Định lượng tài chính và Quản trị rủi ro'),
    ('lecturer-profile-021', 'lecturer-user-021', 'department-ffl-business', 'LEC-DEMO-021', 'ThS.', 'Tiếng Anh chuyên ngành Kỹ thuật & Giao tiếp học thuật'),
    ('lecturer-profile-022', 'lecturer-user-022', 'department-fce-civil', 'LEC-DEMO-022', 'TS.', 'Kết cấu công trình, BIM và Quản lý dự án xây dựng'),
    ('lecturer-profile-023', 'lecturer-user-023', 'department-demo', 'LEC-DEMO-023', 'PGS.TS.', 'Hệ thống Dữ liệu lớn (Big Data) và Học máy ứng dụng'),
    ('lecturer-profile-024', 'lecturer-user-024', 'department-demo', 'LEC-DEMO-024', 'TS.', 'Trí tuệ Nhân tạo, Thị giác máy tính và Xử lý ảnh y tế'),
    ('lecturer-profile-025', 'lecturer-user-025', 'department-demo', 'LEC-DEMO-025', 'ThS.', 'An ninh mạng, Mật mã học và Hệ thống phân tán')
) AS seed(profile_id, user_id, dept_id, employee_id, title, specialization)
WHERE NOT EXISTS (
    SELECT 1 FROM academic."Lecturer" existing WHERE existing."id" = seed.profile_id
);

INSERT INTO campuscore_auth."UserRole" ("id", "userId", "roleId")
SELECT 'user-role-' || seed.profile_id, seed.user_id, 'role-lecturer'
FROM (VALUES
    ('lecturer-profile-013', 'lecturer-user-013'), ('lecturer-profile-014', 'lecturer-user-014'),
    ('lecturer-profile-015', 'lecturer-user-015'), ('lecturer-profile-016', 'lecturer-user-016'),
    ('lecturer-profile-017', 'lecturer-user-017'), ('lecturer-profile-018', 'lecturer-user-018'),
    ('lecturer-profile-019', 'lecturer-user-019'), ('lecturer-profile-020', 'lecturer-user-020'),
    ('lecturer-profile-021', 'lecturer-user-021'), ('lecturer-profile-022', 'lecturer-user-022'),
    ('lecturer-profile-023', 'lecturer-user-023'), ('lecturer-profile-024', 'lecturer-user-024'),
    ('lecturer-profile-025', 'lecturer-user-025')
) AS seed(profile_id, user_id)
WHERE NOT EXISTS (
    SELECT 1 FROM campuscore_auth."UserRole" existing
    WHERE existing."userId" = seed.user_id AND existing."roleId" = 'role-lecturer'
);

-- ------------------------------------------------------------------ 5. THESIS TOPICS (BIG DATA & ENGINEERING)
INSERT INTO thesis.thesis_topic (
    id, round_id, department_id, title, description, max_groups, status, created_by
)
SELECT seed.id::UUID, '22222222-2222-2222-2222-222222222101'::UUID, seed.dept_id, seed.title, seed.description, seed.max_groups, 'APPROVED', seed.created_by
FROM (VALUES
    ('44444444-4444-4444-4444-444444444001', 'department-demo', 'Hệ thống phân tích dữ liệu lớn thời gian thực hỗ trợ giám sát giao thông thông minh', 'Xây dựng luồng dữ liệu Stream Processing bằng Apache Kafka, Apache Spark Streaming và ClickHouse nhằm dự báo ùn tắc giao thông.', 3, 'lecturer-user-023'),
    ('44444444-4444-4444-4444-444444444002', 'department-feee-auto', 'Nền tảng dự báo phụ tải điện năng khu công nghiệp ứng dụng học sâu (Deep Learning & Smart Grid)', 'Thu thập dữ liệu cảm biến công tơ điện tử, áp dụng mô hình LSTM và Transformer dự báo nhu cầu phụ tải theo chu kỳ.', 2, 'lecturer-user-013'),
    ('44444444-4444-4444-4444-444444444003', 'department-fme-robotics', 'Thiết kế và chế tạo cánh tay Robot công nghiệp 6 bậc tự do điều khiển qua ROS 2', 'Nghiên cứu động học ngược, tích hợp thuật toán Motion Planning MoveIt2 và điều khiển vị trí chính xác trong dây chuyền gắp thả.', 3, 'lecturer-user-016'),
    ('44444444-4444-4444-4444-444444444004', 'department-demo', 'Hệ thống AI Camera nhận diện hành vi bất thường và cảnh báo an ninh tòa nhà', 'Ứng dụng YOLOv8 và DeepSORT theo dõi luồng người, phát hiện xâm nhập vùng cấm và kích hoạt cảnh báo tức thời.', 2, 'lecturer-user-024'),
    ('44444444-4444-4444-4444-444444444005', 'department-foe-mis', 'Ứng dụng Blockchain trong truy xuất nguồn gốc nông sản xuất khẩu chất lượng cao', 'Thiết kế Smart Contract trên nền Ethereum/Polygon ghi nhận nhật ký canh tác, đóng gói và vận chuyển minh bạch.', 3, 'lecturer-user-019'),
    ('44444444-4444-4444-4444-444444444006', 'department-foe-finance', 'Nền tảng FinTech vi mô tích hợp AI phân tích điểm tín dụng cá nhân (Credit Scoring)', 'Xây dựng pipeline máy học phân loại mức độ rủi ro tín dụng dựa trên hành vi giao dịch và mô hình XGBoost.', 2, 'lecturer-user-020'),
    ('44444444-4444-4444-4444-444444444007', 'department-feee-telecom', 'Hệ thống giám sát môi trường ao nuôi thủy sản thông minh ứng dụng mạng cảm biến LoRa', 'Thiết kế các nút cảm biến đo pH, oxy hòa tan, nhiệt độ truyền qua sóng LoRa khoảng cách 5km và hiển thị trên Dashboard.', 3, 'lecturer-user-014'),
    ('44444444-4444-4444-4444-444444444008', 'department-fme-automotive', 'Nghiên cứu và chế tạo xe tự hành AGV dẫn đường bằng thị giác máy tính và SLAM', 'Tích hợp LiDAR 2D và Camera RGB-D lập bản đồ thời gian thực, điều khiển bám quỹ đạo tự động tránh chướng ngại vật.', 2, 'lecturer-user-017'),
    ('44444444-4444-4444-4444-444444444009', 'department-demo', 'Xây dựng trợ lý ảo học thuật đa phương thức bằng mô hình ngôn ngữ lớn (LLM & RAG)', 'Xây dựng hệ thống tìm kiếm tăng cường bằng vector database Milvus/PgVector, truy vấn tài liệu đào tạo và giải đáp 24/7.', 3, 'lecturer-user'),
    ('44444444-4444-4444-4444-444444444010', 'department-demo', 'Hệ thống phát hiện tấn công mạng zero-day bằng Graph Neural Networks (GNN)', 'Mô hình hóa lưu lượng luồng mạng thành đồ thị cấu trúc, huấn luyện mô hình phát hiện bất thường với độ trễ thấp.', 2, 'lecturer-user-025'),
    ('44444444-4444-4444-4444-444444444011', 'department-foe-mis', 'Tối ưu hóa hành trình logistics giao hàng chặng cuối sử dụng giải thuật di truyền và Machine Learning', 'Giải quyết bài toán Vehicle Routing Problem có cửa sổ thời gian (VRPTW), giảm thiểu chi phí nhiên liệu đội xe giao hàng.', 2, 'lecturer-user-019'),
    ('44444444-4444-4444-4444-444444444012', 'department-demo', 'Phát triển ứng dụng Web 3.0 và hợp đồng thông minh cho cổng bình chọn điện tử', 'Xây dựng hệ thống bỏ phiếu phi tập trung đảm bảo tính ẩn danh của cử tri và tính toàn vẹn của kết quả bầu cử.', 2, 'lecturer-user-010'),
    ('44444444-4444-4444-4444-444444444013', 'department-demo', 'Nền tảng chẩn đoán sớm bệnh võng mạc tiểu đường qua ảnh đáy mắt bằng Vision Transformer', 'Phát triển mạng nơ-ron phân loại đa nhãn tổn thương vi mạch đáy mắt hỗ trợ bác sĩ nhãn khoa tuyến cơ sở.', 2, 'lecturer-user-024'),
    ('44444444-4444-4444-4444-444444444014', 'department-feee-auto', 'Thiết kế hệ thống điều khiển trạm sạc xe điện thông minh hòa lưới năng lượng mặt trời', 'Tối ưu hóa thuật toán sạc thích ứng theo biểu giá điện giờ cao điểm và dự báo công suất tấm pin quang điện.', 2, 'lecturer-user-015'),
    ('44444444-4444-4444-4444-444444444015', 'department-fme-robotics', 'Tự động hóa quy trình phân loại bưu kiện tốc độ cao kết hợp thị giác máy tính và khí nén', 'Thiết kế băng tải thông minh đọc mã vạch và định vị kích thước 3D kiện hàng, cơ cấu phân làn khí nén phản hồi 50ms.', 2, 'lecturer-user-018'),
    ('44444444-4444-4444-4444-444444444016', 'department-ffl-business', 'Phân tích cảm xúc mạng xã hội đa ngữ phục vụ đo lường thương hiệu trường đại học', 'Ứng dụng mô hình RoBERTa đa ngữ khai phá ý kiến sinh viên trên các diễn đàn học thuật, trực quan hóa xu hướng.', 2, 'lecturer-user-021'),
    ('44444444-4444-4444-4444-444444444017', 'department-fce-civil', 'Ứng dụng công nghệ Digital Twin trong giám sát sức khỏe kết cấu cầu nhịp lớn', 'Kết nối cảm biến độ võng, gia tốc với mô hình 3D BIM/Revit, cảnh báo rung chấn vượt ngưỡng an toàn.', 2, 'lecturer-user-022'),
    ('44444444-4444-4444-4444-444444444018', 'department-demo', 'Nền tảng đào tạo trực tuyến thích ứng cá nhân hóa với thuật toán Knowledge Tracing', 'Theo dõi tiến trình tiếp thu bài giảng của sinh viên qua các bài tập kiểm tra, gợi ý lộ trình ôn tập cá nhân hóa.', 3, 'lecturer-user-005'),
    ('44444444-4444-4444-4444-444444444019', 'department-demo', 'Hệ thống bảo mật dữ liệu y tế phân tán ứng dụng kỹ thuật Học liên kết (Federated Learning)', 'Huấn luyện mô hình trí tuệ nhân tạo trên dữ liệu bệnh án phân tán giữa các bệnh viện mà không cần chia sẻ dữ liệu thô.', 2, 'lecturer-user-025'),
    ('44444444-4444-4444-4444-444444444020', 'department-demo', 'Xây dựng kho dữ liệu Data Lakehouse và luồng xử lý ETL tự động cho hệ sinh thái đại học số', 'Kiến trúc Apache Iceberg kết hợp Trino và dbt xây dựng hệ thống báo cáo phân tích hoạt động sinh viên và giảng viên.', 3, 'lecturer-user-023'),
    ('44444444-4444-4444-4444-444444444021', 'department-feee-telecom', 'Thiết kế vi mạch tích hợp cho cảm biến MEMS đo dao động công nghiệp', 'Thiết kế mạch analog front-end khuyếch đại tín hiệu điện dung siêu nhỏ và giao tiếp chuẩn truyền thông I2C.', 2, 'lecturer-user-014'),
    ('44444444-4444-4444-4444-444444444022', 'department-fme-robotics', 'Phát triển thiết bị bay không người lái (UAV) phục vụ cứu hộ và trinh sát địa hình phức tạp', 'Tối ưu hóa thiết kế khung carbon, tích hợp hệ thống định vị GPS RTK độ chính xác cao và camera hồng ngoại tầm nhiệt.', 2, 'lecturer-user-016'),
    ('44444444-4444-4444-4444-444444444023', 'department-foe-mis', 'Nền tảng quản trị chuỗi cung ứng thông minh thời gian thực tích hợp RFID và GPS', 'Giám sát điều kiện bảo quản nhiệt độ, độ ẩm của kho lạnh vận chuyển vắc-xin và cảnh báo vi phạm quy chuẩn.', 2, 'lecturer-user-019'),
    ('44444444-4444-4444-4444-444444444024', 'department-demo', 'Hệ thống nhận dạng giọng nói tiếng Việt chuyên ngành y khoa hỗ trợ bác sĩ ghi hồ sơ bệnh án', 'Fine-tune mô hình Whisper với tập ngữ liệu hội thoại lâm sàng tiếng Việt, tự động chuẩn hóa văn bản y khoa.', 2, 'lecturer-user-011'),
    ('44444444-4444-4444-4444-444444444025', 'department-fme-automotive', 'Mô phỏng động lực học khí nén cho mô hình xe điện tiết kiệm năng lượng Shell Eco-marathon', 'Phân tích khí động học CFD trên Ansys Fluent, tối ưu hóa hệ số cản Cd và trọng lượng thân vỏ xe sợi carbon.', 2, 'lecturer-user-017')
) AS seed(id, dept_id, title, description, max_groups, created_by)
WHERE NOT EXISTS (
    SELECT 1 FROM thesis.thesis_topic existing WHERE existing.id = seed.id::UUID
);

-- ------------------------------------------------------------------ 6. THESIS DEFENSE COUNCILS
INSERT INTO thesis.thesis_council (id, round_id, name, status, created_by)
SELECT seed.id::UUID, '22222222-2222-2222-2222-222222222101'::UUID, seed.name, 'ACTIVE', 'admin-user'
FROM (VALUES
    ('55555555-5555-5555-5555-555555555001', 'Hội đồng 01: Khoa học Dữ liệu & Trí tuệ Nhân tạo'),
    ('55555555-5555-5555-5555-555555555002', 'Hội đồng 02: Hệ thống nhúng, IoT & Viễn thông'),
    ('55555555-5555-5555-5555-555555555003', 'Hội đồng 03: Cơ điện tử & Kỹ thuật Robot'),
    ('55555555-5555-5555-5555-555555555004', 'Hội đồng 04: Kỹ thuật Phần mềm & Hệ thống Thông tin')
) AS seed(id, name)
WHERE NOT EXISTS (
    SELECT 1 FROM thesis.thesis_council existing WHERE existing.id = seed.id::UUID
);

-- Council Members (Chair, Secretary, Member)
INSERT INTO thesis.thesis_council_member (id, council_id, lecturer_id, member_role)
SELECT seed.id::UUID, seed.council_id::UUID, seed.lecturer_id, seed.member_role
FROM (VALUES
    -- Council 1 (AI & Big Data)
    ('66666666-6666-6666-6666-666666666101', '55555555-5555-5555-5555-555555555001', 'lecturer-user-023', 'CHAIR'),
    ('66666666-6666-6666-6666-666666666102', '55555555-5555-5555-5555-555555555001', 'lecturer-user-024', 'SECRETARY'),
    ('66666666-6666-6666-6666-666666666103', '55555555-5555-5555-5555-555555555001', 'lecturer-user-003', 'MEMBER'),

    -- Council 2 (IoT & Telecom)
    ('66666666-6666-6666-6666-666666666201', '55555555-5555-5555-5555-555555555002', 'lecturer-user-013', 'CHAIR'),
    ('66666666-6666-6666-6666-666666666202', '55555555-5555-5555-5555-555555555002', 'lecturer-user-014', 'SECRETARY'),
    ('66666666-6666-6666-6666-666666666203', '55555555-5555-5555-5555-555555555002', 'lecturer-user-004', 'MEMBER'),

    -- Council 3 (Robotics & Mechatronics)
    ('66666666-6666-6666-6666-666666666301', '55555555-5555-5555-5555-555555555003', 'lecturer-user-016', 'CHAIR'),
    ('66666666-6666-6666-6666-666666666302', '55555555-5555-5555-5555-555555555003', 'lecturer-user-017', 'SECRETARY'),
    ('66666666-6666-6666-6666-666666666303', '55555555-5555-5555-5555-555555555003', 'lecturer-user-018', 'MEMBER'),

    -- Council 4 (Software & MIS)
    ('66666666-6666-6666-6666-666666666401', '55555555-5555-5555-5555-555555555004', 'lecturer-user-008', 'CHAIR'),
    ('66666666-6666-6666-6666-666666666402', '55555555-5555-5555-5555-555555555004', 'lecturer-user-019', 'SECRETARY'),
    ('66666666-6666-6666-6666-666666666403', '55555555-5555-5555-5555-555555555004', 'lecturer-user', 'MEMBER')
) AS seed(id, council_id, lecturer_id, member_role)
WHERE NOT EXISTS (
    SELECT 1 FROM thesis.thesis_council_member existing WHERE existing.id = seed.id::UUID
);

-- Assign topics to Councils
INSERT INTO thesis.thesis_council_topic (id, council_id, topic_id, assigned_by)
SELECT ('77777777-7777-7777-7777-' || lpad(seed.idx::text, 12, '0'))::UUID,
       seed.council_id::UUID,
       seed.topic_id::UUID,
       'admin-user'
FROM (VALUES
    (1, '55555555-5555-5555-5555-555555555001', '44444444-4444-4444-4444-444444444001'),
    (2, '55555555-5555-5555-5555-555555555001', '44444444-4444-4444-4444-444444444004'),
    (3, '55555555-5555-5555-5555-555555555001', '44444444-4444-4444-4444-444444444009'),
    (4, '55555555-5555-5555-5555-555555555001', '44444444-4444-4444-4444-444444444013'),
    (5, '55555555-5555-5555-5555-555555555002', '44444444-4444-4444-4444-444444444002'),
    (6, '55555555-5555-5555-5555-555555555002', '44444444-4444-4444-4444-444444444007'),
    (7, '55555555-5555-5555-5555-555555555002', '44444444-4444-4444-4444-444444444014'),
    (8, '55555555-5555-5555-5555-555555555003', '44444444-4444-4444-4444-444444444003'),
    (9, '55555555-5555-5555-5555-555555555003', '44444444-4444-4444-4444-444444444008'),
    (10, '55555555-5555-5555-5555-555555555003', '44444444-4444-4444-4444-444444444015'),
    (11, '55555555-5555-5555-5555-555555555004', '44444444-4444-4444-4444-444444444005'),
    (12, '55555555-5555-5555-5555-555555555004', '44444444-4444-4444-4444-444444444006')
) AS seed(idx, council_id, topic_id)
WHERE NOT EXISTS (
    SELECT 1 FROM thesis.thesis_council_topic existing WHERE existing.topic_id = seed.topic_id::UUID
);

-- ------------------------------------------------------------------ 7. INSTITUTIONAL ANNOUNCEMENTS
INSERT INTO engagement."Announcement"
    ("id", "title", "content", "priority", "targetRoles", "targetYears", "isGlobal",
     "publishAt", "expiresAt", "publishedBy", "semesterId", "semesterName", "courseCode", "courseName")
SELECT seed."id", seed."title", seed."content", seed."priority", seed."targetRoles", seed."targetYears", seed."isGlobal",
       CURRENT_TIMESTAMP - seed."age", CURRENT_TIMESTAMP + seed."ttl", 'admin-user',
       'semester-demo', 'Học kỳ 1 năm học 2026-2027', seed."courseCode", seed."courseName"
FROM (VALUES
    ('announcement-v32-ute-research', 'Phát động Giải thưởng Nghiên cứu Khoa học Sinh viên 2026-2027', 'Ban Giám hiệu trường ĐH Công nghệ Kỹ thuật TP.HCM phát động phong trào nghiên cứu khoa học với tổng giá trị giải thưởng 500 triệu đồng. Các nhóm sinh viên đăng ký đề tài tại văn phòng khoa trước ngày 30/10.', 'HIGH', ARRAY['STUDENT','LECTURER']::TEXT[], ARRAY[]::INTEGER[], TRUE, INTERVAL '1 hour', INTERVAL '180 days', NULL, NULL),
    ('announcement-v32-smart-campus', 'Khánh thành Hệ thống Phòng thí nghiệm Trí tuệ Nhân tạo & Dữ liệu lớn', 'Nhà trường chính thức đưa vào vận hành 04 phòng Lab hiện đại tại tòa nhà C1 và B2 với hạ tầng máy chủ GPU chuyên dụng hỗ trợ sinh viên học tập và nghiên cứu.', 'HIGH', ARRAY['STUDENT','LECTURER']::TEXT[], ARRAY[]::INTEGER[], TRUE, INTERVAL '12 hours', INTERVAL '120 days', NULL, NULL),
    ('announcement-v32-exchange-prog', 'Học bổng trao đổi sinh viên quốc tế tại Đức, Nhật Bản và Hàn Quốc', 'Trung tâm Hợp tác Quốc tế thông báo tuyển chọn 30 sinh viên xuất sắc các ngành Kỹ thuật và Công nghệ tham gia chương trình trao đổi 1 học kỳ có tài trợ toàn phần.', 'NORMAL', ARRAY['STUDENT']::TEXT[], ARRAY[3,4]::INTEGER[], TRUE, INTERVAL '2 days', INTERVAL '90 days', NULL, NULL),
    ('announcement-v32-job-fair', 'Ngày hội việc làm và Kết nối doanh nghiệp công nghệ UTE Career Expo', 'Hơn 60 tập đoàn công nghệ hàng đầu trong và ngoài nước sẽ có mặt trực tiếp tại sảnh tòa nhà A1 để tuyển dụng thực tập sinh và kỹ sư chính thức.', 'HIGH', ARRAY['STUDENT']::TEXT[], ARRAY[]::INTEGER[], TRUE, INTERVAL '4 days', INTERVAL '60 days', NULL, NULL)
) AS seed("id", "title", "content", "priority", "targetRoles", "targetYears", "isGlobal", "age", "ttl", "courseCode", "courseName")
WHERE NOT EXISTS (SELECT 1 FROM engagement."Announcement" existing WHERE existing."id" = seed."id");
