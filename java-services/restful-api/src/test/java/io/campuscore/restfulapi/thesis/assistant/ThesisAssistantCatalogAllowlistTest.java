package io.campuscore.restfulapi.thesis.assistant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** Exercises the fixed public academic catalog projection against H2 schema parity. */
@SpringBootTest
@ActiveProfiles({"test", "persistence"})
class ThesisAssistantCatalogAllowlistTest {
    @Autowired private ThesisAssistantCatalogRepository catalog;

    @Test
    void catalogProjectionReturnsOnlyPublicCoverageFields() {
        var rows = catalog.search("en", List.of("java", "course"), 20);
        for (var row : rows) {
            assertNotNull(row.entityType());
            assertNotNull(row.entityId());
            String text = (row.title() + " " + row.text()).toLowerCase(java.util.Locale.ROOT);
            assertFalse(text.contains("grade"));
            assertFalse(text.contains("attendance"));
            assertFalse(text.contains("enrollment"));
            assertFalse(text.contains("studentid"));
        }
    }
}
