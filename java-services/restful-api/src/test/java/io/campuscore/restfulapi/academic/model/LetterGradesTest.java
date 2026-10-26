package io.campuscore.restfulapi.academic.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LetterGradesTest {

    @Test
    void everyLetterOnTheScaleHasARankAndGpaPoints() {
        for (String letter : LetterGrades.SCALE) {
            assertThat(LetterGrades.rank(letter)).as("rank(%s)", letter).isNotNull();
            assertThat(LetterGrades.points(letter)).as("points(%s)", letter).isNotNull();
        }
        assertThat(LetterGrades.rank("W")).isNull();
        assertThat(LetterGrades.rank("DRAFT")).isNull();
        assertThat(LetterGrades.points("W")).isNull();
        assertThat(LetterGrades.points("DRAFT")).isNull();
    }

    @Test
    void minusLettersRankBetweenTheirNeighboringBands() {
        assertThat(LetterGrades.rank("B-"))
                .isLessThan(LetterGrades.rank("B"))
                .isGreaterThan(LetterGrades.rank("C"));
        assertThat(LetterGrades.rank("C-"))
                .isLessThan(LetterGrades.rank("C"))
                .isGreaterThan(LetterGrades.rank("D+"));
        assertThat(LetterGrades.rank("F")).isZero();
    }
}
