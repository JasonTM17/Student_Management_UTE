package io.campuscore.restfulapi.audit;

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
}
