package io.campuscore.restfulapi.thesis.assistant.model;

public record CatalogDocument(String entityType, String entityId, String title, String text, java.sql.Timestamp updatedAt) { }
