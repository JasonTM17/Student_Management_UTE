package io.campuscore.restfulapi.engagement.domain;

public record AnnouncementFilter(String semesterId, String sectionId, String priority, String status, String search) {
}
