package io.campuscore.restfulapi.thesis.dto;

import java.util.List;

public record LecturerWorkload(
        List<SupervisedTopic> topics,
        List<CouncilAssignment> councils,
        List<GradingTask> gradingTasks) { }
