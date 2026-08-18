package io.campuscore.academic.service;

import io.campuscore.academic.domain.Faculty;
import io.campuscore.academic.repository.FacultyRepository;
import io.campuscore.academic.web.AcademicDtos;
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
class FacultyServiceTest {

    @Autowired
    private FacultyRepository repository;

    @Autowired
    private FacultyService service;

    @Test
    void create_persistsFaculty() {
        AcademicDtos.CreateFacultyRequest request = new AcademicDtos.CreateFacultyRequest(
                "Engineering", "Engineering", "Kỹ thuật", "ENG", "Engineering faculty", null, null,
                "Dr. Smith", "1234567890", "eng@test.com", "Building A");

        AcademicDtos.FacultyResponse response = service.create(request);
        assertThat(response.id()).isNotNull();
        assertThat(response.code()).isEqualTo("ENG");
    }

    @Test
    void findAll_returnsPagedFaculties() {
        repository.save(new Faculty("Science", "SCI"));

        Page<AcademicDtos.FacultyResponse> result = service.findAll(1, 10);
        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    void findOne_returnsFaculty() {
        Faculty faculty = repository.save(new Faculty("Arts", "ARTS"));

        AcademicDtos.FacultyResponse result = service.findOne(faculty.getId());
        assertThat(result.code()).isEqualTo("ARTS");
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        FacultyService facultyService(FacultyRepository repository) {
            return new FacultyService(repository);
        }
    }
}
