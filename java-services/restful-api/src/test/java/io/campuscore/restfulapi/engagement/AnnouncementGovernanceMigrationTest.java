package io.campuscore.restfulapi.engagement;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Static guard for the additive announcement governance migration contract. */
class AnnouncementGovernanceMigrationTest {

    @Test
    void v15AddsVersionArchiveAndAppendOnlyAuditShape() throws Exception {
        Path script = Path.of("src/main/resources/db/migration/V15__announcement_governance_audit.sql");
        String sql = Files.readString(script);

        assertThat(script).exists();
        assertThat(sql)
                .contains("ADD COLUMN IF NOT EXISTS \"version\" INTEGER NOT NULL DEFAULT 0")
                .contains("ADD COLUMN IF NOT EXISTS \"archivedAt\" TIMESTAMPTZ")
                .contains("ADD COLUMN IF NOT EXISTS \"archivedBy\" VARCHAR(120)")
                .contains("CREATE TABLE IF NOT EXISTS engagement.\"AnnouncementAudit\"")
                .contains("\"actorLabel\" VARCHAR(240)")
                .contains("\"beforeState\" TEXT")
                .contains("\"afterState\" TEXT")
                .contains("CHECK (\"action\" IN ('CREATED', 'UPDATED', 'ARCHIVED', 'RESTORED'))")
                .contains("CHECK (length(trim(\"reason\")) BETWEEN 1 AND 500)")
                .contains("ON DELETE RESTRICT")
                .contains("engagement_announcement_audit_lookup_idx");
    }
}
