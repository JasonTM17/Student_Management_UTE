package io.campuscore.restfulapi.thesis.web;

import io.campuscore.restfulapi.thesis.domain.RoundStatus;
import io.campuscore.restfulapi.thesis.service.ThesisMutationService;
import io.campuscore.restfulapi.thesis.service.ThesisReportService;
import io.campuscore.restfulapi.thesis.service.ThesisSupervisorService;
import io.campuscore.restfulapi.thesis.web.ThesisGroupReadDtos.GroupResponse;
import io.campuscore.restfulapi.thesis.web.ThesisMutationDtos.GroupCreateRequest;
import io.campuscore.restfulapi.thesis.web.ThesisMutationDtos.GroupRejectionRequest;
import io.campuscore.restfulapi.thesis.web.ThesisMutationDtos.MemberRequest;
import io.campuscore.restfulapi.thesis.web.ThesisMutationDtos.ProgressRequest;
import io.campuscore.restfulapi.thesis.web.ThesisMutationDtos.RoundCreateRequest;
import io.campuscore.restfulapi.thesis.web.ThesisMutationDtos.StudentSearchResponse;
import io.campuscore.restfulapi.thesis.web.ThesisMutationDtos.TopicAssignmentRequest;
import io.campuscore.restfulapi.thesis.web.ThesisMutationDtos.TopicCreateRequest;
import io.campuscore.restfulapi.thesis.web.ThesisMutationDtos.TopicUpdateRequest;
import io.campuscore.restfulapi.thesis.service.ThesisReportService.ReportResponse;
import io.campuscore.restfulapi.thesis.web.ThesisRoundDtos.RoundResponse;
import io.campuscore.restfulapi.thesis.web.ThesisTopicDtos.TopicResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Brief-governed thesis mutations. Round lifecycle ownership sits with the
 * faculty head (TRUONG_KHOA) with ADMIN as staff fallback; the legacy
 * SUPER_ADMIN grant is intentionally not accepted in this package.
 */
@RestController
@Profile("persistence")
@RequestMapping("/api/v1/thesis")
public class ThesisMutationController {

    private final ThesisMutationService mutations;
    private final ThesisSupervisorService supervisors;
    private final ThesisReportService reports;

    public ThesisMutationController(
            ThesisMutationService mutations,
            ThesisSupervisorService supervisors,
            ThesisReportService reports) {
        this.mutations = mutations;
        this.supervisors = supervisors;
        this.reports = reports;
    }

    @PostMapping("/rounds")
    @PreAuthorize("hasAnyRole('ADMIN','TRUONG_KHOA')")
    public RoundResponse createRound(@RequestBody RoundCreateRequest request) {
        return mutations.createRound(request);
    }

    /** Brief phase one: the faculty head opens the lecturer topic-submission window. */
    @PostMapping("/rounds/{id}/open-proposals")
    @PreAuthorize("hasAnyRole('ADMIN','TRUONG_KHOA')")
    public RoundResponse openProposals(@PathVariable UUID id) {
        return mutations.transitionRound(id, RoundStatus.DRAFT, RoundStatus.PROPOSAL_OPEN);
    }

    /** Brief phase one closure: publish the topic catalog to student groups. */
    @PostMapping("/rounds/{id}/publish-proposals")
    @PreAuthorize("hasAnyRole('ADMIN','TRUONG_KHOA')")
    public RoundResponse publishProposals(@PathVariable UUID id) {
        return mutations.transitionRound(id, RoundStatus.PROPOSAL_OPEN, RoundStatus.PROPOSALS_PUBLISHED);
    }

    /** Brief phase two: student groups may register inside the student window. */
    @PostMapping("/rounds/{id}/open-registration")
    @PreAuthorize("hasAnyRole('ADMIN','TRUONG_KHOA')")
    public RoundResponse openRegistration(@PathVariable UUID id) {
        return mutations.transitionRound(id, RoundStatus.PROPOSALS_PUBLISHED, RoundStatus.REGISTRATION_OPEN);
    }

    @PostMapping("/rounds/{id}/close-registration")
    @PreAuthorize("hasAnyRole('ADMIN','TRUONG_KHOA')")
    public RoundResponse closeRegistration(@PathVariable UUID id) {
        return mutations.transitionRound(id, RoundStatus.REGISTRATION_OPEN, RoundStatus.REGISTRATION_CLOSED);
    }

    /** Brief R9: publish graded results to the students of the round. */
    @PostMapping("/rounds/{id}/publish-results")
    @PreAuthorize("hasAnyRole('ADMIN','TRUONG_KHOA')")
    public RoundResponse publishResults(@PathVariable UUID id) {
        return mutations.publishResults(id);
    }

    @PostMapping("/topics")
    @PreAuthorize("hasAnyRole('ADMIN','TRUONG_KHOA','LECTURER')")
    public TopicResponse createTopic(
            @RequestBody TopicCreateRequest request,
            @AuthenticationPrincipal Jwt actor) {
        return mutations.createTopic(request, actor);
    }

    @PutMapping("/topics/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TRUONG_KHOA','LECTURER')")
    public TopicResponse updateTopic(
            @PathVariable UUID id,
            @RequestBody TopicUpdateRequest request,
            @AuthenticationPrincipal Jwt actor) {
        return mutations.updateTopic(id, request, actor);
    }

    @PostMapping("/topics/{id}/publish")
    @PreAuthorize("hasAnyRole('ADMIN','TRUONG_KHOA','LECTURER')")
    public TopicResponse publishTopic(@PathVariable UUID id, @AuthenticationPrincipal Jwt actor) {
        return mutations.publishTopic(id, actor);
    }

    /** Brief R3: replace the topic's supervisor list (one or two lecturers). */
    @PutMapping("/topics/{id}/supervisors")
    @PreAuthorize("hasAnyRole('ADMIN','TRUONG_KHOA','LECTURER')")
    public List<ThesisSupervisorService.SupervisorRow> setSupervisors(
            @PathVariable UUID id,
            @RequestBody Map<String, List<String>> request,
            @AuthenticationPrincipal Jwt actor) {
        List<String> lecturerIds = request == null ? null : request.get("supervisorIds");
        return supervisors.setSupervisors(id, lecturerIds, actor);
    }

    @GetMapping("/topics/{id}/supervisors")
    @PreAuthorize("hasAnyRole('ADMIN','TRUONG_KHOA','LECTURER','STUDENT')")
    public List<ThesisSupervisorService.SupervisorRow> listSupervisors(@PathVariable UUID id) {
        return supervisors.list(id);
    }

    @PostMapping("/groups")
    @PreAuthorize("hasAnyRole('STUDENT','ADMIN')")
    public GroupResponse createGroup(
            @RequestBody GroupCreateRequest request,
            @AuthenticationPrincipal Jwt actor) {
        return mutations.createGroup(request, actor);
    }

    @PostMapping("/groups/{id}/members")
    @PreAuthorize("hasAnyRole('STUDENT','ADMIN')")
    public GroupResponse addMember(
            @PathVariable UUID id,
            @RequestBody MemberRequest request,
            @AuthenticationPrincipal Jwt actor) {
        return mutations.addMember(id, request, actor);
    }

    @GetMapping("/students/search")
    @PreAuthorize("hasAnyRole('STUDENT','ADMIN')")
    public List<StudentSearchResponse> searchStudents(@RequestParam String q) {
        return mutations.searchStudents(q);
    }

    @DeleteMapping("/groups/{id}/members/{studentId}")
    @PreAuthorize("hasAnyRole('STUDENT','ADMIN')")
    public GroupResponse removeMember(
            @PathVariable UUID id,
            @PathVariable String studentId,
            @AuthenticationPrincipal Jwt actor) {
        return mutations.removeMember(id, studentId, actor);
    }

    @PostMapping("/groups/{id}/topic")
    @PreAuthorize("hasAnyRole('STUDENT','ADMIN')")
    public GroupResponse assignTopic(
            @PathVariable UUID id,
            @RequestBody TopicAssignmentRequest request,
            @AuthenticationPrincipal Jwt actor) {
        return mutations.assignTopic(id, request, actor);
    }

    @PatchMapping("/groups/{id}/progress")
    @PreAuthorize("hasAnyRole('STUDENT','ADMIN')")
    public GroupResponse updateProgress(
            @PathVariable UUID id,
            @RequestBody ProgressRequest request,
            @AuthenticationPrincipal Jwt actor) {
        return mutations.updateProgress(id, request, actor);
    }

    /** Brief R5: only the group leader submits the topic report. */
    @PostMapping("/groups/{id}/report")
    @PreAuthorize("hasAnyRole('STUDENT','ADMIN','TRUONG_KHOA','LECTURER')")
    public ReportResponse submitReport(
            @PathVariable UUID id,
            @RequestBody Map<String, String> request,
            @AuthenticationPrincipal Jwt actor) {
        return reports.submit(
                id,
                request == null ? null : request.get("title"),
                request == null ? null : request.get("url"),
                request == null ? null : request.get("note"),
                actor);
    }

    @GetMapping("/groups/{id}/report")
    @PreAuthorize("hasAnyRole('STUDENT','ADMIN','TRUONG_KHOA','LECTURER')")
    public ReportResponse getReport(@PathVariable UUID id, @AuthenticationPrincipal Jwt actor) {
        return reports.get(id, actor);
    }

    @GetMapping("/topics/{id}/report")
    @PreAuthorize("hasAnyRole('STUDENT','ADMIN','TRUONG_KHOA','LECTURER')")
    public org.springframework.http.ResponseEntity<ReportResponse> getReportByTopic(@PathVariable UUID id, @AuthenticationPrincipal Jwt actor) {
        ReportResponse response = reports.getByTopic(id, actor);
        if (response == null) {
            return org.springframework.http.ResponseEntity.noContent().build();
        }
        return org.springframework.http.ResponseEntity.ok(response);
    }

    @PostMapping("/groups/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN','TRUONG_KHOA','LECTURER')")
    public GroupResponse approveGroup(@PathVariable UUID id, @AuthenticationPrincipal Jwt actor) {
        return mutations.approveGroup(id, actor);
    }

    @PostMapping("/groups/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMIN','TRUONG_KHOA','LECTURER')")
    public GroupResponse rejectGroup(
            @PathVariable UUID id,
            @RequestBody GroupRejectionRequest request,
            @AuthenticationPrincipal Jwt actor) {
        return mutations.rejectGroup(id, request, actor);
    }
}
