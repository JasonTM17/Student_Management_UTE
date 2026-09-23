package io.campuscore.restfulapi.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Global Exception Handler for CampusUTE REST API.
 * Intercepts all controller exceptions and converts them into standardized ErrorResponse payloads.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ErrorResponse> handleAppException(AppException exception, HttpServletRequest request) {
        Map<String, String> fields = exception instanceof ValidationException ve ? ve.getFieldErrors() : Map.of();
        return buildErrorResponse(
                exception.getStatus(),
                exception.getCode(),
                exception.getMessage(),
                request,
                fields);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(
            AuthenticationException exception,
            HttpServletRequest request) {
        return buildErrorResponse(
                HttpStatus.UNAUTHORIZED,
                ErrorCode.UNAUTHENTICATED.name(),
                "Authentication is required",
                request,
                Map.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(
            AccessDeniedException exception,
            HttpServletRequest request) {
        return buildErrorResponse(
                HttpStatus.FORBIDDEN,
                ErrorCode.ACCESS_DENIED.name(),
                "Access denied",
                request,
                Map.of());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(
            ResponseStatusException exception,
            HttpServletRequest request) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        return buildErrorResponse(
                status,
                "HTTP_" + status.value(),
                exception.getReason() == null ? status.getReasonPhrase() : exception.getReason(),
                request,
                Map.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException exception,
            HttpServletRequest request) {
        Map<String, String> fields = exception.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        field -> field.getField(),
                        field -> field.getDefaultMessage() == null ? "Invalid value" : field.getDefaultMessage(),
                        (first, ignored) -> first,
                        LinkedHashMap::new));
        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                ErrorCode.VALIDATION_ERROR.name(),
                "Request validation failed",
                request,
                fields);
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class})
    public ResponseEntity<ErrorResponse> handleMalformedRequest(Exception exception, HttpServletRequest request) {
        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                ErrorCode.INVALID_REQUEST.name(),
                "Request could not be parsed",
                request,
                Map.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(NoResourceFoundException exception, HttpServletRequest request) {
        return buildErrorResponse(
                HttpStatus.NOT_FOUND,
                "NOT_FOUND",
                "Resource not found",
                request,
                Map.of());
    }

    /**
     * A wrong HTTP method on an existing route is a client mistake, not an
     * internal failure: answer 405 so callers can correct the request instead
     * of chasing a phantom server error.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException exception, HttpServletRequest request) {
        return buildErrorResponse(
                HttpStatus.METHOD_NOT_ALLOWED,
                "METHOD_NOT_ALLOWED",
                "Request method is not supported for this resource",
                request,
                Map.of());
    }

    /**
     * WS-Security round-11: a JSON body endpoint hit with a non-JSON content
     * type used to fall through to the 500 catch-all. A wrong media type is a
     * client mistake: answer 415 with the standard envelope, never a 5xx.
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException exception, HttpServletRequest request) {
        return buildErrorResponse(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "UNSUPPORTED_MEDIA_TYPE",
                "Request content type is not supported",
                request,
                Map.of());
    }

    /**
     * WS-Admin round-11: an Accept header the endpoint cannot honour (for
     * example JSON on the HTML mail preview) used to fall through to the 500
     * catch-all via HttpMediaTypeNotAcceptableException. An unacceptable
     * Accept is a client mistake: answer 406 with the standard envelope.
     */
    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ErrorResponse> handleMediaTypeNotAcceptable(
            org.springframework.web.HttpMediaTypeNotAcceptableException exception, HttpServletRequest request) {
        return buildErrorResponse(
                HttpStatus.NOT_ACCEPTABLE,
                "NOT_ACCEPTABLE",
                "No acceptable representation for the requested Accept header",
                request,
                Map.of());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException exception,
            HttpServletRequest request) {
        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                ErrorCode.INVALID_REQUEST.name(),
                exception.getMessage(),
                request,
                Map.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException exception,
            HttpServletRequest request) {
        LOGGER.warn("Database constraint conflict at {}: {}", request.getRequestURI(), exception.getMessage());
        return buildErrorResponse(
                HttpStatus.CONFLICT,
                ErrorCode.CONFLICT.name(),
                "Operation conflicted with existing database state",
                request,
                Map.of());
    }

    /** Wukong finding: an oversize multipart used to fall through to the 500
     *  catch-all; the report-upload contract promises a 413 envelope. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSize(MaxUploadSizeExceededException exception, HttpServletRequest request) {
        return buildErrorResponse(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "FILE_TOO_LARGE",
                "The uploaded document must be 20 MB or smaller",
                request,
                Map.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnhandledException(Exception exception, HttpServletRequest request) {
        Object requestId = request.getAttribute(io.campuscore.restfulapi.security.RequestIdFilter.ATTRIBUTE);
        LOGGER.error("Unhandled internal server exception at {} (requestId: {})",
                request.getRequestURI(), requestId, exception);
        return buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "An unexpected internal error occurred",
                request,
                Map.of());
    }

    private static ResponseEntity<ErrorResponse> buildErrorResponse(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request,
            Map<String, String> fields) {
        Object requestId = request.getAttribute(io.campuscore.restfulapi.security.RequestIdFilter.ATTRIBUTE);
        ErrorResponse body = new ErrorResponse(
                Instant.now(),
                status.value(),
                code,
                message,
                request.getRequestURI(),
                requestId == null ? null : requestId.toString(),
                fields);
        return ResponseEntity.status(status).body(body);
    }
}
