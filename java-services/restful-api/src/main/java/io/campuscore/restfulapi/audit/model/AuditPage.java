package io.campuscore.restfulapi.audit.model;

import java.util.List;

public record AuditPage(List<AuditEntry> data, long total, int page, int limit, long totalPages) {
}
