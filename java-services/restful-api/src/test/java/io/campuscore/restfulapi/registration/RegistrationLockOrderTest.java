package io.campuscore.restfulapi.registration;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.campuscore.restfulapi.academic.persistence.AcademicSectionEntity;
import io.campuscore.restfulapi.academic.persistence.AcademicSectionRepository;
import io.campuscore.restfulapi.academic.persistence.EnrollmentAuditRepository;
import io.campuscore.restfulapi.academic.persistence.EnrollmentOperationEntity;
import io.campuscore.restfulapi.academic.persistence.EnrollmentOperationRepository;
import io.campuscore.restfulapi.academic.persistence.EnrollmentRepository;
import io.campuscore.restfulapi.academic.persistence.RegistrationJpaMutationGateway;
import io.campuscore.restfulapi.academic.persistence.RegistrationRoundEntity;
import io.campuscore.restfulapi.academic.persistence.RegistrationRoundRepository;
import io.campuscore.restfulapi.academic.persistence.RegistrationSlipRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class RegistrationLockOrderTest {
    private static final Instant NOW = Instant.parse("2026-08-25T00:00:00Z");

    @Test
    void enrollmentReservesOperationBeforeRoundSectionAndStudentLocks() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        RegistrationRoundRepository rounds = mock(RegistrationRoundRepository.class);
        AcademicSectionRepository sections = mock(AcademicSectionRepository.class);
        EnrollmentRepository enrollments = mock(EnrollmentRepository.class);
        EnrollmentOperationRepository operations = mock(EnrollmentOperationRepository.class);
        EnrollmentAuditRepository audits = mock(EnrollmentAuditRepository.class);
        RegistrationSlipRepository slips = mock(RegistrationSlipRepository.class);
        RegistrationJpaMutationGateway gateway = mock(RegistrationJpaMutationGateway.class);
        UUID key = UUID.fromString("00000000-0000-4000-8000-000000000001");
        String hash = sha256("ENROLL|student-1|round-1|section-1");
        EnrollmentOperationEntity operation = EnrollmentOperationEntity.processing(
                "operation-1", "student-1", key.toString(), hash, "ENROLL", NOW);
        RegistrationRoundEntity round = RegistrationRoundEntity.create(
                "round-1", "semester-1", "OPEN", NOW.minusSeconds(60), NOW.plusSeconds(60),
                NOW.minusSeconds(60), NOW.plusSeconds(120), 28, "Asia/Ho_Chi_Minh");
        AcademicSectionEntity section = mock(AcademicSectionEntity.class);

        when(gateway.insertOperationIfAbsent(anyString(), eq("student-1"), eq(key.toString()),
                eq(hash), eq("ENROLL"), eq(NOW))).thenReturn(true);
        when(operations.findLockedByStudentIdAndIdempotencyKey("student-1", key.toString()))
                .thenReturn(Optional.of(operation));
        when(rounds.findLockedById("round-1")).thenReturn(Optional.of(round));
        when(sections.findLockedById("section-1")).thenReturn(Optional.of(section));
        when(enrollments.findLockedStudent("student-1")).thenReturn(List.of("student-1"));
        when(enrollments.findLockedStudentEnrollments(eq("student-1"), eq("semester-1"), any()))
                .thenReturn(List.of());

        RegistrationService service = new RegistrationService(jdbc, new ObjectMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC), rounds, sections, enrollments, operations, audits, slips, gateway);

        assertThrows(RegistrationProblemException.class, () -> service.enroll(
                "student-1", new RegistrationDtos.EnrollmentRequest("section-1", "round-1"), key));

        InOrder order = inOrder(gateway, operations, rounds, sections, enrollments);
        order.verify(gateway).insertOperationIfAbsent(anyString(), eq("student-1"), eq(key.toString()),
                eq(hash), eq("ENROLL"), eq(NOW));
        order.verify(operations).findLockedByStudentIdAndIdempotencyKey("student-1", key.toString());
        order.verify(rounds).findLockedById("round-1");
        order.verify(sections).findLockedById("section-1");
        order.verify(enrollments).findLockedStudentEnrollments(eq("student-1"), eq("semester-1"), any());
    }

    private static String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
