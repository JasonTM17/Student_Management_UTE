package io.campuscore.restfulapi.academic.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AcademicMutationServiceGradeTest {

    @Test
    void calculatesFiftyFiftyFinalGradeWithTwoDecimalRounding() {
        assertEquals(new BigDecimal("7.00"), AcademicMutationService.calculateFinalGrade(
                new BigDecimal("8.0"), new BigDecimal("6.0")));
        assertEquals(new BigDecimal("8.56"), AcademicMutationService.calculateFinalGrade(
                new BigDecimal("8.555"), new BigDecimal("8.565")));
    }

    @Test
    void mapsEveryPublishedLetterGradeBoundary() {
        Map<String, String> expected = Map.ofEntries(
                Map.entry("9.0", "A+"), Map.entry("8.5", "A"), Map.entry("8.0", "B+"),
                Map.entry("7.0", "B"), Map.entry("6.5", "C+"), Map.entry("5.5", "C"),
                Map.entry("5.0", "D+"), Map.entry("4.0", "D"), Map.entry("3.99", "F"));

        expected.forEach((score, letter) ->
                assertEquals(letter, AcademicMutationService.letterGrade(new BigDecimal(score))));
    }
}
