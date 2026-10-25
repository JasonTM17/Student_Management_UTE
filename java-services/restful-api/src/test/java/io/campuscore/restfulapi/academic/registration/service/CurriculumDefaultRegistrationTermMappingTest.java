package io.campuscore.restfulapi.academic.registration.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CurriculumDefaultRegistrationTermMappingTest {
    @Test
    void mapsKnownAcademicSemesterNamesAndNumbersToCurriculumTerms() {
        assertThat(CurriculumDefaultRegistrationService.curriculumSemester("FIRST")).isEqualTo(1);
        assertThat(CurriculumDefaultRegistrationService.curriculumSemester("FALL")).isEqualTo(1);
        assertThat(CurriculumDefaultRegistrationService.curriculumSemester("SEMESTER_1")).isEqualTo(1);
        assertThat(CurriculumDefaultRegistrationService.curriculumSemester("1")).isEqualTo(1);

        assertThat(CurriculumDefaultRegistrationService.curriculumSemester("SECOND")).isEqualTo(2);
        assertThat(CurriculumDefaultRegistrationService.curriculumSemester("SPRING")).isEqualTo(2);
        assertThat(CurriculumDefaultRegistrationService.curriculumSemester("SEMESTER_2")).isEqualTo(2);
        assertThat(CurriculumDefaultRegistrationService.curriculumSemester("2")).isEqualTo(2);

        assertThat(CurriculumDefaultRegistrationService.curriculumSemester("SUMMER")).isEqualTo(3);
        assertThat(CurriculumDefaultRegistrationService.curriculumSemester("THIRD")).isEqualTo(3);
        assertThat(CurriculumDefaultRegistrationService.curriculumSemester("SEMESTER_3")).isEqualTo(3);
        assertThat(CurriculumDefaultRegistrationService.curriculumSemester("3")).isEqualTo(3);
    }

    @Test
    void unknownAndMissingSemesterTypesDoNotGuessATerm() {
        assertThat(CurriculumDefaultRegistrationService.curriculumSemester("TRIMESTER")).isNull();
        assertThat(CurriculumDefaultRegistrationService.curriculumSemester(null)).isNull();
    }
}
