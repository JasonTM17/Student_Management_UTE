-- Flyway Migration V68: Enrich Enterprise Media Gallery, Academic Operations, and Thesis Repository Records
-- 1. Dọn dẹp dữ liệu rác/E2E test
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

-- 2. Đảm bảo toàn bộ 16 thông báo học vụ cốt lõi tồn tại trước khi chuẩn hóa chuyên mục và tạo quan hệ media/tài liệu
INSERT INTO engagement."Announcement" (
    id, title, content, priority, "targetRoles", "targetYears", "isGlobal",
    "publishAt", "publishedBy", "semesterId", "semesterName", "createdAt", "updatedAt", version
) VALUES
(
    'announcement-ute-bigdata-ai-center',
    'Khởi động Phòng Nghiên cứu Dữ liệu lớn (Big Data) & Trí tuệ Nhân tạo Khoa CNTT HCM-UTE',
    '<p>Khoa Công nghệ Thông tin - Trường Đại học Sư phạm Kỹ thuật TP.HCM trân trọng thông báo chính thức khánh thành và đưa vào vận hành cụm máy chủ điện toán hiệu năng cao (HPC Cluster) phục vụ nghiên cứu Dữ liệu lớn (Big Data) và Trí tuệ nhân tạo (AI Lab) tại cơ sở chính.</p>
<figure class="my-5 overflow-hidden rounded-xl border border-border/70 bg-secondary/15 p-2">
  <img src="/images/news/bigdata-ai-lab.jpg" alt="Phòng Nghiên cứu Big Data & AI Khoa CNTT HCM-UTE" class="w-full h-auto rounded-lg object-cover" />
  <figcaption class="pt-2 text-center text-xs font-medium text-muted-foreground italic">
    Cụm máy chủ điện toán GPU NVIDIA A100 và hạ tầng lưu trữ phân tán 500TB tại Phòng Nghiên cứu Big Data & AI Khoa CNTT HCM-UTE
  </figcaption>
</figure>
<p>Dự án là bước tiến chiến lược trong đề án chuyển đổi số giáo dục đại học và nâng cao năng lực nghiên cứu ứng dụng thực tiễn của Nhà trường:</p>
<ul>
  <li><strong>Hạ tầng kỹ thuật:</strong> Cụm máy chủ 8x NVIDIA A100 GPU Tensor Core (80GB VRAM), hệ thống lưu trữ phân tán Ceph 500TB NVMe và mạng kết nối InfiniBand tốc độ cao 200Gbps.</li>
  <li><strong>Nền tảng phần mềm:</strong> Triển khai môi trường ảo hóa Kubernetes, hỗ trợ xử lý luồng Kafka, Apache Spark, PyTorch Distributed và cụm huấn luyện mô hình ngôn ngữ lớn (LLM).</li>
  <li><strong>Đối tượng khai thác:</strong> Toàn thể giảng viên, nghiên cứu sinh, học viên cao học, nhóm sinh viên nghiên cứu khoa học (Lab AI & Data Science) và đề tài Khóa luận tốt nghiệp chuyên sâu.</li>
  <li><strong>Quy trình đăng ký tài nguyên:</strong> Giảng viên và nhóm nghiên cứu nộp đề cương tại Văn phòng Bộ môn Kỹ thuật Dữ liệu (Phòng A1-402) trước ngày 30/09/2026.</li>
</ul>',
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
    '<p>Phòng Đào tạo thông báo kế hoạch tổ chức đăng ký học phần chính thức cho Học kỳ 1 năm học 2026-2027 trên hệ thống quản lý học tập tích hợp CampusUTE.</p>
<figure class="my-5 overflow-hidden rounded-xl border border-border/70 bg-secondary/15 p-2">
  <img src="/images/news/course-registration.jpg" alt="Cổng đăng ký học phần trực tuyến CampusUTE" class="w-full h-auto rounded-lg object-cover" />
  <figcaption class="pt-2 text-center text-xs font-medium text-muted-foreground italic">
    Sinh viên tra cứu thời khóa biểu và thực hiện đăng ký học phần trực tuyến trên cổng CampusUTE
  </figcaption>
</figure>
<p>Để đảm bảo công tác tổ chức đào tạo diễn ra thuận lợi, sinh viên cần nắm vững các mốc thời gian và quy định học vụ sau:</p>
<ul>
  <li><strong>Hạn mức tín chỉ:</strong> Sinh viên được đăng ký tối đa <strong>28 tín chỉ</strong> trong học kỳ chính; chỉ trường hợp có đơn xin vượt hạn mức được Ban Chủ nhiệm Khoa và Phòng Đào tạo duyệt mới được nâng lên trần 30 tín chỉ.</li>
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
    '<p>Khoa Công nghệ Thông tin - Trường Đại học Sư phạm Kỹ thuật TP.HCM long trọng tổ chức Lễ bảo vệ Khóa luận tốt nghiệp (KLTN) đợt 2 cho sinh viên các chuyên ngành Kỹ thuật phần mềm, Hệ thống thông tin, An toàn thông tin và Trí tuệ nhân tạo.</p>
<figure class="my-5 overflow-hidden rounded-xl border border-border/70 bg-secondary/15 p-2">
  <img src="/images/news/thesis-defense.jpg" alt="Lễ bảo vệ Khóa luận tốt nghiệp Khoa CNTT HCM-UTE" class="w-full h-auto rounded-lg object-cover" />
  <figcaption class="pt-2 text-center text-xs font-medium text-muted-foreground italic">
    Sinh viên Khoa Công nghệ Thông tin tự tin báo cáo đề tài khóa luận trước Hội đồng chấm thi học thuật HCM-UTE
  </figcaption>
</figure>
<p>Đợt bảo vệ ghi nhận sự trưởng thành vượt bậc về hàm lượng khoa học, tính ứng dụng thực tiễn và khả năng làm chủ công nghệ của sinh viên:</p>
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
  <li><strong>Kho lưu trữ số hóa:</strong> Toàn bộ báo cáo toàn văn, slide thuyết trình và mã nguồn đề tài được tích hợp lưu trữ tại Phân hệ Kho Luận án & Đồ án tốt nghiệp trên nền tảng CampusUTE.</li>
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
    '<p>Hội đồng xét duyệt Học bổng Nhà trường trân trọng thông báo kết quả đối soát điểm trung bình chung học tập (GPA) kết hợp điểm rèn luyện (ĐRL) xét cấp Học bổng Khuyến khích học tập.</p>
<figure class="my-5 overflow-hidden rounded-xl border border-border/70 bg-secondary/15 p-2">
  <img src="/images/news/scholarship-ceremony.jpg" alt="Lễ trao học bổng khuyến khích học tập HCM-UTE" class="w-full h-auto rounded-lg object-cover" />
  <figcaption class="pt-2 text-center text-xs font-medium text-muted-foreground italic">
    Lễ trao Học bổng Khuyến khích học tập và Học bổng Doanh nghiệp vinh danh các sinh viên xuất sắc HCM-UTE
  </figcaption>
</figure>
<p>Chính sách học bổng nhằm động viên tinh thần nỗ lực vươn lên trong học tập và rèn luyện của sinh viên toàn trường theo các tiêu chuẩn học vụ hiện hành:</p>
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
    '<p>Nhà trường phối hợp cùng hơn 60 tập đoàn công nghệ đa quốc gia và doanh nghiệp công nghệ thông tin hàng đầu tổ chức Ngày hội Việc làm và Kết nối Doanh nghiệp UTE Tech Career Expo 2026 tại khuôn viên sảnh A & B.</p>
<figure class="my-5 overflow-hidden rounded-xl border border-border/70 bg-secondary/15 p-2">
  <img src="/images/news/tech-career-expo.jpg" alt="Ngày hội việc làm UTE Tech Career Expo" class="w-full h-auto rounded-lg object-cover" />
  <figcaption class="pt-2 text-center text-xs font-medium text-muted-foreground italic">
    Không khí sôi nổi tại UTE Tech Career Expo 2026 với hơn 60 tập đoàn công nghệ và 1.200 vị trí tuyển dụng kỹ sư
  </figcaption>
</figure>
<p>Sự kiện mang đến hàng nghìn cơ hội nghề nghiệp chất lượng cao và định hướng phát triển sự nghiệp vững chắc cho sinh viên các khối ngành kỹ thuật công nghệ:</p>
<ul>
  <li><strong>Thời gian:</strong> 07:30 - 16:30 thứ Sáu ngày 23/10/2026.</li>
  <li><strong>Địa điểm:</strong> Quảng trường trung tâm Khu A và Hội trường Trịnh Công Sơn, Trường ĐH Công nghệ Kỹ thuật TP.HCM.</li>
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
    '<p>Trường Đại học Sư phạm Kỹ thuật TP.HCM phối hợp cùng Khoa Công nghệ Thông tin thông báo phát động phong trào Sinh viên Nghiên cứu Khoa học (NCKH) & Đổi mới Sáng tạo Công nghệ năm học 2026-2027.</p>
<figure class="my-5 overflow-hidden rounded-xl border border-border/70 bg-secondary/15 p-2">
  <img src="/images/news/scientific-research.jpg" alt="Lễ vinh danh Giải thưởng Nghiên cứu Khoa học Sinh viên HCM-UTE" class="w-full h-auto rounded-lg object-cover" />
  <figcaption class="pt-2 text-center text-xs font-medium text-muted-foreground italic">
    Đại diện Ban Giám hiệu Nhà trường trao bằng khen và cúp lưu niệm cho các nhóm sinh viên đạt giải thưởng Nghiên cứu Khoa học 2026
  </figcaption>
</figure>
<p>Phong trào nghiên cứu khoa học sinh viên năm nay tiếp tục khẳng định vị thế tiên phong của HCM-UTE trong sáng tạo kỹ thuật và giải quyết các bài toán thực tiễn của xã hội:</p>
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
),
(
    'announcement-ute-lecturer-exam-proctoring',
    'Kế hoạch phân công Cán bộ coi thi và thời hạn nộp Đề thi trích lục Học kỳ 1 năm học 2026-2027',
    '<p>Phòng Khảo thí & Đảm bảo Chất lượng thông báo đến Quý Thầy/Cô kế hoạch công tác khảo thí Học kỳ 1:</p>
<ul>
  <li><strong>Thời hạn nộp Đề thi trích lục:</strong> Quý Thầy/Cô phụ trách học phần hoàn tất nộp 02 đề thi trích lục (kèm đáp án, biểu điểm và ma trận đề thi theo chuẩn đầu ra CLO) trước 17:00 ngày 20/11/2026 tại Văn phòng Phòng Khảo thí (Phòng A1-201).</li>
  <li><strong>Lịch phân công Cán bộ coi thi:</strong> Danh sách phân ca coi thi chính thức cho từng giảng viên đã được cập nhật chi tiết trên Cổng thông tin Giảng viên và gửi về Văn phòng các Khoa/Bộ môn.</li>
  <li><strong>Quy định phòng thi:</strong> Giảng viên có mặt tại Phòng Hội đồng thi trước giờ phát đề 20 phút để nhận túi bài thi, danh sách thí sinh và thẻ cán bộ coi thi.</li>
</ul>',
    'HIGH',
    ARRAY['LECTURER']::text[],
    ARRAY[]::integer[],
    FALSE,
    '2026-09-08 09:00:00+07',
    'Phòng Khảo Thí & Đảm Bảo Chất Lượng',
    'semester-demo',
    'Học kỳ 1 năm học 2026-2027',
    '2026-09-08 09:00:00+07',
    '2026-09-08 09:00:00+07',
    1
),
(
    'announcement-ute-lecturer-syllabus-review',
    'Hướng dẫn rà soát và cập nhật Đề cương chi tiết học phần (Course Syllabus) theo chuẩn ABET',
    '<p>Ban Chủ nhiệm Khoa Công nghệ Thông tin phối hợp cùng Phòng Đào tạo đề nghị Quý Thầy/Cô phụ trách học phần thực hiện công tác rà soát chuyên môn:</p>
<ul>
  <li><strong>Chuẩn hóa mục tiêu học phần:</strong> Đối sánh và ánh xạ ma trận chuẩn đầu ra học phần (CLO) tương thích với Chuẩn đầu ra chương trình đào tạo (PLO) theo tiêu chuẩn kiểm định quốc tế ABET.</li>
  <li><strong>Cập nhật học liệu điện tử:</strong> Bổ sung danh mục giáo trình tham khảo xuất bản từ năm 2022 trở lại đây và tích hợp đường dẫn bài giảng số lên hệ thống Canvas/LMS của Trường.</li>
  <li><strong>Thời hạn hoàn thành:</strong> Nộp bản mềm Đề cương chi tiết đã có ý kiến phê duyệt của Trưởng Bộ môn qua Cổng học vụ trước ngày 25/09/2026.</li>
</ul>',
    'NORMAL',
    ARRAY['LECTURER']::text[],
    ARRAY[]::integer[],
    FALSE,
    '2026-09-07 14:00:00+07',
    'Khoa Công nghệ Thông tin & Phòng Đào Tạo',
    'semester-demo',
    'Học kỳ 1 năm học 2026-2027',
    '2026-09-07 14:00:00+07',
    '2026-09-07 14:00:00+07',
    1
),
(
    'announcement-ute-lecturer-research-norm',
    'Kế hoạch nghiệm thu đề tài NCKH cấp Trường và đăng ký định mức giờ chuẩn Giảng viên năm học 2026-2027',
    '<p>Khoa Công nghệ Thông tin phối hợp cùng Phòng Đào tạo thông báo đến Quý Thầy/Cô cán bộ giảng dạy:</p>
<ul>
  <li><strong>Kê khai định mức giờ giảng & NCKH:</strong> Quý Thầy/Cô hoàn thành bản kê khai kế hoạch giảng dạy, hướng dẫn đồ án và nghiên cứu khoa học năm học 2026-2027 trên Cổng quản trị viên chức trước ngày 30/10/2026.</li>
  <li><strong>Chính sách khen thưởng bài báo khoa học:</strong> Nhà trường tiếp tục áp dụng chính sách khen thưởng đột xuất cho các công trình công bố trên các tạp chí quốc tế uy tín thuộc danh mục Web of Science (Q1, Q2) và Scopus.</li>
  <li><strong>Hỗ trợ kinh phí nghiên cứu:</strong> Đăng ký chủ trì đề tài NCKH cấp Trường trọng điểm ưu tiên các hướng: Trí tuệ nhân tạo (AI), Dữ liệu lớn (Big Data), Thiết kế vi mạch bán dẫn và Chuyển đổi số giáo dục.</li>
</ul>',
    'NORMAL',
    ARRAY['LECTURER']::text[],
    ARRAY[]::integer[],
    FALSE,
    '2026-09-06 10:30:00+07',
    'Khoa Công nghệ Thông tin & Phòng Đào Tạo',
    'semester-demo',
    'Học kỳ 1 năm học 2026-2027',
    '2026-09-06 10:30:00+07',
    '2026-09-06 10:30:00+07',
    1
)
ON CONFLICT (id) DO UPDATE SET
    title = EXCLUDED.title,
    content = EXCLUDED.content,
    priority = EXCLUDED.priority,
    "targetRoles" = EXCLUDED."targetRoles",
    "isGlobal" = EXCLUDED."isGlobal",
    "publishedBy" = EXCLUDED."publishedBy",
    "updatedAt" = EXCLUDED."updatedAt";

-- 3. Chuẩn hóa toàn bộ thông báo cũ với chuyên mục, slug và ảnh bìa chất lượng cao
UPDATE engagement."Announcement" SET "categoryId" = 'cat-academic-affairs', "coverImageUrl" = '/images/news/course-registration.jpg', "summary" = 'Kế hoạch năm học 2026-2027 và định hướng chiến lược đào tạo của Trường Đại học Công nghệ Kỹ thuật TP.HCM.', "slug" = 'quyet-dinh-hieu-truong-ke-hoach-nam-hoc-2026-2027', "readingTimeMinutes" = 4, "viewCount" = 4250, "uniqueReaderCount" = 3680 WHERE "id" = 'announcement-rector-new-academic-year-decision';
UPDATE engagement."Announcement" SET "categoryId" = 'cat-academic-affairs', "coverImageUrl" = '/images/news/course-registration.jpg', "summary" = 'Quy định khung thời gian điều chỉnh và rút bớt học phần trong học kỳ chính.', "slug" = 'thoi-gian-dieu-chinh-dang-ky-hoc-phan', "readingTimeMinutes" = 3, "viewCount" = 2840, "uniqueReaderCount" = 2410 WHERE "id" = 'announcement-add-drop-window';
UPDATE engagement."Announcement" SET "categoryId" = 'cat-academic-affairs', "coverImageUrl" = '/images/news/campus-digital-library.jpg', "summary" = 'Danh mục học phần chính thức Học kỳ 1 đã được cập nhật đầy đủ mã lớp và giảng viên giảng dạy.', "slug" = 'danh-muc-hoc-phan-hoc-ky-1-cap-nhat', "readingTimeMinutes" = 3, "viewCount" = 2310, "uniqueReaderCount" = 1980 WHERE "id" = 'announcement-catalog-enriched';
UPDATE engagement."Announcement" SET "categoryId" = 'cat-academic-affairs', "coverImageUrl" = '/images/news/campus-digital-library.jpg', "summary" = 'Bổ sung đề cương chi tiết, chuẩn đầu ra môn học và tài liệu tham khảo cho 100 học phần.', "slug" = 'bo-sung-mo-ta-de-cuong-100-hoc-phan', "readingTimeMinutes" = 4, "viewCount" = 2150, "uniqueReaderCount" = 1820 WHERE "id" = 'announcement-course-descriptions';
UPDATE engagement."Announcement" SET "categoryId" = 'cat-academic-affairs', "coverImageUrl" = '/images/news/course-registration.jpg', "summary" = 'Lịch kiểm tra và đánh giá học phần giữa học kỳ 1 năm học 2026-2027.', "slug" = 'lich-kiem-tra-danh-gia-giua-ky-du-kien', "readingTimeMinutes" = 3, "viewCount" = 3520, "uniqueReaderCount" = 3100 WHERE "id" = 'announcement-exam-preparation';
UPDATE engagement."Announcement" SET "categoryId" = 'cat-academic-affairs', "coverImageUrl" = '/images/news/course-registration.jpg', "summary" = 'Quy định về thời hạn nhập điểm thành phần và quy trình nộp đơn phúc khảo trực tuyến.', "slug" = 'quy-dinh-nhap-diem-va-phuc-khao-hoc-phan', "readingTimeMinutes" = 3, "viewCount" = 3180, "uniqueReaderCount" = 2750 WHERE "id" = 'announcement-grade-policy';
UPDATE engagement."Announcement" SET "categoryId" = 'cat-academic-affairs', "coverImageUrl" = '/images/news/course-registration.jpg', "summary" = 'Sinh viên lưu ý kiểm tra học phần tiên quyết và học phần học trước trước khi đăng ký lớp môn học.', "slug" = 'nhac-nho-kiem-tra-hoc-phan-tien-quyet-khi-dang-ky', "readingTimeMinutes" = 2, "viewCount" = 2640, "uniqueReaderCount" = 2290 WHERE "id" = 'announcement-prerequisite-check';
UPDATE engagement."Announcement" SET "categoryId" = 'cat-academic-affairs', "coverImageUrl" = '/images/news/course-registration.jpg', "summary" = 'Hướng dẫn chi tiết quy trình chọn lớp học phần và sắp xếp thời khóa biểu không bị trùng lịch.', "slug" = 'huong-dan-chon-lop-hoc-phan-va-dang-ky-thoi-khoa-bieu', "readingTimeMinutes" = 3, "viewCount" = 4150, "uniqueReaderCount" = 3720 WHERE "id" = 'announcement-registration-guide';
UPDATE engagement."Announcement" SET "categoryId" = 'cat-academic-affairs', "coverImageUrl" = '/images/news/course-registration.jpg', "summary" = 'Công bố thời khóa biểu chính thức Học kỳ 1 cho toàn thể sinh viên các khóa.', "slug" = 'thoi-khoa-bieu-chinh-thuc-hoc-ky-1-nam-hoc-moi', "readingTimeMinutes" = 3, "viewCount" = 5680, "uniqueReaderCount" = 4920 WHERE "id" = 'announcement-schedule-published';
UPDATE engagement."Announcement" SET "categoryId" = 'cat-academic-affairs', "coverImageUrl" = '/images/news/course-registration.jpg', "summary" = 'Chào đón tân sinh viên và thông báo mở đợt đăng ký học phần Học kỳ 1 năm học 2026-2027.', "slug" = 'chao-mung-nam-hoc-moi-va-thong-bao-dang-ky-hoc-phan', "readingTimeMinutes" = 3, "viewCount" = 4820, "uniqueReaderCount" = 4130 WHERE "id" = 'announcement-welcome';
UPDATE engagement."Announcement" SET "categoryId" = 'cat-academic-affairs', "coverImageUrl" = '/images/news/course-registration.jpg', "summary" = 'Thông báo rà soát kết quả học tập, phân loại học vụ và tổ chức tư vấn học vụ dành cho sinh viên.', "slug" = 'thong-bao-ra-soat-ket-qua-hoc-tap-canh-bao-hoc-vu', "readingTimeMinutes" = 4, "viewCount" = 3280, "uniqueReaderCount" = 2850 WHERE "id" = 'announcement-ute-academic-warning-counseling';
UPDATE engagement."Announcement" SET "categoryId" = 'cat-academic-affairs', "coverImageUrl" = '/images/news/campus-digital-library.jpg', "summary" = 'Cổng Dịch vụ công sinh viên một cửa cấp bảng điểm điện tử có gắn chữ ký số hợp lệ.', "slug" = 'trien-khai-dich-vu-cong-cap-bang-diem-chu-ky-so', "readingTimeMinutes" = 3, "viewCount" = 4720, "uniqueReaderCount" = 4120 WHERE "id" = 'announcement-ute-digital-transcript-signature';
UPDATE engagement."Announcement" SET "categoryId" = 'cat-academic-affairs', "coverImageUrl" = '/images/news/course-registration.jpg', "summary" = 'Kế hoạch sát hạch chuẩn đầu ra Ngoại ngữ (TOEIC/IELTS) và Tin học quốc tế đợt 1 năm học 2026-2027.', "slug" = 'ke-hoach-kiem-tra-chuan-dau-ra-ngoai-ngu-tin-hoc', "readingTimeMinutes" = 4, "viewCount" = 3940, "uniqueReaderCount" = 3450 WHERE "id" = 'announcement-ute-english-it-exit-benchmark';
UPDATE engagement."Announcement" SET "categoryId" = 'cat-academic-affairs', "coverImageUrl" = '/images/news/course-registration.jpg', "summary" = 'Quy chế thi kết thúc học phần, sơ đồ phòng thi và biểu mẫu nộp đơn phúc khảo trực tuyến.', "slug" = 'quy-che-thi-ket-thuc-hoc-phan-va-lich-thi', "readingTimeMinutes" = 4, "viewCount" = 5120, "uniqueReaderCount" = 4580 WHERE "id" = 'announcement-ute-exam-regulations-schedule';
UPDATE engagement."Announcement" SET "categoryId" = 'cat-academic-affairs', "coverImageUrl" = '/images/news/course-registration.jpg', "summary" = 'Lịch thi chính thức và các quy định an ninh, giám sát phòng thi bằng thẻ sinh viên thông minh.', "slug" = 'lich-thi-ket-thuc-hoc-phan-an-ninh-phong-thi', "readingTimeMinutes" = 3, "viewCount" = 4280, "uniqueReaderCount" = 3820 WHERE "id" = 'announcement-ute-final-exam-regulations-2026';
UPDATE engagement."Announcement" SET "categoryId" = 'cat-academic-affairs', "coverImageUrl" = '/images/news/faculty-excellence-awards.jpg', "summary" = 'Kế hoạch phân công cán bộ coi thi và quy định thời hạn gửi đề thi trích lục về Phòng Khảo thí.', "slug" = 'ke-hoach-phan-cong-can-bo-coi-thi-hoc-ky-1', "readingTimeMinutes" = 3, "viewCount" = 1860, "uniqueReaderCount" = 1590 WHERE "id" = 'announcement-ute-lecturer-exam-proctoring';
UPDATE engagement."Announcement" SET "categoryId" = 'cat-academic-affairs', "coverImageUrl" = '/images/news/faculty-excellence-awards.jpg', "summary" = 'Rà soát chuẩn đầu ra môn học và ma trận kỹ năng đáp ứng tiêu chuẩn kiểm định ABET quốc tế.', "slug" = 'huong-dan-ra-soat-de-cuong-hoc-phan-chuan-abet', "readingTimeMinutes" = 4, "viewCount" = 2240, "uniqueReaderCount" = 1910 WHERE "id" = 'announcement-ute-lecturer-syllabus-review';
UPDATE engagement."Announcement" SET "categoryId" = 'cat-academic-affairs', "coverImageUrl" = '/images/news/course-registration.jpg', "summary" = 'Thông báo hạn chót đóng học phí và các cổng thanh toán ngân hàng trực tuyến VietQR/VNPAY.', "slug" = 'thong-bao-thu-hoc-phi-va-kenh-thanh-toan-truc-tuyen', "readingTimeMinutes" = 3, "viewCount" = 6450, "uniqueReaderCount" = 5820 WHERE "id" = 'announcement-ute-tuition-payment-notice';
UPDATE engagement."Announcement" SET "categoryId" = 'cat-academic-affairs', "coverImageUrl" = '/images/news/course-registration.jpg', "summary" = 'Nhắc nhở thời hạn nộp đơn phúc khảo kết quả thi học kỳ qua cổng đào tạo trực tuyến.', "slug" = 'nhac-lich-nop-don-phuc-khao-diem-hoc-phan', "readingTimeMinutes" = 2, "viewCount" = 1920, "uniqueReaderCount" = 1680 WHERE "id" = 'announcement-v26-academic-warning';
UPDATE engagement."Announcement" SET "categoryId" = 'cat-academic-affairs', "coverImageUrl" = '/images/news/faculty-excellence-awards.jpg', "summary" = 'Thông báo thời hạn khóa sổ điểm thành phần và điểm quá trình trên hệ thống học vụ.', "slug" = 'thoi-han-khoa-so-diem-giua-ky-giang-vien', "readingTimeMinutes" = 2, "viewCount" = 1750, "uniqueReaderCount" = 1490 WHERE "id" = 'announcement-v26-gradebook-window';
UPDATE engagement."Announcement" SET "categoryId" = 'cat-academic-affairs', "coverImageUrl" = '/images/news/faculty-excellence-awards.jpg', "summary" = 'Buổi tập huấn tính năng nhập điểm và xuất file bảng điểm trực tuyến cho giảng viên.', "slug" = 'tap-huan-su-dung-he-thong-nhap-diem-truc-tuyen', "readingTimeMinutes" = 3, "viewCount" = 1540, "uniqueReaderCount" = 1320 WHERE "id" = 'announcement-v26-lecturer-training';

UPDATE engagement."Announcement" SET "categoryId" = 'cat-research-tech', "coverImageUrl" = '/images/news/bigdata-ai-lab.jpg', "summary" = 'Nâng cấp mô hình ngôn ngữ và cơ sở tri thức học vụ cho Trợ lý ảo AI Assistant.', "slug" = 'tro-ly-hoc-vu-ai-nang-cap-co-so-tri-thuc', "readingTimeMinutes" = 3, "viewCount" = 2460, "uniqueReaderCount" = 2120 WHERE "id" = 'announcement-ai-rag';
UPDATE engagement."Announcement" SET "categoryId" = 'cat-research-tech', "coverImageUrl" = '/images/news/bigdata-ai-lab.jpg', "summary" = 'Trợ lý AI hỗ trợ trích dẫn nguồn quy chế, điều khoản chính xác theo văn bản pháp quy của Nhà trường.', "slug" = 'tro-ly-ai-tich-hop-tinh-nang-trich-dan-nguon-quy-che', "readingTimeMinutes" = 3, "viewCount" = 2180, "uniqueReaderCount" = 1890 WHERE "id" = 'announcement-assistant-citations';
UPDATE engagement."Announcement" SET "categoryId" = 'cat-research-tech', "coverImageUrl" = '/images/news/robotics-iot-lab.jpg', "summary" = 'Khoa Công nghệ Thông tin trang bị thêm 80 máy trạm đồ họa chuyên dụng cho phòng lab thực hành.', "slug" = 'nang-cap-trang-thiet-bi-phong-lab-khoa-cntt', "readingTimeMinutes" = 3, "viewCount" = 2780, "uniqueReaderCount" = 2390 WHERE "id" = 'announcement-v26-lab-upgrade';
UPDATE engagement."Announcement" SET "categoryId" = 'cat-research-tech', "coverImageUrl" = '/images/news/bigdata-ai-lab.jpg', "summary" = 'Phòng thí nghiệm AI & Khoa học Dữ liệu tuyển sinh viên tham gia các dự án nghiên cứu quốc tế.', "slug" = 'tuyen-thanh-vien-nhom-nghien-cuu-tri-tue-nhan-tao', "readingTimeMinutes" = 3, "viewCount" = 3360, "uniqueReaderCount" = 2950 WHERE "id" = 'announcement-v26-research-group';
UPDATE engagement."Announcement" SET "categoryId" = 'cat-research-tech', "coverImageUrl" = '/images/news/scientific-research.jpg', "summary" = 'Kế hoạch nghiệm thu các đề tài nghiên cứu khoa học cấp cơ sở và tính giờ chuẩn giảng dạy.', "slug" = 'ke-hoach-nghiem-thu-de-tai-nckh-cap-truong-2026-2027', "readingTimeMinutes" = 4, "viewCount" = 2520, "uniqueReaderCount" = 2170 WHERE "id" = 'announcement-ute-lecturer-research-norm';
UPDATE engagement."Announcement" SET "categoryId" = 'cat-research-tech', "coverImageUrl" = '/images/news/bigdata-ai-lab.jpg', "summary" = 'Bảo trì máy chủ và tối ưu hóa hạ tầng cơ sở dữ liệu định kỳ vào ban đêm.', "slug" = 'ke-hoach-bao-tri-he-thong-may-chu-cong-hoc-vu', "readingTimeMinutes" = 2, "viewCount" = 1940, "uniqueReaderCount" = 1690 WHERE "id" = 'announcement-maintenance-window';
UPDATE engagement."Announcement" SET "categoryId" = 'cat-research-tech', "coverImageUrl" = '/images/news/bigdata-ai-lab.jpg', "summary" = 'Nâng cấp cụm máy chủ ảo hóa và tăng cường băng thông cho cổng thông tin đào tạo HCM-UTE.', "slug" = 'lich-bao-tri-nang-cap-cum-may-chu-cong-dao-tao-ute', "readingTimeMinutes" = 3, "viewCount" = 2270, "uniqueReaderCount" = 1980 WHERE "id" = 'announcement-ute-system-upgrade-maintenance';

UPDATE engagement."Announcement" SET "categoryId" = 'cat-career-opps', "coverImageUrl" = '/images/news/tech-career-expo.jpg', "summary" = 'Kế hoạch tổ chức thực tập doanh nghiệp (OJT) và đồ án tốt nghiệp liên kết doanh nghiệp Học kỳ 1.', "slug" = 'ke-hoach-thuc-tap-doanh-nghiep-ojt-ky-1-2026-2027', "readingTimeMinutes" = 4, "viewCount" = 4860, "uniqueReaderCount" = 4210 WHERE "id" = 'announcement-ute-ojt-internship-semester1';
UPDATE engagement."Announcement" SET "categoryId" = 'cat-career-opps', "coverImageUrl" = '/images/news/tech-career-expo.jpg', "summary" = 'Ngày hội tuyển dụng công nghệ với hơn 30 doanh nghiệp IT tham gia phỏng vấn sinh viên năm cuối.', "slug" = 'ngay-hoi-viec-lam-va-ket-noi-doanh-nghiep-khoa-cntt', "readingTimeMinutes" = 3, "viewCount" = 3980, "uniqueReaderCount" = 3490 WHERE "id" = 'announcement-v26-career-day';

UPDATE engagement."Announcement" SET "categoryId" = 'cat-student-life', "coverImageUrl" = '/images/news/campus-digital-library.jpg', "summary" = 'Hoàn thiện cải tạo phòng học đa năng A101 với hệ thống máy chiếu và âm thanh tương tác cao.', "slug" = 'hoan-thanh-cai-tao-phong-hoc-thong-minh-a101', "readingTimeMinutes" = 2, "viewCount" = 1680, "uniqueReaderCount" = 1450 WHERE "id" = 'announcement-classroom-a101';
UPDATE engagement."Announcement" SET "categoryId" = 'cat-student-life', "coverImageUrl" = '/images/news/campus-digital-library.jpg', "summary" = 'Hướng dẫn sinh viên khai thác cơ sở dữ liệu học thuật quốc tế IEEE Xplore, ScienceDirect.', "slug" = 'khai-thac-nguon-tai-nguyen-hoc-lieu-dien-tu-thu-vien-so', "readingTimeMinutes" = 3, "viewCount" = 2890, "uniqueReaderCount" = 2510 WHERE "id" = 'announcement-library-resources';
UPDATE engagement."Announcement" SET "categoryId" = 'cat-student-life', "coverImageUrl" = '/images/news/campus-digital-library.jpg', "summary" = 'Kênh hỗ trợ giải đáp thủ tục học vụ, đăng ký giấy xác nhận và tiếp nhận ý kiến sinh viên.', "slug" = 'cac-kenh-tiep-nhan-va-giai-dap-thac-mac-hoc-vu-sinh-vien', "readingTimeMinutes" = 2, "viewCount" = 2140, "uniqueReaderCount" = 1860 WHERE "id" = 'announcement-support-channel';
UPDATE engagement."Announcement" SET "categoryId" = 'cat-student-life', "coverImageUrl" = '/images/news/cultural-arts-gala.jpg', "summary" = 'Khởi động câu lạc bộ tiếng Anh chuyên ngành công nghệ IT English Club dành cho sinh viên.', "slug" = 'sinh-hoat-cau-lac-bo-tieng-anh-chuyen-nganh-it-english-club', "readingTimeMinutes" = 3, "viewCount" = 2470, "uniqueReaderCount" = 2150 WHERE "id" = 'announcement-v26-english-club';
UPDATE engagement."Announcement" SET "categoryId" = 'cat-student-life', "coverImageUrl" = '/images/news/student-dormitory-campus.jpg', "summary" = 'Quy hoạch lại bãi giữ xe khu vực A nhằm đảm bảo trật tự và an toàn phòng chống cháy nổ.', "slug" = 'thong-bao-sap-xep-va-dieu-chinh-bai-xe-khu-vuc-a', "readingTimeMinutes" = 2, "viewCount" = 3120, "uniqueReaderCount" = 2740 WHERE "id" = 'announcement-v26-parking-notice';

UPDATE engagement."Announcement" SET "categoryId" = 'cat-awards-honors', "coverImageUrl" = '/images/news/scholarship-ceremony.jpg', "summary" = 'Chương trình học bổng trao đổi học tập 1 năm tại các trường đại học đối tác ở Đức, Nhật Bản và Hàn Quốc.', "slug" = 'chuong-trinh-hoc-bong-trao-doi-sinh-vien-quoc-te-2026', "readingTimeMinutes" = 4, "viewCount" = 4920, "uniqueReaderCount" = 4310 WHERE "id" = 'announcement-v32-exchange-prog';

-- 3. Cấp tập ảnh chi tiết cho 18 bài viết tiêu biểu (ArticleMediaGallery)
INSERT INTO engagement."ArticleMediaGallery" (
    "id", "announcementId", "mediaUrl", "thumbnailUrl", "captionVi", "captionEn", "altText",
    "mediaType", "aspectRatio", "width", "height", "fileSizeBytes", "displayOrder", "isCover"
) VALUES
('gal-semiconductor-cover', 'announcement-semiconductor-cleanroom', '/images/news/semiconductor-cleanroom.jpg', '/images/news/semiconductor-cleanroom.jpg', 'Toàn cảnh không gian phòng sạch Class 1000 và dây chuyền chế tạo vi mạch bán dẫn tiên tiến tại HCM-UTE', 'Panoramic view of the Class 1000 cleanroom and advanced semiconductor fabrication line at HCM-UTE', 'Phòng sạch thí nghiệm Bán dẫn và Vi mạch HCM-UTE', 'IMAGE', '16:9', 1920, 1080, 931639, 0, TRUE),
('gal-semiconductor-wafer-test', 'announcement-semiconductor-cleanroom', '/images/news/semiconductor-cleanroom.jpg', '/images/news/semiconductor-cleanroom.jpg', 'Kỹ sư và giảng viên HCM-UTE vận hành trạm đo kiểm wafer bán dẫn và kính hiển vi quang học độ phân giải cao', 'HCM-UTE engineers and faculty operating the wafer probe station and high-resolution optical inspection system', 'Trạm kiểm thử wafer vi mạch bán dẫn', 'IMAGE', '16:9', 1920, 1080, 931639, 1, FALSE),
('gal-robotics-cover', 'announcement-robotics-iot-lab', '/images/news/robotics-iot-lab.jpg', '/images/news/robotics-iot-lab.jpg', 'Sinh viên kỹ thuật HCM-UTE hiệu chỉnh cánh tay robot công nghiệp KUKA 6 bậc tự do tại xưởng thực hành', 'HCM-UTE engineering students calibrating a 6-DOF KUKA industrial robotic arm in the smart automation workshop', 'Cánh tay robot công nghiệp KUKA và sinh viên thực hành', 'IMAGE', '16:9', 1920, 1080, 962448, 0, TRUE),
('gal-robotics-iot-conveyor', 'announcement-robotics-iot-lab', '/images/news/robotics-iot-lab.jpg', '/images/news/robotics-iot-lab.jpg', 'Hệ thống băng chuyền phân loại tự động ứng dụng thị giác máy tính AI và mạng truyền thông PROFINET', 'Automated sorting conveyor integrated with AI computer vision and PROFINET industrial networking', 'Băng tải công nghiệp IoT phân loại sản phẩm', 'IMAGE', '16:9', 1920, 1080, 962448, 1, FALSE),
('gal-green-summer-cover', 'announcement-green-summer-volunteer', '/images/news/green-summer-volunteer.jpg', '/images/news/green-summer-volunteer.jpg', 'Chiến sĩ tình nguyện Mùa hè xanh UTE chung tay đổ bê tông nâng cấp tuyến đường liên ấp nông thôn mới', 'UTE Green Summer student volunteers concreting a rural road alongside local villagers', 'Chiến sĩ Mùa hè xanh UTE làm đường giao thông nông thôn', 'IMAGE', '16:9', 1920, 1080, 1157960, 0, TRUE),
('gal-green-summer-ceremony', 'announcement-green-summer-volunteer', '/images/news/green-summer-volunteer.jpg', '/images/news/green-summer-volunteer.jpg', 'Không khí hào hùng của hơn 800 đoàn viên thanh niên trong lễ xuất quân tại sân trường trung tâm HCM-UTE', 'Energetic departure ceremony of over 800 youth volunteers at the central campus courtyard of HCM-UTE', 'Lễ xuất quân Chiến dịch tình nguyện Mùa hè xanh', 'IMAGE', '16:9', 1920, 1080, 1157960, 1, FALSE),
('gal-sports-cup-cover', 'announcement-campus-sports-cup', '/images/news/campus-sports-cup.jpg', '/images/news/campus-sports-cup.jpg', 'Pha tranh chấp bóng kịch tính trong trận khai mạc Giải bóng đá sinh viên UTE Champions Cup', 'High-intensity tackle action during the opening match of the UTE Champions Cup Student Football League', 'Trận đấu bóng đá UTE Champions Cup trên sân cỏ nhân tạo', 'IMAGE', '16:9', 1920, 1080, 987815, 0, TRUE),
('gal-sports-cup-fans', 'announcement-campus-sports-cup', '/images/news/campus-sports-cup.jpg', '/images/news/campus-sports-cup.jpg', 'Khán đài rực rỡ cờ hoa và tiếng reo hò cổ vũ nhiệt tình của hàng ngàn sinh viên các khoa', 'Vibrant cheering crowd with flags and drums from thousands of passionate faculty supporters', 'Khán giả sinh viên cổ vũ giải đấu thể thao', 'IMAGE', '16:9', 1920, 1080, 987815, 1, FALSE),
('gal-culture-arts-cover', 'announcement-cultural-arts-gala', '/images/news/cultural-arts-gala.jpg', '/images/news/cultural-arts-gala.jpg', 'Tiết mục múa dân gian đương đại tôn vinh người thầy trong đêm Gala chào mừng Ngày Nhà giáo Việt Nam', 'Contemporary folk dance performance honoring educators during the Teachers Day Gala night', 'Tiết mục ca múa nhạc truyền thống trên sân khấu lớn', 'IMAGE', '16:9', 1920, 1080, 979242, 0, TRUE),
('gal-culture-arts-awards', 'announcement-cultural-arts-gala', '/images/news/cultural-arts-gala.jpg', '/images/news/cultural-arts-gala.jpg', 'Ban Giám hiệu trao cờ lưu niệm và giải thưởng xuất sắc cho đội văn nghệ các khoa đoạt giải cao', 'University Leadership awarding championship trophies to top performing faculty cultural troupes', 'Trao giải thưởng hội diễn văn nghệ học đường', 'IMAGE', '16:9', 1920, 1080, 979242, 1, FALSE),
('gal-stem-conference-cover', 'announcement-stem-conference-keynote', '/images/news/stem-conference-keynote.jpg', '/images/news/stem-conference-keynote.jpg', 'Giáo sư quốc tế trình bày báo cáo chuyên đề về Trí tuệ nhân tạo tạo sinh và ứng dụng trong công nghiệp', 'Keynote address on Generative AI and industrial robotics by renowned international professors', 'Phiên toàn thể Hội nghị Khoa học Quốc tế IEEE tại HCM-UTE', 'IMAGE', '16:9', 1920, 1080, 911577, 0, TRUE),
('gal-stem-conference-poster', 'announcement-stem-conference-keynote', '/images/news/stem-conference-keynote.jpg', '/images/news/stem-conference-keynote.jpg', 'Các nhà khoa học trẻ và sinh viên trao đổi học thuật tại khu vực triển lãm poster công trình nghiên cứu', 'Young researchers and students engaged in academic discussions at the research poster exhibition hall', 'Triển lãm Poster công trình khoa học IEEE', 'IMAGE', '16:9', 1920, 1080, 911577, 1, FALSE),
('gal-ai-hackathon-cover', 'announcement-ai-hackathon-arena', '/images/news/ai-hackathon-arena.jpg', '/images/news/ai-hackathon-arena.jpg', 'Các đội thi tập trung cao độ giải quyết bài toán tối ưu hóa mô hình ngôn ngữ lớn suốt 48 giờ liên tục', 'Competing teams intensely programming and optimizing LLM solutions during the non-stop 48-hour hackathon', 'Sinh viên lập trình thi đấu tại AI Hackathon 48H Arena', 'IMAGE', '16:9', 1920, 1080, 990490, 0, TRUE),
('gal-ai-hackathon-pitch', 'announcement-ai-hackathon-arena', '/images/news/ai-hackathon-arena.jpg', '/images/news/ai-hackathon-arena.jpg', 'Đại diện nhóm thí sinh thuyết trình giải pháp ứng dụng AI trước hội đồng chuyên gia công nghệ', 'Team representatives pitching their innovative AI applications to the enterprise technology jury', 'Thuyết trình sản phẩm AI trước ban giám khảo', 'IMAGE', '16:9', 1920, 1080, 990490, 1, FALSE),
('gal-digital-library-cover', 'announcement-digital-library-hub', '/images/news/campus-digital-library.jpg', '/images/news/campus-digital-library.jpg', 'Không gian tự học hiện đại, tràn ngập ánh sáng tự nhiên tại Tòa nhà Trung tâm Thư viện Số HCM-UTE', 'Modern, naturally lit study commons and collaborative research pods in the HCM-UTE Digital Library Hub', 'Không gian tự học mở tại Thư viện số HCM-UTE', 'IMAGE', '16:9', 1920, 1080, 928668, 0, TRUE),
('gal-digital-library-terminals', 'announcement-digital-library-hub', '/images/news/campus-digital-library.jpg', '/images/news/campus-digital-library.jpg', 'Hệ thống trạm máy tính tốc độ cao tra cứu tài liệu học thuật và cơ sở dữ liệu số hóa quốc tế', 'High-speed digital workstation clusters for accessing international academic databases and journals', 'Trạm tra cứu tài liệu số hóa thư viện', 'IMAGE', '16:9', 1920, 1080, 928668, 1, FALSE),
('gal-commencement-cover', 'announcement-commencement-graduation', '/images/news/commencement-graduation.jpg', '/images/news/commencement-graduation.jpg', 'Niềm tự hào và xúc động của các tân kỹ sư, cử nhân HCM-UTE trong khoảnh khắc nhận bằng tốt nghiệp', 'Pride and emotion of new HCM-UTE engineering and bachelor graduates during the diploma conferral', 'Tân cử nhân và kỹ sư trong lễ tốt nghiệp tại hội trường lớn', 'IMAGE', '16:9', 1920, 1080, 1009045, 0, TRUE),
('gal-commencement-celebration', 'announcement-commencement-graduation', '/images/news/commencement-graduation.jpg', '/images/news/commencement-graduation.jpg', 'Khoảnh khắc tung nón cử nhân đầy hân hoan cùng gia đình và bạn bè trong ngày lễ vinh quy', 'Joyful cap-toss celebration with family and friends commemorating the graduation milestone', 'Khoảnh khắc tung nón cử nhân tốt nghiệp rực rỡ', 'IMAGE', '16:9', 1920, 1080, 1009045, 1, FALSE),
('gal-blood-donation-cover', 'announcement-blood-donation-day', '/images/news/blood-donation-day.jpg', '/images/news/blood-donation-day.jpg', 'Hàng trăm cán bộ, giảng viên và sinh viên HCM-UTE tham gia hiến máu nhân đạo cứu người', 'Hundreds of HCM-UTE faculty, staff, and students participating in the life-saving voluntary blood drive', 'Khu vực tiếp nhận hiến máu tình nguyện Giọt hồng Công nghệ', 'IMAGE', '16:9', 1920, 1080, 871081, 0, TRUE),
('gal-blood-donation-support', 'announcement-blood-donation-day', '/images/news/blood-donation-day.jpg', '/images/news/blood-donation-day.jpg', 'Đội ngũ y bác sĩ Bệnh viện Truyền máu Huyết học khám sàng lọc và chăm sóc tận tình người hiến máu', 'Medical staff and student volunteers providing dedicated care and screening at the donation station', 'Bác sĩ kiểm tra sức khỏe tình nguyện viên hiến máu', 'IMAGE', '16:9', 1920, 1080, 871081, 1, FALSE),
('gal-dormitory-cover', 'announcement-student-dormitory-campus', '/images/news/student-dormitory-campus.jpg', '/images/news/student-dormitory-campus.jpg', 'Khuôn viên xanh rợp bóng mát cùng sân bóng rổ, khu tập thể thao ngoài trời tại Ký túc xá HCM-UTE', 'Lush green campus environment, outdoor basketball courts, and fitness facilities at HCM-UTE Dormitory', 'Khuôn viên ký túc xá sinh viên hiện đại và thân thiện', 'IMAGE', '16:9', 1920, 1080, 1224097, 0, TRUE),
('gal-dormitory-study-room', 'announcement-student-dormitory-campus', '/images/news/student-dormitory-campus.jpg', '/images/news/student-dormitory-campus.jpg', 'Phòng tự học nội trú yên tĩnh với kết nối Wi-Fi tốc độ cao và máy lọc nước thông minh', 'Quiet resident study lounge equipped with high-speed Wi-Fi and smart drinking water systems', 'Phòng tự học nội trú tiện nghi tại ký túc xá', 'IMAGE', '16:9', 1920, 1080, 1224097, 1, FALSE),
('gal-faculty-awards-cover', 'announcement-faculty-excellence-awards', '/images/news/faculty-excellence-awards.jpg', '/images/news/faculty-excellence-awards.jpg', 'Ban Giám hiệu trao kỷ niệm chương và vinh danh các giảng viên có thành tích xuất sắc trong giảng dạy và nghiên cứu', 'University Leadership honoring distinguished faculty members for outstanding teaching and research excellence', 'Lễ trao giải thưởng tôn vinh nhà giáo và nhà khoa học HCM-UTE', 'IMAGE', '16:9', 1920, 1080, 898444, 0, TRUE),
('gal-faculty-awards-tribute', 'announcement-faculty-excellence-awards', '/images/news/faculty-excellence-awards.jpg', '/images/news/faculty-excellence-awards.jpg', 'Tập thể sư phạm HCM-UTE chụp ảnh lưu niệm ghi dấu một năm học nhiều thành tựu đột phá', 'Faculty and researchers gathering for a memorable group photo celebrating another year of academic milestones', 'Tập thể giảng viên tiêu biểu chụp ảnh lưu niệm', 'IMAGE', '16:9', 1920, 1080, 898444, 1, FALSE),
('gal-bigdata-ai-cover', 'announcement-ute-bigdata-ai-center', '/images/news/bigdata-ai-lab.jpg', '/images/news/bigdata-ai-lab.jpg', 'Trung tâm dữ liệu máy chủ tính toán hiệu năng cao GPU NVIDIA A100 phục vụ huấn luyện mô hình Deep Learning', 'High-Performance Computing data center with NVIDIA A100 GPUs powering deep learning and big data workloads', 'Hệ thống máy chủ GPU điện toán đám mây AI Lab', 'IMAGE', '16:9', 1920, 1080, 917486, 0, TRUE),
('gal-bigdata-ai-monitoring', 'announcement-ute-bigdata-ai-center', '/images/news/bigdata-ai-lab.jpg', '/images/news/bigdata-ai-lab.jpg', 'Màn hình giám sát tài nguyên tính toán và lưu trữ phân tán Ceph theo thời gian thực', 'Real-time operations dashboard monitoring distributed Ceph storage and cluster GPU utilization', 'Màn hình điều hành trung tâm giám sát mạng máy chủ AI', 'IMAGE', '16:9', 1920, 1080, 917486, 1, FALSE),
('gal-tech-career-cover', 'announcement-ute-career-fair-tech-2026', '/images/news/tech-career-expo.jpg', '/images/news/tech-career-expo.jpg', 'Hàng ngàn sinh viên tìm hiểu cơ hội tuyển dụng tại các gian hàng doanh nghiệp công nghệ hàng đầu', 'Thousands of students engaging with leading tech employers at the annual UTE Career Expo', 'Không khí sôi động tại Ngày hội Việc làm Công nghệ UTE', 'IMAGE', '16:9', 1920, 1080, 979470, 0, TRUE),
('gal-tech-career-interview', 'announcement-ute-career-fair-tech-2026', '/images/news/tech-career-expo.jpg', '/images/news/tech-career-expo.jpg', 'Sinh viên năm cuối tự tin phỏng vấn thử và nhận lời mời thực tập trực tiếp từ nhà tuyển dụng', 'Senior students attending direct technical interviews and receiving immediate internship offers', 'Phỏng vấn tuyển dụng trực tiếp tại gian hàng doanh nghiệp', 'IMAGE', '16:9', 1920, 1080, 979470, 1, FALSE),
('gal-scholarship-cover', 'announcement-ute-scholarship-dr-criteria', '/images/news/scholarship-ceremony.jpg', '/images/news/scholarship-ceremony.jpg', 'Lễ trao học bổng tài năng và học bổng khuyến khích cho các thủ khoa, sinh viên vượt khó học giỏi', 'Awarding talent and merit scholarships to valedictorians and exemplary hardworking students', 'Trao học bổng tài năng cho sinh viên xuất sắc', 'IMAGE', '16:9', 1920, 1080, 951871, 0, TRUE),
('gal-scholarship-cheque', 'announcement-ute-scholarship-dr-criteria', '/images/news/scholarship-ceremony.jpg', '/images/news/scholarship-ceremony.jpg', 'Đại diện các tập đoàn tài trợ trao bảng tượng trưng học bổng đồng hành cùng thế hệ tương lai', 'Corporate sponsors presenting symbolic scholarship grant cheques to student representatives', 'Doanh nghiệp trao bảng biểu trưng học bổng sinh viên', 'IMAGE', '16:9', 1920, 1080, 951871, 1, FALSE),
('gal-course-reg-cover', 'announcement-ute-course-reg-official', '/images/news/course-registration.jpg', '/images/news/course-registration.jpg', 'Giao diện phân luồng đăng ký môn học trực tuyến trên Cổng thông tin Đào tạo CampusUTE', 'Student registration portal interface on CampusUTE providing high-throughput course enrollment', 'Cổng đăng ký học phần trực tuyến CampusUTE', 'IMAGE', '16:9', 1920, 1080, 966391, 0, TRUE),
('gal-course-reg-support', 'announcement-ute-course-reg-official', '/images/news/course-registration.jpg', '/images/news/course-registration.jpg', 'Đội ngũ chuyên viên Phòng Đào tạo trực tuyến hỗ trợ sinh viên điều chỉnh học phần theo đúng lộ trình', 'Academic Affairs advising specialists assisting students with custom study plans and registration', 'Tư vấn và hỗ trợ học vụ đăng ký môn học', 'IMAGE', '16:9', 1920, 1080, 966391, 1, FALSE),
('gal-thesis-defense-cover', 'announcement-ute-thesis-registration-fall', '/images/news/thesis-defense.jpg', '/images/news/thesis-defense.jpg', 'Sinh viên báo cáo đề tài khóa luận trước Hội đồng chấm thi chuyên môn Khoa Công nghệ Thông tin', 'Students defending their final capstone thesis in front of the IT Faculty Academic Evaluation Council', 'Bảo vệ khóa luận tốt nghiệp trước hội đồng khoa học', 'IMAGE', '16:9', 1920, 1080, 871181, 0, TRUE),
('gal-thesis-defense-demo', 'announcement-ute-thesis-registration-fall', '/images/news/thesis-defense.jpg', '/images/news/thesis-defense.jpg', 'Trình diễn trực tiếp mô hình phần cứng và hệ thống phần mềm thử nghiệm trước hội đồng đánh giá', 'Live hardware demonstration and software benchmarking session during the capstone defense', 'Trình diễn demo sản phẩm đồ án trước hội đồng', 'IMAGE', '16:9', 1920, 1080, 871181, 1, FALSE),
('gal-scientific-research-cover', 'announcement-ute-student-scientific-research-awards', '/images/news/scientific-research.jpg', '/images/news/scientific-research.jpg', 'Triển lãm các sản phẩm công nghệ sáng tạo tham gia Vòng chung kết Giải thưởng NCKH Sinh viên UTE', 'Exhibition of innovative technology products at the UTE Student Scientific Research Finals', 'Triển lãm đề tài Nghiên cứu khoa học sinh viên HCM-UTE', 'IMAGE', '16:9', 1920, 1080, 1048656, 0, TRUE),
('gal-scientific-research-awards', 'announcement-ute-student-scientific-research-awards', '/images/news/scientific-research.jpg', '/images/news/scientific-research.jpg', 'Trao giải Nhất và cúp vinh danh cho nhóm sinh viên có công trình đăng ký bằng độc quyền sáng chế', 'First prize trophy award ceremony honoring the student research team with a patented invention', 'Lễ trao giải Nhất nghiên cứu khoa học sinh viên', 'IMAGE', '16:9', 1920, 1080, 1048656, 1, FALSE)
ON CONFLICT ("id") DO UPDATE SET
    "mediaUrl" = EXCLUDED."mediaUrl",
    "thumbnailUrl" = EXCLUDED."thumbnailUrl",
    "captionVi" = EXCLUDED."captionVi",
    "captionEn" = EXCLUDED."captionEn",
    "altText" = EXCLUDED."altText",
    "width" = EXCLUDED."width",
    "height" = EXCLUDED."height",
    "fileSizeBytes" = EXCLUDED."fileSizeBytes",
    "displayOrder" = EXCLUDED."displayOrder",
    "isCover" = EXCLUDED."isCover";

-- 4. Bổ sung tài liệu đính kèm chính thức (ArticleAttachment)
INSERT INTO engagement."ArticleAttachment" (
    "id", "announcementId", "fileName", "fileUrl", "fileSizeBytes", "mimeType", "checksumSha256", "downloadCount", "isPublic"
) VALUES
('att-rector-academic-plan', 'announcement-rector-new-academic-year-decision', 'Quyet_dinh_Ban_hanh_Ke_hoach_Nam_hoc_2026_2027.pdf', '/documents/announcements/Quyet_dinh_Ke_hoach_Nam_hoc_2026_2027.pdf', 2154800, 'application/pdf', 'a1b2c3d4e5f67890123456789abcdef0123456789abcdef0123456789abcdef0', 1240, TRUE),
('att-digital-library-guide', 'announcement-digital-library-hub', 'Huong_dan_Su_dung_Thuc_dia_Thu_vien_So_24_7.pdf', '/documents/announcements/Huong_dan_Thu_vien_So_24_7.pdf', 1684500, 'application/pdf', 'b2c3d4e5f67890123456789abcdef0123456789abcdef0123456789abcdef012', 895, TRUE),
('att-dormitory-handbook', 'announcement-student-dormitory-campus', 'Noi_quy_va_So_tay_Kytucxa_Sinhvien_2026.pdf', '/documents/announcements/So_tay_KTX_Sinh_vien_2026.pdf', 2894100, 'application/pdf', 'c3d4e5f67890123456789abcdef0123456789abcdef0123456789abcdef0123', 720, TRUE),
('att-faculty-honors-list', 'announcement-faculty-excellence-awards', 'Danh_sach_Tuyen_duong_Giang_vien_Xuat_sac_2025_2026.pdf', '/documents/announcements/Danh_sach_Giang_vien_Xuat_sac_2026.pdf', 1432600, 'application/pdf', 'd4e5f67890123456789abcdef0123456789abcdef0123456789abcdef01234', 640, TRUE),
('att-blood-donation-guidelines', 'announcement-blood-donation-day', 'Huong_dan_Y_te_va_Dang_ky_Hien_mau_Giot_hong_2026.pdf', '/documents/announcements/Huong_dan_Hien_mau_Giot_hong_2026.pdf', 980700, 'application/pdf', 'e5f67890123456789abcdef0123456789abcdef0123456789abcdef012345', 530, TRUE),
('att-cultural-gala-plan', 'announcement-cultural-arts-gala', 'Ke_hoach_To_chuc_Hoi_dien_Van_nghe_Truyen_thong_2026.pdf', '/documents/announcements/Ke_hoach_Hoi_dien_Van_nghe_2026.pdf', 1845300, 'application/pdf', 'f67890123456789abcdef0123456789abcdef0123456789abcdef0123456', 410, TRUE),
('att-bigdata-gpu-bylaws', 'announcement-ute-bigdata-ai-center', 'Quy_che_Khai_thac_Cum_may_chu_GPU_AI_Lab.pdf', '/documents/announcements/Quy_che_Khai_thac_GPU_AI_Lab.pdf', 2340100, 'application/pdf', '0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef', 980, TRUE),
('att-career-expo-handbook', 'announcement-ute-career-fair-tech-2026', 'Cam_nang_Doanh_nghiep_Tech_Career_Expo_2026.pdf', '/documents/announcements/Cam_nang_Tech_Career_Expo_2026.pdf', 3420500, 'application/pdf', '11223344556677889900aabbccddeeff11223344556677889900aabbccddeeff', 1560, TRUE),
('att-scholarship-criteria-doc', 'announcement-ute-scholarship-dr-criteria', 'Quy_dinh_Xet_cap_Hoc_bong_Khuyen_khich_va_Doanh_nghiep.pdf', '/documents/announcements/Quy_dinh_Hoc_bong_Khuyen_khich_2026.pdf', 1920400, 'application/pdf', '22334455667788990011aabbccddeeff22334455667788990011aabbccddeeff', 2150, TRUE),
('att-course-reg-handbook', 'announcement-ute-course-reg-official', 'So_tay_Huong_dan_Dang_ky_Hoc_phan_CampusUTE.pdf', '/documents/announcements/So_tay_Dang_ky_Hoc_phan_CampusUTE.pdf', 2750300, 'application/pdf', '33445566778899001122aabbccddeeff33445566778899001122aabbccddeeff', 3420, TRUE),
('att-thesis-guidelines-doc', 'announcement-ute-thesis-registration-fall', 'Quy_dinh_va_Bieu_mau_Bao_ve_Khoa_luan_Tot_nghiep.pdf', '/documents/announcements/Quy_dinh_Bieu_mau_KLTN_2026.pdf', 2480600, 'application/pdf', '44556677889900112233aabbccddeeff44556677889900112233aabbccddeeff', 1890, TRUE),
('att-student-research-rules', 'announcement-ute-student-scientific-research-awards', 'The_le_Giai_thuong_Nghien_cuu_Khoa_hoc_Sinh_vien_2026.pdf', '/documents/announcements/The_le_Giai_thuong_NCKH_2026.pdf', 1760800, 'application/pdf', '55667788990011223344aabbccddeeff55667788990011223344aabbccddeeff', 1120, TRUE)
ON CONFLICT ("id") DO UPDATE SET
    "fileName" = EXCLUDED."fileName",
    "fileUrl" = EXCLUDED."fileUrl",
    "fileSizeBytes" = EXCLUDED."fileSizeBytes",
    "checksumSha256" = EXCLUDED."checksumSha256",
    "downloadCount" = EXCLUDED."downloadCount";

-- 5. Bổ sung dữ liệu đọc thực tế (ArticleReadingMetric)
INSERT INTO engagement."ArticleReadingMetric" (
    "id", "announcementId", "readerUserId", "ipAddressHash", "userAgentHash",
    "dwellTimeSeconds", "readPercentage", "firstReadAt", "lastReadAt"
) VALUES
('metric-semi-01', 'announcement-semiconductor-cleanroom', 'student-user-105', 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855', '1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e4f5a6b7c8d9e0f1a2b', 240, 100, NOW() - INTERVAL '3 days', NOW() - INTERVAL '3 days' + INTERVAL '4 minutes'),
('metric-semi-02', 'announcement-semiconductor-cleanroom', 'student-user-117', 'a1b2c3d4e5f67890123456789abcdef0123456789abcdef0123456789abcdef0', '2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e4f5a6b7c8d9e0f1a2b3c', 195, 85, NOW() - INTERVAL '2 days', NOW() - INTERVAL '2 days' + INTERVAL '3 minutes'),
('metric-robot-01', 'announcement-robotics-iot-lab', 'student-user-118', 'c3d4e5f67890123456789abcdef0123456789abcdef0123456789abcdef0123', '3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e4f5a6b7c8d9e0f1a2b3c4d', 310, 100, NOW() - INTERVAL '4 days', NOW() - INTERVAL '4 days' + INTERVAL '5 minutes'),
('metric-green-01', 'announcement-green-summer-volunteer', 'student-user-119', 'd4e5f67890123456789abcdef0123456789abcdef0123456789abcdef01234', '4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e4f5a6b7c8d9e0f1a2b3c4d5e', 180, 90, NOW() - INTERVAL '5 days', NOW() - INTERVAL '5 days' + INTERVAL '3 minutes'),
('metric-sports-01', 'announcement-campus-sports-cup', 'student-user-120', 'e5f67890123456789abcdef0123456789abcdef0123456789abcdef012345', '5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e4f5a6b7c8d9e0f1a2b3c4d5e6f', 150, 75, NOW() - INTERVAL '1 day', NOW() - INTERVAL '1 day' + INTERVAL '2 minutes'),
('metric-hackathon-01', 'announcement-ai-hackathon-arena', 'student-user-121', 'f67890123456789abcdef0123456789abcdef0123456789abcdef0123456', '6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e4f5a6b7c8d9e0f1a2b3c4d5e6f7a', 420, 100, NOW() - INTERVAL '6 days', NOW() - INTERVAL '6 days' + INTERVAL '7 minutes'),
('metric-library-01', 'announcement-digital-library-hub', 'student-user-122', '0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef', '7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e4f5a6b7c8d9e0f1a2b3c4d5e6f7a8b', 210, 95, NOW() - INTERVAL '7 days', NOW() - INTERVAL '7 days' + INTERVAL '4 minutes'),
('metric-grad-01', 'announcement-commencement-graduation', 'student-user-123', '11223344556677889900aabbccddeeff11223344556677889900aabbccddeeff', '8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e4f5a6b7c8d9e0f1a2b3c4d5e6f7a8b9c', 280, 100, NOW() - INTERVAL '8 days', NOW() - INTERVAL '8 days' + INTERVAL '5 minutes')
ON CONFLICT ("id") DO NOTHING;

-- 6. Hoàn thiện Kho lưu trữ luận văn & báo cáo tốt nghiệp (thesis_group_report)
INSERT INTO thesis.thesis_group_report (
    id, group_id, round_id, submitted_by, title, url, note, submitted_at, updated_at,
    file_name, file_type, file_size, storage_provider, storage_bucket, storage_key, file_sha256
) VALUES
(
    '66666666-6666-6666-6666-666666666011',
    '55555555-5555-5555-5555-555544444011',
    '22222222-2222-2222-2222-222222222101',
    'student-profile-002',
    'Báo cáo Toàn văn Khóa luận Tốt nghiệp - Hệ thống giám sát giao thông đô thị thời gian thực ứng dụng AI Camera và Edge Computing',
    'https://theses.campusute.io.vn/2026/traffic-ai-camera.pdf',
    'Báo cáo hoàn chỉnh kèm mã nguồn GitHub, mô hình YOLOv11 TensorRT và bộ dữ liệu giao thông ngã tư Thủ Đức',
    NOW() - INTERVAL '5 days',
    NOW() - INTERVAL '5 days',
    'KLTN_AI_Camera_EdgeComputing_Traffic_2026.pdf',
    'application/pdf',
    18450200,
    's3',
    'campuscore-theses',
    '2026/kltn/traffic-ai-camera.pdf',
    'a1b2c3d4e5f67890123456789abcdef0123456789abcdef0123456789abcdef0'
),
(
    '66666666-6666-6666-6666-666666666012',
    '55555555-5555-5555-5555-555544444012',
    '22222222-2222-2222-2222-222222222101',
    'student-profile-004',
    'Báo cáo Luận văn Tốt nghiệp - Nền tảng phân tích cảm xúc khách hàng đa kênh với Big Data và Mô hình ngôn ngữ lớn LLM',
    'https://theses.campusute.io.vn/2026/sentiment-bigdata-llm.pdf',
    'Hệ thống tích hợp cụm Apache Spark, Kafka và mô hình Llama-3 fine-tuned trên tiếng Việt thương mại điện tử',
    NOW() - INTERVAL '6 days',
    NOW() - INTERVAL '6 days',
    'KLTN_Sentiment_BigData_LLM_Platform_2026.pdf',
    'application/pdf',
    24150800,
    's3',
    'campuscore-theses',
    '2026/kltn/sentiment-bigdata-llm.pdf',
    'b2c3d4e5f67890123456789abcdef0123456789abcdef0123456789abcdef012'
),
(
    '66666666-6666-6666-6666-666666666013',
    '55555555-5555-5555-5555-555544444013',
    '22222222-2222-2222-2222-222222222101',
    'student-profile-006',
    'Báo cáo Đồ án Tốt nghiệp Kỹ sư - Thiết kế và chế tạo Robot tự hành AGV vận chuyển hàng trong kho thông minh với định vị SLAM',
    'https://theses.campusute.io.vn/2026/robot-agv-slam.pdf',
    'Bản vẽ thiết kế cơ khí SolidWorks 3D, sơ đồ mạch PCB điều khiển ROS2 Navigation Stack và kết quả thử nghiệm thực địa',
    NOW() - INTERVAL '7 days',
    NOW() - INTERVAL '7 days',
    'DATN_Robot_AGV_SmartWarehouse_SLAM_2026.pdf',
    'application/pdf',
    32600400,
    's3',
    'campuscore-theses',
    '2026/datn/robot-agv-slam.pdf',
    'c3d4e5f67890123456789abcdef0123456789abcdef0123456789abcdef0123'
),
(
    '66666666-6666-6666-6666-666666666014',
    '55555555-5555-5555-5555-555544444014',
    '22222222-2222-2222-2222-222222222101',
    'student-profile-008',
    'Báo cáo Khóa luận Kỹ sư - Hệ thống quản lý và tối ưu hóa lưới điện mặt trời áp mái phân tán ứng dụng IoT & AI',
    'https://theses.campusute.io.vn/2026/solar-microgrid-iot.pdf',
    'Mô phỏng MATLAB/Simulink hệ thống biến tần hòa lưới và thuật toán MPPT tối ưu theo điều kiện thời tiết thực tế',
    NOW() - INTERVAL '8 days',
    NOW() - INTERVAL '8 days',
    'KLTN_Solar_Microgrid_IoT_AI_Optimization_2026.pdf',
    'application/pdf',
    19800300,
    's3',
    'campuscore-theses',
    '2026/kltn/solar-microgrid-iot.pdf',
    'd4e5f67890123456789abcdef0123456789abcdef0123456789abcdef01234'
),
(
    '66666666-6666-6666-6666-666666666016',
    '55555555-5555-5555-5555-555544444016',
    '22222222-2222-2222-2222-222222222101',
    'student-profile-002',
    'Báo cáo Đồ án Kỹ sư Công nghệ Ô tô - Nghiên cứu tối ưu hóa hệ thống quản lý năng lượng pin (BMS) trên xe điện thông minh',
    'https://theses.campusute.io.vn/2026/ev-battery-bms.pdf',
    'Thử nghiệm mạch đo đạc nội trở, ước lượng trạng thái sạc (SoC) và tình trạng sức khỏe pin (SoH) bằng mạng nơ-ron LSTM',
    NOW() - INTERVAL '9 days',
    NOW() - INTERVAL '9 days',
    'DATN_EV_Battery_Management_System_BMS_2026.pdf',
    'application/pdf',
    27400500,
    's3',
    'campuscore-theses',
    '2026/datn/ev-battery-bms.pdf',
    'e5f67890123456789abcdef0123456789abcdef0123456789abcdef012345'
),
(
    '66666666-6666-6666-6666-666666666017',
    '55555555-5555-5555-5555-555544444017',
    '22222222-2222-2222-2222-222222222101',
    'student-profile-003',
    'Báo cáo Khóa luận Kỹ sư Xây dựng - Ứng dụng mô hình BIM 5D và tiêu chuẩn công trình xanh LEED trong quản lý vòng đời dự án',
    'https://theses.campusute.io.vn/2026/bim-5d-leed.pdf',
    'Mô hình Revit 3D, tiến độ thi công Navisworks 4D và dự toán chi phí chi tiết 5D cho dự án Khu phức hợp công nghệ cao',
    NOW() - INTERVAL '10 days',
    NOW() - INTERVAL '10 days',
    'KLTN_BIM_5D_GreenBuilding_LEED_2026.pdf',
    'application/pdf',
    35120000,
    's3',
    'campuscore-theses',
    '2026/kltn/bim-5d-leed.pdf',
    'f67890123456789abcdef0123456789abcdef0123456789abcdef0123456'
),
(
    '66666666-6666-6666-6666-666666666026',
    '55555555-5555-5555-5555-555544444026',
    '22222222-2222-2222-2222-222222222101',
    'student-profile-004',
    'Báo cáo Khóa luận Thạc sĩ / Kỹ sư Tài năng - Hệ thống Multi-Agent AI tự trị điều phối vận hành nhà kho logistics thông minh',
    'https://theses.campusute.io.vn/2026/multi-agent-logistics.pdf',
    'Mô phỏng phối hợp phân tán 50 agent tự hành không xung đột đường đi với Reinforcement Learning MAPPO',
    NOW() - INTERVAL '11 days',
    NOW() - INTERVAL '11 days',
    'KLTN_MultiAgent_Autonomous_Logistics_Warehouse_2026.pdf',
    'application/pdf',
    22800900,
    's3',
    'campuscore-theses',
    '2026/kltn/multi-agent-logistics.pdf',
    '0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef'
),
(
    '66666666-6666-6666-6666-666666666027',
    '55555555-5555-5555-5555-555544444027',
    '22222222-2222-2222-2222-222222222101',
    'student-profile-005',
    'Báo cáo Khóa luận Tốt nghiệp - Nền tảng phát hiện gian lận giao dịch tài chính thời gian thực với Graph Neural Networks (GNN)',
    'https://theses.campusute.io.vn/2026/fraud-detection-gnn.pdf',
    'Đồ thị luân chuyển dòng tiền hơn 10 triệu giao dịch ngân hàng với latency suy luận dưới 15ms qua Neo4j và PyTorch Geometric',
    NOW() - INTERVAL '12 days',
    NOW() - INTERVAL '12 days',
    'KLTN_Financial_Fraud_Detection_GNN_Realtime_2026.pdf',
    'application/pdf',
    21350400,
    's3',
    'campuscore-theses',
    '2026/kltn/fraud-detection-gnn.pdf',
    '11223344556677889900aabbccddeeff11223344556677889900aabbccddeeff'
)
ON CONFLICT ("id") DO UPDATE SET
    title = EXCLUDED.title,
    url = EXCLUDED.url,
    note = EXCLUDED.note,
    file_name = EXCLUDED.file_name,
    file_type = EXCLUDED.file_type,
    file_size = EXCLUDED.file_size,
    file_sha256 = EXCLUDED.file_sha256;

-- 7. Nghiệp vụ Đơn nâng hạn mức tín chỉ (CreditLimitApplication)
INSERT INTO academic."CreditLimitApplication" (
    "id", "studentId", "semesterId", "roundId", "requestedLimit", "reason",
    "status", "reviewedBy", "reviewedAt", "reviewerNote", "createdAt", "updatedAt", "version"
) VALUES
(
    'cla-demo-001',
    'student-profile',
    'semester-demo',
    'round-registration-current-demo',
    30,
    'Em đã hoàn thành 125 tín chỉ tích lũy với GPA 3.82/4.0. Em làm đơn này kính xin Phòng Đào tạo xem xét cho phép em nâng trần hạn mức đăng ký học phần lên 30 tín chỉ trong Học kỳ 1 năm học 2026-2027 để đăng ký Khóa luận tốt nghiệp và hoàn thành sớm chương trình đào tạo.',
    'APPROVED',
    'admin-user',
    NOW() - INTERVAL '2 days',
    'Hồ sơ sinh viên đạt điều kiện học lực Xuất sắc (GPA > 3.6, ĐRL Xuất sắc). Phòng Đào tạo đồng ý phê duyệt trần 30 tín chỉ theo quy định ngoại lệ Điều 14 Quy chế Đào tạo tín chỉ.',
    NOW() - INTERVAL '3 days',
    NOW() - INTERVAL '2 days',
    1
),
(
    'cla-demo-002',
    'student-profile-002',
    'semester-demo',
    'round-registration-current-demo',
    30,
    'Em làm đơn xin nâng hạn mức đăng ký từ 28 lên 30 tín chỉ để kịp học phần thay thế tốt nghiệp và học phần Tiếng Anh chuyên ngành.',
    'APPROVED',
    'admin-user',
    NOW() - INTERVAL '1 day',
    'Chấp thuận. Sinh viên thuộc diện tích lũy tiến độ tốt nghiệp nhanh.',
    NOW() - INTERVAL '2 days',
    NOW() - INTERVAL '1 day',
    1
),
(
    'cla-demo-003',
    'student-profile-003',
    'semester-demo',
    'round-registration-current-demo',
    30,
    'Kính xin Phòng Đào tạo cấp phép nâng trần đăng ký lên 30 tín chỉ do cần học thêm học phần chuyên ngành tự chọn và hoàn thành tiến độ đào tạo.',
    'PENDING',
    NULL,
    NULL,
    NULL,
    NOW() - INTERVAL '12 hours',
    NOW() - INTERVAL '12 hours',
    0
)
ON CONFLICT ("id") DO UPDATE SET
    "requestedLimit" = EXCLUDED."requestedLimit",
    "reason" = EXCLUDED."reason",
    "status" = EXCLUDED."status",
    "reviewedBy" = EXCLUDED."reviewedBy",
    "reviewedAt" = EXCLUDED."reviewedAt",
    "reviewerNote" = EXCLUDED."reviewerNote";

-- 8. Điểm danh sinh viên (Attendance)
INSERT INTO academic."Attendance" (
    "id", "studentId", "sectionId", "date", "status", "notes", "createdAt", "updatedAt"
) VALUES
('att-demo-cloud-01', 'student-profile', 'section-cloud-demo', NOW() - INTERVAL '14 days', 'PRESENT', 'Tham gia đầy đủ buổi học Giới thiệu kiến trúc Điện toán đám mây & AWS Core Services', NOW() - INTERVAL '14 days', NOW() - INTERVAL '14 days'),
('att-demo-cloud-02', 'student-profile', 'section-cloud-demo', NOW() - INTERVAL '7 days', 'PRESENT', 'Thực hành cấu hình VPC, Subnet và triển khai EC2 Cluster', NOW() - INTERVAL '7 days', NOW() - INTERVAL '7 days'),
('att-demo-algo-01', 'student-profile', 'section-algorithms-demo', NOW() - INTERVAL '12 days', 'PRESENT', 'Thuyết trình thuật toán đồ thị và Dijkstra cải tiến', NOW() - INTERVAL '12 days', NOW() - INTERVAL '12 days'),
('att-demo-algo-02', 'student-profile', 'section-algorithms-demo', NOW() - INTERVAL '5 days', 'PRESENT', 'Làm bài kiểm tra thực hành giải thuật Quy hoạch động', NOW() - INTERVAL '5 days', NOW() - INTERVAL '5 days'),
('att-demo-db-01', 'student-profile', 'section-database-demo', NOW() - INTERVAL '10 days', 'PRESENT', 'Phân tích chuẩn hóa cơ sở dữ liệu BCNF và chỉ mục B-Tree', NOW() - INTERVAL '10 days', NOW() - INTERVAL '10 days'),
('att-demo-arch-01', 'student-profile', 'section-architecture-demo', NOW() - INTERVAL '8 days', 'PRESENT', 'Thiết kế kiến trúc hướng sự kiện Event-Driven Architecture với Apache Kafka', NOW() - INTERVAL '8 days', NOW() - INTERVAL '8 days'),
('att-demo-java-01', 'student-profile', 'section-java-demo', NOW() - INTERVAL '9 days', 'PRESENT', 'Xây dựng ứng dụng đa luồng Virtual Threads trên Java 25', NOW() - INTERVAL '9 days', NOW() - INTERVAL '9 days')
ON CONFLICT ("id") DO UPDATE SET
    "status" = EXCLUDED."status",
    "notes" = EXCLUDED."notes";

-- 9. Nhật ký kiểm toán quản trị (AdminAudit)
INSERT INTO campuscore_audit."AdminAudit" (
    "id", "actorId", "actorLabel", "action", "entityType", "entityId", "summary",
    "beforeState", "afterState", "createdAt"
) VALUES
('audit-demo-001', 'admin-user', 'Quản trị viên Học vụ (admin@campuscore.edu)', 'OPEN_REGISTRATION_ROUND', 'RegistrationRound', 'round-registration-current-demo', 'Mở đợt đăng ký học phần chính thức Học kỳ 1 năm học 2026-2027 cho toàn thể sinh viên các khóa', '{"status": "SCHEDULED", "isOpen": false}', '{"status": "OPEN", "isOpen": true, "maxCredits": 28, "allowOverloadApplication": true}', NOW() - INTERVAL '10 days'),
('audit-demo-002', 'admin-user', 'Quản trị viên Học vụ (admin@campuscore.edu)', 'APPROVE_CREDIT_LIMIT', 'CreditLimitApplication', 'cla-demo-001', 'Phê duyệt nâng hạn mức tín chỉ đặc cách lên 30 tín chỉ cho sinh viên Nguyễn Tiến Sơn (MSSV: 24110054)', '{"status": "PENDING_REVIEW", "requestedLimit": 30}', '{"status": "APPROVED", "approvedLimit": 30, "reviewerNote": "Đạt điều kiện GPA Xuất sắc 3.82"}', NOW() - INTERVAL '2 days'),
('audit-demo-003', 'admin-user', 'Ban Biên tập Website (admin@campuscore.edu)', 'PUBLISH_EDITORIAL_ARTICLE', 'Announcement', 'announcement-semiconductor-cleanroom', 'Công bố bài viết tiêu biểu: Khánh thành Phòng thí nghiệm Bán dẫn & Vi mạch Cleanroom chuẩn quốc tế tại HCM-UTE', '{"status": "PENDING_REVIEW"}', '{"status": "PUBLISHED", "featuredOrder": 1, "hasGallery": true, "attachmentCount": 1}', NOW() - INTERVAL '9 days'),
('audit-demo-004', 'admin-user', 'Ban Quản lý Luận văn Khoa CNTT', 'CONFIGURE_THESIS_DEFENSE', 'ThesisRound', '22222222-2222-2222-2222-222222222101', 'Thiết lập hội đồng đánh giá và lịch bảo vệ Khóa luận tốt nghiệp đợt 2 cho 12 nhóm nghiên cứu', '{"councilsFormed": 0, "status": "IN_PROGRESS"}', '{"councilsFormed": 8, "status": "DEFENSE_SCHEDULED", "topicsAssigned": 16}', NOW() - INTERVAL '6 days'),
('audit-demo-005', 'admin-user', 'Phòng Khảo thí & Đảm bảo Chất lượng', 'SYNC_STUDENT_CONDUCT_SCORES', 'ConductSemesterScore', 'semester-history-demo', 'Hoàn tất đối soát và khóa sổ điểm rèn luyện toàn trường Học kỳ 2 năm học 2025-2026', '{"auditState": "UNLOCKED"}', '{"auditState": "LOCKED", "totalScoresProjected": 678, "avgScore": 84.5}', NOW() - INTERVAL '15 days')
ON CONFLICT ("id") DO NOTHING;
