-- Migration 20260919200000_populate_production_engagement_rich_data.sql
-- 1. Xóa sạch 5 bản ghi thử nghiệm/rác và các bản ghi phụ thuộc
DELETE FROM engagement."AnnouncementAudit"
WHERE "announcementId" IN (
    '0967096b-c6fa-46e6-a724-1221059b659e',
    'fd12ca0d-73d9-41dc-b74d-ba28c721074c',
    '95b7d429-c5e3-461c-86ad-60953fd23011',
    'eb045319-fe4d-41aa-af55-add2c039af69',
    '145f17d9-0f5a-43c7-b143-4cb2c33bdb43'
);

DELETE FROM engagement."Announcement"
WHERE "id" IN (
    '0967096b-c6fa-46e6-a724-1221059b659e',
    'fd12ca0d-73d9-41dc-b74d-ba28c721074c',
    '95b7d429-c5e3-461c-86ad-60953fd23011',
    'eb045319-fe4d-41aa-af55-add2c039af69',
    '145f17d9-0f5a-43c7-b143-4cb2c33bdb43'
);

-- 2. Chuẩn hóa toàn bộ các thông báo học vụ cũ đang thiếu chuyên mục, slug và ảnh bìa
-- A. Đào tạo & Học vụ (cat-academic-affairs)
UPDATE engagement."Announcement"
SET "categoryId" = 'cat-academic-affairs',
    "coverImageUrl" = '/images/news/course-registration.jpg',
    "summary" = 'Kế hoạch năm học 2026-2027 và định hướng chiến lược đào tạo của Trường Đại học Công nghệ Kỹ thuật TP.HCM.',
    "slug" = 'quyet-dinh-hieu-truong-ke-hoach-nam-hoc-2026-2027',
    "readingTimeMinutes" = 4,
    "viewCount" = 4250,
    "uniqueReaderCount" = 3680
WHERE "id" = 'announcement-rector-new-academic-year-decision';

UPDATE engagement."Announcement"
SET "categoryId" = 'cat-academic-affairs',
    "coverImageUrl" = '/images/news/course-registration.jpg',
    "summary" = 'Quy định khung thời gian điều chỉnh và rút bớt học phần trong học kỳ chính.',
    "slug" = 'thoi-gian-dieu-chinh-dang-ky-hoc-phan',
    "readingTimeMinutes" = 3,
    "viewCount" = 2840,
    "uniqueReaderCount" = 2410
WHERE "id" = 'announcement-add-drop-window';

UPDATE engagement."Announcement"
SET "categoryId" = 'cat-academic-affairs',
    "coverImageUrl" = '/images/news/campus-digital-library.jpg',
    "summary" = 'Danh mục học phần chính thức Học kỳ 1 đã được cập nhật đầy đủ mã lớp và giảng viên giảng dạy.',
    "slug" = 'danh-muc-hoc-phan-hoc-ky-1-cap-nhat',
    "readingTimeMinutes" = 3,
    "viewCount" = 2310,
    "uniqueReaderCount" = 1980
WHERE "id" = 'announcement-catalog-enriched';

UPDATE engagement."Announcement"
SET "categoryId" = 'cat-academic-affairs',
    "coverImageUrl" = '/images/news/campus-digital-library.jpg',
    "summary" = 'Bổ sung đề cương chi tiết, chuẩn đầu ra môn học và tài liệu tham khảo cho 100 học phần.',
    "slug" = 'bo-sung-mo-ta-de-cuong-100-hoc-phan',
    "readingTimeMinutes" = 4,
    "viewCount" = 2150,
    "uniqueReaderCount" = 1820
WHERE "id" = 'announcement-course-descriptions';

UPDATE engagement."Announcement"
SET "categoryId" = 'cat-academic-affairs',
    "coverImageUrl" = '/images/news/course-registration.jpg',
    "summary" = 'Lịch kiểm tra và đánh giá học phần giữa học kỳ 1 năm học 2026-2027.',
    "slug" = 'lich-kiem-tra-danh-gia-giua-ky-du-kien',
    "readingTimeMinutes" = 3,
    "viewCount" = 3520,
    "uniqueReaderCount" = 3100
WHERE "id" = 'announcement-exam-preparation';

UPDATE engagement."Announcement"
SET "categoryId" = 'cat-academic-affairs',
    "coverImageUrl" = '/images/news/course-registration.jpg',
    "summary" = 'Quy định về thời hạn nhập điểm thành phần và quy trình nộp đơn phúc khảo trực tuyến.',
    "slug" = 'quy-dinh-nhap-diem-va-phuc-khao-hoc-phan',
    "readingTimeMinutes" = 3,
    "viewCount" = 3180,
    "uniqueReaderCount" = 2750
WHERE "id" = 'announcement-grade-policy';

UPDATE engagement."Announcement"
SET "categoryId" = 'cat-academic-affairs',
    "coverImageUrl" = '/images/news/course-registration.jpg',
    "summary" = 'Sinh viên lưu ý kiểm tra học phần tiên quyết và học phần học trước trước khi đăng ký lớp môn học.',
    "slug" = 'nhac-nho-kiem-tra-hoc-phan-tien-quyet-khi-dang-ky',
    "readingTimeMinutes" = 2,
    "viewCount" = 2640,
    "uniqueReaderCount" = 2290
WHERE "id" = 'announcement-prerequisite-check';

UPDATE engagement."Announcement"
SET "categoryId" = 'cat-academic-affairs',
    "coverImageUrl" = '/images/news/course-registration.jpg',
    "summary" = 'Hướng dẫn chi tiết quy trình chọn lớp học phần và sắp xếp thời khóa biểu không bị trùng lịch.',
    "slug" = 'huong-dan-chon-lop-hoc-phan-va-dang-ky-thoi-khoa-bieu',
    "readingTimeMinutes" = 3,
    "viewCount" = 4150,
    "uniqueReaderCount" = 3720
WHERE "id" = 'announcement-registration-guide';

UPDATE engagement."Announcement"
SET "categoryId" = 'cat-academic-affairs',
    "coverImageUrl" = '/images/news/course-registration.jpg',
    "summary" = 'Công bố thời khóa biểu chính thức Học kỳ 1 cho toàn thể sinh viên các khóa.',
    "slug" = 'thoi-khoa-bieu-chinh-thuc-hoc-ky-1-nam-hoc-moi',
    "readingTimeMinutes" = 3,
    "viewCount" = 5680,
    "uniqueReaderCount" = 4920
WHERE "id" = 'announcement-schedule-published';

UPDATE engagement."Announcement"
SET "categoryId" = 'cat-academic-affairs',
    "coverImageUrl" = '/images/news/course-registration.jpg',
    "summary" = 'Chào đón tân sinh viên và thông báo mở đợt đăng ký học phần Học kỳ 1 năm học 2026-2027.',
    "slug" = 'chao-mung-nam-hoc-moi-va-thong-bao-dang-ky-hoc-phan',
    "readingTimeMinutes" = 3,
    "viewCount" = 4820,
    "uniqueReaderCount" = 4130
WHERE "id" = 'announcement-welcome';

UPDATE engagement."Announcement"
SET "categoryId" = 'cat-academic-affairs',
    "coverImageUrl" = '/images/news/course-registration.jpg',
    "summary" = 'Thông báo rà soát kết quả học tập, phân loại học vụ và tổ chức tư vấn học vụ dành cho sinh viên.',
    "slug" = 'thong-bao-ra-soat-ket-qua-hoc-tap-canh-bao-hoc-vu',
    "readingTimeMinutes" = 4,
    "viewCount" = 3280,
    "uniqueReaderCount" = 2850
WHERE "id" = 'announcement-ute-academic-warning-counseling';

UPDATE engagement."Announcement"
SET "categoryId" = 'cat-academic-affairs',
    "coverImageUrl" = '/images/news/campus-digital-library.jpg',
    "summary" = 'Cổng Dịch vụ công sinh viên một cửa cấp bảng điểm điện tử có gắn chữ ký số hợp lệ.',
    "slug" = 'trien-khai-dich-vu-cong-cap-bang-diem-chu-ky-so',
    "readingTimeMinutes" = 3,
    "viewCount" = 4720,
    "uniqueReaderCount" = 4120
WHERE "id" = 'announcement-ute-digital-transcript-signature';

UPDATE engagement."Announcement"
SET "categoryId" = 'cat-academic-affairs',
    "coverImageUrl" = '/images/news/course-registration.jpg',
    "summary" = 'Kế hoạch sát hạch chuẩn đầu ra Ngoại ngữ (TOEIC/IELTS) và Tin học quốc tế đợt 1 năm học 2026-2027.',
    "slug" = 'ke-hoach-kiem-tra-chuan-dau-ra-ngoai-ngu-tin-hoc',
    "readingTimeMinutes" = 4,
    "viewCount" = 3940,
    "uniqueReaderCount" = 3450
WHERE "id" = 'announcement-ute-english-it-exit-benchmark';

UPDATE engagement."Announcement"
SET "categoryId" = 'cat-academic-affairs',
    "coverImageUrl" = '/images/news/course-registration.jpg',
    "summary" = 'Quy chế thi kết thúc học phần, sơ đồ phòng thi và biểu mẫu nộp đơn phúc khảo trực tuyến.',
    "slug" = 'quy-che-thi-ket-thuc-hoc-phan-va-lich-thi',
    "readingTimeMinutes" = 4,
    "viewCount" = 5120,
    "uniqueReaderCount" = 4580
WHERE "id" = 'announcement-ute-exam-regulations-schedule';

UPDATE engagement."Announcement"
SET "categoryId" = 'cat-academic-affairs',
    "coverImageUrl" = '/images/news/course-registration.jpg',
    "summary" = 'Lịch thi chính thức và các quy định an ninh, giám sát phòng thi bằng thẻ sinh viên thông minh.',
    "slug" = 'lich-thi-ket-thuc-hoc-phan-an-ninh-phong-thi',
    "readingTimeMinutes" = 3,
    "viewCount" = 4280,
    "uniqueReaderCount" = 3820
WHERE "id" = 'announcement-ute-final-exam-regulations-2026';

UPDATE engagement."Announcement"
SET "categoryId" = 'cat-academic-affairs',
    "coverImageUrl" = '/images/news/faculty-excellence-awards.jpg',
    "summary" = 'Kế hoạch phân công cán bộ coi thi và quy định thời hạn gửi đề thi trích lục về Phòng Khảo thí.',
    "slug" = 'ke-hoach-phan-cong-can-bo-coi-thi-hoc-ky-1',
    "readingTimeMinutes" = 3,
    "viewCount" = 1860,
    "uniqueReaderCount" = 1590
WHERE "id" = 'announcement-ute-lecturer-exam-proctoring';

UPDATE engagement."Announcement"
SET "categoryId" = 'cat-academic-affairs',
    "coverImageUrl" = '/images/news/faculty-excellence-awards.jpg',
    "summary" = 'Rà soát chuẩn đầu ra môn học và ma trận kỹ năng đáp ứng tiêu chuẩn kiểm định ABET quốc tế.',
    "slug" = 'huong-dan-ra-soat-de-cuong-hoc-phan-chuan-abet',
    "readingTimeMinutes" = 4,
    "viewCount" = 2240,
    "uniqueReaderCount" = 1910
WHERE "id" = 'announcement-ute-lecturer-syllabus-review';

UPDATE engagement."Announcement"
SET "categoryId" = 'cat-academic-affairs',
    "coverImageUrl" = '/images/news/course-registration.jpg',
    "summary" = 'Thông báo hạn chót đóng học phí và các cổng thanh toán ngân hàng trực tuyến VietQR/VNPAY.',
    "slug" = 'thong-bao-thu-hoc-phi-va-kenh-thanh-toan-truc-tuyen',
    "readingTimeMinutes" = 3,
    "viewCount" = 6450,
    "uniqueReaderCount" = 5820
WHERE "id" = 'announcement-ute-tuition-payment-notice';

UPDATE engagement."Announcement"
SET "categoryId" = 'cat-academic-affairs',
    "coverImageUrl" = '/images/news/course-registration.jpg',
    "summary" = 'Nhắc nhở thời hạn nộp đơn phúc khảo kết quả thi học kỳ qua cổng đào tạo trực tuyến.',
    "slug" = 'nhac-lich-nop-don-phuc-khao-diem-hoc-phan',
    "readingTimeMinutes" = 2,
    "viewCount" = 1920,
    "uniqueReaderCount" = 1680
WHERE "id" = 'announcement-v26-academic-warning';

UPDATE engagement."Announcement"
SET "categoryId" = 'cat-academic-affairs',
    "coverImageUrl" = '/images/news/faculty-excellence-awards.jpg',
    "summary" = 'Thông báo thời hạn khóa sổ điểm thành phần và điểm quá trình trên hệ thống học vụ.',
    "slug" = 'thoi-han-khoa-so-diem-giua-ky-giang-vien',
    "readingTimeMinutes" = 2,
    "viewCount" = 1750,
    "uniqueReaderCount" = 1490
WHERE "id" = 'announcement-v26-gradebook-window';

UPDATE engagement."Announcement"
SET "categoryId" = 'cat-academic-affairs',
    "coverImageUrl" = '/images/news/faculty-excellence-awards.jpg',
    "summary" = 'Buổi tập huấn tính năng nhập điểm và xuất file bảng điểm trực tuyến cho giảng viên.',
    "slug" = 'tap-huan-su-dung-he-thong-nhap-diem-truc-tuyen',
    "readingTimeMinutes" = 3,
    "viewCount" = 1540,
    "uniqueReaderCount" = 1320
WHERE "id" = 'announcement-v26-lecturer-training';

-- B. Nghiên cứu & Công nghệ (cat-research-tech)
UPDATE engagement."Announcement"
SET "categoryId" = 'cat-research-tech',
    "coverImageUrl" = '/images/news/bigdata-ai-lab.jpg',
    "summary" = 'Nâng cấp mô hình ngôn ngữ và cơ sở tri thức học vụ cho Trợ lý ảo AI Assistant.',
    "slug" = 'tro-ly-hoc-vu-ai-nang-cap-co-so-tri-thuc',
    "readingTimeMinutes" = 3,
    "viewCount" = 2460,
    "uniqueReaderCount" = 2120
WHERE "id" = 'announcement-ai-rag';

UPDATE engagement."Announcement"
SET "categoryId" = 'cat-research-tech',
    "coverImageUrl" = '/images/news/bigdata-ai-lab.jpg',
    "summary" = 'Trợ lý AI hỗ trợ trích dẫn nguồn quy chế, điều khoản chính xác theo văn bản pháp quy của Nhà trường.',
    "slug" = 'tro-ly-ai-tich-hop-tinh-nang-trich-dan-nguon-quy-che',
    "readingTimeMinutes" = 3,
    "viewCount" = 2180,
    "uniqueReaderCount" = 1890
WHERE "id" = 'announcement-assistant-citations';

UPDATE engagement."Announcement"
SET "categoryId" = 'cat-research-tech',
    "coverImageUrl" = '/images/news/robotics-iot-lab.jpg',
    "summary" = 'Khoa Công nghệ Thông tin trang bị thêm 80 máy trạm đồ họa chuyên dụng cho phòng lab thực hành.',
    "slug" = 'nang-cap-trang-thiet-bi-phong-lab-khoa-cntt',
    "readingTimeMinutes" = 3,
    "viewCount" = 2780,
    "uniqueReaderCount" = 2390
WHERE "id" = 'announcement-v26-lab-upgrade';

UPDATE engagement."Announcement"
SET "categoryId" = 'cat-research-tech',
    "coverImageUrl" = '/images/news/bigdata-ai-lab.jpg',
    "summary" = 'Phòng thí nghiệm AI & Khoa học Dữ liệu tuyển sinh viên tham gia các dự án nghiên cứu quốc tế.',
    "slug" = 'tuyen-thanh-vien-nhom-nghien-cuu-tri-tue-nhan-tao',
    "readingTimeMinutes" = 3,
    "viewCount" = 3360,
    "uniqueReaderCount" = 2950
WHERE "id" = 'announcement-v26-research-group';

UPDATE engagement."Announcement"
SET "categoryId" = 'cat-research-tech',
    "coverImageUrl" = '/images/news/scientific-research.jpg',
    "summary" = 'Kế hoạch nghiệm thu các đề tài nghiên cứu khoa học cấp cơ sở và tính giờ chuẩn giảng dạy.',
    "slug" = 'ke-hoach-nghiem-thu-de-tai-nckh-cap-truong-2026-2027',
    "readingTimeMinutes" = 4,
    "viewCount" = 2520,
    "uniqueReaderCount" = 2170
WHERE "id" = 'announcement-ute-lecturer-research-norm';

UPDATE engagement."Announcement"
SET "categoryId" = 'cat-research-tech',
    "coverImageUrl" = '/images/news/bigdata-ai-lab.jpg',
    "summary" = 'Bảo trì máy chủ và tối ưu hóa hạ tầng cơ sở dữ liệu định kỳ vào ban đêm.',
    "slug" = 'ke-hoach-bao-tri-he-thong-may-chu-cong-hoc-vu',
    "readingTimeMinutes" = 2,
    "viewCount" = 1940,
    "uniqueReaderCount" = 1690
WHERE "id" = 'announcement-maintenance-window';

UPDATE engagement."Announcement"
SET "categoryId" = 'cat-research-tech',
    "coverImageUrl" = '/images/news/bigdata-ai-lab.jpg',
    "summary" = 'Nâng cấp cụm máy chủ ảo hóa và tăng cường băng thông cho cổng thông tin đào tạo HCM-UTE.',
    "slug" = 'lich-bao-tri-nang-cap-cum-may-chu-cong-dao-tao-ute',
    "readingTimeMinutes" = 3,
    "viewCount" = 2270,
    "uniqueReaderCount" = 1980
WHERE "id" = 'announcement-ute-system-upgrade-maintenance';

-- C. Cơ hội Việc làm (cat-career-opps)
UPDATE engagement."Announcement"
SET "categoryId" = 'cat-career-opps',
    "coverImageUrl" = '/images/news/tech-career-expo.jpg',
    "summary" = 'Kế hoạch tổ chức thực tập doanh nghiệp (OJT) và đồ án tốt nghiệp liên kết doanh nghiệp Học kỳ 1.',
    "slug" = 'ke-hoach-thuc-tap-doanh-nghiep-ojt-ky-1-2026-2027',
    "readingTimeMinutes" = 4,
    "viewCount" = 4860,
    "uniqueReaderCount" = 4210
WHERE "id" = 'announcement-ute-ojt-internship-semester1';

UPDATE engagement."Announcement"
SET "categoryId" = 'cat-career-opps',
    "coverImageUrl" = '/images/news/tech-career-expo.jpg',
    "summary" = 'Ngày hội tuyển dụng công nghệ với hơn 30 doanh nghiệp IT tham gia phỏng vấn sinh viên năm cuối.',
    "slug" = 'ngay-hoi-viec-lam-va-ket-noi-doanh-nghiep-khoa-cntt',
    "readingTimeMinutes" = 3,
    "viewCount" = 3980,
    "uniqueReaderCount" = 3490
WHERE "id" = 'announcement-v26-career-day';

-- D. Đời sống Sinh viên (cat-student-life)
UPDATE engagement."Announcement"
SET "categoryId" = 'cat-student-life',
    "coverImageUrl" = '/images/news/campus-digital-library.jpg',
    "summary" = 'Hoàn thiện cải tạo phòng học đa năng A101 với hệ thống máy chiếu và âm thanh tương tác cao.',
    "slug" = 'hoan-thanh-cai-tao-phong-hoc-thong-minh-a101',
    "readingTimeMinutes" = 2,
    "viewCount" = 1680,
    "uniqueReaderCount" = 1450
WHERE "id" = 'announcement-classroom-a101';

UPDATE engagement."Announcement"
SET "categoryId" = 'cat-student-life',
    "coverImageUrl" = '/images/news/campus-digital-library.jpg',
    "summary" = 'Hướng dẫn sinh viên khai thác cơ sở dữ liệu học thuật quốc tế IEEE Xplore, ScienceDirect.',
    "slug" = 'khai-thac-nguon-tai-nguyen-hoc-lieu-dien-tu-thu-vien-so',
    "readingTimeMinutes" = 3,
    "viewCount" = 2890,
    "uniqueReaderCount" = 2510
WHERE "id" = 'announcement-library-resources';

UPDATE engagement."Announcement"
SET "categoryId" = 'cat-student-life',
    "coverImageUrl" = '/images/news/campus-digital-library.jpg',
    "summary" = 'Kênh hỗ trợ giải đáp thủ tục học vụ, đăng ký giấy xác nhận và tiếp nhận ý kiến sinh viên.',
    "slug" = 'cac-kenh-tiep-nhan-va-giai-dap-thac-mac-hoc-vu-sinh-vien',
    "readingTimeMinutes" = 2,
    "viewCount" = 2140,
    "uniqueReaderCount" = 1860
WHERE "id" = 'announcement-support-channel';

UPDATE engagement."Announcement"
SET "categoryId" = 'cat-student-life',
    "coverImageUrl" = '/images/news/cultural-arts-gala.jpg',
    "summary" = 'Khởi động câu lạc bộ tiếng Anh chuyên ngành công nghệ IT English Club dành cho sinh viên.',
    "slug" = 'sinh-hoat-cau-lac-bo-tieng-anh-chuyen-nganh-it-english-club',
    "readingTimeMinutes" = 3,
    "viewCount" = 2470,
    "uniqueReaderCount" = 2150
WHERE "id" = 'announcement-v26-english-club';

UPDATE engagement."Announcement"
SET "categoryId" = 'cat-student-life',
    "coverImageUrl" = '/images/news/student-dormitory-campus.jpg',
    "summary" = 'Quy hoạch lại bãi giữ xe khu vực A nhằm đảm bảo trật tự và an toàn phòng chống cháy nổ.',
    "slug" = 'thong-bao-sap-xep-va-dieu-chinh-bai-xe-khu-vuc-a',
    "readingTimeMinutes" = 2,
    "viewCount" = 3120,
    "uniqueReaderCount" = 2740
WHERE "id" = 'announcement-v26-parking-notice';

-- E. Học bổng & Khen thưởng (cat-awards-honors)
UPDATE engagement."Announcement"
SET "categoryId" = 'cat-awards-honors',
    "coverImageUrl" = '/images/news/scholarship-ceremony.jpg',
    "summary" = 'Chương trình học bổng trao đổi học tập 1 năm tại các trường đại học đối tác ở Đức, Nhật Bản và Hàn Quốc.',
    "slug" = 'chuong-trinh-hoc-bong-trao-doi-sinh-vien-quoc-te-2026',
    "readingTimeMinutes" = 4,
    "viewCount" = 4920,
    "uniqueReaderCount" = 4310
WHERE "id" = 'announcement-v32-exchange-prog';
