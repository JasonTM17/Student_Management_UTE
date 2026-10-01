-- Synthetic audit rows only; target is the isolated audit database.
BEGIN;
INSERT INTO academic."Course"
SELECT (jsonb_populate_record(NULL::academic."Course", to_jsonb(c) ||
  '{"id":"course-ux-audit","code":"AUDITUX","name":"Audit interaction course","nameEn":"Audit interaction course","nameVi":"Học phần kiểm thử thao tác"}'::jsonb)).*
FROM academic."Course" c WHERE c."id"='course-java-demo'
ON CONFLICT ("id") DO NOTHING;
INSERT INTO academic."Section" ("id","sectionNumber","courseId","semesterId","lecturerId","capacity","enrolledCount","status")
VALUES ('section-ux-audit','000-UX-AUDIT','course-ux-audit','semester-demo','lecturer-profile',10,10,'OPEN')
ON CONFLICT ("id") DO UPDATE SET "sectionNumber"='000-UX-AUDIT',"capacity"=10,"enrolledCount"=10;
INSERT INTO campuscore_auth."User"
SELECT (jsonb_populate_record(NULL::campuscore_auth."User", to_jsonb(u) || jsonb_build_object(
 'id','ux-audit-user-'||lpad(n::text,2,'0'), 'email','ux-audit-'||n||'@campuscore.test',
 'firstName','Audit', 'lastName','Student '||lpad(n::text,2,'0')))).*
FROM campuscore_auth."User" u CROSS JOIN generate_series(2,10) n WHERE u."email"='student@campuscore.edu'
ON CONFLICT ("id") DO NOTHING;
INSERT INTO academic."Student"
SELECT (jsonb_populate_record(NULL::academic."Student", to_jsonb(s) || jsonb_build_object(
 'id','ux-audit-student-'||lpad(n::text,2,'0'), 'userId','ux-audit-user-'||lpad(n::text,2,'0'),
 'studentId','UX-AUDIT-'||lpad(n::text,2,'0')))).*
FROM academic."Student" s CROSS JOIN generate_series(2,10) n WHERE s."id"='student-profile'
ON CONFLICT ("id") DO NOTHING;
INSERT INTO academic."Enrollment" ("id","studentId","sectionId","semesterId","status","enrolledAt","gradeStatus","finalGrade","letterGrade","courseId","creditsSnapshot")
VALUES ('enrollment-ux-audit','student-profile','section-ux-audit','semester-demo','COMPLETED',CURRENT_TIMESTAMP,'DRAFT',6,'C','course-ux-audit',3)
ON CONFLICT ("id") DO UPDATE SET "gradeStatus"='DRAFT',"finalGrade"=6,"letterGrade"='C';
INSERT INTO academic."Enrollment" ("id","studentId","sectionId","semesterId","status","enrolledAt","gradeStatus","finalGrade","letterGrade","courseId","creditsSnapshot")
SELECT 'enrollment-ux-audit-'||lpad(n::text,2,'0'),'ux-audit-student-'||lpad(n::text,2,'0'),
 'section-ux-audit','semester-demo','COMPLETED',CURRENT_TIMESTAMP,'DRAFT',6,'C','course-ux-audit',3
FROM generate_series(2,10) n
ON CONFLICT ("id") DO UPDATE SET "gradeStatus"='DRAFT',"finalGrade"=6,"letterGrade"='C';
DELETE FROM academic."StudentGrade" WHERE "enrollmentId" LIKE 'enrollment-ux-audit%';
DELETE FROM academic."GradeItem" WHERE "sectionId"='section-ux-audit';
INSERT INTO academic."GradeItem" ("id","sectionId","name","type","maxScore","weight") VALUES
('section-ux-audit-process-50','section-ux-audit','Process','PROCESS',10,50),
('section-ux-audit-final-50','section-ux-audit','Final','FINAL',10,50)
ON CONFLICT ("id") DO NOTHING;
INSERT INTO academic."StudentGrade" ("id","enrollmentId","gradeItemId","score") VALUES
('score-process-ux-audit','enrollment-ux-audit','section-ux-audit-process-50',6),
('score-final-ux-audit','enrollment-ux-audit','section-ux-audit-final-50',6)
ON CONFLICT ("id") DO UPDATE SET "score"=6;
INSERT INTO academic."StudentGrade" ("id","enrollmentId","gradeItemId","score")
SELECT 'score-'||kind||'-ux-audit-'||lpad(n::text,2,'0'),'enrollment-ux-audit-'||lpad(n::text,2,'0'),
 'section-ux-audit-'||kind||'-50',6
FROM generate_series(2,10) n CROSS JOIN (VALUES ('process'),('final')) kinds(kind);
DELETE FROM academic."SectionSchedule" WHERE "sectionId"='section-ux-audit';
INSERT INTO academic."SectionSchedule" ("id","sectionId","classroomId","dayOfWeek","startTime","endTime")
SELECT 'schedule-ux-audit','section-ux-audit',"id",1,'22:00','23:00'
FROM academic."Classroom" ORDER BY "id" LIMIT 1;
COMMIT;
