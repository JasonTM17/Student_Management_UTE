package io.campuscore.restfulapi.academic;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.PreparedStatement;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "persistence"})
@TestPropertySource(properties = {
        "migration.academic-read.enabled=true",
        "spring.flyway.enabled=false"
})
class AcademicReadPersistenceTest {

    private static final Instant BASE_TIME = Instant.parse("2026-08-20T00:00:00Z");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mvc;

    @BeforeEach
    void prepareReadOnlyFixture() {
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS \"academic\"");
        jdbc.execute("DROP TABLE IF EXISTS \"academic\".\"Section\"");
        jdbc.execute("DROP TABLE IF EXISTS \"academic\".\"Lecturer\"");
        jdbc.execute("DROP TABLE IF EXISTS \"academic\".\"CurriculumCourse\"");
        jdbc.execute("DROP TABLE IF EXISTS \"academic\".\"Curriculum\"");
        jdbc.execute("DROP TABLE IF EXISTS \"academic\".\"Course\"");
        jdbc.execute("DROP TABLE IF EXISTS \"academic\".\"Department\"");
        jdbc.execute("DROP TABLE IF EXISTS \"academic\".\"Faculty\"");
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "academic"."AcademicYear" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "year" INTEGER NOT NULL,
                    "startDate" TIMESTAMP NOT NULL,
                    "endDate" TIMESTAMP NOT NULL,
                    "isCurrent" BOOLEAN NOT NULL,
                    "createdAt" TIMESTAMP NOT NULL,
                    "updatedAt" TIMESTAMP NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE "academic"."Faculty" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "name" VARCHAR(200) NOT NULL,
                    "nameEn" VARCHAR(200),
                    "nameVi" VARCHAR(200),
                    "code" VARCHAR(40) NOT NULL,
                    "description" VARCHAR(1000),
                    "descriptionEn" VARCHAR(1000),
                    "descriptionVi" VARCHAR(1000),
                    "dean" VARCHAR(200),
                    "phone" VARCHAR(80),
                    "email" VARCHAR(200),
                    "building" VARCHAR(120),
                    "createdAt" TIMESTAMP NOT NULL DEFAULT TIMESTAMP '2026-08-20 00:00:00',
                    "updatedAt" TIMESTAMP NOT NULL DEFAULT TIMESTAMP '2026-08-20 00:00:00',
                    "isActive" BOOLEAN NOT NULL DEFAULT TRUE
                )
                """);
        jdbc.execute("""
                CREATE TABLE "academic"."Department" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "name" VARCHAR(200) NOT NULL,
                    "nameEn" VARCHAR(200),
                    "nameVi" VARCHAR(200),
                    "code" VARCHAR(40) NOT NULL,
                    "description" VARCHAR(1000),
                    "descriptionEn" VARCHAR(1000),
                    "descriptionVi" VARCHAR(1000),
                    "chair" VARCHAR(200),
                    "phone" VARCHAR(80),
                    "email" VARCHAR(200),
                    "building" VARCHAR(120),
                    "facultyId" VARCHAR(120) NOT NULL,
                    "createdAt" TIMESTAMP NOT NULL DEFAULT TIMESTAMP '2026-08-20 00:00:00',
                    "updatedAt" TIMESTAMP NOT NULL DEFAULT TIMESTAMP '2026-08-20 00:00:00',
                    "isActive" BOOLEAN NOT NULL DEFAULT TRUE
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "academic"."Semester" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "name" VARCHAR(200) NOT NULL,
                    "nameEn" VARCHAR(200),
                    "nameVi" VARCHAR(200),
                    "type" VARCHAR(40) NOT NULL,
                    "academicYearId" VARCHAR(120) NOT NULL,
                    "startDate" TIMESTAMP NOT NULL,
                    "endDate" TIMESTAMP NOT NULL,
                    "registrationStart" TIMESTAMP,
                    "registrationEnd" TIMESTAMP,
                    "addDropStart" TIMESTAMP,
                    "addDropEnd" TIMESTAMP,
                    "status" VARCHAR(40) NOT NULL,
                    "createdAt" TIMESTAMP NOT NULL,
                    "updatedAt" TIMESTAMP NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "academic"."Course" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "code" VARCHAR(40) NOT NULL,
                    "name" VARCHAR(200) NOT NULL,
                    "nameEn" VARCHAR(200),
                    "nameVi" VARCHAR(200),
                    "description" VARCHAR(1000),
                    "descriptionEn" VARCHAR(1000),
                    "descriptionVi" VARCHAR(1000),
                    "credits" INTEGER NOT NULL,
                    "departmentId" VARCHAR(120) NOT NULL,
                    "semesterId" VARCHAR(120),
                    "isActive" BOOLEAN NOT NULL,
                    "createdAt" TIMESTAMP NOT NULL,
                    "updatedAt" TIMESTAMP NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE "academic"."Curriculum" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "name" VARCHAR(200) NOT NULL,
                    "nameEn" VARCHAR(200),
                    "nameVi" VARCHAR(200),
                    "code" VARCHAR(40) NOT NULL,
                    "departmentId" VARCHAR(120) NOT NULL,
                    "academicYearId" VARCHAR(120) NOT NULL,
                    "semesterId" VARCHAR(120),
                    "totalCredits" INTEGER NOT NULL,
                    "description" VARCHAR(1000),
                    "descriptionEn" VARCHAR(1000),
                    "descriptionVi" VARCHAR(1000),
                    "isActive" BOOLEAN NOT NULL DEFAULT TRUE,
                    "createdAt" TIMESTAMP NOT NULL DEFAULT TIMESTAMP '2026-08-20 00:00:00',
                    "updatedAt" TIMESTAMP NOT NULL DEFAULT TIMESTAMP '2026-08-20 00:00:00'
                )
                """);
        jdbc.execute("""
                CREATE TABLE "academic"."CurriculumCourse" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "curriculumId" VARCHAR(120) NOT NULL,
                    "courseId" VARCHAR(120) NOT NULL,
                    "year" INTEGER NOT NULL,
                    "semester" INTEGER NOT NULL,
                    "isMandatory" BOOLEAN NOT NULL DEFAULT TRUE
                )
                """);
        jdbc.execute("""
                CREATE TABLE "academic"."Lecturer" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "userId" VARCHAR(120) NOT NULL,
                    "departmentId" VARCHAR(120) NOT NULL,
                    "employeeId" VARCHAR(80) NOT NULL,
                    "title" VARCHAR(120),
                    "specialization" VARCHAR(200),
                    "office" VARCHAR(120),
                    "phone" VARCHAR(80),
                    "createdAt" TIMESTAMP NOT NULL DEFAULT TIMESTAMP '2026-08-20 00:00:00',
                    "updatedAt" TIMESTAMP NOT NULL DEFAULT TIMESTAMP '2026-08-20 00:00:00',
                    "isActive" BOOLEAN NOT NULL DEFAULT TRUE
                )
                """);
        jdbc.execute("DROP TABLE IF EXISTS \"academic\".\"Section\"");
        jdbc.execute("DROP TABLE IF EXISTS \"academic\".\"Classroom\"");
        jdbc.execute("""
                CREATE TABLE "academic"."Classroom" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "building" VARCHAR(120) NOT NULL,
                    "roomNumber" VARCHAR(120) NOT NULL,
                    "capacity" INTEGER NOT NULL DEFAULT 0,
                    "type" VARCHAR(80) NOT NULL DEFAULT 'CLASSROOM',
                    "isActive" BOOLEAN NOT NULL DEFAULT TRUE,
                    "createdAt" TIMESTAMP NOT NULL DEFAULT TIMESTAMP '2026-08-20 00:00:00',
                    "updatedAt" TIMESTAMP NOT NULL DEFAULT TIMESTAMP '2026-08-20 00:00:00'
                )
                """);
        jdbc.execute("""
                CREATE TABLE "academic"."Section" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "sectionNumber" VARCHAR(40) NOT NULL,
                    "courseId" VARCHAR(120) NOT NULL,
                    "semesterId" VARCHAR(120) NOT NULL,
                    "lecturerId" VARCHAR(120),
                    "classroomId" VARCHAR(120),
                    "capacity" INTEGER NOT NULL,
                    "enrolledCount" INTEGER NOT NULL,
                    "status" VARCHAR(40) NOT NULL
                )
                """);
        jdbc.update("DELETE FROM \"academic\".\"Section\"");
        jdbc.update("DELETE FROM \"academic\".\"Classroom\"");
        jdbc.update("DELETE FROM \"academic\".\"CurriculumCourse\"");
        jdbc.update("DELETE FROM \"academic\".\"Curriculum\"");
        jdbc.update("DELETE FROM \"academic\".\"Course\"");
        jdbc.update("DELETE FROM \"academic\".\"Lecturer\"");
        jdbc.update("DELETE FROM \"academic\".\"Semester\"");
        jdbc.update("DELETE FROM \"academic\".\"Department\"");
        jdbc.update("DELETE FROM \"academic\".\"Faculty\"");
        jdbc.update("DELETE FROM \"academic\".\"AcademicYear\"");
    }

    @Test
    void semestersPreserveLegacyListEnvelopeOrderingAndHydration() throws Exception {
        insertAcademicYear("ay-2026", 2026, true);
        insertSemester("fall-2026", "Fall", null, null, "FALL", "ay-2026", "2026-09-01T00:00:00Z");
        insertSemester("spring-2026", "Spring", "Spring 2026 Custom", null, "SPRING", "ay-2026", "2026-01-01T00:00:00Z");

        mvc.perform(get("/api/v1/semesters")
                        .queryParam("page", "1")
                        .queryParam("limit", "1")
                        .with(jwt().jwt(token -> token.subject("student-user"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value("fall-2026"))
                .andExpect(jsonPath("$.data[0].nameEn").value("Fall"))
                .andExpect(jsonPath("$.data[0].nameVi").value("Học kỳ Thu 2026"))
                .andExpect(jsonPath("$.data[0].academicYear.year").value(2026))
                .andExpect(jsonPath("$.data[0].startDate").value("2026-09-01T00:00:00.000Z"))
                .andExpect(jsonPath("$.meta.total").value(2))
                .andExpect(jsonPath("$.meta.totalPages").value(2));

        mvc.perform(get("/api/v1/semesters/spring-2026")
                        .with(jwt().jwt(token -> token.subject("lecturer-user"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("spring-2026"))
                .andExpect(jsonPath("$.nameEn").value("Spring 2026 Custom"))
                .andExpect(jsonPath("$.nameVi").value("Học kỳ Xuân 2026"));
    }

    @Test
    void academicYearsPreserveLegacyListEnvelopeOrderingAndSemesters() throws Exception {
        insertAcademicYear("ay-2025", 2025, false);
        insertAcademicYear("ay-2026", 2026, true);
        insertSemester("spring-2026", "Spring", null, null, "SPRING", "ay-2026", "2026-01-01T00:00:00Z");
        insertSemester("fall-2026", "Fall", null, null, "FALL", "ay-2026", "2026-09-01T00:00:00Z");
        insertSemester("fall-2025", "Fall", null, null, "FALL", "ay-2025", "2025-09-01T00:00:00Z");

        mvc.perform(get("/api/v1/academic-years")
                        .queryParam("page", "1")
                        .queryParam("limit", "1")
                        .with(jwt().jwt(token -> token.subject("student-user"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value("ay-2026"))
                .andExpect(jsonPath("$.data[0].isCurrent").value(true))
                .andExpect(jsonPath("$.data[0].semesters.length()").value(2))
                .andExpect(jsonPath("$.data[0].semesters[0].id").value("fall-2026"))
                .andExpect(jsonPath("$.data[0].semesters[1].id").value("spring-2026"))
                .andExpect(jsonPath("$.meta.total").value(2))
                .andExpect(jsonPath("$.meta.totalPages").value(2));

        mvc.perform(get("/api/v1/academic-years/ay-2025")
                        .with(jwt().jwt(token -> token.subject("lecturer-user"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.year").value(2025))
                .andExpect(jsonPath("$.semesters[0].id").value("fall-2025"));
    }

    @Test
    void facultiesPreserveLegacyAdminListEnvelopeHydrationAndDetailDepartments() throws Exception {
        insertFaculty("faculty-cs", "Computer Science", null, null, "FCS", null, null, null);
        insertFaculty("faculty-business", "Business", null, null, "FBA", null, null, null);
        insertDepartment(
                "department-se",
                "Software Engineering",
                null,
                null,
                "SE",
                null,
                null,
                null,
                "faculty-cs",
                "Dr. SE");
        insertDepartment(
                "department-ce",
                "Computer Engineering",
                null,
                null,
                "CE",
                null,
                null,
                null,
                "faculty-cs",
                "Dr. CE");

        mvc.perform(get("/api/v1/faculties")
                        .queryParam("page", "1")
                        .queryParam("limit", "1")
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value("faculty-business"))
                .andExpect(jsonPath("$.data[0].nameEn").value("Business"))
                .andExpect(jsonPath("$.data[0].nameVi").value("Khoa Quản trị kinh doanh"))
                .andExpect(jsonPath("$.data[0].descriptionEn").value("Faculty of Business Administration"))
                .andExpect(jsonPath("$.data[0].departments.length()").value(0))
                .andExpect(jsonPath("$.meta.total").value(2))
                .andExpect(jsonPath("$.meta.totalPages").value(2));

        mvc.perform(get("/api/v1/faculties")
                        .with(studentJwt()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mvc.perform(get("/api/v1/faculties/faculty-cs")
                        .with(studentJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("faculty-cs"))
                .andExpect(jsonPath("$.nameEn").value("Computer Science"))
                .andExpect(jsonPath("$.descriptionVi").value("Khoa Khoa học máy tính và công nghệ thông tin"))
                .andExpect(jsonPath("$.departments.length()").value(2))
                .andExpect(jsonPath("$.departments[0].id").value("department-ce"))
                .andExpect(jsonPath("$.departments[0].nameVi").value("Kỹ thuật máy tính"))
                .andExpect(jsonPath("$.departments[1].id").value("department-se"))
                .andExpect(jsonPath("$.departments[1].nameVi").value("Kỹ thuật phần mềm"));
    }

    @Test
    void departmentsPreserveLegacyAdminListEnvelopeFacultyAndDetailLecturers() throws Exception {
        insertFaculty("faculty-cs", "Computer Science", null, null, "FCS", null, null, null);
        insertFaculty("faculty-business", "Business", null, null, "FBA", null, null, null);
        insertDepartment(
                "department-se",
                "Software Engineering",
                null,
                null,
                "SE",
                null,
                null,
                null,
                "faculty-cs",
                "Dr. SE");
        insertDepartment(
                "department-ba",
                "Business Administration",
                null,
                null,
                "BA",
                null,
                null,
                null,
                "faculty-business",
                "Dr. BA");
        insertLecturer("lecturer-2", "user-2", "department-se", "EMP-02", "Senior Lecturer");
        insertLecturer("lecturer-1", "user-1", "department-se", "EMP-01", "Lecturer");

        mvc.perform(get("/api/v1/departments")
                        .queryParam("page", "1")
                        .queryParam("limit", "20")
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value("department-ba"))
                .andExpect(jsonPath("$.data[0].nameVi").value("Quản trị kinh doanh"))
                .andExpect(jsonPath("$.data[0].faculty.code").value("FBA"))
                .andExpect(jsonPath("$.data[0].faculty.nameEn").value("Business"))
                .andExpect(jsonPath("$.data[0].faculty.descriptionEn").value("Faculty of Business Administration"))
                .andExpect(jsonPath("$.data[1].id").value("department-se"))
                .andExpect(jsonPath("$.meta.total").value(2));

        mvc.perform(get("/api/v1/departments")
                        .with(studentJwt()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mvc.perform(get("/api/v1/departments/department-se")
                        .with(studentJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("department-se"))
                .andExpect(jsonPath("$.nameEn").value("Software Engineering"))
                .andExpect(jsonPath("$.nameVi").value("Kỹ thuật phần mềm"))
                .andExpect(jsonPath("$.faculty.nameVi").value("Khoa Khoa học máy tính"))
                .andExpect(jsonPath("$.lecturers.length()").value(2))
                .andExpect(jsonPath("$.lecturers[0].id").value("lecturer-1"))
                .andExpect(jsonPath("$.lecturers[0].employeeId").value("EMP-01"))
                .andExpect(jsonPath("$.lecturers[1].id").value("lecturer-2"));
    }

    @Test
    void coursesPreserveLegacyListEnvelopeDepartmentAndHydration() throws Exception {
        insertAcademicYear("ay-2026", 2026, true);
        insertSemester("fall-2026", "Fall", null, null, "FALL", "ay-2026", "2026-09-01T00:00:00Z");
        insertDepartment("department-se", "Software Engineering", null, null, "SE", null, null, null);
        insertCourse("se401", "SE401", "Web Development", null, null, "department-se", "fall-2026", 3);
        insertCourse("cs101", "CS101", "Intro", "Intro Custom", "Nhap tuy chinh", "department-se", null, 4);

        mvc.perform(get("/api/v1/courses")
                        .queryParam("page", "1")
                        .queryParam("limit", "20")
                        .with(jwt().jwt(token -> token.subject("student-user"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value("cs101"))
                .andExpect(jsonPath("$.data[0].nameEn").value("Intro Custom"))
                .andExpect(jsonPath("$.data[0].nameVi").value("Nhap tuy chinh"))
                .andExpect(jsonPath("$.data[0].department.nameEn").value("Software Engineering"))
                .andExpect(jsonPath("$.data[0].department.nameVi").value("Kỹ thuật phần mềm"))
                .andExpect(jsonPath("$.data[1].id").value("se401"))
                .andExpect(jsonPath("$.data[1].nameVi").value("Phát triển web"))
                .andExpect(jsonPath("$.meta.total").value(2));

        mvc.perform(get("/api/v1/courses/se401")
                        .with(jwt().jwt(token -> token.subject("student-user"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SE401"))
                .andExpect(jsonPath("$.credits").value(3))
                .andExpect(jsonPath("$.semesterId").value("fall-2026"))
                .andExpect(jsonPath("$.department.code").value("SE"));
    }

    @Test
    void curriculaPreserveLegacyListEnvelopeDepartmentAndDetailCourses() throws Exception {
        insertAcademicYear("ay-2026", 2026, true);
        insertSemester("fall-2026", "Fall", null, null, "FALL", "ay-2026", "2026-09-01T00:00:00Z");
        insertFaculty("faculty-cs", "Computer Science", null, null, "FCS", null, null, null);
        insertDepartment(
                "department-cs",
                "Computer Science",
                null,
                null,
                "CSE",
                null,
                null,
                null,
                "faculty-cs",
                "Dr. CS");
        insertDepartment(
                "department-se",
                "Software Engineering",
                null,
                null,
                "SE",
                null,
                null,
                null,
                "faculty-cs",
                "Dr. SE");
        insertCourse("cs101", "CS101", "Intro", null, null, "department-cs", "fall-2026", 4);
        insertCourse("se401", "SE401", "Web Development", null, null, "department-se", "fall-2026", 3);
        insertCurriculum("curriculum-se", "Software Engineering 2025", null, null, "SE2025", "department-se", "ay-2026", null, 152);
        insertCurriculum("curriculum-cs", "Computer Science 2026", null, null, "CS2026", "department-cs", "ay-2026", "fall-2026", 150);
        insertCurriculumCourse("curriculum-course-2", "curriculum-cs", "se401", 2, 1, false);
        insertCurriculumCourse("curriculum-course-1", "curriculum-cs", "cs101", 1, 1, true);

        mvc.perform(get("/api/v1/curricula")
                        .queryParam("page", "1")
                        .queryParam("limit", "1")
                        .with(studentJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value("curriculum-cs"))
                .andExpect(jsonPath("$.data[0].nameEn").value("Computer Science 2026"))
                .andExpect(jsonPath("$.data[0].nameVi").value("Chương trình Khoa học máy tính 2026"))
                .andExpect(jsonPath("$.data[0].descriptionVi").value("Chương trình Khoa học máy tính cho khóa tuyển sinh 2026"))
                .andExpect(jsonPath("$.data[0].department.code").value("CSE"))
                .andExpect(jsonPath("$.data[0].department.nameVi").value("Khoa học máy tính"))
                .andExpect(jsonPath("$.data[0].courses.length()").value(0))
                .andExpect(jsonPath("$.meta.total").value(2))
                .andExpect(jsonPath("$.meta.totalPages").value(2));

        mvc.perform(get("/api/v1/curricula/curriculum-cs")
                        .with(studentJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("curriculum-cs"))
                .andExpect(jsonPath("$.semesterId").value("fall-2026"))
                .andExpect(jsonPath("$.totalCredits").value(150))
                .andExpect(jsonPath("$.courses.length()").value(2))
                .andExpect(jsonPath("$.courses[0].id").value("curriculum-course-1"))
                .andExpect(jsonPath("$.courses[0].courseId").value("cs101"))
                .andExpect(jsonPath("$.courses[0].isMandatory").value(true))
                .andExpect(jsonPath("$.courses[1].id").value("curriculum-course-2"))
                .andExpect(jsonPath("$.courses[1].isMandatory").value(false));
    }

    @Test
    void classroomsPreserveLegacyListEnvelopeOrderingAndDetailSections() throws Exception {
        insertAcademicYear("ay-2026", 2026, true);
        insertSemester("fall-2026", "Fall", null, null, "FALL", "ay-2026", "2026-09-01T00:00:00Z");
        insertClassroom("classroom-b202", "B", "202", 60, "LECTURE_HALL");
        insertClassroom("classroom-a101", "A", "101", 40, "LAB");
        insertClassroomSection("section-2", "B", "course-1", "fall-2026", "lecturer-1", "classroom-a101", 40, 35);
        insertClassroomSection("section-1", "A", "course-1", "fall-2026", "lecturer-1", "classroom-a101", 40, 20);

        mvc.perform(get("/api/v1/classrooms")
                        .queryParam("page", "1")
                        .queryParam("limit", "20")
                        .with(jwt().jwt(token -> token.subject("student-user"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value("classroom-a101"))
                .andExpect(jsonPath("$.data[0].building").value("A"))
                .andExpect(jsonPath("$.data[0].roomNumber").value("101"))
                .andExpect(jsonPath("$.data[0].capacity").value(40))
                .andExpect(jsonPath("$.data[0].type").value("LAB"))
                .andExpect(jsonPath("$.data[0].sections.length()").value(0))
                .andExpect(jsonPath("$.data[1].id").value("classroom-b202"))
                .andExpect(jsonPath("$.meta.total").value(2));

        mvc.perform(get("/api/v1/classrooms/classroom-a101")
                        .with(jwt().jwt(token -> token.subject("student-user"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive").value(true))
                .andExpect(jsonPath("$.sections.length()").value(2))
                .andExpect(jsonPath("$.sections[0].sectionNumber").value("A"))
                .andExpect(jsonPath("$.sections[1].sectionNumber").value("B"))
                .andExpect(jsonPath("$.sections[1].enrolledCount").value(35));
    }

    @Test
    void readBoundaryFailsClosedForAnonymousInvalidPagesAndUnexpectedQuery() throws Exception {
        mvc.perform(get("/api/v1/semesters"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        mvc.perform(get("/api/v1/courses")
                        .queryParam("limit", "201")
                        .with(jwt().jwt(token -> token.subject("student-user"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mvc.perform(get("/api/v1/semesters")
                        .queryParam("page", "1", "2")
                        .with(jwt().jwt(token -> token.subject("student-user"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mvc.perform(get("/api/v1/courses")
                        .queryParam("status", "ACTIVE")
                        .with(jwt().jwt(token -> token.subject("student-user"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mvc.perform(get("/api/v1/courses/missing")
                        .with(jwt().jwt(token -> token.subject("student-user"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HTTP_404"));

        mvc.perform(get("/api/v1/academic-years/missing")
                        .with(jwt().jwt(token -> token.subject("student-user"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HTTP_404"));

        mvc.perform(get("/api/v1/classrooms/missing")
                        .with(jwt().jwt(token -> token.subject("student-user"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HTTP_404"));

        mvc.perform(get("/api/v1/faculties/missing")
                        .with(jwt().jwt(token -> token.subject("student-user"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HTTP_404"));

        mvc.perform(get("/api/v1/departments/missing")
                        .with(jwt().jwt(token -> token.subject("student-user"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HTTP_404"));

        mvc.perform(get("/api/v1/curricula/missing")
                        .with(jwt().jwt(token -> token.subject("student-user"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HTTP_404"));

        mvc.perform(get("/api/v1/classrooms")
                        .queryParam("building", "A")
                        .with(jwt().jwt(token -> token.subject("student-user"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mvc.perform(get("/api/v1/faculties")
                        .queryParam("status", "ACTIVE")
                .with(adminJwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mvc.perform(get("/api/v1/curricula")
                        .queryParam("status", "ACTIVE")
                        .with(jwt().jwt(token -> token.subject("student-user"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    private void insertAcademicYear(String id, int year, boolean current) {
        Instant start = Instant.parse(year + "-01-01T00:00:00Z");
        jdbc.update(
                "INSERT INTO \"academic\".\"AcademicYear\""
                        + " (\"id\", \"year\", \"startDate\", \"endDate\", \"isCurrent\", \"createdAt\", \"updatedAt\")"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?)",
                id,
                year,
                localDateTime(start),
                localDateTime(start.plusSeconds(31_536_000)),
                current,
                localDateTime(BASE_TIME),
                localDateTime(BASE_TIME));
    }

    private void insertSemester(
            String id,
            String name,
            String nameEn,
            String nameVi,
            String type,
            String academicYearId,
            String startDate) {
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO "academic"."Semester" (
                        "id", "name", "nameEn", "nameVi", "type", "academicYearId",
                        "startDate", "endDate", "registrationStart", "registrationEnd",
                        "addDropStart", "addDropEnd", "status", "createdAt", "updatedAt"
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """)) {
                statement.setString(1, id);
                statement.setString(2, name);
                statement.setString(3, nameEn);
                statement.setString(4, nameVi);
                statement.setString(5, type);
                statement.setString(6, academicYearId);
                timestamp(statement, 7, Instant.parse(startDate));
                timestamp(statement, 8, Instant.parse(startDate).plusSeconds(7_776_000));
                statement.setNull(9, Types.TIMESTAMP);
                statement.setNull(10, Types.TIMESTAMP);
                statement.setNull(11, Types.TIMESTAMP);
                statement.setNull(12, Types.TIMESTAMP);
                statement.setString(13, "ACTIVE");
                timestamp(statement, 14, BASE_TIME);
                timestamp(statement, 15, BASE_TIME);
                statement.executeUpdate();
            }
            return null;
        });
    }

    private void insertFaculty(
            String id,
            String name,
            String nameEn,
            String nameVi,
            String code,
            String description,
            String descriptionEn,
            String descriptionVi) {
        jdbc.update(
                "INSERT INTO \"academic\".\"Faculty\""
                        + " (\"id\", \"name\", \"nameEn\", \"nameVi\", \"code\", \"description\","
                        + " \"descriptionEn\", \"descriptionVi\", \"dean\", \"phone\", \"email\","
                        + " \"building\", \"createdAt\", \"updatedAt\", \"isActive\")"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id,
                name,
                nameEn,
                nameVi,
                code,
                description,
                descriptionEn,
                descriptionVi,
                null,
                null,
                null,
                null,
                localDateTime(BASE_TIME),
                localDateTime(BASE_TIME),
                true);
    }

    private void insertDepartment(
            String id,
            String name,
            String nameEn,
            String nameVi,
            String code,
            String description,
            String descriptionEn,
            String descriptionVi) {
        insertDepartment(id, name, nameEn, nameVi, code, description, descriptionEn, descriptionVi, "faculty-1", null);
    }

    private void insertDepartment(
            String id,
            String name,
            String nameEn,
            String nameVi,
            String code,
            String description,
            String descriptionEn,
            String descriptionVi,
            String facultyId,
            String chair) {
        jdbc.update(
                "INSERT INTO \"academic\".\"Department\""
                        + " (\"id\", \"name\", \"nameEn\", \"nameVi\", \"code\", \"description\","
                        + " \"descriptionEn\", \"descriptionVi\", \"chair\", \"phone\", \"email\","
                        + " \"building\", \"facultyId\", \"createdAt\", \"updatedAt\", \"isActive\")"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id,
                name,
                nameEn,
                nameVi,
                code,
                description,
                descriptionEn,
                descriptionVi,
                chair,
                null,
                null,
                null,
                facultyId,
                localDateTime(BASE_TIME),
                localDateTime(BASE_TIME),
                true);
    }

    private void insertCourse(
            String id,
            String code,
            String name,
            String nameEn,
            String nameVi,
            String departmentId,
            String semesterId,
            int credits) {
        jdbc.update(
                "INSERT INTO \"academic\".\"Course\""
                        + " (\"id\", \"code\", \"name\", \"nameEn\", \"nameVi\", \"description\","
                        + " \"descriptionEn\", \"descriptionVi\", \"credits\", \"departmentId\","
                        + " \"semesterId\", \"isActive\", \"createdAt\", \"updatedAt\")"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id,
                code,
                name,
                nameEn,
                nameVi,
                "Description " + code,
                null,
                null,
                credits,
                departmentId,
                semesterId,
                true,
                localDateTime(BASE_TIME),
                localDateTime(BASE_TIME));
    }

    private void insertCurriculum(
            String id,
            String name,
            String nameEn,
            String nameVi,
            String code,
            String departmentId,
            String academicYearId,
            String semesterId,
            int totalCredits) {
        jdbc.update(
                "INSERT INTO \"academic\".\"Curriculum\""
                        + " (\"id\", \"name\", \"nameEn\", \"nameVi\", \"code\", \"departmentId\","
                        + " \"academicYearId\", \"semesterId\", \"totalCredits\", \"description\","
                        + " \"descriptionEn\", \"descriptionVi\", \"isActive\", \"createdAt\", \"updatedAt\")"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id,
                name,
                nameEn,
                nameVi,
                code,
                departmentId,
                academicYearId,
                semesterId,
                totalCredits,
                "Description " + code,
                null,
                null,
                true,
                localDateTime(BASE_TIME),
                localDateTime(BASE_TIME));
    }

    private void insertCurriculumCourse(
            String id,
            String curriculumId,
            String courseId,
            int year,
            int semester,
            boolean mandatory) {
        jdbc.update(
                "INSERT INTO \"academic\".\"CurriculumCourse\""
                        + " (\"id\", \"curriculumId\", \"courseId\", \"year\", \"semester\", \"isMandatory\")"
                        + " VALUES (?, ?, ?, ?, ?, ?)",
                id,
                curriculumId,
                courseId,
                year,
                semester,
                mandatory);
    }

    private void insertLecturer(String id, String userId, String departmentId, String employeeId, String title) {
        jdbc.update(
                "INSERT INTO \"academic\".\"Lecturer\""
                        + " (\"id\", \"userId\", \"departmentId\", \"employeeId\", \"title\","
                        + " \"specialization\", \"office\", \"phone\", \"createdAt\", \"updatedAt\", \"isActive\")"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id,
                userId,
                departmentId,
                employeeId,
                title,
                "Software systems",
                "A-101",
                "0123456789",
                localDateTime(BASE_TIME),
                localDateTime(BASE_TIME),
                true);
    }

    private void insertClassroom(String id, String building, String roomNumber, int capacity, String type) {
        jdbc.update(
                "INSERT INTO \"academic\".\"Classroom\""
                        + " (\"id\", \"building\", \"roomNumber\", \"capacity\", \"type\", \"isActive\","
                        + " \"createdAt\", \"updatedAt\")"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                id,
                building,
                roomNumber,
                capacity,
                type,
                true,
                localDateTime(BASE_TIME),
                localDateTime(BASE_TIME));
    }

    private void insertClassroomSection(
            String id,
            String sectionNumber,
            String courseId,
            String semesterId,
            String lecturerId,
            String classroomId,
            int capacity,
            int enrolledCount) {
        jdbc.update(
                "INSERT INTO \"academic\".\"Section\""
                        + " (\"id\", \"sectionNumber\", \"courseId\", \"semesterId\", \"lecturerId\","
                        + " \"classroomId\", \"capacity\", \"enrolledCount\", \"status\")"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id,
                sectionNumber,
                courseId,
                semesterId,
                lecturerId,
                classroomId,
                capacity,
                enrolledCount,
                "OPEN");
    }

    private static RequestPostProcessor adminJwt() {
        return jwt().jwt(token -> token
                .subject("admin-user")
                .claim("roles", List.of("ADMIN")))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private static RequestPostProcessor studentJwt() {
        return jwt().jwt(token -> token
                .subject("student-user")
                .claim("roles", List.of("STUDENT")))
                .authorities(new SimpleGrantedAuthority("ROLE_STUDENT"));
    }

    private static void timestamp(PreparedStatement statement, int index, Instant value)
            throws java.sql.SQLException {
        statement.setObject(index, localDateTime(value), Types.TIMESTAMP);
    }

    private static java.time.LocalDateTime localDateTime(Instant value) {
        return java.time.LocalDateTime.ofInstant(value, java.time.ZoneOffset.UTC);
    }
}
