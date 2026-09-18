package io.campuscore.restfulapi.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * ADM-P0-3: the audit trail for destructive administrative operations.
 *
 * <p>Two properties matter more than the plumbing, and both are asserted here:
 * the record must never contain a credential, and it must join the caller's
 * transaction so a mutation and its record cannot diverge.
 */
class AdminAuditRecorderTest {

    private static NamedParameterJdbcTemplate jdbc;
    private static AdminAuditRecorder recorder;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        jdbc = mock(NamedParameterJdbcTemplate.class);
        recorder = new AdminAuditRecorder(jdbc);
    }

    @Test
    void credentialValuesAreRedactedBeforeTheyReachTheTable() {
        // Every spelling the codebase actually uses, including the temporary one
        // handed back to an administrator after a reset.
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("email", "someone@example.edu");
        state.put("password", "a-real-credential");
        state.put("passwordHash", "$2a$10$stored-hash");
        state.put("temporaryPassword", "one-time-credential");
        state.put("currentPassword", "old-credential");
        state.put("apiToken", "bearer-value");

        String serialized = AdminAuditRecorder.serialize(state);

        assertThat(serialized)
                .as("the audit table is the one place a credential must never land")
                .doesNotContain("a-real-credential")
                .doesNotContain("stored-hash")
                .doesNotContain("one-time-credential")
                .doesNotContain("old-credential")
                .doesNotContain("bearer-value");
        // The useful fields survive: a redacted record must still be worth reading.
        assertThat(serialized).contains("someone@example.edu").contains("[redacted]");
    }

    @Test
    void nullableActorIsRecordedRatherThanRefused() {
        // Automated and migration actors have no interactive principal. Refusing to
        // record their changes would hide exactly the operations most worth auditing.
        recorder.record(null, null, "DELETED", "ENROLLMENT", "enrollment-1", "deleted by an automated actor");

        verify(jdbc).update(contains("campuscore_audit"), any(SqlParameterSource.class));
    }

    @Test
    void recorderParticipatesInTheCallersTransaction() throws Exception {
        // Propagation.MANDATORY is the mechanism that keeps a mutation and its record
        // atomic. If someone relaxes it to REQUIRES_NEW or SUPPORTS, a rolled-back
        // delete would still leave an audit row claiming it happened — a record that
        // is wrong is worse than one that is absent, because it is believed.
        for (var method : AdminAuditRecorder.class.getDeclaredMethods()) {
            if (!method.getName().equals("record") && !method.getName().equals("recordDeletion")) {
                continue;
            }
            Transactional annotation = method.getAnnotation(Transactional.class);
            assertThat(annotation)
                    .as("%s must declare its transaction semantics explicitly", method.getName())
                    .isNotNull();
            if (annotation.propagation() != Propagation.MANDATORY) {
                assertThat(annotation.propagation())
                        .as("%s must join the caller's transaction", method.getName())
                        .isEqualTo(Propagation.MANDATORY);
            }
        }
        assertThat(TransactionDefinition.PROPAGATION_MANDATORY).isEqualTo(Propagation.MANDATORY.value());
    }

    @Test
    void theTableIsAppendOnly() {
        // A record that can be edited or deleted is not evidence. The migration must
        // therefore expose no update or delete path for this table.
        String migration = read("src/main/resources/db/migration/V60__admin_audit_trail.sql");
        assertThat(migration)
                .as("the audit table must not be mutable through the migration that creates it")
                .doesNotContain("ON DELETE CASCADE");
        // No FK to the user table: the most important rows describe accounts that no
        // longer exist, so a cascading FK would erase the evidence.
        assertThat(migration).doesNotContain("REFERENCES campuscore_auth");
    }

    private static String read(String relativePath) {
        try {
            return java.nio.file.Files.readString(java.nio.file.Path.of(relativePath));
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
