-- Restore canonical student identity for the showcase demo account (MSSV 24110054 - Nguyễn Tiến Sơn)
UPDATE campuscore_auth."User"
SET "firstName" = 'Tiến Sơn',
    "lastName" = 'Nguyễn',
    "gender" = 'MALE',
    "dateOfBirth" = '2006-10-17 00:00:00',
    "phone" = '0903112233',
    "address" = 'Phường Buôn Ma Thuột, Tỉnh Đắk Lắk',
    "avatar" = 'https://images.unsplash.com/photo-1539571696357-5a69c17a67c6?w=300&auto=format&fit=crop&q=80',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE "email" = 'student@campuscore.edu';

UPDATE academic."Student"
SET "studentId" = '24110054'
WHERE "userId" IN (SELECT "id" FROM campuscore_auth."User" WHERE "email" = 'student@campuscore.edu');
