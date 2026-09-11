package io.campuscore.restfulapi.exception;

import org.springframework.http.HttpStatus;

/**
 * Standard institutional error codes with canonical HTTP status and messages.
 */
public enum ErrorCode {

    UNCATEGORIZED_EXCEPTION(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected internal server error occurred"),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Authentication is required"),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "Access denied"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "Action is forbidden"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "The requested resource was not found"),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "User profile not found"),
    STUDENT_NOT_FOUND(HttpStatus.NOT_FOUND, "Student record not found"),
    LECTURER_NOT_FOUND(HttpStatus.NOT_FOUND, "Lecturer profile not found"),
    COURSE_NOT_FOUND(HttpStatus.NOT_FOUND, "Academic course not found"),
    SECTION_NOT_FOUND(HttpStatus.NOT_FOUND, "Class section not found"),
    SEMESTER_NOT_FOUND(HttpStatus.NOT_FOUND, "Academic semester not found"),
    TOPIC_NOT_FOUND(HttpStatus.NOT_FOUND, "Thesis topic not found"),
    GROUP_NOT_FOUND(HttpStatus.NOT_FOUND, "Thesis group not found"),
    REPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "Thesis report document not found"),
    COUNCIL_NOT_FOUND(HttpStatus.NOT_FOUND, "Defense council not found"),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "Request payload or parameters are invalid"),
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Field validation failed"),
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "Bad request"),
    CONFLICT(HttpStatus.CONFLICT, "Data conflict with existing resource state"),
    DUPLICATE_RESOURCE(HttpStatus.CONFLICT, "Resource already exists in database"),
    SECTION_FULL(HttpStatus.CONFLICT, "Class section has reached maximum capacity"),
    SCHEDULE_CONFLICT(HttpStatus.CONFLICT, "Class schedule conflicts with already enrolled sections"),
    CREDIT_CAP_EXCEEDED(HttpStatus.BAD_REQUEST, "Exceeded maximum allowed credits for semester"),
    SUPERVISOR_CANNOT_GRADE(HttpStatus.FORBIDDEN, "Supervisors cannot grade their own supervised thesis topic"),
    GROUP_OWNER_REQUIRED(HttpStatus.FORBIDDEN, "Only the thesis group leader can perform this operation"),
    GROUP_MEMBER_REQUIRED(HttpStatus.FORBIDDEN, "Only registered group members can access this resource"),
    REPORT_DEADLINE_PASSED(HttpStatus.CONFLICT, "The report submission deadline has passed"),
    STUDENT_ALREADY_IN_GROUP(HttpStatus.CONFLICT, "Student is already a member of an active thesis group"),
    GROUP_FULL(HttpStatus.CONFLICT, "Thesis group has reached maximum member capacity"),
    GROUP_TOO_SMALL(HttpStatus.CONFLICT, "Thesis group must have at least two members to be approved");

    private final HttpStatus httpStatus;
    private final String defaultMessage;

    ErrorCode(HttpStatus httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
