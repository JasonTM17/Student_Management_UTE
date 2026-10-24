package io.campuscore.restfulapi.academic.controller;

import io.campuscore.restfulapi.academic.service.LecturerMutationService;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Lecturer Administration (Admin)", description = "Quản trị danh sách và hồ sơ giảng viên dành cho Phòng Đào tạo / Admin")
@RestController
@Profile("persistence")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
@RequestMapping("/api/v1/lecturers")
public class LecturerMutationController {

    private final LecturerMutationService lecturers;

    public LecturerMutationController(LecturerMutationService lecturers) {
        this.lecturers = lecturers;
    }

    @Operation(summary = "Tạo mới hồ sơ giảng viên", description = "Thêm mới giảng viên vào hệ thống đào tạo HCM-UTE")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Tạo hồ sơ thành công")
    })
    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> input, Authentication authentication) {
        return lecturers.create(input, isSuperAdmin(authentication));
    }

    @Operation(summary = "Cập nhật hồ sơ giảng viên", description = "Chỉnh sửa thông tin học hàm, học vị, bộ môn của giảng viên")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Cập nhật thành công")
    })
    @PutMapping("/{id}")
    public Map<String, Object> update(
            @Parameter(description = "Mã định danh giảng viên (UUID)", required = true) @PathVariable String id,
            @RequestBody Map<String, Object> input,
            Authentication authentication) {
        return lecturers.update(id, input, isSuperAdmin(authentication));
    }

    @Operation(summary = "Xóa hồ sơ giảng viên", description = "Xóa hồ sơ giảng viên khỏi hệ thống học vụ")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Xóa thành công")
    })
    @DeleteMapping("/{id}")
    public Map<String, String> delete(
            @Parameter(description = "Mã định danh giảng viên (UUID)", required = true) @PathVariable String id,
            Authentication authentication) {
        lecturers.delete(id, isSuperAdmin(authentication));
        return Map.of("message", "Lecturer deleted successfully");
    }

    private static boolean isSuperAdmin(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_SUPER_ADMIN".equals(authority.getAuthority()));
    }
}
