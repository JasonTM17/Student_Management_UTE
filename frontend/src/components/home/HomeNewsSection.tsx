'use client';

import React, { useEffect, useMemo, useState } from 'react';
import {
  ArrowRight,
  Award,
  BookOpen,
  Briefcase,
  Calendar,
  ChevronDown,
  ChevronUp,
  Clock,
  Flame,
  FlaskConical,
  GraduationCap,
  Heart,
  Newspaper,
  Sparkles,
  Users,
} from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import { announcementsApi, type AnnouncementRecord } from '@/lib/api';
import { AnnouncementReaderModal } from '@/components/announcements/AnnouncementReaderModal';
import { AnnouncementFeedCard } from '@/components/announcements/feed/AnnouncementFeedCard';
import { SectionEyebrow } from '@/components/ui/page-header';
import { LocalizedLink } from '@/components/LocalizedLink';
import { useI18n } from '@/i18n';
import { cn } from '@/lib/utils';
import {
  resolveAnnouncementDomain,
  resolveArticleCover,
  calculateReadingTime,
  extractAnnouncementExcerpt,
  formatAnnouncementPublisher,
  formatRelativeTime,
  type ArticleCategoryCode,
} from '@/lib/announcement-presentation';

export const HOMEPAGE_FALLBACK_NEWS: AnnouncementRecord[] = [
  // 1. Semiconductor Cleanroom
  {
    id: 'notice-semiconductor-cleanroom',
    title: 'Khánh thành Phòng thí nghiệm Bán dẫn & Vi mạch Cleanroom chuẩn quốc tế tại HCM-UTE',
    content: `<p>Trường Đại học Công nghệ Kỹ thuật TP.HCM long trọng tổ chức Lễ khánh thành Phòng thí nghiệm Bán dẫn & Thiết kế Vi mạch (Semiconductor & Cleanroom Lab) đạt tiêu chuẩn phòng sạch Class 1000 tại Khu Công nghệ cao Cơ sở 1.</p>
<figure class="my-5 overflow-hidden rounded-xl border border-border/70 bg-secondary/15 p-2">
  <img src="/images/news/semiconductor-cleanroom.jpg" alt="Phòng Thí nghiệm Bán dẫn & Vi mạch Cleanroom HCM-UTE" class="w-full h-auto rounded-lg object-cover" />
  <figcaption class="pt-2 text-center text-xs font-medium text-muted-foreground italic">
    Toàn cảnh không gian phòng sạch Class 1000 và hệ thống thiết bị kiểm thử wafer vi mạch bán dẫn tại HCM-UTE
  </figcaption>
</figure>
<p>Công trình trọng điểm này khẳng định vị thế tiên phong của HCM-UTE trong việc đón đầu làn sóng dịch chuyển chuỗi cung ứng công nghệ vi mạch và công nghiệp bán dẫn toàn cầu:</p>
<ul>
  <li><strong>Hạ tầng kỹ thuật tiêu chuẩn quốc tế:</strong> Hệ thống phòng sạch được trang bị buồng đệm áp suất không khí (Air shower), bộ lọc khí hạt siêu mịn HEPA/ULPA đảm bảo kiểm soát nồng độ bụi dưới 1.000 hạt/foot khối. Dây chuyền bao gồm thiết bị quang khắc UV Photolithography, lò ủ chân không cao tần, trạm kiểm thử wafer và kính hiển vi điện tử quét (SEM).</li>
  <li><strong>Chương trình liên kết doanh nghiệp và học bổng tài năng:</strong> Dự án nhận được sự đồng hành chiến lược từ các tập đoàn công nghệ bán dẫn đa quốc gia như Intel Products Vietnam, Synopsys, Marvell Technology và FPT Semiconductor. Toàn bộ sinh viên xuất sắc chuyên ngành Vi mạch sẽ được cấp học bổng 100% học phí cùng cơ hội thực tập trực tiếp tại các trung tâm R&D hàng đầu.</li>
</ul>`,
    priority: 'HIGH',
    publishedBy: 'Ban Giám hiệu & Khoa Điện - Điện tử',
    createdAt: '2026-09-10T08:00:00Z',
    publishAt: '2026-09-10T08:00:00Z',
  },
  // 2. Smart Robotics & IoT Lab
  {
    id: 'notice-robotics-iot-lab',
    title: 'Khởi động Trung tâm Đổi mới Sáng tạo Robotics & IoT Công nghiệp Thông minh',
    content: `<p>Khoa Cơ khí Chế tạo máy phối hợp cùng Khoa Điện - Điện tử khánh thành Trung tâm Đổi mới Sáng tạo Robotics & IoT Công nghiệp Thông minh tại xưởng thực hành A2.</p>
<figure class="my-5 overflow-hidden rounded-xl border border-border/70 bg-secondary/15 p-2">
  <img src="/images/news/robotics-iot-lab.jpg" alt="Sinh viên thực hành tại Trung tâm Robotics & IoT HCM-UTE" class="w-full h-auto rounded-lg object-cover" />
  <figcaption class="pt-2 text-center text-xs font-medium text-muted-foreground italic">
    Sinh viên kỹ thuật HCM-UTE hiệu chỉnh cánh tay robot KUKA và lập trình cảm biến IoT công nghiệp
  </figcaption>
</figure>
<p>Trung tâm mở ra cơ hội tiếp cận công nghệ tự động hóa cấp cao cho hơn 2.500 sinh viên ngành Kỹ thuật Robot, Cơ điện tử và Tự động hóa:</p>
<ul>
  <li><strong>Năng lực thiết bị hiện đại:</strong> Xưởng được trang bị 6 trạm thao tác robot cánh tay 6 bậc tự do KUKA, băng chuyền phân loại tự động sử dụng thị giác máy tính AI (Computer Vision) và hệ thống mạng truyền thông công nghiệp PROFINET/EtherCAT.</li>
  <li><strong>Khóa học cấp chứng chỉ quốc tế:</strong> Sinh viên hoàn thành học phần ứng dụng tại lab sẽ được dự thi lấy chứng chỉ vận hành robot công nghiệp tiêu chuẩn châu Âu, đáp ứng ngay yêu cầu làm việc tại các nhà máy thông minh (Smart Factory).</li>
</ul>`,
    priority: 'HIGH',
    publishedBy: 'Khoa Cơ khí Chế tạo máy & Phòng KHCN',
    createdAt: '2026-09-08T09:00:00Z',
    publishAt: '2026-09-08T09:00:00Z',
  },
  // 3. Green Summer Volunteer
  {
    id: 'notice-green-summer-volunteer',
    title: 'Lễ ra quân Chiến dịch Mùa hè xanh: Tuổi trẻ UTE chung sức xây dựng nông thôn mới',
    content: `<p>Đoàn Thanh niên - Hội Sinh viên Trường Đại học Công nghệ Kỹ thuật TP.HCM long trọng tổ chức Lễ ra quân Chiến dịch tình nguyện Mùa hè xanh năm 2026 với chủ đề "Chiến sĩ tình nguyện UTE - Tiên phong, bản lĩnh, xung kích vì cộng đồng".</p>
<figure class="my-5 overflow-hidden rounded-xl border border-border/70 bg-secondary/15 p-2">
  <img src="/images/news/green-summer-volunteer.jpg" alt="Chiến sĩ Mùa hè xanh UTE thi công đường bê tông nông thôn" class="w-full h-auto rounded-lg object-cover" />
  <figcaption class="pt-2 text-center text-xs font-medium text-muted-foreground italic">
    Nụ cười rạng rỡ của sinh viên tình nguyện UTE và bà con nhân dân trong ngày đổ bê tông tuyến đường nông thôn mới
  </figcaption>
</figure>
<p>Chiến dịch năm nay triển khai trên địa bàn 5 tỉnh Tây Nam Bộ với các công trình thanh niên trọng điểm mang hàm lượng chuyên môn cao:</p>
<ul>
  <li><strong>Công trình dân sinh và chuyển giao kỹ thuật:</strong> Các đội hình chuyên môn đảm nhận thi công 15km đường giao thông nông thôn bê tông hóa, xây dựng 8 cây cầu nông thôn, lắp đặt 350 bộ đèn năng lượng mặt trời "Thắp sáng đường quê" và sửa chữa hệ thống điện sinh hoạt an toàn cho hơn 600 hộ gia đình chính sách.</li>
  <li><strong>Chuyển đổi số cộng đồng và sinh hoạt hè cho thiếu nhi:</strong> Đội hình Trí thức trẻ tổ chức các lớp phổ cập tin học, hướng dẫn người dân sử dụng dịch vụ công trực tuyến VNeID và tổ chức các sân chơi khoa học vui STEM cho hơn 3.000 em học sinh tiểu học địa phương.</li>
</ul>`,
    priority: 'NORMAL',
    publishedBy: 'Đoàn Thanh niên & Hội Sinh viên UTE',
    createdAt: '2026-09-05T07:30:00Z',
    publishAt: '2026-09-05T07:30:00Z',
  },
  // 4. Campus Sports Championship
  {
    id: 'notice-campus-sports-cup',
    title: 'Khai mạc Giải Bóng đá Sinh viên UTE Champions Cup: Cuộc tranh tài đỉnh cao của 24 đội bóng',
    content: `<p>Tại Sân vận động Khu liên hợp Thể thao Trung tâm, Trung tâm Thể dục Thể thao long trọng tổ chức Lễ khai mạc Giải Bóng đá Sinh viên truyền thống UTE Champions Cup 2026.</p>
<figure class="my-5 overflow-hidden rounded-xl border border-border/70 bg-secondary/15 p-2">
  <img src="/images/news/campus-sports-cup.jpg" alt="Pha tranh bóng quyết liệt tại UTE Champions Cup" class="w-full h-auto rounded-lg object-cover" />
  <figcaption class="pt-2 text-center text-xs font-medium text-muted-foreground italic">
    Trận cầu nảy lửa giữa Đội tuyển Khoa Cơ khí và Khoa Công nghệ Thông tin dưới ánh đèn sân vận động rực sáng
  </figcaption>
</figure>
<p>Giải đấu quy tụ 24 đội bóng xuất sắc nhất đại diện cho các Khoa, Viện đào tạo và Khối sinh viên quốc tế:</p>
<ul>
  <li><strong>Thể thức thi đấu và tinh thần thể thao:</strong> Các đội thi đấu vòng bảng theo thể thức vòng tròn tính điểm để chọn ra 8 đội mạnh nhất vào vòng knock-out tứ kết, bán kết và chung kết. Giải đấu áp dụng luật thi đấu sân 11 người của FIFA dưới sự điều hành của tổ trọng tài Liên đoàn Bóng đá TP.HCM.</li>
  <li><strong>Giải thưởng hấp dẫn:</strong> Đội Vô địch sẽ nhận Cúp vàng truyền thống UTE, cờ lưu niệm và phần thưởng trị giá 30 triệu đồng cùng học bổng rèn luyện thể chất. Các cầu thủ xuất sắc nhất sẽ được tuyển chọn vào Đội tuyển Sinh viên UTE tham dự Giải Vô địch Sinh viên Toàn quốc.</li>
</ul>`,
    priority: 'NORMAL',
    publishedBy: 'Trung tâm GDTC & Hội Sinh viên UTE',
    createdAt: '2026-09-04T15:00:00Z',
    publishAt: '2026-09-04T15:00:00Z',
  },
  // 5. Cultural Arts Gala 20/11
  {
    id: 'notice-cultural-arts-gala',
    title: 'Hội diễn Nghệ thuật Tri ân Ngày Nhà giáo Việt Nam 20/11: Khúc ca ngợi người đưa đò thầm lặng',
    content: `<p>Nhân kỷ niệm Ngày Nhà giáo Việt Nam 20/11, Công đoàn và Đoàn Thanh niên Trường Đại học Công nghệ Kỹ thuật TP.HCM tổ chức Đêm nhạc hội Tri ân Thầy Cô với chủ đề "Nâng cánh ước mơ - Sáng mãi lửa tri thức" tại Hội trường Lớn Khu A.</p>
<figure class="my-5 overflow-hidden rounded-xl border border-border/70 bg-secondary/15 p-2">
  <img src="/images/news/cultural-arts-gala.jpg" alt="Tiết mục múa hoa sen truyền thống tại Hội diễn 20/11" class="w-full h-auto rounded-lg object-cover" />
  <figcaption class="pt-2 text-center text-xs font-medium text-muted-foreground italic">
    Tiết mục múa hoa sen truyền thống trong trang phục Áo dài thướt tha ngợi ca công ơn Thầy Cô tại Hội trường Lớn HCM-UTE
  </figcaption>
</figure>
<p>Đêm diễn mang lại những xúc cảm sâu lắng, là lời tri ân chân thành nhất của bao thế hệ sinh viên gửi tới các Thầy, Cô giáo:</p>
<ul>
  <li><strong>Sắc màu nghệ thuật đa dạng và công phu:</strong> Chương trình gồm 20 tiết mục múa dân gian đương đại, hợp xướng, độc tấu nhạc cụ dân tộc và biểu diễn ban nhạc acoustic trẻ trung do chính các thầy cô giáo và sinh viên dàn dựng công phu.</li>
  <li><strong>Tuyên dương Giảng viên Ưu tú:</strong> Ban Giám hiệu Nhà trường đã trao tặng hoa và biểu trưng tri ân cho 50 Nhà giáo Tiêu biểu có nhiều đóng góp xuất sắc trong sự nghiệp trồng người và nghiên cứu khoa học của Nhà trường.</li>
</ul>`,
    priority: 'NORMAL',
    publishedBy: 'Công đoàn Trường & Đoàn Thanh niên UTE',
    createdAt: '2026-09-02T19:00:00Z',
    publishAt: '2026-09-02T19:00:00Z',
  },
  // 6. IEEE STEM Academic Conference
  {
    id: 'notice-stem-conference-keynote',
    title: 'Hội nghị Khoa học Quốc tế IEEE về Kỹ thuật Tiên tiến & Trí tuệ Nhân tạo tại HCM-UTE',
    content: `<p>Trường Đại học Công nghệ Kỹ thuật TP.HCM phối hợp cùng Hiệp hội Kỹ sư Điện và Điện tử Quốc tế (IEEE) đăng cai tổ chức Hội nghị Khoa học Quốc tế IEEE on Advanced Engineering & AI Systems tại Trung tâm Hội nghị Quốc tế Nhà trường.</p>
<figure class="my-5 overflow-hidden rounded-xl border border-border/70 bg-secondary/15 p-2">
  <img src="/images/news/stem-conference-keynote.jpg" alt="Diễn giả báo cáo tại Hội nghị Quốc tế IEEE HCM-UTE" class="w-full h-auto rounded-lg object-cover" />
  <figcaption class="pt-2 text-center text-xs font-medium text-muted-foreground italic">
    GS.TS Nguyễn Văn Minh - Trưởng nhóm nghiên cứu AI trình bày báo cáo chuyên đề về Tối ưu hóa Mạng nơ-ron sâu tại Hội thảo IEEE
  </figcaption>
</figure>
<p>Hội nghị là diễn đàn khoa học uy tín quy mô toàn cầu nhằm công bố và thảo luận các kết quả nghiên cứu mũi nhọn:</p>
<ul>
  <li><strong>Các phiên tiểu ban chuyên sâu:</strong> Hội đồng khoa học đã thẩm định và chọn lọc 145 bài báo cáo xuất sắc trong tổng số hơn 400 bài gửi về từ các trường đại học tại Mỹ, Nhật Bản, Hàn Quốc, Đức và Singapore để đăng kỷ yếu IEEE Xplore.</li>
  <li><strong>Thúc đẩy hợp tác nghiên cứu đa quốc gia:</strong> Hội nghị mở ra 6 biên bản ghi nhớ hợp tác trao đổi nghiên cứu sinh và liên kết phòng thí nghiệm trọng điểm song phương giữa HCM-UTE và các trường đại học đối tác quốc tế.</li>
</ul>`,
    priority: 'HIGH',
    publishedBy: 'Phòng Khoa học Công nghệ & Quan hệ Quốc tế',
    createdAt: '2026-09-01T08:30:00Z',
    publishAt: '2026-09-01T08:30:00Z',
  },
  // 7. AI Hackathon Arena
  {
    id: 'notice-ai-hackathon-arena',
    title: 'Khởi tranh Đấu trường Đổi mới Sáng tạo AI Hackathon 48 Giờ Liên tục: Chinh phục mô hình lớn',
    content: `<p>Khoa Công nghệ Thông tin phối hợp cùng Vườn ươm Khởi nghiệp Đổi mới Sáng tạo chính thức phát lệnh xuất phát Đấu trường AI Hackathon 2026 kéo dài 48 giờ liên tục tại Không gian Sáng tạo Innovation Hub.</p>
<figure class="my-5 overflow-hidden rounded-xl border border-border/70 bg-secondary/15 p-2">
  <img src="/images/news/ai-hackathon-arena.jpg" alt="Không khí làm việc xuyên đêm tại HCM-UTE AI Hackathon" class="w-full h-auto rounded-lg object-cover" />
  <figcaption class="pt-2 text-center text-xs font-medium text-muted-foreground italic">
    Các kỹ sư phần mềm trẻ miệt mài gõ mã nguồn và thiết kế kiến trúc đường ống xử lý dữ liệu trong đêm chung kết AI Hackathon
  </figcaption>
</figure>
<p>Cuộc thi là sân chơi đỉnh cao thử thách sức bền, tư duy thuật toán và kỹ năng hiện thực hóa ý tưởng của sinh viên công nghệ:</p>
<ul>
  <li><strong>Đề bài thách thức và tài nguyên đám mây:</strong> Mỗi đội thi được cấp quyền truy cập máy chủ GPU chuyên dụng và kho dữ liệu thực tế ẩn danh để xây dựng mô hình AI dự báo kẹt xe thời gian thực hoặc trợ lý AI y tế số.</li>
  <li><strong>Quỹ giải thưởng 150 triệu đồng:</strong> Đội Quán quân sẽ nhận giải thưởng 50 triệu đồng tiền mặt và gói tài trợ ươm tạo doanh nghiệp khởi nghiệp trị giá 100 triệu đồng từ Quỹ Đổi mới Sáng tạo Quốc gia.</li>
</ul>`,
    priority: 'NORMAL',
    publishedBy: 'Khoa Công nghệ Thông tin & Innovation Hub',
    createdAt: '2026-08-28T18:00:00Z',
    publishAt: '2026-08-28T18:00:00Z',
  },
  // 8. Digital Library Commons
  {
    id: 'notice-digital-library-hub',
    title: 'Mở rộng Không gian Học tập Đa năng & Dịch vụ Thư viện Số 24/7 tại Tòa nhà Trung tâm',
    content: `<p>Ban Quản lý Thư viện Trung tâm HCM-UTE trân trọng thông báo hoàn tất nâng cấp và đưa vào sử dụng Không gian Học tập Đa năng (Learning Commons) thế hệ mới tại Tòa nhà Thư viện 6 tầng.</p>
<figure class="my-5 overflow-hidden rounded-xl border border-border/70 bg-secondary/15 p-2">
  <img src="/images/news/campus-digital-library.jpg" alt="Không gian Thư viện Số hiện đại tại HCM-UTE" class="w-full h-auto rounded-lg object-cover" />
  <figcaption class="pt-2 text-center text-xs font-medium text-muted-foreground italic">
    Không gian học tập mở tràn ngập ánh sáng tự nhiên và trạm tra cứu tài liệu số thông minh tại Thư viện Trung tâm HCM-UTE
  </figcaption>
</figure>
<p>Công trình mang đến môi trường học tập học thuật đẳng cấp quốc tế, thân thiện và giàu cảm hứng sáng tạo:</p>
<ul>
  <li><strong>Phân khu chức năng thông minh:</strong> Thư viện bao gồm 30 phòng thảo luận nhóm cách âm bằng kính, khu vực đọc sách yên tĩnh chuyên sâu, khu cà phê học thuật và phòng lab máy tính cấu hình cao phục vụ phân tích dữ liệu nghiên cứu.</li>
  <li><strong>Kết nối học liệu số toàn cầu:</strong> Sinh viên sử dụng tài khoản CampusUTE để truy cập miễn phí toàn văn hàng triệu bài báo khoa học từ các cơ sở dữ liệu hàng đầu thế giới như ScienceDirect, IEEE Xplore, SpringerLink.</li>
</ul>`,
    priority: 'NORMAL',
    publishedBy: 'Ban Giám đốc Thư viện Trung tâm',
    createdAt: '2026-08-25T08:00:00Z',
    publishAt: '2026-08-25T08:00:00Z',
  },
  // 9. Commencement Graduation
  {
    id: 'notice-commencement-graduation',
    title: 'Lễ Trao bằng Tốt nghiệp Tân Kỹ sư & Cử nhân: Tự hào thế hệ nhân tài vững bước tương lai',
    content: `<p>Tại Quảng trường Trung tâm và Hội trường Lớn, Trường Đại học Công nghệ Kỹ thuật TP.HCM long trọng tổ chức Lễ Bế giảng và Trao bằng Tốt nghiệp Đại học chính quy đợt 2 năm 2026.</p>
<figure class="my-5 overflow-hidden rounded-xl border border-border/70 bg-secondary/15 p-2">
  <img src="/images/news/commencement-graduation.jpg" alt="Khoảnh khắc tung mũ tốt nghiệp rạng rỡ của tân khoa HCM-UTE" class="w-full h-auto rounded-lg object-cover" />
  <figcaption class="pt-2 text-center text-xs font-medium text-muted-foreground italic">
    Khoảnh khắc tung mũ cử nhân rạng rỡ niềm tự hào và khát vọng cống hiến của các tân kỹ sư HCM-UTE trước sảnh chính Tòa nhà Trung tâm
  </figcaption>
</figure>
<p>Lễ tốt nghiệp đánh dấu cột mốc trưởng thành quan trọng sau 4 năm tôi rèn tri thức và kỹ năng thực hành nghề nghiệp:</p>
<ul>
  <li><strong>Tỷ lệ có việc làm ấn tượng:</strong> Đợt tốt nghiệp ghi nhận tỷ lệ sinh viên tốt nghiệp loại Xuất sắc và Giỏi đạt 28,4%. Đặc biệt, theo khảo sát độc lập của Phòng Quan hệ Doanh nghiệp, hơn 96% tân kỹ sư đã có việc làm chính thức hoặc nhận được thư mời tuyển dụng từ trước ngày nhận bằng.</li>
  <li><strong>Vinh danh Thủ khoa:</strong> Hiệu trưởng Nhà trường đã trực tiếp trao bằng khen, kỷ niệm chương và học bổng sau đại học cho 12 Thủ khoa xuất sắc của các ngành đào tạo.</li>
</ul>`,
    priority: 'HIGH',
    publishedBy: 'Phòng Đào tạo & Ban Giám hiệu',
    createdAt: '2026-08-22T08:00:00Z',
    publishAt: '2026-08-22T08:00:00Z',
  },
  // 10. Blood Donation Day
  {
    id: 'notice-blood-donation-day',
    title: 'Ngày hội Hiến máu Tình nguyện "Giọt hồng Công nghệ" Lần thứ VI: Trao hy vọng, sẻ chia sự sống',
    content: `<p>Hội Chữ thập đỏ phối hợp cùng Đoàn Thanh niên Trường Đại học Công nghệ Kỹ thuật TP.HCM tổ chức Ngày hội Hiến máu Tình nguyện "Giọt hồng Công nghệ" lần thứ VI năm 2026 với thông điệp "Một giọt máu cho đi - Một cuộc đời ở lại".</p>
<figure class="my-5 overflow-hidden rounded-xl border border-border/70 bg-secondary/15 p-2">
  <img src="/images/news/blood-donation-day.jpg" alt="Sinh viên và y bác sĩ tại Ngày hội Hiến máu Giọt hồng Công nghệ" class="w-full h-auto rounded-lg object-cover" />
  <figcaption class="pt-2 text-center text-xs font-medium text-muted-foreground italic">
    Các y bác sĩ Bệnh viện Truyền máu Huyết học và tình nguyện viên UTE ân cần chăm sóc sinh viên hiến máu tình nguyện
  </figcaption>
</figure>
<p>Hoạt động nhân văn sâu sắc đã nhận được sự hưởng ứng nhiệt tình từ đông đảo cán bộ, giảng viên và sinh viên toàn trường:</p>
<ul>
  <li><strong>Tiếp nhận an toàn:</strong> Tiếp nhận hơn 650 đơn vị máu quý giá phục vụ cấp cứu và điều trị người bệnh tại các bệnh viện tuyến đầu theo quy trình 1 chiều khép kín, an toàn vô trùng tuyệt đối.</li>
  <li><strong>Chính sách rèn luyện:</strong> Mỗi người tham gia hiến máu được nhận Giấy chứng nhận hiến máu tình nguyện, quà tặng bồi dưỡng sức khỏe và được cộng 10 điểm rèn luyện sinh viên theo quy định học vụ.</li>
</ul>`,
    priority: 'NORMAL',
    publishedBy: 'Hội Chữ thập đỏ & Đoàn Thanh niên UTE',
    createdAt: '2026-08-18T07:30:00Z',
    publishAt: '2026-08-18T07:30:00Z',
  },
  // 11. Student Dormitory Campus
  {
    id: 'notice-student-dormitory-campus',
    title: 'Nâng cấp Không gian Sống Xanh & Tiện ích Thể thao tại Ký túc xá Sinh viên HCM-UTE',
    content: `<p>Ban Quản lý Ký túc xá công bố hoàn tất dự án "KTX Xanh - An toàn - Văn minh" nhằm nâng cao toàn diện chất lượng đời sống nội trú cho hơn 3.200 sinh viên nội trú tại cơ sở chính.</p>
<figure class="my-5 overflow-hidden rounded-xl border border-border/70 bg-secondary/15 p-2">
  <img src="/images/news/student-dormitory-campus.jpg" alt="Khuôn viên xanh rợp bóng cây tại KTX HCM-UTE" class="w-full h-auto rounded-lg object-cover" />
  <figcaption class="pt-2 text-center text-xs font-medium text-muted-foreground italic">
    Khuôn viên xanh mát và các tòa nhà nội trú KTX HCM-UTE tràn ngập ánh nắng buổi chiều an lành
  </figcaption>
</figure>
<p>Khuôn viên KTX mới được cải tạo theo hướng đại học xanh bền vững, thân thiện với môi trường:</p>
<ul>
  <li><strong>Tiện ích hiện đại:</strong> Lắp đặt pin năng lượng mặt trời áp mái cung cấp 40% điện chiếu sáng công cộng, hệ thống cửa kiểm soát vân tay/thẻ từ thông minh và trạm lọc nước uống tinh khiết đạt chuẩn y tế.</li>
  <li><strong>Thể thao học đường:</strong> Sân bóng rổ, khu tập gym ngoài trời và phòng tự học nhóm máy lạnh mở cửa miễn phí phục vụ sinh viên nội trú.</li>
</ul>`,
    priority: 'NORMAL',
    publishedBy: 'Ban Quản lý Ký túc xá Sinh viên',
    createdAt: '2026-08-15T09:00:00Z',
    publishAt: '2026-08-15T09:00:00Z',
  },
  // 12. Faculty Excellence Awards
  {
    id: 'notice-faculty-excellence-awards',
    title: 'Lễ Tôn vinh Giảng viên Xuất sắc & Nhà Khoa học Tiêu biểu HCM-UTE Năm học 2025-2026',
    content: `<p>Hội đồng Thi đua - Khen thưởng Trường Đại học Công nghệ Kỹ thuật TP.HCM trang trọng tổ chức Lễ Tôn vinh Giảng viên Xuất sắc và Nhà Khoa học Tiêu biểu năm học tại Hội trường Trung tâm.</p>
<figure class="my-5 overflow-hidden rounded-xl border border-border/70 bg-secondary/15 p-2">
  <img src="/images/news/faculty-excellence-awards.jpg" alt="Hiệu trưởng trao cúp vinh danh cho Giảng viên xuất sắc HCM-UTE" class="w-full h-auto rounded-lg object-cover" />
  <figcaption class="pt-2 text-center text-xs font-medium text-muted-foreground italic">
    Hiệu trưởng Nhà trường trao tặng Kỷ niệm chương pha lê và Bằng khen vinh danh Nhà khoa học nữ xuất sắc có nhiều công bố ISI/Scopus Q1
  </figcaption>
</figure>
<p>Buổi lễ là sự ghi nhận xứng đáng đối với tinh thần cống hiến không ngừng nghỉ của đội ngũ cán bộ giảng dạy và nghiên cứu:</p>
<ul>
  <li><strong>Công bố quốc tế vượt bậc:</strong> Trong năm học qua, toàn trường đã công bố hơn 450 bài báo khoa học thuộc danh mục WoS/Scopus, đăng ký 18 bằng sáng chế độc quyền cùng 35 đề tài nghiên cứu cấp Nhà nước và cấp Bộ.</li>
  <li><strong>Đổi mới giảng dạy:</strong> Trao giải "Giảng viên Dạy giỏi Sáng tạo" cho 25 thầy cô giáo áp dụng thành công phương pháp học tập kết hợp (Blended Learning) định hướng chuyển đổi số.</li>
</ul>`,
    priority: 'HIGH',
    publishedBy: 'Phòng Tổ chức - Cán bộ & Ban Giám hiệu',
    createdAt: '2026-08-10T14:00:00Z',
    publishAt: '2026-08-10T14:00:00Z',
  },
  // 13. Big Data & AI Research Lab
  {
    id: 'notice-home-01',
    title: 'Khởi động Phòng Nghiên cứu Dữ liệu lớn (Big Data) & Trí tuệ Nhân tạo Khoa CNTT HCM-UTE',
    content: `<p>Khoa Công nghệ Thông tin - Trường Đại học Công nghệ Kỹ thuật TP.HCM trân trọng thông báo chính thức khánh thành và đưa vào vận hành cụm máy chủ điện toán hiệu năng cao phục vụ nghiên cứu Dữ liệu lớn (Big Data) và Trí tuệ nhân tạo (AI Lab) tại cơ sở chính.</p>
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
</ul>`,
    priority: 'HIGH',
    publishedBy: 'Khoa Công nghệ Thông tin & Phòng Đào tạo',
    createdAt: '2026-09-08T08:00:00Z',
    publishAt: '2026-09-08T08:00:00Z',
  },
  // 14. Tech Career Expo
  {
    id: 'notice-home-02',
    title: 'Ngày hội việc làm và Kết nối doanh nghiệp công nghệ thông tin UTE Tech Career Expo 2026',
    content: `<p>Nhà trường phối hợp cùng hơn 60 tập đoàn công nghệ đa quốc gia và doanh nghiệp công nghệ thông tin hàng đầu tổ chức Ngày hội Việc làm và Kết nối Doanh nghiệp UTE Tech Career Expo 2026 tại khuôn viên sảnh A & B.</p>
<figure class="my-5 overflow-hidden rounded-xl border border-border/70 bg-secondary/15 p-2">
  <img src="/images/news/tech-career-expo.jpg" alt="Ngày hội việc làm UTE Tech Career Expo" class="w-full h-auto rounded-lg object-cover" />
  <figcaption class="pt-2 text-center text-xs font-medium text-muted-foreground italic">
    Không khí sôi nổi tại UTE Tech Career Expo 2026 với hơn 60 tập đoàn công nghệ và 1.200 vị trí tuyển dụng kỹ sư
  </figcaption>
</figure>
<p>Sự kiện mang đến hàng nghìn cơ hội nghề nghiệp chất lượng cao và định hướng phát triển sự nghiệp vững chắc cho sinh viên các khối ngành kỹ thuật công nghệ:</p>
<ul>
  <li><strong>Quy mô tuyển dụng:</strong> Hơn 1.200 vị trí tuyển dụng thực tập sinh và kỹ sư phần mềm chính thức (Fresher/Junior Software Engineer, AI Engineer, Cloud DevOps, Embedded Systems).</li>
  <li><strong>Phỏng vấn nhanh tại chỗ:</strong> Hơn 30 doanh nghiệp tổ chức phỏng vấn và trao thư mời nhận việc (Offer Letter) trực tiếp ngay tại gian hàng.</li>
  <li><strong>Hoạt động cố vấn:</strong> Gian hàng tư vấn sửa CV 1-on-1 cùng các chuyên gia nhân sự kỳ cựu và hội thảo định hướng "Kỹ sư thích ứng kỷ nguyên AI".</li>
</ul>`,
    priority: 'HIGH',
    publishedBy: 'Trung Tâm Hướng Nghiệp & Quan Hệ Doanh Nghiệp',
    createdAt: '2026-09-09T10:00:00Z',
    publishAt: '2026-09-09T10:00:00Z',
  },
  // 15. Scholarship Ceremony
  {
    id: 'notice-home-03',
    title: 'Danh sách sinh viên đủ điều kiện xét Học bổng khuyến khích học tập Học kỳ vừa qua',
    content: `<p>Hội đồng xét duyệt Học bổng Nhà trường trân trọng thông báo kết quả đối soát điểm trung bình chung học tập (GPA) kết hợp điểm rèn luyện (ĐRL) xét cấp Học bổng Khuyến khích học tập.</p>
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
</ul>`,
    priority: 'NORMAL',
    publishedBy: 'Phòng Công Tác Sinh Viên & Đào Tạo',
    createdAt: '2026-09-05T09:00:00Z',
    publishAt: '2026-09-05T09:00:00Z',
  },
  // 16. Course Registration Window
  {
    id: 'notice-home-04',
    title: 'Kế hoạch mở cổng Đăng ký học phần chính thức Học kỳ 1 năm học 2026-2027',
    content: `<p>Phòng Đào tạo thông báo kế hoạch tổ chức đăng ký học phần chính thức cho Học kỳ 1 năm học 2026-2027 trên hệ thống quản lý học tập tích hợp CampusUTE.</p>
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
</ul>`,
    priority: 'HIGH',
    publishedBy: 'Phòng Đào tạo UTE',
    createdAt: '2026-08-25T08:00:00Z',
    publishAt: '2026-08-25T08:00:00Z',
  },
  // 17. Thesis Defense
  {
    id: 'notice-home-05',
    title: 'Lễ bảo vệ Khóa luận tốt nghiệp (KLTN) Khoa Công nghệ Thông tin đợt 2 năm học 2025-2026',
    content: `<p>Khoa Công nghệ Thông tin - Trường Đại học Công nghệ Kỹ thuật TP.HCM long trọng tổ chức Lễ bảo vệ Khóa luận tốt nghiệp (KLTN) đợt 2 cho sinh viên các chuyên ngành Kỹ thuật phần mềm, Hệ thống thông tin, An toàn thông tin và Trí tuệ nhân tạo.</p>
<figure class="my-5 overflow-hidden rounded-xl border border-border/70 bg-secondary/15 p-2">
  <img src="/images/news/thesis-defense.jpg" alt="Lễ bảo vệ Khóa luận tốt nghiệp Khoa CNTT HCM-UTE" class="w-full h-auto rounded-lg object-cover" />
  <figcaption class="pt-2 text-center text-xs font-medium text-muted-foreground italic">
    Sinh viên Khoa Công nghệ Thông tin tự tin báo cáo đề tài khóa luận trước Hội đồng chấm thi học thuật HCM-UTE
  </figcaption>
</figure>
<p>Đợt bảo vệ ghi nhận sự trưởng thành vượt bậc về hàm lượng khoa học, tính ứng dụng thực tiễn và khả năng làm chủ công nghệ của sinh viên:</p>
<ul>
  <li><strong>Quy mô hội đồng:</strong> 18 hội đồng chuyên môn với sự tham gia của các Giáo sư, Tiến sĩ đầu ngành và các chuyên gia cấp cao (Chief Architect, Engineering Lead) từ các doanh nghiệp đối tác.</li>
  <li><strong>Số lượng đề tài:</strong> Hơn 120 nhóm sinh viên tham gia bảo vệ với các hướng nghiên cứu tiêu biểu: Hệ thống phân tán chịu tải cao, Thị giác máy tính trong y tế số, An toàn mạng dựa trên Zero-Trust.</li>
</ul>`,
    priority: 'HIGH',
    publishedBy: 'Khoa Công nghệ Thông tin',
    createdAt: '2026-09-12T08:30:00Z',
    publishAt: '2026-09-12T08:30:00Z',
  },
  // 18. Scientific Research Awards
  {
    id: 'notice-home-06',
    title: 'Vinh danh các nhóm sinh viên đạt Giải thưởng Nghiên cứu Khoa học & Sáng tạo Đổi mới Công nghệ cấp Trường',
    content: `<p>Trường Đại học Công nghệ Kỹ thuật TP.HCM tổ chức Lễ tổng kết và trao giải thưởng Nghiên cứu Khoa học Sinh viên (NCKH) & Đổi mới Sáng tạo Công nghệ năm học 2025-2026 tại Hội trường Lớn Khu A.</p>
<figure class="my-5 overflow-hidden rounded-xl border border-border/70 bg-secondary/15 p-2">
  <img src="/images/news/scientific-research.jpg" alt="Lễ vinh danh Giải thưởng Nghiên cứu Khoa học Sinh viên HCM-UTE" class="w-full h-auto rounded-lg object-cover" />
  <figcaption class="pt-2 text-center text-xs font-medium text-muted-foreground italic">
    Đại diện Ban Giám hiệu Nhà trường trao bằng khen và cúp lưu niệm cho các nhóm sinh viên đạt giải thưởng Nghiên cứu Khoa học 2026
  </figcaption>
</figure>
<p>Phong trào nghiên cứu khoa học sinh viên năm nay tiếp tục khẳng định vị thế tiên phong của HCM-UTE trong sáng tạo kỹ thuật và giải quyết các bài toán thực tiễn của xã hội:</p>
<ul>
  <li><strong>Kết quả chung cuộc:</strong> Ban giám khảo đã chấm chọn và trao 05 Giải Nhất, 10 Giải Nhì, 15 Giải Ba cùng 20 Giải Khuyến khích từ hơn 350 công trình dự thi trên toàn trường.</li>
  <li><strong>Chính sách ưu đãi:</strong> Sinh viên đạt giải được cộng điểm rèn luyện tối đa (100 điểm), ưu tiên xét tuyển đề tài Khóa luận tốt nghiệp và nhận học bổng tài năng của Nhà trường.</li>
</ul>`,
    priority: 'NORMAL',
    publishedBy: 'Phòng Khoa học Công nghệ & Hợp tác Quốc tế',
    createdAt: '2026-09-10T14:00:00Z',
    publishAt: '2026-09-10T14:00:00Z',
  },
];

type CategoryFilter = 'ALL' | ArticleCategoryCode;

interface CategoryTab {
  key: CategoryFilter;
  labelVi: string;
  labelEn: string;
  icon: React.ReactNode;
}

const CATEGORY_TABS: CategoryTab[] = [
  {
    key: 'ALL',
    labelVi: 'Tất cả bài viết',
    labelEn: 'All Stories',
    icon: <Newspaper className="h-4 w-4" />,
  },
  {
    key: 'RESEARCH_TECH',
    labelVi: 'Nghiên cứu & Công nghệ',
    labelEn: 'Research & Tech',
    icon: <FlaskConical className="h-4 w-4" />,
  },
  {
    key: 'AWARDS_HONORS',
    labelVi: 'Học bổng & Khen thưởng',
    labelEn: 'Scholarships & Honors',
    icon: <Award className="h-4 w-4" />,
  },
  {
    key: 'STUDENT_LIFE',
    labelVi: 'Đời sống Sinh viên',
    labelEn: 'Student Life',
    icon: <Users className="h-4 w-4" />,
  },
  {
    key: 'CULTURE_ARTS',
    labelVi: 'Văn hóa & Nghệ thuật',
    labelEn: 'Culture & Arts',
    icon: <Sparkles className="h-4 w-4" />,
  },
  {
    key: 'ACADEMIC_AFFAIRS',
    labelVi: 'Đào tạo & Học vụ',
    labelEn: 'Academic Affairs',
    icon: <BookOpen className="h-4 w-4" />,
  },
  {
    key: 'CAREER_OPPORTUNITIES',
    labelVi: 'Việc làm & Doanh nghiệp',
    labelEn: 'Career & Industry',
    icon: <Briefcase className="h-4 w-4" />,
  },
];

export function HomeNewsSection() {
  const { user } = useAuth();
  const { locale } = useI18n();
  const [items, setItems] = useState<AnnouncementRecord[]>(HOMEPAGE_FALLBACK_NEWS);
  const [selectedNotice, setSelectedNotice] = useState<AnnouncementRecord | null>(null);
  const [activeCategory, setActiveCategory] = useState<CategoryFilter>('ALL');
  const [isExpanded, setIsExpanded] = useState(false);

  const isVi = locale === 'vi';

  useEffect(() => {
    let isMounted = true;
    if (!user) {
      return;
    }
    announcementsApi
      .getMy({ page: 1, limit: 18 })
      .then((res) => {
        if (isMounted && res.data && res.data.length > 0) {
          // Merge API data with fallback photos if API articles lack images
          const merged = res.data.map((apiItem, idx) => {
            const fallbackMatch = HOMEPAGE_FALLBACK_NEWS.find(
              (fb) => fb.id === apiItem.id || fb.title === apiItem.title,
            ) || HOMEPAGE_FALLBACK_NEWS[idx % HOMEPAGE_FALLBACK_NEWS.length];
            return {
              ...fallbackMatch,
              ...apiItem,
              content: apiItem.content || fallbackMatch.content,
            };
          });
          setItems(merged);
        }
      })
      .catch(() => {
        // Fallback to static editorial notices
      });
    return () => {
      isMounted = false;
    };
  }, [user]);

  // Filter items by active category
  const filteredItems = useMemo(() => {
    if (activeCategory === 'ALL') {
      return items;
    }
    return items.filter((item) => {
      const domain = resolveAnnouncementDomain(item, locale);
      return domain.categoryCode === activeCategory;
    });
  }, [items, activeCategory, locale]);

  // Featured and secondary streams
  const featured = filteredItems[0] || HOMEPAGE_FALLBACK_NEWS[0];
  const secondaryList = filteredItems.slice(1, 4);
  const galleryGrid = filteredItems.slice(4);
  const visibleGridItems = isExpanded ? galleryGrid : galleryGrid.slice(0, 6);

  return (
    <section className="border-t border-border/70 bg-secondary/15 py-12 sm:py-16">
      <div className="mx-auto max-w-[1280px] px-4 sm:px-6 lg:px-12 space-y-8">
        {/* Section Header */}
        <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
          <div className="space-y-1.5">
            <SectionEyebrow>
              {isVi ? 'TẠP CHÍ & BẢN TIN ĐẠI HỌC' : 'CAMPUS PRESS & MAGAZINE'}
            </SectionEyebrow>
            <h2 className="text-2xl sm:text-3xl font-extrabold tracking-tight text-foreground">
              {isVi ? 'Nhịp Sống Học Thuật & Sáng Tạo HCMUTE' : 'HCMUTE Academic Life & Innovation'}
            </h2>
            <p className="text-sm text-muted-foreground max-w-2xl">
              {isVi
                ? 'Tuyển tập 18 phóng sự báo chí, thành tựu nghiên cứu khoa học công nghệ, phong trào sinh viên và chính sách đào tạo mới nhất.'
                : '18 authentic editorial stories covering cutting-edge research laboratories, student achievements, and academic announcements.'}
            </p>
          </div>

          <LocalizedLink
            href="/dashboard/announcements"
            className="group inline-flex min-h-8 items-center gap-1.5 font-bold text-sm text-primary hover:underline self-start sm:self-end"
          >
            <span>{isVi ? 'Xem tất cả thông báo' : 'View all announcements'}</span>
            <ArrowRight className="h-4 w-4 transition-transform group-hover:translate-x-1" />
          </LocalizedLink>
        </div>

        {/* Category Navigation Pills */}
        <div className="flex flex-wrap items-center gap-2 overflow-x-auto pb-1 scrollbar-none">
          {CATEGORY_TABS.map((tab) => {
            const count =
              tab.key === 'ALL'
                ? items.length
                : items.filter(
                    (item) => resolveAnnouncementDomain(item, locale).categoryCode === tab.key,
                  ).length;
            const isActive = activeCategory === tab.key;
            return (
              <button
                key={tab.key}
                onClick={() => {
                  setActiveCategory(tab.key);
                  setIsExpanded(false);
                }}
                className={cn(
                  'inline-flex items-center gap-2 rounded-full px-4 py-2 text-xs font-semibold transition-all shadow-2xs cursor-pointer border',
                  isActive
                    ? 'bg-primary text-primary-foreground border-primary shadow-sm ring-2 ring-primary/20'
                    : 'bg-card text-muted-foreground border-border/80 hover:bg-accent hover:text-foreground',
                )}
              >
                {tab.icon}
                <span>{isVi ? tab.labelVi : tab.labelEn}</span>
                <span
                  className={cn(
                    'rounded-full px-1.5 py-0.2 text-[10px] font-bold',
                    isActive ? 'bg-primary-foreground/20 text-primary-foreground' : 'bg-muted text-muted-foreground',
                  )}
                >
                  {count}
                </span>
              </button>
            );
          })}
        </div>

        {/* Primary News Stage (Featured + Sidebar) */}
        <div className="grid grid-cols-1 gap-6 lg:grid-cols-12 items-stretch">
          {/* Featured Article (7 columns on desktop) */}
          <div className="lg:col-span-7 flex flex-col">
            <AnnouncementFeedCard
              announcement={featured}
              onClick={() => setSelectedNotice(featured)}
              variant="featured"
              layout="vertical"
              locale={locale}
              className="h-full"
            />
          </div>

          {/* Secondary News Stream (5 columns on desktop) */}
          <div className="lg:col-span-5 flex flex-col justify-between gap-4">
            {secondaryList.map((ann) => (
              <AnnouncementFeedCard
                key={ann.id}
                announcement={ann}
                onClick={() => setSelectedNotice(ann)}
                variant="standard"
                locale={locale}
              />
            ))}
            {secondaryList.length === 0 && (
              <div className="flex h-full min-h-[280px] items-center justify-center rounded-2xl border border-dashed border-border/80 bg-card/50 p-6 text-center text-sm text-muted-foreground">
                {isVi ? 'Không có thêm bài viết trong danh mục này' : 'No more stories in this category'}
              </div>
            )}
          </div>
        </div>

        {/* 3-Column Magazine Card Grid for Additional Stories */}
        {galleryGrid.length > 0 && (
          <div className="space-y-6 pt-4">
            <div className="flex items-center justify-between border-t border-border/60 pt-6">
              <h3 className="text-lg font-bold text-foreground flex items-center gap-2">
                <Sparkles className="h-4 w-4 text-primary" />
                <span>{isVi ? 'Phóng sự & Chuyên đề Khác' : 'Special Feature Stories'}</span>
                <span className="text-xs font-normal text-muted-foreground">({galleryGrid.length} bài viết)</span>
              </h3>
            </div>

            <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3">
              {visibleGridItems.map((ann) => {
                const cover = resolveArticleCover(ann);
                const domain = resolveAnnouncementDomain(ann, locale);
                const reading = calculateReadingTime(ann.content);
                const sapo = extractAnnouncementExcerpt(ann.content, 110);
                const pub = formatAnnouncementPublisher(ann.publishedBy, locale);
                const relTime = formatRelativeTime(ann.publishAt || ann.createdAt, locale);

                return (
                  <article
                    key={ann.id}
                    onClick={() => setSelectedNotice(ann)}
                    className="group flex flex-col overflow-hidden rounded-2xl border border-border/70 bg-card shadow-2xs transition-all duration-300 hover:-translate-y-1 hover:border-primary/50 hover:shadow-lg cursor-pointer"
                  >
                    {/* 16:9 Thumbnail */}
                    <div className="relative aspect-video w-full overflow-hidden bg-muted">
                      {/* eslint-disable-next-line @next/next/no-img-element */}
                      <img
                        src={cover}
                        alt={ann.title}
                        loading="lazy"
                        className="h-full w-full object-cover transition-transform duration-500 group-hover:scale-105"
                      />
                      <div className="absolute top-2.5 left-2.5 z-10">
                        <span className="inline-flex items-center gap-1 rounded-md bg-background/90 px-2 py-0.5 text-[11px] font-bold text-foreground backdrop-blur-md shadow-2xs">
                          {domain.categoryLabel}
                        </span>
                      </div>
                    </div>

                    {/* Card Content */}
                    <div className="flex flex-1 flex-col justify-between p-5 space-y-3">
                      <div className="space-y-2">
                        <div className="flex items-center justify-between text-[11px] text-muted-foreground">
                          <span className="font-semibold text-primary">{pub}</span>
                          <span>{relTime}</span>
                        </div>
                        <h4 className="font-bold text-sm sm:text-base leading-snug line-clamp-2 text-foreground group-hover:text-primary transition-colors">
                          {ann.title}
                        </h4>
                        <p className="text-xs text-muted-foreground line-clamp-2 leading-relaxed">
                          {sapo}
                        </p>
                      </div>

                      <div className="flex items-center justify-between border-t border-border/50 pt-3 text-[11px] text-muted-foreground">
                        <span className="flex items-center gap-1">
                          <Clock className="h-3 w-3 text-muted-foreground" />
                          <span>{reading.displayText}</span>
                        </span>
                        <span className="font-bold text-primary inline-flex items-center gap-1 group-hover:translate-x-0.5 transition-transform">
                          <span>{isVi ? 'Đọc bài' : 'Read'}</span>
                          <ArrowRight className="h-3 w-3" />
                        </span>
                      </div>
                    </div>
                  </article>
                );
              })}
            </div>

            {/* Expand / Collapse Button */}
            {galleryGrid.length > 6 && (
              <div className="flex justify-center pt-2">
                <button
                  onClick={() => setIsExpanded(!isExpanded)}
                  className="inline-flex items-center gap-2 rounded-full border border-border/80 bg-card px-6 py-2.5 text-xs font-bold text-foreground shadow-xs hover:bg-accent transition-colors cursor-pointer"
                >
                  {isExpanded ? (
                    <>
                      <span>{isVi ? 'Thu gọn bài viết' : 'Show Less'}</span>
                      <ChevronUp className="h-4 w-4" />
                    </>
                  ) : (
                    <>
                      <span>{isVi ? `Xem thêm ${galleryGrid.length - 6} bài viết khác` : `View ${galleryGrid.length - 6} More Stories`}</span>
                      <ChevronDown className="h-4 w-4" />
                    </>
                  )}
                </button>
              </div>
            )}
          </div>
        )}
      </div>

      {/* Embedded Announcement Reader Modal */}
      <AnnouncementReaderModal
        announcement={selectedNotice}
        isOpen={Boolean(selectedNotice)}
        onClose={() => setSelectedNotice(null)}
        allAnnouncements={items}
        onSelectAnnouncement={(ann) => setSelectedNotice(ann)}
      />
    </section>
  );
}
