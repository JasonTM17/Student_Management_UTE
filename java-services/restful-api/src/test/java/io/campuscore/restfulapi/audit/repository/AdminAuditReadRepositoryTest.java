package io.campuscore.restfulapi.audit.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * Regression for the admin audit keyword search: the admin console previously
 * only filtered by action/entityType, so locating a specific event meant
 * paging the whole trail. The {@code search} parameter must hit summary,
 * actor label, entity id, action, and entity type, multi-token ANDed, with
 * LIKE metacharacters treated literally.
 */
class AdminAuditReadRepositoryTest {

    private JdbcTemplate jdbc;
    private AdminAuditReadRepository repository;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:audit_read_test_" + System.nanoTime()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        jdbc = new JdbcTemplate(dataSource);
        repository = new AdminAuditReadRepository(new NamedParameterJdbcTemplate(dataSource));

        jdbc.execute("CREATE SCHEMA IF NOT EXISTS \"campuscore_audit\"");
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS \"campuscore_auth\"");
        jdbc.execute("""
            CREATE TABLE "campuscore_audit"."AdminAudit" (
                "id" VARCHAR(120) PRIMARY KEY,
                "actorId" VARCHAR(120),
                "actorLabel" VARCHAR(240),
                "action" VARCHAR(48) NOT NULL,
                "entityType" VARCHAR(48) NOT NULL,
                "entityId" VARCHAR(120),
                "summary" VARCHAR(500),
                "beforeState" TEXT,
                "afterState" TEXT,
                "createdAt" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
            )
        """);
        jdbc.execute("""
            CREATE TABLE "campuscore_auth"."User" (
                "id" VARCHAR(120) PRIMARY KEY,
                "email" VARCHAR(320) NOT NULL,
                "firstName" VARCHAR(120),
                "lastName" VARCHAR(120)
            )
        """);
    }

    private void insert(String id, String actorId, String actorLabel, String action,
            String entityType, String entityId, String summary) {
        jdbc.update(
                "INSERT INTO \"campuscore_audit\".\"AdminAudit\""
                        + " (\"id\",\"actorId\",\"actorLabel\",\"action\",\"entityType\",\"entityId\",\"summary\")"
                        + " VALUES (?,?,?,?,?,?,?)",
                id, actorId, actorLabel, action, entityType, entityId, summary);
    }

    @Test
    void searchMatchesSummaryActorAndEntityAcrossFields() {
        insert("a1", "admin-user", "Phòng Đào tạo (admin@campuscore.edu)",
                "PASSWORD_RESET", "USER", "u-1", "Reset password for student@campuscore.edu");
        insert("a2", "admin-user", "Phòng Đào tạo (admin@campuscore.edu)",
                "ROLE_CHANGE", "USER", "u-2", "Promoted lecturer@campuscore.edu");
        insert("a3", "audit-user", "Hệ thống",
                "SECTION_UPDATE", "SECTION", "s-9", "Updated section SE401-01");

        // Summary hit, case-insensitive.
        assertThat(repository.page(1, 20, null, null, null, null, "reset password").data())
                .extracting(AdminAuditReadRepository.AuditEntry::id)
                .containsExactly("a1");
        // Actor label hit.
        assertThat(repository.page(1, 20, null, null, null, null, "Hệ thống").data())
                .extracting(AdminAuditReadRepository.AuditEntry::id)
                .containsExactly("a3");
        // entityId hit.
        assertThat(repository.page(1, 20, null, null, null, null, "u-2").data())
                .extracting(AdminAuditReadRepository.AuditEntry::id)
                .containsExactly("a2");
        // Action / entityType hit.
        assertThat(repository.page(1, 20, null, null, null, null, "section").data())
                .extracting(AdminAuditReadRepository.AuditEntry::id)
                .containsExactly("a3");
    }

    @Test
    void searchIsMultiTokenAndedAndLiteralOnMetacharacters() {
        insert("a1", "admin-user", "Phòng Đào tạo",
                "PASSWORD_RESET", "USER", "u-1", "Reset password for student@campuscore.edu");
        insert("a2", "admin-user", "Phòng Đào tạo",
                "PASSWORD_RESET", "USER", "u-2", "Reset password for lecturer@campuscore.edu");

        // Both tokens must hit the same row's field set.
        assertThat(repository.page(1, 20, null, null, null, null, "reset student").data())
                .extracting(AdminAuditReadRepository.AuditEntry::id)
                .containsExactly("a1");
        // A '%' in the query is literal, not a wildcard.
        assertThat(repository.page(1, 20, null, null, null, null, "%").data()).isEmpty();
        // Blank search behaves like no filter.
        assertThat(repository.page(1, 20, null, null, null, null, "  ").total()).isEqualTo(2);
        assertThat(repository.page(1, 20, null, null, null, null, null).total()).isEqualTo(2);
    }

    @Test
    void searchCombinesWithStructuredFilters() {
        insert("a1", "admin-user", "Phòng Đào tạo",
                "PASSWORD_RESET", "USER", "u-1", "Reset password for student@campuscore.edu");
        insert("a2", "admin-user", "Phòng Đào tạo",
                "ROLE_CHANGE", "USER", "u-1", "Changed role for student@campuscore.edu");

        assertThat(repository.page(1, 20, "PASSWORD_RESET", null, null, null, "student").data())
                .extracting(AdminAuditReadRepository.AuditEntry::id)
                .containsExactly("a1");
        assertThat(repository.page(1, 20, null, "SECTION", null, null, "student").total())
                .isZero();
    }

    @Test
    void searchFoldsVietnameseAcrossFieldsAndKeepsCountAndPaginationAligned() {
        insert("a1", "admin-user", "Nguyễn Tiến Sơn", "SECTION_UPDATE", "SECTION", "s-1", "Đã cập nhật lớp");
        insert("a2", "admin-user", "Nguyễn Tiến Sơn", "SECTION_UPDATE", "SECTION", "s-2", "Đã cập nhật lớp");
        insert("other", "other-user", "Nguyễn Văn Bình", "SECTION_UPDATE", "SECTION", "s-3", "Đã cập nhật lớp");

        var first = repository.page(1, 1, "UPDATE", "SECTION", null, null, "NGUYEN son cap nhat");
        var second = repository.page(2, 1, "UPDATE", "SECTION", null, null, "NGUYEN son cap nhat");
        assertThat(first.total()).isEqualTo(2);
        assertThat(first.totalPages()).isEqualTo(2);
        assertThat(first.data()).hasSize(1);
        assertThat(second.data()).hasSize(1);
        assertThat(List.of(first.data().getFirst().id(), second.data().getFirst().id()))
                .containsExactlyInAnyOrder("a1", "a2");
    }

    @Test
    void searchMatchesTheDisplayedFallbackForLegacyActors() {
        jdbc.update("INSERT INTO campuscore_auth.\"User\" (\"id\",\"email\",\"firstName\",\"lastName\") VALUES (?,?,?,?)",
                "legacy-user", "legacy@example.invalid", "Tiến Sơn", "Nguyễn");
        insert("missing", "legacy-user", null, "UPDATED", "USER", "u-1", "Profile updated");
        insert("blank", "legacy-user", "   ", "UPDATED", "USER", "u-2", "Profile updated");
        insert("snapshot", "legacy-user", "Trần Văn Bình", "UPDATED", "USER", "u-3", "Profile updated");
        insert("removed", "deleted-actor-42", null, "UPDATED", "USER", "u-4", "Profile updated");

        var result = repository.page(1, 20, null, null, null, null, "nguyen son legacy@example.invalid");
        assertThat(result.total()).isEqualTo(2);
        assertThat(result.data()).extracting(AdminAuditReadRepository.AuditEntry::id)
                .containsExactlyInAnyOrder("missing", "blank");
        assertThat(result.data()).extracting(AdminAuditReadRepository.AuditEntry::actorLabel)
                .containsOnly("Nguyễn Tiến Sơn (legacy@example.invalid)");
        assertThat(repository.page(1, 20, null, null, null, null, "deleted-actor-42").data())
                .extracting(AdminAuditReadRepository.AuditEntry::id).containsExactly("removed");
        assertThat(repository.page(1, 20, null, null, null, null, "tran binh").data())
                .extracting(AdminAuditReadRepository.AuditEntry::id).containsExactly("snapshot");
        assertThat(jdbc.queryForObject("SELECT \"actorLabel\" FROM campuscore_audit.\"AdminAudit\" WHERE \"id\"='snapshot'", String.class))
                .isEqualTo("Trần Văn Bình");
    }

    @Test
    void foldedSearchKeepsPercentUnderscoreAndBackslashLiteral() {
        insert("literal", "admin-user", "Đào tạo", "UPDATED", "USER", "u-1", "Mã 50%_\\Sơn");
        insert("wildcard", "admin-user", "Đào tạo", "UPDATED", "USER", "u-2", "Mã 500xxSon");
        assertThat(repository.page(1, 20, null, null, null, null, "dao 50%_\\son").data())
                .extracting(AdminAuditReadRepository.AuditEntry::id).containsExactly("literal");
    }

    @Test
    void genericAuditSearchRequiresEveryLiteralTokenWithoutNameSearchTruncation() {
        insert("cn", "admin-user", "Admin", "UPDATED", "COURSE", "c-1", "Updated CN course");
        insert("se", "admin-user", "Admin", "UPDATED", "COURSE", "c-2", "Updated SE course");
        assertThat(repository.page(1, 20, null, null, null, null, "CN updated").data())
                .extracting(AdminAuditReadRepository.AuditEntry::id).containsExactly("cn");
        String prefix = "x".repeat(64);
        insert("long", "admin-user", "Admin", "UPDATED", "COURSE", "c-3", prefix + "a");
        assertThat(repository.page(1, 20, null, null, null, null, prefix + "b").data()).isEmpty();
        insert("many", "admin-user", "Admin", "UPDATED", "COURSE", "c-4", "one two three four five six seven eight nine");
        assertThat(repository.page(1, 20, null, null, null, null, "one two three four five six seven eight missing").data()).isEmpty();
    }
}
