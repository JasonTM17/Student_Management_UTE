package io.campuscore.restfulapi.academic.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Unit coverage for the CSV cell escaping used by enrollment exports. */
class AcademicMutationServiceCsvTest {

    @Test
    void quotesAndEscapesEmbeddedQuotes() {
        assertThat(AcademicMutationService.csv("say \"hi\"")).isEqualTo("\"say \"\"hi\"\"\"");
    }

    @Test
    void prefixesSpreadsheetFormulaStartingValues() {
        assertThat(AcademicMutationService.csv("=cmd|' /C calc'!A0")).isEqualTo("\"'=cmd|' /C calc'!A0\"");
        assertThat(AcademicMutationService.csv("=SUM(A1:A2)")).isEqualTo("\"'=SUM(A1:A2)\"");
        assertThat(AcademicMutationService.csv("+1+1")).isEqualTo("\"'+1+1\"");
        assertThat(AcademicMutationService.csv("-1")).isEqualTo("\"'-1\"");
        assertThat(AcademicMutationService.csv("@SUM(1)")).isEqualTo("\"'@SUM(1)\"");
        assertThat(AcademicMutationService.csv("\t=cmd")).isEqualTo("\"'\t=cmd\"");
        assertThat(AcademicMutationService.csv("\r=cmd")).isEqualTo("\"'\r=cmd\"");
    }

    @Test
    void leavesPlainValuesUnprefixed() {
        assertThat(AcademicMutationService.csv("SE402")).isEqualTo("\"SE402\"");
        assertThat(AcademicMutationService.csv("student1@campuscore.edu")).isEqualTo("\"student1@campuscore.edu\"");
        assertThat(AcademicMutationService.csv("ENROLLED")).isEqualTo("\"ENROLLED\"");
        assertThat(AcademicMutationService.csv("")).isEqualTo("\"\"");
        assertThat(AcademicMutationService.csv(null)).isEqualTo("\"\"");
    }

    @Test
    void onlyTheLeadingFormulaCharacterIsPrefixed() {
        assertThat(AcademicMutationService.csv("A-B")).isEqualTo("\"A-B\"");
        assertThat(AcademicMutationService.csv("total=sum")).isEqualTo("\"total=sum\"");
        assertThat(AcademicMutationService.csv("user@host")).isEqualTo("\"user@host\"");
    }
}
