package io.campuscore.restfulapi.thesis.web;

import io.campuscore.restfulapi.thesis.service.ThesisGroupReadService;
import io.campuscore.restfulapi.thesis.web.ThesisGroupReadDtos.GroupResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Thesis Groups & Members", description = "Quản lý và tra cứu nhóm sinh viên thực hiện khóa luận, thành viên nhóm và phân công đề tài")
@RestController
@Profile("persistence")
@RequestMapping("/api/v1/thesis/groups")
public class ThesisGroupReadController {

    private final ThesisGroupReadService groups;

    public ThesisGroupReadController(ThesisGroupReadService groups) {
        this.groups = groups;
    }

    @Operation(summary = "Lấy danh sách các nhóm sinh viên trong đợt khóa luận", description = "Truy xuất danh sách nhóm sinh viên làm khóa luận theo đợt đăng ký, bao gồm nhóm trưởng và thành viên")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Truy xuất danh sách nhóm thành công"),
        @ApiResponse(responseCode = "401", description = "Chưa xác thực"),
        @ApiResponse(responseCode = "403", description = "Không có quyền truy cập")
    })
    @GetMapping
    @PreAuthorize("hasAnyRole('STUDENT','LECTURER','ADMIN','TRUONG_KHOA')")
    public List<GroupResponse> list(
            @Parameter(description = "Mã UUID của đợt khóa luận", required = true)
            @RequestParam UUID roundId,
            @AuthenticationPrincipal Jwt actor) {
        return groups.list(roundId, roles(actor), actor.getClaimAsString("studentId"),
                actor.getClaimAsString("lecturerId"));
    }

    @Operation(summary = "Xem chi tiết một nhóm sinh viên theo UUID", description = "Truy xuất thông tin chi tiết một nhóm khóa luận: đề tài đã chọn, trạng thái phê duyệt của GVHD và danh sách thành viên")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Tìm thấy nhóm khóa luận"),
        @ApiResponse(responseCode = "404", description = "Không tìm thấy nhóm với UUID tương ứng")
    })
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('STUDENT','LECTURER','ADMIN','TRUONG_KHOA')")
    public GroupResponse get(
            @Parameter(description = "Mã UUID của nhóm", required = true)
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt actor) {
        return groups.get(id, roles(actor), actor.getClaimAsString("studentId"),
                actor.getClaimAsString("lecturerId"));
    }

    private static List<String> roles(Jwt actor) {
        List<String> roles = actor.getClaimAsStringList("roles");
        return roles == null ? List.of() : roles;
    }
}
