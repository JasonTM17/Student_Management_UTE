package io.campuscore.restfulapi.thesis.web;

import io.campuscore.restfulapi.thesis.service.ThesisCouncilService;
import io.campuscore.restfulapi.thesis.service.ThesisCouncilService.CouncilResponse;
import io.campuscore.restfulapi.thesis.service.ThesisCouncilService.ScoreResponse;
import io.campuscore.restfulapi.thesis.service.ThesisCouncilService.StudentResultRow;
import io.campuscore.restfulapi.thesis.service.ThesisCouncilService.TopicResult;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
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

/** Brief R6–R9: council management, component scoring, and student results. */
@RestController
@Profile("persistence")
@RequestMapping("/api/v1/thesis")
public class ThesisCouncilController {

    private final ThesisCouncilService councils;

    public ThesisCouncilController(ThesisCouncilService councils) {
        this.councils = councils;
    }

    @PostMapping("/councils")
    @PreAuthorize("hasAnyRole('ADMIN','TRUONG_KHOA')")
    public CouncilResponse createCouncil(@RequestBody Map<String, Object> request, @AuthenticationPrincipal Jwt actor) {
        String roundIdRaw = request == null ? null : (String) request.get("roundId");
        String name = request == null ? null : (String) request.get("name");
        return councils.createCouncil(parseUuid(roundIdRaw, "roundId"), name, actor);
    }

    @GetMapping("/councils")
    @PreAuthorize("isAuthenticated()")
    public List<CouncilResponse> listCouncils(@RequestParam UUID roundId) {
        return councils.listByRound(roundId);
    }

    @GetMapping("/councils/{id}")
    @PreAuthorize("isAuthenticated()")
    public CouncilResponse getCouncil(@PathVariable UUID id) {
        return councils.getCouncil(id);
    }

    @PostMapping("/councils/{id}/members")
    @PreAuthorize("hasAnyRole('ADMIN','TRUONG_KHOA')")
    public CouncilResponse addMember(
            @PathVariable UUID id,
            @RequestBody Map<String, String> request,
            @AuthenticationPrincipal Jwt actor) {
        return councils.addMember(id, request == null ? null : request.get("lecturerId"),
                request == null ? null : request.get("memberRole"), actor);
    }

    @DeleteMapping("/councils/{id}/members/{lecturerId}")
    @PreAuthorize("hasAnyRole('ADMIN','TRUONG_KHOA')")
    public CouncilResponse removeMember(
            @PathVariable UUID id,
            @PathVariable String lecturerId,
            @AuthenticationPrincipal Jwt actor) {
        return councils.removeMember(id, lecturerId, actor);
    }

    @PostMapping("/councils/{id}/topics")
    @PreAuthorize("hasAnyRole('ADMIN','TRUONG_KHOA')")
    public CouncilResponse assignTopic(
            @PathVariable UUID id,
            @RequestBody Map<String, String> request,
            @AuthenticationPrincipal Jwt actor) {
        String topicIdRaw = request == null ? null : request.get("topicId");
        return councils.assignTopic(id, parseUuid(topicIdRaw, "topicId"), actor);
    }

    @PostMapping("/councils/{councilId}/topics/{topicId}/scores")
    @PreAuthorize("hasAnyRole('ADMIN','TRUONG_KHOA','LECTURER')")
    public ScoreResponse submitScore(
            @PathVariable UUID councilId,
            @PathVariable UUID topicId,
            @RequestBody Map<String, Object> request,
            @AuthenticationPrincipal Jwt actor) {
        Object rawComponent = request == null ? null : request.get("component");
        String component = rawComponent == null ? null : rawComponent.toString();
        BigDecimal score = parseScore(request == null ? null : request.get("score"));
        return councils.submitScore(councilId, topicId, component, score, actor);
    }

    @GetMapping("/councils/{councilId}/topics/{topicId}/scores")
    @PreAuthorize("hasAnyRole('ADMIN','TRUONG_KHOA','LECTURER')")
    public List<ScoreResponse> listScores(
            @PathVariable UUID councilId,
            @PathVariable UUID topicId,
            @AuthenticationPrincipal Jwt actor) {
        return councils.listScores(councilId, topicId, actor);
    }

    @PostMapping("/councils/{councilId}/topics/{topicId}/finalize")
    @PreAuthorize("hasAnyRole('ADMIN','TRUONG_KHOA','LECTURER')")
    public TopicResult finalizeScores(
            @PathVariable UUID councilId,
            @PathVariable UUID topicId,
            @AuthenticationPrincipal Jwt actor) {
        return councils.finalizeScores(councilId, topicId, actor);
    }

    /** Brief R9: students read their own graded result after publication. */
    @GetMapping("/me/results")
    @PreAuthorize("hasAnyRole('STUDENT','ADMIN','TRUONG_KHOA')")
    public List<StudentResultRow> studentResults(
            @RequestParam UUID roundId,
            @AuthenticationPrincipal Jwt actor) {
        String studentId = actor == null ? null : actor.getClaimAsString("studentId");
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

    private static BigDecimal parseScore(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException exception) {
            throw new io.campuscore.restfulapi.web.DomainException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "score must be numeric");
        }
    }
}
