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
}
