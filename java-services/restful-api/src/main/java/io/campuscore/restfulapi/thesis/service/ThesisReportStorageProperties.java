package io.campuscore.restfulapi.thesis.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Runtime-only configuration for thesis report object storage. */
@ConfigurationProperties(prefix = "thesis.report.storage")
public class ThesisReportStorageProperties {

    private String provider = "local";
    private String bucket = "thesis-reports";
    private String localRoot = System.getProperty("java.io.tmpdir") + "/campuscore/thesis-reports";
    private String supabaseUrl = "";
    private String supabaseServiceRoleKey = "";
    private int timeoutMs = 10_000;

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public String getLocalRoot() {
        return localRoot;
    }

    public void setLocalRoot(String localRoot) {
        this.localRoot = localRoot;
    }

    public String getSupabaseUrl() {
        return supabaseUrl;
    }

    public void setSupabaseUrl(String supabaseUrl) {
        this.supabaseUrl = supabaseUrl;
    }

    public String getSupabaseServiceRoleKey() {
        return supabaseServiceRoleKey;
    }

    public void setSupabaseServiceRoleKey(String supabaseServiceRoleKey) {
        this.supabaseServiceRoleKey = supabaseServiceRoleKey;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }
}
