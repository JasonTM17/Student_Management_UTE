package io.campuscore.restfulapi.thesis.service;

import io.campuscore.restfulapi.thesis.repository.ThesisDeanOverviewRepository;
import io.campuscore.restfulapi.thesis.web.ThesisDeanOverviewDtos.DeanGroup;
import io.campuscore.restfulapi.thesis.web.ThesisDeanOverviewDtos.DeanOverviewResponse;
import io.campuscore.restfulapi.thesis.web.ThesisDeanOverviewDtos.DeanSupervisor;
import io.campuscore.restfulapi.thesis.web.ThesisDeanOverviewDtos.DepartmentCount;
import io.campuscore.restfulapi.thesis.web.ThesisDeanOverviewDtos.FacultyScope;
import io.campuscore.restfulapi.thesis.web.ThesisDeanOverviewDtos.OverviewStats;
import io.campuscore.restfulapi.thesis.web.ThesisDeanOverviewDtos.RoundInfo;
import io.campuscore.restfulapi.thesis.web.ThesisDeanOverviewDtos.SupervisorWorkload;
import io.campuscore.restfulapi.web.DomainException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Faculty-head (TRUONG_KHOA) thesis observation. The scope is resolved live
 * from the dean's bound Lecturer profile, so a demoted or deactivated profile
 * immediately shrinks the view — matching the account-repair contract that a
 * dean account always carries an active lecturer profile.
 */
@Service
@Profile("persistence")
public class ThesisDeanOverviewService {

    private final ThesisDeanOverviewRepository repository;

    public ThesisDeanOverviewService(ThesisDeanOverviewRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public DeanOverviewResponse overview(UUID roundId, Jwt actor) {
        FacultyScope scope = resolveScope(actor);
        RoundInfo round = repository.findRound(roundId)
                .orElseThrow(() -> new DomainException(
                        HttpStatus.NOT_FOUND, "ROUND_NOT_FOUND", "Thesis registration round not found"));
        List<DeanGroup> groups = repository.findFacultyGroups(roundId, scope.facultyId());
        List<DepartmentCount> departmentCounts = repository.findDepartmentCounts(roundId, scope.facultyId());
        return new DeanOverviewResponse(
                scope, round, stats(groups, departmentCounts), supervisorWorkloads(groups), groups);
    }

    private FacultyScope resolveScope(Jwt actor) {
        List<String> roles = actor == null ? null : actor.getClaimAsStringList("roles");
        if (roles == null || !roles.contains("TRUONG_KHOA")) {
            throw new DomainException(HttpStatus.FORBIDDEN, "ROLE_FORBIDDEN",
                    "Faculty-head role required");
        }
        String lecturerId = actor.getClaimAsString("lecturerId");
        if (lecturerId == null || lecturerId.isBlank()) {
            throw new DomainException(HttpStatus.FORBIDDEN, "FACULTY_SCOPE_UNAVAILABLE",
                    "An active lecturer profile is required to resolve the faculty scope");
        }
        return repository.findDeanFaculty(actor.getSubject(), lecturerId.trim())
                .orElseThrow(() -> new DomainException(HttpStatus.FORBIDDEN,
                        "FACULTY_SCOPE_UNAVAILABLE",
                        "An active lecturer profile is required to resolve the faculty scope"));
    }

    private static OverviewStats stats(List<DeanGroup> groups, List<DepartmentCount> departments) {
        int pending = 0;
        int approved = 0;
        int rejected = 0;
        int withoutTopic = 0;
        int members = 0;
        Map<String, Integer> groupCountByDepartment = new LinkedHashMap<>();
        Set<String> supervisorIds = new LinkedHashSet<>();
        for (DeanGroup group : groups) {
            // A cancelled group is a historical row, not active work: it stays
            // in overview.groups but never enters the pending/member/department
            // or supervisor counters.
            if (group.status() == io.campuscore.restfulapi.thesis.domain.GroupStatus.CANCELLED) {
                continue;
            }
            switch (group.approvalStatus()) {
                case PENDING -> pending++;
                case APPROVED -> approved++;
                case REJECTED -> rejected++;
                default -> { }
            }
            if (group.topicId() == null) {
                withoutTopic++;
            }
            members += group.members().size();
            for (DeanSupervisor supervisor : group.supervisors()) {
                supervisorIds.add(supervisor.lecturerId());
            }
            String key = group.departmentId() == null ? "" : group.departmentId();
            groupCountByDepartment.merge(key, 1, Integer::sum);
        }
        List<DepartmentCount> byDepartment = departments.stream()
                .map(d -> new DepartmentCount(d.departmentId(), d.departmentName(),
                        groupCountByDepartment.getOrDefault(d.departmentId(), 0), d.topicCount()))
                .toList();
        int topics = departments.stream().mapToInt(DepartmentCount::topicCount).sum();
        return new OverviewStats(groups.size(), pending, approved, rejected, withoutTopic,
                members, topics, supervisorIds.size(), byDepartment);
    }

    private static List<SupervisorWorkload> supervisorWorkloads(List<DeanGroup> groups) {
        Map<String, SupervisorAccumulator> byLecturer = new LinkedHashMap<>();
        for (DeanGroup group : groups) {
            // Cancelled groups keep their supervisors historically, but they
            // hold no active workload.
            if (group.status() == io.campuscore.restfulapi.thesis.domain.GroupStatus.CANCELLED) {
                continue;
            }
            for (DeanSupervisor supervisor : group.supervisors()) {
                SupervisorAccumulator acc = byLecturer.computeIfAbsent(supervisor.lecturerId(),
                        k -> new SupervisorAccumulator(supervisor));
                acc.groupIds.add(group.id());
                if (group.topicId() != null) {
                    acc.topicIds.add(group.topicId());
                }
            }
        }
        return byLecturer.values().stream()
                .map(acc -> new SupervisorWorkload(
                        acc.supervisor.lecturerId(),
                        acc.supervisor.employeeId(),
                        acc.supervisor.displayName(),
                        acc.supervisor.departmentName(),
                        acc.topicIds.size(),
                        acc.groupIds.size()))
                .sorted((a, b) -> {
                    int byGroups = Integer.compare(b.supervisedGroups(), a.supervisedGroups());
                    if (byGroups != 0) {
                        return byGroups;
                    }
                    String an = a.displayName() == null ? "" : a.displayName();
                    String bn = b.displayName() == null ? "" : b.displayName();
                    return an.compareToIgnoreCase(bn);
                })
                .toList();
    }

    private static final class SupervisorAccumulator {
        private final DeanSupervisor supervisor;
        private final Set<UUID> topicIds = new LinkedHashSet<>();
        private final Set<UUID> groupIds = new LinkedHashSet<>();

        private SupervisorAccumulator(DeanSupervisor supervisor) {
            this.supervisor = supervisor;
        }
    }
}
