package io.campuscore.people.service;

import io.campuscore.people.domain.Student;
import io.campuscore.people.repository.StudentRepository;
import io.campuscore.people.web.PeopleDtos;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.Page;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@Transactional
class StudentServiceTest {

    @Autowired
    private StudentRepository repository;

    @Autowired
    private StudentService service;

    @Test
    void create_persistsStudent() {
        PeopleDtos.CreateStudentRequest request = new PeopleDtos.CreateStudentRequest(
                UUID.randomUUID(), "student@test.com", "John", "Doe", "STU001",
                UUID.randomUUID(), "CUR001", "Computer Science", UUID.randomUUID(),
                "DEPT001", "IT Department", 2024, "ACTIVE", Instant.now());

        PeopleDtos.StudentResponse response = service.create(request);
        assertThat(response.id()).isNotNull();
        assertThat(response.studentId()).isEqualTo("STU001");
    }

    @Test
    void findAll_returnsPagedStudents() {
        UUID userId = UUID.randomUUID();
        repository.save(new Student(userId, "test@test.com", "Jane", "Doe", "STU002",
                UUID.randomUUID(), "CUR001", "CS", UUID.randomUUID(), "DEPT001", "IT", 2024, "ACTIVE", Instant.now()));

        Page<PeopleDtos.StudentResponse> result = service.findAll(1, 10, null);
        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    void findOne_returnsStudent() {
        Student student = repository.save(new Student(UUID.randomUUID(), "find@test.com", "Find", "Me", "STU003",
                UUID.randomUUID(), "CUR001", "CS", UUID.randomUUID(), "DEPT001", "IT", 2024, "ACTIVE", Instant.now()));

        PeopleDtos.StudentResponse result = service.findOne(student.getId());
        assertThat(result.studentId()).isEqualTo("STU003");
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        StudentService studentService(StudentRepository repository) {
            return new StudentService(repository);
        }
    }
}
