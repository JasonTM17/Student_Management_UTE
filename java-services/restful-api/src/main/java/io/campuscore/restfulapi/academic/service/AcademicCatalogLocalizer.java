package io.campuscore.restfulapi.academic.service;

import io.campuscore.restfulapi.academic.web.AcademicReadDtos.CourseResponse;
import io.campuscore.restfulapi.academic.web.AcademicReadDtos.CurriculumResponse;
import io.campuscore.restfulapi.academic.web.AcademicReadDtos.DepartmentResponse;
import io.campuscore.restfulapi.academic.web.AcademicReadDtos.DepartmentSummary;
import io.campuscore.restfulapi.academic.web.AcademicReadDtos.FacultyDepartmentSummary;
import io.campuscore.restfulapi.academic.web.AcademicReadDtos.FacultyResponse;
import io.campuscore.restfulapi.academic.web.AcademicReadDtos.FacultySummary;
import io.campuscore.restfulapi.academic.web.AcademicReadDtos.SemesterResponse;
import java.util.Map;

final class AcademicCatalogLocalizer {

    private static final Map<String, LocalizedDefaults> COURSE_DEFAULTS = Map.of(
            "CS101", new LocalizedDefaults("Introduction to Programming", "Nhập môn lập trình", null, null),
            "CS201", new LocalizedDefaults("Data Structures", "Cấu trúc dữ liệu", null, null),
            "CS301", new LocalizedDefaults("Algorithms", "Giải thuật", null, null),
            "SE401", new LocalizedDefaults("Web Development", "Phát triển web", null, null),
            "COMP101", new LocalizedDefaults("Introduction to Computer Science", "Nhập môn khoa học máy tính", null, null),
            "COMP202", new LocalizedDefaults("Data Structures", "Cấu trúc dữ liệu", null, null));

    private static final Map<String, LocalizedDefaults> DEPARTMENT_DEFAULTS = Map.of(
            "CS", new LocalizedDefaults(
                    "Computer Science",
                    "Khoa học máy tính",
                    "Department of Computer Science",
                    "Bộ môn Khoa học máy tính"),
            "CSE", new LocalizedDefaults(
                    "Computer Science",
                    "Khoa học máy tính",
                    "Department of Computer Science",
                    "Bộ môn Khoa học máy tính"),
            "SE", new LocalizedDefaults(
                    "Software Engineering",
                    "Kỹ thuật phần mềm",
                    "Department of Software Engineering",
                    "Bộ môn Kỹ thuật phần mềm"),
            "CE", new LocalizedDefaults(
                    "Computer Engineering",
                    "Kỹ thuật máy tính",
                    "Department of Computer Engineering",
                    "Bộ môn Kỹ thuật máy tính"),
            "BA", new LocalizedDefaults(
                    "Business Administration",
                    "Quản trị kinh doanh",
                    "Department of Business Administration",
                    "Bộ môn Quản trị kinh doanh"));

    private static final Map<String, LocalizedDefaults> FACULTY_DEFAULTS = Map.of(
            "FCS", new LocalizedDefaults(
                    "Faculty of Computer Science",
                    "Khoa Khoa học máy tính",
                    "Faculty of Computer Science and Information Technology",
                    "Khoa Khoa học máy tính và công nghệ thông tin"),
            "ENG", new LocalizedDefaults(
                    "Faculty of Engineering",
                    "Khoa Kỹ thuật",
                    "Faculty of Engineering",
                    "Khoa Kỹ thuật"),
            "FE", new LocalizedDefaults(
                    "Faculty of Engineering",
                    "Khoa Kỹ thuật",
                    "Faculty of Engineering",
                    "Khoa Kỹ thuật"),
            "FBA", new LocalizedDefaults(
                    "Faculty of Business Administration",
                    "Khoa Quản trị kinh doanh",
                    "Faculty of Business Administration",
                    "Khoa Quản trị kinh doanh"));

    private static final Map<String, LocalizedDefaults> CURRICULUM_DEFAULTS = Map.of(
            "CS2025", new LocalizedDefaults(
                    "Computer Science 2025",
                    "Chương trình Khoa học máy tính 2025",
                    "Computer Science curriculum for the 2025 intake",
                    "Chương trình Khoa học máy tính cho khóa tuyển sinh 2025"),
            "CS2026", new LocalizedDefaults(
                    "Computer Science 2026",
                    "Chương trình Khoa học máy tính 2026",
                    "Computer Science curriculum for the 2026 intake",
                    "Chương trình Khoa học máy tính cho khóa tuyển sinh 2026"),
            "SE2025", new LocalizedDefaults(
                    "Software Engineering 2025",
                    "Chương trình Kỹ thuật phần mềm 2025",
                    "Software Engineering curriculum for the 2025 intake",
                    "Chương trình Kỹ thuật phần mềm cho khóa tuyển sinh 2025"));

    private AcademicCatalogLocalizer() {
    }

    static SemesterResponse hydrateSemester(SemesterResponse semester) {
        String[] semesterDefaults = semesterDefaults(semester.type(), semester.academicYear().year());
        return new SemesterResponse(
                semester.id(),
                semester.name(),
                pick(semester.nameEn(), semester.name(), semesterDefaults[0]),
                pick(semester.nameVi(), semesterDefaults[1]),
                semester.type(),
                semester.academicYearId(),
                semester.startDate(),
                semester.endDate(),
                semester.registrationStart(),
                semester.registrationEnd(),
                semester.addDropStart(),
                semester.addDropEnd(),
                semester.status(),
                semester.createdAt(),
                semester.updatedAt(),
                semester.academicYear());
    }

    static CourseResponse hydrateCourse(CourseResponse course) {
        LocalizedDefaults defaults = COURSE_DEFAULTS.getOrDefault(
                upper(course.code()),
                LocalizedDefaults.EMPTY);
        DepartmentSummary department = hydrateDepartment(course.department());
        return new CourseResponse(
                course.id(),
                course.code(),
                course.name(),
                pick(course.nameEn(), course.name(), defaults.nameEn()),
                pick(course.nameVi(), defaults.nameVi()),
                course.description(),
                pick(course.descriptionEn(), course.description(), defaults.descriptionEn()),
                pick(course.descriptionVi(), defaults.descriptionVi()),
                course.credits(),
                course.departmentId(),
                course.semesterId(),
                course.isActive(),
                course.createdAt(),
                course.updatedAt(),
                department);
    }

    static CurriculumResponse hydrateCurriculum(CurriculumResponse curriculum) {
        LocalizedDefaults defaults = CURRICULUM_DEFAULTS.getOrDefault(
                upper(curriculum.code()),
                LocalizedDefaults.EMPTY);
        FacultyDepartmentSummary department = hydrateFacultyDepartment(curriculum.department());
        return new CurriculumResponse(
                curriculum.id(),
                curriculum.name(),
                pick(curriculum.nameEn(), curriculum.name(), defaults.nameEn()),
                pick(curriculum.nameVi(), defaults.nameVi()),
                curriculum.code(),
                curriculum.departmentId(),
                curriculum.academicYearId(),
                curriculum.semesterId(),
                curriculum.totalCredits(),
                curriculum.description(),
                pick(curriculum.descriptionEn(), curriculum.description(), defaults.descriptionEn()),
                pick(curriculum.descriptionVi(), defaults.descriptionVi()),
                curriculum.isActive(),
                curriculum.createdAt(),
                curriculum.updatedAt(),
                department,
                curriculum.courses());
    }

    static FacultyResponse hydrateFaculty(FacultyResponse faculty) {
        LocalizedDefaults defaults = FACULTY_DEFAULTS.getOrDefault(
                upper(faculty.code()),
                LocalizedDefaults.EMPTY);
        return new FacultyResponse(
                faculty.id(),
                faculty.name(),
                pick(faculty.nameEn(), faculty.name(), defaults.nameEn()),
                pick(faculty.nameVi(), defaults.nameVi()),
                faculty.code(),
                faculty.description(),
                pick(faculty.descriptionEn(), faculty.description(), defaults.descriptionEn()),
                pick(faculty.descriptionVi(), defaults.descriptionVi()),
                faculty.dean(),
                faculty.phone(),
                faculty.email(),
                faculty.building(),
                faculty.createdAt(),
                faculty.updatedAt(),
                faculty.isActive(),
                faculty.departments().stream()
                        .map(AcademicCatalogLocalizer::hydrateFacultyDepartment)
                        .toList());
    }

    static DepartmentResponse hydrateDepartmentResponse(DepartmentResponse department) {
        LocalizedDefaults defaults = DEPARTMENT_DEFAULTS.getOrDefault(
                upper(department.code()),
                LocalizedDefaults.EMPTY);
        return new DepartmentResponse(
                department.id(),
                department.name(),
                pick(department.nameEn(), department.name(), defaults.nameEn()),
                pick(department.nameVi(), defaults.nameVi()),
                department.code(),
                department.description(),
                pick(department.descriptionEn(), department.description(), defaults.descriptionEn()),
                pick(department.descriptionVi(), defaults.descriptionVi()),
                department.chair(),
                department.phone(),
                department.email(),
                department.building(),
                department.facultyId(),
                department.createdAt(),
                department.updatedAt(),
                department.isActive(),
                hydrateFacultySummary(department.faculty()),
                department.lecturers());
    }

    private static DepartmentSummary hydrateDepartment(DepartmentSummary department) {
        LocalizedDefaults defaults = DEPARTMENT_DEFAULTS.getOrDefault(
                upper(department.code()),
                LocalizedDefaults.EMPTY);
        return new DepartmentSummary(
                department.id(),
                department.name(),
                pick(department.nameEn(), department.name(), defaults.nameEn()),
                pick(department.nameVi(), defaults.nameVi()),
                department.code(),
                department.description(),
                pick(department.descriptionEn(), department.description(), defaults.descriptionEn()),
                pick(department.descriptionVi(), defaults.descriptionVi()),
                department.facultyId(),
                department.isActive());
    }

    private static FacultySummary hydrateFacultySummary(FacultySummary faculty) {
        LocalizedDefaults defaults = FACULTY_DEFAULTS.getOrDefault(
                upper(faculty.code()),
                LocalizedDefaults.EMPTY);
        return new FacultySummary(
                faculty.id(),
                faculty.name(),
                pick(faculty.nameEn(), faculty.name(), defaults.nameEn()),
                pick(faculty.nameVi(), defaults.nameVi()),
                faculty.code(),
                faculty.description(),
                pick(faculty.descriptionEn(), faculty.description(), defaults.descriptionEn()),
                pick(faculty.descriptionVi(), defaults.descriptionVi()),
                faculty.dean(),
                faculty.phone(),
                faculty.email(),
                faculty.building(),
                faculty.createdAt(),
                faculty.updatedAt(),
                faculty.isActive());
    }

    private static FacultyDepartmentSummary hydrateFacultyDepartment(FacultyDepartmentSummary department) {
        LocalizedDefaults defaults = DEPARTMENT_DEFAULTS.getOrDefault(
                upper(department.code()),
                LocalizedDefaults.EMPTY);
        return new FacultyDepartmentSummary(
                department.id(),
                department.name(),
                pick(department.nameEn(), department.name(), defaults.nameEn()),
                pick(department.nameVi(), defaults.nameVi()),
                department.code(),
                department.description(),
                pick(department.descriptionEn(), department.description(), defaults.descriptionEn()),
                pick(department.descriptionVi(), defaults.descriptionVi()),
                department.chair(),
                department.phone(),
                department.email(),
                department.building(),
                department.facultyId(),
                department.createdAt(),
                department.updatedAt(),
                department.isActive());
    }

    private static String[] semesterDefaults(String type, int year) {
        return switch (upper(type)) {
            case "SPRING" -> new String[]{"Spring " + year, "Học kỳ Xuân " + year};
            case "SUMMER" -> new String[]{"Summer " + year, "Học kỳ Hè " + year};
            case "FALL" -> new String[]{"Fall " + year, "Học kỳ Thu " + year};
            default -> new String[]{null, null};
        };
    }

    private static String pick(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private static String upper(String value) {
        return value == null ? "" : value.toUpperCase(java.util.Locale.ROOT);
    }

    private record LocalizedDefaults(
            String nameEn,
            String nameVi,
            String descriptionEn,
            String descriptionVi) {
        private static final LocalizedDefaults EMPTY = new LocalizedDefaults(null, null, null, null);
    }
}
