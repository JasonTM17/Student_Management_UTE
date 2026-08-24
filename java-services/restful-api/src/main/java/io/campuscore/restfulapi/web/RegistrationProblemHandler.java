package io.campuscore.restfulapi.web;

import io.campuscore.restfulapi.registration.RegistrationProblemException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** RFC 7807-compatible registration errors while preserving the legacy ApiError contract elsewhere. */
@RestControllerAdvice
@Order(-10)
public class RegistrationProblemHandler {
    @ExceptionHandler(RegistrationProblemException.class)
    ResponseEntity<Problem> registration(RegistrationProblemException exception, HttpServletRequest request) {
        Object requestId = request.getAttribute(io.campuscore.restfulapi.security.RequestIdFilter.ATTRIBUTE);
        Problem body = new Problem(URI.create("https://campuscore.edu/problems/registration"), "Registration rejected",
                exception.status().value(), exception.getMessage(), exception.code(), requestId == null ? null : requestId.toString(),
                exception.retryable(), exception.violations());
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(exception.status()).contentType(MediaType.valueOf("application/problem+json"));
        if (exception.retryable()) builder.header("Retry-After", "1");
        return builder.body(body);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    ResponseEntity<Problem> missingHeader(MissingRequestHeaderException exception, HttpServletRequest request) {
        String code = "Idempotency-Key".equalsIgnoreCase(exception.getHeaderName())
                ? "IDEMPOTENCY_KEY_REQUIRED" : "REQUIRED_HEADER_MISSING";
        Problem body = new Problem(URI.create("https://campuscore.edu/problems/registration"),
                "Registration rejected", HttpStatus.BAD_REQUEST.value(),
                "Idempotency-Key is required for enrollment mutations", code, requestId(request), false, List.of());
        return ResponseEntity.badRequest().contentType(MediaType.valueOf("application/problem+json")).body(body);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<?> invalidHeaderType(MethodArgumentTypeMismatchException exception, HttpServletRequest request) {
        if (!"key".equalsIgnoreCase(exception.getName())) {
            return ResponseEntity.badRequest()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ApiExceptionHandler.ApiError(
                            Instant.now(),
                            HttpStatus.BAD_REQUEST.value(),
                            "INVALID_REQUEST",
                            "Request could not be parsed",
                            request.getRequestURI(),
                            requestId(request),
                            List.of(),
                            Map.of()));
        }
        Problem body = new Problem(URI.create("https://campuscore.edu/problems/registration"),
                "Registration rejected", HttpStatus.BAD_REQUEST.value(),
                "Idempotency-Key must be a UUID", "IDEMPOTENCY_KEY_INVALID", requestId(request), false, List.of());
        return ResponseEntity.badRequest().contentType(MediaType.valueOf("application/problem+json")).body(body);
    }

    private static String requestId(HttpServletRequest request) {
        Object value = request.getAttribute(io.campuscore.restfulapi.security.RequestIdFilter.ATTRIBUTE);
        return value == null ? null : value.toString();
    }

    public record Problem(URI type, String title, int status, String detail, String code, String requestId,
            boolean retryable, List<RegistrationProblemException.Violation> violations) { }
}
