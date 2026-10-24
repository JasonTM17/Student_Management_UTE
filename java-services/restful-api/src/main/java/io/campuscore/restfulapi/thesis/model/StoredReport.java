package io.campuscore.restfulapi.thesis.model;

/** The attached document bytes, served only through the authorized download path. */
public record StoredReport(String fileName, String contentType, byte[] data) { }
