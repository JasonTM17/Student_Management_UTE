package io.campuscore.restfulapi.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import io.campuscore.restfulapi.web.ApiErrorWriter;

/**
 * Caps the declared size of JSON bodies before Jackson materialises them.
 *
 * <p>Multipart limits exist at the servlet level, but a JSON request is read
 * in full before message-length validation runs, so an unbounded body is an
 * unbounded memory allocation. The assistant chat routes accept at most a few
 * kilobytes of text; anything approaching this cap is already invalid by the
 * message-length rule and only exists to exhaust memory.
 */
@Component
public class JsonBodyCapFilter extends OncePerRequestFilter {

    /** 64 KB comfortably exceeds the largest legitimate assistant message + metadata. */
    static final long MAX_JSON_BYTES = 64 * 1024L;

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
        if (json && length > MAX_JSON_BYTES) {
            errorWriter.write(request, response, org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE,
                    "REQUEST_BODY_TOO_LARGE", "Request body exceeds the accepted size");
            return;
        }
        chain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Only guard the routes that accept a JSON body and have a tight
        // message contract; everything else keeps servlet-default behaviour.
        String uri = request.getRequestURI();
        return !(uri.startsWith("/api/v1/assistant/") || uri.startsWith("/api/v1/thesis/assistant/"));
    }
}
