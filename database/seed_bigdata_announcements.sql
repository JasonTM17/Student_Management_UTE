-- Seed Comprehensive Big Data Academic Announcements for HCMUTE (CampusCore)
-- Covers Registration, Thesis, Scholarships, Exams, Career, AI/Big Data Lab, and Student Services

INSERT INTO engagement."Announcement" (
    id,
    title,
    content,
    priority,
    "targetRoles",
    "targetYears",
    "isGlobal",
    "publishAt",
    "publishedBy",
    "semesterId",
    "semesterName",
    "createdAt",
    "updatedAt",
    version
) VALUES
(
    'announcement-ute-bigdata-ai-center',
    'Khởi động Phòng Nghiên cứu Dữ liệu lớn (Big Data) & Trí tuệ Nhân tạo Khoa CNTT HCMUTE',
    '<p>Khoa Công nghệ Thông tin - Trường Đại học Sư phạm Kỹ thuật TP.HCM trân trọng thông báo đưa vào vận hành cụm máy chủ điện toán hiệu năng cao (HPC Cluster) phục vụ nghiên cứu Big Data và Trí tuệ nhân tạo:</p>
<ul>
  <li><strong>Hạ tầng kỹ thuật:</strong> Cụm máy chủ 8x NVIDIA A100 GPU Tensor Core, hệ thống lưu trữ phân tán Ceph 500TB và mạng InfiniBand 200Gbps.</li>
  <li><strong>Đối tượng khai thác:</strong> Toàn thể giảng viên, học viên cao học, nhóm nghiên cứu sinh viên (Lab AI & Data Science) và các đề tài Khóa luận tốt nghiệp chuyên sâu.</li>
  <li><strong>Quy trình đăng ký tài khoản:</strong> Sinh viên nộp đề cương nghiên cứu hoặc thư giới thiệu của Giảng viên hướng dẫn tại Văn phòng Bộ môn Kỹ thuật Dữ liệu (Phòng A1-402) trước ngày 30/09/2026.</li>
  <li><strong>Hội thảo đào tạo:</strong> Khóa huấn luyện "Xử lý dữ liệu lớn với Apache Spark & Distributed Deep Learning" diễn ra vào 08:30 thứ Bảy ngày 19/09/2026 tại Hội trường Lớn Khu A.</li>
</ul>
<p>Mọi chi tiết xin liên hệ PGS.TS. Trần Văn Bình - Trưởng nhóm nghiên cứu Big Data Lab (Email: binhtv@campuscore.edu).</p>',
    'HIGH',
    ARRAY['STUDENT', 'LECTURER', 'ADMIN']::text[],
    ARRAY[]::integer[],
    TRUE,
    '2026-09-08 08:00:00+07',
    'Khoa Công nghệ Thông tin & Phòng Đào Tạo',
    'semester-demo',
    'Học kỳ 1 năm học 2026-2027',
    '2026-09-08 08:00:00+07',
    '2026-09-08 08:00:00+07',
    1
),
(
    'announcement-ute-course-reg-official',
    'Kế hoạch tổ chức đăng ký học phần chính thức Học kỳ 1 năm học 2026-2027',
    '<p>Phòng Đào tạo thông báo kế hoạch chi tiết về việc đăng ký học phần chính thức dành cho sinh viên bậc Đại học chính quy các khóa như sau:</p>
<ul>
  <li><strong>Hạn mức tín chỉ tối đa:</strong> 28 tín chỉ/học kỳ theo Điều 14 Quy chế Đào tạo tín chỉ HCMUTE. Sinh viên đạt GPA học kỳ trước &ge; 3.20 được đăng ký tối đa 30 tín chỉ theo phê duyệt của Cố vấn học tập.</li>
  <li><strong>Khung thời gian mở cổng đăng ký:</strong>
    <ul>
      <li>Khóa 2023 (Năm 4): Từ 08:00 ngày 25/08/2026 đến 17:00 ngày 27/08/2026.</li>
      <li>Khóa 2024 (Năm 3): Từ 08:00 ngày 28/08/2026 đến 17:00 ngày 30/08/2026.</li>
      <li>Khóa 2025 (Năm 2): Từ 08:00 ngày 31/08/2026 đến 17:00 ngày 02/09/2026.</li>
      <li>Đăng ký bổ sung toàn trường &amp; Đổi lớp: Từ 08:00 ngày 05/09/2026 đến 17:00 ngày 15/09/2026.</li>
    </ul>
  </li>
  <li><strong>Lưu ý học vụ quan trọng:</strong> Hệ thống tự động kiểm tra điều kiện tiên quyết và trùng thời khóa biểu. Sinh viên cần rà soát kỹ tiến độ học tập trên trang cá nhân trước khi ấn nút xác nhận lưu đăng ký.</li>
</ul>',
    'URGENT',
    ARRAY['STUDENT', 'LECTURER']::text[],
    ARRAY[]::integer[],
    TRUE,
    '2026-08-25 08:00:00+07',
    'Phòng Đào Tạo UTE',
    'semester-demo',
    'Học kỳ 1 năm học 2026-2027',
    '2026-08-25 08:00:00+07',
    '2026-08-25 08:00:00+07',
    1
),
(
    'announcement-ute-thesis-registration-fall',
    'Thông báo mở đợt đăng ký Khóa luận tốt nghiệp (KLTN) & Tiểu luận chuyên ngành Khoa CNTT',
    '<p>Ban Chủ nhiệm Khoa Công nghệ Thông tin phối hợp cùng Phòng Đào tạo thông báo mở đợt đăng ký đề tài tốt nghiệp đợt 1 năm học 2026-2027:</p>
<ul>
  <li><strong>Điều kiện đăng ký KLTN:</strong> Sinh viên tích lũy tối thiểu 110 tín chỉ, điểm trung bình tích lũy CPA &ge; 2.00, không bị cảnh báo học vụ mức 2.</li>
  <li><strong>Hình thức thực hiện:</strong> Mỗi nhóm từ 1 đến tối đa 3 sinh viên; Nhóm trưởng trực tiếp khởi tạo đề tài và mời thành viên cùng Giảng viên hướng dẫn (GVHD) trên Cổng thông tin.</li>
  <li><strong>Mốc thời gian quan trọng:</strong>
    <ul>
      <li>Nộp phiếu giao đề tài có chữ ký duyệt của GVHD: Hạn chót 17:00 ngày 25/09/2026.</li>
      <li>Báo cáo tiến độ giữa kỳ (Mid-term evaluation): Từ ngày 10/11/2026 đến 15/11/2026.</li>
      <li>Nộp bản thảo toàn văn và báo cáo kiểm tra trùng lặp đạo văn (&le; 20%): Ngày 15/12/2026.</li>
      <li>Hội đồng bảo vệ chính thức: Dự kiến từ ngày 05/01/2027 đến 10/01/2027.</li>
    </ul>
  </li>
</ul>',
    'HIGH',
    ARRAY['STUDENT', 'LECTURER']::text[],
    ARRAY[]::integer[],
    TRUE,
    '2026-09-01 07:30:00+07',
    'Văn Phòng Khoa CNTT & Phòng Đào Tạo',
    'semester-demo',
    'Học kỳ 1 năm học 2026-2027',
    '2026-09-01 07:30:00+07',
    '2026-09-01 07:30:00+07',
    1
),
(
    'announcement-ute-scholarship-dr-criteria',
    'Thông báo xét cấp Học bổng Khuyến khích học tập & Tiêu chuẩn Điểm rèn luyện (ĐRL) đợt 1',
    '<p>Hội đồng Thi đua - Khen thưởng Nhà trường thông báo quy định xét duyệt học bổng khuyến khích học tập cho học kỳ:</p>
<ul>
  <li><strong>Học bổng Xuất sắc (Loại A):</strong> GPA &ge; 3.60 và Điểm rèn luyện &ge; 90 điểm (Xếp loại Xuất sắc), hoàn thành tối thiểu 15 tín chỉ trong học kỳ xét. Mức cấp: 120% học phí học kỳ.</li>
  <li><strong>Học bổng Giỏi (Loại B):</strong> GPA &ge; 3.20 và Điểm rèn luyện &ge; 80 điểm (Xếp loại Tốt trở lên). Mức cấp: 100% học phí học kỳ.</li>
  <li><strong>Học bổng Khá (Loại C):</strong> GPA &ge; 2.50 và Điểm rèn luyện &ge; 70 điểm (Xếp loại Khá trở lên). Mức cấp: 50% học phí học kỳ.</li>
  <li><strong>Quy trình đối soát:</strong> Sinh viên kiểm tra điểm rèn luyện tại phân hệ Quản lý ĐRL trên Cổng thông tin. Mọi khiếu nại về minh chứng điểm hoạt động gửi về Văn phòng Đoàn - Hội trường trước 17:00 ngày 20/09/2026.</li>
</ul>',
    'HIGH',
    ARRAY['STUDENT', 'LECTURER']::text[],
    ARRAY[]::integer[],
    TRUE,
    '2026-09-05 09:00:00+07',
    'Phòng Công Tác Sinh Viên & Đào Tạo',
    'semester-demo',
    'Học kỳ 1 năm học 2026-2027',
    '2026-09-05 09:00:00+07',
    '2026-09-05 09:00:00+07',
    1
),
(
    'announcement-ute-exam-regulations-schedule',
    'Quy chế thi kết thúc học phần, phân ca phòng thi và thủ tục nộp đơn phúc khảo trực tuyến',
    '<p>Phòng Khảo thí và Đảm bảo Chất lượng Đào tạo thông báo hướng dẫn tham gia các kỳ thi học phần:</p>
<ul>
  <li><strong>Thẻ dự thi và nhận diện:</strong> Sinh viên bắt buộc xuất trình Thẻ sinh viên có tích hợp chip hoặc Căn cước công dân gắn chip khi vào phòng thi. Trường hợp mất thẻ phải xin giấy xác nhận tại Phòng CTSV trước giờ thi 30 phút.</li>
  <li><strong>Quy định phòng thi:</strong> Tuyệt đối không mang điện thoại di động, đồng hồ thông minh hoặc thiết bị thu phát sóng vào bàn thi. Vi phạm sẽ bị lập biên bản đình chỉ thi và nhận điểm 0 (F).</li>
  <li><strong>Thủ tục phúc khảo bài thi:</strong> Thời gian nộp đơn phúc khảo trực tuyến là 07 ngày làm việc kể từ thời điểm giảng viên công bố điểm tổng kết trên hệ thống. Kết quả phúc khảo được Hội đồng chấm lại và công bố trong vòng 10 ngày làm việc.</li>
</ul>',
    'NORMAL',
    ARRAY['STUDENT', 'LECTURER']::text[],
    ARRAY[]::integer[],
    TRUE,
    '2026-09-07 14:00:00+07',
    'Phòng Khảo Thí & Đảm Bảo Chất Lượng',
    'semester-demo',
    'Học kỳ 1 năm học 2026-2027',
    '2026-09-07 14:00:00+07',
    '2026-09-07 14:00:00+07',
    1
),
(
    'announcement-ute-career-fair-tech-2026',
    'Ngày hội việc làm và Kết nối doanh nghiệp công nghệ thông tin UTE Tech Career Expo 2026',
    '<p>Nhà trường phối hợp cùng hơn 60 tập đoàn công nghệ đa quốc gia và doanh nghiệp phần mềm hàng đầu tổ chức Ngày hội Việc làm UTE Tech Career Expo:</p>
<ul>
  <li><strong>Thời gian:</strong> 07:30 - 16:30 thứ Sáu ngày 23/10/2026.</li>
  <li><strong>Địa điểm:</strong> Quảng trường trung tâm Khu A và Hội trường Trịnh Công Sơn, Trường ĐH Sư phạm Kỹ thuật TP.HCM.</li>
  <li><strong>Doanh nghiệp tham gia:</strong> FPT Software, Viettel, VNPT, VNG, KMS Technology, Bosch Global Software, Renesas, Shopee, NAB Innovation Centre Vietnam...</li>
  <li><strong>Cơ hội dành cho sinh viên:</strong>
    <ul>
      <li>Hơn 1.200 vị trí tuyển dụng thực tập sinh (Internship) và kỹ sư chính thức (Fresher/Junior Software Engineer, Data Engineer, AI Engineer).</li>
      <li>Phỏng vấn tuyển dụng trực tiếp tại gian hàng (On-site Fast-track Interview).</li>
      <li>Tư vấn sửa CV chuyên nghiệp và định hướng lộ trình nghề nghiệp cùng các chuyên gia Headhunter.</li>
    </ul>
  </li>
  <li><strong>Quyền lợi:</strong> Sinh viên tham dự được cộng 05 điểm rèn luyện vào Mục 3 (Hoạt động hỗ trợ kỹ năng và hội nhập nghề nghiệp).</li>
</ul>',
    'HIGH',
    ARRAY['STUDENT', 'LECTURER']::text[],
    ARRAY[]::integer[],
    TRUE,
    '2026-09-09 10:00:00+07',
    'Trung Tâm Hướng Nghiệp & Quan Hệ Doanh Nghiệp',
    'semester-demo',
    'Học kỳ 1 năm học 2026-2027',
    '2026-09-09 10:00:00+07',
    '2026-09-09 10:00:00+07',
    1
),
(
    'announcement-ute-english-it-exit-benchmark',
    'Kế hoạch kiểm tra Chuẩn đầu ra Ngoại ngữ (TOEIC/IELTS) và Tin học chuẩn Quốc tế MOS đợt 1',
    '<p>Trung tâm Ngoại ngữ - Tin học UTE thông báo lịch kiểm tra và xét công nhận chuẩn đầu ra đợt tháng 10/2026:</p>
<ul>
  <li><strong>Chuẩn Ngoại ngữ tốt nghiệp:</strong>
    <ul>
      <li>Bậc Đại học hệ chuẩn: TOEIC 500+ / IELTS 5.0+ / VSTEP B1.</li>
      <li>Bậc Đại học hệ chất lượng cao / Tiên tiến: TOEIC 600+ / IELTS 5.5+ / VSTEP B2.</li>
    </ul>
  </li>
  <li><strong>Chuẩn Tin học:</strong> Chứng chỉ MOS (Word, Excel) đạt từ 700/1000 điểm hoặc chứng chỉ Ứng dụng CNTT nâng cao.</li>
  <li><strong>Thời hạn nộp chứng chỉ quốc tế để miễn thi:</strong> Đến hết 17:00 ngày 15/10/2026 qua cổng nộp hồ sơ trực tuyến.</li>
  <li><strong>Lịch thi tập trung:</strong> Ngày 24 và 25/10/2026 tại Tòa nhà Trung tâm Dạy học số.</li>
</ul>',
    'NORMAL',
    ARRAY['STUDENT']::text[],
    ARRAY[]::integer[],
    TRUE,
    '2026-09-06 08:30:00+07',
    'Trung Tâm Ngoại Ngữ & Tin Học UTE',
    'semester-demo',
    'Học kỳ 1 năm học 2026-2027',
    '2026-09-06 08:30:00+07',
    '2026-09-06 08:30:00+07',
    1
),
(
    'announcement-ute-tuition-payment-notice',
    'Thông báo thu học phí Học kỳ 1 năm học 2026-2027 và các kênh thanh toán trực tuyến',
    '<p>Phòng Kế hoạch - Tài chính thông báo thời hạn và phương thức nộp học phí học kỳ 1 năm học 2026-2027:</p>
<ul>
  <li><strong>Thời gian nộp:</strong> Từ ngày 01/09/2026 đến hết 17:00 ngày 10/10/2026. Sau thời hạn trên, sinh viên chưa hoàn thành nghĩa vụ học phí sẽ bị khóa quyền thi kết thúc học phần.</li>
  <li><strong>Phương thức thanh toán trực tuyến:</strong>
    <ul>
      <li>Cổng thanh toán VietQR (mã QR động có gắn sẵn mã sinh viên và số tiền học phí chính xác).</li>
      <li>Chuyển khoản định danh qua Ngân hàng số Agribank, Vietcombank, BIDV theo mã định danh sinh viên: <code>HCMUTE + [Mã SV]</code>.</li>
      <li>Thanh toán trực tiếp tại Cổng Cán sự lớp hoặc Văn phòng Phòng Kế hoạch - Tài chính (Phòng A1-102).</li>
    </ul>
  </li>
  <li><strong>Chính sách gia hạn học phí:</strong> Sinh viên có hoàn cảnh khó khăn làm đơn xin gia hạn nộp học phí theo mẫu số 04-ĐT và nộp tại Phòng CTSV trước ngày 25/09/2026.</li>
</ul>',
    'NORMAL',
    ARRAY['STUDENT']::text[],
    ARRAY[]::integer[],
    TRUE,
    '2026-09-01 10:00:00+07',
    'Phòng Kế Hoạch - Tài Chính',
    'semester-demo',
    'Học kỳ 1 năm học 2026-2027',
    '2026-09-01 10:00:00+07',
    '2026-09-01 10:00:00+07',
    1
),
(
    'announcement-ute-academic-warning-counseling',
    'Thông báo rà soát kết quả học tập, cảnh báo học vụ và chương trình tư vấn học vụ cá nhân',
    '<p>Phòng Đào tạo phối hợp cùng Tổ Cố vấn học tập các Khoa thông báo kế hoạch hỗ trợ sinh viên có nguy cơ học vụ:</p>
<ul>
  <li><strong>Cảnh báo học vụ mức 1:</strong> Điểm trung bình học kỳ GPA &lt; 1.00 (học kỳ đầu) hoặc GPA &lt; 1.20 (các học kỳ tiếp theo).</li>
  <li><strong>Cảnh báo học vụ mức 2:</strong> Bị cảnh báo mức 1 trong hai học kỳ liên tiếp hoặc tổng số tín chỉ nợ đọng vượt quá 24 tín chỉ.</li>
  <li><strong>Chương trình tư vấn học tập:</strong> Các sinh viên thuộc diện cảnh báo học vụ được phân công Cố vấn học tập riêng để xây dựng lộ trình cải thiện điểm số, giới hạn khối lượng đăng ký từ 10 - 14 tín chỉ/học kỳ.</li>
  <li><strong>Lịch hẹn tư vấn:</strong> Các buổi tư vấn trực tiếp diễn ra từ ngày 15/09/2026 đến 25/09/2026 tại Văn phòng Cố vấn học tập Tòa nhà Trung tâm.</li>
</ul>',
    'HIGH',
    ARRAY['STUDENT', 'LECTURER']::text[],
    ARRAY[]::integer[],
    TRUE,
    '2026-09-04 15:00:00+07',
    'Phòng Đào Tạo & Ban Cố Vấn Học Tập',
    'semester-demo',
    'Học kỳ 1 năm học 2026-2027',
    '2026-09-04 15:00:00+07',
    '2026-09-04 15:00:00+07',
    1
),
(
    'announcement-ute-student-scientific-research-awards',
    'Phát động Giải thưởng Nghiên cứu Khoa học Sinh viên và Khởi nghiệp Đổi mới Sáng tạo 2026-2027',
    '<p>Khoa Công nghệ Thông tin phối hợp cùng Phòng Đào tạo thông báo phát động phong trào Sinh viên Nghiên cứu Khoa học (NCKH) năm học 2026-2027:</p>
<ul>
  <li><strong>Lĩnh vực ưu tiên tài trợ kinh phí:</strong>
    <ul>
      <li>Khoa học Dữ liệu lớn (Big Data Analytics), Trí tuệ Nhân tạo và Thị giác máy tính.</li>
      <li>Internet vạn vật (IoT), Hệ thống nhúng và Tự động hóa sản xuất thông minh.</li>
      <li>Năng lượng tái tạo, Công nghệ xanh và Vật liệu tiên tiến.</li>
      <li>Giải pháp chuyển đổi số trong quản lý giáo dục và đô thị thông minh.</li>
    </ul>
  </li>
  <li><strong>Kinh phí hỗ trợ:</strong> Mỗi đề tài được phê duyệt nhận hỗ trợ từ 10.000.000 VNĐ đến 30.000.000 VNĐ từ Quỹ Phát triển Khoa học Công nghệ HCMUTE.</li>
  <li><strong>Hạn nộp thuyết minh đề tài:</strong> Trước 17:00 ngày 15/10/2026 qua Cổng quản lý NCKH trực tuyến.</li>
  <li><strong>Quyền lợi:</strong> Tác giả đề tài đạt giải Nhất, Nhì cấp Trường được quy đổi điểm Khóa luận tốt nghiệp (điểm 10) và cộng 15 điểm rèn luyện.</li>
</ul>',
    'HIGH',
    ARRAY['STUDENT', 'LECTURER']::text[],
    ARRAY[]::integer[],
    TRUE,
    '2026-09-03 09:00:00+07',
    'Khoa Công nghệ Thông tin & Phòng Đào Tạo',
    'semester-demo',
    'Học kỳ 1 năm học 2026-2027',
    '2026-09-03 09:00:00+07',
    '2026-09-03 09:00:00+07',
    1
),
(
    'announcement-ute-digital-transcript-signature',
    'Triển khai Cổng Dịch vụ công Một cửa điện tử và Cấp bản sao trích lục Bảng điểm có Chữ ký số',
    '<p>Nhằm đẩy mạnh chuyển đổi số học vụ và tạo điều kiện thuận lợi cho sinh viên tra cứu và xin cấp giấy tờ học vụ:</p>
<ul>
  <li><strong>Dịch vụ trực tuyến mới:</strong>
    <ul>
      <li>Cấp Giấy chứng nhận sinh viên trực tuyến có mã QR xác thực tức thời.</li>
      <li>Cấp Bảng điểm học tập điện tử có Chữ ký số của Hiệu trưởng / Trưởng phòng Đào tạo (có giá trị pháp lý tương đương bản giấy đóng dấu đỏ).</li>
      <li>Đăng ký cấp lại Thẻ sinh viên, Đơn tạm hoãn nghĩa vụ quân sự và Đơn xin nghỉ học tạm thời.</li>
    </ul>
  </li>
  <li><strong>Thời gian xử lý:</strong> Tối đa 02 ngày làm việc kể từ thời điểm gửi yêu cầu thành công trên hệ thống. Bản điện tử PDF ký số được tự động gửi về Email sinh viên.</li>
  <li><strong>Cổng truy cập dịch vụ:</strong> Mục "Dịch vụ một cửa" trên Cổng thông tin đào tạo HCMUTE.</li>
</ul>',
    'NORMAL',
    ARRAY['STUDENT', 'LECTURER', 'ADMIN']::text[],
    ARRAY[]::integer[],
    TRUE,
    '2026-09-02 11:00:00+07',
    'Phòng Đào Tạo & Trung Tâm CNTT',
    'semester-demo',
    'Học kỳ 1 năm học 2026-2027',
    '2026-09-02 11:00:00+07',
    '2026-09-02 11:00:00+07',
    1
),
(
    'announcement-ute-ojt-internship-semester1',
    'Kế hoạch tổ chức Thực tập Doanh nghiệp (OJT) & Thực tập tốt nghiệp Học kỳ 1 năm học 2026-2027',
    '<p>Văn phòng các Khoa phối hợp cùng Trung tâm Hướng nghiệp &amp; Quan hệ Doanh nghiệp hướng dẫn quy trình thực tập:</p>
<ul>
  <li><strong>Đối tượng:</strong> Sinh viên năm thứ 3 và năm thứ 4 đã hoàn thành tối thiểu 90 tín chỉ chuyên ngành.</li>
  <li><strong>Thời lượng thực tập:</strong> Tối thiểu 10 tuần làm việc toàn thời gian (Full-time) tại doanh nghiệp đối tác đã được Nhà trường thẩm định.</li>
  <li><strong>Quy trình thực hiện:</strong>
    <ul>
      <li>Bước 1: Nộp Giấy tiếp nhận thực tập của Doanh nghiệp trên Cổng thông tin trước ngày 25/09/2026.</li>
      <li>Bước 2: Giảng viên phụ trách OJT duyệt kế hoạch làm việc và giao nhiệm vụ chuyên môn.</li>
      <li>Bước 3: Viết nhật ký thực tập hàng tuần và nộp Báo cáo tổng kết có xác nhận, chấm điểm của Người hướng dẫn tại doanh nghiệp trước ngày 20/12/2026.</li>
    </ul>
  </li>
</ul>',
    'NORMAL',
    ARRAY['STUDENT', 'LECTURER']::text[],
    ARRAY[]::integer[],
    TRUE,
    '2026-09-01 14:00:00+07',
    'Văn Phòng Khoa & TT Quan Hệ Doanh Nghiệp',
    'semester-demo',
    'Học kỳ 1 năm học 2026-2027',
    '2026-09-01 14:00:00+07',
    '2026-09-01 14:00:00+07',
    1
),
(
    'announcement-ute-system-upgrade-maintenance',
    'Lịch bảo trì, nâng cấp hạ tầng Cụm máy chủ Cổng Đào tạo UTE định kỳ tháng 09/2026',
    '<p>Trung tâm Công nghệ Thông tin thông báo kế hoạch bảo trì kỹ thuật hệ thống nhằm nâng cao băng thông và độ ổn định:</p>
<ul>
  <li><strong>Thời gian tạm dừng dịch vụ:</strong> Từ 23:00 thứ Bảy ngày 12/09/2026 đến 04:00 sáng Chủ Nhật ngày 13/09/2026.</li>
  <li><strong>Nội dung nâng cấp:</strong> Cập nhật bản vá bảo mật cơ sở dữ liệu PostgreSQL, nâng cấp cấu hình cân bằng tải (HAProxy Load Balancer) và mở rộng cụm nhớ đệm Redis phục vụ đợt đăng ký môn học cao điểm.</li>
  <li><strong>Phạm vi ảnh hưởng:</strong> Cổng đăng ký học phần, Cổng xem điểm và Hệ thống nộp bài trực tuyến sẽ tạm thời gián đoạn truy cập trong khoảng thời gian trên.</li>
</ul>
<p>Kính đề nghị Quý Thầy/Cô và các bạn sinh viên lưu ý kế hoạch để chủ động trong công tác và học tập.</p>',
    'LOW',
    ARRAY['STUDENT', 'LECTURER', 'ADMIN']::text[],
    ARRAY[]::integer[],
    TRUE,
    '2026-08-30 16:00:00+07',
    'Trung Tâm Công Nghệ Thông Tin',
    'semester-demo',
    'Học kỳ 1 năm học 2026-2027',
    '2026-08-30 16:00:00+07',
    '2026-08-30 16:00:00+07',
    1
)
ON CONFLICT (id) DO UPDATE SET
    title = EXCLUDED.title,
    content = EXCLUDED.content,
    priority = EXCLUDED.priority,
    "targetRoles" = EXCLUDED."targetRoles",
    "targetYears" = EXCLUDED."targetYears",
    "isGlobal" = EXCLUDED."isGlobal",
    "publishAt" = EXCLUDED."publishAt",
    "publishedBy" = EXCLUDED."publishedBy",
    "semesterId" = EXCLUDED."semesterId",
    "semesterName" = EXCLUDED."semesterName",
    "updatedAt" = EXCLUDED."updatedAt",
    version = engagement."Announcement".version + 1;
