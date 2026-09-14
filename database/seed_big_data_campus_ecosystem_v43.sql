-- =====================================================================
-- Flyway Migration V43: Comprehensive Big Data Campus Ecosystem Enrichment
-- Normalizes course catalog, prerequisites, faculty departments, lecturers,
-- student conduct scores, thesis topics, campus announcements, and RAG knowledge.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. SPECIALIZED DEPARTMENTS & ACADEMIC DIVISIONS
-- ---------------------------------------------------------------------
INSERT INTO academic."Department" (
    id, name, "nameEn", "nameVi", code, description, "descriptionEn", "descriptionVi",
    chair, phone, email, building, "facultyId", "createdAt", "updatedAt", "isActive"
) VALUES (
    'department-fit-software', 'Bộ môn Công nghệ Phần mềm', 'Department of Software Engineering', 'Bộ môn Công nghệ Phần mềm', 'SE', 'Đào tạo kỹ sư phần mềm chuyên sâu kiến trúc phân tán, cloud native và AI engineering', 'Đào tạo kỹ sư phần mềm chuyên sâu kiến trúc phân tán, cloud native và AI engineering', 'Đào tạo kỹ sư phần mềm chuyên sâu kiến trúc phân tán, cloud native và AI engineering',
    'PGS.TS. Huỳnh Thanh Sang', '028-3722-1223', 'dept.se@campuscore.edu', 'Tòa nhà A1 - Tầng 4', 'faculty-demo',
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, TRUE
) ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name, "nameEn" = EXCLUDED."nameEn", "nameVi" = EXCLUDED."nameVi",
    code = EXCLUDED.code, description = EXCLUDED.description, chair = EXCLUDED.chair,
    building = EXCLUDED.building, "updatedAt" = CURRENT_TIMESTAMP;
INSERT INTO academic."Department" (
    id, name, "nameEn", "nameVi", code, description, "descriptionEn", "descriptionVi",
    chair, phone, email, building, "facultyId", "createdAt", "updatedAt", "isActive"
) VALUES (
    'department-fit-ai', 'Bộ môn Trí tuệ Nhân tạo & Khoa học Dữ liệu', 'Department of AI & Data Science', 'Bộ môn Trí tuệ Nhân tạo & Khoa học Dữ liệu', 'AIDS', 'Nghiên cứu và đào tạo chuyên sâu về học máy, thị giác máy tính, NLP và Big Data', 'Nghiên cứu và đào tạo chuyên sâu về học máy, thị giác máy tính, NLP và Big Data', 'Nghiên cứu và đào tạo chuyên sâu về học máy, thị giác máy tính, NLP và Big Data',
    'TS. Cao Minh Thông', '028-3722-1223', 'dept.aids@campuscore.edu', 'Tòa nhà A1 - Tầng 4', 'faculty-demo',
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, TRUE
) ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name, "nameEn" = EXCLUDED."nameEn", "nameVi" = EXCLUDED."nameVi",
    code = EXCLUDED.code, description = EXCLUDED.description, chair = EXCLUDED.chair,
    building = EXCLUDED.building, "updatedAt" = CURRENT_TIMESTAMP;
INSERT INTO academic."Department" (
    id, name, "nameEn", "nameVi", code, description, "descriptionEn", "descriptionVi",
    chair, phone, email, building, "facultyId", "createdAt", "updatedAt", "isActive"
) VALUES (
    'department-fit-network', 'Bộ môn Mạng máy tính & An toàn thông tin', 'Department of Computer Networks & Cybersecurity', 'Bộ môn Mạng máy tính & An toàn thông tin', 'NETSEC', 'Đào tạo an ninh mạng, kiểm thử xâm nhập, điện toán đám mây và hệ thống mạng doanh nghiệp', 'Đào tạo an ninh mạng, kiểm thử xâm nhập, điện toán đám mây và hệ thống mạng doanh nghiệp', 'Đào tạo an ninh mạng, kiểm thử xâm nhập, điện toán đám mây và hệ thống mạng doanh nghiệp',
    'ThS. Lê Thị Ánh Nguyệt', '028-3722-1223', 'dept.netsec@campuscore.edu', 'Tòa nhà A1 - Tầng 5', 'faculty-demo',
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, TRUE
) ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name, "nameEn" = EXCLUDED."nameEn", "nameVi" = EXCLUDED."nameVi",
    code = EXCLUDED.code, description = EXCLUDED.description, chair = EXCLUDED.chair,
    building = EXCLUDED.building, "updatedAt" = CURRENT_TIMESTAMP;
INSERT INTO academic."Department" (
    id, name, "nameEn", "nameVi", code, description, "descriptionEn", "descriptionVi",
    chair, phone, email, building, "facultyId", "createdAt", "updatedAt", "isActive"
) VALUES (
    'department-fit-is', 'Bộ môn Hệ thống Thông tin & Dữ liệu lớn', 'Department of Information Systems & Big Data', 'Bộ môn Hệ thống Thông tin & Dữ liệu lớn', 'ISBD', 'Đào tạo phân tích dữ liệu kinh doanh, kho dữ liệu, hệ thống ERP và quản trị CSDL lớn', 'Đào tạo phân tích dữ liệu kinh doanh, kho dữ liệu, hệ thống ERP và quản trị CSDL lớn', 'Đào tạo phân tích dữ liệu kinh doanh, kho dữ liệu, hệ thống ERP và quản trị CSDL lớn',
    'TS. Nguyễn Thị Hoa', '028-3722-1223', 'dept.isbd@campuscore.edu', 'Tòa nhà A1 - Tầng 5', 'faculty-demo',
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, TRUE
) ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name, "nameEn" = EXCLUDED."nameEn", "nameVi" = EXCLUDED."nameVi",
    code = EXCLUDED.code, description = EXCLUDED.description, chair = EXCLUDED.chair,
    building = EXCLUDED.building, "updatedAt" = CURRENT_TIMESTAMP;
INSERT INTO academic."Department" (
    id, name, "nameEn", "nameVi", code, description, "descriptionEn", "descriptionVi",
    chair, phone, email, building, "facultyId", "createdAt", "updatedAt", "isActive"
) VALUES (
    'department-fme-manufacturing', 'Bộ môn Kỹ thuật Chế tạo máy & CNC', 'Department of Mechanical Manufacturing & CNC', 'Bộ môn Kỹ thuật Chế tạo máy & CNC', 'MANUF', 'Đào tạo kỹ thuật gia công chính xác 5 trục, công nghệ CAD/CAM/CAE và tự động hóa xưởng', 'Đào tạo kỹ thuật gia công chính xác 5 trục, công nghệ CAD/CAM/CAE và tự động hóa xưởng', 'Đào tạo kỹ thuật gia công chính xác 5 trục, công nghệ CAD/CAM/CAE và tự động hóa xưởng',
    'TS. Lê Hoàng Nam', '028-3722-1223', 'dept.manuf@campuscore.edu', 'Xưởng Cơ khí Khu F', 'faculty-fme',
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, TRUE
) ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name, "nameEn" = EXCLUDED."nameEn", "nameVi" = EXCLUDED."nameVi",
    code = EXCLUDED.code, description = EXCLUDED.description, chair = EXCLUDED.chair,
    building = EXCLUDED.building, "updatedAt" = CURRENT_TIMESTAMP;
INSERT INTO academic."Department" (
    id, name, "nameEn", "nameVi", code, description, "descriptionEn", "descriptionVi",
    chair, phone, email, building, "facultyId", "createdAt", "updatedAt", "isActive"
) VALUES (
    'department-foe-logistics', 'Bộ môn Logistics & Quản lý Chuỗi cung ứng', 'Department of Logistics & Supply Chain', 'Bộ môn Logistics & Quản lý Chuỗi cung ứng', 'LSCM', 'Đào tạo quản trị chuỗi cung ứng thông minh, logistics quốc tế và vận tải đa phương thức', 'Đào tạo quản trị chuỗi cung ứng thông minh, logistics quốc tế và vận tải đa phương thức', 'Đào tạo quản trị chuỗi cung ứng thông minh, logistics quốc tế và vận tải đa phương thức',
    'TS. Phan Thị Diệu Linh', '028-3722-1223', 'dept.lscm@campuscore.edu', 'Tòa nhà Trung tâm - Tầng 6', 'faculty-foe',
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, TRUE
) ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name, "nameEn" = EXCLUDED."nameEn", "nameVi" = EXCLUDED."nameVi",
    code = EXCLUDED.code, description = EXCLUDED.description, chair = EXCLUDED.chair,
    building = EXCLUDED.building, "updatedAt" = CURRENT_TIMESTAMP;
INSERT INTO academic."Department" (
    id, name, "nameEn", "nameVi", code, description, "descriptionEn", "descriptionVi",
    chair, phone, email, building, "facultyId", "createdAt", "updatedAt", "isActive"
) VALUES (
    'department-fce-bim', 'Bộ môn Kỹ thuật Xây dựng số & Mô hình BIM', 'Department of Digital Construction & BIM', 'Bộ môn Kỹ thuật Xây dựng số & Mô hình BIM', 'BIMENG', 'Đào tạo mô hình hóa thông tin công trình BIM 5D, phân tích kết cấu và quản lý thi công số', 'Đào tạo mô hình hóa thông tin công trình BIM 5D, phân tích kết cấu và quản lý thi công số', 'Đào tạo mô hình hóa thông tin công trình BIM 5D, phân tích kết cấu và quản lý thi công số',
    'TS. Đoàn Văn Long', '028-3722-1223', 'dept.bimeng@campuscore.edu', 'Tòa nhà C - Tầng 3', 'faculty-fce',
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, TRUE
) ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name, "nameEn" = EXCLUDED."nameEn", "nameVi" = EXCLUDED."nameVi",
    code = EXCLUDED.code, description = EXCLUDED.description, chair = EXCLUDED.chair,
    building = EXCLUDED.building, "updatedAt" = CURRENT_TIMESTAMP;

-- ---------------------------------------------------------------------
-- 2. NORMALIZE 88 PLACEHOLDER COURSES INTO AUTHENTIC SPECIALIZED CURRICULA
-- ---------------------------------------------------------------------
UPDATE academic."Course" SET
    code = 'SE013',
    name = 'Lập trình Web nâng cao với React & Node.js',
    "nameVi" = 'Lập trình Web nâng cao với React & Node.js',
    "nameEn" = 'Advanced Web Development with React & Node.js',
    description = 'Phát triển ứng dụng Web hiện đại fullstack với React 19, TypeScript, Next.js, kiến trúc RESTful/GraphQL và cơ sở dữ liệu phân tán',
    "descriptionVi" = 'Phát triển ứng dụng Web hiện đại fullstack với React 19, TypeScript, Next.js, kiến trúc RESTful/GraphQL và cơ sở dữ liệu phân tán',
    "descriptionEn" = 'Phát triển ứng dụng Web hiện đại fullstack với React 19, TypeScript, Next.js, kiến trúc RESTful/GraphQL và cơ sở dữ liệu phân tán',
    credits = 3,
    "departmentId" = 'department-demo',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE id = 'course-auto-013';
UPDATE academic."Course" SET
    code = 'SE014',
    name = 'Kiến trúc Microservices & Hệ thống Phân tán',
    "nameVi" = 'Kiến trúc Microservices & Hệ thống Phân tán',
    "nameEn" = 'Microservices & Distributed Systems Architecture',
    description = 'Thiết kế hệ thống hướng dịch vụ quy mô lớn, Service Mesh, Kafka Event-driven, API Gateway và phân tán cơ sở dữ liệu',
    "descriptionVi" = 'Thiết kế hệ thống hướng dịch vụ quy mô lớn, Service Mesh, Kafka Event-driven, API Gateway và phân tán cơ sở dữ liệu',
    "descriptionEn" = 'Thiết kế hệ thống hướng dịch vụ quy mô lớn, Service Mesh, Kafka Event-driven, API Gateway và phân tán cơ sở dữ liệu',
    credits = 4,
    "departmentId" = 'department-demo',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE id = 'course-auto-014';
UPDATE academic."Course" SET
    code = 'SE015',
    name = 'Phát triển ứng dụng di động đa nền tảng',
    "nameVi" = 'Phát triển ứng dụng di động đa nền tảng',
    "nameEn" = 'Cross-Platform Mobile Application Development',
    description = 'Lập trình ứng dụng di động với Flutter và React Native, tích hợp cảm biến, push notifications, offline-first và bảo mật di động',
    "descriptionVi" = 'Lập trình ứng dụng di động với Flutter và React Native, tích hợp cảm biến, push notifications, offline-first và bảo mật di động',
    "descriptionEn" = 'Lập trình ứng dụng di động với Flutter và React Native, tích hợp cảm biến, push notifications, offline-first và bảo mật di động',
    credits = 3,
    "departmentId" = 'department-demo',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE id = 'course-auto-015';
UPDATE academic."Course" SET
    code = 'SE016',
    name = 'Kiểm thử tự động và Đảm bảo chất lượng phần mềm',
    "nameVi" = 'Kiểm thử tự động và Đảm bảo chất lượng phần mềm',
    "nameEn" = 'Automated Testing & Software Quality Assurance',
    description = 'Kiểm thử phần mềm từ Unit, Integration đến E2E với JUnit 5, Playwright, Cypress, phân tích độ bao phủ mã nguồn và kiểm thử hiệu năng k6',
    "descriptionVi" = 'Kiểm thử phần mềm từ Unit, Integration đến E2E với JUnit 5, Playwright, Cypress, phân tích độ bao phủ mã nguồn và kiểm thử hiệu năng k6',
    "descriptionEn" = 'Kiểm thử phần mềm từ Unit, Integration đến E2E với JUnit 5, Playwright, Cypress, phân tích độ bao phủ mã nguồn và kiểm thử hiệu năng k6',
    credits = 3,
    "departmentId" = 'department-demo',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE id = 'course-auto-016';
UPDATE academic."Course" SET
    code = 'SE017',
    name = 'Kỹ thuật DevOps và Hạ tầng đám mây CI/CD',
    "nameVi" = 'Kỹ thuật DevOps và Hạ tầng đám mây CI/CD',
    "nameEn" = 'DevOps Engineering & Cloud Infrastructure CI/CD',
    description = 'Tự động hóa triển khai phần mềm liên tục CI/CD với GitHub Actions, Docker, Kubernetes, Terraform và giám sát Prometheus/Grafana',
    "descriptionVi" = 'Tự động hóa triển khai phần mềm liên tục CI/CD với GitHub Actions, Docker, Kubernetes, Terraform và giám sát Prometheus/Grafana',
    "descriptionEn" = 'Tự động hóa triển khai phần mềm liên tục CI/CD với GitHub Actions, Docker, Kubernetes, Terraform và giám sát Prometheus/Grafana',
    credits = 3,
    "departmentId" = 'department-demo',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE id = 'course-auto-017';
UPDATE academic."Course" SET
    code = 'SE018',
    name = 'Thiết kế Giao diện & Trải nghiệm người dùng UI/UX',
    "nameVi" = 'Thiết kế Giao diện & Trải nghiệm người dùng UI/UX',
    "nameEn" = 'UI/UX Interface & Interaction Design',
    description = 'Nguyên lý thiết kế Design System, Accessibility (WCAG 2.2), Wireframing & Prototyping bằng Figma, quy chuẩn trải nghiệm người dùng',
    "descriptionVi" = 'Nguyên lý thiết kế Design System, Accessibility (WCAG 2.2), Wireframing & Prototyping bằng Figma, quy chuẩn trải nghiệm người dùng',
    "descriptionEn" = 'Nguyên lý thiết kế Design System, Accessibility (WCAG 2.2), Wireframing & Prototyping bằng Figma, quy chuẩn trải nghiệm người dùng',
    credits = 3,
    "departmentId" = 'department-demo',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE id = 'course-auto-018';
UPDATE academic."Course" SET
    code = 'SE019',
    name = 'Mẫu thiết kế phần mềm nâng cao',
    "nameVi" = 'Mẫu thiết kế phần mềm nâng cao',
    "nameEn" = 'Advanced Software Design Patterns & Refactoring',
    description = 'Mẫu thiết kế GoF, Clean Architecture, Domain-Driven Design (DDD), nguyên lý SOLID và kỹ thuật tái cấu trúc mã nguồn kế thừa',
    "descriptionVi" = 'Mẫu thiết kế GoF, Clean Architecture, Domain-Driven Design (DDD), nguyên lý SOLID và kỹ thuật tái cấu trúc mã nguồn kế thừa',
    "descriptionEn" = 'Mẫu thiết kế GoF, Clean Architecture, Domain-Driven Design (DDD), nguyên lý SOLID và kỹ thuật tái cấu trúc mã nguồn kế thừa',
    credits = 3,
    "departmentId" = 'department-demo',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE id = 'course-auto-019';
UPDATE academic."Course" SET
    code = 'SE020',
    name = 'Phát triển ứng dụng đám mây với Docker & Kubernetes',
    "nameVi" = 'Phát triển ứng dụng đám mây với Docker & Kubernetes',
    "nameEn" = 'Cloud Native Applications with Docker & K8s',
    description = 'Container hóa ứng dụng, điều phối cụm Kubernetes, triển khai Helm chart, cân bằng tải Ingress và quản trị bí mật bảo mật',
    "descriptionVi" = 'Container hóa ứng dụng, điều phối cụm Kubernetes, triển khai Helm chart, cân bằng tải Ingress và quản trị bí mật bảo mật',
    "descriptionEn" = 'Container hóa ứng dụng, điều phối cụm Kubernetes, triển khai Helm chart, cân bằng tải Ingress và quản trị bí mật bảo mật',
    credits = 3,
    "departmentId" = 'department-demo',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE id = 'course-auto-020';
UPDATE academic."Course" SET
    code = 'SE021',
    name = 'Lập trình hướng khía cạnh & Modern Frameworks',
    "nameVi" = 'Lập trình hướng khía cạnh & Modern Frameworks',
    "nameEn" = 'Aspect-Oriented Programming & Modern Frameworks',
    description = 'Nguyên lý AOP, Spring Boot 3 chuyên sâu, dependency injection, quản lý giao dịch phân tán và tối ưu hóa bộ nhớ JVM',
    "descriptionVi" = 'Nguyên lý AOP, Spring Boot 3 chuyên sâu, dependency injection, quản lý giao dịch phân tán và tối ưu hóa bộ nhớ JVM',
    "descriptionEn" = 'Nguyên lý AOP, Spring Boot 3 chuyên sâu, dependency injection, quản lý giao dịch phân tán và tối ưu hóa bộ nhớ JVM',
    credits = 3,
    "departmentId" = 'department-demo',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE id = 'course-auto-021';
UPDATE academic."Course" SET
    code = 'SE022',
    name = 'Quản trị dự án phần mềm theo Agile/Scrum',
    "nameVi" = 'Quản trị dự án phần mềm theo Agile/Scrum',
    "nameEn" = 'Agile & Scrum Software Project Management',
    description = 'Phương pháp luận Agile, khung làm việc Scrum/Kanban, ước lượng nỗ lực Sprint, quản trị rủi ro và các chỉ số đo lường hiệu suất',
    "descriptionVi" = 'Phương pháp luận Agile, khung làm việc Scrum/Kanban, ước lượng nỗ lực Sprint, quản trị rủi ro và các chỉ số đo lường hiệu suất',
    "descriptionEn" = 'Phương pháp luận Agile, khung làm việc Scrum/Kanban, ước lượng nỗ lực Sprint, quản trị rủi ro và các chỉ số đo lường hiệu suất',
    credits = 3,
    "departmentId" = 'department-demo',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE id = 'course-auto-022';
UPDATE academic."Course" SET
    code = 'SE023',
    name = 'Phân tích yêu cầu và Đặc tả phần mềm',
    "nameVi" = 'Phân tích yêu cầu và Đặc tả phần mềm',
    "nameEn" = 'Software Requirements Engineering & Modeling',
    description = 'Kỹ thuật thu thập yêu cầu người dùng, phân tích ca sử dụng Use Case, sơ đồ tương tác UML 2.5 và kiểm chứng tính nhất quán phần mềm',
    "descriptionVi" = 'Kỹ thuật thu thập yêu cầu người dùng, phân tích ca sử dụng Use Case, sơ đồ tương tác UML 2.5 và kiểm chứng tính nhất quán phần mềm',
    "descriptionEn" = 'Kỹ thuật thu thập yêu cầu người dùng, phân tích ca sử dụng Use Case, sơ đồ tương tác UML 2.5 và kiểm chứng tính nhất quán phần mềm',
    credits = 3,
    "departmentId" = 'department-demo',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE id = 'course-auto-023';
UPDATE academic."Course" SET
    code = 'SE024',
    name = 'Lập trình an toàn và Phòng chống lỗ hổng OWASP',
    "nameVi" = 'Lập trình an toàn và Phòng chống lỗ hổng OWASP',
    "nameEn" = 'Secure Coding & OWASP Vulnerability Defense',
    description = 'Phát hiện và vá lỗi bảo mật Top 10 OWASP, SQL Injection, XSS, CSRF, mã hóa mật mã và rà quét lỗ hổng tĩnh/động SAST/DAST',
    "descriptionVi" = 'Phát hiện và vá lỗi bảo mật Top 10 OWASP, SQL Injection, XSS, CSRF, mã hóa mật mã và rà quét lỗ hổng tĩnh/động SAST/DAST',
    "descriptionEn" = 'Phát hiện và vá lỗi bảo mật Top 10 OWASP, SQL Injection, XSS, CSRF, mã hóa mật mã và rà quét lỗ hổng tĩnh/động SAST/DAST',
    credits = 3,
    "departmentId" = 'department-demo',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE id = 'course-auto-024';
UPDATE academic."Course" SET
    code = 'SE025',
    name = 'Đồ án Phát triển phần mềm doanh nghiệp',
    "nameVi" = 'Đồ án Phát triển phần mềm doanh nghiệp',
    "nameEn" = 'Enterprise Software Engineering Capstone Project',
    description = 'Đồ án tổng hợp theo nhóm sinh viên xây dựng giải pháp phần mềm doanh nghiệp hoàn chỉnh từ thiết kế đến triển khai sản phẩm thực tế',
    "descriptionVi" = 'Đồ án tổng hợp theo nhóm sinh viên xây dựng giải pháp phần mềm doanh nghiệp hoàn chỉnh từ thiết kế đến triển khai sản phẩm thực tế',
    "descriptionEn" = 'Đồ án tổng hợp theo nhóm sinh viên xây dựng giải pháp phần mềm doanh nghiệp hoàn chỉnh từ thiết kế đến triển khai sản phẩm thực tế',
    credits = 4,
    "departmentId" = 'department-demo',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE id = 'course-auto-025';
UPDATE academic."Course" SET
    code = 'AI026',
    name = 'Xử lý ngôn ngữ tự nhiên và Mô hình ngôn ngữ lớn LLM',
    "nameVi" = 'Xử lý ngôn ngữ tự nhiên và Mô hình ngôn ngữ lớn LLM',
    "nameEn" = 'Natural Language Processing & Large Language Models',
    description = 'Kiến trúc Transformer, Attention mechanism, Fine-tuning mô hình ngôn ngữ lớn, kỹ thuật RAG (Retrieval-Augmented Generation) và Prompt Engineering',
    "descriptionVi" = 'Kiến trúc Transformer, Attention mechanism, Fine-tuning mô hình ngôn ngữ lớn, kỹ thuật RAG (Retrieval-Augmented Generation) và Prompt Engineering',
    "descriptionEn" = 'Kiến trúc Transformer, Attention mechanism, Fine-tuning mô hình ngôn ngữ lớn, kỹ thuật RAG (Retrieval-Augmented Generation) và Prompt Engineering',
    credits = 4,
    "departmentId" = 'department-demo',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE id = 'course-auto-026';
UPDATE academic."Course" SET
    code = 'AI027',
    name = 'Thị giác máy tính và Xử lý ảnh số',
    "nameVi" = 'Thị giác máy tính và Xử lý ảnh số',
    "nameEn" = 'Computer Vision & Digital Image Processing',
    description = 'Phát hiện vật thể YOLO, phân đoạn ảnh Semantic Segmentation, nhận diện khuôn mặt, trích xuất đặc trưng và xử lý luồng video thời gian thực',
    "descriptionVi" = 'Phát hiện vật thể YOLO, phân đoạn ảnh Semantic Segmentation, nhận diện khuôn mặt, trích xuất đặc trưng và xử lý luồng video thời gian thực',
    "descriptionEn" = 'Phát hiện vật thể YOLO, phân đoạn ảnh Semantic Segmentation, nhận diện khuôn mặt, trích xuất đặc trưng và xử lý luồng video thời gian thực',
    credits = 4,
    "departmentId" = 'department-demo',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE id = 'course-auto-027';
UPDATE academic."Course" SET
    code = 'AI028',
    name = 'Học sâu nâng cao và Mạng nơ-ron',
    "nameVi" = 'Học sâu nâng cao và Mạng nơ-ron',
    "nameEn" = 'Advanced Deep Learning & Neural Architectures',
    description = 'Mạng nơ-ron tích chập CNN, mạng hồi quy RNN/LSTM, mô hình sinh GANs/Diffusion và kỹ thuật tối ưu hóa trọng số mô hình',
    "descriptionVi" = 'Mạng nơ-ron tích chập CNN, mạng hồi quy RNN/LSTM, mô hình sinh GANs/Diffusion và kỹ thuật tối ưu hóa trọng số mô hình',
    "descriptionEn" = 'Mạng nơ-ron tích chập CNN, mạng hồi quy RNN/LSTM, mô hình sinh GANs/Diffusion và kỹ thuật tối ưu hóa trọng số mô hình',
    credits = 4,
    "departmentId" = 'department-demo',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE id = 'course-auto-028';
UPDATE academic."Course" SET
    code = 'AI029',
    name = 'Khai phá dữ liệu và Trực quan hóa dữ liệu',
    "nameVi" = 'Khai phá dữ liệu và Trực quan hóa dữ liệu',
    "nameEn" = 'Data Mining & Data Visualization',
    description = 'Thuật toán phân cụm K-Means/DBSCAN, luật kết hợp Apriori, phân lớp cây quyết định và trực quan hóa dữ liệu trực quan bằng Seaborn & D3.js',
    "descriptionVi" = 'Thuật toán phân cụm K-Means/DBSCAN, luật kết hợp Apriori, phân lớp cây quyết định và trực quan hóa dữ liệu trực quan bằng Seaborn & D3.js',
    "descriptionEn" = 'Thuật toán phân cụm K-Means/DBSCAN, luật kết hợp Apriori, phân lớp cây quyết định và trực quan hóa dữ liệu trực quan bằng Seaborn & D3.js',
    credits = 3,
    "departmentId" = 'department-demo',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE id = 'course-auto-029';
UPDATE academic."Course" SET
    code = 'AI030',
    name = 'Xử lý dữ liệu lớn với Apache Spark & Hadoop',
    "nameVi" = 'Xử lý dữ liệu lớn với Apache Spark & Hadoop',
    "nameEn" = 'Big Data Processing with Apache Spark & Hadoop',
    description = 'Hệ sinh thái Big Data phân tán HDFS, MapReduce, xử lý luồng dữ liệu Spark Streaming, Resilient Distributed Datasets (RDD) và Spark SQL',
    "descriptionVi" = 'Hệ sinh thái Big Data phân tán HDFS, MapReduce, xử lý luồng dữ liệu Spark Streaming, Resilient Distributed Datasets (RDD) và Spark SQL',
    "descriptionEn" = 'Hệ sinh thái Big Data phân tán HDFS, MapReduce, xử lý luồng dữ liệu Spark Streaming, Resilient Distributed Datasets (RDD) và Spark SQL',
    credits = 4,
    "departmentId" = 'department-demo',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE id = 'course-auto-030';
UPDATE academic."Course" SET
    code = 'AI031',
    name = 'Kỹ thuật Học máy ứng dụng',
    "nameVi" = 'Kỹ thuật Học máy ứng dụng',
    "nameEn" = 'Applied Machine Learning Engineering',
    description = 'Quy trình xây dựng mô hình Machine Learning thực chiến với Scikit-Learn, tiền xử lý dữ liệu, Feature Engineering và tinh chỉnh siêu tham số',
    "descriptionVi" = 'Quy trình xây dựng mô hình Machine Learning thực chiến với Scikit-Learn, tiền xử lý dữ liệu, Feature Engineering và tinh chỉnh siêu tham số',
    "descriptionEn" = 'Quy trình xây dựng mô hình Machine Learning thực chiến với Scikit-Learn, tiền xử lý dữ liệu, Feature Engineering và tinh chỉnh siêu tham số',
    credits = 3,
    "departmentId" = 'department-demo',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE id = 'course-auto-031';
UPDATE academic."Course" SET
    code = 'AI032',
    name = 'Hệ thống gợi ý và Tìm kiếm thông tin',
    "nameVi" = 'Hệ thống gợi ý và Tìm kiếm thông tin',
    "nameEn" = 'Recommender Systems & Information Retrieval',
    description = 'Hệ gợi ý lọc cộng tác Collaborative Filtering, Matrix Factorization, Vector Search và công cụ tìm kiếm ngữ nghĩa Elasticsearch',
    "descriptionVi" = 'Hệ gợi ý lọc cộng tác Collaborative Filtering, Matrix Factorization, Vector Search và công cụ tìm kiếm ngữ nghĩa Elasticsearch',
    "descriptionEn" = 'Hệ gợi ý lọc cộng tác Collaborative Filtering, Matrix Factorization, Vector Search và công cụ tìm kiếm ngữ nghĩa Elasticsearch',
    credits = 3,
    "departmentId" = 'department-demo',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE id = 'course-auto-032';
UPDATE academic."Course" SET
    code = 'AI033',
    name = 'Kỹ thuật MLOps và Triển khai mô hình AI',
    "nameVi" = 'Kỹ thuật MLOps và Triển khai mô hình AI',
    "nameEn" = 'MLOps & Scalable AI Model Serving',
    description = 'Vòng đời quản trị mô hình AI, theo dõi thử nghiệm MLflow, đóng gói Docker/ONNX, phục vụ suy luận bằng Triton Server và giám sát độ lệch mô hình',
    "descriptionVi" = 'Vòng đời quản trị mô hình AI, theo dõi thử nghiệm MLflow, đóng gói Docker/ONNX, phục vụ suy luận bằng Triton Server và giám sát độ lệch mô hình',
    "descriptionEn" = 'Vòng đời quản trị mô hình AI, theo dõi thử nghiệm MLflow, đóng gói Docker/ONNX, phục vụ suy luận bằng Triton Server và giám sát độ lệch mô hình',
    credits = 3,
    "departmentId" = 'department-demo',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE id = 'course-auto-033';
UPDATE academic."Course" SET
    code = 'AI034',
    name = 'Toán cho Khoa học dữ liệu và Học máy',
    "nameVi" = 'Toán cho Khoa học dữ liệu và Học máy',
    "nameEn" = 'Mathematics for Data Science & Machine Learning',
    description = 'Đại số tuyến tính ma trận, Giải tích đa biến, Xác suất thống kê Bayes và Tối ưu hóa lồi áp dụng trực tiếp trong thuật toán AI',
    "descriptionVi" = 'Đại số tuyến tính ma trận, Giải tích đa biến, Xác suất thống kê Bayes và Tối ưu hóa lồi áp dụng trực tiếp trong thuật toán AI',
    "descriptionEn" = 'Đại số tuyến tính ma trận, Giải tích đa biến, Xác suất thống kê Bayes và Tối ưu hóa lồi áp dụng trực tiếp trong thuật toán AI',
    credits = 3,
    "departmentId" = 'department-demo',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE id = 'course-auto-034';
UPDATE academic."Course" SET
    code = 'AI035',
    name = 'Đồ án Trí tuệ nhân tạo và Kỹ thuật dữ liệu',
    "nameVi" = 'Đồ án Trí tuệ nhân tạo và Kỹ thuật dữ liệu',
    "nameEn" = 'AI & Data Engineering Capstone Project',
    description = 'Đồ án chuyên sâu theo nhóm giải quyết bài toán dự báo thông minh, thị giác nhân tạo hoặc xử lý dữ liệu lớn phục vụ thực tiễn',
    "descriptionVi" = 'Đồ án chuyên sâu theo nhóm giải quyết bài toán dự báo thông minh, thị giác nhân tạo hoặc xử lý dữ liệu lớn phục vụ thực tiễn',
    "descriptionEn" = 'Đồ án chuyên sâu theo nhóm giải quyết bài toán dự báo thông minh, thị giác nhân tạo hoặc xử lý dữ liệu lớn phục vụ thực tiễn',
    credits = 4,
    "departmentId" = 'department-demo',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE id = 'course-auto-035';
UPDATE academic."Course" SET
    code = 'NET036',
    name = 'Quản trị mạng doanh nghiệp Cisco CCNA',
    "nameVi" = 'Quản trị mạng doanh nghiệp Cisco CCNA',
    "nameEn" = 'Enterprise Network Administration (CCNA)',
    description = 'Định tuyến động OSPF/EIGRP, chuyển mạch VLAN, danh sách kiểm soát truy cập ACL, giao thức dự phòng HSRP và cấu hình mạng Cisco',
    "descriptionVi" = 'Định tuyến động OSPF/EIGRP, chuyển mạch VLAN, danh sách kiểm soát truy cập ACL, giao thức dự phòng HSRP và cấu hình mạng Cisco',
    "descriptionEn" = 'Định tuyến động OSPF/EIGRP, chuyển mạch VLAN, danh sách kiểm soát truy cập ACL, giao thức dự phòng HSRP và cấu hình mạng Cisco',
    credits = 4,
    "departmentId" = 'department-demo',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE id = 'course-auto-036';
UPDATE academic."Course" SET
    code = 'NET037',
    name = 'An toàn mạng và Tường lửa thế hệ mới',
    "nameVi" = 'An toàn mạng và Tường lửa thế hệ mới',
    "nameEn" = 'Network Security & Next-Generation Firewalls',
    description = 'Bảo vệ chu vi mạng, hệ thống phát hiện/ngăn chặn xâm nhập IDS/IPS, mạng riêng ảo VPN IPsec/SSL và kiến trúc Zero Trust',
    "descriptionVi" = 'Bảo vệ chu vi mạng, hệ thống phát hiện/ngăn chặn xâm nhập IDS/IPS, mạng riêng ảo VPN IPsec/SSL và kiến trúc Zero Trust',
    "descriptionEn" = 'Bảo vệ chu vi mạng, hệ thống phát hiện/ngăn chặn xâm nhập IDS/IPS, mạng riêng ảo VPN IPsec/SSL và kiến trúc Zero Trust',
    credits = 3,
    "departmentId" = 'department-demo',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE id = 'course-auto-037';
UPDATE academic."Course" SET
    code = 'NET038',
    name = 'Điều tra số và Ứng cứu sự cố an ninh mạng',
    "nameVi" = 'Điều tra số và Ứng cứu sự cố an ninh mạng',
    "nameEn" = 'Digital Forensics & Incident Response',
    description = 'Thu thập chứng cứ số, phân tích bộ nhớ RAM, phân tích nhật ký log hệ thống, truy vết mã độc và quy trình ứng cứu sự cố SOC',
    "descriptionVi" = 'Thu thập chứng cứ số, phân tích bộ nhớ RAM, phân tích nhật ký log hệ thống, truy vết mã độc và quy trình ứng cứu sự cố SOC',
    "descriptionEn" = 'Thu thập chứng cứ số, phân tích bộ nhớ RAM, phân tích nhật ký log hệ thống, truy vết mã độc và quy trình ứng cứu sự cố SOC',
    credits = 3,
    "departmentId" = 'department-demo',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE id = 'course-auto-038';
UPDATE academic."Course" SET
    code = 'NET039',
    name = 'Kiểm thử xâm nhập và Đánh giá an ninh mạng',
    "nameVi" = 'Kiểm thử xâm nhập và Đánh giá an ninh mạng',
    "nameEn" = 'Penetration Testing & Ethical Hacking',
    description = 'Kỹ thuật tấn công đạo đức, khai thác lỗ hổng mạng và ứng dụng bằng Metasploit, Burp Suite, Nmap và lập báo cáo khắc phục bảo mật',
    "descriptionVi" = 'Kỹ thuật tấn công đạo đức, khai thác lỗ hổng mạng và ứng dụng bằng Metasploit, Burp Suite, Nmap và lập báo cáo khắc phục bảo mật',
    "descriptionEn" = 'Kỹ thuật tấn công đạo đức, khai thác lỗ hổng mạng và ứng dụng bằng Metasploit, Burp Suite, Nmap và lập báo cáo khắc phục bảo mật',
    credits = 4,
    "departmentId" = 'department-demo',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE id = 'course-auto-039';
UPDATE academic."Course" SET
    code = 'NET040',
    name = 'Mạng điều khiển bằng phần mềm SDN & Ảo hóa',
    "nameVi" = 'Mạng điều khiển bằng phần mềm SDN & Ảo hóa',
    "nameEn" = 'Software-Defined Networking & Network Virtualization',
    description = 'Kiến trúc mạng SDN, giao thức OpenFlow, ảo hóa chức năng mạng NFV và tự động hóa cấu hình mạng bằng Ansible/Python',
    "descriptionVi" = 'Kiến trúc mạng SDN, giao thức OpenFlow, ảo hóa chức năng mạng NFV và tự động hóa cấu hình mạng bằng Ansible/Python',
    "descriptionEn" = 'Kiến trúc mạng SDN, giao thức OpenFlow, ảo hóa chức năng mạng NFV và tự động hóa cấu hình mạng bằng Ansible/Python',
    credits = 3,
    "departmentId" = 'department-demo',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE id = 'course-auto-040';
UPDATE academic."Course" SET
    code = 'NET041',
    name = 'Mật mã học ứng dụng và Bảo mật dữ liệu',
    "nameVi" = 'Mật mã học ứng dụng và Bảo mật dữ liệu',
    "nameEn" = 'Applied Cryptography & Data Protection',
    description = 'Mã hóa đối xứng AES, bất đối xứng RSA/ECC, hàm băm SHA, chữ ký số điện tử PKI và bảo vệ dữ liệu theo tiêu chuẩn quốc tế',
    "descriptionVi" = 'Mã hóa đối xứng AES, bất đối xứng RSA/ECC, hàm băm SHA, chữ ký số điện tử PKI và bảo vệ dữ liệu theo tiêu chuẩn quốc tế',
    "descriptionEn" = 'Mã hóa đối xứng AES, bất đối xứng RSA/ECC, hàm băm SHA, chữ ký số điện tử PKI và bảo vệ dữ liệu theo tiêu chuẩn quốc tế',
    credits = 3,
    "departmentId" = 'department-demo',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE id = 'course-auto-041';
UPDATE academic."Course" SET
    code = 'NET042',
    name = 'An ninh hệ điều hành Linux và Máy chủ dịch vụ',
    "nameVi" = 'An ninh hệ điều hành Linux và Máy chủ dịch vụ',
    "nameEn" = 'Linux OS Hardening & Enterprise Server Security',
    description = 'Quản trị Linux nâng cao, thiết lập SELinux/AppArmor, quản lý quyền hạn PAM, bảo mật dịch vụ Web Nginx/Apache và SSH hardening',
    "descriptionVi" = 'Quản trị Linux nâng cao, thiết lập SELinux/AppArmor, quản lý quyền hạn PAM, bảo mật dịch vụ Web Nginx/Apache và SSH hardening',
    "descriptionEn" = 'Quản trị Linux nâng cao, thiết lập SELinux/AppArmor, quản lý quyền hạn PAM, bảo mật dịch vụ Web Nginx/Apache và SSH hardening',
    credits = 3,
    "departmentId" = 'department-demo',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE id = 'course-auto-042';
UPDATE academic."Course" SET
    code = 'IS043',
    name = 'Quản trị cơ sở dữ liệu lớn Oracle & PostgreSQL',
    "nameVi" = 'Quản trị cơ sở dữ liệu lớn Oracle & PostgreSQL',
    "nameEn" = 'Enterprise Database Administration (PostgreSQL & Oracle)',
    description = 'Tối ưu hóa chỉ mục Index, điều chỉnh hiệu năng truy vấn Execution Plan, sao lưu dự phòng PITR, phân mảnh Partitioning và Replication HA',
    "descriptionVi" = 'Tối ưu hóa chỉ mục Index, điều chỉnh hiệu năng truy vấn Execution Plan, sao lưu dự phòng PITR, phân mảnh Partitioning và Replication HA',
    "descriptionEn" = 'Tối ưu hóa chỉ mục Index, điều chỉnh hiệu năng truy vấn Execution Plan, sao lưu dự phòng PITR, phân mảnh Partitioning và Replication HA',
    credits = 3,
    "departmentId" = 'department-demo',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE id = 'course-auto-043';
UPDATE academic."Course" SET
    code = 'IS044',
    name = 'Kho dữ liệu và Hệ thống hỗ trợ ra quyết định',
    "nameVi" = 'Kho dữ liệu và Hệ thống hỗ trợ ra quyết định',
    "nameEn" = 'Data Warehousing & Decision Support Systems',
    description = 'Mô hình hóa dữ liệu đa chiều Star/Snowflake Schema, quy trình trích xuất chuyển đổi nạp ETL/ELT, OLAP cubes và báo cáo BI',
    "descriptionVi" = 'Mô hình hóa dữ liệu đa chiều Star/Snowflake Schema, quy trình trích xuất chuyển đổi nạp ETL/ELT, OLAP cubes và báo cáo BI',
    "descriptionEn" = 'Mô hình hóa dữ liệu đa chiều Star/Snowflake Schema, quy trình trích xuất chuyển đổi nạp ETL/ELT, OLAP cubes và báo cáo BI',
    credits = 3,
    "departmentId" = 'department-demo',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE id = 'course-auto-044';
UPDATE academic."Course" SET
    code = 'IS045',
    name = 'Phân tích và Thiết kế hệ thống thông tin',
    "nameVi" = 'Phân tích và Thiết kế hệ thống thông tin',
    "nameEn" = 'Information Systems Analysis & Design',
    description = 'Phương pháp luận phân tích hệ thống hướng đối tượng, mô hình hóa dữ liệu DFD, ERD, giao diện tương tác và kiến trúc phần mềm doanh nghiệp',
    "descriptionVi" = 'Phương pháp luận phân tích hệ thống hướng đối tượng, mô hình hóa dữ liệu DFD, ERD, giao diện tương tác và kiến trúc phần mềm doanh nghiệp',
    "descriptionEn" = 'Phương pháp luận phân tích hệ thống hướng đối tượng, mô hình hóa dữ liệu DFD, ERD, giao diện tương tác và kiến trúc phần mềm doanh nghiệp',
    credits = 3,
    "departmentId" = 'department-demo',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE id = 'course-auto-045';
UPDATE academic."Course" SET
    code = 'IS046',
    name = 'Hệ thống hoạch định nguồn lực doanh nghiệp ERP',
    "nameVi" = 'Hệ thống hoạch định nguồn lực doanh nghiệp ERP',
    "nameEn" = 'Enterprise Resource Planning (ERP) Systems',
    description = 'Nguyên lý vận hành các phân hệ ERP (Tài chính, Bán hàng, Kho vận, Nhân sự) trên nền tảng Odoo và SAP ERP, quy trình tích hợp hệ thống',
    "descriptionVi" = 'Nguyên lý vận hành các phân hệ ERP (Tài chính, Bán hàng, Kho vận, Nhân sự) trên nền tảng Odoo và SAP ERP, quy trình tích hợp hệ thống',
    "descriptionEn" = 'Nguyên lý vận hành các phân hệ ERP (Tài chính, Bán hàng, Kho vận, Nhân sự) trên nền tảng Odoo và SAP ERP, quy trình tích hợp hệ thống',
    credits = 3,
    "departmentId" = 'department-demo',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE id = 'course-auto-046';
UPDATE academic."Course" SET
    code = 'IS047',
    name = 'Quản trị quy trình nghiệp vụ BPMN',
    "nameVi" = 'Quản trị quy trình nghiệp vụ BPMN',
    "nameEn" = 'Business Process Management & Workflow Automation',
    description = 'Thiết kế và chuẩn hóa quy trình doanh nghiệp bằng BPMN 2.0, tự động hóa luồng phê duyệt công việc bằng Camunda Workflow Engine',
    "descriptionVi" = 'Thiết kế và chuẩn hóa quy trình doanh nghiệp bằng BPMN 2.0, tự động hóa luồng phê duyệt công việc bằng Camunda Workflow Engine',
    "descriptionEn" = 'Thiết kế và chuẩn hóa quy trình doanh nghiệp bằng BPMN 2.0, tự động hóa luồng phê duyệt công việc bằng Camunda Workflow Engine',
    credits = 3,
    "departmentId" = 'department-demo',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE id = 'course-auto-047';
UPDATE academic."Course" SET
    code = 'EE048',
    name = 'Kỹ thuật vi điều khiển và Hệ thống nhúng ARM',
    "nameVi" = 'Kỹ thuật vi điều khiển và Hệ thống nhúng ARM',
    "nameEn" = 'Microcontrollers & ARM Embedded Systems',
    description = 'Lập trình C cho vi điều khiển STM32/ARM Cortex-M, giao tiếp ngoại vi I2C/SPI/UART, xử lý ngắt và quản lý tài nguyên hệ điều hành FreeRTOS',
    "descriptionVi" = 'Lập trình C cho vi điều khiển STM32/ARM Cortex-M, giao tiếp ngoại vi I2C/SPI/UART, xử lý ngắt và quản lý tài nguyên hệ điều hành FreeRTOS',
    "descriptionEn" = 'Lập trình C cho vi điều khiển STM32/ARM Cortex-M, giao tiếp ngoại vi I2C/SPI/UART, xử lý ngắt và quản lý tài nguyên hệ điều hành FreeRTOS',
    credits = 4,
    "departmentId" = 'department-feee-auto',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE id = 'course-auto-048';
UPDATE academic."Course" SET
    code = 'EE049',
    name = 'Thiết kế mạch tích hợp VLSI với Verilog',
    "nameVi" = 'Thiết kế mạch tích hợp VLSI với Verilog',
    "nameEn" = 'VLSI Integrated Circuit Design with Verilog',
    description = 'Thiết kế logic số bằng ngôn ngữ mô tả phần cứng Verilog HDL, tổng hợp mạch trên chip FPGA và quy trình thiết kế vi mạch chuyên dụng ASIC',
    "descriptionVi" = 'Thiết kế logic số bằng ngôn ngữ mô tả phần cứng Verilog HDL, tổng hợp mạch trên chip FPGA và quy trình thiết kế vi mạch chuyên dụng ASIC',
    "descriptionEn" = 'Thiết kế logic số bằng ngôn ngữ mô tả phần cứng Verilog HDL, tổng hợp mạch trên chip FPGA và quy trình thiết kế vi mạch chuyên dụng ASIC',
    credits = 3,
    "departmentId" = 'department-feee-telecom',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE id = 'course-auto-049';
UPDATE academic."Course" SET
    code = 'EE050',
    name = 'Lập trình PLC và Điều khiển SCADA công nghiệp',
    "nameVi" = 'Lập trình PLC và Điều khiển SCADA công nghiệp',
    "nameEn" = 'Industrial PLC Programming & SCADA Systems',
    description = 'Lập trình điều khiển logic PLC Siemens S7-1200/1500, kết nối mạng Profinet, thiết kế giao diện vận hành HMI và hệ thống giám sát SCADA WinCC',
    "descriptionVi" = 'Lập trình điều khiển logic PLC Siemens S7-1200/1500, kết nối mạng Profinet, thiết kế giao diện vận hành HMI và hệ thống giám sát SCADA WinCC',
    "descriptionEn" = 'Lập trình điều khiển logic PLC Siemens S7-1200/1500, kết nối mạng Profinet, thiết kế giao diện vận hành HMI và hệ thống giám sát SCADA WinCC',
    credits = 4,
    "departmentId" = 'department-feee-auto',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE id = 'course-auto-050';
UPDATE academic."Course" SET
    code = 'EE051',
    name = 'Internet vạn vật IoT và Mạng cảm biến không dây',
    "nameVi" = 'Internet vạn vật IoT và Mạng cảm biến không dây',
    "nameEn" = 'Internet of Things & Wireless Sensor Networks',
    description = 'Kiến trúc hệ thống IoT, giao thức truyền thông MQTT/CoAP, mạng không dây diện rộng LoRaWAN, BLE, Zigbee và kết nối nền tảng đám mây AWS IoT',
    "descriptionVi" = 'Kiến trúc hệ thống IoT, giao thức truyền thông MQTT/CoAP, mạng không dây diện rộng LoRaWAN, BLE, Zigbee và kết nối nền tảng đám mây AWS IoT',
    "descriptionEn" = 'Kiến trúc hệ thống IoT, giao thức truyền thông MQTT/CoAP, mạng không dây diện rộng LoRaWAN, BLE, Zigbee và kết nối nền tảng đám mây AWS IoT',
    credits = 3,
    "departmentId" = 'department-feee-telecom',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE id = 'course-auto-051';
UPDATE academic."Course" SET
    code = 'EE052',
    name = 'Xử lý tín hiệu số DSP',
    "nameVi" = 'Xử lý tín hiệu số DSP',
    "nameEn" = 'Digital Signal Processing (DSP)',
    description = 'Biến đổi Fourier rời rạc DFT/FFT, thiết kế bộ lọc số FIR/IIR, xử lý tín hiệu âm thanh và ứng dụng trên chip chuyên dụng DSP',
    "descriptionVi" = 'Biến đổi Fourier rời rạc DFT/FFT, thiết kế bộ lọc số FIR/IIR, xử lý tín hiệu âm thanh và ứng dụng trên chip chuyên dụng DSP',
    "descriptionEn" = 'Biến đổi Fourier rời rạc DFT/FFT, thiết kế bộ lọc số FIR/IIR, xử lý tín hiệu âm thanh và ứng dụng trên chip chuyên dụng DSP',
    credits = 3,
    "departmentId" = 'department-feee-telecom',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE id = 'course-auto-052';
UPDATE academic."Course" SET
    code = 'EE053',
    name = 'Điện tử công suất và Bộ biến đổi điện tử',
    "nameVi" = 'Điện tử công suất và Bộ biến đổi điện tử',
    "nameEn" = 'Power Electronics & Electronic Power Converters',
    description = 'Nguyên lý các bộ nghịch lưu Inverter, biến đổi DC-DC Buck/Boost, điều chế độ rộng xung PWM và ứng dụng trong điều khiển động cơ',
    "descriptionVi" = 'Nguyên lý các bộ nghịch lưu Inverter, biến đổi DC-DC Buck/Boost, điều chế độ rộng xung PWM và ứng dụng trong điều khiển động cơ',
    "descriptionEn" = 'Nguyên lý các bộ nghịch lưu Inverter, biến đổi DC-DC Buck/Boost, điều chế độ rộng xung PWM và ứng dụng trong điều khiển động cơ',
    credits = 3,
    "departmentId" = 'department-feee-auto',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE id = 'course-auto-053';
UPDATE academic."Course" SET
    code = 'EE054',
    name = 'Năng lượng tái tạo và Lưới điện thông minh',
    "nameVi" = 'Năng lượng tái tạo và Lưới điện thông minh',
    "nameEn" = 'Renewable Energy & Smart Grid Systems',
    description = 'Hệ thống điện mặt trời áp mái, tuabin gió, hệ thống lưu trữ năng lượng pin BESS, hòa lưới điện và điều khiển giám sát vi lưới Microgrid',
    "descriptionVi" = 'Hệ thống điện mặt trời áp mái, tuabin gió, hệ thống lưu trữ năng lượng pin BESS, hòa lưới điện và điều khiển giám sát vi lưới Microgrid',
    "descriptionEn" = 'Hệ thống điện mặt trời áp mái, tuabin gió, hệ thống lưu trữ năng lượng pin BESS, hòa lưới điện và điều khiển giám sát vi lưới Microgrid',
    credits = 3,
    "departmentId" = 'department-feee-auto',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE id = 'course-auto-054';
UPDATE academic."Course" SET
    code = 'EE055',
    name = 'Hệ thống viễn thông 5G và Mạng di động',
    "nameVi" = 'Hệ thống viễn thông 5G và Mạng di động',
    "nameEn" = '5G Telecommunications & Mobile Cellular Networks',
    description = 'Công nghệ truyền thông băng rộng 5G NR, đa truy nhập Massive MIMO, phân lát mạng Network Slicing và quy hoạch vùng phủ sóng trạm BTS',
    "descriptionVi" = 'Công nghệ truyền thông băng rộng 5G NR, đa truy nhập Massive MIMO, phân lát mạng Network Slicing và quy hoạch vùng phủ sóng trạm BTS',
    "descriptionEn" = 'Công nghệ truyền thông băng rộng 5G NR, đa truy nhập Massive MIMO, phân lát mạng Network Slicing và quy hoạch vùng phủ sóng trạm BTS',
    credits = 3,
    "departmentId" = 'department-feee-telecom',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE id = 'course-auto-055';
UPDATE academic."Course" SET
    code = 'EE056',
    name = 'Kỹ thuật đo lường và Cảm biến công nghiệp',
    "nameVi" = 'Kỹ thuật đo lường và Cảm biến công nghiệp',
    "nameEn" = 'Industrial Instrumentation & Sensor Technology',
    description = 'Cảm biến đo lường nhiệt độ, áp suất, lưu lượng, quang học, mạch khuếch đại chuẩn hóa tín hiệu công nghiệp 4-20mA và chuẩn RS-485 Modbus',
    "descriptionVi" = 'Cảm biến đo lường nhiệt độ, áp suất, lưu lượng, quang học, mạch khuếch đại chuẩn hóa tín hiệu công nghiệp 4-20mA và chuẩn RS-485 Modbus',
    "descriptionEn" = 'Cảm biến đo lường nhiệt độ, áp suất, lưu lượng, quang học, mạch khuếch đại chuẩn hóa tín hiệu công nghiệp 4-20mA và chuẩn RS-485 Modbus',
    credits = 3,
    "departmentId" = 'department-feee-auto',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE id = 'course-auto-056';
UPDATE academic."Course" SET
    code = 'EE057',
    name = 'Hệ thống truyền động điện tự động hóa',
    "nameVi" = 'Hệ thống truyền động điện tự động hóa',
    "nameEn" = 'Automated Electric Drive Systems',
    description = 'Điều khiển vector từ thông FOC cho động cơ xoay chiều, động cơ bước Stepper, động cơ Servo công nghiệp và biến tần đa năng',
    "descriptionVi" = 'Điều khiển vector từ thông FOC cho động cơ xoay chiều, động cơ bước Stepper, động cơ Servo công nghiệp và biến tần đa năng',
    "descriptionEn" = 'Điều khiển vector từ thông FOC cho động cơ xoay chiều, động cơ bước Stepper, động cơ Servo công nghiệp và biến tần đa năng',
    credits = 3,
    "departmentId" = 'department-feee-auto',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE id = 'course-auto-057';
UPDATE academic."Course" SET
    code = 'ME058',
    name = 'Robot công nghiệp và Cánh tay máy tự động',
    "nameVi" = 'Robot công nghiệp và Cánh tay máy tự động',
    "nameEn" = 'Industrial Robotics & Automated Manipulators',
    description = 'Động học thuận nghịch cánh tay robot Denavit-Hartenberg, quy hoạch quỹ đạo chuyển động, lập trình robot công nghiệp ABB/KUKA và an toàn tự động',
    "descriptionVi" = 'Động học thuận nghịch cánh tay robot Denavit-Hartenberg, quy hoạch quỹ đạo chuyển động, lập trình robot công nghiệp ABB/KUKA và an toàn tự động',
    "descriptionEn" = 'Động học thuận nghịch cánh tay robot Denavit-Hartenberg, quy hoạch quỹ đạo chuyển động, lập trình robot công nghiệp ABB/KUKA và an toàn tự động',
    credits = 4,
    "departmentId" = 'department-fme-robotics',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE id = 'course-auto-058';
UPDATE academic."Course" SET
    code = 'ME059',
    name = 'Hệ điều hành Robot ROS 2 và Xe tự hành AGV',
    "nameVi" = 'Hệ điều hành Robot ROS 2 và Xe tự hành AGV',
    "nameEn" = 'Robot Operating System (ROS 2) & Autonomous AGVs',
    description = 'Lập trình ROS 2 nodes, định vị và lập bản đồ đồng thời SLAM, dẫn đường điều hướng xe tự hành AGV và cảm biến LiDAR/Camera',
    "descriptionVi" = 'Lập trình ROS 2 nodes, định vị và lập bản đồ đồng thời SLAM, dẫn đường điều hướng xe tự hành AGV và cảm biến LiDAR/Camera',
    "descriptionEn" = 'Lập trình ROS 2 nodes, định vị và lập bản đồ đồng thời SLAM, dẫn đường điều hướng xe tự hành AGV và cảm biến LiDAR/Camera',
    credits = 4,
    "departmentId" = 'department-fme-robotics',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE id = 'course-auto-059';
UPDATE academic."Course" SET
    code = 'ME060',
    name = 'Thiết kế cơ khí chính xác với SolidWorks & AutoCAD',
    "nameVi" = 'Thiết kế cơ khí chính xác với SolidWorks & AutoCAD',
    "nameEn" = 'Precision Mechanical Design with CAD',
    description = 'Mô hình hóa chi tiết máy 3D, thiết kế cụm lắp ráp cơ cấu, xuất bản vẽ kỹ thuật theo tiêu chuẩn ISO và phân tích dung sai kích thước',
    "descriptionVi" = 'Mô hình hóa chi tiết máy 3D, thiết kế cụm lắp ráp cơ cấu, xuất bản vẽ kỹ thuật theo tiêu chuẩn ISO và phân tích dung sai kích thước',
    "descriptionEn" = 'Mô hình hóa chi tiết máy 3D, thiết kế cụm lắp ráp cơ cấu, xuất bản vẽ kỹ thuật theo tiêu chuẩn ISO và phân tích dung sai kích thước',
    credits = 3,
    "departmentId" = 'department-fme-robotics',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE id = 'course-auto-060';
UPDATE academic."Course" SET
    code = 'ME061',
    name = 'Gia công CNC 5 trục và Công nghệ CAM nâng cao',
    "nameVi" = 'Gia công CNC 5 trục và Công nghệ CAM nâng cao',
    "nameEn" = '5-Axis CNC Machining & Advanced CAM',
    description = 'Lập trình gia công chi tiết phức tạp trên máy phay/tiện CNC 5 trục bằng Mastercam, tối ưu hóa đường chạy dao và mô phỏng va chạm',
    "descriptionVi" = 'Lập trình gia công chi tiết phức tạp trên máy phay/tiện CNC 5 trục bằng Mastercam, tối ưu hóa đường chạy dao và mô phỏng va chạm',
    "descriptionEn" = 'Lập trình gia công chi tiết phức tạp trên máy phay/tiện CNC 5 trục bằng Mastercam, tối ưu hóa đường chạy dao và mô phỏng va chạm',
    credits = 4,
    "departmentId" = 'department-fme-robotics',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE id = 'course-auto-061';
UPDATE academic."Course" SET
    code = 'ME062',
    name = 'Động lực học ô tô và Hệ thống an toàn chủ động',
    "nameVi" = 'Động lực học ô tô và Hệ thống an toàn chủ động',
    "nameEn" = 'Automotive Dynamics & Active Safety Systems',
    description = 'Mô hình hóa chuyển động của xe, hệ thống phanh chống bó cứng ABS, kiểm soát cân bằng điện tử ESP và hệ thống treo thông minh',
    "descriptionVi" = 'Mô hình hóa chuyển động của xe, hệ thống phanh chống bó cứng ABS, kiểm soát cân bằng điện tử ESP và hệ thống treo thông minh',
    "descriptionEn" = 'Mô hình hóa chuyển động của xe, hệ thống phanh chống bó cứng ABS, kiểm soát cân bằng điện tử ESP và hệ thống treo thông minh',
    credits = 3,
    "departmentId" = 'department-fme-automotive',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE id = 'course-auto-062';
UPDATE academic."Course" SET
    code = 'ME063',
    name = 'Công nghệ Ô tô điện (EV) và Pin Lithium-ion',
    "nameVi" = 'Công nghệ Ô tô điện (EV) và Pin Lithium-ion',
    "nameEn" = 'Electric Vehicle (EV) Technology & Battery Systems',
    description = 'Cấu trúc xe điện thuần EV, công nghệ pin Lithium-ion, hệ thống quản lý pin BMS, hệ thống trạm sạc nhanh DC và phanh tái sinh năng lượng',
    "descriptionVi" = 'Cấu trúc xe điện thuần EV, công nghệ pin Lithium-ion, hệ thống quản lý pin BMS, hệ thống trạm sạc nhanh DC và phanh tái sinh năng lượng',
    "descriptionEn" = 'Cấu trúc xe điện thuần EV, công nghệ pin Lithium-ion, hệ thống quản lý pin BMS, hệ thống trạm sạc nhanh DC và phanh tái sinh năng lượng',
    credits = 4,
    "departmentId" = 'department-fme-automotive',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE id = 'course-auto-063';
UPDATE academic."Course" SET
    code = 'ME064',
    name = 'Hệ thống điện - điện tử trên ô tô hiện đại',
    "nameVi" = 'Hệ thống điện - điện tử trên ô tô hiện đại',
    "nameEn" = 'Modern Automotive Electrical & Electronic Systems',
    description = 'Mạng truyền thông trên xe ô tô CAN Bus, LIN, FlexRay, hệ thống đánh lửa, phun xăng điện tử EFI và điều khiển tiện nghi thân xe BCM',
    "descriptionVi" = 'Mạng truyền thông trên xe ô tô CAN Bus, LIN, FlexRay, hệ thống đánh lửa, phun xăng điện tử EFI và điều khiển tiện nghi thân xe BCM',
    "descriptionEn" = 'Mạng truyền thông trên xe ô tô CAN Bus, LIN, FlexRay, hệ thống đánh lửa, phun xăng điện tử EFI và điều khiển tiện nghi thân xe BCM',
    credits = 3,
    "departmentId" = 'department-fme-automotive',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE id = 'course-auto-064';
UPDATE academic."Course" SET
    code = 'ME065',
    name = 'Chẩn đoán kỹ thuật ô tô bằng máy quét chuyên dụng',
    "nameVi" = 'Chẩn đoán kỹ thuật ô tô bằng máy quét chuyên dụng',
    "nameEn" = 'Automotive Diagnostic Systems & OBD-II',
    description = 'Quy trình chẩn đoán mã lỗi OBD-II/UDS, phân tích dữ liệu cảm biến thời gian thực, kiểm tra dao động ký oscilloscope và xử lý pan kỹ thuật',
    "descriptionVi" = 'Quy trình chẩn đoán mã lỗi OBD-II/UDS, phân tích dữ liệu cảm biến thời gian thực, kiểm tra dao động ký oscilloscope và xử lý pan kỹ thuật',
    "descriptionEn" = 'Quy trình chẩn đoán mã lỗi OBD-II/UDS, phân tích dữ liệu cảm biến thời gian thực, kiểm tra dao động ký oscilloscope và xử lý pan kỹ thuật',
    credits = 3,
    "departmentId" = 'department-fme-automotive',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE id = 'course-auto-065';
UPDATE academic."Course" SET
    code = 'ME066',
    name = 'Mô phỏng động lực học ANSYS & Phần tử hữu hạn',
    "nameVi" = 'Mô phỏng động lực học ANSYS & Phần tử hữu hạn',
    "nameEn" = 'ANSYS FEA Dynamics Simulation',
    description = 'Phương pháp phần tử hữu hạn FEA, phân tích ứng suất, biến dạng cơ học, độ bền mỏi và mô phỏng nhiệt trên phần mềm ANSYS Workbench',
    "descriptionVi" = 'Phương pháp phần tử hữu hạn FEA, phân tích ứng suất, biến dạng cơ học, độ bền mỏi và mô phỏng nhiệt trên phần mềm ANSYS Workbench',
    "descriptionEn" = 'Phương pháp phần tử hữu hạn FEA, phân tích ứng suất, biến dạng cơ học, độ bền mỏi và mô phỏng nhiệt trên phần mềm ANSYS Workbench',
    credits = 3,
    "departmentId" = 'department-fme-robotics',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE id = 'course-auto-066';
UPDATE academic."Course" SET
    code = 'ME067',
    name = 'Khí nén và Thủy lực công nghiệp ứng dụng',
    "nameVi" = 'Khí nén và Thủy lực công nghiệp ứng dụng',
    "nameEn" = 'Applied Industrial Pneumatics & Hydraulics',
    description = 'Thiết kế mạch điều khiển khí nén - điện khí nén, bơm và van điều áp thủy lực, xi lanh truyền lực và bảo dưỡng hệ thống công nghiệp',
    "descriptionVi" = 'Thiết kế mạch điều khiển khí nén - điện khí nén, bơm và van điều áp thủy lực, xi lanh truyền lực và bảo dưỡng hệ thống công nghiệp',
    "descriptionEn" = 'Thiết kế mạch điều khiển khí nén - điện khí nén, bơm và van điều áp thủy lực, xi lanh truyền lực và bảo dưỡng hệ thống công nghiệp',
    credits = 3,
    "departmentId" = 'department-fme-robotics',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE id = 'course-auto-067';
UPDATE academic."Course" SET
    code = 'EC068',
    name = 'Phân tích dữ liệu kinh doanh với PowerBI',
    "nameVi" = 'Phân tích dữ liệu kinh doanh với PowerBI',
    "nameEn" = 'Business Intelligence & Analytics with PowerBI',
    description = 'Xây dựng dashboard chỉ số kinh doanh KPI, mô hình hóa DAX, kết nối nguồn dữ liệu đa dạng và phân tích xu hướng doanh thu',
    "descriptionVi" = 'Xây dựng dashboard chỉ số kinh doanh KPI, mô hình hóa DAX, kết nối nguồn dữ liệu đa dạng và phân tích xu hướng doanh thu',
    "descriptionEn" = 'Xây dựng dashboard chỉ số kinh doanh KPI, mô hình hóa DAX, kết nối nguồn dữ liệu đa dạng và phân tích xu hướng doanh thu',
    credits = 3,
    "departmentId" = 'department-foe-mis',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE id = 'course-auto-068';
UPDATE academic."Course" SET
    code = 'EC069',
    name = 'Quản trị chuỗi cung ứng toàn cầu & Logistics 4.0',
    "nameVi" = 'Quản trị chuỗi cung ứng toàn cầu & Logistics 4.0',
    "nameEn" = 'Global Supply Chain Management & Logistics 4.0',
    description = 'Hoạch định nhu cầu thị trường, quản trị chuỗi cung ứng số, tối ưu hóa mạng lưới phân phối và công nghệ truy vết RFID/IoT trong logistics',
    "descriptionVi" = 'Hoạch định nhu cầu thị trường, quản trị chuỗi cung ứng số, tối ưu hóa mạng lưới phân phối và công nghệ truy vết RFID/IoT trong logistics',
    "descriptionEn" = 'Hoạch định nhu cầu thị trường, quản trị chuỗi cung ứng số, tối ưu hóa mạng lưới phân phối và công nghệ truy vết RFID/IoT trong logistics',
    credits = 3,
    "departmentId" = 'department-foe-mis',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE id = 'course-auto-069';
UPDATE academic."Course" SET
    code = 'EC070',
    name = 'Công nghệ tài chính FinTech và Ngân hàng số',
    "nameVi" = 'Công nghệ tài chính FinTech và Ngân hàng số',
    "nameEn" = 'Financial Technology (FinTech) & Digital Banking',
    description = 'Kiến trúc ngân hàng mở Open Banking, cổng thanh toán trực tuyến, ví điện tử, ứng dụng AI thẩm định tín dụng và quản lý tài sản số',
    "descriptionVi" = 'Kiến trúc ngân hàng mở Open Banking, cổng thanh toán trực tuyến, ví điện tử, ứng dụng AI thẩm định tín dụng và quản lý tài sản số',
    "descriptionEn" = 'Kiến trúc ngân hàng mở Open Banking, cổng thanh toán trực tuyến, ví điện tử, ứng dụng AI thẩm định tín dụng và quản lý tài sản số',
    credits = 3,
    "departmentId" = 'department-foe-finance',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE id = 'course-auto-070';
UPDATE academic."Course" SET
    code = 'EC071',
    name = 'Thương mại điện tử và Digital Marketing đa kênh',
    "nameVi" = 'Thương mại điện tử và Digital Marketing đa kênh',
    "nameEn" = 'E-Commerce & Omnichannel Digital Marketing',
    description = 'Vận hành sàn TMĐT, chiến lược tiếp thị tìm kiếm SEO/SEM, quảng cáo số đa kênh, tối ưu hóa tỷ lệ chuyển đổi CRO và chăm sóc khách hàng tự động',
    "descriptionVi" = 'Vận hành sàn TMĐT, chiến lược tiếp thị tìm kiếm SEO/SEM, quảng cáo số đa kênh, tối ưu hóa tỷ lệ chuyển đổi CRO và chăm sóc khách hàng tự động',
    "descriptionEn" = 'Vận hành sàn TMĐT, chiến lược tiếp thị tìm kiếm SEO/SEM, quảng cáo số đa kênh, tối ưu hóa tỷ lệ chuyển đổi CRO và chăm sóc khách hàng tự động',
    credits = 3,
    "departmentId" = 'department-foe-mis',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE id = 'course-auto-071';
UPDATE academic."Course" SET
    code = 'EC072',
    name = 'Phân tích báo cáo tài chính và Định giá doanh nghiệp',
    "nameVi" = 'Phân tích báo cáo tài chính và Định giá doanh nghiệp',
    "nameEn" = 'Financial Statement Analysis & Business Valuation',
    description = 'Đọc hiểu bảng cân đối kế toán, báo cáo lưu chuyển tiền tệ, phân tích chỉ số tài chính Dupont và các phương pháp định giá chiết khấu dòng tiền DCF',
    "descriptionVi" = 'Đọc hiểu bảng cân đối kế toán, báo cáo lưu chuyển tiền tệ, phân tích chỉ số tài chính Dupont và các phương pháp định giá chiết khấu dòng tiền DCF',
    "descriptionEn" = 'Đọc hiểu bảng cân đối kế toán, báo cáo lưu chuyển tiền tệ, phân tích chỉ số tài chính Dupont và các phương pháp định giá chiết khấu dòng tiền DCF',
    credits = 3,
    "departmentId" = 'department-foe-finance',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE id = 'course-auto-072';
UPDATE academic."Course" SET
    code = 'EC073',
    name = 'Quản trị rủi ro tài chính và Đầu tư chứng khoán',
    "nameVi" = 'Quản trị rủi ro tài chính và Đầu tư chứng khoán',
    "nameEn" = 'Financial Risk Management & Securities Investment',
    description = 'Phân tích kỹ thuật và cơ bản thị trường chứng khoán, công cụ phái sinh Hedging, quản trị rủi ro thanh khoản và quản lý danh mục đầu tư',
    "descriptionVi" = 'Phân tích kỹ thuật và cơ bản thị trường chứng khoán, công cụ phái sinh Hedging, quản trị rủi ro thanh khoản và quản lý danh mục đầu tư',
    "descriptionEn" = 'Phân tích kỹ thuật và cơ bản thị trường chứng khoán, công cụ phái sinh Hedging, quản trị rủi ro thanh khoản và quản lý danh mục đầu tư',
    credits = 3,
    "departmentId" = 'department-foe-finance',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE id = 'course-auto-073';
UPDATE academic."Course" SET
    code = 'EC074',
    name = 'Quản lý kho hàng thông minh và Vận tải đa phương thức',
    "nameVi" = 'Quản lý kho hàng thông minh và Vận tải đa phương thức',
    "nameEn" = 'Smart Warehousing & Multimodal Freight Transport',
    description = 'Quy trình vận hành kho WMS, công nghệ tự động hóa lấy hàng AS/RS, đóng gói xuất khẩu và thủ tục hải quan xuất nhập khẩu quốc tế',
    "descriptionVi" = 'Quy trình vận hành kho WMS, công nghệ tự động hóa lấy hàng AS/RS, đóng gói xuất khẩu và thủ tục hải quan xuất nhập khẩu quốc tế',
    "descriptionEn" = 'Quy trình vận hành kho WMS, công nghệ tự động hóa lấy hàng AS/RS, đóng gói xuất khẩu và thủ tục hải quan xuất nhập khẩu quốc tế',
    credits = 3,
    "departmentId" = 'department-foe-mis',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE id = 'course-auto-074';
UPDATE academic."Course" SET
    code = 'EC075',
    name = 'Khởi nghiệp đổi mới sáng tạo trong thời đại số',
    "nameVi" = 'Khởi nghiệp đổi mới sáng tạo trong thời đại số',
    "nameEn" = 'Digital Tech Innovation & Entrepreneurship',
    description = 'Mô hình kinh doanh Canvas, xây dựng sản phẩm khả thi tối thiểu MVP, gọi vốn đầu tư mạo hiểm VC và bảo hộ quyền sở hữu trí tuệ khởi nghiệp',
    "descriptionVi" = 'Mô hình kinh doanh Canvas, xây dựng sản phẩm khả thi tối thiểu MVP, gọi vốn đầu tư mạo hiểm VC và bảo hộ quyền sở hữu trí tuệ khởi nghiệp',
    "descriptionEn" = 'Mô hình kinh doanh Canvas, xây dựng sản phẩm khả thi tối thiểu MVP, gọi vốn đầu tư mạo hiểm VC và bảo hộ quyền sở hữu trí tuệ khởi nghiệp',
    credits = 3,
    "departmentId" = 'department-foe-mis',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE id = 'course-auto-075';
UPDATE academic."Course" SET
    code = 'FL076',
    name = 'Tiếng Anh chuyên ngành Công nghệ thông tin',
    "nameVi" = 'Tiếng Anh chuyên ngành Công nghệ thông tin',
    "nameEn" = 'English for Information Technology',
    description = 'Từ vựng kỹ thuật phần mềm, đọc hiểu tài liệu API, viết đặc tả kỹ thuật, giao tiếp trong nhóm phát triển Scrum quốc tế và viết email chuyên nghiệp',
    "descriptionVi" = 'Từ vựng kỹ thuật phần mềm, đọc hiểu tài liệu API, viết đặc tả kỹ thuật, giao tiếp trong nhóm phát triển Scrum quốc tế và viết email chuyên nghiệp',
    "descriptionEn" = 'Từ vựng kỹ thuật phần mềm, đọc hiểu tài liệu API, viết đặc tả kỹ thuật, giao tiếp trong nhóm phát triển Scrum quốc tế và viết email chuyên nghiệp',
    credits = 3,
    "departmentId" = 'department-ffl-business',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE id = 'course-auto-076';
UPDATE academic."Course" SET
    code = 'FL077',
    name = 'Tiếng Anh giao tiếp thương mại và Đàm phán quốc tế',
    "nameVi" = 'Tiếng Anh giao tiếp thương mại và Đàm phán quốc tế',
    "nameEn" = 'Business English Communication & Negotiation',
    description = 'Kỹ năng đàm phán hợp đồng thương mại, thuyết trình kinh doanh, điều hành cuộc họp bằng tiếng Anh và văn hóa ứng xử doanh nghiệp đa quốc gia',
    "descriptionVi" = 'Kỹ năng đàm phán hợp đồng thương mại, thuyết trình kinh doanh, điều hành cuộc họp bằng tiếng Anh và văn hóa ứng xử doanh nghiệp đa quốc gia',
    "descriptionEn" = 'Kỹ năng đàm phán hợp đồng thương mại, thuyết trình kinh doanh, điều hành cuộc họp bằng tiếng Anh và văn hóa ứng xử doanh nghiệp đa quốc gia',
    credits = 3,
    "departmentId" = 'department-ffl-business',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE id = 'course-auto-077';
UPDATE academic."Course" SET
    code = 'FL078',
    name = 'Kỹ năng thuyết trình học thuật bằng tiếng Anh',
    "nameVi" = 'Kỹ năng thuyết trình học thuật bằng tiếng Anh',
    "nameEn" = 'Academic English Presentation Skills',
    description = 'Kỹ thuật thuyết trình đề tài nghiên cứu khoa học, bảo vệ khóa luận trước hội đồng quốc tế và kỹ năng phản biện tự tin bằng tiếng Anh',
    "descriptionVi" = 'Kỹ thuật thuyết trình đề tài nghiên cứu khoa học, bảo vệ khóa luận trước hội đồng quốc tế và kỹ năng phản biện tự tin bằng tiếng Anh',
    "descriptionEn" = 'Kỹ thuật thuyết trình đề tài nghiên cứu khoa học, bảo vệ khóa luận trước hội đồng quốc tế và kỹ năng phản biện tự tin bằng tiếng Anh',
    credits = 2,
    "departmentId" = 'department-ffl-business',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE id = 'course-auto-078';
UPDATE academic."Course" SET
    code = 'FL079',
    name = 'Tiếng Anh chuyên ngành Kỹ thuật Điện - Cơ khí',
    "nameVi" = 'Tiếng Anh chuyên ngành Kỹ thuật Điện - Cơ khí',
    "nameEn" = 'English for Electrical & Mechanical Engineering',
    description = 'Đọc hiểu bản vẽ kỹ thuật, hướng dẫn lắp đặt thiết bị công nghiệp, tiêu chuẩn an toàn kỹ thuật quốc tế IEEE/ISO và dịch tài liệu kỹ thuật',
    "descriptionVi" = 'Đọc hiểu bản vẽ kỹ thuật, hướng dẫn lắp đặt thiết bị công nghiệp, tiêu chuẩn an toàn kỹ thuật quốc tế IEEE/ISO và dịch tài liệu kỹ thuật',
    "descriptionEn" = 'Đọc hiểu bản vẽ kỹ thuật, hướng dẫn lắp đặt thiết bị công nghiệp, tiêu chuẩn an toàn kỹ thuật quốc tế IEEE/ISO và dịch tài liệu kỹ thuật',
    credits = 3,
    "departmentId" = 'department-ffl-business',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE id = 'course-auto-079';
UPDATE academic."Course" SET
    code = 'FL080',
    name = 'Luyện thi TOEIC 650+ chuẩn đầu ra tốt nghiệp',
    "nameVi" = 'Luyện thi TOEIC 650+ chuẩn đầu ra tốt nghiệp',
    "nameEn" = 'Intensive TOEIC 650+ Graduation Benchmark',
    description = 'Chiến thuật làm bài thi TOEIC Listening & Reading, ngữ pháp nâng cao, luyện đề chuẩn ETS và cam kết đạt chuẩn đầu ra tốt nghiệp đại học',
    "descriptionVi" = 'Chiến thuật làm bài thi TOEIC Listening & Reading, ngữ pháp nâng cao, luyện đề chuẩn ETS và cam kết đạt chuẩn đầu ra tốt nghiệp đại học',
    "descriptionEn" = 'Chiến thuật làm bài thi TOEIC Listening & Reading, ngữ pháp nâng cao, luyện đề chuẩn ETS và cam kết đạt chuẩn đầu ra tốt nghiệp đại học',
    credits = 3,
    "departmentId" = 'department-ffl-business',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE id = 'course-auto-080';
UPDATE academic."Course" SET
    code = 'CE081',
    name = 'Mô hình thông tin công trình BIM với Revit',
    "nameVi" = 'Mô hình thông tin công trình BIM với Revit',
    "nameEn" = 'Building Information Modeling (BIM) with Revit',
    description = 'Dựng mô hình kiến trúc và kết cấu 3D trên Autodesk Revit, phối hợp đa bộ môn Navisworks, phát hiện xung đột và bóc tách khối lượng tự động',
    "descriptionVi" = 'Dựng mô hình kiến trúc và kết cấu 3D trên Autodesk Revit, phối hợp đa bộ môn Navisworks, phát hiện xung đột và bóc tách khối lượng tự động',
    "descriptionEn" = 'Dựng mô hình kiến trúc và kết cấu 3D trên Autodesk Revit, phối hợp đa bộ môn Navisworks, phát hiện xung đột và bóc tách khối lượng tự động',
    credits = 4,
    "departmentId" = 'department-fce-civil',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE id = 'course-auto-081';
UPDATE academic."Course" SET
    code = 'CE082',
    name = 'Kết cấu bê tông cốt thép nâng cao & Ứng lực trước',
    "nameVi" = 'Kết cấu bê tông cốt thép nâng cao & Ứng lực trước',
    "nameEn" = 'Advanced Reinforced & Prestressed Concrete',
    description = 'Tính toán thiết kế dầm, sàn không dầm, cột chịu nén lệch tâm, công nghệ bê tông ứng lực trước cho công trình nhịp lớn và nhà cao tầng',
    "descriptionVi" = 'Tính toán thiết kế dầm, sàn không dầm, cột chịu nén lệch tâm, công nghệ bê tông ứng lực trước cho công trình nhịp lớn và nhà cao tầng',
    "descriptionEn" = 'Tính toán thiết kế dầm, sàn không dầm, cột chịu nén lệch tâm, công nghệ bê tông ứng lực trước cho công trình nhịp lớn và nhà cao tầng',
    credits = 4,
    "departmentId" = 'department-fce-civil',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE id = 'course-auto-082';
UPDATE academic."Course" SET
    code = 'CE083',
    name = 'Cơ học đất và Kỹ thuật nền móng công trình cao tầng',
    "nameVi" = 'Cơ học đất và Kỹ thuật nền móng công trình cao tầng',
    "nameEn" = 'Soil Mechanics & Tall Building Foundation Engineering',
    description = 'Độ lún và sức chịu tải của đất nền, thiết kế móng cọc khoan nhồi sâu, tường vây ngầm tầng hầm và giải pháp xử lý nền đất yếu',
    "descriptionVi" = 'Độ lún và sức chịu tải của đất nền, thiết kế móng cọc khoan nhồi sâu, tường vây ngầm tầng hầm và giải pháp xử lý nền đất yếu',
    "descriptionEn" = 'Độ lún và sức chịu tải của đất nền, thiết kế móng cọc khoan nhồi sâu, tường vây ngầm tầng hầm và giải pháp xử lý nền đất yếu',
    credits = 3,
    "departmentId" = 'department-fce-civil',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE id = 'course-auto-083';
UPDATE academic."Course" SET
    code = 'CE084',
    name = 'Quản lý dự án xây dựng và Dự toán công trình',
    "nameVi" = 'Quản lý dự án xây dựng và Dự toán công trình',
    "nameEn" = 'Construction Project Management & Cost Estimation',
    description = 'Lập tiến độ thi công bằng Primavera/MS Project, quản lý chi phí dự toán, định mức đơn giá xây dựng và quy trình nghiệm thu thanh quyết toán',
    "descriptionVi" = 'Lập tiến độ thi công bằng Primavera/MS Project, quản lý chi phí dự toán, định mức đơn giá xây dựng và quy trình nghiệm thu thanh quyết toán',
    "descriptionEn" = 'Lập tiến độ thi công bằng Primavera/MS Project, quản lý chi phí dự toán, định mức đơn giá xây dựng và quy trình nghiệm thu thanh quyết toán',
    credits = 3,
    "departmentId" = 'department-fce-civil',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE id = 'course-auto-084';
UPDATE academic."Course" SET
    code = 'CE085',
    name = 'Kết cấu thép và Nhà công nghiệp nhịp lớn',
    "nameVi" = 'Kết cấu thép và Nhà công nghiệp nhịp lớn',
    "nameEn" = 'Structural Steel & Industrial Long-Span Design',
    description = 'Tính toán khung thép tiền chế Zamil, liên kết bu lông chịu lực cao, mối hàn công nghiệp và tiêu chuẩn thiết kế kết cấu thép TCVN/AISC',
    "descriptionVi" = 'Tính toán khung thép tiền chế Zamil, liên kết bu lông chịu lực cao, mối hàn công nghiệp và tiêu chuẩn thiết kế kết cấu thép TCVN/AISC',
    "descriptionEn" = 'Tính toán khung thép tiền chế Zamil, liên kết bu lông chịu lực cao, mối hàn công nghiệp và tiêu chuẩn thiết kế kết cấu thép TCVN/AISC',
    credits = 3,
    "departmentId" = 'department-fce-civil',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE id = 'course-auto-085';
UPDATE academic."Course" SET
    code = 'CE086',
    name = 'Công nghệ vật liệu xây dựng mới và Bền vững',
    "nameVi" = 'Công nghệ vật liệu xây dựng mới và Bền vững',
    "nameEn" = 'Sustainable Building Materials & Green Technologies',
    description = 'Bê tông tự lèn tính năng cao UHPC, vật liệu tái chế xanh, tiêu chuẩn chứng nhận công trình xanh LEED/Lotus và đánh giá vòng đời công trình',
    "descriptionVi" = 'Bê tông tự lèn tính năng cao UHPC, vật liệu tái chế xanh, tiêu chuẩn chứng nhận công trình xanh LEED/Lotus và đánh giá vòng đời công trình',
    "descriptionEn" = 'Bê tông tự lèn tính năng cao UHPC, vật liệu tái chế xanh, tiêu chuẩn chứng nhận công trình xanh LEED/Lotus và đánh giá vòng đời công trình',
    credits = 3,
    "departmentId" = 'department-fce-civil',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE id = 'course-auto-086';
UPDATE academic."Course" SET
    code = 'CE087',
    name = 'Trắc địa công trình và Định vị vệ tinh GNSS',
    "nameVi" = 'Trắc địa công trình và Định vị vệ tinh GNSS',
    "nameEn" = 'Engineering Surveying & GNSS Satellite Positioning',
    description = 'Đo đạc trắc địa bằng máy toàn đạc điện tử, công nghệ định vị vệ tinh vi sai RTK GNSS và chuyển bản vẽ thiết kế ra hiện trường thi công',
    "descriptionVi" = 'Đo đạc trắc địa bằng máy toàn đạc điện tử, công nghệ định vị vệ tinh vi sai RTK GNSS và chuyển bản vẽ thiết kế ra hiện trường thi công',
    "descriptionEn" = 'Đo đạc trắc địa bằng máy toàn đạc điện tử, công nghệ định vị vệ tinh vi sai RTK GNSS và chuyển bản vẽ thiết kế ra hiện trường thi công',
    credits = 3,
    "departmentId" = 'department-fce-civil',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE id = 'course-auto-087';
UPDATE academic."Course" SET
    code = 'CE088',
    name = 'Quy hoạch đô thị và Hạ tầng kỹ thuật thông minh',
    "nameVi" = 'Quy hoạch đô thị và Hạ tầng kỹ thuật thông minh',
    "nameEn" = 'Smart Urban Planning & Infrastructure Systems',
    description = 'Thiết kế mạng lưới cấp thoát nước đô thị, xử lý nước thải, hệ thống giao thông đô thị và ứng dụng GIS trong quản lý không gian thông minh',
    "descriptionVi" = 'Thiết kế mạng lưới cấp thoát nước đô thị, xử lý nước thải, hệ thống giao thông đô thị và ứng dụng GIS trong quản lý không gian thông minh',
    "descriptionEn" = 'Thiết kế mạng lưới cấp thoát nước đô thị, xử lý nước thải, hệ thống giao thông đô thị và ứng dụng GIS trong quản lý không gian thông minh',
    credits = 3,
    "departmentId" = 'department-fce-civil',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE id = 'course-auto-088';
UPDATE academic."Course" SET
    code = 'INT089',
    name = 'Đạo đức nghề nghiệp và Sở hữu trí tuệ công nghệ',
    "nameVi" = 'Đạo đức nghề nghiệp và Sở hữu trí tuệ công nghệ',
    "nameEn" = 'Professional Ethics & Tech Intellectual Property',
    description = 'Luật sở hữu trí tuệ, bản quyền phần mềm, sáng chế công nghệ, đạo đức nghề nghiệp kỹ sư và trách nhiệm xã hội của người làm công nghệ',
    "descriptionVi" = 'Luật sở hữu trí tuệ, bản quyền phần mềm, sáng chế công nghệ, đạo đức nghề nghiệp kỹ sư và trách nhiệm xã hội của người làm công nghệ',
    "descriptionEn" = 'Luật sở hữu trí tuệ, bản quyền phần mềm, sáng chế công nghệ, đạo đức nghề nghiệp kỹ sư và trách nhiệm xã hội của người làm công nghệ',
    credits = 2,
    "departmentId" = 'department-demo',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE id = 'course-auto-089';
UPDATE academic."Course" SET
    code = 'INT090',
    name = 'Phương pháp nghiên cứu khoa học kỹ thuật',
    "nameVi" = 'Phương pháp nghiên cứu khoa học kỹ thuật',
    "nameEn" = 'Engineering Research Methodologies & Academic Writing',
    description = 'Phương pháp luận nghiên cứu khoa học, kỹ thuật tìm kiếm tài liệu trên IEEE/Scopus, thiết kế thí nghiệm và cách viết báo cáo chuẩn quốc tế',
    "descriptionVi" = 'Phương pháp luận nghiên cứu khoa học, kỹ thuật tìm kiếm tài liệu trên IEEE/Scopus, thiết kế thí nghiệm và cách viết báo cáo chuẩn quốc tế',
    "descriptionEn" = 'Phương pháp luận nghiên cứu khoa học, kỹ thuật tìm kiếm tài liệu trên IEEE/Scopus, thiết kế thí nghiệm và cách viết báo cáo chuẩn quốc tế',
    credits = 2,
    "departmentId" = 'department-demo',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE id = 'course-auto-090';
UPDATE academic."Course" SET
    code = 'INT091',
    name = 'Kỹ năng lãnh đạo và Làm việc nhóm trong môi trường số',
    "nameVi" = 'Kỹ năng lãnh đạo và Làm việc nhóm trong môi trường số',
    "nameEn" = 'Digital Leadership & Agile Teamwork Dynamics',
    description = 'Kỹ năng truyền cảm hứng lãnh đạo, quản trị xung đột nhóm, tư duy giải quyết vấn đề phức tạp và giao tiếp hiệu quả nơi công sở',
    "descriptionVi" = 'Kỹ năng truyền cảm hứng lãnh đạo, quản trị xung đột nhóm, tư duy giải quyết vấn đề phức tạp và giao tiếp hiệu quả nơi công sở',
    "descriptionEn" = 'Kỹ năng truyền cảm hứng lãnh đạo, quản trị xung đột nhóm, tư duy giải quyết vấn đề phức tạp và giao tiếp hiệu quả nơi công sở',
    credits = 2,
    "departmentId" = 'department-demo',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE id = 'course-auto-091';
UPDATE academic."Course" SET
    code = 'INT092',
    name = 'Đổi mới sáng tạo và Tư duy thiết kế',
    "nameVi" = 'Đổi mới sáng tạo và Tư duy thiết kế',
    "nameEn" = 'Innovation & Design Thinking Methodologies',
    description = '5 bước quy trình Design Thinking (Thấu cảm, Xác định, Ý tưởng, Mẫu thử, Kiểm thử) nhằm phát triển giải pháp công nghệ lấy con người làm trung tâm',
    "descriptionVi" = '5 bước quy trình Design Thinking (Thấu cảm, Xác định, Ý tưởng, Mẫu thử, Kiểm thử) nhằm phát triển giải pháp công nghệ lấy con người làm trung tâm',
    "descriptionEn" = '5 bước quy trình Design Thinking (Thấu cảm, Xác định, Ý tưởng, Mẫu thử, Kiểm thử) nhằm phát triển giải pháp công nghệ lấy con người làm trung tâm',
    credits = 2,
    "departmentId" = 'department-demo',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE id = 'course-auto-092';
UPDATE academic."Course" SET
    code = 'INT093',
    name = 'Chuyên đề Công nghệ bán dẫn và Đóng gói vi mạch',
    "nameVi" = 'Chuyên đề Công nghệ bán dẫn và Đóng gói vi mạch',
    "nameEn" = 'Semiconductor Manufacturing & IC Packaging',
    description = 'Quy trình chế tạo tấm wafer bán dẫn, công nghệ quang khắc vi mô, kiểm thử đóng gói vi mạch bán dẫn và xu hướng ngành công nghiệp chip',
    "descriptionVi" = 'Quy trình chế tạo tấm wafer bán dẫn, công nghệ quang khắc vi mô, kiểm thử đóng gói vi mạch bán dẫn và xu hướng ngành công nghiệp chip',
    "descriptionEn" = 'Quy trình chế tạo tấm wafer bán dẫn, công nghệ quang khắc vi mô, kiểm thử đóng gói vi mạch bán dẫn và xu hướng ngành công nghiệp chip',
    credits = 3,
    "departmentId" = 'department-feee-telecom',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE id = 'course-auto-093';
UPDATE academic."Course" SET
    code = 'INT094',
    name = 'Công nghệ Chuỗi khối Blockchain và Smart Contracts',
    "nameVi" = 'Công nghệ Chuỗi khối Blockchain và Smart Contracts',
    "nameEn" = 'Blockchain Technology & Smart Contract Engineering',
    description = 'Cơ chế đồng thuận Proof of Work/Stake, lập trình hợp đồng thông minh Solidity trên máy ảo Ethereum EVM và ứng dụng Web3 phân tán',
    "descriptionVi" = 'Cơ chế đồng thuận Proof of Work/Stake, lập trình hợp đồng thông minh Solidity trên máy ảo Ethereum EVM và ứng dụng Web3 phân tán',
    "descriptionEn" = 'Cơ chế đồng thuận Proof of Work/Stake, lập trình hợp đồng thông minh Solidity trên máy ảo Ethereum EVM và ứng dụng Web3 phân tán',
    credits = 3,
    "departmentId" = 'department-demo',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE id = 'course-auto-094';
UPDATE academic."Course" SET
    code = 'INT095',
    name = 'Điện toán biên Edge Computing và TinyML cho IoT',
    "nameVi" = 'Điện toán biên Edge Computing và TinyML cho IoT',
    "nameEn" = 'Edge Computing & TinyML for Smart IoT Devices',
    description = 'Thu nhỏ mô hình học máy triển khai trên vi điều khiển tài nguyên thấp (TinyML), suy luận AI cục bộ thời gian thực và tối ưu năng lượng',
    "descriptionVi" = 'Thu nhỏ mô hình học máy triển khai trên vi điều khiển tài nguyên thấp (TinyML), suy luận AI cục bộ thời gian thực và tối ưu năng lượng',
    "descriptionEn" = 'Thu nhỏ mô hình học máy triển khai trên vi điều khiển tài nguyên thấp (TinyML), suy luận AI cục bộ thời gian thực và tối ưu năng lượng',
    credits = 3,
    "departmentId" = 'department-feee-telecom',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE id = 'course-auto-095';
UPDATE academic."Course" SET
    code = 'INT096',
    name = 'Tự động hóa quy trình bằng Robot phần mềm RPA',
    "nameVi" = 'Tự động hóa quy trình bằng Robot phần mềm RPA',
    "nameEn" = 'Robotic Process Automation (RPA) with UiPath',
    description = 'Tự động hóa nghiệp vụ lặp lại bằng UiPath/Power Automate, trích xuất hóa đơn tự động với OCR và tích hợp luồng xử lý thông minh',
    "descriptionVi" = 'Tự động hóa nghiệp vụ lặp lại bằng UiPath/Power Automate, trích xuất hóa đơn tự động với OCR và tích hợp luồng xử lý thông minh',
    "descriptionEn" = 'Tự động hóa nghiệp vụ lặp lại bằng UiPath/Power Automate, trích xuất hóa đơn tự động với OCR và tích hợp luồng xử lý thông minh',
    credits = 3,
    "departmentId" = 'department-demo',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE id = 'course-auto-096';
UPDATE academic."Course" SET
    code = 'INT097',
    name = 'An toàn sức khỏe môi trường trong công nghiệp EHS',
    "nameVi" = 'An toàn sức khỏe môi trường trong công nghiệp EHS',
    "nameEn" = 'Industrial Environment, Health & Safety (EHS)',
    description = 'Quy chuẩn an toàn lao động trong nhà máy, quản lý chất thải nguy hại, phòng chống cháy nổ công nghiệp và hệ thống quản lý ISO 14001 / ISO 45001',
    "descriptionVi" = 'Quy chuẩn an toàn lao động trong nhà máy, quản lý chất thải nguy hại, phòng chống cháy nổ công nghiệp và hệ thống quản lý ISO 14001 / ISO 45001',
    "descriptionEn" = 'Quy chuẩn an toàn lao động trong nhà máy, quản lý chất thải nguy hại, phòng chống cháy nổ công nghiệp và hệ thống quản lý ISO 14001 / ISO 45001',
    credits = 2,
    "departmentId" = 'department-fme-robotics',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE id = 'course-auto-097';
UPDATE academic."Course" SET
    code = 'INT098',
    name = 'Quản trị an ninh mạng và Tuân thủ tiêu chuẩn ISO 27001',
    "nameVi" = 'Quản trị an ninh mạng và Tuân thủ tiêu chuẩn ISO 27001',
    "nameEn" = 'Information Security Governance & ISO 27001 Compliance',
    description = 'Khung quản trị an toàn thông tin ISMS theo chuẩn ISO/IEC 27001, đánh giá rủi ro an ninh, chính sách bảo vệ dữ liệu cá nhân theo Nghị định 13/2023',
    "descriptionVi" = 'Khung quản trị an toàn thông tin ISMS theo chuẩn ISO/IEC 27001, đánh giá rủi ro an ninh, chính sách bảo vệ dữ liệu cá nhân theo Nghị định 13/2023',
    "descriptionEn" = 'Khung quản trị an toàn thông tin ISMS theo chuẩn ISO/IEC 27001, đánh giá rủi ro an ninh, chính sách bảo vệ dữ liệu cá nhân theo Nghị định 13/2023',
    credits = 3,
    "departmentId" = 'department-demo',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE id = 'course-auto-098';
UPDATE academic."Course" SET
    code = 'INT099',
    name = 'Trí tuệ nhân tạo tạo sinh Generative AI ứng dụng',
    "nameVi" = 'Trí tuệ nhân tạo tạo sinh Generative AI ứng dụng',
    "nameEn" = 'Applied Generative AI & Autonomous Agent Systems',
    description = 'Xây dựng ứng dụng AI với LangChain/LlamaIndex, hệ thống đa tác tử Multi-Agent Autonomous Systems, tạo sinh ảnh/video và đạo đức ứng dụng GenAI',
    "descriptionVi" = 'Xây dựng ứng dụng AI với LangChain/LlamaIndex, hệ thống đa tác tử Multi-Agent Autonomous Systems, tạo sinh ảnh/video và đạo đức ứng dụng GenAI',
    "descriptionEn" = 'Xây dựng ứng dụng AI với LangChain/LlamaIndex, hệ thống đa tác tử Multi-Agent Autonomous Systems, tạo sinh ảnh/video và đạo đức ứng dụng GenAI',
    credits = 3,
    "departmentId" = 'department-demo',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE id = 'course-auto-099';
UPDATE academic."Course" SET
    code = 'INT100',
    name = 'Đồ án Tốt nghiệp Kỹ sư đa ngành',
    "nameVi" = 'Đồ án Tốt nghiệp Kỹ sư đa ngành',
    "nameEn" = 'Interdisciplinary Engineering Capstone Project',
    description = 'Đồ án tốt nghiệp liên ngành kết hợp Công nghệ thông tin, Cơ điện tử, Tự động hóa và Quản lý nhằm giải quyết bài toán chuyển đổi số toàn diện',
    "descriptionVi" = 'Đồ án tốt nghiệp liên ngành kết hợp Công nghệ thông tin, Cơ điện tử, Tự động hóa và Quản lý nhằm giải quyết bài toán chuyển đổi số toàn diện',
    "descriptionEn" = 'Đồ án tốt nghiệp liên ngành kết hợp Công nghệ thông tin, Cơ điện tử, Tự động hóa và Quản lý nhằm giải quyết bài toán chuyển đổi số toàn diện',
    credits = 4,
    "departmentId" = 'department-demo',
    "updatedAt" = CURRENT_TIMESTAMP
WHERE id = 'course-auto-100';

-- ---------------------------------------------------------------------
-- 3. ACADEMIC COURSE REQUIREMENTS & PREREQUISITES (CourseRequirement)
-- ---------------------------------------------------------------------
INSERT INTO academic."CourseRequirement" ("id", "courseId", "requiredCourseId", "kind", "minLetterGrade")
VALUES ('req-001', 'course-software-architecture-demo', 'course-algorithms-demo', 'PREREQUISITE', 'D')
ON CONFLICT ("courseId", "requiredCourseId", "kind") DO NOTHING;
INSERT INTO academic."CourseRequirement" ("id", "courseId", "requiredCourseId", "kind", "minLetterGrade")
VALUES ('req-002', 'course-devops-demo', 'course-database-demo', 'PREREQUISITE', 'D')
ON CONFLICT ("courseId", "requiredCourseId", "kind") DO NOTHING;
INSERT INTO academic."CourseRequirement" ("id", "courseId", "requiredCourseId", "kind", "minLetterGrade")
VALUES ('req-003', 'course-ai-demo', 'course-algorithms-demo', 'PREREQUISITE', 'D')
ON CONFLICT ("courseId", "requiredCourseId", "kind") DO NOTHING;
INSERT INTO academic."CourseRequirement" ("id", "courseId", "requiredCourseId", "kind", "minLetterGrade")
VALUES ('req-004', 'course-auto-014', 'course-auto-013', 'PREREQUISITE', 'D')
ON CONFLICT ("courseId", "requiredCourseId", "kind") DO NOTHING;
INSERT INTO academic."CourseRequirement" ("id", "courseId", "requiredCourseId", "kind", "minLetterGrade")
VALUES ('req-005', 'course-auto-016', 'course-testing-demo', 'PREREQUISITE', 'D')
ON CONFLICT ("courseId", "requiredCourseId", "kind") DO NOTHING;
INSERT INTO academic."CourseRequirement" ("id", "courseId", "requiredCourseId", "kind", "minLetterGrade")
VALUES ('req-006', 'course-auto-020', 'course-auto-017', 'PREREQUISITE', 'D')
ON CONFLICT ("courseId", "requiredCourseId", "kind") DO NOTHING;
INSERT INTO academic."CourseRequirement" ("id", "courseId", "requiredCourseId", "kind", "minLetterGrade")
VALUES ('req-007', 'course-auto-024', 'course-security-demo', 'PREREQUISITE', 'D')
ON CONFLICT ("courseId", "requiredCourseId", "kind") DO NOTHING;
INSERT INTO academic."CourseRequirement" ("id", "courseId", "requiredCourseId", "kind", "minLetterGrade")
VALUES ('req-008', 'course-auto-025', 'course-auto-019', 'PREREQUISITE', 'D')
ON CONFLICT ("courseId", "requiredCourseId", "kind") DO NOTHING;
INSERT INTO academic."CourseRequirement" ("id", "courseId", "requiredCourseId", "kind", "minLetterGrade")
VALUES ('req-009', 'course-auto-026', 'course-ai-demo', 'PREREQUISITE', 'D')
ON CONFLICT ("courseId", "requiredCourseId", "kind") DO NOTHING;
INSERT INTO academic."CourseRequirement" ("id", "courseId", "requiredCourseId", "kind", "minLetterGrade")
VALUES ('req-010', 'course-auto-027', 'course-ai-demo', 'PREREQUISITE', 'D')
ON CONFLICT ("courseId", "requiredCourseId", "kind") DO NOTHING;
INSERT INTO academic."CourseRequirement" ("id", "courseId", "requiredCourseId", "kind", "minLetterGrade")
VALUES ('req-011', 'course-auto-028', 'course-ai-demo', 'PREREQUISITE', 'D')
ON CONFLICT ("courseId", "requiredCourseId", "kind") DO NOTHING;
INSERT INTO academic."CourseRequirement" ("id", "courseId", "requiredCourseId", "kind", "minLetterGrade")
VALUES ('req-012', 'course-auto-029', 'course-database-demo', 'PREREQUISITE', 'D')
ON CONFLICT ("courseId", "requiredCourseId", "kind") DO NOTHING;
INSERT INTO academic."CourseRequirement" ("id", "courseId", "requiredCourseId", "kind", "minLetterGrade")
VALUES ('req-013', 'course-auto-030', 'course-auto-029', 'PREREQUISITE', 'D')
ON CONFLICT ("courseId", "requiredCourseId", "kind") DO NOTHING;
INSERT INTO academic."CourseRequirement" ("id", "courseId", "requiredCourseId", "kind", "minLetterGrade")
VALUES ('req-014', 'course-auto-031', 'course-algorithms-demo', 'PREREQUISITE', 'D')
ON CONFLICT ("courseId", "requiredCourseId", "kind") DO NOTHING;
INSERT INTO academic."CourseRequirement" ("id", "courseId", "requiredCourseId", "kind", "minLetterGrade")
VALUES ('req-015', 'course-auto-033', 'course-auto-031', 'PREREQUISITE', 'D')
ON CONFLICT ("courseId", "requiredCourseId", "kind") DO NOTHING;
INSERT INTO academic."CourseRequirement" ("id", "courseId", "requiredCourseId", "kind", "minLetterGrade")
VALUES ('req-016', 'course-auto-035', 'course-auto-028', 'PREREQUISITE', 'D')
ON CONFLICT ("courseId", "requiredCourseId", "kind") DO NOTHING;
INSERT INTO academic."CourseRequirement" ("id", "courseId", "requiredCourseId", "kind", "minLetterGrade")
VALUES ('req-017', 'course-auto-037', 'course-security-demo', 'PREREQUISITE', 'D')
ON CONFLICT ("courseId", "requiredCourseId", "kind") DO NOTHING;
INSERT INTO academic."CourseRequirement" ("id", "courseId", "requiredCourseId", "kind", "minLetterGrade")
VALUES ('req-018', 'course-auto-038', 'course-security-demo', 'PREREQUISITE', 'D')
ON CONFLICT ("courseId", "requiredCourseId", "kind") DO NOTHING;
INSERT INTO academic."CourseRequirement" ("id", "courseId", "requiredCourseId", "kind", "minLetterGrade")
VALUES ('req-019', 'course-auto-039', 'course-auto-037', 'PREREQUISITE', 'D')
ON CONFLICT ("courseId", "requiredCourseId", "kind") DO NOTHING;
INSERT INTO academic."CourseRequirement" ("id", "courseId", "requiredCourseId", "kind", "minLetterGrade")
VALUES ('req-020', 'course-auto-043', 'course-database-demo', 'PREREQUISITE', 'D')
ON CONFLICT ("courseId", "requiredCourseId", "kind") DO NOTHING;
INSERT INTO academic."CourseRequirement" ("id", "courseId", "requiredCourseId", "kind", "minLetterGrade")
VALUES ('req-021', 'course-auto-044', 'course-database-demo', 'PREREQUISITE', 'D')
ON CONFLICT ("courseId", "requiredCourseId", "kind") DO NOTHING;
INSERT INTO academic."CourseRequirement" ("id", "courseId", "requiredCourseId", "kind", "minLetterGrade")
VALUES ('req-022', 'course-auto-050', 'course-auto-048', 'PREREQUISITE', 'D')
ON CONFLICT ("courseId", "requiredCourseId", "kind") DO NOTHING;
INSERT INTO academic."CourseRequirement" ("id", "courseId", "requiredCourseId", "kind", "minLetterGrade")
VALUES ('req-023', 'course-auto-051', 'course-auto-048', 'PREREQUISITE', 'D')
ON CONFLICT ("courseId", "requiredCourseId", "kind") DO NOTHING;
INSERT INTO academic."CourseRequirement" ("id", "courseId", "requiredCourseId", "kind", "minLetterGrade")
VALUES ('req-024', 'course-auto-054', 'course-auto-053', 'PREREQUISITE', 'D')
ON CONFLICT ("courseId", "requiredCourseId", "kind") DO NOTHING;
INSERT INTO academic."CourseRequirement" ("id", "courseId", "requiredCourseId", "kind", "minLetterGrade")
VALUES ('req-025', 'course-auto-057', 'course-auto-050', 'PREREQUISITE', 'D')
ON CONFLICT ("courseId", "requiredCourseId", "kind") DO NOTHING;
INSERT INTO academic."CourseRequirement" ("id", "courseId", "requiredCourseId", "kind", "minLetterGrade")
VALUES ('req-026', 'course-auto-059', 'course-auto-058', 'PREREQUISITE', 'D')
ON CONFLICT ("courseId", "requiredCourseId", "kind") DO NOTHING;
INSERT INTO academic."CourseRequirement" ("id", "courseId", "requiredCourseId", "kind", "minLetterGrade")
VALUES ('req-027', 'course-auto-061', 'course-auto-060', 'PREREQUISITE', 'D')
ON CONFLICT ("courseId", "requiredCourseId", "kind") DO NOTHING;
INSERT INTO academic."CourseRequirement" ("id", "courseId", "requiredCourseId", "kind", "minLetterGrade")
VALUES ('req-028', 'course-auto-063', 'course-auto-062', 'PREREQUISITE', 'D')
ON CONFLICT ("courseId", "requiredCourseId", "kind") DO NOTHING;
INSERT INTO academic."CourseRequirement" ("id", "courseId", "requiredCourseId", "kind", "minLetterGrade")
VALUES ('req-029', 'course-auto-065', 'course-auto-064', 'PREREQUISITE', 'D')
ON CONFLICT ("courseId", "requiredCourseId", "kind") DO NOTHING;
INSERT INTO academic."CourseRequirement" ("id", "courseId", "requiredCourseId", "kind", "minLetterGrade")
VALUES ('req-030', 'course-auto-069', 'course-auto-068', 'PREREQUISITE', 'D')
ON CONFLICT ("courseId", "requiredCourseId", "kind") DO NOTHING;
INSERT INTO academic."CourseRequirement" ("id", "courseId", "requiredCourseId", "kind", "minLetterGrade")
VALUES ('req-031', 'course-auto-070', 'course-auto-072', 'PREREQUISITE', 'D')
ON CONFLICT ("courseId", "requiredCourseId", "kind") DO NOTHING;
INSERT INTO academic."CourseRequirement" ("id", "courseId", "requiredCourseId", "kind", "minLetterGrade")
VALUES ('req-032', 'course-auto-080', 'course-auto-076', 'PREREQUISITE', 'D')
ON CONFLICT ("courseId", "requiredCourseId", "kind") DO NOTHING;
INSERT INTO academic."CourseRequirement" ("id", "courseId", "requiredCourseId", "kind", "minLetterGrade")
VALUES ('req-033', 'course-auto-082', 'course-auto-081', 'PREREQUISITE', 'D')
ON CONFLICT ("courseId", "requiredCourseId", "kind") DO NOTHING;
INSERT INTO academic."CourseRequirement" ("id", "courseId", "requiredCourseId", "kind", "minLetterGrade")
VALUES ('req-034', 'course-auto-084', 'course-auto-081', 'PREREQUISITE', 'D')
ON CONFLICT ("courseId", "requiredCourseId", "kind") DO NOTHING;
INSERT INTO academic."CourseRequirement" ("id", "courseId", "requiredCourseId", "kind", "minLetterGrade")
VALUES ('req-035', 'course-auto-085', 'course-auto-082', 'PREREQUISITE', 'D')
ON CONFLICT ("courseId", "requiredCourseId", "kind") DO NOTHING;
INSERT INTO academic."CourseRequirement" ("id", "courseId", "requiredCourseId", "kind", "minLetterGrade")
VALUES ('req-036', 'course-auto-095', 'course-auto-051', 'PREREQUISITE', 'D')
ON CONFLICT ("courseId", "requiredCourseId", "kind") DO NOTHING;
INSERT INTO academic."CourseRequirement" ("id", "courseId", "requiredCourseId", "kind", "minLetterGrade")
VALUES ('req-037', 'course-auto-099', 'course-auto-026', 'PREREQUISITE', 'D')
ON CONFLICT ("courseId", "requiredCourseId", "kind") DO NOTHING;
INSERT INTO academic."CourseRequirement" ("id", "courseId", "requiredCourseId", "kind", "minLetterGrade")
VALUES ('req-038', 'course-auto-100', 'course-project-demo', 'PREREQUISITE', 'D')
ON CONFLICT ("courseId", "requiredCourseId", "kind") DO NOTHING;

-- ---------------------------------------------------------------------
-- 4. DISTINGUISHED PROFESSORS & LECTURERS
-- ---------------------------------------------------------------------
INSERT INTO campuscore_auth."User" ("id", "email", "password", "firstName", "lastName", "status", "emailVerified")
SELECT 'lecturer-user-026', 'khoi.td@campuscore.demo', (SELECT "password" FROM campuscore_auth."User" WHERE "id" = 'lecturer-user' LIMIT 1),
       'GS.TS. Khôi', 'Trần Đình', 'ACTIVE', TRUE
WHERE NOT EXISTS (SELECT 1 FROM campuscore_auth."User" WHERE "id" = 'lecturer-user-026');
INSERT INTO academic."Lecturer" ("id", "userId", "departmentId", "employeeId", "title", "specialization")
SELECT 'lecturer-profile-026', 'lecturer-user-026', 'department-fit-ai', 'LEC-DEMO-026', 'GS.TS.', 'Trí tuệ Nhân tạo và Mô hình Ngôn ngữ Lớn LLM'
WHERE NOT EXISTS (SELECT 1 FROM academic."Lecturer" WHERE "id" = 'lecturer-profile-026');
INSERT INTO campuscore_auth."UserRole" ("id", "userId", "roleId")
SELECT 'user-role-lecturer-profile-026', 'lecturer-user-026', 'role-lecturer'
WHERE NOT EXISTS (SELECT 1 FROM campuscore_auth."UserRole" WHERE "userId" = 'lecturer-user-026' AND "roleId" = 'role-lecturer');
INSERT INTO campuscore_auth."User" ("id", "email", "password", "firstName", "lastName", "status", "emailVerified")
SELECT 'lecturer-user-027', 'nam.ph@campuscore.demo', (SELECT "password" FROM campuscore_auth."User" WHERE "id" = 'lecturer-user' LIMIT 1),
       'PGS.TS. Nam', 'Phạm Hoài', 'ACTIVE', TRUE
WHERE NOT EXISTS (SELECT 1 FROM campuscore_auth."User" WHERE "id" = 'lecturer-user-027');
INSERT INTO academic."Lecturer" ("id", "userId", "departmentId", "employeeId", "title", "specialization")
SELECT 'lecturer-profile-027', 'lecturer-user-027', 'department-fit-software', 'LEC-DEMO-027', 'PGS.TS.', 'Kiến trúc phần mềm phân tán và Cloud Native'
WHERE NOT EXISTS (SELECT 1 FROM academic."Lecturer" WHERE "id" = 'lecturer-profile-027');
INSERT INTO campuscore_auth."UserRole" ("id", "userId", "roleId")
SELECT 'user-role-lecturer-profile-027', 'lecturer-user-027', 'role-lecturer'
WHERE NOT EXISTS (SELECT 1 FROM campuscore_auth."UserRole" WHERE "userId" = 'lecturer-user-027' AND "roleId" = 'role-lecturer');
INSERT INTO campuscore_auth."User" ("id", "email", "password", "firstName", "lastName", "status", "emailVerified")
SELECT 'lecturer-user-028', 'bao.dv@campuscore.demo', (SELECT "password" FROM campuscore_auth."User" WHERE "id" = 'lecturer-user' LIMIT 1),
       'TS. Bảo', 'Đặng Vũ', 'ACTIVE', TRUE
WHERE NOT EXISTS (SELECT 1 FROM campuscore_auth."User" WHERE "id" = 'lecturer-user-028');
INSERT INTO academic."Lecturer" ("id", "userId", "departmentId", "employeeId", "title", "specialization")
SELECT 'lecturer-profile-028', 'lecturer-user-028', 'department-fit-network', 'LEC-DEMO-028', 'TS.', 'An toàn thông tin và Điều tra số Digital Forensics'
WHERE NOT EXISTS (SELECT 1 FROM academic."Lecturer" WHERE "id" = 'lecturer-profile-028');
INSERT INTO campuscore_auth."UserRole" ("id", "userId", "roleId")
SELECT 'user-role-lecturer-profile-028', 'lecturer-user-028', 'role-lecturer'
WHERE NOT EXISTS (SELECT 1 FROM campuscore_auth."UserRole" WHERE "userId" = 'lecturer-user-028' AND "roleId" = 'role-lecturer');
INSERT INTO campuscore_auth."User" ("id", "email", "password", "firstName", "lastName", "status", "emailVerified")
SELECT 'lecturer-user-029', 'ha.vt@campuscore.demo', (SELECT "password" FROM campuscore_auth."User" WHERE "id" = 'lecturer-user' LIMIT 1),
       'TS. Hà', 'Vũ Thu', 'ACTIVE', TRUE
WHERE NOT EXISTS (SELECT 1 FROM campuscore_auth."User" WHERE "id" = 'lecturer-user-029');
INSERT INTO academic."Lecturer" ("id", "userId", "departmentId", "employeeId", "title", "specialization")
SELECT 'lecturer-profile-029', 'lecturer-user-029', 'department-fit-is', 'LEC-DEMO-029', 'TS.', 'Khai phá dữ liệu lớn và Kho dữ liệu thông minh'
WHERE NOT EXISTS (SELECT 1 FROM academic."Lecturer" WHERE "id" = 'lecturer-profile-029');
INSERT INTO campuscore_auth."UserRole" ("id", "userId", "roleId")
SELECT 'user-role-lecturer-profile-029', 'lecturer-user-029', 'role-lecturer'
WHERE NOT EXISTS (SELECT 1 FROM campuscore_auth."UserRole" WHERE "userId" = 'lecturer-user-029' AND "roleId" = 'role-lecturer');
INSERT INTO campuscore_auth."User" ("id", "email", "password", "firstName", "lastName", "status", "emailVerified")
SELECT 'lecturer-user-030', 'huy.nq@campuscore.demo', (SELECT "password" FROM campuscore_auth."User" WHERE "id" = 'lecturer-user' LIMIT 1),
       'ThS. Huy', 'Nguyễn Quang', 'ACTIVE', TRUE
WHERE NOT EXISTS (SELECT 1 FROM campuscore_auth."User" WHERE "id" = 'lecturer-user-030');
INSERT INTO academic."Lecturer" ("id", "userId", "departmentId", "employeeId", "title", "specialization")
SELECT 'lecturer-profile-030', 'lecturer-user-030', 'department-fit-software', 'LEC-DEMO-030', 'ThS.', 'DevOps, SRE và Tự động hóa kiểm thử phần mềm'
WHERE NOT EXISTS (SELECT 1 FROM academic."Lecturer" WHERE "id" = 'lecturer-profile-030');
INSERT INTO campuscore_auth."UserRole" ("id", "userId", "roleId")
SELECT 'user-role-lecturer-profile-030', 'lecturer-user-030', 'role-lecturer'
WHERE NOT EXISTS (SELECT 1 FROM campuscore_auth."UserRole" WHERE "userId" = 'lecturer-user-030' AND "roleId" = 'role-lecturer');
INSERT INTO campuscore_auth."User" ("id", "email", "password", "firstName", "lastName", "status", "emailVerified")
SELECT 'lecturer-user-031', 'thang.lq@campuscore.demo', (SELECT "password" FROM campuscore_auth."User" WHERE "id" = 'lecturer-user' LIMIT 1),
       'PGS.TS. Thắng', 'Lê Quốc', 'ACTIVE', TRUE
WHERE NOT EXISTS (SELECT 1 FROM campuscore_auth."User" WHERE "id" = 'lecturer-user-031');
INSERT INTO academic."Lecturer" ("id", "userId", "departmentId", "employeeId", "title", "specialization")
SELECT 'lecturer-profile-031', 'lecturer-user-031', 'department-feee-auto', 'LEC-DEMO-031', 'PGS.TS.', 'Lưới điện thông minh Smart Grid và Năng lượng tái tạo'
WHERE NOT EXISTS (SELECT 1 FROM academic."Lecturer" WHERE "id" = 'lecturer-profile-031');
INSERT INTO campuscore_auth."UserRole" ("id", "userId", "roleId")
SELECT 'user-role-lecturer-profile-031', 'lecturer-user-031', 'role-lecturer'
WHERE NOT EXISTS (SELECT 1 FROM campuscore_auth."UserRole" WHERE "userId" = 'lecturer-user-031' AND "roleId" = 'role-lecturer');
INSERT INTO campuscore_auth."User" ("id", "email", "password", "firstName", "lastName", "status", "emailVerified")
SELECT 'lecturer-user-032', 'nghia.ht@campuscore.demo', (SELECT "password" FROM campuscore_auth."User" WHERE "id" = 'lecturer-user' LIMIT 1),
       'TS. Nghĩa', 'Hoàng Trọng', 'ACTIVE', TRUE
WHERE NOT EXISTS (SELECT 1 FROM campuscore_auth."User" WHERE "id" = 'lecturer-user-032');
INSERT INTO academic."Lecturer" ("id", "userId", "departmentId", "employeeId", "title", "specialization")
SELECT 'lecturer-profile-032', 'lecturer-user-032', 'department-fme-robotics', 'LEC-DEMO-032', 'TS.', 'Robot điều hướng ROS 2 và Xe tự hành AGV'
WHERE NOT EXISTS (SELECT 1 FROM academic."Lecturer" WHERE "id" = 'lecturer-profile-032');
INSERT INTO campuscore_auth."UserRole" ("id", "userId", "roleId")
SELECT 'user-role-lecturer-profile-032', 'lecturer-user-032', 'role-lecturer'
WHERE NOT EXISTS (SELECT 1 FROM campuscore_auth."UserRole" WHERE "userId" = 'lecturer-user-032' AND "roleId" = 'role-lecturer');
INSERT INTO campuscore_auth."User" ("id", "email", "password", "firstName", "lastName", "status", "emailVerified")
SELECT 'lecturer-user-033', 'cuong.mq@campuscore.demo', (SELECT "password" FROM campuscore_auth."User" WHERE "id" = 'lecturer-user' LIMIT 1),
       'TS. Cường', 'Mai Quốc', 'ACTIVE', TRUE
WHERE NOT EXISTS (SELECT 1 FROM campuscore_auth."User" WHERE "id" = 'lecturer-user-033');
INSERT INTO academic."Lecturer" ("id", "userId", "departmentId", "employeeId", "title", "specialization")
SELECT 'lecturer-profile-033', 'lecturer-user-033', 'department-fme-automotive', 'LEC-DEMO-033', 'TS.', 'Pin Lithium-ion và Hệ thống truyền động xe điện EV'
WHERE NOT EXISTS (SELECT 1 FROM academic."Lecturer" WHERE "id" = 'lecturer-profile-033');
INSERT INTO campuscore_auth."UserRole" ("id", "userId", "roleId")
SELECT 'user-role-lecturer-profile-033', 'lecturer-user-033', 'role-lecturer'
WHERE NOT EXISTS (SELECT 1 FROM campuscore_auth."UserRole" WHERE "userId" = 'lecturer-user-033' AND "roleId" = 'role-lecturer');
INSERT INTO campuscore_auth."User" ("id", "email", "password", "firstName", "lastName", "status", "emailVerified")
SELECT 'lecturer-user-034', 'tam.bm@campuscore.demo', (SELECT "password" FROM campuscore_auth."User" WHERE "id" = 'lecturer-user' LIMIT 1),
       'PGS.TS. Tâm', 'Bùi Minh', 'ACTIVE', TRUE
WHERE NOT EXISTS (SELECT 1 FROM campuscore_auth."User" WHERE "id" = 'lecturer-user-034');
INSERT INTO academic."Lecturer" ("id", "userId", "departmentId", "employeeId", "title", "specialization")
SELECT 'lecturer-profile-034', 'lecturer-user-034', 'department-foe-logistics', 'LEC-DEMO-034', 'PGS.TS.', 'Chuỗi cung ứng số và Quản trị Logistics quốc tế'
WHERE NOT EXISTS (SELECT 1 FROM academic."Lecturer" WHERE "id" = 'lecturer-profile-034');
INSERT INTO campuscore_auth."UserRole" ("id", "userId", "roleId")
SELECT 'user-role-lecturer-profile-034', 'lecturer-user-034', 'role-lecturer'
WHERE NOT EXISTS (SELECT 1 FROM campuscore_auth."UserRole" WHERE "userId" = 'lecturer-user-034' AND "roleId" = 'role-lecturer');
INSERT INTO campuscore_auth."User" ("id", "email", "password", "firstName", "lastName", "status", "emailVerified")
SELECT 'lecturer-user-035', 'dung.nh@campuscore.demo', (SELECT "password" FROM campuscore_auth."User" WHERE "id" = 'lecturer-user' LIMIT 1),
       'TS. Dũng', 'Nguyễn Hữu', 'ACTIVE', TRUE
WHERE NOT EXISTS (SELECT 1 FROM campuscore_auth."User" WHERE "id" = 'lecturer-user-035');
INSERT INTO academic."Lecturer" ("id", "userId", "departmentId", "employeeId", "title", "specialization")
SELECT 'lecturer-profile-035', 'lecturer-user-035', 'department-fce-bim', 'LEC-DEMO-035', 'TS.', 'Mô hình thông tin công trình BIM 5D và Kết cấu dự ứng lực'
WHERE NOT EXISTS (SELECT 1 FROM academic."Lecturer" WHERE "id" = 'lecturer-profile-035');
INSERT INTO campuscore_auth."UserRole" ("id", "userId", "roleId")
SELECT 'user-role-lecturer-profile-035', 'lecturer-user-035', 'role-lecturer'
WHERE NOT EXISTS (SELECT 1 FROM campuscore_auth."UserRole" WHERE "userId" = 'lecturer-user-035' AND "roleId" = 'role-lecturer');

-- ---------------------------------------------------------------------
-- 5. STUDENT CONDUCT ACTIVITIES & SEMESTER EVALUATIONS (DRL)
-- ---------------------------------------------------------------------
INSERT INTO academic.conduct_activity (
    id, student_id, semester_id, title, category, points, activity_date, organizer, created_at
) VALUES (
    'act-drl-ute-mua-he-xanh-2026', 'student-profile', 'semester-demo', 'Chiến dịch tình nguyện Mùa hè xanh 2026', 'Tình nguyện vì cộng đồng', 10.0, '2026-07-20', 'Đoàn Thanh niên - Hội Sinh viên UTE', CURRENT_TIMESTAMP
) ON CONFLICT (id) DO NOTHING;
INSERT INTO academic.conduct_activity (
    id, student_id, semester_id, title, category, points, activity_date, organizer, created_at
) VALUES (
    'act-drl-ute-tiep-suc-mua-thi-2026', 'student-profile', 'semester-demo', 'Chương trình Tiếp sức mùa thi & Đón tân sinh viên khóa 2026', 'Tình nguyện vì cộng đồng', 8.0, '2026-08-10', 'Hội Sinh viên ĐH Công nghệ Kỹ thuật TP.HCM', CURRENT_TIMESTAMP
) ON CONFLICT (id) DO NOTHING;
INSERT INTO academic.conduct_activity (
    id, student_id, semester_id, title, category, points, activity_date, organizer, created_at
) VALUES (
    'act-drl-ute-hien-mau-nhan-dao-2', 'student-profile', 'semester-demo', 'Ngày hội Hiến máu tình nguyện ''Giọt hồng Công nghệ'' đợt 2', 'Tình nguyện vì cộng đồng', 8.0, '2026-09-05', 'Đoàn Thanh niên & Hội Chữ thập đỏ UTE', CURRENT_TIMESTAMP
) ON CONFLICT (id) DO NOTHING;
INSERT INTO academic.conduct_activity (
    id, student_id, semester_id, title, category, points, activity_date, organizer, created_at
) VALUES (
    'act-drl-ute-olympic-tin-hoc-2026', 'student-profile', 'semester-demo', 'Kỳ thi Olympic Tin học sinh viên cấp Trường lần thứ 18', 'Học thuật & Nghiên cứu khoa học', 10.0, '2026-09-02', 'Khoa Công nghệ Thông tin', CURRENT_TIMESTAMP
) ON CONFLICT (id) DO NOTHING;
INSERT INTO academic.conduct_activity (
    id, student_id, semester_id, title, category, points, activity_date, organizer, created_at
) VALUES (
    'act-drl-ute-ai-hackathon-2026', 'student-profile', 'semester-demo', 'Cuộc thi Lập trình AI Hackathon & Big Data Innovation 2026', 'Học thuật & Nghiên cứu khoa học', 10.0, '2026-08-28', 'Khoa CNTT phối hợp Doanh nghiệp Tech', CURRENT_TIMESTAMP
) ON CONFLICT (id) DO NOTHING;
INSERT INTO academic.conduct_activity (
    id, student_id, semester_id, title, category, points, activity_date, organizer, created_at
) VALUES (
    'act-drl-ute-nckh-sinh-vien-2026', 'student-profile', 'semester-demo', 'Nghiệm thu Đề tài Nghiên cứu khoa học sinh viên cấp Trường', 'Học thuật & Nghiên cứu khoa học', 10.0, '2026-08-15', 'Phòng Khoa học Công nghệ', CURRENT_TIMESTAMP
) ON CONFLICT (id) DO NOTHING;
INSERT INTO academic.conduct_activity (
    id, student_id, semester_id, title, category, points, activity_date, organizer, created_at
) VALUES (
    'act-drl-ute-techtalk-cyber-sec', 'student-profile', 'semester-demo', 'Hội thảo Chuyên đề An ninh mạng & Bảo vệ dữ liệu cá nhân', 'Học thuật & Nghiên cứu khoa học', 5.0, '2026-08-22', 'Bộ môn Mạng máy tính & An toàn thông tin', CURRENT_TIMESTAMP
) ON CONFLICT (id) DO NOTHING;
INSERT INTO academic.conduct_activity (
    id, student_id, semester_id, title, category, points, activity_date, organizer, created_at
) VALUES (
    'act-drl-ute-career-fair-2026', 'student-profile', 'semester-demo', 'Ngày hội Việc làm & Kết nối Doanh nghiệp UTE Career Expo', 'Kỹ năng & Hướng nghiệp', 6.0, '2026-09-04', 'Trung tâm Dịch vụ Sinh viên & Hướng nghiệp', CURRENT_TIMESTAMP
) ON CONFLICT (id) DO NOTHING;
INSERT INTO academic.conduct_activity (
    id, student_id, semester_id, title, category, points, activity_date, organizer, created_at
) VALUES (
    'act-drl-ute-workshop-cv-interview', 'student-profile', 'semester-demo', 'Tọa đàm ''Kỹ năng viết CV chuyên nghiệp & Chinh phục phỏng vấn''', 'Kỹ năng & Hướng nghiệp', 5.0, '2026-08-19', 'Phòng Công tác Sinh viên & CLB Kỹ năng', CURRENT_TIMESTAMP
) ON CONFLICT (id) DO NOTHING;
INSERT INTO academic.conduct_activity (
    id, student_id, semester_id, title, category, points, activity_date, organizer, created_at
) VALUES (
    'act-drl-ute-company-tour-intel', 'student-profile', 'semester-demo', 'Chuyến tham quan thực tế nhà máy Intel Products Vietnam', 'Kỹ năng & Hướng nghiệp', 6.0, '2026-08-12', 'Trung tâm Hợp tác Doanh nghiệp', CURRENT_TIMESTAMP
) ON CONFLICT (id) DO NOTHING;
INSERT INTO academic.conduct_activity (
    id, student_id, semester_id, title, category, points, activity_date, organizer, created_at
) VALUES (
    'act-drl-ute-giai-bong-da-2026', 'student-profile', 'semester-demo', 'Giải bóng đá sinh viên truyền thống UTE Champions Cup', 'Văn hóa - Thể thao', 7.0, '2026-08-30', 'Bộ môn Giáo dục Thể chất & Ban Thể thao', CURRENT_TIMESTAMP
) ON CONFLICT (id) DO NOTHING;
INSERT INTO academic.conduct_activity (
    id, student_id, semester_id, title, category, points, activity_date, organizer, created_at
) VALUES (
    'act-drl-ute-sinh-vien-khoe', 'student-profile', 'semester-demo', 'Ngày hội ''Sinh viên Khỏe'' và Giải việt dã truyền thống', 'Văn hóa - Thể thao', 6.0, '2026-09-01', 'Hội Sinh viên Trường', CURRENT_TIMESTAMP
) ON CONFLICT (id) DO NOTHING;
INSERT INTO academic.conduct_activity (
    id, student_id, semester_id, title, category, points, activity_date, organizer, created_at
) VALUES (
    'act-drl-ute-van-nghe-20-11', 'student-profile', 'semester-history-demo', 'Hội thi Văn nghệ truyền thống Chào mừng ngày Nhà giáo VN 20/11', 'Văn hóa - Thể thao', 7.0, '2025-11-18', 'Công đoàn & Đoàn Thanh niên Trường', CURRENT_TIMESTAMP
) ON CONFLICT (id) DO NOTHING;
INSERT INTO academic.conduct_activity (
    id, student_id, semester_id, title, category, points, activity_date, organizer, created_at
) VALUES (
    'act-drl-ute-chu-nhat-xanh-2025', 'student-profile', 'semester-history-demo', 'Ngày Chủ nhật xanh dọn dẹp vệ sinh khuôn viên KTX và giảng đường', 'Tình nguyện vì cộng đồng', 6.0, '2025-10-15', 'Đoàn Thanh niên Trường', CURRENT_TIMESTAMP
) ON CONFLICT (id) DO NOTHING;
INSERT INTO academic.conduct_activity (
    id, student_id, semester_id, title, category, points, activity_date, organizer, created_at
) VALUES (
    'act-drl-ute-hoi-thao-cloud-aws', 'student-profile', 'semester-history-demo-1', 'TechTalk ''Điện toán đám mây & Thực hành AWS Certified''', 'Học thuật & Nghiên cứu khoa học', 6.0, '2025-04-10', 'CLB Tin học UTE', CURRENT_TIMESTAMP
) ON CONFLICT (id) DO NOTHING;
-- Seed comprehensive conduct scores across all students and 3 semesters
INSERT INTO academic.conduct_semester_score (
    id, student_id, semester_id,
    criteria1_score, criteria2_score, criteria3_score, criteria4_score, criteria5_score,
    total_score, classification, classification_vi, status, evaluator_name, evaluated_at, created_at, updated_at
)
SELECT
    'conduct-score-' || s.id || '-' || sem.id,
    s.id,
    sem.id,
    ROUND(16.0 + (abs(hashtext(s.id || sem.id || 'c1')) % 41)::numeric / 10.0, 2),
    ROUND(20.0 + (abs(hashtext(s.id || sem.id || 'c2')) % 51)::numeric / 10.0, 2),
    ROUND(15.0 + (abs(hashtext(s.id || sem.id || 'c3')) % 46)::numeric / 10.0, 2),
    ROUND(18.0 + (abs(hashtext(s.id || sem.id || 'c4')) % 61)::numeric / 10.0, 2),
    ROUND(6.0 + (abs(hashtext(s.id || sem.id || 'c5')) % 36)::numeric / 10.0, 2),
    0,
    'TOT', 'Tốt', 'APPROVED',
    'Hội đồng đánh giá rèn luyện Khoa', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM academic."Student" s
CROSS JOIN (
    SELECT 'semester-demo' AS id UNION ALL
    SELECT 'semester-history-demo' AS id UNION ALL
    SELECT 'semester-history-demo-1' AS id
) sem
WHERE NOT EXISTS (
    SELECT 1 FROM academic.conduct_semester_score existing
    WHERE existing.student_id = s.id AND existing.semester_id = sem.id
);

-- Update total score and correct classification
UPDATE academic.conduct_semester_score
SET total_score = criteria1_score + criteria2_score + criteria3_score + criteria4_score + criteria5_score,
    classification = CASE
        WHEN (criteria1_score + criteria2_score + criteria3_score + criteria4_score + criteria5_score) >= 90 THEN 'XUAT_SAC'
        WHEN (criteria1_score + criteria2_score + criteria3_score + criteria4_score + criteria5_score) >= 80 THEN 'TOT'
        WHEN (criteria1_score + criteria2_score + criteria3_score + criteria4_score + criteria5_score) >= 65 THEN 'KHA'
        ELSE 'TRUNG_BINH'
    END,
    classification_vi = CASE
        WHEN (criteria1_score + criteria2_score + criteria3_score + criteria4_score + criteria5_score) >= 90 THEN 'Xuất sắc'
        WHEN (criteria1_score + criteria2_score + criteria3_score + criteria4_score + criteria5_score) >= 80 THEN 'Tốt'
        WHEN (criteria1_score + criteria2_score + criteria3_score + criteria4_score + criteria5_score) >= 65 THEN 'Khá'
        ELSE 'Trung bình'
    END
WHERE id LIKE 'conduct-score-%';

-- ---------------------------------------------------------------------
-- 6. GRADUATION THESIS TOPICS, GROUPS, SUPERVISORS & EVALUATIONS
-- ---------------------------------------------------------------------
INSERT INTO thesis.thesis_topic (
    id, round_id, department_id, title, description, max_groups, status, created_by, version, created_at, updated_at
) VALUES (
    '44444444-4444-4444-4444-444444444011', '22222222-2222-2222-2222-222222222101', 'department-demo',
    'Hệ thống giám sát giao thông đô thị thời gian thực ứng dụng AI Camera và Edge Computing', 'Nghiên cứu và triển khai hệ thống nhận diện vi phạm giao thông, đếm lưu lượng phương tiện và tối ưu pha đèn tín hiệu thời gian thực bằng mô hình YOLOv11 tối ưu hóa trên NVIDIA Jetson Orin.',
    2, 'PUBLISHED', 'lecturer-user-024', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
) ON CONFLICT (id) DO UPDATE SET
    title = EXCLUDED.title, description = EXCLUDED.description, status = EXCLUDED.status, updated_at = CURRENT_TIMESTAMP;
INSERT INTO thesis.thesis_topic_supervisor (id, topic_id, lecturer_id, supervisor_order, created_at)
VALUES (md5('44444444-4444-4444-4444-444444444011' || '-supervisor')::uuid, '44444444-4444-4444-4444-444444444011', 'lecturer-profile-024', 1, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;
INSERT INTO thesis.thesis_topic (
    id, round_id, department_id, title, description, max_groups, status, created_by, version, created_at, updated_at
) VALUES (
    '44444444-4444-4444-4444-444444444012', '22222222-2222-2222-2222-222222222101', 'department-demo',
    'Nền tảng phân tích cảm xúc khách hàng đa kênh với Big Data và Mô hình ngôn ngữ lớn LLM', 'Xây dựng pipeline xử lý dữ liệu lớn với Apache Kafka, Spark Streaming và phân loại cảm xúc phản hồi người dùng đa kênh bằng mô hình ngôn ngữ lớn tiếng Việt chuyên biệt.',
    2, 'PUBLISHED', 'lecturer-user-023', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
) ON CONFLICT (id) DO UPDATE SET
    title = EXCLUDED.title, description = EXCLUDED.description, status = EXCLUDED.status, updated_at = CURRENT_TIMESTAMP;
INSERT INTO thesis.thesis_topic_supervisor (id, topic_id, lecturer_id, supervisor_order, created_at)
VALUES (md5('44444444-4444-4444-4444-444444444012' || '-supervisor')::uuid, '44444444-4444-4444-4444-444444444012', 'lecturer-profile-023', 1, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;
INSERT INTO thesis.thesis_topic (
    id, round_id, department_id, title, description, max_groups, status, created_by, version, created_at, updated_at
) VALUES (
    '44444444-4444-4444-4444-444444444013', '22222222-2222-2222-2222-222222222101', 'department-fme-robotics',
    'Thiết kế và chế tạo Robot tự hành AGV vận chuyển hàng trong kho thông minh với định vị SLAM', 'Nghiên cứu chế tạo robot AGV công nghiệp chịu tải 200kg, tích hợp LiDAR 2D/3D, hệ điều hành ROS 2 và thuật toán lập bản đồ thời gian thực Cartographer SLAM.',
    2, 'PUBLISHED', 'lecturer-user-016', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
) ON CONFLICT (id) DO UPDATE SET
    title = EXCLUDED.title, description = EXCLUDED.description, status = EXCLUDED.status, updated_at = CURRENT_TIMESTAMP;
INSERT INTO thesis.thesis_topic_supervisor (id, topic_id, lecturer_id, supervisor_order, created_at)
VALUES (md5('44444444-4444-4444-4444-444444444013' || '-supervisor')::uuid, '44444444-4444-4444-4444-444444444013', 'lecturer-profile-016', 1, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;
INSERT INTO thesis.thesis_topic (
    id, round_id, department_id, title, description, max_groups, status, created_by, version, created_at, updated_at
) VALUES (
    '44444444-4444-4444-4444-444444444014', '22222222-2222-2222-2222-222222222101', 'department-feee-auto',
    'Hệ thống quản lý và tối ưu hóa lưới điện mặt trời áp mái phân tán ứng dụng IoT & AI', 'Dự báo sản lượng phát điện năng lượng mặt trời dựa trên mạng học sâu LSTM và tự động cân bằng phụ tải Microgrid điều khiển qua giao thức Modbus TCP.',
    2, 'PUBLISHED', 'lecturer-user-013', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
) ON CONFLICT (id) DO UPDATE SET
    title = EXCLUDED.title, description = EXCLUDED.description, status = EXCLUDED.status, updated_at = CURRENT_TIMESTAMP;
INSERT INTO thesis.thesis_topic_supervisor (id, topic_id, lecturer_id, supervisor_order, created_at)
VALUES (md5('44444444-4444-4444-4444-444444444014' || '-supervisor')::uuid, '44444444-4444-4444-4444-444444444014', 'lecturer-profile-013', 1, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;
INSERT INTO thesis.thesis_topic (
    id, round_id, department_id, title, description, max_groups, status, created_by, version, created_at, updated_at
) VALUES (
    '44444444-4444-4444-4444-444444444015', '22222222-2222-2222-2222-222222222101', 'department-fme-robotics',
    'Ứng dụng Digital Twin trong giám sát thời gian thực dây chuyền sản xuất tự động', 'Tạo bản sao kỹ thuật số 3D của dây chuyền lắp ráp công nghiệp, đồng bộ dữ liệu cảm biến thời gian thực qua MQTT và dự báo bảo trì sự cố bằng AI.',
    2, 'PUBLISHED', 'lecturer-user-018', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
) ON CONFLICT (id) DO UPDATE SET
    title = EXCLUDED.title, description = EXCLUDED.description, status = EXCLUDED.status, updated_at = CURRENT_TIMESTAMP;
INSERT INTO thesis.thesis_topic_supervisor (id, topic_id, lecturer_id, supervisor_order, created_at)
VALUES (md5('44444444-4444-4444-4444-444444444015' || '-supervisor')::uuid, '44444444-4444-4444-4444-444444444015', 'lecturer-profile-018', 1, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;
INSERT INTO thesis.thesis_topic (
    id, round_id, department_id, title, description, max_groups, status, created_by, version, created_at, updated_at
) VALUES (
    '44444444-4444-4444-4444-444444444016', '22222222-2222-2222-2222-222222222101', 'department-fme-automotive',
    'Nghiên cứu tối ưu hóa hệ thống quản lý năng lượng pin (BMS) trên xe điện thông minh', 'Thuật toán ước lượng trạng thái sạc SOC và trạng thái suy giảm SOH của cell pin Lithium-ion NMC bằng mạng nơ-ron sâu kết hợp bộ lọc Kalman mở rộng (EKF).',
    2, 'PUBLISHED', 'lecturer-user-017', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
) ON CONFLICT (id) DO UPDATE SET
    title = EXCLUDED.title, description = EXCLUDED.description, status = EXCLUDED.status, updated_at = CURRENT_TIMESTAMP;
INSERT INTO thesis.thesis_topic_supervisor (id, topic_id, lecturer_id, supervisor_order, created_at)
VALUES (md5('44444444-4444-4444-4444-444444444016' || '-supervisor')::uuid, '44444444-4444-4444-4444-444444444016', 'lecturer-profile-017', 1, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;
INSERT INTO thesis.thesis_topic (
    id, round_id, department_id, title, description, max_groups, status, created_by, version, created_at, updated_at
) VALUES (
    '44444444-4444-4444-4444-444444444017', '22222222-2222-2222-2222-222222222101', 'department-fce-civil',
    'Ứng dụng mô hình BIM 5D và tiêu chuẩn công trình xanh LEED trong quản lý vòng đời dự án', 'Tích hợp tiến độ thi công (4D) và chi phí dự toán (5D) trên Autodesk Revit & Navisworks, mô phỏng hiệu quả năng lượng công trình đạt chuẩn xanh LEED Gold.',
    2, 'PUBLISHED', 'lecturer-user-022', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
) ON CONFLICT (id) DO UPDATE SET
    title = EXCLUDED.title, description = EXCLUDED.description, status = EXCLUDED.status, updated_at = CURRENT_TIMESTAMP;
INSERT INTO thesis.thesis_topic_supervisor (id, topic_id, lecturer_id, supervisor_order, created_at)
VALUES (md5('44444444-4444-4444-4444-444444444017' || '-supervisor')::uuid, '44444444-4444-4444-4444-444444444017', 'lecturer-profile-022', 1, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;
INSERT INTO thesis.thesis_topic (
    id, round_id, department_id, title, description, max_groups, status, created_by, version, created_at, updated_at
) VALUES (
    '44444444-4444-4444-4444-444444444018', '22222222-2222-2222-2222-222222222101', 'department-foe-finance',
    'Nền tảng chấm điểm tín dụng vi mô ứng dụng Machine Learning và dữ liệu thay thế', 'Xây dựng thuật toán Credit Scoring cho khách hàng chưa có lịch sử tín dụng ngân hàng dựa trên hành vi giao dịch số, thanh toán hóa đơn và mạng xã hội.',
    2, 'PUBLISHED', 'lecturer-user-020', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
) ON CONFLICT (id) DO UPDATE SET
    title = EXCLUDED.title, description = EXCLUDED.description, status = EXCLUDED.status, updated_at = CURRENT_TIMESTAMP;
INSERT INTO thesis.thesis_topic_supervisor (id, topic_id, lecturer_id, supervisor_order, created_at)
VALUES (md5('44444444-4444-4444-4444-444444444018' || '-supervisor')::uuid, '44444444-4444-4444-4444-444444444018', 'lecturer-profile-020', 1, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;
INSERT INTO thesis.thesis_topic (
    id, round_id, department_id, title, description, max_groups, status, created_by, version, created_at, updated_at
) VALUES (
    '44444444-4444-4444-4444-444444444019', '22222222-2222-2222-2222-222222222101', 'department-foe-mis',
    'Hệ thống truy xuất nguồn gốc nông sản xuất khẩu chất lượng cao dựa trên Blockchain Hyperledger', 'Triển khai mạng riêng Blockchain Hyperledger Fabric lưu trữ bất biến các công đoạn gieo trồng, kiểm định chất lượng, đóng gói và quét mã QR xác thực quốc tế.',
    2, 'PUBLISHED', 'lecturer-user-019', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
) ON CONFLICT (id) DO UPDATE SET
    title = EXCLUDED.title, description = EXCLUDED.description, status = EXCLUDED.status, updated_at = CURRENT_TIMESTAMP;
INSERT INTO thesis.thesis_topic_supervisor (id, topic_id, lecturer_id, supervisor_order, created_at)
VALUES (md5('44444444-4444-4444-4444-444444444019' || '-supervisor')::uuid, '44444444-4444-4444-4444-444444444019', 'lecturer-profile-019', 1, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;
INSERT INTO thesis.thesis_topic (
    id, round_id, department_id, title, description, max_groups, status, created_by, version, created_at, updated_at
) VALUES (
    '44444444-4444-4444-4444-444444444020', '22222222-2222-2222-2222-222222222101', 'department-feee-telecom',
    'Nghiên cứu triển khai mạng riêng 5G Private Network cho nhà máy thông minh Smart Factory', 'Cấu hình kiến trúc mạng lõi 5G Standalone mã nguồn mở Open5GS, phân lát mạng Network Slicing cho hệ thống điều khiển tự động độ trễ cực thấp URLLC.',
    2, 'PUBLISHED', 'lecturer-user-014', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
) ON CONFLICT (id) DO UPDATE SET
    title = EXCLUDED.title, description = EXCLUDED.description, status = EXCLUDED.status, updated_at = CURRENT_TIMESTAMP;
INSERT INTO thesis.thesis_topic_supervisor (id, topic_id, lecturer_id, supervisor_order, created_at)
VALUES (md5('44444444-4444-4444-4444-444444444020' || '-supervisor')::uuid, '44444444-4444-4444-4444-444444444020', 'lecturer-profile-014', 1, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;
INSERT INTO thesis.thesis_topic (
    id, round_id, department_id, title, description, max_groups, status, created_by, version, created_at, updated_at
) VALUES (
    '44444444-4444-4444-4444-444444444021', '22222222-2222-2222-2222-222222222101', 'department-fme-automotive',
    'Hệ thống hỗ trợ lái xe nâng cao ADAS cảnh báo lệch làn và va chạm dựa trên Computer Vision', 'Thuật toán phát hiện vạch kẻ đường Lane Departure Warning và ước lượng khoảng cách phương tiện phía trước bằng camera đơn gắn trên kính chắn gió ô tô.',
    2, 'PUBLISHED', 'lecturer-user-017', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
) ON CONFLICT (id) DO UPDATE SET
    title = EXCLUDED.title, description = EXCLUDED.description, status = EXCLUDED.status, updated_at = CURRENT_TIMESTAMP;
INSERT INTO thesis.thesis_topic_supervisor (id, topic_id, lecturer_id, supervisor_order, created_at)
VALUES (md5('44444444-4444-4444-4444-444444444021' || '-supervisor')::uuid, '44444444-4444-4444-4444-444444444021', 'lecturer-profile-017', 1, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;
INSERT INTO thesis.thesis_topic (
    id, round_id, department_id, title, description, max_groups, status, created_by, version, created_at, updated_at
) VALUES (
    '44444444-4444-4444-4444-444444444022', '22222222-2222-2222-2222-222222222101', 'department-demo',
    'Phát hiện sớm tổn thương trên ảnh X-quang phổi sử dụng mạng nơ-ron tích chập sâu (Deep CNN)', 'Mô hình phân loại đa nhãn bệnh lý phổi (viêm phổi, tràn dịch, xẹp phổi) từ tệp ảnh y tế chuẩn DICOM với độ chính xác AUC-ROC > 0.94 và giải thích bằng Grad-CAM.',
    2, 'PUBLISHED', 'lecturer-user-024', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
) ON CONFLICT (id) DO UPDATE SET
    title = EXCLUDED.title, description = EXCLUDED.description, status = EXCLUDED.status, updated_at = CURRENT_TIMESTAMP;
INSERT INTO thesis.thesis_topic_supervisor (id, topic_id, lecturer_id, supervisor_order, created_at)
VALUES (md5('44444444-4444-4444-4444-444444444022' || '-supervisor')::uuid, '44444444-4444-4444-4444-444444444022', 'lecturer-profile-024', 1, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;
INSERT INTO thesis.thesis_topic (
    id, round_id, department_id, title, description, max_groups, status, created_by, version, created_at, updated_at
) VALUES (
    '44444444-4444-4444-4444-444444444023', '22222222-2222-2222-2222-222222222101', 'department-feee-telecom',
    'Mạng cảm biến LoRaWAN giám sát xâm nhập mặn và chất lượng nước vùng ĐBSCL', 'Thiết kế các nút cảm biến phao nổi đo độ mặn, pH, DO sử dụng năng lượng mặt trời, truyền thông không dây LoRaWAN bán kính 10km về trạm cảnh báo trung tâm.',
    2, 'PUBLISHED', 'lecturer-user-014', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
) ON CONFLICT (id) DO UPDATE SET
    title = EXCLUDED.title, description = EXCLUDED.description, status = EXCLUDED.status, updated_at = CURRENT_TIMESTAMP;
INSERT INTO thesis.thesis_topic_supervisor (id, topic_id, lecturer_id, supervisor_order, created_at)
VALUES (md5('44444444-4444-4444-4444-444444444023' || '-supervisor')::uuid, '44444444-4444-4444-4444-444444444023', 'lecturer-profile-014', 1, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;
INSERT INTO thesis.thesis_topic (
    id, round_id, department_id, title, description, max_groups, status, created_by, version, created_at, updated_at
) VALUES (
    '44444444-4444-4444-4444-444444444024', '22222222-2222-2222-2222-222222222101', 'department-fme-robotics',
    'Hệ thống tự động hóa kho hàng thông minh ứng dụng cánh tay Robot và thị giác máy 3D', 'Gắp và phân loại kiện hàng tự động bằng cánh tay robot 6 trục, nhận diện vị trí và góc nghiêng đồ vật dựa trên camera độ sâu Intel RealSense 3D.',
    2, 'PUBLISHED', 'lecturer-user-016', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
) ON CONFLICT (id) DO UPDATE SET
    title = EXCLUDED.title, description = EXCLUDED.description, status = EXCLUDED.status, updated_at = CURRENT_TIMESTAMP;
INSERT INTO thesis.thesis_topic_supervisor (id, topic_id, lecturer_id, supervisor_order, created_at)
VALUES (md5('44444444-4444-4444-4444-444444444024' || '-supervisor')::uuid, '44444444-4444-4444-4444-444444444024', 'lecturer-profile-016', 1, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;
INSERT INTO thesis.thesis_topic (
    id, round_id, department_id, title, description, max_groups, status, created_by, version, created_at, updated_at
) VALUES (
    '44444444-4444-4444-4444-444444444025', '22222222-2222-2222-2222-222222222101', 'department-demo',
    'Nền tảng học trực tuyến thích ứng cá nhân hóa lộ trình học tập bằng thuật toán AI', 'Hệ thống Adaptive Learning phân tích hành vi và kết quả bài kiểm tra của người học để tự động đề xuất tài liệu, bài tập và lộ trình bổ khuyết kiến thức tối ưu.',
    2, 'PUBLISHED', 'lecturer-user-023', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
) ON CONFLICT (id) DO UPDATE SET
    title = EXCLUDED.title, description = EXCLUDED.description, status = EXCLUDED.status, updated_at = CURRENT_TIMESTAMP;
INSERT INTO thesis.thesis_topic_supervisor (id, topic_id, lecturer_id, supervisor_order, created_at)
VALUES (md5('44444444-4444-4444-4444-444444444025' || '-supervisor')::uuid, '44444444-4444-4444-4444-444444444025', 'lecturer-profile-023', 1, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;
INSERT INTO thesis.thesis_topic (
    id, round_id, department_id, title, description, max_groups, status, created_by, version, created_at, updated_at
) VALUES (
    '44444444-4444-4444-4444-444444444026', '22222222-2222-2222-2222-222222222101', 'department-fit-ai',
    'Hệ thống Multi-Agent AI tự trị điều phối vận hành nhà kho logistics thông minh', 'Thiết kế kiến trúc đa tác tử Multi-Agent phối hợp tự động giữa robot AGV, trạm đóng gói và hệ thống quản lý kho WMS dựa trên mô hình ngôn ngữ lớn LLM.',
    2, 'PUBLISHED', 'lecturer-user-026', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
) ON CONFLICT (id) DO UPDATE SET
    title = EXCLUDED.title, description = EXCLUDED.description, status = EXCLUDED.status, updated_at = CURRENT_TIMESTAMP;
INSERT INTO thesis.thesis_topic_supervisor (id, topic_id, lecturer_id, supervisor_order, created_at)
VALUES (md5('44444444-4444-4444-4444-444444444026' || '-supervisor')::uuid, '44444444-4444-4444-4444-444444444026', 'lecturer-profile-026', 1, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;
INSERT INTO thesis.thesis_topic (
    id, round_id, department_id, title, description, max_groups, status, created_by, version, created_at, updated_at
) VALUES (
    '44444444-4444-4444-4444-444444444027', '22222222-2222-2222-2222-222222222101', 'department-foe-finance',
    'Nền tảng phát hiện gian lận giao dịch tài chính thời gian thực với Graph Neural Networks (GNN)', 'Phát hiện các mô hình rửa tiền và gian lận thẻ tín dụng phức tạp bằng cách mô hình hóa mạng lưới quan hệ tài khoản trên đồ thị và học sâu GNN.',
    2, 'PUBLISHED', 'lecturer-user-020', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
) ON CONFLICT (id) DO UPDATE SET
    title = EXCLUDED.title, description = EXCLUDED.description, status = EXCLUDED.status, updated_at = CURRENT_TIMESTAMP;
INSERT INTO thesis.thesis_topic_supervisor (id, topic_id, lecturer_id, supervisor_order, created_at)
VALUES (md5('44444444-4444-4444-4444-444444444027' || '-supervisor')::uuid, '44444444-4444-4444-4444-444444444027', 'lecturer-profile-020', 1, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;
INSERT INTO thesis.thesis_topic (
    id, round_id, department_id, title, description, max_groups, status, created_by, version, created_at, updated_at
) VALUES (
    '44444444-4444-4444-4444-444444444028', '22222222-2222-2222-2222-222222222101', 'department-feee-auto',
    'Thiết kế và chế tạo trạm sạc xe điện thông minh hai chiều V2G (Vehicle-to-Grid)', 'Hệ thống sạc nhanh DC 60kW tích hợp tính năng phát điện ngược từ xe vào lưới điện trong giờ cao điểm, quản trị thông minh qua giao thức OCPP 2.0.',
    2, 'PUBLISHED', 'lecturer-user-031', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
) ON CONFLICT (id) DO UPDATE SET
    title = EXCLUDED.title, description = EXCLUDED.description, status = EXCLUDED.status, updated_at = CURRENT_TIMESTAMP;
INSERT INTO thesis.thesis_topic_supervisor (id, topic_id, lecturer_id, supervisor_order, created_at)
VALUES (md5('44444444-4444-4444-4444-444444444028' || '-supervisor')::uuid, '44444444-4444-4444-4444-444444444028', 'lecturer-profile-031', 1, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;
INSERT INTO thesis.thesis_topic (
    id, round_id, department_id, title, description, max_groups, status, created_by, version, created_at, updated_at
) VALUES (
    '44444444-4444-4444-4444-444444444029', '22222222-2222-2222-2222-222222222101', 'department-fce-bim',
    'Ứng dụng thị giác máy tính và AI phân tích dữ liệu bay từ Drone trong giám sát công trình', 'Xử lý ảnh chụp từ Flycam/Drone dựng mô hình 3D đám mây điểm Point Cloud so sánh tiến độ thực tế với mô hình thiết kế BIM để cảnh báo sai lệch thi công.',
    2, 'PUBLISHED', 'lecturer-user-035', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
) ON CONFLICT (id) DO UPDATE SET
    title = EXCLUDED.title, description = EXCLUDED.description, status = EXCLUDED.status, updated_at = CURRENT_TIMESTAMP;
INSERT INTO thesis.thesis_topic_supervisor (id, topic_id, lecturer_id, supervisor_order, created_at)
VALUES (md5('44444444-4444-4444-4444-444444444029' || '-supervisor')::uuid, '44444444-4444-4444-4444-444444444029', 'lecturer-profile-035', 1, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;
INSERT INTO thesis.thesis_topic (
    id, round_id, department_id, title, description, max_groups, status, created_by, version, created_at, updated_at
) VALUES (
    '44444444-4444-4444-4444-444444444030', '22222222-2222-2222-2222-222222222101', 'department-fit-network',
    'Hệ thống bảo vệ hồ sơ bệnh án điện tử EHR ứng dụng mật mã đồng cấu và Zero-Knowledge Proof', 'Lưu trữ và chia sẻ dữ liệu y tế nhạy cảm giữa các bệnh viện mà không làm lộ thông tin cá nhân bệnh nhân nhờ mật mã ZKP và Homomorphic Encryption.',
    2, 'PUBLISHED', 'lecturer-user-028', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
) ON CONFLICT (id) DO UPDATE SET
    title = EXCLUDED.title, description = EXCLUDED.description, status = EXCLUDED.status, updated_at = CURRENT_TIMESTAMP;
INSERT INTO thesis.thesis_topic_supervisor (id, topic_id, lecturer_id, supervisor_order, created_at)
VALUES (md5('44444444-4444-4444-4444-444444444030' || '-supervisor')::uuid, '44444444-4444-4444-4444-444444444030', 'lecturer-profile-028', 1, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;
INSERT INTO thesis.thesis_topic (
    id, round_id, department_id, title, description, max_groups, status, created_by, version, created_at, updated_at
) VALUES (
    '44444444-4444-4444-4444-444444444031', '22222222-2222-2222-2222-222222222101', 'department-fme-robotics',
    'Nghiên cứu tối ưu hóa quỹ đạo cánh tay robot phẫu thuật dựa trên mạng học tăng cường Deep RL', 'Huấn luyện thuật toán điều khiển cánh tay robot thao tác phẫu thuật nội soi mềm mại, tránh va chạm mô cơ quan nhạy cảm với độ chính xác dưới milimet.',
    2, 'PUBLISHED', 'lecturer-user-032', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
) ON CONFLICT (id) DO UPDATE SET
    title = EXCLUDED.title, description = EXCLUDED.description, status = EXCLUDED.status, updated_at = CURRENT_TIMESTAMP;
INSERT INTO thesis.thesis_topic_supervisor (id, topic_id, lecturer_id, supervisor_order, created_at)
VALUES (md5('44444444-4444-4444-4444-444444444031' || '-supervisor')::uuid, '44444444-4444-4444-4444-444444444031', 'lecturer-profile-032', 1, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;
INSERT INTO thesis.thesis_topic (
    id, round_id, department_id, title, description, max_groups, status, created_by, version, created_at, updated_at
) VALUES (
    '44444444-4444-4444-4444-444444444032', '22222222-2222-2222-2222-222222222101', 'department-foe-logistics',
    'Hệ thống phân tích rủi ro chuỗi cung ứng toàn cầu ứng dụng dữ liệu vệ tinh và xử lý NLP', 'Tổng hợp tin tức kinh tế, thời tiết, đình công cảng biển trên toàn cầu bằng NLP và kết hợp dữ liệu giám sát tàu biển AIS để dự báo đứt gãy nguồn cung.',
    2, 'PUBLISHED', 'lecturer-user-034', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
) ON CONFLICT (id) DO UPDATE SET
    title = EXCLUDED.title, description = EXCLUDED.description, status = EXCLUDED.status, updated_at = CURRENT_TIMESTAMP;
INSERT INTO thesis.thesis_topic_supervisor (id, topic_id, lecturer_id, supervisor_order, created_at)
VALUES (md5('44444444-4444-4444-4444-444444444032' || '-supervisor')::uuid, '44444444-4444-4444-4444-444444444032', 'lecturer-profile-034', 1, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;
INSERT INTO thesis.thesis_topic (
    id, round_id, department_id, title, description, max_groups, status, created_by, version, created_at, updated_at
) VALUES (
    '44444444-4444-4444-4444-444444444033', '22222222-2222-2222-2222-222222222101', 'department-feee-telecom',
    'Thiết kế chip gia tốc AI chuyên dụng (NPU) trên nền tảng kiến trúc tập lệnh mở RISC-V', 'Thiết kế khối tính toán ma trận Systolic Array trên FPGA nhằm tăng tốc độ suy luận mô hình học sâu với điện năng tiêu thụ thấp cho thiết bị nhúng.',
    2, 'PUBLISHED', 'lecturer-user-014', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
) ON CONFLICT (id) DO UPDATE SET
    title = EXCLUDED.title, description = EXCLUDED.description, status = EXCLUDED.status, updated_at = CURRENT_TIMESTAMP;
INSERT INTO thesis.thesis_topic_supervisor (id, topic_id, lecturer_id, supervisor_order, created_at)
VALUES (md5('44444444-4444-4444-4444-444444444033' || '-supervisor')::uuid, '44444444-4444-4444-4444-444444444033', 'lecturer-profile-014', 1, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;
INSERT INTO thesis.thesis_topic (
    id, round_id, department_id, title, description, max_groups, status, created_by, version, created_at, updated_at
) VALUES (
    '44444444-4444-4444-4444-444444444034', '22222222-2222-2222-2222-222222222101', 'department-fce-civil',
    'Giải pháp quan trắc lún và chuyển vị nhà cao tầng thời gian thực bằng cảm biến MEMS', 'Mạng lưới cảm biến gia tốc và độ nghiêng MEMS không dây đo dao động của tòa nhà dưới tác động của gió bão và động đất, cảnh báo nguy hiểm thời gian thực.',
    2, 'PUBLISHED', 'lecturer-user-022', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
) ON CONFLICT (id) DO UPDATE SET
    title = EXCLUDED.title, description = EXCLUDED.description, status = EXCLUDED.status, updated_at = CURRENT_TIMESTAMP;
INSERT INTO thesis.thesis_topic_supervisor (id, topic_id, lecturer_id, supervisor_order, created_at)
VALUES (md5('44444444-4444-4444-4444-444444444034' || '-supervisor')::uuid, '44444444-4444-4444-4444-444444444034', 'lecturer-profile-022', 1, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;
INSERT INTO thesis.thesis_topic (
    id, round_id, department_id, title, description, max_groups, status, created_by, version, created_at, updated_at
) VALUES (
    '44444444-4444-4444-4444-444444444035', '22222222-2222-2222-2222-222222222101', 'department-fit-ai',
    'Hệ thống AI Camera phát hiện sớm đám cháy và khói phục vụ an toàn khu dân cư thông minh', 'Nhận diện khói và ngọn lửa từ camera giám sát công cộng bằng mô hình Vision Transformer, phân tích hướng lan và tự động gửi tin nhắn báo động cứu hỏa.',
    2, 'PUBLISHED', 'lecturer-user-024', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
) ON CONFLICT (id) DO UPDATE SET
    title = EXCLUDED.title, description = EXCLUDED.description, status = EXCLUDED.status, updated_at = CURRENT_TIMESTAMP;
INSERT INTO thesis.thesis_topic_supervisor (id, topic_id, lecturer_id, supervisor_order, created_at)
VALUES (md5('44444444-4444-4444-4444-444444444035' || '-supervisor')::uuid, '44444444-4444-4444-4444-444444444035', 'lecturer-profile-024', 1, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;
INSERT INTO thesis.thesis_topic (
    id, round_id, department_id, title, description, max_groups, status, created_by, version, created_at, updated_at
) VALUES (
    '44444444-4444-4444-4444-444444444036', '22222222-2222-2222-2222-222222222101', 'department-fit-software',
    'Nền tảng kiểm thử bảo mật tự động hợp đồng thông minh Smart Contract trên Blockchain', 'Phát hiện tự động các lỗ hổng Reentrancy, Integer Overflow, Flash Loan Attack trong mã nguồn Solidity bằng kỹ thuật Symbolic Execution và Fuzzing.',
    2, 'PUBLISHED', 'lecturer-user-027', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
) ON CONFLICT (id) DO UPDATE SET
    title = EXCLUDED.title, description = EXCLUDED.description, status = EXCLUDED.status, updated_at = CURRENT_TIMESTAMP;
INSERT INTO thesis.thesis_topic_supervisor (id, topic_id, lecturer_id, supervisor_order, created_at)
VALUES (md5('44444444-4444-4444-4444-444444444036' || '-supervisor')::uuid, '44444444-4444-4444-4444-444444444036', 'lecturer-profile-027', 1, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;
INSERT INTO thesis.thesis_topic (
    id, round_id, department_id, title, description, max_groups, status, created_by, version, created_at, updated_at
) VALUES (
    '44444444-4444-4444-4444-444444444037', '22222222-2222-2222-2222-222222222101', 'department-fme-automotive',
    'Mô phỏng khí động học và giảm thiểu lực cản xe điện bằng phần mềm CFD OpenFOAM', 'Phân tích dòng khí quyển xung quanh thân xe điện sedan, thiết kế cánh gió chủ động và tấm ốp gầm nhằm tăng tầm hoạt động của xe điện thêm 12%.',
    2, 'PUBLISHED', 'lecturer-user-033', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
) ON CONFLICT (id) DO UPDATE SET
    title = EXCLUDED.title, description = EXCLUDED.description, status = EXCLUDED.status, updated_at = CURRENT_TIMESTAMP;
INSERT INTO thesis.thesis_topic_supervisor (id, topic_id, lecturer_id, supervisor_order, created_at)
VALUES (md5('44444444-4444-4444-4444-444444444037' || '-supervisor')::uuid, '44444444-4444-4444-4444-444444444037', 'lecturer-profile-033', 1, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;
INSERT INTO thesis.thesis_topic (
    id, round_id, department_id, title, description, max_groups, status, created_by, version, created_at, updated_at
) VALUES (
    '44444444-4444-4444-4444-444444444038', '22222222-2222-2222-2222-222222222101', 'department-foe-logistics',
    'Hệ thống tự động lập kế hoạch và phân bổ nguồn lực container tại cảng biển thông minh', 'Thuật toán di truyền GA kết hợp học máy tối ưu hóa vị trí xếp dỡ container tại bãi cảng Cát Lái, giảm thời gian chờ đợi của xe đầu kéo container.',
    2, 'PUBLISHED', 'lecturer-user-034', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
) ON CONFLICT (id) DO UPDATE SET
    title = EXCLUDED.title, description = EXCLUDED.description, status = EXCLUDED.status, updated_at = CURRENT_TIMESTAMP;
INSERT INTO thesis.thesis_topic_supervisor (id, topic_id, lecturer_id, supervisor_order, created_at)
VALUES (md5('44444444-4444-4444-4444-444444444038' || '-supervisor')::uuid, '44444444-4444-4444-4444-444444444038', 'lecturer-profile-034', 1, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;
INSERT INTO thesis.thesis_topic (
    id, round_id, department_id, title, description, max_groups, status, created_by, version, created_at, updated_at
) VALUES (
    '44444444-4444-4444-4444-444444444039', '22222222-2222-2222-2222-222222222101', 'department-fme-robotics',
    'Nghiên cứu chế tạo thiết bị đo khúc xạ mắt tự động ứng dụng xử lý ảnh và trí tuệ nhân tạo', 'Thiết bị quang học gọn nhẹ chụp ảnh đáy mắt và phân tích phản xạ đồng tử bằng AI nhằm đo độ cận/loạn thị tự động cho học sinh vùng sâu vùng xa.',
    2, 'PUBLISHED', 'lecturer-user-016', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
) ON CONFLICT (id) DO UPDATE SET
    title = EXCLUDED.title, description = EXCLUDED.description, status = EXCLUDED.status, updated_at = CURRENT_TIMESTAMP;
INSERT INTO thesis.thesis_topic_supervisor (id, topic_id, lecturer_id, supervisor_order, created_at)
VALUES (md5('44444444-4444-4444-4444-444444444039' || '-supervisor')::uuid, '44444444-4444-4444-4444-444444444039', 'lecturer-profile-016', 1, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;
INSERT INTO thesis.thesis_topic (
    id, round_id, department_id, title, description, max_groups, status, created_by, version, created_at, updated_at
) VALUES (
    '44444444-4444-4444-4444-444444444040', '22222222-2222-2222-2222-222222222101', 'department-fit-ai',
    'Hệ thống tổng hợp và tóm tắt biên bản hội họp tự động bằng mô hình Whisper và LLM', 'Tự động nhận dạng giọng nói đa người họp tiếng Việt (Speaker Diarization), trích xuất các nhiệm vụ được giao và tạo biên bản tóm tắt định dạng chuẩn.',
    2, 'PUBLISHED', 'lecturer-user-026', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
) ON CONFLICT (id) DO UPDATE SET
    title = EXCLUDED.title, description = EXCLUDED.description, status = EXCLUDED.status, updated_at = CURRENT_TIMESTAMP;
INSERT INTO thesis.thesis_topic_supervisor (id, topic_id, lecturer_id, supervisor_order, created_at)
VALUES (md5('44444444-4444-4444-4444-444444444040' || '-supervisor')::uuid, '44444444-4444-4444-4444-444444444040', 'lecturer-profile-026', 1, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;
INSERT INTO thesis.thesis_group (
    id, round_id, leader_student_id, topic_id, status, approval_status, approved_by, approved_at, version, created_at, updated_at
) VALUES (
    '55555555-5555-5555-5555-555544444011', '22222222-2222-2222-2222-222222222101', 'student-profile-002', '44444444-4444-4444-4444-444444444011',
    'ASSIGNED', 'APPROVED', 'lecturer-user-024', CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
) ON CONFLICT (id) DO NOTHING;
INSERT INTO thesis.thesis_group_member (
    id, group_id, round_id, student_id, member_order, is_leader, created_at
) VALUES (
    md5('55555555-5555-5555-5555-555544444011' || 'student-profile-002')::uuid, '55555555-5555-5555-5555-555544444011', '22222222-2222-2222-2222-222222222101',
    'student-profile-002', 1, TRUE, CURRENT_TIMESTAMP
) ON CONFLICT (id) DO NOTHING;
INSERT INTO thesis.thesis_group_member (
    id, group_id, round_id, student_id, member_order, is_leader, created_at
) VALUES (
    md5('55555555-5555-5555-5555-555544444011' || 'student-profile-003')::uuid, '55555555-5555-5555-5555-555544444011', '22222222-2222-2222-2222-222222222101',
    'student-profile-003', 2, FALSE, CURRENT_TIMESTAMP
) ON CONFLICT (id) DO NOTHING;
INSERT INTO thesis.thesis_group (
    id, round_id, leader_student_id, topic_id, status, approval_status, approved_by, approved_at, version, created_at, updated_at
) VALUES (
    '55555555-5555-5555-5555-555544444012', '22222222-2222-2222-2222-222222222101', 'student-profile-004', '44444444-4444-4444-4444-444444444012',
    'ASSIGNED', 'APPROVED', 'lecturer-user-023', CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
) ON CONFLICT (id) DO NOTHING;
INSERT INTO thesis.thesis_group_member (
    id, group_id, round_id, student_id, member_order, is_leader, created_at
) VALUES (
    md5('55555555-5555-5555-5555-555544444012' || 'student-profile-004')::uuid, '55555555-5555-5555-5555-555544444012', '22222222-2222-2222-2222-222222222101',
    'student-profile-004', 1, TRUE, CURRENT_TIMESTAMP
) ON CONFLICT (id) DO NOTHING;
INSERT INTO thesis.thesis_group_member (
    id, group_id, round_id, student_id, member_order, is_leader, created_at
) VALUES (
    md5('55555555-5555-5555-5555-555544444012' || 'student-profile-005')::uuid, '55555555-5555-5555-5555-555544444012', '22222222-2222-2222-2222-222222222101',
    'student-profile-005', 2, FALSE, CURRENT_TIMESTAMP
) ON CONFLICT (id) DO NOTHING;
INSERT INTO thesis.thesis_group (
    id, round_id, leader_student_id, topic_id, status, approval_status, approved_by, approved_at, version, created_at, updated_at
) VALUES (
    '55555555-5555-5555-5555-555544444013', '22222222-2222-2222-2222-222222222101', 'student-profile-006', '44444444-4444-4444-4444-444444444013',
    'ASSIGNED', 'APPROVED', 'lecturer-user-016', CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
) ON CONFLICT (id) DO NOTHING;
INSERT INTO thesis.thesis_group_member (
    id, group_id, round_id, student_id, member_order, is_leader, created_at
) VALUES (
    md5('55555555-5555-5555-5555-555544444013' || 'student-profile-006')::uuid, '55555555-5555-5555-5555-555544444013', '22222222-2222-2222-2222-222222222101',
    'student-profile-006', 1, TRUE, CURRENT_TIMESTAMP
) ON CONFLICT (id) DO NOTHING;
INSERT INTO thesis.thesis_group_member (
    id, group_id, round_id, student_id, member_order, is_leader, created_at
) VALUES (
    md5('55555555-5555-5555-5555-555544444013' || 'student-profile-007')::uuid, '55555555-5555-5555-5555-555544444013', '22222222-2222-2222-2222-222222222101',
    'student-profile-007', 2, FALSE, CURRENT_TIMESTAMP
) ON CONFLICT (id) DO NOTHING;
INSERT INTO thesis.thesis_group (
    id, round_id, leader_student_id, topic_id, status, approval_status, approved_by, approved_at, version, created_at, updated_at
) VALUES (
    '55555555-5555-5555-5555-555544444014', '22222222-2222-2222-2222-222222222101', 'student-profile-008', '44444444-4444-4444-4444-444444444014',
    'ASSIGNED', 'APPROVED', 'lecturer-user-013', CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
) ON CONFLICT (id) DO NOTHING;
INSERT INTO thesis.thesis_group_member (
    id, group_id, round_id, student_id, member_order, is_leader, created_at
) VALUES (
    md5('55555555-5555-5555-5555-555544444014' || 'student-profile-008')::uuid, '55555555-5555-5555-5555-555544444014', '22222222-2222-2222-2222-222222222101',
    'student-profile-008', 1, TRUE, CURRENT_TIMESTAMP
) ON CONFLICT (id) DO NOTHING;
INSERT INTO thesis.thesis_group_member (
    id, group_id, round_id, student_id, member_order, is_leader, created_at
) VALUES (
    md5('55555555-5555-5555-5555-555544444014' || 'student-profile-009')::uuid, '55555555-5555-5555-5555-555544444014', '22222222-2222-2222-2222-222222222101',
    'student-profile-009', 2, FALSE, CURRENT_TIMESTAMP
) ON CONFLICT (id) DO NOTHING;
INSERT INTO thesis.thesis_group (
    id, round_id, leader_student_id, topic_id, status, approval_status, approved_by, approved_at, version, created_at, updated_at
) VALUES (
    '55555555-5555-5555-5555-555544444016', '22222222-2222-2222-2222-222222222101', 'student-profile-010', '44444444-4444-4444-4444-444444444016',
    'ASSIGNED', 'APPROVED', 'lecturer-user-017', CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
) ON CONFLICT (id) DO NOTHING;
INSERT INTO thesis.thesis_group_member (
    id, group_id, round_id, student_id, member_order, is_leader, created_at
) VALUES (
    md5('55555555-5555-5555-5555-555544444016' || 'student-profile-010')::uuid, '55555555-5555-5555-5555-555544444016', '22222222-2222-2222-2222-222222222101',
    'student-profile-010', 1, TRUE, CURRENT_TIMESTAMP
) ON CONFLICT (id) DO NOTHING;
INSERT INTO thesis.thesis_group_member (
    id, group_id, round_id, student_id, member_order, is_leader, created_at
) VALUES (
    md5('55555555-5555-5555-5555-555544444016' || 'student-profile-011')::uuid, '55555555-5555-5555-5555-555544444016', '22222222-2222-2222-2222-222222222101',
    'student-profile-011', 2, FALSE, CURRENT_TIMESTAMP
) ON CONFLICT (id) DO NOTHING;
INSERT INTO thesis.thesis_group (
    id, round_id, leader_student_id, topic_id, status, approval_status, approved_by, approved_at, version, created_at, updated_at
) VALUES (
    '55555555-5555-5555-5555-555544444017', '22222222-2222-2222-2222-222222222101', 'student-profile-012', '44444444-4444-4444-4444-444444444017',
    'ASSIGNED', 'APPROVED', 'lecturer-user-022', CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
) ON CONFLICT (id) DO NOTHING;
INSERT INTO thesis.thesis_group_member (
    id, group_id, round_id, student_id, member_order, is_leader, created_at
) VALUES (
    md5('55555555-5555-5555-5555-555544444017' || 'student-profile-012')::uuid, '55555555-5555-5555-5555-555544444017', '22222222-2222-2222-2222-222222222101',
    'student-profile-012', 1, TRUE, CURRENT_TIMESTAMP
) ON CONFLICT (id) DO NOTHING;
INSERT INTO thesis.thesis_group_member (
    id, group_id, round_id, student_id, member_order, is_leader, created_at
) VALUES (
    md5('55555555-5555-5555-5555-555544444017' || 'student-profile-013')::uuid, '55555555-5555-5555-5555-555544444017', '22222222-2222-2222-2222-222222222101',
    'student-profile-013', 2, FALSE, CURRENT_TIMESTAMP
) ON CONFLICT (id) DO NOTHING;
INSERT INTO thesis.thesis_group (
    id, round_id, leader_student_id, topic_id, status, approval_status, approved_by, approved_at, version, created_at, updated_at
) VALUES (
    '55555555-5555-5555-5555-555544444026', '22222222-2222-2222-2222-222222222101', 'student-profile-014', '44444444-4444-4444-4444-444444444026',
    'ASSIGNED', 'APPROVED', 'lecturer-user-026', CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
) ON CONFLICT (id) DO NOTHING;
INSERT INTO thesis.thesis_group_member (
    id, group_id, round_id, student_id, member_order, is_leader, created_at
) VALUES (
    md5('55555555-5555-5555-5555-555544444026' || 'student-profile-014')::uuid, '55555555-5555-5555-5555-555544444026', '22222222-2222-2222-2222-222222222101',
    'student-profile-014', 1, TRUE, CURRENT_TIMESTAMP
) ON CONFLICT (id) DO NOTHING;
INSERT INTO thesis.thesis_group_member (
    id, group_id, round_id, student_id, member_order, is_leader, created_at
) VALUES (
    md5('55555555-5555-5555-5555-555544444026' || 'student-profile-015')::uuid, '55555555-5555-5555-5555-555544444026', '22222222-2222-2222-2222-222222222101',
    'student-profile-015', 2, FALSE, CURRENT_TIMESTAMP
) ON CONFLICT (id) DO NOTHING;
INSERT INTO thesis.thesis_group (
    id, round_id, leader_student_id, topic_id, status, approval_status, approved_by, approved_at, version, created_at, updated_at
) VALUES (
    '55555555-5555-5555-5555-555544444027', '22222222-2222-2222-2222-222222222101', 'student-profile-016', '44444444-4444-4444-4444-444444444027',
    'ASSIGNED', 'APPROVED', 'lecturer-user-020', CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
) ON CONFLICT (id) DO NOTHING;
INSERT INTO thesis.thesis_group_member (
    id, group_id, round_id, student_id, member_order, is_leader, created_at
) VALUES (
    md5('55555555-5555-5555-5555-555544444027' || 'student-profile-016')::uuid, '55555555-5555-5555-5555-555544444027', '22222222-2222-2222-2222-222222222101',
    'student-profile-016', 1, TRUE, CURRENT_TIMESTAMP
) ON CONFLICT (id) DO NOTHING;
INSERT INTO thesis.thesis_group_member (
    id, group_id, round_id, student_id, member_order, is_leader, created_at
) VALUES (
    md5('55555555-5555-5555-5555-555544444027' || 'student-profile-017')::uuid, '55555555-5555-5555-5555-555544444027', '22222222-2222-2222-2222-222222222101',
    'student-profile-017', 2, FALSE, CURRENT_TIMESTAMP
) ON CONFLICT (id) DO NOTHING;

-- ---------------------------------------------------------------------
-- 7. RICH CAMPUS ANNOUNCEMENTS (SCHOLARSHIPS, EXAMS, CAREER, OJT)
-- ---------------------------------------------------------------------
INSERT INTO engagement."Announcement" (
    id, title, content, priority, "targetRoles", "targetYears", "isGlobal",
    "publishAt", "publishedBy", "semesterId", "semesterName", "createdAt", "updatedAt", version
) VALUES (
    'announcement-ute-samsung-scholarship-2026', 'Học bổng Tài năng Công nghệ Samsung Talent Program (STP) 2026', '<p>Trung tâm Nghiên cứu & Phát triển Samsung Việt Nam (SRV) phối hợp cùng Trường Đại học Sư phạm Kỹ thuật TP.HCM thông báo chương trình Học bổng Samsung Talent Program 2026:</p>
<ul>
  <li><strong>Giá trị học bổng:</strong> 54.000.000 VNĐ/suất tài trợ trực tiếp và tài trợ hoàn toàn khóa học Thuật toán nâng cao & Đồ án tốt nghiệp tại Samsung Lab.</li>
  <li><strong>Đối tượng dự tuyển:</strong> Sinh viên năm 3 và năm 4 các ngành Công nghệ Thông tin, Kỹ thuật Máy tính, Điện tử Viễn thông có GPA &ge; 2.80.</li>
  <li><strong>Đặc quyền:</strong> Được tiếp nhận chính thức vào làm việc tại Samsung R&D sau khi tốt nghiệp mà không phải trải qua kỳ thi tuyển dụng thông thường.</li>
  <li><strong>Hạn chót nhận hồ sơ:</strong> Trước 17:00 ngày 15/10/2026 tại Cổng thông tin Sinh viên.</li>
</ul>', 'HIGH', ARRAY['STUDENT']::text[], ARRAY[]::integer[], TRUE,
    CURRENT_TIMESTAMP, 'Phòng Công Tác Sinh Viên & SRV', 'semester-demo', 'Học kỳ 1 năm học 2026-2027',
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1
) ON CONFLICT (id) DO UPDATE SET
    title = EXCLUDED.title, content = EXCLUDED.content, priority = EXCLUDED.priority,
    "targetRoles" = EXCLUDED."targetRoles", "publishedBy" = EXCLUDED."publishedBy", "updatedAt" = CURRENT_TIMESTAMP;
INSERT INTO engagement."Announcement" (
    id, title, content, priority, "targetRoles", "targetYears", "isGlobal",
    "publishAt", "publishedBy", "semesterId", "semesterName", "createdAt", "updatedAt", version
) VALUES (
    'announcement-ute-intel-stem-women-2026', 'Chương trình Học bổng Nữ sinh Kỹ thuật Intel STEM Scholarship 2026', '<p>Tập đoàn Intel Products Vietnam công bố gói học bổng khuyến khích tài năng nữ trong khối ngành kỹ thuật công nghệ:</p>
<ul>
  <li><strong>Mức học bổng:</strong> 30.000.000 VNĐ/suất cùng cơ hội thực tập có lương tại nhà máy Intel TP.HCM.</li>
  <li><strong>Điều kiện:</strong> Nữ sinh viên hệ chính quy các ngành Cơ khí, Điện - Điện tử, Tự động hóa, CNTT có kết quả học tập xếp loại Giỏi hoặc đạt giải thưởng NCKH.</li>
  <li><strong>Thời gian phỏng vấn:</strong> Dự kiến từ ngày 20/10/2026 đến ngày 25/10/2026.</li>
</ul>', 'HIGH', ARRAY['STUDENT']::text[], ARRAY[]::integer[], TRUE,
    CURRENT_TIMESTAMP, 'Phòng Quan Hệ Doanh Nghiệp & Intel', 'semester-demo', 'Học kỳ 1 năm học 2026-2027',
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1
) ON CONFLICT (id) DO UPDATE SET
    title = EXCLUDED.title, content = EXCLUDED.content, priority = EXCLUDED.priority,
    "targetRoles" = EXCLUDED."targetRoles", "publishedBy" = EXCLUDED."publishedBy", "updatedAt" = CURRENT_TIMESTAMP;
INSERT INTO engagement."Announcement" (
    id, title, content, priority, "targetRoles", "targetYears", "isGlobal",
    "publishAt", "publishedBy", "semesterId", "semesterName", "createdAt", "updatedAt", version
) VALUES (
    'announcement-ute-fpt-ojt-career-day', 'Ngày hội Tuyển dụng Thực tập tốt nghiệp OJT & Fresher cùng FPT Software 2026', '<p>Phòng Quan hệ Doanh nghiệp trân trọng thông báo Ngày hội phỏng vấn tuyển dụng thực tập doanh nghiệp (OJT Kỳ 1 năm học 2026-2027):</p>
<ul>
  <li><strong>Vị trí tuyển dụng:</strong> 150 chỉ tiêu Thực tập sinh & Kỹ sư Fresher: Java Backend, React/Node.js Fullstack, Embedded Systems, C/C++ Automotive, AI/Data Engineer.</li>
  <li><strong>Thời gian tổ chức:</strong> 08:00 - 16:30 Thứ Sáu, ngày 25/09/2026 tại Sảnh Lớn Tòa nhà Trung tâm.</li>
  <li><strong>Quy trình:</strong> Phỏng vấn trực tiếp 1-1 với Technical Lead và nhận thư mời thực tập (Offer Letter) ngay trong ngày.</li>
</ul>', 'URGENT', ARRAY['STUDENT']::text[], ARRAY[]::integer[], TRUE,
    CURRENT_TIMESTAMP, 'Phòng Quan Hệ Doanh Nghiệp & FPT', 'semester-demo', 'Học kỳ 1 năm học 2026-2027',
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1
) ON CONFLICT (id) DO UPDATE SET
    title = EXCLUDED.title, content = EXCLUDED.content, priority = EXCLUDED.priority,
    "targetRoles" = EXCLUDED."targetRoles", "publishedBy" = EXCLUDED."publishedBy", "updatedAt" = CURRENT_TIMESTAMP;
INSERT INTO engagement."Announcement" (
    id, title, content, priority, "targetRoles", "targetYears", "isGlobal",
    "publishAt", "publishedBy", "semesterId", "semesterName", "createdAt", "updatedAt", version
) VALUES (
    'announcement-ute-final-exam-regulations-2026', 'Thông báo Lịch thi kết thúc học phần & Quy định kiểm tra an ninh phòng thi', '<p>Phòng Khảo thí & Đảm bảo Chất lượng giáo dục ban hành lịch thi tập trung và quy chế phòng thi:</p>
<ul>
  <li><strong>Thời gian thi tập trung:</strong> Bắt đầu từ ngày 20/12/2026 đến hết ngày 10/01/2027.</li>
  <li><strong>Yêu cầu giấy tờ:</strong> Sinh viên bắt buộc xuất trình Thẻ sinh viên có tích hợp chip hoặc Căn cước công dân gắn chip khi vào phòng thi.</li>
  <li><strong>Nghiêm cấm tuyệt đối:</strong> Mang thiết bị truyền tin không dây, đồng hồ thông minh, tài liệu không được phép vào phòng thi. Vi phạm sẽ bị lập biên bản đình chỉ thi và nhận điểm F học phần.</li>
</ul>', 'URGENT', ARRAY['STUDENT', 'LECTURER']::text[], ARRAY[]::integer[], TRUE,
    CURRENT_TIMESTAMP, 'Phòng Khảo Thí & ĐBCL', 'semester-demo', 'Học kỳ 1 năm học 2026-2027',
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1
) ON CONFLICT (id) DO UPDATE SET
    title = EXCLUDED.title, content = EXCLUDED.content, priority = EXCLUDED.priority,
    "targetRoles" = EXCLUDED."targetRoles", "publishedBy" = EXCLUDED."publishedBy", "updatedAt" = CURRENT_TIMESTAMP;
INSERT INTO engagement."Announcement" (
    id, title, content, priority, "targetRoles", "targetYears", "isGlobal",
    "publishAt", "publishedBy", "semesterId", "semesterName", "createdAt", "updatedAt", version
) VALUES (
    'announcement-ute-innovation-awards-2026', 'Phát động Giải thưởng Sinh viên Nghiên cứu Khoa học UTE Innovation Award 2026', '<p>Nhằm thúc đẩy phong trào sáng tạo học thuật, Nhà trường chính thức phát động giải thưởng NCKH sinh viên năm học 2026-2027:</p>
<ul>
  <li><strong>Tổng kinh phí giải thưởng:</strong> Hơn 300.000.000 VNĐ cùng kinh phí hỗ trợ chế tạo mẫu thử nghiệm lên đến 20.000.000 VNĐ/đề tài.</li>
  <li><strong>Lĩnh vực dự thi:</strong> Trí tuệ nhân tạo, Thiết kế vi mạch, Tự động hóa công nghiệp, Ô tô điện thông minh, Năng lượng xanh và Chuyển đổi số.</li>
  <li><strong>Thời hạn đăng ký đề cương:</strong> Hạn chót ngày 30/10/2026 tại Văn phòng Đoàn trường hoặc gửi về email: nckh.sv@campuscore.edu.</li>
</ul>', 'NORMAL', ARRAY['STUDENT', 'LECTURER']::text[], ARRAY[]::integer[], TRUE,
    CURRENT_TIMESTAMP, 'Phòng Khoa Học Công Nghệ & Đoàn Trường', 'semester-demo', 'Học kỳ 1 năm học 2026-2027',
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1
) ON CONFLICT (id) DO UPDATE SET
    title = EXCLUDED.title, content = EXCLUDED.content, priority = EXCLUDED.priority,
    "targetRoles" = EXCLUDED."targetRoles", "publishedBy" = EXCLUDED."publishedBy", "updatedAt" = CURRENT_TIMESTAMP;

-- ---------------------------------------------------------------------
-- 8. COMPREHENSIVE RAG VECTOR KNOWLEDGE BASE (assistant.knowledge_document)
-- ---------------------------------------------------------------------
INSERT INTO assistant.knowledge_document
    (id, slug, locale, title, content, source, priority, domain)
SELECT md5(seed.slug || '-document')::uuid, seed.slug, seed.locale, seed.title, seed.content,
       'campuscore-bigdata-enrichment-v43', seed.priority, seed.domain
FROM (VALUES
    ('english-exit-benchmark-toeic-ielts-vi', 'vi', 'Quy định chuẩn đầu ra ngoại ngữ TOEIC, IELTS và chứng chỉ tương đương', 'Quy định chuẩn đầu ra tiếng Anh đối với sinh viên đại học chính quy: 1. Chuẩn đầu ra theo ngành: Sinh viên khối ngành kỹ thuật và công nghệ (CNTT, Điện, Cơ khí, Xây dựng) đạt tối thiểu TOEIC 500 điểm hoặc IELTS 5.0, VSTEP bậc 3 (B1). Sinh viên khối ngành kinh tế và chương trình chất lượng cao đạt tối thiểu TOEIC 600 điểm hoặc IELTS 5.5, VSTEP bậc 4 (B2). 2. Quy trình nộp chứng chỉ: Sinh viên đăng tải bản quét màu chứng chỉ quốc tế còn hiệu lực (trong vòng 2 năm) lên cổng học vụ tại mục ''Chuẩn đầu ra ngoại ngữ'' và nộp bản gốc đối chiếu tại Phòng Đào tạo (Phòng A1-202). 3. Thời hạn nộp: Chậm nhất 30 ngày trước đợt xét tốt nghiệp chính thức của học kỳ.', 12, 'POLICY'),
    ('english-exit-benchmark-toeic-ielts-en', 'en', 'English exit language proficiency standards (TOEIC, IELTS, VSTEP)', 'Exit English language proficiency benchmark policies: 1. Departmental thresholds: Engineering and Technology students must attain at least TOEIC 500 or IELTS 5.0 / VSTEP Level 3 (B1). Economics and High-Quality programs require TOEIC 600 or IELTS 5.5 / VSTEP Level 4 (B2). 2. Submission protocol: Upload color scans of valid certificates (within 2 years of issue) to the Student Portal under ''Exit Benchmarks'' and present original hardcopies for verification at Academic Affairs (Room A1-202). 3. Deadline: At least 30 calendar days prior to the graduation council review session.', 12, 'POLICY'),
    ('it-skills-exit-benchmark-mos-ic3-vi', 'vi', 'Chuẩn đầu ra kỹ năng ứng dụng CNTT quốc tế (MOS, IC3)', 'Quy định chuẩn kỹ năng công nghệ thông tin tốt nghiệp: 1. Yêu cầu chứng chỉ: Sinh viên tất cả các ngành đào tạo phải hoàn thành chuẩn tin học quốc tế: Chứng chỉ MOS (Microsoft Office Specialist) đạt tối thiểu 700/1000 điểm cho 3 môn (Word, Excel, PowerPoint) hoặc Chứng chỉ IC3 (Internet and Computing Core Certification) GS5/GS6. 2. Miễn trừ chuẩn tin học: Sinh viên chuyên ngành Công nghệ thông tin và Kỹ thuật phần mềm được tự động công nhận hoàn thành chuẩn tin học sau khi tích lũy đủ các học phần cơ sở ngành liên quan.', 14, 'POLICY'),
    ('it-skills-exit-benchmark-mos-ic3-en', 'en', 'International IT competencies exit benchmark (MOS, IC3)', 'Graduation IT competencies benchmark requirements: 1. Certified standards: Undergraduates of all majors must satisfy international IT certification: Microsoft Office Specialist (MOS) with a minimum score of 700/1000 across 3 modules (Word, Excel, PowerPoint) or IC3 GS5/GS6 certification. 2. Major exemptions: Information Technology and Software Engineering majors are automatically exempted upon successful completion of core computer science curriculum prerequisites.', 14, 'POLICY'),
    ('graduation-thesis-eligibility-defense-vi', 'vi', 'Điều kiện đăng ký và quy trình bảo vệ khóa luận tốt nghiệp (KLTN)', 'Điều kiện học phần Khóa luận tốt nghiệp và đồ án cử nhân/kỹ sư: 1. Điều kiện tiên quyết: Sinh viên tích lũy tối thiểu 110 tín chỉ, điểm trung bình tích lũy CPA >= 2.00, hoàn thành các học phần chuyên ngành cốt lõi và không bị kỷ luật học vụ. 2. Số lượng thành viên: Mỗi đề tài thực hiện theo nhóm từ 1 đến tối đa 3 sinh viên dưới sự hướng dẫn của tối đa 2 giảng viên. 3. Đánh giá hội đồng: Khóa luận được đánh giá qua 3 cột điểm độc lập: Giảng viên hướng dẫn (30%), Giảng viên phản biện (20%) và Hội đồng bảo vệ trực tiếp (50%). Điểm tổng kết đạt từ 5.5/10 (điểm C) trở lên được công nhận đạt học phần tốt nghiệp.', 10, 'THESIS'),
    ('graduation-thesis-eligibility-defense-en', 'en', 'Graduation thesis eligibility criteria and defense examination protocol', 'Regulations regarding graduation thesis eligibility and defense proceedings: 1. Prerequisites: Accumulation of >= 110 academic credits, cumulative CPA >= 2.00, completion of core specialized courses, and no unresolved disciplinary probation. 2. Group allocation: Capstone topics are undertaken by teams of 1 to 3 students under supervision of up to 2 faculty mentors. 3. Council scoring: Final grade comprises Supervisor evaluation (30%), Reviewer appraisal (20%), and Oral Defense Council deliberation (50%). A minimum composite score of 5.5/10 (Letter grade C) is mandatory for graduation credit conferral.', 10, 'THESIS'),
    ('enterprise-internship-ojt-procedure-vi', 'vi', 'Quy trình thực tập doanh nghiệp (OJT) và nộp báo cáo thực tập', 'Hướng dẫn học phần Thực tập tốt nghiệp ngoài doanh nghiệp (On-the-Job Training - OJT): 1. Kế hoạch đăng ký: Thời lượng tối thiểu 8 đến 12 tuần làm việc thực tế toàn thời gian tại các doanh nghiệp đối tác có ký kết thỏa thuận hợp tác (MOU) với Nhà trường hoặc doanh nghiệp do sinh viên tự đề xuất được Khoa phê duyệt. 2. Nhật ký và Báo cáo: Sinh viên ghi nhận nhật ký công việc định kỳ hàng tuần có xác nhận của cán bộ hướng dẫn doanh nghiệp. Kết thúc đợt thực tập, nộp Báo cáo thực tập có dấu mộc đỏ và Phiếu đánh giá năng lực của đơn vị tiếp nhận về Văn phòng Bộ môn.', 14, 'POLICY'),
    ('enterprise-internship-ojt-procedure-en', 'en', 'On-the-Job Training (OJT) enterprise internship guidelines and reporting', 'Guidelines for enterprise graduation internships (OJT): 1. Placement protocol: Minimum duration of 8 to 12 weeks full-time operational engagement at verified enterprise partners possessing academic MOUs or vetted student-sourced companies approved by the Department. 2. Logbooks and reporting: Interns maintain weekly activity logs validated by enterprise mentors. Upon conclusion, submit a formal Internship Dossier with corporate seal and Mentor Assessment to the Department Office.', 14, 'POLICY'),
    ('academic-scholarship-criteria-vi', 'vi', 'Tiêu chí xét học bổng khuyến khích học tập và học bổng doanh nghiệp', 'Chính sách học bổng học tập dành cho sinh viên chính quy: 1. Học bổng khuyến khích học tập (HBKKHT): Xét theo từng học kỳ dựa trên điểm trung bình học kỳ (GPA) và điểm rèn luyện (ĐRL). Mức Xuất sắc (GPA >= 3.60 và ĐRL >= 90): 120% định mức học phí; Mức Giỏi (GPA >= 3.20 và ĐRL >= 80): 100% học phí; Mức Khá (GPA >= 2.50 và ĐRL >= 70): 80% học phí. 2. Học bổng tài trợ doanh nghiệp: Các tập đoàn Samsung, Intel, Bosch, VNPT, FPT tài trợ học bổng thường niên từ 20 đến 50 triệu đồng/suất cho sinh viên có thành tích xuất sắc trong học thuật và NCKH.', 15, 'POLICY'),
    ('academic-scholarship-criteria-en', 'en', 'Academic merit scholarship criteria and corporate sponsored grants', 'Scholarship grant policies for full-time undergraduates: 1. University Merit Scholarships: Evaluated per semester based on semester GPA and Conduct Points (DRL). Excellent tier (GPA >= 3.60 & DRL >= 90): 120% tuition quota; Very Good tier (GPA >= 3.20 & DRL >= 80): 100% tuition quota; Good tier (GPA >= 2.50 & DRL >= 70): 80% tuition quota. 2. Corporate endowment grants: Samsung, Intel, Bosch, VNPT, and FPT sponsor annual grants of 20 to 50 million VND per recipient for outstanding scholastic and research leadership.', 15, 'POLICY'),
    ('exam-deferral-re-evaluation-rules-vi', 'vi', 'Quy định hoãn thi kết thúc học phần và thủ tục phúc khảo bài thi', 'Quy định về hoãn thi và chấm phúc khảo bài thi cuối kỳ: 1. Thủ tục hoãn thi: Sinh viên bị ốm đau, tai nạn hoặc sự cố bất khả kháng cần nộp Đơn xin hoãn thi kèm giấy xác nhận của bệnh viện cấp huyện trở lên về Phòng Đào tạo trong vòng 03 ngày làm việc kể từ ngày thi. Sinh viên được dự thi bù vào kỳ thi gần nhất mà không tính thêm học phí. 2. Phúc khảo bài thi: Trong vòng 07 ngày làm việc kể từ ngày công bố điểm thi trên hệ thống, sinh viên có quyền nộp đơn phúc khảo tại Phòng Khảo thí. Bài thi được hai giảng viên chấm độc lập đối chiếu ma trận đáp án và công bố điểm chính thức sau 10 ngày.', 16, 'POLICY'),
    ('exam-deferral-re-evaluation-rules-en', 'en', 'Final exam deferrals protocol and grade re-evaluation appeals', 'Procedures regarding examination deferrals and regrading appeals: 1. Exam deferral: Students experiencing illness, accidents, or force majeure must submit a Deferral Application accompanied by district-level hospital medical documentation to Academic Affairs within 3 working days of the exam date. Makeup exams are scheduled during subsequent sessions without supplemental tuition. 2. Grade appeals: Within 7 business days of grade publication, students may petition for regrading at the Testing Office. Two independent examiners regrade against the rubric, with finalized scores published within 10 days.', 16, 'POLICY'),
    ('tuition-exemption-reduction-support-vi', 'vi', 'Chính sách miễn giảm học phí và thủ tục vay vốn tín dụng sinh viên', 'Chính sách an sinh và hỗ trợ tài chính sinh viên: 1. Miễn giảm học phí: Thực hiện theo Nghị định 81/2021/NĐ-CP của Chính phủ. Các đối tượng được miễn 100% học phí gồm: Sinh viên khuyết tật nặng, sinh viên mồ côi cả cha lẫn mẹ, con liệt sĩ, thương binh; Giảm 70% học phí cho sinh viên người dân tộc thiểu số tại vùng có điều kiện kinh tế - xã hội đặc biệt khó khăn. 2. Vay vốn tín dụng học tập: Nhà trường xác nhận giấy đề nghị vay vốn Ngân hàng Chính sách Xã hội (NHCSXH) với hạn mức tối đa 4.000.000 VNĐ/tháng để trang trải chi phí học tập và sinh hoạt.', 18, 'POLICY'),
    ('tuition-exemption-reduction-support-en', 'en', 'Tuition exemption policies and governmental student loan support', 'Financial aid and tuition relief policies: 1. Tuition exemptions: Pursuant to governmental Decree 81/2021/ND-CP, 100% tuition exemptions apply to students with severe disabilities, orphans, children of martyrs, and war invalids. 70% reductions apply to ethnic minority students from socio-economically disadvantaged regions. 2. Student educational loans: The University certifies student loan dossiers for the Bank for Social Policies (VBSP) granting credit lines up to 4,000,000 VND/month for academic and living sustenance.', 18, 'POLICY'),
    ('dormitory-campus-amenities-services-vi', 'vi', 'Cẩm nang lưu trú Ký túc xá và hệ thống tiện ích dịch vụ sinh viên UTE', 'Hướng dẫn đời sống nội trú và tiện ích học tập trong khuôn viên trường: 1. Ký túc xá sinh viên: Ký túc xá Khu A và Khu B có sức chứa hơn 4.000 sinh viên, trang bị đầy đủ điều hòa, Wi-Fi tốc độ cao, hệ thống thẻ từ an ninh ra vào 24/7 và camera giám sát hành lang. Giờ mở cửa ký túc xá từ 05:00 đến 23:00 hàng ngày. 2. Tiện ích học tập và rèn luyện: Thư viện trung tâm phục vụ 24/7 trong mùa thi, phòng tự học thông minh Smart Study Zone, nhà thi đấu đa năng, sân bóng đá cỏ nhân tạo và chuỗi căng tin đạt chuẩn an toàn vệ sinh thực phẩm.', 20, 'POLICY'),
    ('dormitory-campus-amenities-services-en', 'en', 'Campus dormitory residency guide and comprehensive student amenities', 'Residential and campus facilities handbook: 1. Student Dormitories: Campus Dormitories Area A and Area B accommodate over 4,000 residents, fully equipped with air conditioning, high-speed Wi-Fi, 24/7 smart RFID access control, and security surveillance. Daily operating hours span 05:00 to 23:00. 2. Learning & recreation amenities: Central Library offering 24/7 study access during finals, Smart Study Zones, multi-purpose sports complex, synthetic turf soccer pitches, and certified hygienic student cafeterias.', 20, 'POLICY')
) AS seed(slug, locale, title, content, priority, domain)
WHERE NOT EXISTS (SELECT 1 FROM assistant.knowledge_document existing WHERE existing.slug = seed.slug);

INSERT INTO assistant.knowledge_document_revision
    (id, document_id, version, state, locale, slug, title, content, source, priority, created_by, reviewed_by, published_at, domain)
SELECT md5(d.id::text || '-revision-1')::uuid, d.id, 1, 'PUBLISHED', d.locale, d.slug, d.title, d.content,
       d.source, d.priority, 'system-seed', 'system-seed', CURRENT_TIMESTAMP, d.domain
FROM assistant.knowledge_document d
WHERE d.source = 'campuscore-bigdata-enrichment-v43'
  AND NOT EXISTS (SELECT 1 FROM assistant.knowledge_document_revision r WHERE r.document_id = d.id AND r.version = 1);

WITH canonical AS (
    SELECT d.id::text AS source_id, r.version, COALESCE(r.domain, d.domain, 'POLICY') AS domain,
           d.slug, d.locale, d.title, d.content, d.source, d.priority
    FROM assistant.knowledge_document d
    JOIN assistant.knowledge_document_revision r ON r.document_id = d.id AND r.state = 'PUBLISHED'
    WHERE d.active = TRUE AND d.visibility = 'PUBLIC'
), summary AS (
    SELECT encode(digest(COALESCE(string_agg(concat_ws('|', source_id, domain, slug, locale, title, content, source, priority::text, version::text), E'\n' ORDER BY source_id), ''), 'sha256'), 'hex') AS corpus_hash,
           COUNT(*)::integer AS row_count,
           COALESCE(jsonb_agg(jsonb_build_object('sourceId', source_id, 'domain', domain, 'slug', slug, 'locale', locale) ORDER BY source_id), '[]'::jsonb) AS documents
    FROM canonical
)
INSERT INTO assistant.knowledge_release (id, corpus_version, corpus_hash, row_count, source, status, manifest, created_by, activated_at, previous_release_id)
SELECT '00000000-0000-0000-0000-000000000043'::uuid, 'local-demo-v43', corpus_hash, row_count, 'MANUAL', 'PUBLISHED',
       jsonb_build_object('schemaVersion', 1, 'corpusVersion', 'local-demo-v43', 'rowCount', row_count, 'sha256', corpus_hash, 'documents', documents),
       'system-migration', CURRENT_TIMESTAMP,
       (SELECT active_release_id FROM assistant.knowledge_runtime_state WHERE singleton = TRUE)
FROM summary
WHERE NOT EXISTS (SELECT 1 FROM assistant.knowledge_release WHERE id = '00000000-0000-0000-0000-000000000043'::uuid);

INSERT INTO assistant.knowledge_runtime_document
    (release_id, source_id, revision_id, version, domain, slug, locale, title, content, source, priority, active, visibility, published_at)
SELECT '00000000-0000-0000-0000-000000000043'::uuid, d.id::text, r.id, r.version,
       COALESCE(r.domain, d.domain, 'POLICY'), d.slug, d.locale, d.title, d.content, d.source, d.priority,
       TRUE, 'PUBLIC', COALESCE(r.published_at, CURRENT_TIMESTAMP)
FROM assistant.knowledge_document d
JOIN assistant.knowledge_document_revision r ON r.document_id = d.id AND r.state = 'PUBLISHED'
WHERE d.active = TRUE AND d.visibility = 'PUBLIC'
  AND NOT EXISTS (
      SELECT 1 FROM assistant.knowledge_runtime_document p
      WHERE p.release_id = '00000000-0000-0000-0000-000000000043'::uuid AND p.source_id = d.id::text
  );

INSERT INTO assistant.knowledge_runtime_state (singleton, active_release_id)
VALUES (TRUE, '00000000-0000-0000-0000-000000000043'::uuid)
ON CONFLICT (singleton) DO UPDATE
SET active_release_id = EXCLUDED.active_release_id, updated_at = CURRENT_TIMESTAMP;
