package io.campuscore.restfulapi.security.ratelimit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Encapsulates configurable rate limit properties.
 */
@Component
public class RateLimitProperties {

    private final boolean enabled;
    private final boolean trustProxyHeaders;
    private final boolean trustRealIp;
    private final int loginLimit;
    private final int defaultLimit;
    private final int defaultWindowSeconds;

    public RateLimitProperties(
            @Value("${app.rate-limit.enabled:true}") boolean enabled,
            @Value("${app.rate-limit.trust-proxy-headers:false}") boolean trustProxyHeaders,
            @Value("${app.rate-limit.trust-real-ip:false}") boolean trustRealIp,
            @Value("${app.rate-limit.login-limit:15}") int loginLimit,
            @Value("${app.rate-limit.default-limit:40}") int defaultLimit,
            @Value("${app.rate-limit.default-window-seconds:60}") int defaultWindowSeconds) {
        this.enabled = enabled;
        this.trustProxyHeaders = trustProxyHeaders;
        this.trustRealIp = trustRealIp;
        this.loginLimit = loginLimit;
        this.defaultLimit = defaultLimit;
        this.defaultWindowSeconds = defaultWindowSeconds;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * True only when the deployment sits behind a reverse proxy trusted to set
     * X-Real-IP / X-Forwarded-For. When false, client keys fall back to the
     * socket address so spoofed forwarded headers cannot evade IP limits.
     */
    public boolean isTrustProxyHeaders() {
        return trustProxyHeaders;
    }

    /**
     * True only when the trusted proxy is also trusted to overwrite (not
     * append) X-Real-IP. X-Forwarded-For appends client-supplied values, so a
     * spoofed X-Real-IP is ignored unless this flag is explicitly enabled.
     */
    public boolean isTrustRealIp() {
        return trustRealIp;
    }

    public int getLoginLimit() {
        return loginLimit;
    }

    public int getDefaultLimit() {
        return defaultLimit;
    }

    public int getDefaultWindowSeconds() {
        return defaultWindowSeconds;
    }
}
