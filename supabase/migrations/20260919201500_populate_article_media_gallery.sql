-- Migration 20260919201500_populate_article_media_gallery.sql
-- Cấp tập ảnh chi tiết tỉ lệ 16:9 chất lượng cao cho 18 bài viết tiêu biểu trong engagement."ArticleMediaGallery"

INSERT INTO engagement."ArticleMediaGallery" (
    "id", "announcementId", "mediaUrl", "thumbnailUrl", "captionVi", "captionEn", "altText",
    "mediaType", "aspectRatio", "width", "height", "fileSizeBytes", "displayOrder", "isCover"
) VALUES
-- 1. Phòng sạch Bán dẫn & Vi mạch Cleanroom
(
    'gal-semiconductor-cover',
    'announcement-semiconductor-cleanroom',
    '/images/news/semiconductor-cleanroom.jpg',
    '/images/news/semiconductor-cleanroom.jpg',
    'Toàn cảnh không gian phòng sạch Class 1000 và dây chuyền chế tạo vi mạch bán dẫn tiên tiến tại HCM-UTE',
    'Panoramic view of the Class 1000 cleanroom and advanced semiconductor fabrication line at HCM-UTE',
    'Phòng sạch thí nghiệm Bán dẫn và Vi mạch HCM-UTE',
    'IMAGE', '16:9', 1920, 1080, 931639, 0, TRUE
),
(
    'gal-semiconductor-wafer-test',
    'announcement-semiconductor-cleanroom',
    '/images/news/semiconductor-cleanroom.jpg',
    '/images/news/semiconductor-cleanroom.jpg',
    'Kỹ sư và giảng viên HCM-UTE vận hành trạm đo kiểm wafer bán dẫn và kính hiển vi quang học độ phân giải cao',
    'HCM-UTE engineers and faculty operating the wafer probe station and high-resolution optical inspection system',
    'Trạm kiểm thử wafer vi mạch bán dẫn',
    'IMAGE', '16:9', 1920, 1080, 931639, 1, FALSE
),

-- 2. Trung tâm Robotics & IoT Công nghiệp Thông minh
(
    'gal-robotics-cover',
    'announcement-robotics-iot-lab',
    '/images/news/robotics-iot-lab.jpg',
    '/images/news/robotics-iot-lab.jpg',
    'Sinh viên kỹ thuật HCM-UTE hiệu chỉnh cánh tay robot công nghiệp KUKA 6 bậc tự do tại xưởng thực hành',
    'HCM-UTE engineering students calibrating a 6-DOF KUKA industrial robotic arm in the smart automation workshop',
    'Cánh tay robot công nghiệp KUKA và sinh viên thực hành',
    'IMAGE', '16:9', 1920, 1080, 962448, 0, TRUE
),
(
    'gal-robotics-iot-conveyor',
    'announcement-robotics-iot-lab',
    '/images/news/robotics-iot-lab.jpg',
    '/images/news/robotics-iot-lab.jpg',
    'Hệ thống băng chuyền phân loại tự động ứng dụng thị giác máy tính AI và mạng truyền thông PROFINET',
    'Automated sorting conveyor integrated with AI computer vision and PROFINET industrial networking',
    'Băng tải công nghiệp IoT phân loại sản phẩm',
    'IMAGE', '16:9', 1920, 1080, 962448, 1, FALSE
),

-- 3. Lễ ra quân Chiến dịch Mùa hè xanh
(
    'gal-green-summer-cover',
    'announcement-green-summer-volunteer',
    '/images/news/green-summer-volunteer.jpg',
    '/images/news/green-summer-volunteer.jpg',
    'Chiến sĩ tình nguyện Mùa hè xanh UTE chung tay đổ bê tông nâng cấp tuyến đường liên ấp nông thôn mới',
    'UTE Green Summer student volunteers concreting a rural road alongside local villagers',
    'Chiến sĩ Mùa hè xanh UTE làm đường giao thông nông thôn',
    'IMAGE', '16:9', 1920, 1080, 1157960, 0, TRUE
),
(
    'gal-green-summer-ceremony',
    'announcement-green-summer-volunteer',
    '/images/news/green-summer-volunteer.jpg',
    '/images/news/green-summer-volunteer.jpg',
    'Không khí hào hùng của hơn 800 đoàn viên thanh niên trong lễ xuất quân tại sân trường trung tâm HCM-UTE',
    'Energetic departure ceremony of over 800 youth volunteers at the central campus courtyard of HCM-UTE',
    'Lễ xuất quân Chiến dịch tình nguyện Mùa hè xanh',
    'IMAGE', '16:9', 1920, 1080, 1157960, 1, FALSE
),

-- 4. Khai mạc Giải Bóng đá UTE Champions Cup
(
    'gal-sports-cup-cover',
    'announcement-campus-sports-cup',
    '/images/news/campus-sports-cup.jpg',
    '/images/news/campus-sports-cup.jpg',
    'Pha tranh chấp bóng kịch tính trong trận khai mạc Giải bóng đá sinh viên UTE Champions Cup',
    'High-intensity tackle action during the opening match of the UTE Champions Cup Student Football League',
    'Trận đấu bóng đá UTE Champions Cup trên sân cỏ nhân tạo',
    'IMAGE', '16:9', 1920, 1080, 987815, 0, TRUE
),
(
    'gal-sports-cup-fans',
    'announcement-campus-sports-cup',
    '/images/news/campus-sports-cup.jpg',
    '/images/news/campus-sports-cup.jpg',
    'Khán đài rực rỡ cờ hoa và tiếng reo hò cổ vũ nhiệt tình của hàng ngàn sinh viên các khoa',
    'Vibrant cheering crowd with flags and drums from thousands of passionate faculty supporters',
    'Khán giả sinh viên cổ vũ giải đấu thể thao',
    'IMAGE', '16:9', 1920, 1080, 987815, 1, FALSE
),

-- 5. Hội diễn Văn nghệ Truyền thống & Gala 20/11
(
    'gal-culture-arts-cover',
    'announcement-cultural-arts-gala',
    '/images/news/cultural-arts-gala.jpg',
    '/images/news/cultural-arts-gala.jpg',
    'Tiết mục múa dân gian đương đại tôn vinh người thầy trong đêm Gala chào mừng Ngày Nhà giáo Việt Nam',
    'Contemporary folk dance performance honoring educators during the Teachers Day Gala night',
    'Tiết mục ca múa nhạc truyền thống trên sân khấu lớn',
    'IMAGE', '16:9', 1920, 1080, 979242, 0, TRUE
),
(
    'gal-culture-arts-awards',
    'announcement-cultural-arts-gala',
    '/images/news/cultural-arts-gala.jpg',
    '/images/news/cultural-arts-gala.jpg',
    'Ban Giám hiệu trao cờ lưu niệm và giải thưởng xuất sắc cho đội văn nghệ các khoa đoạt giải cao',
    'University Leadership awarding championship trophies to top performing faculty cultural troupes',
    'Trao giải thưởng hội diễn văn nghệ học đường',
    'IMAGE', '16:9', 1920, 1080, 979242, 1, FALSE
),

-- 6. Hội nghị Khoa học Quốc tế IEEE về STEM & AI
(
    'gal-stem-conference-cover',
    'announcement-stem-conference-keynote',
    '/images/news/stem-conference-keynote.jpg',
    '/images/news/stem-conference-keynote.jpg',
    'Giáo sư quốc tế trình bày báo cáo chuyên đề về Trí tuệ nhân tạo tạo sinh và ứng dụng trong công nghiệp',
    'Keynote address on Generative AI and industrial robotics by renowned international professors',
    'Phiên toàn thể Hội nghị Khoa học Quốc tế IEEE tại HCM-UTE',
    'IMAGE', '16:9', 1920, 1080, 911577, 0, TRUE
),
(
    'gal-stem-conference-poster',
    'announcement-stem-conference-keynote',
    '/images/news/stem-conference-keynote.jpg',
    '/images/news/stem-conference-keynote.jpg',
    'Các nhà khoa học trẻ và sinh viên trao đổi học thuật tại khu vực triển lãm poster công trình nghiên cứu',
    'Young researchers and students engaged in academic discussions at the research poster exhibition hall',
    'Triển lãm Poster công trình khoa học IEEE',
    'IMAGE', '16:9', 1920, 1080, 911577, 1, FALSE
),

-- 7. Đấu trường Đổi mới Sáng tạo AI Hackathon 48 Giờ
(
    'gal-ai-hackathon-cover',
    'announcement-ai-hackathon-arena',
    '/images/news/ai-hackathon-arena.jpg',
    '/images/news/ai-hackathon-arena.jpg',
    'Các đội thi tập trung cao độ giải quyết bài toán tối ưu hóa mô hình ngôn ngữ lớn suốt 48 giờ liên tục',
    'Competing teams intensely programming and optimizing LLM solutions during the non-stop 48-hour hackathon',
    'Sinh viên lập trình thi đấu tại AI Hackathon 48H Arena',
    'IMAGE', '16:9', 1920, 1080, 990490, 0, TRUE
),
(
    'gal-ai-hackathon-pitch',
    'announcement-ai-hackathon-arena',
    '/images/news/ai-hackathon-arena.jpg',
    '/images/news/ai-hackathon-arena.jpg',
    'Đại diện nhóm thí sinh thuyết trình giải pháp ứng dụng AI trước hội đồng chuyên gia công nghệ',
    'Team representatives pitching their innovative AI applications to the enterprise technology jury',
    'Thuyết trình sản phẩm AI trước ban giám khảo',
    'IMAGE', '16:9', 1920, 1080, 990490, 1, FALSE
),

-- 8. Thư viện Số 24/7 & Không gian Học tập Đa năng
(
    'gal-digital-library-cover',
    'announcement-digital-library-hub',
    '/images/news/campus-digital-library.jpg',
    '/images/news/campus-digital-library.jpg',
    'Không gian tự học hiện đại, tràn ngập ánh sáng tự nhiên tại Tòa nhà Trung tâm Thư viện Số HCM-UTE',
    'Modern, naturally lit study commons and collaborative research pods in the HCM-UTE Digital Library Hub',
    'Không gian tự học mở tại Thư viện số HCM-UTE',
    'IMAGE', '16:9', 1920, 1080, 928668, 0, TRUE
),
(
    'gal-digital-library-terminals',
    'announcement-digital-library-hub',
    '/images/news/campus-digital-library.jpg',
    '/images/news/campus-digital-library.jpg',
    'Hệ thống trạm máy tính tốc độ cao tra cứu tài liệu học thuật và cơ sở dữ liệu số hóa quốc tế',
    'High-speed digital workstation clusters for accessing international academic databases and journals',
    'Trạm tra cứu tài liệu số hóa thư viện',
    'IMAGE', '16:9', 1920, 1080, 928668, 1, FALSE
),

-- 9. Lễ Trao bằng Tốt nghiệp Tân Kỹ sư & Cử nhân
(
    'gal-commencement-cover',
    'announcement-commencement-graduation',
    '/images/news/commencement-graduation.jpg',
    '/images/news/commencement-graduation.jpg',
    'Niềm tự hào và xúc động của các tân kỹ sư, cử nhân HCM-UTE trong khoảnh khắc nhận bằng tốt nghiệp',
    'Pride and emotion of new HCM-UTE engineering and bachelor graduates during the diploma conferral',
    'Tân cử nhân và kỹ sư trong lễ tốt nghiệp tại hội trường lớn',
    'IMAGE', '16:9', 1920, 1080, 1009045, 0, TRUE
),
(
    'gal-commencement-celebration',
    'announcement-commencement-graduation',
    '/images/news/commencement-graduation.jpg',
    '/images/news/commencement-graduation.jpg',
    'Khoảnh khắc tung nón cử nhân đầy hân hoan cùng gia đình và bạn bè trong ngày lễ vinh quy',
    'Joyful cap-toss celebration with family and friends commemorating the graduation milestone',
    'Khoảnh khắc tung nón cử nhân tốt nghiệp rực rỡ',
    'IMAGE', '16:9', 1920, 1080, 1009045, 1, FALSE
),

-- 10. Ngày hội Hiến máu Tình nguyện "Giọt hồng Công nghệ"
(
    'gal-blood-donation-cover',
    'announcement-blood-donation-day',
    '/images/news/blood-donation-day.jpg',
    '/images/news/blood-donation-day.jpg',
    'Hàng trăm cán bộ, giảng viên và sinh viên HCM-UTE tham gia hiến máu nhân đạo cứu người',
    'Hundreds of HCM-UTE faculty, staff, and students participating in the life-saving voluntary blood drive',
    'Khu vực tiếp nhận hiến máu tình nguyện Giọt hồng Công nghệ',
    'IMAGE', '16:9', 1920, 1080, 871081, 0, TRUE
),
(
    'gal-blood-donation-support',
    'announcement-blood-donation-day',
    '/images/news/blood-donation-day.jpg',
    '/images/news/blood-donation-day.jpg',
    'Đội ngũ y bác sĩ Bệnh viện Truyền máu Huyết học khám sàng lọc và chăm sóc tận tình người hiến máu',
    'Medical staff and student volunteers providing dedicated care and screening at the donation station',
    'Bác sĩ kiểm tra sức khỏe tình nguyện viên hiến máu',
    'IMAGE', '16:9', 1920, 1080, 871081, 1, FALSE
),

-- 11. Ký túc xá Sinh viên HCM-UTE
(
    'gal-dormitory-cover',
    'announcement-student-dormitory-campus',
    '/images/news/student-dormitory-campus.jpg',
    '/images/news/student-dormitory-campus.jpg',
    'Khuôn viên xanh rợp bóng mát cùng sân bóng rổ, khu tập thể thao ngoài trời tại Ký túc xá HCM-UTE',
    'Lush green campus environment, outdoor basketball courts, and fitness facilities at HCM-UTE Dormitory',
    'Khuôn viên ký túc xá sinh viên hiện đại và thân thiện',
    'IMAGE', '16:9', 1920, 1080, 1224097, 0, TRUE
),
(
    'gal-dormitory-study-room',
    'announcement-student-dormitory-campus',
    '/images/news/student-dormitory-campus.jpg',
    '/images/news/student-dormitory-campus.jpg',
    'Phòng tự học nội trú yên tĩnh với kết nối Wi-Fi tốc độ cao và máy lọc nước thông minh',
    'Quiet resident study lounge equipped with high-speed Wi-Fi and smart drinking water systems',
    'Phòng tự học nội trú tiện nghi tại ký túc xá',
    'IMAGE', '16:9', 1920, 1080, 1224097, 1, FALSE
),

-- 12. Lễ Tôn vinh Giảng viên Xuất sắc & Nhà Khoa học Tiêu biểu
(
    'gal-faculty-awards-cover',
    'announcement-faculty-excellence-awards',
    '/images/news/faculty-excellence-awards.jpg',
    '/images/news/faculty-excellence-awards.jpg',
    'Ban Giám hiệu trao kỷ niệm chương và vinh danh các giảng viên có thành tích xuất sắc trong giảng dạy và nghiên cứu',
    'University Leadership honoring distinguished faculty members for outstanding teaching and research excellence',
    'Lễ trao giải thưởng tôn vinh nhà giáo và nhà khoa học HCM-UTE',
    'IMAGE', '16:9', 1920, 1080, 898444, 0, TRUE
),
(
    'gal-faculty-awards-tribute',
    'announcement-faculty-excellence-awards',
    '/images/news/faculty-excellence-awards.jpg',
    '/images/news/faculty-excellence-awards.jpg',
    'Tập thể sư phạm HCM-UTE chụp ảnh lưu niệm ghi dấu một năm học nhiều thành tựu đột phá',
    'Faculty and researchers gathering for a memorable group photo celebrating another year of academic milestones',
    'Tập thể giảng viên tiêu biểu chụp ảnh lưu niệm',
    'IMAGE', '16:9', 1920, 1080, 898444, 1, FALSE
),

-- 13. Cụm máy chủ GPU NVIDIA A100 & Big Data Lab
(
    'gal-bigdata-ai-cover',
    'announcement-ute-bigdata-ai-center',
    '/images/news/bigdata-ai-lab.jpg',
    '/images/news/bigdata-ai-lab.jpg',
    'Trung tâm dữ liệu máy chủ tính toán hiệu năng cao GPU NVIDIA A100 phục vụ huấn luyện mô hình Deep Learning',
    'High-Performance Computing data center with NVIDIA A100 GPUs powering deep learning and big data workloads',
    'Hệ thống máy chủ GPU điện toán đám mây AI Lab',
    'IMAGE', '16:9', 1920, 1080, 917486, 0, TRUE
),
(
    'gal-bigdata-ai-monitoring',
    'announcement-ute-bigdata-ai-center',
    '/images/news/bigdata-ai-lab.jpg',
    '/images/news/bigdata-ai-lab.jpg',
    'Màn hình giám sát tài nguyên tính toán và lưu trữ phân tán Ceph theo thời gian thực',
    'Real-time operations dashboard monitoring distributed Ceph storage and cluster GPU utilization',
    'Màn hình điều hành trung tâm giám sát mạng máy chủ AI',
    'IMAGE', '16:9', 1920, 1080, 917486, 1, FALSE
),

-- 14. UTE Tech Career Expo & Ngày hội Việc làm
(
    'gal-tech-career-cover',
    'announcement-ute-career-fair-tech-2026',
    '/images/news/tech-career-expo.jpg',
    '/images/news/tech-career-expo.jpg',
    'Hàng ngàn sinh viên tìm hiểu cơ hội tuyển dụng tại các gian hàng doanh nghiệp công nghệ hàng đầu',
    'Thousands of students engaging with leading tech employers at the annual UTE Career Expo',
    'Không khí sôi động tại Ngày hội Việc làm Công nghệ UTE',
    'IMAGE', '16:9', 1920, 1080, 979470, 0, TRUE
),
(
    'gal-tech-career-interview',
    'announcement-ute-career-fair-tech-2026',
    '/images/news/tech-career-expo.jpg',
    '/images/news/tech-career-expo.jpg',
    'Sinh viên năm cuối tự tin phỏng vấn thử và nhận lời mời thực tập trực tiếp từ nhà tuyển dụng',
    'Senior students attending direct technical interviews and receiving immediate internship offers',
    'Phỏng vấn tuyển dụng trực tiếp tại gian hàng doanh nghiệp',
    'IMAGE', '16:9', 1920, 1080, 979470, 1, FALSE
),

-- 15. Học bổng Khuyến khích Học tập & Học bổng Doanh nghiệp
(
    'gal-scholarship-cover',
    'announcement-ute-scholarship-dr-criteria',
    '/images/news/scholarship-ceremony.jpg',
    '/images/news/scholarship-ceremony.jpg',
    'Lễ trao học bổng tài năng và học bổng khuyến khích cho các thủ khoa, sinh viên vượt khó học giỏi',
    'Awarding talent and merit scholarships to valedictorians and exemplary hardworking students',
    'Trao học bổng tài năng cho sinh viên xuất sắc',
    'IMAGE', '16:9', 1920, 1080, 951871, 0, TRUE
),
(
    'gal-scholarship-cheque',
    'announcement-ute-scholarship-dr-criteria',
    '/images/news/scholarship-ceremony.jpg',
    '/images/news/scholarship-ceremony.jpg',
    'Đại diện các tập đoàn tài trợ trao bảng tượng trưng học bổng đồng hành cùng thế hệ tương lai',
    'Corporate sponsors presenting symbolic scholarship grant cheques to student representatives',
    'Doanh nghiệp trao bảng biểu trưng học bổng sinh viên',
    'IMAGE', '16:9', 1920, 1080, 951871, 1, FALSE
),

-- 16. Kế hoạch Đăng ký Học phần Trực tuyến
(
    'gal-course-reg-cover',
    'announcement-ute-course-reg-official',
    '/images/news/course-registration.jpg',
    '/images/news/course-registration.jpg',
    'Giao diện phân luồng đăng ký môn học trực tuyến trên Cổng thông tin Đào tạo CampusUTE',
    'Student registration portal interface on CampusUTE providing high-throughput course enrollment',
    'Cổng đăng ký học phần trực tuyến CampusUTE',
    'IMAGE', '16:9', 1920, 1080, 966391, 0, TRUE
),
(
    'gal-course-reg-support',
    'announcement-ute-course-reg-official',
    '/images/news/course-registration.jpg',
    '/images/news/course-registration.jpg',
    'Đội ngũ chuyên viên Phòng Đào tạo trực tuyến hỗ trợ sinh viên điều chỉnh học phần theo đúng lộ trình',
    'Academic Affairs advising specialists assisting students with custom study plans and registration',
    'Tư vấn và hỗ trợ học vụ đăng ký môn học',
    'IMAGE', '16:9', 1920, 1080, 966391, 1, FALSE
),

-- 17. Lễ Bảo vệ Khóa luận Tốt nghiệp KLTN
(
    'gal-thesis-defense-cover',
    'announcement-ute-thesis-registration-fall',
    '/images/news/thesis-defense.jpg',
    '/images/news/thesis-defense.jpg',
    'Sinh viên báo cáo đề tài khóa luận trước Hội đồng chấm thi chuyên môn Khoa Công nghệ Thông tin',
    'Students defending their final capstone thesis in front of the IT Faculty Academic Evaluation Council',
    'Bảo vệ khóa luận tốt nghiệp trước hội đồng khoa học',
    'IMAGE', '16:9', 1920, 1080, 871181, 0, TRUE
),
(
    'gal-thesis-defense-demo',
    'announcement-ute-thesis-registration-fall',
    '/images/news/thesis-defense.jpg',
    '/images/news/thesis-defense.jpg',
    'Trình diễn trực tiếp mô hình phần cứng và hệ thống phần mềm thử nghiệm trước hội đồng đánh giá',
    'Live hardware demonstration and software benchmarking session during the capstone defense',
    'Trình diễn demo sản phẩm đồ án trước hội đồng',
    'IMAGE', '16:9', 1920, 1080, 871181, 1, FALSE
),

-- 18. Giải thưởng Nghiên cứu Khoa học Sinh viên NCKH
(
    'gal-scientific-research-cover',
    'announcement-ute-student-scientific-research-awards',
    '/images/news/scientific-research.jpg',
    '/images/news/scientific-research.jpg',
    'Triển lãm các sản phẩm công nghệ sáng tạo tham gia Vòng chung kết Giải thưởng NCKH Sinh viên UTE',
    'Exhibition of innovative technology products at the UTE Student Scientific Research Finals',
    'Triển lãm đề tài Nghiên cứu khoa học sinh viên HCM-UTE',
    'IMAGE', '16:9', 1920, 1080, 1048656, 0, TRUE
),
(
    'gal-scientific-research-awards',
    'announcement-ute-student-scientific-research-awards',
    '/images/news/scientific-research.jpg',
    '/images/news/scientific-research.jpg',
    'Trao giải Nhất và cúp vinh danh cho nhóm sinh viên có công trình đăng ký bằng độc quyền sáng chế',
    'First prize trophy award ceremony honoring the student research team with a patented invention',
    'Lễ trao giải Nhất nghiên cứu khoa học sinh viên',
    'IMAGE', '16:9', 1920, 1080, 1048656, 1, FALSE
)
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
