package io.campuscore.restfulapi.academic.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;

class EnrollmentJpaBoundaryTest {
    @Test
    void activeFactoryAndDropMutationPreserveDeterministicState() {
        Instant now = Instant.parse("2026-08-24T00:00:00Z");
        EnrollmentEntity entity = EnrollmentEntity.active("enroll-1", "student-1", "section-1", "semester-1", now);
        assertThat(entity.getStatus()).isEqualTo("ACTIVE");
        assertThat(entity.getGradeStatus()).isEqualTo("PENDING");
        entity.markDropped(now.plusSeconds(30));
        assertThat(entity.getStatus()).isEqualTo("DROPPED");
        assertThat(entity.getDroppedAt()).isEqualTo(now.plusSeconds(30));
        assertThat(entity.getUpdatedAt()).isEqualTo(now.plusSeconds(30));

        EnrollmentEntity legacy = EnrollmentEntity.enrolled("enroll-2", "student-1", "section-1", "semester-1", now);
        assertThat(legacy.getStatus()).isEqualTo("ENROLLED");
        assertThat(legacy.getGradeStatus()).isEqualTo("NOT_GRADED");
    }

    @Test
    void factoryRejectsMissingIdentityAndRepositoryDeclaresLock() throws Exception {
        Instant now = Instant.parse("2026-08-24T00:00:00Z");
        assertThatThrownBy(() -> EnrollmentEntity.active("", "student-1", "section-1", "semester-1", now))
                .isInstanceOf(IllegalArgumentException.class);
        Lock lock = EnrollmentRepository.class
                .getMethod("findLockedStudentEnrollments", String.class, String.class, java.util.Collection.class)
                .getAnnotation(Lock.class);
        assertThat(lock.value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
        assertThat(EnrollmentRepository.class.getMethod("findLockedById", String.class)
                .getAnnotation(Lock.class).value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
        assertThat(List.of(CourseRepository.class, SectionScheduleRepository.class)).hasSize(2);
    }

    @Test
    void sectionCapacityMutationIsBoundedAndDeterministic() {
        AcademicSectionEntity section = AcademicSectionEntity.snapshot("section-1", 1, 0, "OPEN");
        section.incrementEnrollment();
        assertThat(section.getEnrolledCount()).isEqualTo(1);
        assertThatThrownBy(section::incrementEnrollment)
                .isInstanceOf(IllegalStateException.class).hasMessage("SECTION_FULL");
        section.decrementEnrollment();
        assertThat(section.getEnrolledCount()).isZero();
        assertThatThrownBy(section::decrementEnrollment)
                .isInstanceOf(IllegalStateException.class).hasMessage("SECTION_COUNT_UNDERFLOW");
    }

    @Test
    void operationAuditAndSlipFactoriesKeepTerminalStateExplicit() {
        Instant now = Instant.parse("2026-08-24T00:00:00Z");
        String hash = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
        EnrollmentOperationEntity operation = EnrollmentOperationEntity.processing(
                "op-1", "student-1", "key-1", hash, "ENROLL", now);
        operation.complete(201, "{\"ok\":true}", now.plusSeconds(1));
        assertThat(operation.getState()).isEqualTo("COMPLETED");
        assertThat(operation.getResponseStatus()).isEqualTo(201);
        assertThat(operation.getCompletedAt()).isEqualTo(now.plusSeconds(1));

        EnrollmentAuditEntity audit = EnrollmentAuditEntity.record(
                "audit-1", "op-1", "student-1", "section-1", "ENROLL", null, now);
        assertThat(audit.getAction()).isEqualTo("ENROLL");
        RegistrationSlipEntity slip = RegistrationSlipEntity.snapshot(
                "slip-1", "student-1", "round-1", hash, now);
        assertThat(slip.getContentHash()).isEqualTo(hash);
        assertThatThrownBy(() -> EnrollmentOperationEntity.processing(
                "op-2", "student-1", "key-2", "bad", "ENROLL", now))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
