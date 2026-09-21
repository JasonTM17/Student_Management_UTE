-- The demo catalog concentrated every student on the single CNTT curriculum,
-- so the admin analytics department chart showed one full bar and fifteen
-- zeros, and seven departments had no courses at all. This migration gives
-- every department its own curriculum, spreads the demo students across them
-- with a realistic weighting (CNTT stays the flagship cohort but no longer
-- swallows the campus), and seeds starter courses plus one open section for
-- each department that had none.

-- ---------------------------------------------------------------------------
-- 1. One curriculum per department that did not have one
-- ---------------------------------------------------------------------------
INSERT INTO academic."Curriculum"
    ("id", "code", "name", "nameEn", "nameVi", "departmentId", "academicYearId",
     "totalCredits", "descriptionVi", "isActive")
VALUES
    ('curriculum-se',      'CT-SE-2026',      'Kỹ thuật phần mềm',                'Software Engineering',            'Kỹ thuật phần mềm',                'department-fit-software',      'academic-year-demo', 150, 'Chương trình kỹ sư ngành Kỹ thuật phần mềm.', TRUE),
    ('curriculum-ais',     'CT-AIDS-2026',    'Trí tuệ nhân tạo',                 'Artificial Intelligence',         'Trí tuệ nhân tạo',                 'department-fit-ai',            'academic-year-demo', 148, 'Chương trình kỹ sư ngành Trí tuệ nhân tạo.', TRUE),
    ('curriculum-netsec',  'CT-NETSEC-2026',  'An toàn thông tin',                'Information Security',            'An toàn thông tin',                'department-fit-network',       'academic-year-demo', 152, 'Chương trình kỹ sư ngành An toàn thông tin.', TRUE),
    ('curriculum-isbd',    'CT-ISBD-2026',    'Hệ thống thông tin doanh nghiệp',  'Enterprise Information Systems',  'Hệ thống thông tin doanh nghiệp',  'department-fit-is',            'academic-year-demo', 145, 'Chương trình ngành Hệ thống thông tin doanh nghiệp.', TRUE),
    ('curriculum-mis',     'CT-MIS-2026',     'Hệ thống thông tin kinh doanh',    'Business Information Systems',    'Hệ thống thông tin kinh doanh',    'department-foe-mis',           'academic-year-demo', 145, 'Chương trình ngành Hệ thống thông tin kinh doanh.', TRUE),
    ('curriculum-auto',    'CT-AUTO-2026',    'Kỹ thuật điều khiển và tự động hóa', 'Control Engineering and Automation', 'Kỹ thuật điều khiển và tự động hóa', 'department-feee-auto',     'academic-year-demo', 150, 'Chương trình ngành Kỹ thuật điều khiển và tự động hóa.', TRUE),
    ('curriculum-etec',    'CT-ETEC-2026',    'Kỹ thuật điện tử - viễn thông',    'Electronics and Telecommunications', 'Kỹ thuật điện tử - viễn thông', 'department-feee-telecom',      'academic-year-demo', 150, 'Chương trình ngành Kỹ thuật điện tử - viễn thông.', TRUE),
    ('curriculum-robot',   'CT-ROBOT-2026',   'Kỹ thuật robot',                   'Robotics Engineering',            'Kỹ thuật robot',                   'department-fme-robotics',      'academic-year-demo', 149, 'Chương trình ngành Kỹ thuật robot.', TRUE),
    ('curriculum-manuf',   'CT-MANUF-2026',   'Công nghệ chế tạo máy',            'Manufacturing Engineering',       'Công nghệ chế tạo máy',            'department-fme-manufacturing', 'academic-year-demo', 148, 'Chương trình ngành Công nghệ chế tạo máy.', TRUE),
    ('curriculum-autoeng', 'CT-AUTOENG-2026', 'Kỹ thuật ô tô',                    'Automotive Engineering',          'Kỹ thuật ô tô',                    'department-fme-automotive',    'academic-year-demo', 150, 'Chương trình ngành Kỹ thuật ô tô.', TRUE),
    ('curriculum-bimeng',  'CT-BIMENG-2026',  'Công nghệ xây dựng và BIM',        'Construction Technology and BIM', 'Công nghệ xây dựng và BIM',        'department-fce-bim',           'academic-year-demo', 146, 'Chương trình ngành Công nghệ xây dựng và BIM.', TRUE),
    ('curriculum-civil',   'CT-CIVIL-2026',   'Kỹ thuật xây dựng',                'Civil Engineering',               'Kỹ thuật xây dựng',                'department-fce-civil',         'academic-year-demo', 147, 'Chương trình ngành Kỹ thuật xây dựng.', TRUE),
    ('curriculum-bte',     'CT-BTE-2026',     'Kinh doanh quốc tế',               'International Business',          'Kinh doanh quốc tế',               'department-ffl-business',      'academic-year-demo', 138, 'Chương trình ngành Kinh doanh quốc tế.', TRUE),
    ('curriculum-fin',     'CT-FIN-2026',     'Tài chính - Ngân hàng',            'Finance and Banking',             'Tài chính - Ngân hàng',            'department-foe-finance',       'academic-year-demo', 140, 'Chương trình ngành Tài chính - Ngân hàng.', TRUE),
    ('curriculum-lscm',    'CT-LSCM-2026',    'Quản lý chuỗi cung ứng',           'Supply Chain Management',         'Quản lý chuỗi cung ứng',           'department-foe-logistics',     'academic-year-demo', 142, 'Chương trình ngành Quản lý chuỗi cung ứng.', TRUE)
ON CONFLICT ("id") DO NOTHING;

-- ---------------------------------------------------------------------------
-- 2. Spread the demo students across every curriculum
--
-- Deterministic: students are ordered by id and sliced into weighted ranges,
-- so re-running the migration against an already-migrated database is a
-- no-op for the row count and always yields the same distribution.
-- CNTT keeps the largest single cohort; every other department gets a
-- plausible share of the campus.
-- ---------------------------------------------------------------------------
WITH ordered AS (
    SELECT s."id" AS student_id,
           ROW_NUMBER() OVER (ORDER BY s."id") AS rn
    FROM academic."Student" s
    WHERE s."curriculumId" = 'curriculum-demo'
),
ranges(curriculum_id, lo, hi) AS (
    VALUES
        ('curriculum-demo',      1,  62),
        ('curriculum-se',       63,  90),
        ('curriculum-mis',      91, 110),
        ('curriculum-netsec',  111, 126),
        ('curriculum-ais',     127, 142),
        ('curriculum-auto',    143, 156),
        ('curriculum-etec',    157, 168),
        ('curriculum-manuf',   169, 178),
        ('curriculum-civil',   179, 187),
        ('curriculum-lscm',    188, 195),
        ('curriculum-bte',     196, 202),
        ('curriculum-fin',     203, 209),
        ('curriculum-isbd',    210, 215),
        ('curriculum-robot',   216, 221),
        ('curriculum-autoeng', 222, 226),
        ('curriculum-bimeng',  227, 230)
)
UPDATE academic."Student" s
SET "curriculumId" = r.curriculum_id,
    "updatedAt" = CURRENT_TIMESTAMP
FROM ordered o
JOIN ranges r ON o.rn BETWEEN r.lo AND r.hi
WHERE s."id" = o.student_id;

-- ---------------------------------------------------------------------------
-- 3. Starter courses for departments with an empty catalog
-- ---------------------------------------------------------------------------
INSERT INTO academic."Course"
    ("id", "code", "name", "nameEn", "nameVi", "departmentId", "credits", "isActive")
VALUES
    ('course-se-arch',      'SE401', 'Kiến trúc phần mềm nâng cao',        'Advanced Software Architecture',     'Kiến trúc phần mềm nâng cao',        'department-fit-software', 3, TRUE),
    ('course-se-quality',   'SE402', 'Kiểm thử và đảm bảo chất lượng phần mềm', 'Software Testing and Quality Assurance', 'Kiểm thử và đảm bảo chất lượng phần mềm', 'department-fit-software', 3, TRUE),
    ('course-se-devops',    'SE403', 'DevOps và triển khai hệ thống',      'DevOps and System Deployment',       'DevOps và triển khai hệ thống',      'department-fit-software', 3, TRUE),
    ('course-se-analysis',  'SE404', 'Phân tích và thiết kế hệ thống',     'Systems Analysis and Design',        'Phân tích và thiết kế hệ thống',     'department-fit-software', 3, TRUE),
    ('course-ns-netsec',    'NS401', 'An toàn mạng',                       'Network Security',                   'An toàn mạng',                       'department-fit-network', 3, TRUE),
    ('course-ns-crypto',    'NS402', 'Mật mã học ứng dụng',                'Applied Cryptography',               'Mật mã học ứng dụng',                'department-fit-network', 3, TRUE),
    ('course-ns-forensic',  'NS403', 'Điều tra số',                        'Digital Forensics',                  'Điều tra số',                        'department-fit-network', 3, TRUE),
    ('course-ns-embedded',  'NS404', 'Bảo mật hệ thống nhúng',             'Embedded Systems Security',          'Bảo mật hệ thống nhúng',             'department-fit-network', 3, TRUE),
    ('course-ai-deep',      'AI401', 'Học sâu',                            'Deep Learning',                      'Học sâu',                            'department-fit-ai', 3, TRUE),
    ('course-ai-nlp',       'AI402', 'Xử lý ngôn ngữ tự nhiên',            'Natural Language Processing',        'Xử lý ngôn ngữ tự nhiên',            'department-fit-ai', 3, TRUE),
    ('course-ai-vision',    'AI403', 'Thị giác máy tính',                  'Computer Vision',                    'Thị giác máy tính',                  'department-fit-ai', 3, TRUE),
    ('course-ai-rl',        'AI404', 'Học tăng cường',                     'Reinforcement Learning',             'Học tăng cường',                     'department-fit-ai', 3, TRUE),
    ('course-is-ent',       'IS401', 'Hệ thống thông tin doanh nghiệp',    'Enterprise Information Systems',     'Hệ thống thông tin doanh nghiệp',    'department-fit-is', 3, TRUE),
    ('course-is-bi',        'IS402', 'Phân tích dữ liệu kinh doanh',       'Business Analytics',                 'Phân tích dữ liệu kinh doanh',       'department-fit-is', 3, TRUE),
    ('course-is-governance','IS403', 'Quản trị dữ liệu',                   'Data Governance',                    'Quản trị dữ liệu',                   'department-fit-is', 3, TRUE),
    ('course-is-dx',        'IS404', 'Chuyển đổi số',                      'Digital Transformation',             'Chuyển đổi số',                      'department-fit-is', 3, TRUE),
    ('course-ls-scm',       'LS401', 'Quản trị chuỗi cung ứng',            'Supply Chain Management',            'Quản trị chuỗi cung ứng',            'department-foe-logistics', 3, TRUE),
    ('course-ls-transport', 'LS402', 'Vận tải và phân phối',               'Transportation and Distribution',    'Vận tải và phân phối',               'department-foe-logistics', 3, TRUE),
    ('course-ls-warehouse', 'LS403', 'Quản trị kho vận',                   'Warehouse Operations',               'Quản trị kho vận',                   'department-foe-logistics', 3, TRUE),
    ('course-ls-ecom',      'LS404', 'Thương mại điện tử logistics',       'E-commerce Logistics',               'Thương mại điện tử logistics',       'department-foe-logistics', 3, TRUE),
    ('course-mf-cnc',       'MF401', 'Gia công CNC chính xác',             'Precision CNC Machining',            'Gia công CNC chính xác',             'department-fme-manufacturing', 3, TRUE),
    ('course-mf-cadcam',    'MF402', 'CAD/CAM và công nghệ chế tạo',       'CAD/CAM and Manufacturing',          'CAD/CAM và công nghệ chế tạo',       'department-fme-manufacturing', 3, TRUE),
    ('course-mf-design',    'MF403', 'Kỹ thuật chi tiết máy',              'Machine Element Design',             'Kỹ thuật chi tiết máy',              'department-fme-manufacturing', 3, TRUE),
    ('course-mf-automation','MF404', 'Tự động hóa sản xuất',               'Manufacturing Automation',           'Tự động hóa sản xuất',               'department-fme-manufacturing', 3, TRUE),
    ('course-bm-bim',       'BM401', 'Mô hình thông tin công trình',       'Building Information Modeling',      'Mô hình thông tin công trình',       'department-fce-bim', 3, TRUE),
    ('course-bm-digital',   'BM402', 'Kỹ thuật số trong xây dựng',         'Digital Construction Engineering',   'Kỹ thuật số trong xây dựng',         'department-fce-bim', 3, TRUE),
    ('course-bm-project',   'BM403', 'Quản lý dự án xây dựng',             'Construction Project Management',    'Quản lý dự án xây dựng',             'department-fce-bim', 3, TRUE),
    ('course-bm-material',  'BM404', 'Vật liệu và công nghệ xây dựng',     'Construction Materials and Technology', 'Vật liệu và công nghệ xây dựng',  'department-fce-bim', 3, TRUE)
ON CONFLICT ("id") DO NOTHING;

-- ---------------------------------------------------------------------------
-- 4. One open section per new course, taught by a lecturer of the same
--    department (first lecturer by id keeps the assignment deterministic).
-- ---------------------------------------------------------------------------
INSERT INTO academic."Section"
    ("id", "sectionNumber", "courseId", "semesterId", "lecturerId", "capacity", "enrolledCount", "status")
SELECT
    'section-demo-' || c."code",
    c."code" || '-01',
    c."id",
    'semester-demo',
    (SELECT l."id" FROM academic."Lecturer" l WHERE l."departmentId" = c."departmentId" ORDER BY l."id" LIMIT 1),
    60,
    0,
    'OPEN'
FROM academic."Course" c
WHERE c."id" IN (
    'course-se-arch', 'course-se-quality', 'course-se-devops', 'course-se-analysis',
    'course-ns-netsec', 'course-ns-crypto', 'course-ns-forensic', 'course-ns-embedded',
    'course-ai-deep', 'course-ai-nlp', 'course-ai-vision', 'course-ai-rl',
    'course-is-ent', 'course-is-bi', 'course-is-governance', 'course-is-dx',
    'course-ls-scm', 'course-ls-transport', 'course-ls-warehouse', 'course-ls-ecom',
    'course-mf-cnc', 'course-mf-cadcam', 'course-mf-design', 'course-mf-automation',
    'course-bm-bim', 'course-bm-digital', 'course-bm-project', 'course-bm-material'
)
ON CONFLICT ("id") DO NOTHING;
