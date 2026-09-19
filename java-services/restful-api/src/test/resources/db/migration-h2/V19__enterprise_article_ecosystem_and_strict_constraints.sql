-- H2 mirror of V64__enterprise_article_ecosystem_and_strict_constraints.sql (parity required by plan R4).
-- Defines engagement schemas, categories, tags, galleries, attachments, and strict check constraints for H2 testing.

CREATE SCHEMA IF NOT EXISTS "engagement";

-- 1. Table: engagement."ArticleCategory"
CREATE TABLE IF NOT EXISTS "engagement"."ArticleCategory" (
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
    "createdAt" TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT article_category_tone_ck CHECK ("colorTone" IN ('blue', 'indigo', 'emerald', 'amber', 'rose', 'purple', 'cyan', 'slate')),
    CONSTRAINT article_category_order_ck CHECK ("displayOrder" >= 0)
);

-- 2. Table: engagement."ArticleTag"
CREATE TABLE IF NOT EXISTS "engagement"."ArticleTag" (
    "id" VARCHAR(60) PRIMARY KEY,
    "slug" VARCHAR(60) UNIQUE NOT NULL,
    "nameVi" VARCHAR(80) NOT NULL,
    "nameEn" VARCHAR(80) NOT NULL,
    "usageCount" INTEGER NOT NULL DEFAULT 0,
    "createdAt" TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT article_tag_name_ck CHECK (length(trim("nameVi")) >= 2),
    CONSTRAINT article_tag_usage_ck CHECK ("usageCount" >= 0)
);

-- 3. Table: engagement."AnnouncementTagMap"
CREATE TABLE IF NOT EXISTS "engagement"."AnnouncementTagMap" (
    "announcementId" VARCHAR(120) NOT NULL,
    "tagId" VARCHAR(60) NOT NULL,
    "createdAt" TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY ("announcementId", "tagId")
);

-- 4. Table: engagement."ArticleMediaGallery"
CREATE TABLE IF NOT EXISTS "engagement"."ArticleMediaGallery" (
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
    "createdAt" TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT article_gallery_type_ck CHECK ("mediaType" IN ('IMAGE', 'VIDEO', 'DIAGRAM', 'INFOGRAPHIC')),
    CONSTRAINT article_gallery_order_ck CHECK ("displayOrder" >= 0)
);

-- 5. Table: engagement."ArticleAttachment"
CREATE TABLE IF NOT EXISTS "engagement"."ArticleAttachment" (
    "id" VARCHAR(120) PRIMARY KEY,
    "announcementId" VARCHAR(120) NOT NULL,
    "fileName" VARCHAR(255) NOT NULL,
    "fileUrl" VARCHAR(500) NOT NULL,
    "fileSizeBytes" BIGINT NOT NULL,
    "mimeType" VARCHAR(120) NOT NULL,
    "checksumSha256" VARCHAR(64),
    "downloadCount" INTEGER NOT NULL DEFAULT 0,
    "isPublic" BOOLEAN NOT NULL DEFAULT TRUE,
    "createdAt" TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT article_attachment_size_ck CHECK ("fileSizeBytes" > 0 AND "fileSizeBytes" <= 52428800),
    CONSTRAINT article_attachment_downloads_ck CHECK ("downloadCount" >= 0)
);

-- 6. Table: engagement."ArticleReadingMetric"
CREATE TABLE IF NOT EXISTS "engagement"."ArticleReadingMetric" (
    "id" VARCHAR(120) PRIMARY KEY,
    "announcementId" VARCHAR(120) NOT NULL,
    "readerUserId" VARCHAR(120),
    "ipAddressHash" VARCHAR(64) NOT NULL,
    "userAgentHash" VARCHAR(64) NOT NULL,
    "dwellTimeSeconds" INTEGER NOT NULL DEFAULT 0,
    "readPercentage" INTEGER NOT NULL DEFAULT 0,
    "firstReadAt" TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "lastReadAt" TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT article_reading_dwell_ck CHECK ("dwellTimeSeconds" >= 0),
    CONSTRAINT article_reading_percentage_ck CHECK ("readPercentage" BETWEEN 0 AND 100)
);
