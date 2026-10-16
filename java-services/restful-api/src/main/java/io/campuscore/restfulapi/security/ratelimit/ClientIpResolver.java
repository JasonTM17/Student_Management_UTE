package io.campuscore.restfulapi.security.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * Shared client-IP resolution for security-sensitive code paths (rate limits,
 * login/session audit, password reset). One resolver so every consumer applies
 * the same proxy-trust rules.
 *
 * Client-controlled forwarded headers are honored only behind a trusted
 * reverse proxy, and even then the RIGHTMOST X-Forwarded-For element is
 * used: proxies append the address they saw, so the rightmost entry is the
 * one the trusted proxy added and the leftmost entries are whatever the
 * client spoofed. Render appends the real client IP last. X-Real-IP is a
 * single overwrite-style header, so it is trusted only when the deployment
 * additionally opts in via {@code app.rate-limit.trust-real-ip}. When
 * proxy-header trust is disabled (the default for direct deployments and
 * local dev), the caller must pass the connection address itself — never
 * {@code request.getRemoteAddr()}, because a container/proxy
 * forwarded-headers strategy may already have replaced it with a
 * client-supplied X-Forwarded-For value.
 */
@Component
public class ClientIpResolver {

    private final RateLimitProperties properties;

    public ClientIpResolver(RateLimitProperties properties) {
        this.properties = properties;
    }

    public String resolve(HttpServletRequest request) {
        return resolve(request, request.getRemoteAddr());
    }

    /**
     * @param socketAddr the address of the direct peer. Callers that cannot
     *                   prove the servlet container is free of forwarded-header
     *                   processing should pass the raw socket address they
     *                   trust (e.g. a pre-filter capture); the default is the
     *                   request's remote address.
     */
    public String resolve(HttpServletRequest request, String socketAddr) {
        if (properties.isTrustProxyHeaders()) {
            // Explicit opt-in: the operator vouches that X-Real-IP is
            // overwritten by the trusted proxy, so it is authoritative.
            if (properties.isTrustRealIp()) {
                String xRealIp = request.getHeader("X-Real-IP");
                if (xRealIp != null && !xRealIp.isBlank()) {
                    return xRealIp.trim();
                }
            }

            String xForwardedFor = request.getHeader("X-Forwarded-For");
            if (xForwardedFor != null && !xForwardedFor.isBlank()) {
                String[] parts = xForwardedFor.split(",");
                for (int index = parts.length - 1; index >= 0; index--) {
                    String candidate = parts[index].trim();
                    if (!candidate.isEmpty()) {
                        return candidate;
                    }
                }
            }
        }

        return (socketAddr != null && !socketAddr.isBlank()) ? socketAddr : "unknown";
    }
}
