package io.campuscore.restfulapi.thesis.service;

import io.campuscore.restfulapi.thesis.domain.ApprovalStatus;
import io.campuscore.restfulapi.thesis.domain.GroupStatus;
import io.campuscore.restfulapi.thesis.repository.ThesisGroupReadRepository;
import io.campuscore.restfulapi.thesis.web.ThesisGroupReadDtos.GroupResponse;
import io.campuscore.restfulapi.thesis.web.ThesisGroupReadDtos.GroupWithdrawalInfo;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Read-side mirror of {@code ThesisMutationService.withdrawGroupApproval}'s
 * gate: the per-group {@code approvalWithdrawal} read model answers "may the
 * requesting actor withdraw this approval right now" without a second round
 * trip, the same way {@code RoundDeletionInfo.deletable} answers the round
 * delete guard. State eligibility and blocker counts come from one batched
 * query; actor authority reuses the repository's own scope predicates:
 *
 * <ul>
 *   <li>ADMIN / SUPER_ADMIN — global.</li>
 *   <li>TRUONG_KHOA — only groups inside the live faculty scope.</li>
 *   <li>TRUONG_BO_MON — only groups inside the live department scope.</li>
 *   <li>LECTURER — only groups whose topic they currently supervise.</li>
 * </ul>
 *
 * <p>Governance roles deliberately get no supervisor escape here, matching
 * the write path: a dean who supervises a foreign-faculty topic can review
 * the group but must not undo its approval.
 */
@Service
@Profile("persistence")
public class ThesisGroupWithdrawalPolicy {

    /** Round statuses where an approval may still change — mirrors approve. */
    private static final Set<String> REVIEWABLE_ROUND_STATUSES = Set.of(
            "PROPOSALS_PUBLISHED", "REGISTRATION_OPEN", "REGISTRATION_CLOSED");

    private final NamedParameterJdbcTemplate jdbc;
    private final ThesisGroupReadRepository groups;

    public ThesisGroupWithdrawalPolicy(
            NamedParameterJdbcTemplate jdbc,
            ThesisGroupReadRepository groups) {
        this.jdbc = jdbc;
        this.groups = groups;
    }

    /** Attaches the actor-scoped withdrawal info to one group response. */
    public GroupResponse attach(GroupResponse group, org.springframework.security.oauth2.jwt.Jwt actor) {
        if (group == null) {
            return null;
        }
        return attach(group, roles(actor),
                actor == null ? null : actor.getClaimAsString("lecturerId"),
                actor == null ? null : actor.getSubject());
    }

    /** Attaches the actor-scoped withdrawal info to each group in the list. */
    public List<GroupResponse> attachAll(
            List<GroupResponse> list,
            List<String> roles,
            String lecturerId,
            String subject) {
        if (list == null || list.isEmpty()) {
            return list;
        }
        Map<UUID, WithdrawalFacts> facts = factsById(
                list.stream().map(GroupResponse::id).toList());
        ThesisGroupReadRepository.LecturerScope scope = lecturerScope(roles, lecturerId, subject);
        return list.stream()
                .map(group -> group.withApprovalWithdrawal(
                        info(group, facts.get(group.id()), roles, lecturerId, subject, scope)))
                .toList();
    }

    public GroupResponse attach(
            GroupResponse group,
            List<String> roles,
            String lecturerId,
            String subject) {
        return attachAll(List.of(group), roles, lecturerId, subject).getFirst();
    }

    private GroupWithdrawalInfo info(
            GroupResponse group,
            WithdrawalFacts facts,
            List<String> roles,
            String lecturerId,
            String subject,
            ThesisGroupReadRepository.LecturerScope scope) {
        long reports = facts == null ? 0 : facts.reports();
        long scores = facts == null ? 0 : facts.scores();
        long councilTopics = facts == null ? 0 : facts.councilTopics();
        long reviewers = facts == null ? 0 : facts.reviewers();
        long results = facts == null ? 0 : facts.results();
        boolean stateEligible = group != null && facts != null
                && group.approvalStatus() == ApprovalStatus.APPROVED
                && group.status() == GroupStatus.SUBMITTED
                && group.topicId() != null
                && REVIEWABLE_ROUND_STATUSES.contains(facts.roundStatus());
        boolean empty = reports + scores + councilTopics + reviewers + results == 0;
        return new GroupWithdrawalInfo(
                stateEligible && empty && mayWithdraw(group, roles, lecturerId, subject, scope),
                reports, scores, councilTopics, reviewers, results);
    }

    /**
     * The same precedence {@code authorizeApprovalWithdrawal} applies on the
     * write path: global admin, then faculty scope, then department scope —
     * governance roles never fall through to the supervisor branch.
     */
    private boolean mayWithdraw(
            GroupResponse group,
            List<String> roles,
            String lecturerId,
            String subject,
            ThesisGroupReadRepository.LecturerScope scope) {
        if (roles.contains("ADMIN") || roles.contains("SUPER_ADMIN")) {
            return true;
        }
        if (roles.contains("TRUONG_KHOA")) {
            return scope != null && groups.groupInFaculty(group.id(), scope.facultyId());
        }
        if (roles.contains("TRUONG_BO_MON")) {
            return scope != null && groups.groupInDepartment(group.id(), scope.departmentId());
        }
        if (roles.contains("LECTURER")) {
            // Mirrors the write path's lecturerId.isBlank() ? actorId : lecturerId fallback.
            String supervisorKey = lecturerId == null || lecturerId.isBlank() ? subject : lecturerId;
            return group.topicId() != null
                    && supervisorKey != null && !supervisorKey.isBlank()
                    && groups.isTopicSupervisedBy(group.topicId(), supervisorKey);
        }
        return false;
    }

    private ThesisGroupReadRepository.LecturerScope lecturerScope(
            List<String> roles, String lecturerId, String subject) {
        if ((!roles.contains("TRUONG_KHOA") && !roles.contains("TRUONG_BO_MON"))
                || lecturerId == null || lecturerId.isBlank()
                || subject == null || subject.isBlank()) {
            return null;
        }
        return groups.findLecturerScope(lecturerId, subject);
    }

    private Map<UUID, WithdrawalFacts> factsById(Collection<UUID> groupIds) {
        if (groupIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, WithdrawalFacts> byId = new LinkedHashMap<>();
        jdbc.query(
                "SELECT g.id, r.status AS round_status, "
                        + "(SELECT COUNT(*) FROM thesis.thesis_group_report rep "
                        + "  WHERE rep.group_id = g.id) AS reports, "
                        + "(SELECT COUNT(*) FROM thesis.thesis_topic_score s "
                        + "  WHERE s.topic_id = g.topic_id) AS scores, "
                        + "(SELECT COUNT(*) FROM thesis.thesis_council_topic ct "
                        + "  WHERE ct.topic_id = g.topic_id) AS council_topics, "
                        + "CASE WHEN t.gvpb_lecturer_id IS NOT NULL THEN 1 ELSE 0 END AS reviewers, "
                        + "CASE WHEN t.result_status IS NOT NULL OR t.final_score IS NOT NULL "
                        + "  THEN 1 ELSE 0 END AS results "
                        + "FROM thesis.thesis_group g "
                        + "JOIN thesis.thesis_registration_round r ON r.id = g.round_id "
                        + "LEFT JOIN thesis.thesis_topic t ON t.id = g.topic_id "
                        + "WHERE g.id IN (:ids)",
                new MapSqlParameterSource("ids", groupIds),
                (org.springframework.jdbc.core.RowCallbackHandler) rs -> byId.put(
                        rs.getObject("id", UUID.class), new WithdrawalFacts(
                        rs.getString("round_status"),
                        rs.getLong("reports"),
                        rs.getLong("scores"),
                        rs.getLong("council_topics"),
                        rs.getLong("reviewers"),
                        rs.getLong("results"))));
        return byId;
    }

    private static List<String> roles(org.springframework.security.oauth2.jwt.Jwt actor) {
        List<String> roles = actor == null ? null : actor.getClaimAsStringList("roles");
        return roles == null ? List.of() : roles;
    }

    private record WithdrawalFacts(
            String roundStatus,
            long reports,
            long scores,
            long councilTopics,
            long reviewers,
            long results) { }
}
