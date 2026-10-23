package io.campuscore.restfulapi.academic.service;

import io.campuscore.restfulapi.academic.repository.AcademicAttendanceWriteRepository;
import io.campuscore.restfulapi.academic.repository.AcademicAttendanceWriteRepository.AttendanceInsertRow;
import io.campuscore.restfulapi.academic.web.AcademicAttendanceMutationDtos.AttendanceMutationResponse;
import io.campuscore.restfulapi.academic.web.AcademicAttendanceMutationDtos.SectionAttendanceUpdateRequest;
import io.campuscore.restfulapi.academic.web.AcademicAttendanceMutationDtos.StudentAttendanceEntry;
import io.campuscore.restfulapi.web.DomainException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
        String sectionStatus = repository.sectionStatus(normalizedSectionId);
        if (sectionStatus == null) {
            throw new DomainException(HttpStatus.NOT_FOUND, "SECTION_NOT_FOUND", "Section not found: " + sectionId);
        }
        // A cancelled section held no sessions; recording attendance for it
        // fabricates participation data. The 409 matches the grading guard.
        if ("CANCELLED".equals(sectionStatus)) {
            throw new DomainException(HttpStatus.CONFLICT, "SECTION_CANCELLED", "Section is cancelled");
        }

        boolean isAdmin = roles != null && (roles.contains("ADMIN") || roles.contains("SUPER_ADMIN"));
        if (!isAdmin) {
            if (lecturerId == null || lecturerId.isBlank() || !repository.isSectionOwnedByLecturer(normalizedSectionId, lecturerId)) {
                throw new DomainException(HttpStatus.FORBIDDEN, "SECTION_FORBIDDEN", "Section is not assigned to the current lecturer");
            }
        }

        Instant normalizedDate = parseAndNormalizeDate(request.date());
        Instant maxAllowedDate = AttendanceDay.parse(Instant.now().toString());
        if (normalizedDate.isAfter(maxAllowedDate)) {
            throw new DomainException(HttpStatus.BAD_REQUEST, "INVALID_ATTENDANCE_DATE", "Attendance date cannot be in the future");
        }

        if (request.records() == null || request.records().isEmpty()) {
            return new AttendanceMutationResponse(normalizedSectionId, request.date(), 0, "No attendance records provided");
        }

        Set<String> enrolledStudentIds = repository.findActiveEnrolledStudentIds(normalizedSectionId);
        // Dedupe by student: a payload that names the same student twice used
        // to insert two rows on the (studentId, sectionId, date) unique key
        // and surface as a 500. Last entry wins, like a JSON object.
        Set<String> targetStudentIds = new java.util.LinkedHashSet<>();
        Map<String, AttendanceInsertRow> insertRows = new java.util.LinkedHashMap<>();
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
            // "RESET" clears a previously saved record for (student, section,
            // date) instead of writing a new one — round-2 sweep finding
            // write-1: the UI's Reset button saved rows the backend silently
            // kept, so a lecturer could never un-mark an attendance entry.
            if ("RESET".equals(upperStatus)) {
                targetStudentIds.add(normalizedStudentId);
                insertRows.remove(normalizedStudentId);
                continue;
            }
            if (!ALLOWED_STATUSES.contains(upperStatus)) {
                throw new DomainException(HttpStatus.BAD_REQUEST, "INVALID_ATTENDANCE_STATUS",
                        "Invalid attendance status: " + entry.status() + ". Allowed: " + ALLOWED_STATUSES + ", RESET");
            }

            targetStudentIds.add(normalizedStudentId);
            insertRows.put(normalizedStudentId, new AttendanceInsertRow(
                    UUID.randomUUID().toString(),
                    normalizedStudentId,
                    normalizedSectionId,
                    normalizedDate,
                    upperStatus,
                    entry.notes(),
                    now,
                    now));
        }

        // Delete existing records for (sectionId, date, studentIds) to ensure idempotent replacement;
        // RESET entries are deleted only (they contribute no insert row).
        repository.deleteAttendanceRecords(normalizedSectionId, normalizedDate, new ArrayList<>(targetStudentIds));

        // Batch insert the attendance rows
        repository.insertAttendanceBatch(new ArrayList<>(insertRows.values()));

        return new AttendanceMutationResponse(
                normalizedSectionId,
                request.date(),
                insertRows.size(),
                insertRows.isEmpty()
                        ? "Attendance records cleared for " + targetStudentIds.size() + " students"
                        : "Attendance recorded successfully for " + insertRows.size() + " students");
    }

    /**
     * Attendance is recorded per local (Asia/Ho_Chi_Minh) calendar day, keyed
     * as that day's UTC midnight. An ISO instant is first resolved to the
     * local date it falls on — truncating the instant at the UTC boundary
     * keyed evening classes (17:00Z–23:59Z = the next local day) to the wrong
     * calendar date and split same-day records across two keys.
     */
    private static Instant parseAndNormalizeDate(String value) {
        if (value == null || value.isBlank()) {
            throw new DomainException(HttpStatus.BAD_REQUEST, "INVALID_ATTENDANCE_DATE", "date is required");
        }
        String trimmed = value.trim();
        try {
            return AttendanceDay.parse(trimmed);
        } catch (DateTimeParseException invalidDate) {
            throw new DomainException(HttpStatus.BAD_REQUEST, "INVALID_ATTENDANCE_DATE",
                    "date must be an ISO-8601 instant or YYYY-MM-DD");
        }
    }
}
