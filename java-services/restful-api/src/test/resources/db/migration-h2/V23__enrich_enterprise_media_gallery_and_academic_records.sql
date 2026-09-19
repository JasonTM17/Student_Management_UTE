-- H2 mirror of V68__enrich_enterprise_media_gallery_and_academic_records.sql (parity required by plan R4).
-- Enriches media galleries, attachments, reading metrics, and operations records for H2 integration testing.

-- 1. Media Gallery H2 Seeds
MERGE INTO "engagement"."ArticleMediaGallery" (
    "id", "announcementId", "mediaUrl", "thumbnailUrl", "captionVi", "captionEn", "altText",
    "mediaType", "aspectRatio", "width", "height", "fileSizeBytes", "displayOrder", "isCover"
) KEY("id") VALUES
('gal-semiconductor-cover', 'announcement-semiconductor-cleanroom', '/images/news/semiconductor-cleanroom.jpg', '/images/news/semiconductor-cleanroom.jpg', 'Toàn cảnh phòng sạch Class 1000', 'Panoramic view of Class 1000 cleanroom', 'Phòng sạch Bán dẫn HCM-UTE', 'IMAGE', '16:9', 1920, 1080, 931639, 0, TRUE),
('gal-robotics-cover', 'announcement-robotics-iot-lab', '/images/news/robotics-iot-lab.jpg', '/images/news/robotics-iot-lab.jpg', 'Cánh tay robot KUKA 6 bậc', '6-DOF KUKA industrial robotic arm', 'Cánh tay robot KUKA', 'IMAGE', '16:9', 1920, 1080, 962448, 0, TRUE);

-- 2. Attachments H2 Seeds
MERGE INTO "engagement"."ArticleAttachment" (
    "id", "announcementId", "fileName", "fileUrl", "fileSizeBytes", "mimeType", "checksumSha256", "downloadCount", "isPublic"
) KEY("id") VALUES
('att-rector-academic-plan', 'announcement-semiconductor-cleanroom', 'Quyet_dinh_Ke_hoach_Nam_hoc.pdf', '/documents/announcements/Quyet_dinh.pdf', 2154800, 'application/pdf', 'a1b2c3d4e5f67890123456789abcdef0123456789abcdef0123456789abcdef0', 100, TRUE);
