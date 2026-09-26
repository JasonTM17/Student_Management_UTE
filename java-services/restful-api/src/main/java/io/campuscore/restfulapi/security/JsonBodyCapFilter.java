package io.campuscore.restfulapi.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import io.campuscore.restfulapi.web.ApiErrorWriter;

/**
 * Caps the declared size of JSON bodies before Jackson materialises them.
 *
 * <p>Multipart limits exist at the servlet level, but a JSON request is read
 * in full before message-length validation runs, so an unbounded body is an
 * unbounded memory allocation. Three caps apply, tightest first (audit S5):
 *
 * <ul>
 *   <li>{@code /api/v1/auth/login|refresh|change-password|logout} — 4 KB:
 *       credential bodies are a couple of fields; anything larger is already a
 *       probe, and these are the routes an anonymous attacker can hit.</li>
 *   <li>{@code /api/v1/assistant/*} and {@code /api/v1/thesis/assistant/*} —
 *       64 KB: comfortably exceeds the largest legitimate assistant message +
 *       metadata; anything approaching this cap is already invalid by the
 *       message-length rule and only exists to exhaust memory.</li>
 *   <li>every other route — 1 MB: a generous global ceiling. This includes
 *       {@code PUT /api/v1/auth/profile}, whose avatar contract legitimately
 *       carries up to a 200 KB base64 data URL and relies on bean validation
 *       to answer 400 for anything larger.</li>
 * </ul>
 */
@Component
public class JsonBodyCapFilter extends OncePerRequestFilter {

    /** Global ceiling for JSON bodies on any route (audit S5). */
    static final long MAX_JSON_BYTES_GLOBAL = 1024 * 1024L;

    /** Tighter ceiling for assistant chat routes. */
    static final long MAX_JSON_BYTES_ASSISTANT = 64 * 1024L;

    /** Tightest ceiling for the unauthenticated credential routes. */
    static final long MAX_JSON_BYTES_AUTH = 4 * 1024L;

    private static final List<String> TIGHT_AUTH_ROUTES = List.of(
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/auth/change-password",
            "/api/v1/auth/logout");

    private final ApiErrorWriter errorWriter;

    public JsonBodyCapFilter(ApiErrorWriter errorWriter) {
        this.errorWriter = errorWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String contentType = request.getContentType();
        boolean json = contentType != null && contentType.toLowerCase().contains("application/json");
        long length = request.getContentLengthLong();
        if (json && length > capFor(request.getRequestURI())) {
            errorWriter.write(request, response, org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE,
                    "REQUEST_BODY_TOO_LARGE", "Request body exceeds the accepted size");
            return;
        }
        chain.doFilter(request, response);
    }

    /** Tightest applicable cap wins: credential routes, then assistant, then the global ceiling. */
    static long capFor(String uri) {
        if (uri != null && TIGHT_AUTH_ROUTES.contains(uri)) {
            return MAX_JSON_BYTES_AUTH;
        }
        if (uri != null && (uri.startsWith("/api/v1/assistant/") || uri.startsWith("/api/v1/thesis/assistant/"))) {
            return MAX_JSON_BYTES_ASSISTANT;
        }
        return MAX_JSON_BYTES_GLOBAL;
    }
}
