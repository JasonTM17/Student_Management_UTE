package io.campuscore.restfulapi.engagement.model;

public record AnnouncementFilter(String semesterId, String sectionId, String priority, String status, String search) {
}
