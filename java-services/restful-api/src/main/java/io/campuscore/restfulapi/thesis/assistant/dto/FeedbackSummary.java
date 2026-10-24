package io.campuscore.restfulapi.thesis.assistant.dto;

import java.util.List;

public record FeedbackSummary(Totals totals, List<ReasonCount> byReason, List<RecentItem> recent) { }
