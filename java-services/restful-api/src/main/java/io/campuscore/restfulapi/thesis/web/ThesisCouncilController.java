package io.campuscore.restfulapi.thesis.web;

import io.campuscore.restfulapi.thesis.service.ThesisCouncilService;
import io.campuscore.restfulapi.thesis.service.ThesisCouncilService.CouncilResponse;
import io.campuscore.restfulapi.thesis.service.ThesisCouncilService.ScoreResponse;
import io.campuscore.restfulapi.thesis.service.ThesisCouncilService.StudentResultRow;
import io.campuscore.restfulapi.thesis.service.ThesisCouncilService.TopicResult;
import io.campuscore.restfulapi.thesis.web.ThesisCouncilRequestDtos.AddCouncilMemberRequest;
import io.campuscore.restfulapi.thesis.web.ThesisCouncilRequestDtos.AssignTopicRequest;
import io.campuscore.restfulapi.thesis.web.ThesisCouncilRequestDtos.CreateCouncilRequest;
import io.campuscore.restfulapi.thesis.web.ThesisCouncilRequestDtos.SubmitScoreRequest;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Brief R6–R9: council management, component scoring, and student results. */
@Tag(name = "Thesis Defense Councils & Grading", description = "Quản lý hội đồng bảo vệ khóa luận, cơ cấu thành viên hội đồng, phân công đề tài phản biện và nhập điểm thành phần")
@RestController
@Profile("persistence")
@RequestMapping("/api/v1/thesis")
public class ThesisCouncilController {

    private final ThesisCouncilService councils;

    public ThesisCouncilController(ThesisCouncilService councils) {
        this.councils = councils;
    }

    @Operation(summary = "Tạo hội đồng đánh giá khóa luận", description = "Khởi tạo hội đồng đánh giá/chấm điểm khóa luận cho một đợt cụ thể")
    @PostMapping("/councils")
    @PreAuthorize("hasAnyRole('ADMIN','TRUONG_KHOA','SUPER_ADMIN')")
    public CouncilResponse createCouncil(
            @Valid @RequestBody CreateCouncilRequest request,
            @AuthenticationPrincipal Jwt actor) {
        return councils.createCouncil(parseUuid(request.roundId(), "roundId"), request.name(), actor);
    }

    @Operation(summary = "Danh sách hội đồng theo đợt khóa luận", description = "Truy xuất danh sách tất cả các hội đồng bảo vệ trong một đợt khóa luận")
    @GetMapping("/councils")
    @PreAuthorize("isAuthenticated()")
    public List<CouncilResponse> listCouncils(@Parameter(description = "Mã UUID của đợt", required = true) @RequestParam UUID roundId) {
        return councils.listByRound(roundId);
    }

    @Operation(summary = "Xem chi tiết một hội đồng", description = "Truy xuất thông tin chi tiết một hội đồng: danh sách giảng viên thành viên và đề tài được phân công")
    @GetMapping("/councils/{id}")
    @PreAuthorize("isAuthenticated()")
    public CouncilResponse getCouncil(@Parameter(description = "Mã UUID của hội đồng", required = true) @PathVariable UUID id) {
        return councils.getCouncil(id);
    }

    @PostMapping("/councils/{id}/members")
    @PreAuthorize("hasAnyRole('ADMIN','TRUONG_KHOA','SUPER_ADMIN')")
    public CouncilResponse addMember(
            @PathVariable UUID id,
            @Valid @RequestBody AddCouncilMemberRequest request,
            @AuthenticationPrincipal Jwt actor) {
        return councils.addMember(id, request.lecturerId(), request.memberRole(), actor);
    }

    @DeleteMapping("/councils/{id}/members/{lecturerId}")
    @PreAuthorize("hasAnyRole('ADMIN','TRUONG_KHOA','SUPER_ADMIN')")
    public CouncilResponse removeMember(
            @PathVariable UUID id,
            @PathVariable String lecturerId,
            @AuthenticationPrincipal Jwt actor) {
        return councils.removeMember(id, lecturerId, actor);
    }

    @PostMapping("/councils/{id}/topics")
    @PreAuthorize("hasAnyRole('ADMIN','TRUONG_KHOA','SUPER_ADMIN')")
    public CouncilResponse assignTopic(
            @PathVariable UUID id,
            @Valid @RequestBody AssignTopicRequest request,
            @AuthenticationPrincipal Jwt actor) {
        return councils.assignTopic(id, parseUuid(request.topicId(), "topicId"), actor);
    }

    @PostMapping("/councils/{councilId}/topics/{topicId}/scores")
    @PreAuthorize("hasAnyRole('ADMIN','TRUONG_KHOA','LECTURER','SUPER_ADMIN')")
    public ScoreResponse submitScore(
            @PathVariable UUID councilId,
            @PathVariable UUID topicId,
            @Valid @RequestBody SubmitScoreRequest request,
            @AuthenticationPrincipal Jwt actor) {
        return councils.submitScore(councilId, topicId, request.component(), parseScore(request.score()), actor);
    }

    @GetMapping("/councils/{councilId}/topics/{topicId}/scores")
    @PreAuthorize("hasAnyRole('ADMIN','TRUONG_KHOA','LECTURER','SUPER_ADMIN')")
    public List<ScoreResponse> listScores(
            @PathVariable UUID councilId,
            @PathVariable UUID topicId,
            @AuthenticationPrincipal Jwt actor) {
        return councils.listScores(councilId, topicId, actor);
    }

    @PostMapping("/councils/{councilId}/topics/{topicId}/finalize")
    @PreAuthorize("hasAnyRole('ADMIN','TRUONG_KHOA','LECTURER','SUPER_ADMIN')")
    public TopicResult finalizeScores(
            @PathVariable UUID councilId,
            @PathVariable UUID topicId,
            @AuthenticationPrincipal Jwt actor) {
        return councils.finalizeScores(councilId, topicId, actor);
    }

    /** Brief R9: students read their own graded result after publication. */
    @GetMapping("/me/results")
    @PreAuthorize("hasAnyRole('STUDENT','LECTURER','ADMIN','TRUONG_KHOA','SUPER_ADMIN')")
    public List<StudentResultRow> studentResults(
            @RequestParam UUID roundId,
            @AuthenticationPrincipal Jwt actor) {
        String studentId = actor == null ? null : actor.getClaimAsString("studentId");
        if (studentId == null || studentId.isBlank()) {
            return java.util.Collections.emptyList();
        }
        return councils.studentResults(roundId, studentId);
    }

    private static UUID parseUuid(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new io.campuscore.restfulapi.web.DomainException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", field + " is required");
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException exception) {
            throw new io.campuscore.restfulapi.web.DomainException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", field + " must be a UUID");
        }
    }

    private static BigDecimal parseScore(String value) {
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException exception) {
            throw new io.campuscore.restfulapi.web.DomainException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "score must be numeric");
        }
    }
}
