-- Complete metadata for the original demo courses and place them in the
-- curriculum so every one of the 100 courses has an explainable bucket.

UPDATE academic."Course"
SET "description" = COALESCE(NULLIF("description", ''), 'Phân tích thiết kế hướng đối tượng, lập trình Java nâng cao, xử lý ngoại lệ, collection, generic, stream và xây dựng dịch vụ có thể kiểm thử.'),
    "descriptionEn" = COALESCE(NULLIF("descriptionEn", ''), 'Object-oriented design, advanced Java, exception handling, collections, generics, streams, and testable service development.'),
    "descriptionVi" = COALESCE(NULLIF("descriptionVi", ''), 'Phân tích thiết kế hướng đối tượng, lập trình Java nâng cao, xử lý ngoại lệ, collection, generic, stream và xây dựng dịch vụ có thể kiểm thử.'),
    "updatedAt" = CURRENT_TIMESTAMP
WHERE "id" = 'course-java-demo';

UPDATE academic."Course"
SET "description" = COALESCE(NULLIF("description", ''), 'Thiết kế giao diện web, HTTP, REST, xác thực, quản lý trạng thái, kết nối PostgreSQL và triển khai ứng dụng web có quan sát.'),
    "descriptionEn" = COALESCE(NULLIF("descriptionEn", ''), 'Web interface design, HTTP, REST, authentication, state management, PostgreSQL integration, and observable web delivery.'),
    "descriptionVi" = COALESCE(NULLIF("descriptionVi", ''), 'Thiết kế giao diện web, HTTP, REST, xác thực, quản lý trạng thái, kết nối PostgreSQL và triển khai ứng dụng web có quan sát.'),
    "updatedAt" = CURRENT_TIMESTAMP
WHERE "id" = 'course-web-demo';

INSERT INTO academic."CurriculumCourse" ("id", "curriculumId", "courseId", "year", "semester", "isMandatory")
SELECT seed.id, 'curriculum-demo', seed.course_id, 2, seed.semester, TRUE
FROM (VALUES
    ('curriculum-course-se401', 'course-java-demo', 1),
    ('curriculum-course-se402', 'course-web-demo', 2)
) AS seed(id, course_id, semester)
WHERE NOT EXISTS (SELECT 1 FROM academic."CurriculumCourse" existing WHERE existing."id" = seed.id);
