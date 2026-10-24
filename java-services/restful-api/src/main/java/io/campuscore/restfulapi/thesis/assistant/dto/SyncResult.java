package io.campuscore.restfulapi.thesis.assistant.dto;

public record SyncResult(String status, String releaseId, String corpusVersion, String corpusHash, int rowCount,
        boolean degraded, String message) {
    public static SyncResult disabled() { return new SyncResult("DISABLED", null, null, null, 0, true, "Supabase authoring sync is not configured"); }
    public static SyncResult failed(String message) { return new SyncResult("FAILED", null, null, null, 0, true, message == null ? "Knowledge sync failed" : message); }
}
