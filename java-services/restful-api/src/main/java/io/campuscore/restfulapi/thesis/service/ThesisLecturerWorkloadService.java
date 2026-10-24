package io.campuscore.restfulapi.thesis.service;

import io.campuscore.restfulapi.thesis.dto.CouncilAssignment;
import io.campuscore.restfulapi.thesis.dto.GradingTask;
import io.campuscore.restfulapi.thesis.dto.LecturerWorkload;
import io.campuscore.restfulapi.thesis.dto.SupervisedTopic;
import io.campuscore.restfulapi.thesis.repository.ThesisLecturerWorkloadRepository;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only lecturer workload aggregation used by the campus assistant:
 * every topic the caller supervises (with registered-group counts) and every
 * defense council the caller sits on (with assigned-topic counts), across all
 * rounds in one query pair. The assistant must not scan rounds and fan out
 * per-topic supervisor lookups client-side, so this is the server-side join.
 */
@Service
@Profile("persistence")
public class ThesisLecturerWorkloadService {

    private final ThesisLecturerWorkloadRepository repository;

    public ThesisLecturerWorkloadService(ThesisLecturerWorkloadRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public LecturerWorkload workload(String lecturerId) {
        List<SupervisedTopic> topics = repository.findSupervisedTopics(lecturerId);
        List<CouncilAssignment> councils = repository.findCouncilAssignments(lecturerId);
        // Grading tasks: council-assigned topics with whether this lecturer has
        // already entered scores, so the assistant can answer "what is still
        // ungraded?" without any client-side fan-out.
        List<GradingTask> gradingTasks = repository.findGradingTasks(lecturerId);
        return new LecturerWorkload(List.copyOf(topics), List.copyOf(councils), List.copyOf(gradingTasks));
    }
}
