-- Update all raw 'admin-user' and technical identifiers in publishedBy to official HCMUTE departments

-- 1. General fallback for any remaining admin-user
UPDATE engagement."Announcement"
SET "publishedBy" = 'Phòng Đào Tạo UTE'
WHERE "publishedBy" IS NULL OR "publishedBy" ILIKE '%admin%' OR "publishedBy" = '';

-- 2. Specific departmental assignments for accurate institutional provenance
UPDATE engagement."Announcement"
SET "publishedBy" = 'Khoa Công nghệ Thông tin & Phòng Đào Tạo'
WHERE id IN (
  'announcement-v32-ute-research',
  'announcement-ute-student-scientific-research-awards',
  'announcement-v32-smart-campus',
  'announcement-v26-thesis-round'
);

UPDATE engagement."Announcement"
SET "publishedBy" = 'Khoa Công nghệ Thông tin & Lab AI'
WHERE id IN ('announcement-v26-research-group');

UPDATE engagement."Announcement"
SET "publishedBy" = 'Trung Tâm Hướng Nghiệp & Quan Hệ Doanh Nghiệp'
WHERE id IN ('announcement-v32-job-fair', 'announcement-v26-career-day');

UPDATE engagement."Announcement"
SET "publishedBy" = 'Phòng Công Tác Sinh Viên & Đào Tạo'
WHERE id IN ('announcement-v26-scholarship', 'announcement-v32-exchange-prog');

UPDATE engagement."Announcement"
SET "publishedBy" = 'Phòng Công Tác Sinh Viên'
WHERE id IN ('announcement-v26-english-club', 'announcement-v26-parking-notice');

UPDATE engagement."Announcement"
SET "publishedBy" = 'Phòng Khảo Thí & Đảm Bảo Chất Lượng'
WHERE id IN ('announcement-exam-preparation', 'announcement-grade-policy', 'announcement-v26-academic-warning', 'announcement-v26-gradebook-window');

UPDATE engagement."Announcement"
SET "publishedBy" = 'Trung Tâm Công Nghệ Thông Tin'
WHERE id IN ('announcement-ai-rag', 'announcement-assistant-citations', 'announcement-maintenance-window', '0967096b-c6fa-46e6-a724-1221059b659e', 'announcement-v26-lab-upgrade');

UPDATE engagement."Announcement"
SET "publishedBy" = 'Thư Viện Trung Tâm HCMUTE'
WHERE id IN ('announcement-library-resources');

UPDATE engagement."Announcement"
SET "publishedBy" = 'Phòng Đào Tạo & Trung Tâm CNTT'
WHERE id IN ('announcement-v26-lecturer-training');
