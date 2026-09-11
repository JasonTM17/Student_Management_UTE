package io.campuscore.restfulapi.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a requested resource (student, course, topic, section) is not found.
 */
public class ResourceNotFoundException extends AppException {

    public ResourceNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND.name(), message);
    }

    public ResourceNotFoundException(String resourceName, String fieldName, Object fieldValue) {
        super(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND.name(),
                String.format("%s not found with %s: '%s'", resourceName, fieldName, fieldValue));
    }
}
