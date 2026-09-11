-- Update all references from 'Sư phạm Kỹ thuật' to 'Công nghệ Kỹ thuật' in Announcements
UPDATE engagement."Announcement"
SET content = REPLACE(content, 'Sư phạm Kỹ thuật', 'Công nghệ Kỹ thuật'),
    title = REPLACE(title, 'Sư phạm Kỹ thuật', 'Công nghệ Kỹ thuật'),
    "publishedBy" = REPLACE("publishedBy", 'Sư phạm Kỹ thuật', 'Công nghệ Kỹ thuật')
WHERE content ILIKE '%Sư phạm Kỹ thuật%' OR title ILIKE '%Sư phạm Kỹ thuật%' OR "publishedBy" ILIKE '%Sư phạm Kỹ thuật%';

-- Insert or ensure official decree from Rector PGS. TS. Lê Hiếu Giang
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
) VALUES (
    'announcement-rector-new-academic-year-decision',
    'Quyết định của Hiệu trưởng về Kế hoạch Năm học & Chiến lược Phát triển Nhà trường 2026-2027',
    '<p><strong>HIỆU TRƯỞNG TRƯỜNG ĐẠI HỌC CÔNG NGHỆ KỸ THUẬT TP. HỒ CHÍ MINH (HCM-UTE)</strong></p>
<p>Căn cứ Quyết định số 2809/QĐ-TTg của Thủ tướng Chính phủ về việc đổi tên Trường Đại học Sư phạm Kỹ thuật TP.HCM thành Trường Đại học Công nghệ Kỹ thuật TP.HCM;</p>
<p>Căn cứ Quyết định của Bộ trưởng Bộ Giáo dục và Đào tạo về việc công nhận Hiệu trưởng Trường Đại học Công nghệ Kỹ thuật TP.HCM nhiệm kỳ 2020 - 2025 đối với PGS. TS. Lê Hiếu Giang;</p>
<p>Căn cứ Nghị quyết Hội đồng trường và đề nghị của Ban Giám hiệu, Trưởng Khoa Công nghệ Thông tin, Trưởng Phòng Đào tạo,</p>
<h3>QUYẾT ĐỊNH:</h3>
<p><strong>Điều 1.</strong> Ban hành Kế hoạch tổng thể Năm học 2026-2027 trên Cổng thông tin học vụ số CampusUTE dành cho toàn thể giảng viên, sinh viên và học viên.</p>
<p><strong>Điều 2.</strong> Các nhiệm vụ trọng tâm:</p>
<ul>
  <li>Kiện toàn hệ sinh thái số CampusUTE: Tích hợp chữ ký số e-Office chính thức cho Bảng điểm học tập điện tử và các biểu mẫu học vụ.</li>
  <li>Đưa vào vận hành Cụm máy chủ tính toán hiệu năng cao (HPC Cluster 8x A100 GPU) tại Khoa CNTT phục vụ đề tài NCKH, Khóa luận tốt nghiệp chuyên sâu về AI & Big Data.</li>
  <li>Bảo đảm quyền lợi học tập, đăng ký tín chỉ tối đa 28 tín chỉ/học kỳ, chính sách học bổng khuyến khích học tập và xét điểm rèn luyện minh bạch.</li>
</ul>
<p><strong>Điều 3.</strong> Các Phó Hiệu trưởng (TS. Quách Thanh Hải, TS. Trương Thị Hiền, PGS. TS. Châu Đình Thành), Trưởng Phòng Đào tạo, Trưởng Khoa CNTT và các đơn vị trực thuộc chịu trách nhiệm thi hành Quyết định này.</p>',
    'URGENT',
    ARRAY['STUDENT', 'LECTURER', 'ADMIN']::text[],
    ARRAY[]::integer[],
    TRUE,
    '2026-09-09 08:00:00+07',
    'Ban Giám hiệu - Hiệu trưởng PGS. TS. Lê Hiếu Giang',
    'semester-demo',
    'Học kỳ 1 năm học 2026-2027',
    '2026-09-09 08:00:00+07',
    '2026-09-09 08:00:00+07',
    1
) ON CONFLICT (id) DO UPDATE SET
    title = EXCLUDED.title,
    content = EXCLUDED.content,
    "publishedBy" = EXCLUDED."publishedBy",
    priority = EXCLUDED.priority,
    "targetRoles" = EXCLUDED."targetRoles",
    "updatedAt" = EXCLUDED."updatedAt";
