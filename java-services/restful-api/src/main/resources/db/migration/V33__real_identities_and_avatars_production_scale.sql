-- V33__real_identities_and_avatars_production_scale.sql
-- Purpose: Provide authentic Vietnamese full names, professional portrait avatars,
-- and rich demographic data (DOB, address, phone, gender) for all students and lecturers.
-- Eliminates generic "Demo" or "Sinh viên XXX" placeholders with real, realistic profiles.

-- 1. DEMO STUDENT (MSSV 24110054 - Nguyễn Tiến Sơn)
UPDATE campuscore_auth."User"
SET "firstName" = 'Tiến Sơn',
    "lastName" = 'Nguyễn',
    "gender" = 'MALE',
    "dateOfBirth" = '2006-10-17 00:00:00',
    "phone" = '0903112233',
    "address" = 'Phường Buôn Ma Thuột, Tỉnh Đắk Lắk',
    "avatar" = 'https://images.unsplash.com/photo-1539571696357-5a69c17a67c6?w=300&auto=format&fit=crop&q=80'
WHERE "id" = 'student-user';

UPDATE academic."Student"
SET "studentId" = '24110054'
WHERE "userId" = 'student-user';

-- 2. DEMO LECTURER (PGS.TS. Trần Văn Bình)
UPDATE campuscore_auth."User"
SET "firstName" = 'Văn Bình',
    "lastName" = 'PGS.TS. Trần',
    "gender" = 'MALE',
    "dateOfBirth" = '1980-05-12 00:00:00',
    "phone" = '0918889922',
    "address" = 'Phường Linh Chiểu, TP. Thủ Đức, TP. Hồ Chí Minh',
    "avatar" = 'https://images.unsplash.com/photo-1560250097-0b93528c311a?w=300&auto=format&fit=crop&q=80'
WHERE "id" = 'lecturer-user';

-- 3. DEMO ADMIN (ThS. Hoàng Tiến Dũng - Trưởng phòng Đào tạo & Quản trị Website)
UPDATE campuscore_auth."User"
SET "firstName" = 'Tiến Dũng',
    "lastName" = 'ThS. Hoàng',
    "gender" = 'MALE',
    "dateOfBirth" = '1985-09-20 00:00:00',
    "phone" = '0909998877',
    "address" = 'Võ Văn Ngân, TP. Thủ Đức, TP. Hồ Chí Minh',
    "avatar" = 'https://images.unsplash.com/photo-1472099645785-5658abf4ff4e?w=300&auto=format&fit=crop&q=80'
WHERE "id" = 'admin-user';

-- 4. UPDATE EXISTING LECTURERS WITH REAL AUTHENTIC VIETNAMESE NAMES & AVATARS
UPDATE campuscore_auth."User" SET "firstName" = 'Thị Hoa', "lastName" = 'TS. Nguyễn', "gender" = 'FEMALE',
    "avatar" = 'https://images.unsplash.com/photo-1573496359142-b8d87734a5a2?w=300&auto=format&fit=crop&q=80'
WHERE "id" = 'lecturer-user-003';

UPDATE campuscore_auth."User" SET "firstName" = 'Minh Tuấn', "lastName" = 'ThS. Lê', "gender" = 'MALE',
    "avatar" = 'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=300&auto=format&fit=crop&q=80'
WHERE "id" = 'lecturer-user-004';

UPDATE campuscore_auth."User" SET "firstName" = 'Ngọc Lan', "lastName" = 'TS. Phạm Thị', "gender" = 'FEMALE',
    "avatar" = 'https://images.unsplash.com/photo-1580489944761-15a19d654956?w=300&auto=format&fit=crop&q=80'
WHERE "id" = 'lecturer-user-005';

UPDATE campuscore_auth."User" SET "firstName" = 'Quốc Dũng', "lastName" = 'ThS. Hoàng', "gender" = 'MALE',
    "avatar" = 'https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=300&auto=format&fit=crop&q=80'
WHERE "id" = 'lecturer-user-006';

UPDATE campuscore_auth."User" SET "firstName" = 'Ngọc Mai', "lastName" = 'ThS. Vũ', "gender" = 'FEMALE',
    "avatar" = 'https://images.unsplash.com/photo-1544005313-94ddf0286df2?w=300&auto=format&fit=crop&q=80'
WHERE "id" = 'lecturer-user-007';

UPDATE campuscore_auth."User" SET "firstName" = 'Thái Sơn', "lastName" = 'TS. Đặng', "gender" = 'MALE',
    "avatar" = 'https://images.unsplash.com/photo-1519085360753-af0119f7cbe7?w=300&auto=format&fit=crop&q=80'
WHERE "id" = 'lecturer-user-008';

UPDATE campuscore_auth."User" SET "firstName" = 'Thu Hà', "lastName" = 'ThS. Bùi Thị', "gender" = 'FEMALE',
    "avatar" = 'https://images.unsplash.com/photo-1517841905240-472988babdf9?w=300&auto=format&fit=crop&q=80'
WHERE "id" = 'lecturer-user-009';

UPDATE campuscore_auth."User" SET "firstName" = 'Công Danh', "lastName" = 'ThS. Dương', "gender" = 'MALE',
    "avatar" = 'https://images.unsplash.com/photo-1506794778202-cad84cf45f1d?w=300&auto=format&fit=crop&q=80'
WHERE "id" = 'lecturer-user-010';

UPDATE campuscore_auth."User" SET "firstName" = 'Thanh Trúc', "lastName" = 'TS. Lý', "gender" = 'FEMALE',
    "avatar" = 'https://images.unsplash.com/photo-1524504388940-b1c1722653e1?w=300&auto=format&fit=crop&q=80'
WHERE "id" = 'lecturer-user-011';

UPDATE campuscore_auth."User" SET "firstName" = 'Hoàng Long', "lastName" = 'ThS. Mai', "gender" = 'MALE',
    "avatar" = 'https://images.unsplash.com/photo-1522075469751-3a6694fb2f61?w=300&auto=format&fit=crop&q=80'
WHERE "id" = 'lecturer-user-012';

-- 5. UPDATE STUDENTS (student-user-002 through student-user-020) WITH REAL VIETNAMESE NAMES & AVATARS
UPDATE campuscore_auth."User" SET "firstName" = 'Mai Phương', "lastName" = 'Trần Thị', "gender" = 'FEMALE', "dateOfBirth" = '2005-03-15 00:00:00', "address" = 'Quận 1, TP. Hồ Chí Minh',
    "avatar" = 'https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=300&auto=format&fit=crop&q=80'
WHERE "id" = 'student-user-002';

UPDATE campuscore_auth."User" SET "firstName" = 'Hoàng Long', "lastName" = 'Lê', "gender" = 'MALE', "dateOfBirth" = '2005-07-22 00:00:00', "address" = 'Biên Hòa, Tỉnh Đồng Nai',
    "avatar" = 'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=300&auto=format&fit=crop&q=80'
WHERE "id" = 'student-user-003';

UPDATE campuscore_auth."User" SET "firstName" = 'Minh Tuấn', "lastName" = 'Phạm', "gender" = 'MALE', "dateOfBirth" = '2005-11-05 00:00:00', "address" = 'Thủ Dầu Một, Tỉnh Bình Dương',
    "avatar" = 'https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=300&auto=format&fit=crop&q=80'
WHERE "id" = 'student-user-004';

UPDATE campuscore_auth."User" SET "firstName" = 'Thu Thảo', "lastName" = 'Đỗ Thị', "gender" = 'FEMALE', "dateOfBirth" = '2006-01-30 00:00:00', "address" = 'TP. Vũng Tàu, Tỉnh Bà Rịa - Vũng Tàu',
    "avatar" = 'https://images.unsplash.com/photo-1438761681033-6461ffad8d80?w=300&auto=format&fit=crop&q=80'
WHERE "id" = 'student-user-005';

UPDATE campuscore_auth."User" SET "firstName" = 'Đức Anh', "lastName" = 'Vũ', "gender" = 'MALE', "dateOfBirth" = '2005-09-14 00:00:00', "address" = 'TP. Nha Trang, Tỉnh Khánh Hòa',
    "avatar" = 'https://images.unsplash.com/photo-1472099645785-5658abf4ff4e?w=300&auto=format&fit=crop&q=80'
WHERE "id" = 'student-user-006';

UPDATE campuscore_auth."User" SET "firstName" = 'Ngọc Ánh', "lastName" = 'Bùi', "gender" = 'FEMALE', "dateOfBirth" = '2006-04-18 00:00:00', "address" = 'TP. Phan Thiết, Tỉnh Bình Thuận',
    "avatar" = 'https://images.unsplash.com/photo-1544005313-94ddf0286df2?w=300&auto=format&fit=crop&q=80'
WHERE "id" = 'student-user-007';

UPDATE campuscore_auth."User" SET "firstName" = 'Quốc Bảo', "lastName" = 'Dương', "gender" = 'MALE', "dateOfBirth" = '2005-12-08 00:00:00', "address" = 'TP. Cần Thơ',
    "avatar" = 'https://images.unsplash.com/photo-1519085360753-af0119f7cbe7?w=300&auto=format&fit=crop&q=80'
WHERE "id" = 'student-user-008';

UPDATE campuscore_auth."User" SET "firstName" = 'Yến Nhi', "lastName" = 'Hoàng', "gender" = 'FEMALE', "dateOfBirth" = '2006-06-25 00:00:00', "address" = 'TP. Đà Lạt, Tỉnh Lâm Đồng',
    "avatar" = 'https://images.unsplash.com/photo-1517841905240-472988babdf9?w=300&auto=format&fit=crop&q=80'
WHERE "id" = 'student-user-009';

UPDATE campuscore_auth."User" SET "firstName" = 'Văn Hùng', "lastName" = 'Ngô', "gender" = 'MALE', "dateOfBirth" = '2005-02-11 00:00:00', "address" = 'TP. Mỹ Tho, Tỉnh Tiền Giang',
    "avatar" = 'https://images.unsplash.com/photo-1506794778202-cad84cf45f1d?w=300&auto=format&fit=crop&q=80'
WHERE "id" = 'student-user-010';

UPDATE campuscore_auth."User" SET "firstName" = 'Thanh Trúc', "lastName" = 'Lý', "gender" = 'FEMALE', "dateOfBirth" = '2006-08-03 00:00:00', "address" = 'Quận 7, TP. Hồ Chí Minh',
    "avatar" = 'https://images.unsplash.com/photo-1524504388940-b1c1722653e1?w=300&auto=format&fit=crop&q=80'
WHERE "id" = 'student-user-011';

UPDATE campuscore_auth."User" SET "firstName" = 'Khắc Việt', "lastName" = 'Trịnh', "gender" = 'MALE', "dateOfBirth" = '2005-10-19 00:00:00', "address" = 'TP. Quy Nhơn, Tỉnh Bình Định',
    "avatar" = 'https://images.unsplash.com/photo-1522075469751-3a6694fb2f61?w=300&auto=format&fit=crop&q=80'
WHERE "id" = 'student-user-012';

UPDATE campuscore_auth."User" SET "firstName" = 'Diệu Huyền', "lastName" = 'Lâm', "gender" = 'FEMALE', "dateOfBirth" = '2006-05-14 00:00:00', "address" = 'TP. Pleiku, Tỉnh Gia Lai',
    "avatar" = 'https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=300&auto=format&fit=crop&q=80'
WHERE "id" = 'student-user-013';

UPDATE campuscore_auth."User" SET "firstName" = 'Gia Huy', "lastName" = 'Đặng', "gender" = 'MALE', "dateOfBirth" = '2005-08-30 00:00:00', "address" = 'TP. Long Xuyên, Tỉnh An Giang',
    "avatar" = 'https://images.unsplash.com/photo-1539571696357-5a69c17a67c6?w=300&auto=format&fit=crop&q=80'
WHERE "id" = 'student-user-014';

UPDATE campuscore_auth."User" SET "firstName" = 'Quỳnh Như', "lastName" = 'Võ Thị', "gender" = 'FEMALE', "dateOfBirth" = '2006-03-09 00:00:00', "address" = 'TP. Rạch Giá, Tỉnh Kiên Giang',
    "avatar" = 'https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=300&auto=format&fit=crop&q=80'
WHERE "id" = 'student-user-015';

UPDATE campuscore_auth."User" SET "firstName" = 'Trọng Nghĩa', "lastName" = 'Đinh', "gender" = 'MALE', "dateOfBirth" = '2005-04-27 00:00:00', "address" = 'Tân An, Tỉnh Long An',
    "avatar" = 'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=300&auto=format&fit=crop&q=80'
WHERE "id" = 'student-user-016';

-- 6. INSERT ADDITIONAL 15 REALISTIC STUDENTS FOR PRODUCTION VOLUME (K22, K23, K24)
INSERT INTO campuscore_auth."User" ("id", "email", "password", "firstName", "lastName", "gender", "dateOfBirth", "address", "avatar", "status", "emailVerified")
SELECT seed.id, seed.email,
       (SELECT "password" FROM campuscore_auth."User" WHERE "id" = 'student-user'),
       seed.first_name, seed.last_name, seed.gender, seed.dob::TIMESTAMP, seed.address, seed.avatar, 'ACTIVE', TRUE
FROM (VALUES
    ('student-user-021', 'phuong.ttm@campuscore.demo', 'Thanh Phong', 'Nguyễn', 'MALE', '2004-02-14', 'TP. Thủ Đức, TP.HCM', 'https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=300&auto=format&fit=crop&q=80'),
    ('student-user-022', 'linh.ntk@campuscore.demo', 'Khánh Linh', 'Nguyễn Thị', 'FEMALE', '2005-11-20', 'Quận Bình Thạnh, TP.HCM', 'https://images.unsplash.com/photo-1438761681033-6461ffad8d80?w=300&auto=format&fit=crop&q=80'),
    ('student-user-023', 'dat.tq@campuscore.demo', 'Quốc Đạt', 'Trần', 'MALE', '2004-08-09', 'Tỉnh Đồng Nai', 'https://images.unsplash.com/photo-1472099645785-5658abf4ff4e?w=300&auto=format&fit=crop&q=80'),
    ('student-user-024', 'vy.ltt@campuscore.demo', 'Thảo Vy', 'Lê Thị', 'FEMALE', '2005-04-12', 'Tỉnh Bình Dương', 'https://images.unsplash.com/photo-1544005313-94ddf0286df2?w=300&auto=format&fit=crop&q=80'),
    ('student-user-025', 'huy.pnh@campuscore.demo', 'Nhật Huy', 'Phạm', 'MALE', '2004-12-01', 'Tỉnh Bà Rịa - Vũng Tàu', 'https://images.unsplash.com/photo-1519085360753-af0119f7cbe7?w=300&auto=format&fit=crop&q=80'),
    ('student-user-026', 'hang.dtm@campuscore.demo', 'Minh Hằng', 'Đỗ Thị', 'FEMALE', '2006-07-17', 'Tỉnh Đắk Lắk', 'https://images.unsplash.com/photo-1517841905240-472988babdf9?w=300&auto=format&fit=crop&q=80'),
    ('student-user-027', 'kien.vdt@campuscore.demo', 'Trung Kiên', 'Vũ', 'MALE', '2004-05-23', 'Tỉnh Lâm Đồng', 'https://images.unsplash.com/photo-1506794778202-cad84cf45f1d?w=300&auto=format&fit=crop&q=80'),
    ('student-user-028', 'trang.btt@campuscore.demo', 'Thùy Trang', 'Bùi Thị', 'FEMALE', '2005-09-30', 'Tỉnh Tiền Giang', 'https://images.unsplash.com/photo-1524504388940-b1c1722653e1?w=300&auto=format&fit=crop&q=80'),
    ('student-user-029', 'hai.dth@campuscore.demo', 'Thanh Hải', 'Dương', 'MALE', '2004-01-19', 'Tỉnh Bến Tre', 'https://images.unsplash.com/photo-1522075469751-3a6694fb2f61?w=300&auto=format&fit=crop&q=80'),
    ('student-user-030', 'an.hnt@campuscore.demo', 'Ngọc An', 'Hoàng', 'FEMALE', '2006-10-04', 'Tỉnh Cần Thơ', 'https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=300&auto=format&fit=crop&q=80')
) AS seed(id, email, first_name, last_name, gender, dob, address, avatar)
WHERE NOT EXISTS (
    SELECT 1 FROM campuscore_auth."User" existing WHERE existing."id" = seed.id
);

INSERT INTO academic."Student" ("id", "userId", "studentId", "curriculumId", "year", "status", "admissionDate")
SELECT 'student-profile-' || substring(seed.id from 14),
       seed.id,
       '2411' || substring(seed.id from 14),
       'curriculum-demo',
       3,
       'ACTIVE',
       CURRENT_TIMESTAMP - INTERVAL '2 years'
FROM (VALUES
    ('student-user-021'), ('student-user-022'), ('student-user-023'), ('student-user-024'), ('student-user-025'),
    ('student-user-026'), ('student-user-027'), ('student-user-028'), ('student-user-029'), ('student-user-030')
) AS seed(id)
WHERE NOT EXISTS (
    SELECT 1 FROM academic."Student" existing WHERE existing."userId" = seed.id
);

INSERT INTO campuscore_auth."UserRole" ("id", "userId", "roleId")
SELECT 'user-role-' || seed.id, seed.id, 'role-student'
FROM (VALUES
    ('student-user-021'), ('student-user-022'), ('student-user-023'), ('student-user-024'), ('student-user-025'),
    ('student-user-026'), ('student-user-027'), ('student-user-028'), ('student-user-029'), ('student-user-030')
) AS seed(id)
WHERE NOT EXISTS (
    SELECT 1 FROM campuscore_auth."UserRole" existing
    WHERE existing."userId" = seed.id AND existing."roleId" = 'role-student'
);
