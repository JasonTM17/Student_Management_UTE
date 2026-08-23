package io.campuscore.restfulapi.web;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.dao.DataIntegrityViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ApiError> unauthenticated(
            AuthenticationException exception,
            HttpServletRequest request) {
        return error(
                HttpStatus.UNAUTHORIZED,
                "UNAUTHENTICATED",
                "Authentication is required",
                request,
                Map.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> accessDenied(
            AccessDeniedException exception,
            HttpServletRequest request) {
        return error(
                HttpStatus.FORBIDDEN,
                "ACCESS_DENIED",
                "Access denied",
                request,
                Map.of());
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ApiError> responseStatus(
            ResponseStatusException exception,
            HttpServletRequest request) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        return error(
                status,
                "HTTP_" + status.value(),
                exception.getReason() == null ? status.getReasonPhrase() : exception.getReason(),
                request,
                Map.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> invalid(
            MethodArgumentNotValidException exception,
            HttpServletRequest request) {
        Map<String, String> fields = exception.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        field -> field.getField(),
                        field -> field.getDefaultMessage() == null ? "Invalid value" : field.getDefaultMessage(),
                        (first, ignored) -> first,
                        LinkedHashMap::new));
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed", request, fields);
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class})
    ResponseEntity<ApiError> malformedRequest(Exception exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Request could not be parsed", request, Map.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiError> notFound(NoResourceFoundException exception, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found", request, Map.of());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiError> invalidArgument(
            IllegalArgumentException exception,
            HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(DomainException.class)
    ResponseEntity<ApiError> domain(DomainException exception, HttpServletRequest request) {
        return error(exception.status(), exception.code(), exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> conflict(
            DataIntegrityViolationException exception,
            HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "CONFLICT", "Resource already exists or violates a relationship", request, Map.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception exception, HttpServletRequest request) {
        Object requestId = request.getAttribute(
                io.campuscore.restfulapi.security.RequestIdFilter.ATTRIBUTE);
        LOGGER.error(
                "Unhandled API exception requestId={} method={} path={}",
                requestId,
                request.getMethod(),
                request.getRequestURI(),
                exception);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Unexpected server error", request, Map.of());
    }

    private ResponseEntity<ApiError> error(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request,
            Map<String, String> fields) {
        Object requestId = request.getAttribute(
                io.campuscore.restfulapi.security.RequestIdFilter.ATTRIBUTE);
        return ResponseEntity.status(status).body(new ApiError(
                Instant.now(),
                status.value(),
                code,
                message == null || message.isBlank() ? status.getReasonPhrase() : message,
                request.getRequestURI(),
                requestId == null ? null : requestId.toString(),
                fields.entrySet().stream()
                        .map(entry -> new FieldError(entry.getKey(), entry.getValue()))
                        .toList(),
                fields));
    }

    public record ApiError(
            Instant timestamp,
            int status,
            String code,
            String message,
            String path,
            String requestId,
            List<FieldError> fieldErrors,
            Map<String, String> fields) {
    }

    public record FieldError(String field, String message) {
    }
}
