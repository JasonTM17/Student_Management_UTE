-- Supabase Migration: Enterprise Academic Article & Media Ecosystem with Strict Relational Constraints
-- Mirrors Flyway V64__enterprise_article_ecosystem_and_strict_constraints.sql for Supabase PostgreSQL parity

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
