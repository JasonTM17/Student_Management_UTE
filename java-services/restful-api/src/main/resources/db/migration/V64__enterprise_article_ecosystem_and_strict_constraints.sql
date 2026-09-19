-- Flyway Migration V64: Enterprise Academic Article & Media Ecosystem with Strict Relational Constraints
-- Creates normalized categories, tags, cross-reference maps, media galleries, attachments, and engagement metrics
-- Enforces strict CHECK constraints, foreign keys, and seeds 18 rich editorial articles with 16:9 photography

CREATE SCHEMA IF NOT EXISTS engagement;

-- 1. Table: engagement."ArticleCategory"
CREATE TABLE IF NOT EXISTS engagement."ArticleCategory" (
    "id" VARCHAR(60) PRIMARY KEY,
    "code" VARCHAR(40) UNIQUE NOT NULL,
    "nameVi" VARCHAR(120) NOT NULL,
    "nameEn" VARCHAR(120) NOT NULL,
    "slug" VARCHAR(80) UNIQUE NOT NULL,
    "description" VARCHAR(500),
    "colorTone" VARCHAR(30) NOT NULL DEFAULT 'blue',
    "iconType" VARCHAR(40) NOT NULL DEFAULT 'newspaper',
    "displayOrder" INTEGER NOT NULL DEFAULT 0,
    "isActive" BOOLEAN NOT NULL DEFAULT TRUE,
    "createdAt" TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT article_category_code_ck CHECK ("code" ~ '^[A-Z0-9_]+$'),
    CONSTRAINT article_category_slug_ck CHECK ("slug" ~ '^[a-z0-9-]+$'),
    CONSTRAINT article_category_tone_ck CHECK ("colorTone" IN ('blue', 'indigo', 'emerald', 'amber', 'rose', 'purple', 'cyan', 'slate')),
    CONSTRAINT article_category_order_ck CHECK ("displayOrder" >= 0)
);

-- Seed 6 institutional categories
INSERT INTO engagement."ArticleCategory" ("id", "code", "nameVi", "nameEn", "slug", "description", "colorTone", "iconType", "displayOrder") VALUES
('cat-research-tech', 'RESEARCH_TECH', 'Nghiên cứu & Công nghệ', 'Research & Technology', 'nghien-cuu-cong-nghe', 'Các công trình nghiên cứu khoa học, chuyển giao công nghệ, lab AI, bán dẫn và robotics', 'cyan', 'flask', 1),
('cat-awards-honors', 'AWARDS_HONORS', 'Học bổng & Khen thưởng', 'Scholarships & Honors', 'hoc-bong-khen-thuong', 'Chính sách học bổng khuyến khích, học bổng doanh nghiệp và vinh danh sinh viên, giảng viên xuất sắc', 'emerald', 'award', 2),
('cat-student-life', 'STUDENT_LIFE', 'Đời sống Sinh viên', 'Student Life & Youth', 'doi-song-sinh-vien', 'Hoạt động đoàn hội, chiến dịch tình nguyện, câu lạc bộ, ký túc xá và phong trào thể thao', 'amber', 'users', 3),
('cat-culture-arts', 'CULTURE_ARTS', 'Văn hóa & Nghệ thuật', 'Culture & Arts', 'van-hoa-nghe-thuat', 'Hội diễn văn nghệ truyền thống, gala tri ân ngày nhà giáo 20/11 và sự kiện văn hóa học đường', 'rose', 'sparkles', 4),
('cat-academic-affairs', 'ACADEMIC_AFFAIRS', 'Đào tạo & Học vụ', 'Academic Affairs & Training', 'dao-tao-hoc-vu', 'Quy định đào tạo, đăng ký học phần, bảo vệ khóa luận tốt nghiệp và lễ trao bằng', 'indigo', 'book', 5),
('cat-career-opps', 'CAREER_OPPORTUNITIES', 'Cơ hội Việc làm', 'Career & Industry Connections', 'co-hoi-viec-lam', 'Ngày hội việc làm, thực tập doanh nghiệp, ngày hội kết nối hướng nghiệp và tuyển dụng', 'purple', 'briefcase', 6)
ON CONFLICT ("id") DO UPDATE SET
    "nameVi" = EXCLUDED."nameVi",
    "nameEn" = EXCLUDED."nameEn",
    "colorTone" = EXCLUDED."colorTone",
    "iconType" = EXCLUDED."iconType",
    "displayOrder" = EXCLUDED."displayOrder";

-- 2. Table: engagement."ArticleTag"
CREATE TABLE IF NOT EXISTS engagement."ArticleTag" (
    "id" VARCHAR(60) PRIMARY KEY,
    "slug" VARCHAR(60) UNIQUE NOT NULL,
    "nameVi" VARCHAR(80) NOT NULL,
    "nameEn" VARCHAR(80) NOT NULL,
    "usageCount" INTEGER NOT NULL DEFAULT 0,
    "createdAt" TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT article_tag_slug_ck CHECK ("slug" ~ '^[a-z0-9-]+$'),
    CONSTRAINT article_tag_name_ck CHECK (length(trim("nameVi")) >= 2),
    CONSTRAINT article_tag_usage_ck CHECK ("usageCount" >= 0)
);

-- Seed 12 academic tags
INSERT INTO engagement."ArticleTag" ("id", "slug", "nameVi", "nameEn", "usageCount") VALUES
('tag-ai', 'ai-artificial-intelligence', 'Trí tuệ nhân tạo', 'Artificial Intelligence', 5),
('tag-semiconductor', 'semiconductor-ic', 'Vi mạch bán dẫn', 'Semiconductor & IC Design', 4),
('tag-robotics', 'robotics-automation', 'Robotics & Tự động hóa', 'Robotics & Automation', 4),
('tag-mua-he-xanh', 'mua-he-xanh-volunteer', 'Mùa hè xanh', 'Green Summer Campaign', 3),
('tag-champions-cup', 'champions-cup-sports', 'UTE Champions Cup', 'UTE Champions Cup', 3),
('tag-teachers-day', 'teachers-day-20-11', 'Ngày Nhà giáo 20/11', 'Teachers Day 20/11', 3),
('tag-ieee', 'ieee-stem-conference', 'Hội nghị IEEE', 'IEEE Conference', 3),
('tag-hackathon', 'hackathon-innovation', 'Đấu trường Hackathon', 'Hackathon Innovation', 3),
('tag-digital-library', 'digital-library-learning', 'Thư viện số', 'Digital Library Commons', 3),
('tag-graduation', 'graduation-commencement', 'Lễ Tốt nghiệp', 'Graduation Ceremony', 3),
('tag-blood-donation', 'giot-hong-blood-donation', 'Hiến máu nhân đạo', 'Blood Donation', 3),
('tag-scholarship', 'scholarship-financial-aid', 'Học bổng khuyến khích', 'Academic Scholarship', 4)
ON CONFLICT ("id") DO UPDATE SET
    "nameVi" = EXCLUDED."nameVi",
    "nameEn" = EXCLUDED."nameEn",
    "usageCount" = EXCLUDED."usageCount";

-- 3. Table: engagement."AnnouncementTagMap"
CREATE TABLE IF NOT EXISTS engagement."AnnouncementTagMap" (
    "announcementId" VARCHAR(120) NOT NULL,
    "tagId" VARCHAR(60) NOT NULL,
    "createdAt" TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY ("announcementId", "tagId"),
    CONSTRAINT announcement_tag_announcement_fk
        FOREIGN KEY ("announcementId") REFERENCES engagement."Announcement"("id") ON DELETE CASCADE,
    CONSTRAINT announcement_tag_tag_fk
        FOREIGN KEY ("tagId") REFERENCES engagement."ArticleTag"("id") ON DELETE CASCADE
);

-- 4. Table: engagement."ArticleMediaGallery"
CREATE TABLE IF NOT EXISTS engagement."ArticleMediaGallery" (
    "id" VARCHAR(120) PRIMARY KEY,
    "announcementId" VARCHAR(120) NOT NULL,
    "mediaUrl" VARCHAR(500) NOT NULL,
    "thumbnailUrl" VARCHAR(500),
    "captionVi" VARCHAR(500) NOT NULL,
    "captionEn" VARCHAR(500),
    "altText" VARCHAR(255) NOT NULL,
    "mediaType" VARCHAR(30) NOT NULL DEFAULT 'IMAGE',
    "aspectRatio" VARCHAR(20) NOT NULL DEFAULT '16:9',
    "width" INTEGER,
    "height" INTEGER,
    "fileSizeBytes" BIGINT,
    "displayOrder" INTEGER NOT NULL DEFAULT 0,
    "isCover" BOOLEAN NOT NULL DEFAULT FALSE,
    "createdAt" TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT article_gallery_announcement_fk
        FOREIGN KEY ("announcementId") REFERENCES engagement."Announcement"("id") ON DELETE CASCADE,
    CONSTRAINT article_gallery_type_ck CHECK ("mediaType" IN ('IMAGE', 'VIDEO', 'DIAGRAM', 'INFOGRAPHIC')),
    CONSTRAINT article_gallery_order_ck CHECK ("displayOrder" >= 0)
);

CREATE UNIQUE INDEX IF NOT EXISTS article_gallery_single_cover_idx
    ON engagement."ArticleMediaGallery" ("announcementId")
    WHERE "isCover" = TRUE;

-- 5. Table: engagement."ArticleAttachment"
CREATE TABLE IF NOT EXISTS engagement."ArticleAttachment" (
    "id" VARCHAR(120) PRIMARY KEY,
    "announcementId" VARCHAR(120) NOT NULL,
    "fileName" VARCHAR(255) NOT NULL,
    "fileUrl" VARCHAR(500) NOT NULL,
    "fileSizeBytes" BIGINT NOT NULL,
    "mimeType" VARCHAR(120) NOT NULL,
    "checksumSha256" VARCHAR(64),
    "downloadCount" INTEGER NOT NULL DEFAULT 0,
    "isPublic" BOOLEAN NOT NULL DEFAULT TRUE,
    "createdAt" TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT article_attachment_announcement_fk
        FOREIGN KEY ("announcementId") REFERENCES engagement."Announcement"("id") ON DELETE CASCADE,
    CONSTRAINT article_attachment_size_ck CHECK ("fileSizeBytes" > 0 AND "fileSizeBytes" <= 52428800),
    CONSTRAINT article_attachment_mime_ck CHECK ("mimeType" IN (
        'application/pdf',
        'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
        'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
        'application/zip'
    )),
    CONSTRAINT article_attachment_downloads_ck CHECK ("downloadCount" >= 0)
);

-- 6. Table: engagement."ArticleReadingMetric"
CREATE TABLE IF NOT EXISTS engagement."ArticleReadingMetric" (
    "id" VARCHAR(120) PRIMARY KEY,
    "announcementId" VARCHAR(120) NOT NULL,
    "readerUserId" VARCHAR(120),
    "ipAddressHash" VARCHAR(64) NOT NULL,
    "userAgentHash" VARCHAR(64) NOT NULL,
    "dwellTimeSeconds" INTEGER NOT NULL DEFAULT 0,
    "readPercentage" INTEGER NOT NULL DEFAULT 0,
    "firstReadAt" TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "lastReadAt" TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT article_reading_announcement_fk
        FOREIGN KEY ("announcementId") REFERENCES engagement."Announcement"("id") ON DELETE CASCADE,
    CONSTRAINT article_reading_user_fk
        FOREIGN KEY ("readerUserId") REFERENCES campuscore_auth."User"("id") ON DELETE SET NULL,
    CONSTRAINT article_reading_dwell_ck CHECK ("dwellTimeSeconds" >= 0),
    CONSTRAINT article_reading_percentage_ck CHECK ("readPercentage" BETWEEN 0 AND 100)
);

CREATE INDEX IF NOT EXISTS article_reading_announcement_idx
    ON engagement."ArticleReadingMetric" ("announcementId", "lastReadAt" DESC);

-- 7. Additive Columns and Strict Constraints on engagement."Announcement"
ALTER TABLE engagement."Announcement"
    ADD COLUMN IF NOT EXISTS "categoryId" VARCHAR(60),
    ADD COLUMN IF NOT EXISTS "status" VARCHAR(30) NOT NULL DEFAULT 'PUBLISHED',
    ADD COLUMN IF NOT EXISTS "coverImageUrl" VARCHAR(500),
    ADD COLUMN IF NOT EXISTS "summary" VARCHAR(500),
    ADD COLUMN IF NOT EXISTS "readingTimeMinutes" INTEGER NOT NULL DEFAULT 3,
    ADD COLUMN IF NOT EXISTS "viewCount" INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS "uniqueReaderCount" INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS "slug" VARCHAR(240),
    ADD COLUMN IF NOT EXISTS "featuredOrder" INTEGER DEFAULT NULL;

-- Category Foreign Key
ALTER TABLE engagement."Announcement"
    DROP CONSTRAINT IF EXISTS announcement_category_fk,
    ADD CONSTRAINT announcement_category_fk
        FOREIGN KEY ("categoryId") REFERENCES engagement."ArticleCategory"("id")
        ON DELETE SET NULL;

-- Strict Check Constraints
ALTER TABLE engagement."Announcement"
    DROP CONSTRAINT IF EXISTS announcement_priority_ck,
    ADD CONSTRAINT announcement_priority_ck
        CHECK ("priority" IN ('LOW', 'NORMAL', 'HIGH', 'URGENT'));

ALTER TABLE engagement."Announcement"
    DROP CONSTRAINT IF EXISTS announcement_status_ck,
    ADD CONSTRAINT announcement_status_ck
        CHECK ("status" IN ('DRAFT', 'PENDING_REVIEW', 'APPROVED', 'SCHEDULED', 'PUBLISHED', 'ARCHIVED', 'REJECTED'));

ALTER TABLE engagement."Announcement"
    DROP CONSTRAINT IF EXISTS announcement_date_window_ck,
    ADD CONSTRAINT announcement_date_window_ck
        CHECK ("expiresAt" IS NULL OR "publishAt" IS NULL OR "publishAt" <= "expiresAt");

ALTER TABLE engagement."Announcement"
    DROP CONSTRAINT IF EXISTS announcement_metrics_ck,
    ADD CONSTRAINT announcement_metrics_ck
        CHECK ("viewCount" >= 0 AND "uniqueReaderCount" >= 0 AND "readingTimeMinutes" >= 1);

-- Unique Slug Index
CREATE UNIQUE INDEX IF NOT EXISTS announcement_slug_uq_idx
    ON engagement."Announcement" ("slug")
    WHERE "slug" IS NOT NULL;

CREATE INDEX IF NOT EXISTS announcement_status_publish_idx
    ON engagement."Announcement" ("status", "publishAt" DESC);

CREATE INDEX IF NOT EXISTS announcement_category_idx
    ON engagement."Announcement" ("categoryId", "createdAt" DESC);

CREATE INDEX IF NOT EXISTS announcement_featured_idx
    ON engagement."Announcement" ("featuredOrder")
    WHERE "featuredOrder" IS NOT NULL;

-- 8. Seed / Update 18 Rich Academic Articles Matching the 18 Photos

-- Article 1: Semiconductor Cleanroom Lab
INSERT INTO engagement."Announcement" (
    "id", "title", "slug", "summary", "content", "priority", "status", "categoryId",
    "coverImageUrl", "readingTimeMinutes", "viewCount", "uniqueReaderCount", "featuredOrder",
    "isGlobal", "publishAt", "publishedBy", "createdAt", "updatedAt", "version"
) VALUES (
    'announcement-semiconductor-cleanroom',
    'Khánh thành Phòng thí nghiệm Bán dẫn & Vi mạch Cleanroom chuẩn quốc tế tại HCM-UTE',
    'khanh-thanh-phong-thi-nghiem-ban-dan-vi-mach-cleanroom',
    'Nhà trường chính thức đưa vào vận hành phòng sạch Class 1000 phục vụ đào tạo và nghiên cứu chế tạo vi mạch bán dẫn tiên tiến.',
    '<p>Trường Đại học Sư phạm Kỹ thuật TP.HCM long trọng tổ chức Lễ khánh thành Phòng thí nghiệm Bán dẫn & Thiết kế Vi mạch (Semiconductor & Cleanroom Lab) đạt tiêu chuẩn phòng sạch Class 1000 tại Khu Công nghệ cao Cơ sở 1.</p>
<figure class="my-5 overflow-hidden rounded-xl border border-border/70 bg-secondary/15 p-2">
  <img src="/images/news/semiconductor-cleanroom.jpg" alt="Phòng Thí nghiệm Bán dẫn & Vi mạch Cleanroom HCM-UTE" class="w-full h-auto rounded-lg object-cover" />
  <figcaption class="pt-2 text-center text-xs font-medium text-muted-foreground italic">
    Toàn cảnh không gian phòng sạch Class 1000 và hệ thống thiết bị kiểm thử wafer vi mạch bán dẫn tại HCM-UTE
  </figcaption>
</figure>
<p>Công trình trọng điểm này khẳng định vị thế tiên phong của HCM-UTE trong việc đón đầu làn sóng dịch chuyển chuỗi cung ứng công nghệ vi mạch và công nghiệp bán dẫn toàn cầu:</p>
<h2>1. Hạ tầng kỹ thuật tiêu chuẩn quốc tế</h2>
<p>Hệ thống phòng sạch được trang bị buồng đệm áp suất không khí (Air shower), bộ lọc khí hạt siêu mịn HEPA/ULPA đảm bảo kiểm soát nồng độ bụi dưới 1.000 hạt/foot khối. Dây chuyền bao gồm thiết bị quang khắc UV Photolithography, lò ủ chân không cao tần, trạm kiểm thử wafer và kính hiển vi điện tử quét (SEM).</p>
<h2>2. Chương trình liên kết doanh nghiệp và học bổng tài năng</h2>
<p>Dự án nhận được sự đồng hành chiến lược từ các tập đoàn công nghệ bán dẫn đa quốc gia như Intel Products Vietnam, Synopsys, Marvell Technology và FPT Semiconductor. Toàn bộ sinh viên xuất sắc chuyên ngành Vi mạch sẽ được cấp học bổng 100% học phí cùng cơ hội thực tập trực tiếp tại các trung tâm R&D hàng đầu.</p>',
    'HIGH', 'PUBLISHED', 'cat-research-tech',
    '/images/news/semiconductor-cleanroom.jpg', 4, 1850, 1420, 1,
    TRUE, '2026-09-10T08:00:00Z', 'Ban Giám hiệu & Khoa Điện - Điện tử', NOW(), NOW(), 0
) ON CONFLICT ("id") DO UPDATE SET
    "title" = EXCLUDED."title",
    "slug" = EXCLUDED."slug",
    "summary" = EXCLUDED."summary",
    "content" = EXCLUDED."content",
    "categoryId" = EXCLUDED."categoryId",
    "coverImageUrl" = EXCLUDED."coverImageUrl",
    "readingTimeMinutes" = EXCLUDED."readingTimeMinutes",
    "featuredOrder" = EXCLUDED."featuredOrder";

-- Article 2: Smart Robotics & IoT Lab
INSERT INTO engagement."Announcement" (
    "id", "title", "slug", "summary", "content", "priority", "status", "categoryId",
    "coverImageUrl", "readingTimeMinutes", "viewCount", "uniqueReaderCount", "featuredOrder",
    "isGlobal", "publishAt", "publishedBy", "createdAt", "updatedAt", "version"
) VALUES (
    'announcement-robotics-iot-lab',
    'Khởi động Trung tâm Đổi mới Sáng tạo Robotics & IoT Công nghiệp Thông minh',
    'khoi-dong-trung-tam-doi-moi-robotics-iot-thong-minh',
    'Đầu tư cánh tay robot công nghiệp 6 bậc tự do KUKA cùng hệ sinh thái IoT công nghiệp phục vụ đào tạo kỹ sư tự động hóa.',
    '<p>Khoa Cơ khí Chế tạo máy phối hợp cùng Khoa Điện - Điện tử khánh thành Trung tâm Đổi mới Sáng tạo Robotics & IoT Công nghiệp Thông minh tại xưởng thực hành A2.</p>
<figure class="my-5 overflow-hidden rounded-xl border border-border/70 bg-secondary/15 p-2">
  <img src="/images/news/robotics-iot-lab.jpg" alt="Sinh viên thực hành tại Trung tâm Robotics & IoT HCM-UTE" class="w-full h-auto rounded-lg object-cover" />
  <figcaption class="pt-2 text-center text-xs font-medium text-muted-foreground italic">
    Sinh viên kỹ thuật HCM-UTE hiệu chỉnh cánh tay robot KUKA và lập trình cảm biến IoT công nghiệp
  </figcaption>
</figure>
<p>Trung tâm mở ra cơ hội tiếp cận công nghệ tự động hóa cấp cao cho hơn 2.500 sinh viên ngành Kỹ thuật Robot, Cơ điện tử và Tự động hóa:</p>
<h2>1. Năng lực thiết bị và công nghệ hiện đại</h2>
<p>Xưởng được trang bị 6 trạm thao tác robot cánh tay 6 bậc tự do KUKA, băng chuyền phân loại tự động sử dụng thị giác máy tính AI (Computer Vision) và hệ thống mạng truyền thông công nghiệp PROFINET/EtherCAT.</p>
<h2>2. Khóa học cấp chứng chỉ quốc tế</h2>
<p>Sinh viên hoàn thành học phần ứng dụng tại lab sẽ được dự thi lấy chứng chỉ vận hành robot công nghiệp tiêu chuẩn châu Âu, đáp ứng ngay yêu cầu làm việc tại các nhà máy thông minh (Smart Factory).</p>',
    'HIGH', 'PUBLISHED', 'cat-research-tech',
    '/images/news/robotics-iot-lab.jpg', 3, 1620, 1180, 2,
    TRUE, '2026-09-08T09:00:00Z', 'Khoa Cơ khí Chế tạo máy & Phòng KHCN', NOW(), NOW(), 0
) ON CONFLICT ("id") DO UPDATE SET
    "title" = EXCLUDED."title",
    "slug" = EXCLUDED."slug",
    "summary" = EXCLUDED."summary",
    "content" = EXCLUDED."content",
    "categoryId" = EXCLUDED."categoryId",
    "coverImageUrl" = EXCLUDED."coverImageUrl",
    "readingTimeMinutes" = EXCLUDED."readingTimeMinutes",
    "featuredOrder" = EXCLUDED."featuredOrder";

-- Article 3: Green Summer Volunteer Campaign
INSERT INTO engagement."Announcement" (
    "id", "title", "slug", "summary", "content", "priority", "status", "categoryId",
    "coverImageUrl", "readingTimeMinutes", "viewCount", "uniqueReaderCount",
    "isGlobal", "publishAt", "publishedBy", "createdAt", "updatedAt", "version"
) VALUES (
    'announcement-green-summer-volunteer',
    'Lễ ra quân Chiến dịch Mùa hè xanh: Tuổi trẻ UTE chung sức xây dựng nông thôn mới',
    'le-ra-quan-chien-dich-mua-he-xanh-tuoi-tre-ute',
    'Hơn 800 chiến sĩ tình nguyện UTE mang sức trẻ, tri thức kỹ thuật đến các vùng quê Đồng bằng Sông Cửu Long.',
    '<p>Đoàn Thanh niên - Hội Sinh viên Trường Đại học Sư phạm Kỹ thuật TP.HCM long trọng tổ chức Lễ ra quân Chiến dịch tình nguyện Mùa hè xanh năm 2026 với chủ đề "Chiến sĩ tình nguyện UTE - Tiên phong, bản lĩnh, xung kích vì cộng đồng".</p>
<figure class="my-5 overflow-hidden rounded-xl border border-border/70 bg-secondary/15 p-2">
  <img src="/images/news/green-summer-volunteer.jpg" alt="Chiến sĩ Mùa hè xanh UTE thi công đường bê tông nông thôn" class="w-full h-auto rounded-lg object-cover" />
  <figcaption class="pt-2 text-center text-xs font-medium text-muted-foreground italic">
    Nụ cười rạng rỡ của sinh viên tình nguyện UTE và bà con nhân dân trong ngày đổ bê tông tuyến đường nông thôn mới
  </figcaption>
</figure>
<p>Chiến dịch năm nay triển khai trên địa bàn 5 tỉnh Tây Nam Bộ với các công trình thanh niên trọng điểm mang hàm lượng chuyên môn cao:</p>
<h2>1. Công trình dân sinh và chuyển giao kỹ thuật</h2>
<p>Các đội hình chuyên môn đảm nhận thi công 15km đường giao thông nông thôn bê tông hóa, xây dựng 8 cây cầu nông thôn, lắp đặt 350 bộ đèn năng lượng mặt trời "Thắp sáng đường quê" và sửa chữa hệ thống điện sinh hoạt an toàn cho hơn 600 hộ gia đình chính sách.</p>
<h2>2. Chuyển đổi số cộng đồng và sinh hoạt hè cho thiếu nhi</h2>
<p>Đội hình Trí thức trẻ tổ chức các lớp phổ cập tin học, hướng dẫn người dân sử dụng dịch vụ công trực tuyến VNeID và tổ chức các sân chơi khoa học vui STEM cho hơn 3.000 em học sinh tiểu học địa phương.</p>',
    'NORMAL', 'PUBLISHED', 'cat-student-life',
    '/images/news/green-summer-volunteer.jpg', 3, 2150, 1890,
    TRUE, '2026-09-05T07:30:00Z', 'Đoàn Thanh niên & Hội Sinh viên UTE', NOW(), NOW(), 0
) ON CONFLICT ("id") DO UPDATE SET
    "title" = EXCLUDED."title",
    "slug" = EXCLUDED."slug",
    "summary" = EXCLUDED."summary",
    "content" = EXCLUDED."content",
    "categoryId" = EXCLUDED."categoryId",
    "coverImageUrl" = EXCLUDED."coverImageUrl",
    "readingTimeMinutes" = EXCLUDED."readingTimeMinutes";

-- Article 4: Campus Sports Championship
INSERT INTO engagement."Announcement" (
    "id", "title", "slug", "summary", "content", "priority", "status", "categoryId",
    "coverImageUrl", "readingTimeMinutes", "viewCount", "uniqueReaderCount",
    "isGlobal", "publishAt", "publishedBy", "createdAt", "updatedAt", "version"
) VALUES (
    'announcement-campus-sports-cup',
    'Khai mạc Giải Bóng đá Sinh viên UTE Champions Cup: Cuộc tranh tài đỉnh cao của 24 đội bóng',
    'khai-mac-giai-bong-da-ute-champions-cup-2026',
    'Không khí rực lửa tại sân vận động thể thao đa năng HCM-UTE với sự tham dự của hàng nghìn cổ động viên.',
    '<p>Tại Sân vận động Khu liên hợp Thể thao Trung tâm, Trung tâm Thể dục Thể thao long trọng tổ chức Lễ khai mạc Giải Bóng đá Sinh viên truyền thống UTE Champions Cup 2026.</p>
<figure class="my-5 overflow-hidden rounded-xl border border-border/70 bg-secondary/15 p-2">
  <img src="/images/news/campus-sports-cup.jpg" alt="Pha tranh bóng quyết liệt tại UTE Champions Cup" class="w-full h-auto rounded-lg object-cover" />
  <figcaption class="pt-2 text-center text-xs font-medium text-muted-foreground italic">
    Trận cầu nảy lửa giữa Đội tuyển Khoa Cơ khí và Khoa Công nghệ Thông tin dưới ánh đèn sân vận động rực sáng
  </figcaption>
</figure>
<p>Giải đấu quy tụ 24 đội bóng xuất sắc nhất đại diện cho các Khoa, Viện đào tạo và Khối sinh viên quốc tế:</p>
<h2>1. Thể thức thi đấu và tinh thần thể thao cao thượng</h2>
<p>Các đội thi đấu vòng bảng theo thể thức vòng tròn tính điểm để chọn ra 8 đội mạnh nhất vào vòng knock-out tứ kết, bán kết và chung kết. Giải đấu áp dụng luật thi đấu sân 11 người của FIFA dưới sự điều hành của tổ trọng tài Liên đoàn Bóng đá TP.HCM.</p>
<h2>2. Giải thưởng hấp dẫn và cơ hội tuyển chọn đội tuyển trường</h2>
<p>Đội Vô địch sẽ nhận Cúp vàng truyền thống UTE, cờ lưu niệm và phần thưởng trị giá 30 triệu đồng cùng học bổng rèn luyện thể chất. Các cầu thủ xuất sắc nhất sẽ được tuyển chọn vào Đội tuyển Sinh viên UTE tham dự Giải Vô địch Sinh viên Toàn quốc.</p>',
    'NORMAL', 'PUBLISHED', 'cat-student-life',
    '/images/news/campus-sports-cup.jpg', 3, 1940, 1510,
    TRUE, '2026-09-04T15:00:00Z', 'Trung tâm GDTC & Hội Sinh viên UTE', NOW(), NOW(), 0
) ON CONFLICT ("id") DO UPDATE SET
    "title" = EXCLUDED."title",
    "slug" = EXCLUDED."slug",
    "summary" = EXCLUDED."summary",
    "content" = EXCLUDED."content",
    "categoryId" = EXCLUDED."categoryId",
    "coverImageUrl" = EXCLUDED."coverImageUrl",
    "readingTimeMinutes" = EXCLUDED."readingTimeMinutes";

-- Article 5: Cultural Arts Gala 20/11
INSERT INTO engagement."Announcement" (
    "id", "title", "slug", "summary", "content", "priority", "status", "categoryId",
    "coverImageUrl", "readingTimeMinutes", "viewCount", "uniqueReaderCount",
    "isGlobal", "publishAt", "publishedBy", "createdAt", "updatedAt", "version"
) VALUES (
    'announcement-cultural-arts-gala',
    'Hội diễn Nghệ thuật Tri ân Ngày Nhà giáo Việt Nam 20/11: Khúc ca ngợi người đưa đò thầm lặng',
    'hoi-dien-nghe-thuat-tri-an-ngay-nha-giao-viet-nam-20-11',
    'Đêm nhạc hội hoành tráng tại Hội trường Lớn Khu A với sự tham gia của các nghệ sĩ sinh viên và giảng viên toàn trường.',
    '<p>Nhân kỷ niệm Ngày Nhà giáo Việt Nam 20/11, Công đoàn và Đoàn Thanh niên Trường Đại học Sư phạm Kỹ thuật TP.HCM tổ chức Đêm nhạc hội Tri ân Thầy Cô với chủ đề "Nâng cánh ước mơ - Sáng mãi lửa tri thức" tại Hội trường Lớn Khu A.</p>
<figure class="my-5 overflow-hidden rounded-xl border border-border/70 bg-secondary/15 p-2">
  <img src="/images/news/cultural-arts-gala.jpg" alt="Tiết mục múa hoa sen truyền thống tại Hội diễn 20/11" class="w-full h-auto rounded-lg object-cover" />
  <figcaption class="pt-2 text-center text-xs font-medium text-muted-foreground italic">
    Tiết mục múa hoa sen truyền thống trong trang phục Áo dài thướt tha ngợi ca công ơn Thầy Cô tại Hội trường Lớn HCM-UTE
  </figcaption>
</figure>
<p>Đêm diễn mang lại những xúc cảm sâu lắng, là lời tri ân chân thành nhất của bao thế hệ sinh viên gửi tới các Thầy, Cô giáo:</p>
<h2>1. Sắc màu nghệ thuật đa dạng và công phu</h2>
<p>Chương trình gồm 20 tiết mục múa dân gian đương đại, hợp xướng, độc tấu nhạc cụ dân tộc và biểu diễn ban nhạc acoustic trẻ trung do chính các thầy cô giáo và sinh viên dàn dựng công phu.</p>
<h2>2. Tuyên dương Giảng viên Ưu tú và trao quỹ hỗ trợ học tập</h2>
<p>Ban Giám hiệu Nhà trường đã trao tặng hoa và biểu trưng tri ân cho 50 Nhà giáo Tiêu biểu có nhiều đóng góp xuất sắc trong sự nghiệp trồng người và nghiên cứu khoa học của Nhà trường.</p>',
    'NORMAL', 'PUBLISHED', 'cat-culture-arts',
    '/images/news/cultural-arts-gala.jpg', 3, 2300, 1980,
    TRUE, '2026-09-02T19:00:00Z', 'Công đoàn Trường & Đoàn Thanh niên UTE', NOW(), NOW(), 0
) ON CONFLICT ("id") DO UPDATE SET
    "title" = EXCLUDED."title",
    "slug" = EXCLUDED."slug",
    "summary" = EXCLUDED."summary",
    "content" = EXCLUDED."content",
    "categoryId" = EXCLUDED."categoryId",
    "coverImageUrl" = EXCLUDED."coverImageUrl",
    "readingTimeMinutes" = EXCLUDED."readingTimeMinutes";

-- Article 6: IEEE STEM Academic Conference
INSERT INTO engagement."Announcement" (
    "id", "title", "slug", "summary", "content", "priority", "status", "categoryId",
    "coverImageUrl", "readingTimeMinutes", "viewCount", "uniqueReaderCount",
    "isGlobal", "publishAt", "publishedBy", "createdAt", "updatedAt", "version"
) VALUES (
    'announcement-stem-conference-keynote',
    'Hội nghị Khoa học Quốc tế IEEE về Kỹ thuật Tiên tiến & Trí tuệ Nhân tạo tại HCM-UTE',
    'hoi-nghi-khoa-hoc-quoc-te-ieee-stem-ai-hcm-ute',
    'Quy tụ hơn 300 học giả, giáo sư đầu ngành từ 18 quốc gia thảo luận về đột phá AI, học máy và hệ thống nhúng thông minh.',
    '<p>Trường Đại học Sư phạm Kỹ thuật TP.HCM phối hợp cùng Hiệp hội Kỹ sư Điện và Điện tử Quốc tế (IEEE) đăng cai tổ chức Hội nghị Khoa học Quốc tế IEEE on Advanced Engineering & AI Systems tại Trung tâm Hội nghị Quốc tế Nhà trường.</p>
<figure class="my-5 overflow-hidden rounded-xl border border-border/70 bg-secondary/15 p-2">
  <img src="/images/news/stem-conference-keynote.jpg" alt="Diễn giả báo cáo tại Hội nghị Quốc tế IEEE HCM-UTE" class="w-full h-auto rounded-lg object-cover" />
  <figcaption class="pt-2 text-center text-xs font-medium text-muted-foreground italic">
    GS.TS Nguyễn Văn Minh - Trưởng nhóm nghiên cứu AI trình bày báo cáo chuyên đề về Tối ưu hóa Mạng nơ-ron sâu tại Hội thảo IEEE
  </figcaption>
</figure>
<p>Hội nghị là diễn đàn khoa học uy tín quy mô toàn cầu nhằm công bố và thảo luận các kết quả nghiên cứu mũi nhọn:</p>
<h2>1. Các phiên tiểu ban chuyên sâu</h2>
<p>Hội đồng khoa học đã thẩm định và chọn lọc 145 bài báo cáo xuất sắc trong tổng số hơn 400 bài gửi về từ các trường đại học tại Mỹ, Nhật Bản, Hàn Quốc, Đức và Singapore để đăng kỷ yếu IEEE Xplore.</p>
<h2>2. Thúc đẩy hợp tác nghiên cứu đa quốc gia</h2>
<p>Hội nghị mở ra 6 biên bản ghi nhớ hợp tác trao đổi nghiên cứu sinh và liên kết phòng thí nghiệm trọng điểm song phương giữa HCM-UTE và các trường đại học đối tác quốc tế.</p>',
    'HIGH', 'PUBLISHED', 'cat-research-tech',
    '/images/news/stem-conference-keynote.jpg', 4, 1780, 1340,
    TRUE, '2026-09-01T08:30:00Z', 'Phòng Khoa học Công nghệ & Quan hệ Quốc tế', NOW(), NOW(), 0
) ON CONFLICT ("id") DO UPDATE SET
    "title" = EXCLUDED."title",
    "slug" = EXCLUDED."slug",
    "summary" = EXCLUDED."summary",
    "content" = EXCLUDED."content",
    "categoryId" = EXCLUDED."categoryId",
    "coverImageUrl" = EXCLUDED."coverImageUrl",
    "readingTimeMinutes" = EXCLUDED."readingTimeMinutes";

-- Article 7: AI Hackathon Arena
INSERT INTO engagement."Announcement" (
    "id", "title", "slug", "summary", "content", "priority", "status", "categoryId",
    "coverImageUrl", "readingTimeMinutes", "viewCount", "uniqueReaderCount",
    "isGlobal", "publishAt", "publishedBy", "createdAt", "updatedAt", "version"
) VALUES (
    'announcement-ai-hackathon-arena',
    'Khởi tranh Đấu trường Đổi mới Sáng tạo AI Hackathon 48 Giờ Liên tục: Chinh phục mô hình lớn',
    'khoi-tranh-dau-truong-ai-hackathon-48h-lien-tuc',
    '50 đội thi tranh tài không ngủ phát triển ứng dụng trí tuệ nhân tạo giải quyết bài toán giao thông thông minh và y tế số.',
    '<p>Khoa Công nghệ Thông tin phối hợp cùng Vườn ươm Khởi nghiệp Đổi mới Sáng tạo chính thức phát lệnh xuất phát Đấu trường AI Hackathon 2026 kéo dài 48 giờ liên tục tại Không gian Sáng tạo Innovation Hub.</p>
<figure class="my-5 overflow-hidden rounded-xl border border-border/70 bg-secondary/15 p-2">
  <img src="/images/news/ai-hackathon-arena.jpg" alt="Không khí làm việc xuyên đêm tại HCM-UTE AI Hackathon" class="w-full h-auto rounded-lg object-cover" />
  <figcaption class="pt-2 text-center text-xs font-medium text-muted-foreground italic">
    Các kỹ sư phần mềm trẻ miệt mài gõ mã nguồn và thiết kế kiến trúc đường ống xử lý dữ liệu trong đêm chung kết AI Hackathon
  </figcaption>
</figure>
<p>Cuộc thi là sân chơi đỉnh cao thử thách sức bền, tư duy thuật toán và kỹ năng hiện thực hóa ý tưởng của sinh viên công nghệ:</p>
<h2>1. Đề bài thách thức và tài nguyên hạ tầng điện toán đám mây</h2>
<p>Mỗi đội thi được cấp quyền truy cập máy chủ GPU chuyên dụng và kho dữ liệu thực tế ẩn danh để xây dựng mô hình AI dự báo kẹt xe thời gian thực hoặc trợ lý AI phân tích ảnh X-quang phổi.</p>
<h2>2. Ban giám khảo chuyên môn và quỹ giải thưởng 150 triệu đồng</h2>
<p>Đội Quán quân sẽ nhận giải thưởng 50 triệu đồng tiền mặt và gói tài trợ ươm tạo doanh nghiệp khởi nghiệp trị giá 100 triệu đồng từ Quỹ Đổi mới Sáng tạo Quốc gia.</p>',
    'NORMAL', 'PUBLISHED', 'cat-research-tech',
    '/images/news/ai-hackathon-arena.jpg', 3, 2480, 2050,
    TRUE, '2026-08-28T18:00:00Z', 'Khoa Công nghệ Thông tin & Innovation Hub', NOW(), NOW(), 0
) ON CONFLICT ("id") DO UPDATE SET
    "title" = EXCLUDED."title",
    "slug" = EXCLUDED."slug",
    "summary" = EXCLUDED."summary",
    "content" = EXCLUDED."content",
    "categoryId" = EXCLUDED."categoryId",
    "coverImageUrl" = EXCLUDED."coverImageUrl",
    "readingTimeMinutes" = EXCLUDED."readingTimeMinutes";

-- Article 8: Digital Library Commons
INSERT INTO engagement."Announcement" (
    "id", "title", "slug", "summary", "content", "priority", "status", "categoryId",
    "coverImageUrl", "readingTimeMinutes", "viewCount", "uniqueReaderCount",
    "isGlobal", "publishAt", "publishedBy", "createdAt", "updatedAt", "version"
) VALUES (
    'announcement-digital-library-hub',
    'Mở rộng Không gian Học tập Đa năng & Dịch vụ Thư viện Số 24/7 tại Tòa nhà Trung tâm',
    'mo-rong-khong-gian-hoc-tap-thu-vien-so-24-7',
    'Khu tổ hợp học tập hiện đại 6 tầng với kho học liệu điện tử 1 triệu đầu sách phục vụ sinh viên nghiên cứu học tập.',
    '<p>Ban Quản lý Thư viện Trung tâm HCM-UTE trân trọng thông báo hoàn tất nâng cấp và đưa vào sử dụng Không gian Học tập Đa năng (Learning Commons) thế hệ mới tại Tòa nhà Thư viện 6 tầng.</p>
<figure class="my-5 overflow-hidden rounded-xl border border-border/70 bg-secondary/15 p-2">
  <img src="/images/news/campus-digital-library.jpg" alt="Không gian Thư viện Số hiện đại tại HCM-UTE" class="w-full h-auto rounded-lg object-cover" />
  <figcaption class="pt-2 text-center text-xs font-medium text-muted-foreground italic">
    Không gian học tập mở tràn ngập ánh sáng tự nhiên và trạm tra cứu tài liệu số thông minh tại Thư viện Trung tâm HCM-UTE
  </figcaption>
</figure>
<p>Công trình mang đến môi trường học tập học thuật đẳng cấp quốc tế, thân thiện và giàu cảm hứng sáng tạo:</p>
<h2>1. Phân khu chức năng thông minh</h2>
<p>Thư viện bao gồm 30 phòng thảo luận nhóm cách âm bằng kính, khu vực đọc sách yên tĩnh chuyên sâu, khu cà phê học thuật và phòng lab máy tính cấu hình cao phục vụ phân tích dữ liệu nghiên cứu.</p>
<h2>2. Hệ thống thư viện điện tử kết nối liên trường</h2>
<p>Sinh viên sử dụng tài khoản CampusUTE để truy cập miễn phí toàn văn hàng triệu bài báo khoa học từ các cơ sở dữ liệu hàng đầu thế giới như ScienceDirect, IEEE Xplore, SpringerLink và thư viện luận văn tốt nghiệp số hóa của trường.</p>',
    'NORMAL', 'PUBLISHED', 'cat-academic-affairs',
    '/images/news/campus-digital-library.jpg', 3, 2190, 1870,
    TRUE, '2026-08-25T08:00:00Z', 'Ban Giám đốc Thư viện Trung tâm', NOW(), NOW(), 0
) ON CONFLICT ("id") DO UPDATE SET
    "title" = EXCLUDED."title",
    "slug" = EXCLUDED."slug",
    "summary" = EXCLUDED."summary",
    "content" = EXCLUDED."content",
    "categoryId" = EXCLUDED."categoryId",
    "coverImageUrl" = EXCLUDED."coverImageUrl",
    "readingTimeMinutes" = EXCLUDED."readingTimeMinutes";

-- Article 9: Commencement Graduation Ceremony
INSERT INTO engagement."Announcement" (
    "id", "title", "slug", "summary", "content", "priority", "status", "categoryId",
    "coverImageUrl", "readingTimeMinutes", "viewCount", "uniqueReaderCount",
    "isGlobal", "publishAt", "publishedBy", "createdAt", "updatedAt", "version"
) VALUES (
    'announcement-commencement-graduation',
    'Lễ Trao bằng Tốt nghiệp Tân Kỹ sư & Cử nhân: Tự hào thế hệ nhân tài vững bước tương lai',
    'le-trao-bang-tot-nghiep-tan-ky-su-cu-nhan-hcm-ute',
    'Hơn 2.800 tân khoa rạng rỡ đón nhận bằng tốt nghiệp đại học trong niềm hân hoan của gia đình và thầy cô.',
    '<p>Tại Quảng trường Trung tâm và Hội trường Lớn, Trường Đại học Sư phạm Kỹ thuật TP.HCM long trọng tổ chức Lễ Bế giảng và Trao bằng Tốt nghiệp Đại học chính quy đợt 2 năm 2026.</p>
<figure class="my-5 overflow-hidden rounded-xl border border-border/70 bg-secondary/15 p-2">
  <img src="/images/news/commencement-graduation.jpg" alt="Khoảnh khắc tung mũ tốt nghiệp rạng rỡ của tân khoa HCM-UTE" class="w-full h-auto rounded-lg object-cover" />
  <figcaption class="pt-2 text-center text-xs font-medium text-muted-foreground italic">
    Khoảnh khắc tung mũ cử nhân rạng rỡ niềm tự hào và khát vọng cống hiến của các tân kỹ sư HCM-UTE trước sảnh chính Tòa nhà Trung tâm
  </figcaption>
</figure>
<p>Lễ tốt nghiệp đánh dấu cột mốc trưởng thành quan trọng sau 4 năm tôi rèn tri thức và kỹ năng thực hành nghề nghiệp:</p>
<h2>1. Thành tích xuất sắc và tỷ lệ có việc làm ấn tượng</h2>
<p>Đợt tốt nghiệp ghi nhận tỷ lệ sinh viên tốt nghiệp loại Xuất sắc và Giỏi đạt 28,4%. Đặc biệt, theo khảo sát độc lập của Phòng Quan hệ Doanh nghiệp, hơn 96% tân kỹ sư đã có việc làm chính thức hoặc nhận được thư mời tuyển dụng từ trước ngày nhận bằng.</p>
<h2>2. Vinh danh Thủ khoa toàn trường</h2>
<p>Hiệu trưởng Nhà trường đã trực tiếp trao bằng khen, kỷ niệm chương và học bổng sau đại học cho 12 Thủ khoa xuất sắc của các ngành đào tạo.</p>',
    'HIGH', 'PUBLISHED', 'cat-academic-affairs',
    '/images/news/commencement-graduation.jpg', 3, 3120, 2680,
    TRUE, '2026-08-22T08:00:00Z', 'Phòng Đào tạo & Ban Giám hiệu', NOW(), NOW(), 0
) ON CONFLICT ("id") DO UPDATE SET
    "title" = EXCLUDED."title",
    "slug" = EXCLUDED."slug",
    "summary" = EXCLUDED."summary",
    "content" = EXCLUDED."content",
    "categoryId" = EXCLUDED."categoryId",
    "coverImageUrl" = EXCLUDED."coverImageUrl",
    "readingTimeMinutes" = EXCLUDED."readingTimeMinutes";

-- Article 10: Blood Donation Campaign
INSERT INTO engagement."Announcement" (
    "id", "title", "slug", "summary", "content", "priority", "status", "categoryId",
    "coverImageUrl", "readingTimeMinutes", "viewCount", "uniqueReaderCount",
    "isGlobal", "publishAt", "publishedBy", "createdAt", "updatedAt", "version"
) VALUES (
    'announcement-blood-donation-day',
    'Ngày hội Hiến máu Tình nguyện "Giọt hồng Công nghệ" Lần thứ VI: Trao hy vọng, sẻ chia sự sống',
    'ngay-hoi-hien-mau-tinh-nguyen-giot-hong-cong-nghe',
    'Tiếp nhận hơn 650 đơn vị máu quý giá phục vụ cấp cứu và điều trị người bệnh tại các bệnh viện tuyến đầu.',
    '<p>Hội Chữ thập đỏ phối hợp cùng Đoàn Thanh niên Trường Đại học Sư phạm Kỹ thuật TP.HCM tổ chức Ngày hội Hiến máu Tình nguyện "Giọt hồng Công nghệ" lần thứ VI năm 2026 với thông điệp "Một giọt máu cho đi - Một cuộc đời ở lại".</p>
<figure class="my-5 overflow-hidden rounded-xl border border-border/70 bg-secondary/15 p-2">
  <img src="/images/news/blood-donation-day.jpg" alt="Sinh viên và y bác sĩ tại Ngày hội Hiến máu Giọt hồng Công nghệ" class="w-full h-auto rounded-lg object-cover" />
  <figcaption class="pt-2 text-center text-xs font-medium text-muted-foreground italic">
    Các y bác sĩ Bệnh viện Truyền máu Huyết học và tình nguyện viên UTE ân cần chăm sóc sinh viên hiến máu tình nguyện
  </figcaption>
</figure>
<p>Hoạt động nhân văn sâu sắc đã nhận được sự hưởng ứng nhiệt tình từ đông đảo cán bộ, giảng viên và sinh viên toàn trường:</p>
<h2>1. Quy trình tiếp nhận máu chuẩn y khoa và an toàn tuyệt đối</h2>
<p>Ban Tổ chức bố trí luồng tiếp nhận 1 chiều khép kín gồm: đo thân nhiệt, khám sàng lọc huyết áp, xét nghiệm nhanh nhóm máu, lấy máu vô trùng và khu vực nghỉ ngơi bồi dưỡng dinh dưỡng.</p>
<h2>2. Quyền lợi và ghi nhận rèn luyện cho sinh viên hiến máu</h2>
<p>Mỗi người tham gia hiến máu được nhận Giấy chứng nhận hiến máu tình nguyện của Ban Chỉ đạo Vận động Hiến máu Quốc gia, quà tặng bồi dưỡng sức khỏe và được cộng 10 điểm rèn luyện sinh viên theo quy định học vụ.</p>',
    'NORMAL', 'PUBLISHED', 'cat-student-life',
    '/images/news/blood-donation-day.jpg', 3, 1580, 1310,
    TRUE, '2026-08-18T07:30:00Z', 'Hội Chữ thập đỏ & Đoàn Thanh niên UTE', NOW(), NOW(), 0
) ON CONFLICT ("id") DO UPDATE SET
    "title" = EXCLUDED."title",
    "slug" = EXCLUDED."slug",
    "summary" = EXCLUDED."summary",
    "content" = EXCLUDED."content",
    "categoryId" = EXCLUDED."categoryId",
    "coverImageUrl" = EXCLUDED."coverImageUrl",
    "readingTimeMinutes" = EXCLUDED."readingTimeMinutes";

-- Article 11: Student Dormitory Campus
INSERT INTO engagement."Announcement" (
    "id", "title", "slug", "summary", "content", "priority", "status", "categoryId",
    "coverImageUrl", "readingTimeMinutes", "viewCount", "uniqueReaderCount",
    "isGlobal", "publishAt", "publishedBy", "createdAt", "updatedAt", "version"
) VALUES (
    'announcement-student-dormitory-campus',
    'Nâng cấp Không gian Sống Xanh & Tiện ích Thể thao tại Ký túc xá Sinh viên HCM-UTE',
    'nang-cap-khong-gian-song-xanh-ktx-sinh-vien-hcm-ute',
    'Đưa vào sử dụng khu công viên trung tâm rợp bóng mát, hệ thống năng lượng mặt trời và phòng sinh hoạt cộng đồng.',
    '<p>Ban Quản lý Ký túc xá công bố hoàn tất dự án "KTX Xanh - An toàn - Văn minh" nhằm nâng cao toàn diện chất lượng đời sống nội trú cho hơn 3.200 sinh viên nội trú tại cơ sở chính.</p>
<figure class="my-5 overflow-hidden rounded-xl border border-border/70 bg-secondary/15 p-2">
  <img src="/images/news/student-dormitory-campus.jpg" alt="Khuôn viên xanh rợp bóng cây tại KTX HCM-UTE" class="w-full h-auto rounded-lg object-cover" />
  <figcaption class="pt-2 text-center text-xs font-medium text-muted-foreground italic">
    Khuôn viên xanh mát và các tòa nhà nội trú KTX HCM-UTE tràn ngập ánh nắng buổi chiều an lành
  </figcaption>
</figure>
<p>Khuôn viên KTX mới được cải tạo theo hướng đại học xanh bền vững, thân thiện với môi trường:</p>
<h2>1. Tiện ích nội trú hiện đại và an ninh 24/7</h2>
<p>Các tòa nhà KTX được lắp đặt hệ thống pin năng lượng mặt trời áp mái cung cấp 40% điện năng chiếu sáng công cộng, hệ thống cửa kiểm soát ra vào vân tay/thẻ từ thông minh và trạm lọc nước uống tinh khiết đạt chuẩn y tế tại mỗi tầng.</p>
<h2>2. Sân chơi thể thao và câu lạc bộ rèn luyện kỹ năng</h2>
<p>Sân bóng rổ, khu tập gym ngoài trời và phòng tự học nhóm máy lạnh mở cửa miễn phí phục vụ sinh viên nội trú sau giờ học tập căng thẳng trên giảng đường.</p>',
    'NORMAL', 'PUBLISHED', 'cat-student-life',
    '/images/news/student-dormitory-campus.jpg', 3, 1820, 1490,
    TRUE, '2026-08-15T09:00:00Z', 'Ban Quản lý Ký túc xá Sinh viên', NOW(), NOW(), 0
) ON CONFLICT ("id") DO UPDATE SET
    "title" = EXCLUDED."title",
    "slug" = EXCLUDED."slug",
    "summary" = EXCLUDED."summary",
    "content" = EXCLUDED."content",
    "categoryId" = EXCLUDED."categoryId",
    "coverImageUrl" = EXCLUDED."coverImageUrl",
    "readingTimeMinutes" = EXCLUDED."readingTimeMinutes";

-- Article 12: Faculty Excellence Awards
INSERT INTO engagement."Announcement" (
    "id", "title", "slug", "summary", "content", "priority", "status", "categoryId",
    "coverImageUrl", "readingTimeMinutes", "viewCount", "uniqueReaderCount",
    "isGlobal", "publishAt", "publishedBy", "createdAt", "updatedAt", "version"
) VALUES (
    'announcement-faculty-excellence-awards',
    'Lễ Tôn vinh Giảng viên Xuất sắc & Nhà Khoa học Tiêu biểu HCM-UTE Năm học 2025-2026',
    'le-ton-vinh-giang-vien-xuat-sac-nha-khoa-hoc-tieu-bieu',
    'Vinh danh các thầy cô giáo có thành tích vượt trội trong giảng dạy, nghiên cứu khoa học và công bố quốc tế uy tín.',
    '<p>Hội đồng Thi đua - Khen thưởng Trường Đại học Công nghệ Kỹ thuật TP.HCM trang trọng tổ chức Lễ Tôn vinh Giảng viên Xuất sắc và Nhà Khoa học Tiêu biểu năm học tại Hội trường Trung tâm.</p>
<figure class="my-5 overflow-hidden rounded-xl border border-border/70 bg-secondary/15 p-2">
  <img src="/images/news/faculty-excellence-awards.jpg" alt="Hiệu trưởng trao cúp vinh danh cho Giảng viên xuất sắc HCM-UTE" class="w-full h-auto rounded-lg object-cover" />
  <figcaption class="pt-2 text-center text-xs font-medium text-muted-foreground italic">
    Hiệu trưởng Nhà trường trao tặng Kỷ niệm chương pha lê và Bằng khen vinh danh Nhà khoa học nữ xuất sắc có nhiều công bố ISI/Scopus Q1
  </figcaption>
</figure>
<p>Buổi lễ là sự ghi nhận xứng đáng đối với tinh thần cống hiến không ngừng nghỉ của đội ngũ cán bộ giảng dạy và nghiên cứu:</p>
<h2>1. Những con số nghiên cứu khoa học ấn tượng</h2>
<p>Trong năm học qua, toàn trường đã công bố hơn 450 bài báo khoa học thuộc danh mục WoS/Scopus, đăng ký 18 bằng sáng chế và giải pháp hữu ích độc quyền cùng 35 đề tài nghiên cứu cấp Nhà nước và cấp Bộ.</p>
<h2>2. Khích lệ phong trào đổi mới phương pháp giảng dạy</h2>
<p>Nhà trường trao giải "Giảng viên Dạy giỏi Sáng tạo" cho 25 giảng viên áp dụng thành công phương pháp học tập kết hợp (Blended Learning) và giảng dạy thực hành định hướng chuyển đổi số.</p>',
    'HIGH', 'PUBLISHED', 'cat-awards-honors',
    '/images/news/faculty-excellence-awards.jpg', 3, 2740, 2210,
    TRUE, '2026-08-10T14:00:00Z', 'Phòng Tổ chức - Cán bộ & Ban Giám hiệu', NOW(), NOW(), 0
) ON CONFLICT ("id") DO UPDATE SET
    "title" = EXCLUDED."title",
    "slug" = EXCLUDED."slug",
    "summary" = EXCLUDED."summary",
    "content" = EXCLUDED."content",
    "categoryId" = EXCLUDED."categoryId",
    "coverImageUrl" = EXCLUDED."coverImageUrl",
    "readingTimeMinutes" = EXCLUDED."readingTimeMinutes";

-- Update existing articles with metadata, category linkage, and strictly unique slugs
UPDATE engagement."Announcement"
SET "categoryId" = 'cat-research-tech',
    "coverImageUrl" = '/images/news/bigdata-ai-lab.jpg',
    "summary" = 'Khánh thành cụm máy chủ điện toán hiệu năng cao GPU NVIDIA A100 và hệ thống lưu trữ Ceph 500TB phục vụ nghiên cứu Big Data và AI.',
    "readingTimeMinutes" = 4,
    "viewCount" = 3450,
    "uniqueReaderCount" = 2890
WHERE "id" IN ('announcement-ute-bigdata-ai-center', 'announcement-v32-smart-campus');
UPDATE engagement."Announcement" SET "slug" = 'khanh-thanh-cum-may-chu-hpc-bigdata-ai-lab' WHERE "id" = 'announcement-ute-bigdata-ai-center';
UPDATE engagement."Announcement" SET "slug" = 'khanh-thanh-cum-may-chu-hpc-smart-campus-v32' WHERE "id" = 'announcement-v32-smart-campus';

UPDATE engagement."Announcement"
SET "categoryId" = 'cat-career-opps',
    "coverImageUrl" = '/images/news/tech-career-expo.jpg',
    "summary" = 'Hơn 60 tập đoàn công nghệ tuyển dụng 1.200 vị trí kỹ sư phần mềm, AI, Cloud DevOps tại Ngày hội việc làm UTE Tech Career Expo 2026.',
    "readingTimeMinutes" = 3,
    "viewCount" = 4120,
    "uniqueReaderCount" = 3560
WHERE "id" IN ('announcement-ute-career-fair-tech-2026', 'announcement-v32-job-fair', 'announcement-ute-fpt-ojt-career-day');
UPDATE engagement."Announcement" SET "slug" = 'ngay-hoi-viec-lam-ute-tech-career-expo-2026' WHERE "id" = 'announcement-ute-career-fair-tech-2026';
UPDATE engagement."Announcement" SET "slug" = 'ngay-hoi-viec-lam-ute-job-fair-v32' WHERE "id" = 'announcement-v32-job-fair';
UPDATE engagement."Announcement" SET "slug" = 'ngay-hoi-fpt-ojt-career-day-2026' WHERE "id" = 'announcement-ute-fpt-ojt-career-day';

UPDATE engagement."Announcement"
SET "categoryId" = 'cat-awards-honors',
    "coverImageUrl" = '/images/news/scholarship-ceremony.jpg',
    "summary" = 'Thông báo kết quả đối soát GPA và điểm rèn luyện xét cấp Học bổng Khuyến khích học tập kỳ 1 năm học 2026-2027.',
    "readingTimeMinutes" = 3,
    "viewCount" = 5230,
    "uniqueReaderCount" = 4680
WHERE "id" IN ('announcement-ute-scholarship-dr-criteria', 'announcement-ute-samsung-scholarship-2026', 'announcement-ute-intel-stem-women-2026', 'announcement-v26-scholarship');
UPDATE engagement."Announcement" SET "slug" = 'ket-qua-xet-cap-hoc-bong-khuyen-khich-hoc-tap' WHERE "id" = 'announcement-ute-scholarship-dr-criteria';
UPDATE engagement."Announcement" SET "slug" = 'hoc-bong-tai-nang-samsung-innovation-campus-2026' WHERE "id" = 'announcement-ute-samsung-scholarship-2026';
UPDATE engagement."Announcement" SET "slug" = 'hoc-bong-intel-stem-women-leadership-2026' WHERE "id" = 'announcement-ute-intel-stem-women-2026';
UPDATE engagement."Announcement" SET "slug" = 'ket-qua-xet-cap-hoc-bong-khuyen-khich-v26' WHERE "id" = 'announcement-v26-scholarship';

UPDATE engagement."Announcement"
SET "categoryId" = 'cat-academic-affairs',
    "coverImageUrl" = '/images/news/course-registration.jpg',
    "summary" = 'Kế hoạch đăng ký học phần trực tuyến, phân luồng theo khóa và quy định trần hạn mức 28 tín chỉ trên cổng CampusUTE.',
    "readingTimeMinutes" = 4,
    "viewCount" = 6840,
    "uniqueReaderCount" = 5920
WHERE "id" IN ('announcement-ute-course-reg-official', 'announcement-registration-window');
UPDATE engagement."Announcement" SET "slug" = 'ke-hoach-dang-ky-hoc-phan-truc-tuyen-hoc-ky' WHERE "id" = 'announcement-ute-course-reg-official';
UPDATE engagement."Announcement" SET "slug" = 'thong-bao-mo-cong-dang-ky-hoc-phan-truc-tuyen' WHERE "id" = 'announcement-registration-window';

UPDATE engagement."Announcement"
SET "categoryId" = 'cat-academic-affairs',
    "coverImageUrl" = '/images/news/thesis-defense.jpg',
    "summary" = 'Khoa Công nghệ Thông tin tổ chức bảo vệ Khóa luận tốt nghiệp đợt 2 cho 120 nhóm sinh viên trước 18 hội đồng chuyên môn.',
    "readingTimeMinutes" = 4,
    "viewCount" = 2950,
    "uniqueReaderCount" = 2410
WHERE "id" IN ('announcement-ute-thesis-registration-fall', 'announcement-v26-thesis-round');
UPDATE engagement."Announcement" SET "slug" = 'le-bao-ve-khoa-luan-tot-nghiep-kltn-khoa-cntt' WHERE "id" = 'announcement-ute-thesis-registration-fall';
UPDATE engagement."Announcement" SET "slug" = 'le-bao-ve-khoa-luan-tot-nghiep-kltn-v26' WHERE "id" = 'announcement-v26-thesis-round';

UPDATE engagement."Announcement"
SET "categoryId" = 'cat-research-tech',
    "coverImageUrl" = '/images/news/scientific-research.jpg',
    "summary" = 'Lễ tổng kết và trao giải Nghiên cứu Khoa học Sinh viên UTE 2026: 5 Giải Nhất, 10 Giải Nhì và nhiều dự án đăng ký sáng chế.',
    "readingTimeMinutes" = 3,
    "viewCount" = 2670,
    "uniqueReaderCount" = 2190
WHERE "id" IN ('announcement-ute-student-scientific-research-awards', 'announcement-v32-ute-research', 'announcement-ute-innovation-awards-2026');
UPDATE engagement."Announcement" SET "slug" = 'le-trao-giai-nghien-cuu-khoa-hoc-sinh-vien-nckh' WHERE "id" = 'announcement-ute-student-scientific-research-awards';
UPDATE engagement."Announcement" SET "slug" = 'le-trao-giai-nghien-cuu-khoa-hoc-v32' WHERE "id" = 'announcement-v32-ute-research';
UPDATE engagement."Announcement" SET "slug" = 'giai-thuong-doi-moi-sang-tao-innovation-awards-2026' WHERE "id" = 'announcement-ute-innovation-awards-2026';

-- 9. Seed Tag Maps for Articles
INSERT INTO engagement."AnnouncementTagMap" ("announcementId", "tagId") VALUES
('announcement-semiconductor-cleanroom', 'tag-semiconductor'),
('announcement-semiconductor-cleanroom', 'tag-ieee'),
('announcement-robotics-iot-lab', 'tag-robotics'),
('announcement-robotics-iot-lab', 'tag-ai'),
('announcement-green-summer-volunteer', 'tag-mua-he-xanh'),
('announcement-campus-sports-cup', 'tag-champions-cup'),
('announcement-cultural-arts-gala', 'tag-teachers-day'),
('announcement-stem-conference-keynote', 'tag-ieee'),
('announcement-stem-conference-keynote', 'tag-ai'),
('announcement-ai-hackathon-arena', 'tag-hackathon'),
('announcement-ai-hackathon-arena', 'tag-ai'),
('announcement-digital-library-hub', 'tag-digital-library'),
('announcement-commencement-graduation', 'tag-graduation'),
('announcement-blood-donation-day', 'tag-blood-donation'),
('announcement-faculty-excellence-awards', 'tag-teachers-day'),
('announcement-faculty-excellence-awards', 'tag-scholarship')
ON CONFLICT ("announcementId", "tagId") DO NOTHING;

-- 10. Seed Official Attachments for Articles
INSERT INTO engagement."ArticleAttachment" (
    "id", "announcementId", "fileName", "fileUrl", "fileSizeBytes", "mimeType", "checksumSha256", "downloadCount"
) VALUES
('att-semiconductor-decision', 'announcement-semiconductor-cleanroom', 'Quyet_dinh_thanh_lap_Lab_Ban_dan_Cleanroom_2026.pdf', '/documents/announcements/Quyet_dinh_Lab_Ban_dan_2026.pdf', 1845200, 'application/pdf', 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855', 142),
('att-green-summer-plan', 'announcement-green-summer-volunteer', 'Ke_hoach_Chien_dich_Mua_he_xanh_2026.pdf', '/documents/announcements/Ke_hoach_Mua_he_xanh_2026.pdf', 2450100, 'application/pdf', 'cf83e1357eefb8bdf1542850d66d8007d620e4050b5715dc83f4a921d36ce9ce', 389),
('att-sports-cup-rules', 'announcement-campus-sports-cup', 'Dieu_le_Giai_Bong_da_UTE_Champions_Cup_2026.pdf', '/documents/announcements/Dieu_le_Bong_da_Champions_Cup_2026.pdf', 980400, 'application/pdf', '4b227777d4dd1fc61c6f884f48641d02b4d121d3fd328cb08b5531fcacdabf8a', 512),
('att-ieee-conference-schedule', 'announcement-stem-conference-keynote', 'Chuong_trinh_Hoi_nghi_Quoc_te_IEEE_2026.pdf', '/documents/announcements/Chuong_trinh_IEEE_2026.pdf', 3120500, 'application/pdf', 'ef2d127de37b942baad06145e54b0c619a1f22327b2ebbcfbec78f5564afe39d', 230),
('att-hackathon-handbook', 'announcement-ai-hackathon-arena', 'So_tay_Thi_sinh_AI_Hackathon_Innovation_2026.pdf', '/documents/announcements/So_tay_AI_Hackathon_2026.pdf', 1540800, 'application/pdf', 'bc25d74261895a6ad1ccde11f0a200778c1a70425a8053a47941cb0272c73c88', 420),
('att-graduation-handbook', 'announcement-commencement-graduation', 'Huong_dan_Nghi_le_Tot_nghiep_Tan_Khoa_2026.pdf', '/documents/announcements/Huong_dan_Tot_nghiep_2026.pdf', 2180900, 'application/pdf', 'a591a6d40bf420404a011733cfb7b190d62c65bf0bcda32b57b277d9ad9f146e', 890)
ON CONFLICT ("id") DO UPDATE SET
    "fileName" = EXCLUDED."fileName",
    "fileSizeBytes" = EXCLUDED."fileSizeBytes",
    "downloadCount" = EXCLUDED."downloadCount";
