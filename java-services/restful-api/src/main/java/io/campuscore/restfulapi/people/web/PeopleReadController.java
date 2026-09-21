package io.campuscore.restfulapi.people.web;

import io.campuscore.restfulapi.people.service.PeopleReadService;
import io.campuscore.restfulapi.people.web.PeopleReadDtos.LecturerListResponse;
import io.campuscore.restfulapi.people.web.PeopleReadDtos.LecturerResponse;
import io.campuscore.restfulapi.people.web.PeopleReadDtos.StudentListResponse;
import io.campuscore.restfulapi.people.web.PeopleReadDtos.StudentResponse;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Student and lecturer directory routes owned by the Java API. */
@Tag(name = "People & Profiles", description = "Danh bạ hồ sơ sinh viên, cán bộ giảng viên trường HCM-UTE")
@RestController
@Profile("persistence")
@RequestMapping("/api/v1")
public class PeopleReadController {

    private final PeopleReadService people;

    public PeopleReadController(PeopleReadService people) {
        this.people = people;
    }

    @Operation(summary = "Danh bạ sinh viên (Giảng viên / Admin)", description = "Truy vấn danh sách sinh viên theo trang và trạng thái học vụ")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Truy vấn thành công danh sách sinh viên")
    })
    @GetMapping("students")
    @PreAuthorize("hasAnyRole('LECTURER', 'ADMIN', 'SUPER_ADMIN')")
    public StudentListResponse getStudents(
            @Parameter(description = "Số trang") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "Số lượng bản ghi mỗi trang") @RequestParam(defaultValue = "20") int limit,
            @Parameter(description = "Trạng thái học vụ (ENROLLED, LEAVE, GRADUATED)") @RequestParam(required = false) String status,
            @RequestParam MultiValueMap<String, String> queryParameters) {
        requireAllowedQuery(queryParameters, Set.of("page", "limit", "status"));
        return people.findStudents(page, limit, status);
    }

    @Operation(summary = "Xem thông tin chi tiết hồ sơ sinh viên", description = "Lấy hồ sơ cá nhân của sinh viên theo ID (có bảo vệ quyền riêng tư)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Tìm thấy hồ sơ sinh viên")
    })
    @GetMapping("students/{id}")
    @PreAuthorize("hasAnyRole('STUDENT', 'LECTURER', 'ADMIN', 'SUPER_ADMIN')")
    public StudentResponse getStudent(
            @Parameter(description = "Mã định danh sinh viên (UUID)", required = true) @PathVariable String id,
            Authentication authentication) {
        return people.findStudent(id, authentication);
    }

    @Operation(summary = "Danh bạ giảng viên", description = "Truy vấn danh sách cán bộ giảng viên theo khoa và bộ môn, hỗ trợ tìm kiếm theo mã giảng viên, họ tên hoặc email")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Truy vấn thành công danh sách giảng viên")
    })
    @GetMapping("lecturers")
    @PreAuthorize("hasAnyRole('LECTURER', 'ADMIN', 'SUPER_ADMIN')")
    public LecturerListResponse getLecturers(
            @Parameter(description = "Số trang") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "Số lượng bản ghi mỗi trang") @RequestParam(defaultValue = "20") int limit,
            @Parameter(description = "Từ khóa tìm kiếm theo mã giảng viên, họ tên hoặc email")
            @RequestParam(required = false) String search,
            @RequestParam MultiValueMap<String, String> queryParameters) {
        requireAllowedQuery(queryParameters, Set.of("page", "limit", "search"));
        return people.findLecturers(page, limit, search);
    }

    @Operation(summary = "Xem thông tin chi tiết cán bộ giảng viên", description = "Lấy thông tin học hàm, học vị, bộ môn và liên hệ của giảng viên")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Tìm thấy thông tin giảng viên")
    })
    @GetMapping("lecturers/{id}")
    @PreAuthorize("hasAnyRole('LECTURER', 'ADMIN', 'SUPER_ADMIN')")
    public LecturerResponse getLecturer(
            @Parameter(description = "Mã định danh giảng viên (UUID)", required = true) @PathVariable String id,
            Authentication authentication) {
        return people.findLecturer(id, authentication);
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
