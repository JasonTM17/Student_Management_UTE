-- V127__standardize_admin_office_identity.sql
-- Purpose:
--   V33 gave admin-user (admin@campuscore.edu) a lecturer-style personal name
--   ("ThS. Hoàng" / "Tiến Dũng") and portrait photo before V89 separated the
--   academic-affairs office (ADMIN) from lecturer/dean identities. Align the
--   stored User row with the office identity ("Phòng Đào tạo", no lecturer
--   honorific or personal photo) so /admin/*, /dashboard/editor, /admin/users,
--   and announcement audit labels all present the same office identity.

UPDATE campuscore_auth."User"
SET "firstName" = 'Đào tạo',
    "lastName" = 'Phòng',
    "avatar" = NULL,
    "updatedAt" = CURRENT_TIMESTAMP
WHERE "id" = 'admin-user'
  AND ("firstName" IS DISTINCT FROM 'Đào tạo'
       OR "lastName" IS DISTINCT FROM 'Phòng'
       OR "avatar" IS NOT NULL);
