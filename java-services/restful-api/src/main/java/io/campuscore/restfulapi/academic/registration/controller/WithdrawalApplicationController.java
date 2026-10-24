package io.campuscore.restfulapi.academic.registration.controller;

import io.campuscore.restfulapi.academic.registration.dto.WithdrawalApplicationDtos.CreateRequest;
import io.campuscore.restfulapi.academic.registration.dto.WithdrawalApplicationDtos.Response;
import io.campuscore.restfulapi.academic.registration.dto.WithdrawalApplicationDtos.ReviewRequest;
import io.campuscore.restfulapi.academic.registration.dto.WithdrawalApplicationDtos.WindowResponse;
import io.campuscore.restfulapi.academic.registration.service.WithdrawalApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Course Withdrawal (grade W)", description = "Đơn xin rút học phần có phê duyệt trong 2 tuần đầu học kỳ chính — duyệt thì ghi điểm W trên bảng điểm, không tính GPA")
@RestController
@Profile("persistence")
@RequestMapping("/api/v1")
public class WithdrawalApplicationController {

    private final WithdrawalApplicationService withdrawals;

    public WithdrawalApplicationController(WithdrawalApplicationService withdrawals) {
        this.withdrawals = withdrawals;
    }

    @Operation(summary = "Cửa sổ rút học phần của học kỳ", description = "Trả về trạng thái mở/đóng và mốc thời gian của cửa sổ rút học phần (2 tuần đầu học kỳ chính, giờ Asia/Bangkok)")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Truy vấn thành công")})
    @GetMapping("me/registration/withdrawal-window")
    @PreAuthorize("hasRole('STUDENT')")
    public WindowResponse window(
            @Parameter(description = "Mã định danh học kỳ (UUID)", required = true) @RequestParam String semesterId) {
        return withdrawals.window(semesterId);
    }

    @Operation(summary = "Đơn rút học phần của tôi", description = "Danh sách đơn xin rút học phần (W) mà sinh viên đã nộp")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Truy vấn thành công")})
    @GetMapping("me/registration/withdrawal-applications")
    @PreAuthorize("hasRole('STUDENT')")
    public List<Response> mine(@AuthenticationPrincipal Jwt jwt) {
        return withdrawals.listForStudent(jwt.getClaimAsString("studentId"));
    }

    @Operation(summary = "Nộp đơn xin rút học phần", description = "Sinh viên nộp đơn rút một học phần đang ghi danh trong 2 tuần đầu học kỳ chính, kèm lý do")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Nộp đơn thành công"),
        @ApiResponse(responseCode = "403", description = "Học phần không thuộc sinh viên"),
        @ApiResponse(responseCode = "409", description = "Ngoài cửa sổ rút, học kỳ không mở/không phải kỳ chính, hoặc đã có đơn đang chờ")
    })
    @PostMapping("me/registration/withdrawal-applications")
    @PreAuthorize("hasRole('STUDENT')")
    public Response submit(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateRequest request) {
        return withdrawals.submit(
                jwt.getClaimAsString("studentId"), jwt.getSubject(), request.enrollmentId(), request.reason());
    }

    @Operation(summary = "Thu hồi đơn rút học phần", description = "Sinh viên tự hủy đơn đang chờ duyệt của chính mình")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Thu hồi thành công"),
        @ApiResponse(responseCode = "409", description = "Đơn không còn ở trạng thái chờ")
    })
    @PostMapping("me/registration/withdrawal-applications/{id}/cancel")
    @PreAuthorize("hasRole('STUDENT')")
    public Response cancel(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Mã định danh đơn (UUID)", required = true) @PathVariable String id) {
        return withdrawals.cancel(id, jwt.getClaimAsString("studentId"), jwt.getSubject());
    }

    @Operation(summary = "Danh sách đơn rút học phần (Phòng Đào tạo / Admin)", description = "Lấy danh sách đơn xin rút học phần cần xét duyệt")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Lấy danh sách thành công")})
    @GetMapping("admin/registration/withdrawal-applications")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public List<Response> list(
            @Parameter(description = "Trạng thái đơn (PENDING, APPROVED, REJECTED, CANCELLED, ALL)")
            @RequestParam(required = false, defaultValue = "PENDING") String status) {
        return withdrawals.listForReview(status);
    }

    @Operation(summary = "Xét duyệt đơn rút học phần", description = "Phòng Đào tạo phê duyệt (APPROVED → điểm W, trả chỗ) hoặc từ chối (REJECTED) đơn rút học phần")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Xét duyệt thành công"),
        @ApiResponse(responseCode = "404", description = "Không tìm thấy đơn"),
        @ApiResponse(responseCode = "409", description = "Đơn không còn chờ hoặc ghi danh không còn hiệu lực")
    })
    @PostMapping("admin/registration/withdrawal-applications/{id}/review")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Response review(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Mã định danh đơn (UUID)", required = true) @PathVariable String id,
            @Valid @RequestBody ReviewRequest request) {
        return withdrawals.review(id, request.decision(), jwt.getSubject(), request.note());
    }
}
