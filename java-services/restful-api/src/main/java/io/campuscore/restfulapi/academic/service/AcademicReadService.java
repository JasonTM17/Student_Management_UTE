package io.campuscore.restfulapi.academic.service;

import io.campuscore.restfulapi.academic.repository.AcademicReadRepository;
import io.campuscore.restfulapi.academic.web.AcademicReadDtos.AcademicYearListResponse;
import io.campuscore.restfulapi.academic.web.AcademicReadDtos.AcademicYearResponse;
import io.campuscore.restfulapi.academic.web.AcademicReadDtos.ClassroomListResponse;
import io.campuscore.restfulapi.academic.web.AcademicReadDtos.ClassroomResponse;
import io.campuscore.restfulapi.academic.web.AcademicReadDtos.ClassroomSectionSummary;
import io.campuscore.restfulapi.academic.web.AcademicReadDtos.CourseListResponse;
import io.campuscore.restfulapi.academic.web.AcademicReadDtos.CourseResponse;
import io.campuscore.restfulapi.academic.web.AcademicReadDtos.CurriculumCourseSummary;
import io.campuscore.restfulapi.academic.web.AcademicReadDtos.CurriculumListResponse;
import io.campuscore.restfulapi.academic.web.AcademicReadDtos.CurriculumResponse;
import io.campuscore.restfulapi.academic.web.AcademicReadDtos.DepartmentLecturerSummary;
import io.campuscore.restfulapi.academic.web.AcademicReadDtos.DepartmentListResponse;
import io.campuscore.restfulapi.academic.web.AcademicReadDtos.DepartmentResponse;
import io.campuscore.restfulapi.academic.web.AcademicReadDtos.FacultyDepartmentSummary;
import io.campuscore.restfulapi.academic.web.AcademicReadDtos.FacultyListResponse;
import io.campuscore.restfulapi.academic.web.AcademicReadDtos.FacultyResponse;
import io.campuscore.restfulapi.academic.web.AcademicReadDtos.PageMeta;
import io.campuscore.restfulapi.academic.web.AcademicReadDtos.SemesterCatalogSummary;
import io.campuscore.restfulapi.academic.web.AcademicReadDtos.SemesterListResponse;
import io.campuscore.restfulapi.academic.web.AcademicReadDtos.SemesterResponse;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Read-only application service for the academic strangler candidate. */
@Service
@Profile("persistence")
@ConditionalOnProperty(prefix = "migration.academic-read", name = "enabled", havingValue = "true")
public class AcademicReadService {

    public static final int MAX_PAGE_SIZE = 200;

    private final AcademicReadRepository academic;

    public AcademicReadService(AcademicReadRepository academic) {
        this.academic = academic;
    }

    @Transactional(readOnly = true)
    public SemesterListResponse findSemesters(int page, int limit) {
        requirePage(page, limit);
        long total = academic.countSemesters();
        List<SemesterResponse> data = academic.findSemesters(offset(page, limit), limit).stream()
                .map(AcademicCatalogLocalizer::hydrateSemester)
                .toList();
        return new SemesterListResponse(data, meta(total, page, limit));
    }

    @Transactional(readOnly = true)
    public SemesterResponse findSemester(String id) {
        return academic.findSemesterById(id)
                .map(AcademicCatalogLocalizer::hydrateSemester)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Semester not found"));
    }

    @Transactional(readOnly = true)
    public FacultyListResponse findFaculties(int page, int limit) {
        requirePage(page, limit);
        long total = academic.countFaculties();
        List<FacultyResponse> faculties = academic.findFaculties(offset(page, limit), limit);
        return new FacultyListResponse(hydrateFaculties(faculties), meta(total, page, limit));
    }

    @Transactional(readOnly = true)
    public FacultyResponse findFaculty(String id) {
        FacultyResponse faculty = academic.findFacultyById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Faculty not found"));
        return hydrateFaculties(List.of(faculty)).getFirst();
    }

    @Transactional(readOnly = true)
    public DepartmentListResponse findDepartments(int page, int limit) {
        requirePage(page, limit);
        long total = academic.countDepartments();
        List<DepartmentResponse> departments = academic.findDepartments(offset(page, limit), limit);
        return new DepartmentListResponse(hydrateDepartments(departments), meta(total, page, limit));
    }

    @Transactional(readOnly = true)
    public DepartmentResponse findDepartment(String id) {
        DepartmentResponse department = academic.findDepartmentById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Department not found"));
        return hydrateDepartments(List.of(department)).getFirst();
    }

    @Transactional(readOnly = true)
    public AcademicYearListResponse findAcademicYears(int page, int limit) {
        requirePage(page, limit);
        long total = academic.countAcademicYears();
        List<AcademicYearResponse> years = academic.findAcademicYears(offset(page, limit), limit);
        return new AcademicYearListResponse(hydrateAcademicYears(years), meta(total, page, limit));
    }

    @Transactional(readOnly = true)
    public AcademicYearResponse findAcademicYear(String id) {
        AcademicYearResponse year = academic.findAcademicYearById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Academic year not found"));
        return hydrateAcademicYears(List.of(year)).getFirst();
    }

    @Transactional(readOnly = true)
    public CourseListResponse findCourses(int page, int limit) {
        requirePage(page, limit);
        long total = academic.countCourses();
        List<CourseResponse> data = academic.findCourses(offset(page, limit), limit).stream()
                .map(AcademicCatalogLocalizer::hydrateCourse)
                .toList();
        return new CourseListResponse(data, meta(total, page, limit));
    }

    @Transactional(readOnly = true)
    public CourseResponse findCourse(String id) {
        return academic.findCourseById(id)
                .map(AcademicCatalogLocalizer::hydrateCourse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found"));
    }

    @Transactional(readOnly = true)
    public CurriculumListResponse findCurricula(int page, int limit) {
        requirePage(page, limit);
        long total = academic.countCurricula();
        List<CurriculumResponse> curricula = academic.findCurricula(offset(page, limit), limit);
        return new CurriculumListResponse(hydrateCurricula(curricula, false), meta(total, page, limit));
    }

    @Transactional(readOnly = true)
    public CurriculumResponse findCurriculum(String id) {
        CurriculumResponse curriculum = academic.findCurriculumById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Curriculum not found"));
        return hydrateCurricula(List.of(curriculum), true).getFirst();
    }

    @Transactional(readOnly = true)
    public ClassroomListResponse findClassrooms(int page, int limit) {
        requirePage(page, limit);
        long total = academic.countClassrooms();
        List<ClassroomResponse> data = academic.findClassrooms(offset(page, limit), limit);
        return new ClassroomListResponse(data, meta(total, page, limit));
    }

    @Transactional(readOnly = true)
    public ClassroomResponse findClassroom(String id) {
        ClassroomResponse classroom = academic.findClassroomById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Classroom not found"));
        return hydrateClassrooms(List.of(classroom)).getFirst();
    }

    private List<AcademicYearResponse> hydrateAcademicYears(List<AcademicYearResponse> years) {
        List<String> ids = years.stream().map(AcademicYearResponse::id).toList();
        Map<String, List<SemesterCatalogSummary>> semestersByYear = academic
                .findSemesterCatalogSummariesByAcademicYearIds(ids)
                .stream()
                .collect(Collectors.groupingBy(SemesterCatalogSummary::academicYearId));
        return years.stream()
                .map(year -> new AcademicYearResponse(
                        year.id(),
                        year.year(),
                        year.startDate(),
                        year.endDate(),
                        year.isCurrent(),
                        year.createdAt(),
                        year.updatedAt(),
                        semestersByYear.getOrDefault(year.id(), List.of())))
                .toList();
    }

    private List<FacultyResponse> hydrateFaculties(List<FacultyResponse> faculties) {
        List<String> ids = faculties.stream().map(FacultyResponse::id).toList();
        Map<String, List<FacultyDepartmentSummary>> departmentsByFaculty = academic
                .findFacultyDepartmentsByFacultyIds(ids)
                .stream()
                .collect(Collectors.groupingBy(FacultyDepartmentSummary::facultyId));
        return faculties.stream()
                .map(faculty -> new FacultyResponse(
                        faculty.id(),
                        faculty.name(),
                        faculty.nameEn(),
                        faculty.nameVi(),
                        faculty.code(),
                        faculty.description(),
                        faculty.descriptionEn(),
                        faculty.descriptionVi(),
                        faculty.dean(),
                        faculty.phone(),
                        faculty.email(),
                        faculty.building(),
                        faculty.createdAt(),
                        faculty.updatedAt(),
                        faculty.isActive(),
                        departmentsByFaculty.getOrDefault(faculty.id(), List.of())))
                .map(AcademicCatalogLocalizer::hydrateFaculty)
                .toList();
    }

    private List<DepartmentResponse> hydrateDepartments(List<DepartmentResponse> departments) {
        List<String> ids = departments.stream().map(DepartmentResponse::id).toList();
        Map<String, List<DepartmentLecturerSummary>> lecturersByDepartment = academic
                .findDepartmentLecturersByDepartmentIds(ids)
                .stream()
                .collect(Collectors.groupingBy(DepartmentLecturerSummary::departmentId));
        return departments.stream()
                .map(department -> new DepartmentResponse(
                        department.id(),
                        department.name(),
                        department.nameEn(),
                        department.nameVi(),
                        department.code(),
                        department.description(),
                        department.descriptionEn(),
                        department.descriptionVi(),
                        department.chair(),
                        department.phone(),
                        department.email(),
                        department.building(),
                        department.facultyId(),
                        department.createdAt(),
                        department.updatedAt(),
                        department.isActive(),
                        department.faculty(),
                        lecturersByDepartment.getOrDefault(department.id(), List.of())))
                .map(AcademicCatalogLocalizer::hydrateDepartmentResponse)
                .toList();
    }

    private List<CurriculumResponse> hydrateCurricula(List<CurriculumResponse> curricula, boolean includeCourses) {
        Map<String, List<CurriculumCourseSummary>> coursesByCurriculum;
        if (includeCourses) {
            List<String> ids = curricula.stream().map(CurriculumResponse::id).toList();
            coursesByCurriculum = academic
                    .findCurriculumCoursesByCurriculumIds(ids)
                    .stream()
                    .collect(Collectors.groupingBy(CurriculumCourseSummary::curriculumId));
        } else {
            coursesByCurriculum = Map.of();
        }
        return curricula.stream()
                .map(curriculum -> new CurriculumResponse(
                        curriculum.id(),
                        curriculum.name(),
                        curriculum.nameEn(),
                        curriculum.nameVi(),
                        curriculum.code(),
                        curriculum.departmentId(),
                        curriculum.academicYearId(),
                        curriculum.semesterId(),
                        curriculum.totalCredits(),
                        curriculum.description(),
                        curriculum.descriptionEn(),
                        curriculum.descriptionVi(),
                        curriculum.isActive(),
                        curriculum.createdAt(),
                        curriculum.updatedAt(),
                        curriculum.department(),
                        includeCourses ? coursesByCurriculum.getOrDefault(curriculum.id(), List.of()) : null))
                .map(AcademicCatalogLocalizer::hydrateCurriculum)
                .toList();
    }

    private List<ClassroomResponse> hydrateClassrooms(List<ClassroomResponse> classrooms) {
        List<String> ids = classrooms.stream().map(ClassroomResponse::id).toList();
        Map<String, List<ClassroomSectionSummary>> sectionsByClassroom = academic
                .findClassroomSectionsByClassroomIds(ids)
                .stream()
                .collect(Collectors.groupingBy(ClassroomSectionSummary::classroomId));
        return classrooms.stream()
                .map(classroom -> new ClassroomResponse(
                        classroom.id(),
                        classroom.building(),
                        classroom.roomNumber(),
                        classroom.capacity(),
                        classroom.type(),
                        classroom.isActive(),
                        classroom.createdAt(),
                        classroom.updatedAt(),
                        sectionsByClassroom.getOrDefault(classroom.id(), List.of())))
                .toList();
    }

    private static PageMeta meta(long total, int page, int limit) {
        long totalPages = total == 0 ? 0 : ((total - 1) / limit) + 1;
        if (totalPages > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Academic catalog result is too large");
        }
        return new PageMeta(total, page, limit, (int) totalPages);
    }

    private static long offset(int page, int limit) {
        return (long) (page - 1) * limit;
    }

    private static void requirePage(int page, int limit) {
        if (page < 1) {
            throw new IllegalArgumentException("page must be at least 1");
        }
        if (limit < 1 || limit > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_PAGE_SIZE);
        }
    }
}
