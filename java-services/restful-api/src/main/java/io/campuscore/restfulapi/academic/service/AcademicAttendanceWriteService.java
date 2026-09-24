package io.campuscore.restfulapi.academic.service;

import io.campuscore.restfulapi.academic.repository.AcademicAttendanceWriteRepository;
import io.campuscore.restfulapi.academic.repository.AcademicAttendanceWriteRepository.AttendanceInsertRow;
import io.campuscore.restfulapi.academic.web.AcademicAttendanceMutationDtos.AttendanceMutationResponse;
import io.campuscore.restfulapi.academic.web.AcademicAttendanceMutationDtos.SectionAttendanceUpdateRequest;
import io.campuscore.restfulapi.academic.web.AcademicAttendanceMutationDtos.StudentAttendanceEntry;
import io.campuscore.restfulapi.web.DomainException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service handling attendance recording and mutations. */
@Service
@Profile("persistence")
public class AcademicAttendanceWriteService {

    private static final Set<String> ALLOWED_STATUSES = Set.of("PRESENT", "ABSENT", "LATE", "EXCUSED");

    private final AcademicAttendanceWriteRepository repository;

    public AcademicAttendanceWriteService(AcademicAttendanceWriteRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public AttendanceMutationResponse recordSectionAttendance(
            String sectionId,
            String lecturerId,
            List<String> roles,
            SectionAttendanceUpdateRequest request) {

        if (sectionId == null || sectionId.isBlank()) {
            throw new DomainException(HttpStatus.BAD_REQUEST, "INVALID_SECTION_ID", "sectionId is required");
        }
        String normalizedSectionId = sectionId.trim();
        if (!repository.sectionExists(normalizedSectionId)) {
            throw new DomainException(HttpStatus.NOT_FOUND, "SECTION_NOT_FOUND", "Section not found: " + sectionId);
        }

        boolean isAdmin = roles != null && (roles.contains("ADMIN") || roles.contains("SUPER_ADMIN"));
        if (!isAdmin) {
            if (lecturerId == null || lecturerId.isBlank() || !repository.isSectionOwnedByLecturer(normalizedSectionId, lecturerId)) {
                throw new DomainException(HttpStatus.FORBIDDEN, "SECTION_FORBIDDEN", "Section is not assigned to the current lecturer");
            }
        }

        Instant normalizedDate = parseAndNormalizeDate(request.date());
        Instant maxAllowedDate = Instant.now().plus(Duration.ofDays(1));
        if (normalizedDate.isAfter(maxAllowedDate)) {
            throw new DomainException(HttpStatus.BAD_REQUEST, "INVALID_ATTENDANCE_DATE", "Attendance date cannot be in the future");
        }

        if (request.records() == null || request.records().isEmpty()) {
            return new AttendanceMutationResponse(normalizedSectionId, request.date(), 0, "No attendance records provided");
        }

        Set<String> enrolledStudentIds = repository.findActiveEnrolledStudentIds(normalizedSectionId);
        List<String> targetStudentIds = new ArrayList<>();
        List<AttendanceInsertRow> insertRows = new ArrayList<>();
        Instant now = Instant.now();

        for (StudentAttendanceEntry entry : request.records()) {
            String studentId = entry.studentId();
            if (studentId == null || studentId.isBlank()) {
                throw new DomainException(HttpStatus.BAD_REQUEST, "INVALID_STUDENT_ID", "studentId must not be blank");
            }
            String normalizedStudentId = studentId.trim();
            if (!enrolledStudentIds.contains(normalizedStudentId)) {
                throw new DomainException(HttpStatus.BAD_REQUEST, "STUDENT_NOT_ENROLLED",
                        "Student " + normalizedStudentId + " is not enrolled in section " + normalizedSectionId);
            }

            String upperStatus = entry.status() != null ? entry.status().trim().toUpperCase() : "";
            if (!ALLOWED_STATUSES.contains(upperStatus)) {
                throw new DomainException(HttpStatus.BAD_REQUEST, "INVALID_ATTENDANCE_STATUS",
                        "Invalid attendance status: " + entry.status() + ". Allowed: " + ALLOWED_STATUSES);
            }

            targetStudentIds.add(normalizedStudentId);
            insertRows.add(new AttendanceInsertRow(
                    UUID.randomUUID().toString(),
                    normalizedStudentId,
                    normalizedSectionId,
                    normalizedDate,
                    upperStatus,
                    entry.notes(),
                    now,
                    now));
        }

        // Delete existing records for (sectionId, date, studentIds) to ensure idempotent replacement
        repository.deleteAttendanceRecords(normalizedSectionId, normalizedDate, targetStudentIds);

        // Batch insert the attendance rows
        repository.insertAttendanceBatch(insertRows);

        return new AttendanceMutationResponse(
                normalizedSectionId,
                request.date(),
                insertRows.size(),
                "Attendance recorded successfully for " + insertRows.size() + " students");
    }

    private static Instant parseAndNormalizeDate(String value) {
        if (value == null || value.isBlank()) {
            throw new DomainException(HttpStatus.BAD_REQUEST, "INVALID_ATTENDANCE_DATE", "date is required");
        }
        String trimmed = value.trim();
        try {
            return Instant.parse(trimmed);
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDate.parse(trimmed).atStartOfDay().toInstant(ZoneOffset.UTC);
            } catch (DateTimeParseException dateOnlyFailure) {
                throw new DomainException(HttpStatus.BAD_REQUEST, "INVALID_ATTENDANCE_DATE",
                        "date must be an ISO-8601 instant or YYYY-MM-DD");
            }
        }
    }
}
