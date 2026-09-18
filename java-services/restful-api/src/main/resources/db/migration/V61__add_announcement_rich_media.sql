-- Flyway Migration V61: Add Announcement Rich Media (Images, Figures, and Captions)
-- Enriches top academic announcements in engagement."Announcement" with authentic 16:9 news photography and figcaptions

-- 1. Big Data & AI Research Lab
UPDATE engagement."Announcement"
SET content = '<p>Khoa Công nghệ Thông tin - Trường Đại học Sư phạm Kỹ thuật TP.HCM trân trọng thông báo chính thức khánh thành và đưa vào vận hành cụm máy chủ điện toán hiệu năng cao (HPC Cluster) phục vụ nghiên cứu Dữ liệu lớn (Big Data) và Trí tuệ nhân tạo (AI Lab) tại cơ sở chính.</p>
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
    "updatedAt" = NOW(),
    version = version + 1
WHERE id IN ('announcement-ute-bigdata-ai-center', 'announcement-v32-smart-campus');

-- 2. Tech Career Expo & Job Fair
UPDATE engagement."Announcement"
SET content = '<p>Nhà trường phối hợp cùng hơn 60 tập đoàn công nghệ đa quốc gia và doanh nghiệp công nghệ thông tin hàng đầu tổ chức Ngày hội Việc làm và Kết nối Doanh nghiệp UTE Tech Career Expo 2026 tại khuôn viên sảnh A & B.</p>
<figure class="my-5 overflow-hidden rounded-xl border border-border/70 bg-secondary/15 p-2">
  <img src="/images/news/tech-career-expo.jpg" alt="Ngày hội việc làm UTE Tech Career Expo" class="w-full h-auto rounded-lg object-cover" />
  <figcaption class="pt-2 text-center text-xs font-medium text-muted-foreground italic">
    Không khí sôi nổi tại UTE Tech Career Expo 2026 với hơn 60 tập đoàn công nghệ và 1.200 vị trí tuyển dụng kỹ sư
  </figcaption>
</figure>
<p>Sự kiện mang đến hàng nghìn cơ hội nghề nghiệp chất lượng cao và định hướng phát triển sự nghiệp vững chắc cho sinh viên các khối ngành kỹ thuật công nghệ:</p>
<ul>
  <li><strong>Quy mô tuyển dụng:</strong> Hơn 1.200 vị trí tuyển dụng thực tập sinh và kỹ sư phần mềm chính thức (Fresher/Junior Software Engineer, AI Engineer, Cloud DevOps, Embedded Systems).</li>
  <li><strong>Phỏng vấn nhanh tại chỗ (On-site Fast-track):</strong> Hơn 30 doanh nghiệp tổ chức phỏng vấn và trao thư mời nhận việc (Offer Letter) trực tiếp ngay tại gian hàng.</li>
  <li><strong>Hoạt động cố vấn:</strong> Gian hàng tư vấn sửa CV 1-on-1 cùng các chuyên gia nhân sự kỳ cựu và hội thảo định hướng "Kỹ sư thích ứng kỷ nguyên AI".</li>
</ul>
<p>Sinh viên tham dự chuẩn bị trang phục lịch sự, mang theo CV bản cứng hoặc quét mã QR hồ sơ năng lực số trên ứng dụng CampusUTE.</p>',
    "updatedAt" = NOW(),
    version = version + 1
WHERE id IN ('announcement-ute-career-fair-tech-2026', 'announcement-v32-job-fair', 'announcement-ute-fpt-ojt-career-day');

-- 3. Academic Scholarship & Conduct Scoring Criteria
UPDATE engagement."Announcement"
SET content = '<p>Hội đồng xét duyệt Học bổng Nhà trường trân trọng thông báo kết quả đối soát điểm trung bình chung học tập (GPA) kết hợp điểm rèn luyện (ĐRL) xét cấp Học bổng Khuyến khích học tập.</p>
<figure class="my-5 overflow-hidden rounded-xl border border-border/70 bg-secondary/15 p-2">
  <img src="/images/news/scholarship-ceremony.jpg" alt="Lễ trao học bổng khuyến khích học tập HCM-UTE" class="w-full h-auto rounded-lg object-cover" />
  <figcaption class="pt-2 text-center text-xs font-medium text-muted-foreground italic">
    Lễ trao Học bổng Khuyến khích học tập và Học bổng Doanh nghiệp vinh danh các sinh viên xuất sắc HCM-UTE
  </figcaption>
</figure>
<p>Chính sách học bổng nhằm động viên tinh thần nỗ lực vươn lên trong học tập và rèn luyện của sinh viên toàn trường theo các tiêu chuẩn học vụ hiện hành:</p>
<ul>
  <li><strong>Tiêu chuẩn xét chọn:</strong> Điểm rèn luyện từ <strong>80 điểm trở lên</strong> (xếp loại Tốt và Xuất sắc), tích lũy đủ số tín chỉ quy định của học kỳ và không nợ bất kỳ môn học nào.</li>
  <li><strong>Các định mức học bổng:</strong> Loại Xuất sắc (120% học phí kỳ), Loại Giỏi (100% học phí kỳ) và Loại Khá (75% học phí kỳ).</li>
  <li><strong>Thời hạn đối soát minh chứng:</strong> Sinh viên kiểm tra danh sách công bố trên cổng cá nhân và phản hồi phúc khảo trước 17:00 ngày 20/09/2026.</li>
</ul>
<p>Kinh phí học bổng sẽ được Phòng Kế hoạch - Tài chính chuyển khoản trực tiếp vào tài khoản ngân hàng liên kết của sinh viên đủ điều kiện.</p>',
    "updatedAt" = NOW(),
    version = version + 1
WHERE id IN ('announcement-ute-scholarship-dr-criteria', 'announcement-ute-samsung-scholarship-2026', 'announcement-ute-intel-stem-women-2026', 'announcement-v26-scholarship');

-- 4. Course Registration Schedule & Limits
UPDATE engagement."Announcement"
SET content = '<p>Phòng Đào tạo thông báo kế hoạch tổ chức đăng ký học phần chính thức cho Học kỳ 1 năm học 2026-2027 trên hệ thống quản lý học tập tích hợp CampusUTE.</p>
<figure class="my-5 overflow-hidden rounded-xl border border-border/70 bg-secondary/15 p-2">
  <img src="/images/news/course-registration.jpg" alt="Cổng đăng ký học phần trực tuyến CampusUTE" class="w-full h-auto rounded-lg object-cover" />
  <figcaption class="pt-2 text-center text-xs font-medium text-muted-foreground italic">
    Sinh viên tra cứu thời khóa biểu và thực hiện đăng ký học phần trực tuyến trên cổng CampusUTE
  </figcaption>
</figure>
<p>Để đảm bảo công tác tổ chức đào tạo diễn ra thuận lợi, sinh viên cần nắm vững các mốc thời gian và quy định học vụ sau:</p>
<ul>
  <li><strong>Hạn mức tín chỉ:</strong> Sinh viên được đăng ký tối đa <strong>28 tín chỉ</strong> trong học kỳ chính; chỉ trường hợp có đơn xin vượt hạn mức được Ban Chủ nhiệm Khoa và Phòng Đào tạo duyệt mới được nâng lên trần 30 tín chỉ.</li>
  <li><strong>Lịch mở cổng phân luồng:</strong> Khóa 2022 và 2023 mở từ 08:00 ngày 25/08; Khóa 2024 và 2025 mở từ 08:00 ngày 28/08; Đăng ký bổ sung và điều chỉnh toàn trường đến 17:00 ngày 15/09/2026.</li>
  <li><strong>Lưu ý quan trọng:</strong> Kiểm tra kỹ điều kiện môn học tiên quyết, tránh xung đột lịch thi và thực hiện ấn nút "Xác nhận lưu đăng ký" để ghi nhận vào cơ sở dữ liệu.</li>
</ul>',
    "updatedAt" = NOW(),
    version = version + 1
WHERE id IN ('announcement-ute-course-reg-official', 'announcement-registration-window');

-- 5. Thesis Registration & Defense (KLTN)
UPDATE engagement."Announcement"
SET content = '<p>Khoa Công nghệ Thông tin - Trường Đại học Sư phạm Kỹ thuật TP.HCM long trọng tổ chức Lễ bảo vệ Khóa luận tốt nghiệp (KLTN) đợt 2 cho sinh viên các chuyên ngành Kỹ thuật phần mềm, Hệ thống thông tin, An toàn thông tin và Trí tuệ nhân tạo.</p>
<figure class="my-5 overflow-hidden rounded-xl border border-border/70 bg-secondary/15 p-2">
  <img src="/images/news/thesis-defense.jpg" alt="Lễ bảo vệ Khóa luận tốt nghiệp Khoa CNTT HCM-UTE" class="w-full h-auto rounded-lg object-cover" />
  <figcaption class="pt-2 text-center text-xs font-medium text-muted-foreground italic">
    Sinh viên Khoa Công nghệ Thông tin tự tin báo cáo đề tài khóa luận trước Hội đồng chấm thi học thuật HCM-UTE
  </figcaption>
</figure>
<p>Đợt bảo vệ ghi nhận sự trưởng thành vượt bậc về hàm lượng khoa học, tính ứng dụng thực tiễn và khả năng làm chủ công nghệ của sinh viên:</p>
<ul>
  <li><strong>Quy mô hội đồng:</strong> 18 hội đồng chuyên môn với sự tham gia của các Giáo sư, Tiến sĩ đầu ngành và các chuyên gia cấp cao (Chief Architect, Engineering Lead) từ các doanh nghiệp đối tác.</li>
  <li><strong>Số lượng đề tài:</strong> Hơn 120 nhóm sinh viên tham gia bảo vệ với các hướng nghiên cứu tiêu biểu: Hệ thống phân tán chịu tải cao, Thị giác máy tính trong y tế số, An toàn mạng dựa trên Zero-Trust và Giải pháp quản trị trường đại học thông minh.</li>
  <li><strong>Kho lưu trữ số hóa:</strong> Toàn bộ báo cáo toàn văn, slide thuyết trình và mã nguồn đề tài được tích hợp lưu trữ tại Phân hệ Kho Luận án & Đồ án tốt nghiệp trên nền tảng CampusUTE.</li>
</ul>',
    "updatedAt" = NOW(),
    version = version + 1
WHERE id IN ('announcement-ute-thesis-registration-fall', 'announcement-v26-thesis-round');

-- 6. Student Scientific Research & Innovation Awards (NCKH)
UPDATE engagement."Announcement"
SET content = '<p>Trường Đại học Sư phạm Kỹ thuật TP.HCM tổ chức Lễ tổng kết và trao giải thưởng Nghiên cứu Khoa học Sinh viên (NCKH) & Đổi mới Sáng tạo Công nghệ năm học 2025-2026 tại Hội trường Lớn Khu A.</p>
<figure class="my-5 overflow-hidden rounded-xl border border-border/70 bg-secondary/15 p-2">
  <img src="/images/news/scientific-research.jpg" alt="Lễ vinh danh Giải thưởng Nghiên cứu Khoa học Sinh viên HCM-UTE" class="w-full h-auto rounded-lg object-cover" />
  <figcaption class="pt-2 text-center text-xs font-medium text-muted-foreground italic">
    Đại diện Ban Giám hiệu Nhà trường trao bằng khen và cúp lưu niệm cho các nhóm sinh viên đạt giải thưởng Nghiên cứu Khoa học 2026
  </figcaption>
</figure>
<p>Phong trào nghiên cứu khoa học sinh viên năm nay tiếp tục khẳng định vị thế tiên phong của HCM-UTE trong sáng tạo kỹ thuật và giải quyết các bài toán thực tiễn của xã hội:</p>
<ul>
  <li><strong>Kết quả chung cuộc:</strong> Ban giám khảo đã chấm chọn và trao 05 Giải Nhất, 10 Giải Nhì, 15 Giải Ba cùng 20 Giải Khuyến khích từ hơn 350 công trình dự thi trên toàn trường.</li>
  <li><strong>Ứng dụng chuyển giao:</strong> Nhiều đề tài xuất sắc đã công bố bài báo tại các hội nghị quốc tế uy tín thuộc danh mục Scopus/IEEE và nhận được tài trợ thương mại hóa từ Quỹ đầu tư mạo hiểm công nghệ.</li>
  <li><strong>Chính sách ưu đãi:</strong> Sinh viên đạt giải được cộng điểm rèn luyện tối đa (100 điểm), ưu tiên xét tuyển đề tài Khóa luận tốt nghiệp và nhận học bổng tài năng của Nhà trường.</li>
</ul>',
    "updatedAt" = NOW(),
    version = version + 1
WHERE id IN ('announcement-ute-student-scientific-research-awards', 'announcement-v32-ute-research', 'announcement-ute-innovation-awards-2026');
