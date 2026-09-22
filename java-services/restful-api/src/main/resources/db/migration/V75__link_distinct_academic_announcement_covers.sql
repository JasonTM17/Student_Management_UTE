-- V75: Link distinct high-resolution academic photography covers to campus announcements.
-- Replaces duplicated course-registration.jpg references with specialized, authentic campus imagery:
-- 1. Examination regulations & proctoring -> exam-proctoring-regulations.jpg
-- 2. Tuition payment & VietQR/banking -> tuition-payment-banking.jpg
-- 3. English & IT competency exit benchmark -> english-it-benchmark-lab.jpg
-- 4. Academic counseling & credit advising -> academic-counseling-room.jpg
-- 5. Digital certified transcript service -> digital-transcript-kiosk.jpg
-- 6. Official timetable & class schedules -> student-schedule-timetable.jpg

UPDATE engagement."Announcement"
SET "coverImageUrl" = '/images/news/exam-proctoring-regulations.jpg'
WHERE "id" IN ('announcement-ute-exam-regulations-schedule', 'announcement-ute-final-exam-regulations-2026', 'announcement-exam-preparation');

UPDATE engagement."Announcement"
SET "coverImageUrl" = '/images/news/tuition-payment-banking.jpg'
WHERE "id" = 'announcement-ute-tuition-payment-notice';

UPDATE engagement."Announcement"
SET "coverImageUrl" = '/images/news/english-it-benchmark-lab.jpg'
WHERE "id" = 'announcement-ute-english-it-exit-benchmark';

UPDATE engagement."Announcement"
SET "coverImageUrl" = '/images/news/academic-counseling-room.jpg'
WHERE "id" IN ('announcement-ute-academic-warning-counseling', 'announcement-v26-academic-warning');

UPDATE engagement."Announcement"
SET "coverImageUrl" = '/images/news/digital-transcript-kiosk.jpg'
WHERE "id" IN ('announcement-ute-digital-transcript-signature', 'announcement-grade-policy');

UPDATE engagement."Announcement"
SET "coverImageUrl" = '/images/news/student-schedule-timetable.jpg'
WHERE "id" IN ('announcement-schedule-published', 'announcement-prerequisite-check');

UPDATE engagement."Announcement"
SET "coverImageUrl" = '/images/news/commencement-graduation.jpg'
WHERE "id" = 'announcement-rector-new-academic-year-decision';
