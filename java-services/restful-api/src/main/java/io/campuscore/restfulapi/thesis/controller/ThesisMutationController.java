package io.campuscore.restfulapi.thesis.controller;

import io.campuscore.restfulapi.thesis.model.RoundStatus;
import io.campuscore.restfulapi.thesis.model.StoredReport;
import io.campuscore.restfulapi.thesis.dto.SupervisorRow;
import io.campuscore.restfulapi.thesis.service.ThesisMutationService;
import io.campuscore.restfulapi.thesis.service.ThesisReportService;
import io.campuscore.restfulapi.thesis.service.ThesisSupervisorService;
import io.campuscore.restfulapi.thesis.dto.ThesisGroupReadDtos.GroupResponse;
import io.campuscore.restfulapi.thesis.dto.ThesisMutationDtos.CloseRegistrationPreviewResponse;
import io.campuscore.restfulapi.thesis.dto.ThesisMutationDtos.GroupApprovalRequest;
import io.campuscore.restfulapi.thesis.dto.ThesisMutationDtos.GroupApprovalWithdrawalRequest;
import io.campuscore.restfulapi.thesis.dto.ThesisMutationDtos.GroupCreateRequest;
import io.campuscore.restfulapi.thesis.dto.ThesisMutationDtos.GroupRejectionRequest;
import io.campuscore.restfulapi.thesis.dto.ThesisMutationDtos.GvpbDeadlineUpdateRequest;
import io.campuscore.restfulapi.thesis.dto.ThesisMutationDtos.LateTopicRequest;
import io.campuscore.restfulapi.thesis.dto.ThesisMutationDtos.PublishResultsRequest;
import io.campuscore.restfulapi.thesis.dto.ThesisMutationDtos.RetractPublicationRequest;
import io.campuscore.restfulapi.thesis.dto.ThesisMutationDtos.RosterOverrideRequest;
import io.campuscore.restfulapi.thesis.dto.ThesisMutationDtos.TopicReassignRequest;
import io.campuscore.restfulapi.thesis.dto.ThesisMutationDtos.MemberRequest;
import io.campuscore.restfulapi.thesis.dto.ThesisMutationDtos.ProgressRequest;
import io.campuscore.restfulapi.thesis.dto.ThesisMutationDtos.PublishResultsPreviewResponse;
import io.campuscore.restfulapi.thesis.dto.ThesisMutationDtos.ReportDeadlineUpdateRequest;
import io.campuscore.restfulapi.thesis.dto.ThesisMutationDtos.ReportUpdateRequest;
import io.campuscore.restfulapi.thesis.dto.ThesisMutationDtos.RoundClosureRequest;
import io.campuscore.restfulapi.thesis.dto.ThesisMutationDtos.RoundCreateRequest;
import io.campuscore.restfulapi.thesis.dto.ThesisMutationDtos.RoundDeleteRequest;
import io.campuscore.restfulapi.thesis.dto.ThesisMutationDtos.RoundRestoreRequest;
import io.campuscore.restfulapi.thesis.dto.ThesisMutationDtos.StudentSearchResponse;
import io.campuscore.restfulapi.thesis.dto.ThesisMutationDtos.TopicAssignmentRequest;
import io.campuscore.restfulapi.thesis.dto.ThesisMutationDtos.TopicCreateRequest;
import io.campuscore.restfulapi.thesis.dto.ThesisMutationDtos.TopicOutcomeRequest;
import io.campuscore.restfulapi.thesis.dto.ThesisMutationDtos.TopicUpdateRequest;
import io.campuscore.restfulapi.thesis.dto.ReportResponse;
import io.campuscore.restfulapi.thesis.dto.RepositoryReportResponse;
import io.campuscore.restfulapi.thesis.dto.ThesisRoundDtos.RoundResponse;
import io.campuscore.restfulapi.thesis.dto.ThesisTopicDtos.TopicResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.campuscore.restfulapi.thesis.dto.ThesisGroupReadDtos;
import io.campuscore.restfulapi.thesis.dto.ThesisMutationDtos;
import io.campuscore.restfulapi.thesis.dto.ThesisRoundDtos;
import io.campuscore.restfulapi.thesis.dto.ThesisTopicDtos;

/**
 * Brief-governed thesis mutations. Round lifecycle ownership sits with the
 * faculty head (TRUONG_KHOA) with ADMIN as staff fallback; SUPER_ADMIN is
 * accepted everywhere ADMIN is, matching ThesisMutationService.isAdmin() and the
 * rest of the API surface, so the admin console never offers a button the
 * server rejects.
 */
@Tag(name = "Thesis Lifecycle Mutations", description = "Quy trình chuyển đổi trạng thái đợt khóa luận, đề xuất đề tài, phân công GVHD, quản lý nhóm và nộp báo cáo")
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

    @Operation(summary = "Khởi tạo đợt khóa luận mới", description = "Tạo một đợt đăng ký khóa luận/tiểu luận mới ở trạng thái DRAFT")
    @PostMapping("/rounds")
    @PreAuthorize("hasAnyRole('ADMIN','TRUONG_KHOA','SUPER_ADMIN')")
    public RoundResponse createRound(@RequestBody RoundCreateRequest request,
            @AuthenticationPrincipal Jwt actor) {
        return mutations.createRound(request, actor);
    }

    @Operation(summary = "Điều chỉnh lịch của đợt", description = "Cập nhật tên, loại và toàn bộ mốc thời gian của đợt còn ở DRAFT, PROPOSAL_OPEN hoặc PROPOSALS_PUBLISHED")
    @PutMapping("/rounds/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TRUONG_KHOA','SUPER_ADMIN')")
    public RoundResponse updateRound(
            @Parameter(description = "Mã UUID của đợt", required = true) @PathVariable UUID id,
            @RequestBody RoundCreateRequest request,
            @AuthenticationPrincipal Jwt actor) {
        return mutations.updateRound(id, request, actor);
    }

    @Operation(summary = "Hủy đợt đăng ký", description = "Chuyển đợt sang CANCELLED khi chưa công bố kết quả; nhóm giữ nguyên roster lịch sử")
    @PostMapping("/rounds/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN','TRUONG_KHOA','SUPER_ADMIN')")
    public RoundResponse cancelRound(
            @Parameter(description = "Mã UUID của đợt", required = true) @PathVariable UUID id,
            @AuthenticationPrincipal Jwt actor) {
        return mutations.cancelRound(id, actor);
    }

    @Operation(summary = "Khôi phục đợt đã hủy", description = "Đưa đợt CANCELLED về trạng thái trước khi hủy (đợt hủy cũ không có dấu trạng thái mà còn nhóm phải chọn targetStatus rõ ràng); bị chặn khi có sinh viên đã sang đợt khác còn hiệu lực")
    @PostMapping("/rounds/{id}/restore")
    @PreAuthorize("hasAnyRole('ADMIN','TRUONG_KHOA','SUPER_ADMIN')")
    public RoundResponse restoreRound(
            @Parameter(description = "Mã UUID của đợt", required = true) @PathVariable UUID id,
            @RequestBody(required = false) RoundRestoreRequest request,
            @AuthenticationPrincipal Jwt actor) {
        return mutations.restoreRound(id, request == null ? null : request.targetStatus(), actor);
    }

    /**
     * Removes a round that was created by mistake. Only an empty DRAFT or
     * CANCELLED round is deletable; a round holding any thesis data is refused
     * with ROUND_HAS_DATA so cancel stays the safe archive path.
     */
    @Operation(summary = "Xóa đợt tạo nhầm", description = "Xóa hẳn một đợt rỗng còn DRAFT hoặc đã CANCELLED (xóa đợt tạo nhầm); đợt đã có đề tài/nhóm/hội đồng bị từ chối với mã ROUND_HAS_DATA; yêu cầu lý do, ghi audit")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Xóa thành công"),
        @ApiResponse(responseCode = "404", description = "Không tìm thấy đợt (ROUND_NOT_FOUND)"),
        @ApiResponse(responseCode = "409", description = "Đợt không đủ điều kiện xóa (ROUND_NOT_DELETABLE hoặc ROUND_HAS_DATA kèm số lượng dữ liệu chặn)")
    })
    @DeleteMapping("/rounds/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TRUONG_KHOA','SUPER_ADMIN')")
    public org.springframework.http.ResponseEntity<Void> deleteRound(
            @Parameter(description = "Mã UUID của đợt", required = true) @PathVariable UUID id,
            @RequestBody RoundDeleteRequest request,
            @AuthenticationPrincipal Jwt actor) {
        mutations.deleteRound(id, request == null ? null : request.reason(), actor);
        return org.springframework.http.ResponseEntity.noContent().build();
    }

    /** Brief phase one: the faculty head opens the lecturer topic-submission window. */
    @Operation(summary = "Mở cổng đề xuất đề tài cho giảng viên", description = "Chuyển trạng thái đợt từ DRAFT sang PROPOSAL_OPEN để giảng viên nộp đề tài")
    @PostMapping("/rounds/{id}/open-proposals")
    @PreAuthorize("hasAnyRole('ADMIN','TRUONG_KHOA','SUPER_ADMIN')")
    public RoundResponse openProposals(@Parameter(description = "Mã UUID của đợt", required = true) @PathVariable UUID id,
            @AuthenticationPrincipal Jwt actor) {
        return mutations.transitionRound(id, RoundStatus.DRAFT, RoundStatus.PROPOSAL_OPEN, actor);
    }

    /** Brief phase one closure: publish the topic catalog to student groups. */
    @Operation(summary = "Công bố danh mục đề tài cho sinh viên", description = "Chuyển đợt sang PROPOSALS_PUBLISHED để sinh viên tìm hiểu danh mục đề tài đã duyệt")
    @PostMapping("/rounds/{id}/publish-proposals")
    @PreAuthorize("hasAnyRole('ADMIN','TRUONG_KHOA','SUPER_ADMIN')")
    public RoundResponse publishProposals(@Parameter(description = "Mã UUID của đợt", required = true) @PathVariable UUID id,
            @AuthenticationPrincipal Jwt actor) {
        return mutations.transitionRound(id, RoundStatus.PROPOSAL_OPEN, RoundStatus.PROPOSALS_PUBLISHED, actor);
    }

    /** Brief phase two: student groups may register inside the student window. */
    @Operation(summary = "Mở cổng đăng ký đề tài cho nhóm sinh viên", description = "Chuyển đợt sang REGISTRATION_OPEN để các nhóm sinh viên nộp nguyện vọng đề tài")
    @PostMapping("/rounds/{id}/open-registration")
    @PreAuthorize("hasAnyRole('ADMIN','TRUONG_KHOA','SUPER_ADMIN')")
    public RoundResponse openRegistration(@Parameter(description = "Mã UUID của đợt", required = true) @PathVariable UUID id,
            @AuthenticationPrincipal Jwt actor) {
        return mutations.transitionRound(id, RoundStatus.PROPOSALS_PUBLISHED, RoundStatus.REGISTRATION_OPEN, actor);
    }

    @Operation(summary = "Đóng cổng đăng ký đề tài", description = "Chuyển đợt sang REGISTRATION_CLOSED sau khi hết hạn đăng ký")
    @PostMapping("/rounds/{id}/close-registration")
    @PreAuthorize("hasAnyRole('ADMIN','TRUONG_KHOA','SUPER_ADMIN')")
    public RoundResponse closeRegistration(@Parameter(description = "Mã UUID của đợt", required = true) @PathVariable UUID id,
            @AuthenticationPrincipal Jwt actor) {
        return mutations.transitionRound(id, RoundStatus.REGISTRATION_OPEN, RoundStatus.REGISTRATION_CLOSED, actor);
    }

    /**
     * The close must never be blind (plan F2): the admin reviews group/report
     * counts and whether the report deadline already passed before confirming.
     */
    @Operation(summary = "Xem trước hệ quả khi đóng đăng ký", description = "Trả về số nhóm, số báo cáo đã nộp và trạng thái hạn nộp báo cáo của đợt trước khi đóng đăng ký")
    @GetMapping("/rounds/{id}/close-registration-preview")
    @PreAuthorize("hasAnyRole('ADMIN','TRUONG_KHOA','SUPER_ADMIN')")
    public CloseRegistrationPreviewResponse closeRegistrationPreview(
            @Parameter(description = "Mã UUID của đợt", required = true) @PathVariable UUID id,
            @AuthenticationPrincipal Jwt actor) {
        return mutations.closeRegistrationPreview(id);
    }

    /**
     * Escape hatch for the report-window dead-end (plan F1): while the round
     * is REGISTRATION_CLOSED the report deadline may be extended forward;
     * roster mutation and the GVPB scoring window stay closed. The change is
     * audited.
     */
    @Operation(summary = "Gia hạn nộp báo cáo", description = "Dời hạn nộp báo cáo (reportDeadline) của đợt đang REGISTRATION_CLOSED sang thời điểm mới; không đổi hạn chấm GVPB; thao tác được ghi audit")
    @PutMapping("/rounds/{id}/report-deadline")
    @PreAuthorize("hasAnyRole('ADMIN','TRUONG_KHOA','SUPER_ADMIN')")
    public RoundResponse extendReportDeadline(
            @Parameter(description = "Mã UUID của đợt", required = true) @PathVariable UUID id,
            @RequestBody ReportDeadlineUpdateRequest request,
            @AuthenticationPrincipal Jwt actor) {
        return mutations.extendReportDeadline(id, request == null ? null : request.reportDeadline(), actor);
    }

    /**
     * Escape hatch for the counter-review dead-end: while the round is live
     * the GVPB deadline may be extended forward (never backwards, never past
     * the report date). The change is audited.
     */
    @Operation(summary = "Gia hạn chấm phản biện (GVPB)", description = "Dời hạn chấm phản biện (gvpbDeadline) của đợt đang mở sang thời điểm mới; thao tác được ghi audit kèm lý do")
    @PutMapping("/rounds/{id}/gvpb-deadline")
    @PreAuthorize("hasAnyRole('ADMIN','TRUONG_KHOA','SUPER_ADMIN')")
    public RoundResponse extendGvpbDeadline(
            @Parameter(description = "Mã UUID của đợt", required = true) @PathVariable UUID id,
            @RequestBody GvpbDeadlineUpdateRequest request,
            @AuthenticationPrincipal Jwt actor) {
        return mutations.extendGvpbDeadline(id, request == null ? null : request.gvpbDeadline(),
                request == null ? null : request.reason(), actor);
    }

    /** Brief R9: publish graded results to the students of the round. */
    @Operation(summary = "Công bố điểm và kết quả bảo vệ khóa luận", description = "Chuyển đợt sang RESULTS_PUBLISHED để sinh viên xem điểm đánh giá chính thức")
    @PostMapping("/rounds/{id}/publish-results")
    @PreAuthorize("hasAnyRole('ADMIN','TRUONG_KHOA','SUPER_ADMIN')")
    public RoundResponse publishResults(@Parameter(description = "Mã UUID của đợt", required = true) @PathVariable UUID id,
            @RequestBody(required = false) PublishResultsRequest request,
            @AuthenticationPrincipal Jwt actor) {
        return mutations.publishResults(id, request == null ? null : request.confirmAllExcluded(), actor);
    }

    /**
     * The publish must name its exclusions: the admin reviews which topics are
     * left out of the completeness check (counter-review failed or withdrawn)
     * before confirming.
     */
    @Operation(summary = "Xem trước hệ quả khi công bố kết quả", description = "Trả về số nhóm sẽ công bố điểm và danh sách đề tài bị loại khỏi yêu cầu hoàn tất (trượt phản biện hoặc bị rút)")
    @GetMapping("/rounds/{id}/publish-results-preview")
    @PreAuthorize("hasAnyRole('ADMIN','TRUONG_KHOA','SUPER_ADMIN')")
    public PublishResultsPreviewResponse publishResultsPreview(
            @Parameter(description = "Mã UUID của đợt", required = true) @PathVariable UUID id,
            @AuthenticationPrincipal Jwt actor) {
        return mutations.publishResultsPreview(id, actor);
    }

    /**
     * Late-topic escape for a missed lecturer window: creates a PUBLISHED
     * topic for a named supervisor while students can still register.
     */
    @Operation(summary = "Thêm đề tài muộn", description = "Tạo đề tài PUBLISHED cho một giảng viên sau khi hết hạn nộp đề xuất, khi đợt đang cho sinh viên đăng ký; ghi audit kèm lý do")
    @PostMapping("/rounds/{id}/late-topic")
    @PreAuthorize("hasAnyRole('ADMIN','TRUONG_KHOA','SUPER_ADMIN')")
    public TopicResponse createLateTopic(
            @Parameter(description = "Mã UUID của đợt", required = true) @PathVariable UUID id,
            @RequestBody LateTopicRequest request,
            @AuthenticationPrincipal Jwt actor) {
        return mutations.createLateTopic(id, request, actor);
    }

    /**
     * Post-registration roster override: adds an active student to a group of
     * a REGISTRATION_CLOSED round whose topic has no scores yet.
     */
    @Operation(summary = "Điều chỉnh thành viên sau đóng đăng ký", description = "Thêm sinh viên vào nhóm của đợt REGISTRATION_CLOSED khi đề tài chưa có điểm; ghi audit kèm lý do")
    @PostMapping("/groups/{id}/roster-override")
    @PreAuthorize("hasAnyRole('ADMIN','TRUONG_KHOA','SUPER_ADMIN')")
    public GroupResponse overrideGroupRoster(
            @Parameter(description = "Mã UUID của nhóm", required = true) @PathVariable UUID id,
            @RequestBody RosterOverrideRequest request,
            @AuthenticationPrincipal Jwt actor) {
        return mutations.overrideGroupRoster(id, request, actor);
    }

    /**
     * Re-points an approved group orphaned when governance withdrew its topic
     * after registration closed; audited with a reason.
     */
    @Operation(summary = "Chuyển nhóm sang đề tài khác", description = "Chuyển nhóm đã duyệt bị mất đề tài do đề tài bị rút sau đóng đăng ký sang đề tài khác trong cùng đợt; ghi audit kèm lý do")
    @PostMapping("/groups/{id}/topic-reassign")
    @PreAuthorize("hasAnyRole('ADMIN','TRUONG_KHOA','SUPER_ADMIN')")
    public GroupResponse reassignOrphanedTopic(
            @Parameter(description = "Mã UUID của nhóm", required = true) @PathVariable UUID id,
            @RequestBody TopicReassignRequest request,
            @AuthenticationPrincipal Jwt actor) {
        return mutations.reassignOrphanedTopic(id, request, actor);
    }

    /**
     * Narrow retraction of a premature publication inside the grace period:
     * recalls the inbox notices, returns the round to REGISTRATION_CLOSED so
     * the normal publish path runs again; scores are never modified.
     */
    @Operation(summary = "Thu hồi công bố kết quả", description = "Thu hồi RESULTS_PUBLISHED trong thời hạn ân hạn; thư điện tử đã gửi không thể thu hồi; ghi audit kèm lý do và xác nhận")
    @PostMapping("/rounds/{id}/retract-results")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public RoundResponse retractResults(
            @Parameter(description = "Mã UUID của đợt", required = true) @PathVariable UUID id,
            @RequestBody RetractPublicationRequest request,
            @AuthenticationPrincipal Jwt actor) {
        return mutations.retractResults(id, request, actor);
    }

    /**
     * End-of-life archive: RESULTS_PUBLISHED -> CLOSED makes the round
     * read-only for every mutation while its published results stay
     * viewable. Refused while the retraction grace window is still open so
     * the documented undo path cannot silently disappear. Reason required,
     * audited, and no notifications are sent.
     */
    @Operation(summary = "Đóng đợt khóa luận", description = "Lưu trữ đợt RESULTS_PUBLISHED sang CLOSED: đợt trở nên chỉ-đọc, kết quả vẫn xem được; từ chối khi hạn thu hồi còn mở; bắt buộc lý do, ghi audit, không gửi thông báo")
    @PostMapping("/rounds/{id}/close")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public RoundResponse closeRound(
            @Parameter(description = "Mã UUID của đợt", required = true) @PathVariable UUID id,
            @RequestBody RoundClosureRequest request,
            @AuthenticationPrincipal Jwt actor) {
        return mutations.closeRound(id, request, actor);
    }

    /**
     * Reverses the archive: CLOSED -> RESULTS_PUBLISHED restores exactly the
     * published state the round left (results_published_at is preserved, so
     * the retract window stays measured from the original publication).
     * Reason required, audited, no notifications.
     */
    @Operation(summary = "Mở lại đợt đã đóng", description = "Đưa đợt CLOSED về RESULTS_PUBLISHED với đúng trạng thái công bố trước đó; bắt buộc lý do, ghi audit, không gửi thông báo")
    @PostMapping("/rounds/{id}/reopen")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public RoundResponse reopenRound(
            @Parameter(description = "Mã UUID của đợt", required = true) @PathVariable UUID id,
            @RequestBody RoundClosureRequest request,
            @AuthenticationPrincipal Jwt actor) {
        return mutations.reopenRound(id, request, actor);
    }

    /**
     * Governance outcome escape hatch: a topic that can no longer complete the
     * round stops blocking publication for the rest. Audited and reversible
     * through reinstate while unfinalized.
     */
    @Operation(summary = "Rút đề tài khỏi đợt", description = "Ghi nhận đề tài không thể hoàn tất đợt (kết quả WITHDRAWN) để các nhóm còn lại được công bố; ghi audit kèm lý do")
    @PostMapping("/topics/{id}/withdraw")
    @PreAuthorize("hasAnyRole('ADMIN','TRUONG_KHOA','SUPER_ADMIN')")
    public TopicResponse withdrawTopic(
            @Parameter(description = "Mã UUID của đề tài", required = true) @PathVariable UUID id,
            @RequestBody(required = false) TopicOutcomeRequest request,
            @AuthenticationPrincipal Jwt actor) {
        return mutations.withdrawTopic(id, request == null ? null : request.reason(), actor);
    }

    @Operation(summary = "Hoàn tác rút đề tài", description = "Đưa đề tài WITHDRAWN trở lại đợt khi chưa chốt điểm; ghi audit kèm lý do")
    @PostMapping("/topics/{id}/reinstate")
    @PreAuthorize("hasAnyRole('ADMIN','TRUONG_KHOA','SUPER_ADMIN')")
    public TopicResponse reinstateTopic(
            @Parameter(description = "Mã UUID của đề tài", required = true) @PathVariable UUID id,
            @RequestBody(required = false) TopicOutcomeRequest request,
            @AuthenticationPrincipal Jwt actor) {
        return mutations.reinstateTopic(id, request == null ? null : request.reason(), actor);
    }

    @PostMapping("/topics")
    @PreAuthorize("hasAnyRole('ADMIN','TRUONG_KHOA','TRUONG_BO_MON','LECTURER','SUPER_ADMIN')")
    public TopicResponse createTopic(
            @RequestBody TopicCreateRequest request,
            @AuthenticationPrincipal Jwt actor) {
        return mutations.createTopic(request, actor);
    }

    @PutMapping("/topics/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TRUONG_KHOA','TRUONG_BO_MON','LECTURER','SUPER_ADMIN')")
    public TopicResponse updateTopic(
            @PathVariable UUID id,
            @RequestBody TopicUpdateRequest request,
            @AuthenticationPrincipal Jwt actor) {
        return mutations.updateTopic(id, request, actor);
    }

    @PostMapping("/topics/{id}/publish")
    @PreAuthorize("hasAnyRole('ADMIN','TRUONG_KHOA','TRUONG_BO_MON','SUPER_ADMIN')")
    public TopicResponse publishTopic(@PathVariable UUID id, @AuthenticationPrincipal Jwt actor) {
        return mutations.publishTopic(id, actor);
    }

    @PostMapping("/topics/{id}/archive")
    @PreAuthorize("hasAnyRole('ADMIN','TRUONG_KHOA','TRUONG_BO_MON','LECTURER','SUPER_ADMIN')")
    public TopicResponse archiveTopic(@PathVariable UUID id, @AuthenticationPrincipal Jwt actor) {
        return mutations.archiveTopic(id, actor);
    }

    /** Brief R3: replace the topic's supervisor list (one or two lecturers). */
    @PutMapping("/topics/{id}/supervisors")
    @PreAuthorize("hasAnyRole('ADMIN','TRUONG_KHOA','TRUONG_BO_MON','LECTURER','SUPER_ADMIN')")
    public List<SupervisorRow> setSupervisors(
            @PathVariable UUID id,
            @RequestBody Map<String, List<String>> request,
            @AuthenticationPrincipal Jwt actor) {
        List<String> lecturerIds = request == null ? null : request.get("supervisorIds");
        return supervisors.setSupervisors(id, lecturerIds, actor);
    }

    /**
     * Students may read this because their group view needs the supervisor's
     * name. The service applies the topic visibility rule (drafts 404) and
     * withholds contact details from anyone who is not staff or the owner.
     */
    @GetMapping("/topics/{id}/supervisors")
    @PreAuthorize("hasAnyRole('ADMIN','TRUONG_KHOA','TRUONG_BO_MON','LECTURER','STUDENT','SUPER_ADMIN')")
    public List<SupervisorRow> listSupervisors(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt actor) {
        return supervisors.list(id, actor);
    }

    @PostMapping("/groups")
    @PreAuthorize("hasAnyRole('STUDENT','ADMIN','TRUONG_KHOA','SUPER_ADMIN')")
    public GroupResponse createGroup(
            @RequestBody GroupCreateRequest request,
            @AuthenticationPrincipal Jwt actor) {
        return mutations.createGroup(request, actor);
    }

    @PostMapping("/groups/{id}/members")
    @PreAuthorize("hasAnyRole('STUDENT','LECTURER','ADMIN','TRUONG_KHOA','SUPER_ADMIN')")
    public GroupResponse addMember(
            @PathVariable UUID id,
            @RequestBody MemberRequest request,
            @AuthenticationPrincipal Jwt actor) {
        return mutations.addMember(id, request, actor);
    }

    @GetMapping("/students/search")
    @PreAuthorize("hasAnyRole('STUDENT','LECTURER','ADMIN','TRUONG_KHOA','SUPER_ADMIN')")
    public List<StudentSearchResponse> searchStudents(@RequestParam String q) {
        return mutations.searchStudents(q);
    }

    @DeleteMapping("/groups/{id}/members/{studentId}")
    @PreAuthorize("hasAnyRole('STUDENT','LECTURER','ADMIN','TRUONG_KHOA','SUPER_ADMIN')")
    public GroupResponse removeMember(
            @PathVariable UUID id,
            @PathVariable String studentId,
            @AuthenticationPrincipal Jwt actor) {
        return mutations.removeMember(id, studentId, actor);
    }

    @PostMapping("/groups/{id}/topic")
    @PreAuthorize("hasAnyRole('STUDENT','ADMIN','TRUONG_KHOA','SUPER_ADMIN')")
    public GroupResponse assignTopic(
            @PathVariable UUID id,
            @RequestBody TopicAssignmentRequest request,
            @AuthenticationPrincipal Jwt actor) {
        return mutations.assignTopic(id, request, actor);
    }

    @PatchMapping("/groups/{id}/progress")
    @PreAuthorize("hasAnyRole('STUDENT','ADMIN','TRUONG_KHOA','SUPER_ADMIN')")
    public GroupResponse updateProgress(
            @PathVariable UUID id,
            @RequestBody ProgressRequest request,
            @AuthenticationPrincipal Jwt actor) {
        return mutations.updateProgress(id, request, actor);
    }

    /** Brief R5: only the group leader submits the topic report (the service 403s everyone else). */
    @PostMapping("/groups/{id}/report")
    @PreAuthorize("hasRole('STUDENT')")
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

    /** Feedback item 7: submit the report as an attached Word/PDF document (leader only). */
    @PostMapping(value = "/groups/{id}/report/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('STUDENT')")
    public ReportResponse submitReportFile(
            @PathVariable UUID id,
            @RequestPart("file") MultipartFile file,
            @RequestPart(value = "title", required = false) String title,
            @RequestPart(value = "note", required = false) String note,
            @AuthenticationPrincipal Jwt actor) {
        return reports.submitFile(id, file, title, note, actor);
    }

    /**
     * Non-destructive report edit (plan F4): title/note/link only — the stored
     * document is never touched here, and clearing the link requires a file
     * artifact to remain.
     */
    @Operation(summary = "Chỉnh sửa báo cáo", description = "Cập nhật tiêu đề, ghi chú hoặc liên kết báo cáo mà không xóa tệp đính kèm; chỉ nhóm trưởng")
    @PatchMapping("/groups/{id}/report")
    @PreAuthorize("hasRole('STUDENT')")
    public ReportResponse updateReport(
            @PathVariable UUID id,
            @RequestBody ReportUpdateRequest request,
            @AuthenticationPrincipal Jwt actor) {
        return reports.updateReport(id, request, actor);
    }

    /**
     * Detaches the stored document while the link artifact stays (plan F4).
     * The object itself is deleted only after the row update commits.
     */
    @Operation(summary = "Gỡ tệp đính kèm khỏi báo cáo", description = "Xóa tệp báo cáo đính kèm nhưng giữ lại liên kết; chỉ nhóm trưởng")
    @DeleteMapping("/groups/{id}/report/file")
    @PreAuthorize("hasRole('STUDENT')")
    public ReportResponse removeReportFile(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt actor) {
        return reports.removeReportFile(id, actor);
    }

    /** Feedback item 7: download the attached document; same read matrix as the report metadata. */
    @GetMapping("/groups/{id}/report/file")
    @PreAuthorize("hasAnyRole('STUDENT','ADMIN','TRUONG_KHOA','TRUONG_BO_MON','LECTURER','SUPER_ADMIN')")
    public org.springframework.http.ResponseEntity<byte[]> downloadReportFile(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt actor) {
        StoredReport stored = reports.download(id, actor);
        String fileName = stored.fileName() == null ? "report" : stored.fileName();
        String encoded = java.net.URLEncoder.encode(fileName, java.nio.charset.StandardCharsets.UTF_8)
                .replace("+", "%20");
        String contentType = StringUtils.hasText(stored.contentType())
                ? stored.contentType()
                : MediaType.APPLICATION_OCTET_STREAM_VALUE;
        return org.springframework.http.ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + encoded)
                .header(org.springframework.http.HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.parseMediaType(contentType))
                .body(stored.data());
    }

    /** Download a repository artifact by its opaque report id. */
    @GetMapping("/reports/{id}/file")
    @PreAuthorize("hasAnyRole('ADMIN','TRUONG_KHOA','TRUONG_BO_MON','LECTURER','SUPER_ADMIN')")
    public org.springframework.http.ResponseEntity<byte[]> downloadRepositoryReportFile(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt actor) {
        StoredReport stored = reports.downloadByReport(id, actor);
        String fileName = stored.fileName() == null ? "report" : stored.fileName();
        String encoded = java.net.URLEncoder.encode(fileName, java.nio.charset.StandardCharsets.UTF_8)
                .replace("+", "%20");
        String contentType = StringUtils.hasText(stored.contentType())
                ? stored.contentType()
                : MediaType.APPLICATION_OCTET_STREAM_VALUE;
        return org.springframework.http.ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + encoded)
                .header(org.springframework.http.HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.parseMediaType(contentType))
                .body(stored.data());
    }

    @GetMapping("/groups/{id}/report")
    @PreAuthorize("hasAnyRole('STUDENT','ADMIN','TRUONG_KHOA','TRUONG_BO_MON','LECTURER','SUPER_ADMIN')")
    public ReportResponse getReport(@PathVariable UUID id, @AuthenticationPrincipal Jwt actor) {
        return reports.get(id, actor);
    }

    @GetMapping("/topics/{id}/report")
    @PreAuthorize("hasAnyRole('STUDENT','ADMIN','TRUONG_KHOA','TRUONG_BO_MON','LECTURER','SUPER_ADMIN')")
    public org.springframework.http.ResponseEntity<ReportResponse> getReportByTopic(@PathVariable UUID id, @AuthenticationPrincipal Jwt actor) {
        ReportResponse response = reports.getByTopic(id, actor);
        if (response == null) {
            return org.springframework.http.ResponseEntity.noContent().build();
        }
        return org.springframework.http.ResponseEntity.ok(response);
    }

    @GetMapping("/rounds/{id}/reports")
    @PreAuthorize("hasAnyRole('ADMIN','TRUONG_KHOA','TRUONG_BO_MON','LECTURER','SUPER_ADMIN')")
    public List<ReportResponse> listReportsByRound(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt actor) {
        return reports.listByRound(id, actor);
    }

    @GetMapping("/rounds/{id}/repository")
    @PreAuthorize("hasAnyRole('ADMIN','TRUONG_KHOA','TRUONG_BO_MON','LECTURER','SUPER_ADMIN')")
    public List<RepositoryReportResponse> listRepository(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt actor) {
        return reports.listRepository(id, actor);
    }

    @PostMapping("/groups/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN','TRUONG_KHOA','LECTURER','SUPER_ADMIN')")
    public GroupResponse approveGroup(@PathVariable UUID id,
            @RequestBody(required = false) GroupApprovalRequest request,
            @AuthenticationPrincipal Jwt actor) {
        return mutations.approveGroup(id, request, actor);
    }

    @PostMapping("/groups/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMIN','TRUONG_KHOA','LECTURER','SUPER_ADMIN')")
    public GroupResponse rejectGroup(
            @PathVariable UUID id,
            @RequestBody GroupRejectionRequest request,
            @AuthenticationPrincipal Jwt actor) {
        return mutations.rejectGroup(id, request, actor);
    }

    /**
     * Withdraws a mistaken approval while the round still reviews
     * registrations; the group returns to PENDING with its roster and topic
     * pick intact. Governance roles act only inside their faculty/department
     * scope — no supervisor escape — and downstream work (report, scores,
     * council, reviewer, result) blocks the withdrawal.
     */
    @PostMapping("/groups/{id}/withdraw-approval")
    @PreAuthorize("hasAnyRole('ADMIN','TRUONG_KHOA','TRUONG_BO_MON','LECTURER','SUPER_ADMIN')")
    public GroupResponse withdrawGroupApproval(
            @PathVariable UUID id,
            @RequestBody GroupApprovalWithdrawalRequest request,
            @AuthenticationPrincipal Jwt actor) {
        return mutations.withdrawGroupApproval(id, request, actor);
    }
}
