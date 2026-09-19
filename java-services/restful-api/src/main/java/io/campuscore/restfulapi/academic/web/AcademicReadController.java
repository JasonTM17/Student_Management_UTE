package io.campuscore.restfulapi.academic.web;

import io.campuscore.restfulapi.academic.service.AcademicReadService;
import io.campuscore.restfulapi.academic.web.AcademicReadDtos.AcademicYearListResponse;
import io.campuscore.restfulapi.academic.web.AcademicReadDtos.AcademicYearResponse;
import io.campuscore.restfulapi.academic.web.AcademicReadDtos.ClassroomListResponse;
import io.campuscore.restfulapi.academic.web.AcademicReadDtos.ClassroomResponse;
import io.campuscore.restfulapi.academic.web.AcademicReadDtos.CourseListResponse;
import io.campuscore.restfulapi.academic.web.AcademicReadDtos.CourseResponse;
import io.campuscore.restfulapi.academic.web.AcademicReadDtos.CurriculumListResponse;
import io.campuscore.restfulapi.academic.web.AcademicReadDtos.CurriculumResponse;
import io.campuscore.restfulapi.academic.web.AcademicReadDtos.MyCurriculumResponse;
import io.campuscore.restfulapi.academic.web.AcademicReadDtos.DepartmentListResponse;
import io.campuscore.restfulapi.academic.web.AcademicReadDtos.DepartmentResponse;
import io.campuscore.restfulapi.academic.web.AcademicReadDtos.FacultyListResponse;
import io.campuscore.restfulapi.academic.web.AcademicReadDtos.FacultyResponse;
import io.campuscore.restfulapi.academic.web.AcademicReadDtos.SemesterListResponse;
import io.campuscore.restfulapi.academic.web.AcademicReadDtos.SemesterResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Role-protected academic catalog query routes. */
@RestController
@Profile("persistence")
@RequestMapping("/api/v1")
@Tag(name = "Academic Catalog", description = "Tra cứu danh mục học vụ: Khoa, Bộ môn, Học kỳ, Năm học, Học phần, Khung chương trình đào tạo và Phòng học")
public class AcademicReadController {

    private final AcademicReadService academic;

    public AcademicReadController(AcademicReadService academic) {
        this.academic = academic;
    }

    @GetMapping("semesters")
    @Operation(summary = "Danh sách học kỳ", description = "Truy xuất danh sách các học kỳ trong năm học kèm trạng thái học vụ và đăng ký.")
    @ApiResponse(responseCode = "200", description = "Danh sách học kỳ phân trang")
    public SemesterListResponse getSemesters(
            @Parameter(description = "Số thứ tự trang") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "Số lượng bản ghi mỗi trang") @RequestParam(defaultValue = "20") int limit,
            @RequestParam MultiValueMap<String, String> queryParameters) {
        requireAllowedQuery(queryParameters, Set.of("page", "limit"));
        return academic.findSemesters(page, limit);
    }

    @GetMapping("semesters/{id}")
    @Operation(summary = "Chi tiết học kỳ", description = "Lấy thông tin chi tiết của một học kỳ theo mã định danh ID.")
    @ApiResponse(responseCode = "200", description = "Thông tin chi tiết học kỳ")
    public SemesterResponse getSemester(@Parameter(description = "Mã học kỳ (Semester ID)") @PathVariable String id) {
        return academic.findSemester(id);
    }

    @GetMapping("faculties")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Danh sách Khoa đào tạo", description = "Tra cứu danh mục các Khoa / Viện đào tạo trực thuộc Trường ĐH Sư phạm Kỹ thuật TP.HCM.")
    @ApiResponse(responseCode = "200", description = "Danh sách Khoa đào tạo")
    public FacultyListResponse getFaculties(
            @Parameter(description = "Số thứ tự trang") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "Số lượng bản ghi mỗi trang") @RequestParam(defaultValue = "20") int limit,
            @RequestParam MultiValueMap<String, String> queryParameters) {
        requireAllowedQuery(queryParameters, Set.of("page", "limit"));
        return academic.findFaculties(page, limit);
    }

    @GetMapping("faculties/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Chi tiết Khoa đào tạo", description = "Lấy thông tin chi tiết của Khoa đào tạo theo ID.")
    @ApiResponse(responseCode = "200", description = "Thông tin chi tiết Khoa đào tạo")
    public FacultyResponse getFaculty(@Parameter(description = "Mã Khoa (Faculty ID)") @PathVariable String id) {
        return academic.findFaculty(id);
    }

    @GetMapping("departments")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Danh sách Bộ môn chuyên ngành", description = "Tra cứu danh mục các Bộ môn trực thuộc các Khoa đào tạo.")
    @ApiResponse(responseCode = "200", description = "Danh sách Bộ môn chuyên ngành")
    public DepartmentListResponse getDepartments(
            @Parameter(description = "Số thứ tự trang") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "Số lượng bản ghi mỗi trang") @RequestParam(defaultValue = "20") int limit,
            @RequestParam MultiValueMap<String, String> queryParameters) {
        requireAllowedQuery(queryParameters, Set.of("page", "limit"));
        return academic.findDepartments(page, limit);
    }

    @GetMapping("departments/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Chi tiết Bộ môn chuyên ngành", description = "Lấy thông tin chi tiết của Bộ môn kèm danh sách giảng viên trực thuộc.")
    @ApiResponse(responseCode = "200", description = "Thông tin chi tiết Bộ môn")
    public DepartmentResponse getDepartment(@Parameter(description = "Mã Bộ môn (Department ID)") @PathVariable String id) {
        return academic.findDepartment(id);
    }

    @GetMapping("academic-years")
    @Operation(summary = "Danh sách năm học", description = "Tra cứu danh mục các niên khóa / năm học của nhà trường.")
    @ApiResponse(responseCode = "200", description = "Danh sách năm học")
    public AcademicYearListResponse getAcademicYears(
            @Parameter(description = "Số thứ tự trang") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "Số lượng bản ghi mỗi trang") @RequestParam(defaultValue = "20") int limit,
            @RequestParam MultiValueMap<String, String> queryParameters) {
        requireAllowedQuery(queryParameters, Set.of("page", "limit"));
        return academic.findAcademicYears(page, limit);
    }

    @GetMapping("academic-years/{id}")
    @Operation(summary = "Chi tiết năm học", description = "Lấy thông tin chi tiết một năm học kèm các học kỳ trực thuộc.")
    @ApiResponse(responseCode = "200", description = "Thông tin chi tiết năm học")
    public AcademicYearResponse getAcademicYear(@Parameter(description = "Mã năm học (Academic Year ID)") @PathVariable String id) {
        return academic.findAcademicYear(id);
    }

    @GetMapping("courses")
    @Operation(summary = "Danh mục học phần / môn học", description = "Tra cứu danh sách các học phần trong chương trình đào tạo kèm số tín chỉ và bộ môn phụ trách.")
    @ApiResponse(responseCode = "200", description = "Danh mục học phần phân trang")
    public CourseListResponse getCourses(
            @Parameter(description = "Số thứ tự trang") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "Số lượng bản ghi mỗi trang") @RequestParam(defaultValue = "20") int limit,
            @RequestParam MultiValueMap<String, String> queryParameters) {
        requireAllowedQuery(queryParameters, Set.of("page", "limit"));
        return academic.findCourses(page, limit);
    }

    @GetMapping("courses/{id}")
    @Operation(summary = "Chi tiết học phần", description = "Lấy thông tin mô tả chi tiết của học phần theo ID.")
    @ApiResponse(responseCode = "200", description = "Thông tin chi tiết học phần")
    public CourseResponse getCourse(@Parameter(description = "Mã học phần (Course ID)") @PathVariable String id) {
        return academic.findCourse(id);
    }

    @GetMapping("curricula")
    @Operation(summary = "Danh sách khung chương trình đào tạo (CTĐT)", description = "Tra cứu các khung CTĐT chuẩn, CLC kèm tổng số tín chỉ yêu cầu tốt nghiệp.")
    @ApiResponse(responseCode = "200", description = "Danh sách khung CTĐT")
    public CurriculumListResponse getCurricula(
            @Parameter(description = "Số thứ tự trang") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "Số lượng bản ghi mỗi trang") @RequestParam(defaultValue = "20") int limit,
            @RequestParam MultiValueMap<String, String> queryParameters) {
        requireAllowedQuery(queryParameters, Set.of("page", "limit"));
        return academic.findCurricula(page, limit);
    }

    @GetMapping("curricula/{id}")
    @Operation(summary = "Chi tiết khung chương trình đào tạo", description = "Lấy cấu trúc chi tiết các học phần theo từng năm học và học kỳ trong CTĐT.")
    @ApiResponse(responseCode = "200", description = "Chi tiết khung CTĐT")
    public CurriculumResponse getCurriculum(@Parameter(description = "Mã CTĐT (Curriculum ID)") @PathVariable String id) {
        return academic.findCurriculum(id);
    }

    @GetMapping("me/curriculum")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Khung CTĐT của sinh viên hiện tại", description = "Tra cứu chương trình đào tạo chính thức áp dụng cho khóa và chuyên ngành của sinh viên đang đăng nhập.")
    @ApiResponse(responseCode = "200", description = "Khung CTĐT của sinh viên")
    public MyCurriculumResponse getMyCurriculum(@AuthenticationPrincipal Jwt jwt) {
        return academic.findMyCurriculum(jwt.getClaimAsString("studentId"));
    }

    @GetMapping("classrooms")
    @Operation(summary = "Danh sách phòng học và giảng đường", description = "Tra cứu hệ thống phòng lý thuyết, thực hành, xưởng tại các cơ sở đào tạo.")
    @ApiResponse(responseCode = "200", description = "Danh sách phòng học")
    public ClassroomListResponse getClassrooms(
            @Parameter(description = "Số thứ tự trang") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "Số lượng bản ghi mỗi trang") @RequestParam(defaultValue = "20") int limit,
            @RequestParam MultiValueMap<String, String> queryParameters) {
        requireAllowedQuery(queryParameters, Set.of("page", "limit"));
        return academic.findClassrooms(page, limit);
    }

    @GetMapping("classrooms/{id}")
    @Operation(summary = "Chi tiết phòng học", description = "Lấy thông tin sức chứa, tòa nhà và loại phòng học (LECTURE, LAB).")
    @ApiResponse(responseCode = "200", description = "Thông tin chi tiết phòng học")
    public ClassroomResponse getClassroom(@Parameter(description = "Mã phòng học (Classroom ID)") @PathVariable String id) {
        return academic.findClassroom(id);
    }

    private static void requireAllowedQuery(
            MultiValueMap<String, String> queryParameters,
            Set<String> allowed) {
        for (Map.Entry<String, List<String>> entry : queryParameters.entrySet()) {
            if ("_cc_nocache".equals(entry.getKey())) {
                continue;
            }
            if (!allowed.contains(entry.getKey()) || entry.getValue().size() != 1) {
                throw new IllegalArgumentException("Unexpected or repeated query parameter: " + entry.getKey());
            }
        }
    }
}
