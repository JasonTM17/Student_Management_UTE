-- H2 twin of production V127__standardize_admin_office_identity.sql.
UPDATE campuscore_auth."User"
SET "firstName" = 'Đào tạo',
    "lastName" = 'Phòng',
    "avatar" = NULL,
    "updatedAt" = CURRENT_TIMESTAMP
WHERE "id" = 'admin-user'
  AND ("firstName" IS DISTINCT FROM 'Đào tạo'
       OR "lastName" IS DISTINCT FROM 'Phòng'
       OR "avatar" IS NOT NULL);
