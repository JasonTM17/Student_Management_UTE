package io.campuscore.restfulapi.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.campuscore.restfulapi.academic.registration.CreditLimitApplicationDtos.Response;
import io.campuscore.restfulapi.academic.registration.CreditLimitApplicationService;
import io.campuscore.restfulapi.audit.AdminAuditRecorder;
import io.campuscore.restfulapi.web.DomainException;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

/**
 * K15: approving or rejecting a per-student credit-limit exception is a
 * privilege-affecting decision and must leave an AdminAudit row carrying the
 * deciding admin, the request/round it belongs to, and the state transition.
 *
 * <p>The decision is exercised at the service layer with a mocked recorder so
 * the assertion is about the audit contract, not about JDBC plumbing; the H2
 * review contract itself is already pinned by
 * {@code CreditLimitApplicationServiceTest}.
 */
class CreditLimitReviewAuditTest {

    private NamedParameterJdbcTemplate jdbc;
    private AdminAuditRecorder audit;
    private CreditLimitApplicationService service;

    @BeforeEach
    void setUp() {
        jdbc = mock(NamedParameterJdbcTemplate.class);
        audit = mock(AdminAuditRecorder.class);
        service = new CreditLimitApplicationService(jdbc, 0, audit);
        when(jdbc.queryForObject(anyString(), any(SqlParameterSource.class), eq(Long.class))).thenReturn(1L);
        when(jdbc.update(anyString(), any(SqlParameterSource.class))).thenReturn(1);
    }

    @Test
    void approvingWritesCreditLimitApprovedAuditWithActorAndTransition() {
        when(jdbc.queryForMap(anyString(), any(SqlParameterSource.class)))
                .thenReturn(applicationRow("PENDING"), applicationRow("APPROVED"));

        Response response = service.review("app-1", "approved", "admin-1", null);

        assertThat(response.status()).isEqualTo("APPROVED");
        ArgumentCaptor<Object> before = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Object> after = ArgumentCaptor.forClass(Object.class);
        verify(audit).record(eq("admin-1"), isNull(), eq("CREDIT_LIMIT_APPROVED"),
                eq("CreditLimitApplication"), eq("app-1"), anyString(), before.capture(), after.capture());
        assertThat(String.valueOf(before.getValue()))
                .contains("PENDING").contains("30").contains("round-1");
        assertThat(String.valueOf(after.getValue()))
                .contains("APPROVED").contains("30").contains("round-1");
    }

    @Test
    void rejectingWritesCreditLimitRejectedAuditWithActorAndTransition() {
        when(jdbc.queryForMap(anyString(), any(SqlParameterSource.class)))
                .thenReturn(applicationRow("PENDING"), applicationRow("REJECTED"));

        Response response = service.review("app-2", "REJECTED", "admin-2", "Không đủ điều kiện.");

        assertThat(response.status()).isEqualTo("REJECTED");
        ArgumentCaptor<Object> before = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Object> after = ArgumentCaptor.forClass(Object.class);
        verify(audit).record(eq("admin-2"), isNull(), eq("CREDIT_LIMIT_REJECTED"),
                eq("CreditLimitApplication"), eq("app-2"), anyString(), before.capture(), after.capture());
        assertThat(String.valueOf(before.getValue())).contains("PENDING");
        assertThat(String.valueOf(after.getValue())).contains("REJECTED");
        // The reviewer note and the applicant reason are not copied into the trail.
        assertThat(String.valueOf(after.getValue())).doesNotContain("Không đủ điều kiện.");
    }

    @Test
    void reviewingANonPendingApplicationWritesNoAuditRow() {
        when(jdbc.queryForMap(anyString(), any(SqlParameterSource.class)))
                .thenReturn(applicationRow("APPROVED"));

        assertThatThrownBy(() -> service.review("app-3", "REJECTED", "admin-1", null))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Only a pending credit-limit application");

        verifyNoInteractions(audit);
    }

    private static Map<String, Object> applicationRow(String status) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", "app-1");
        row.put("studentId", "student-1");
        row.put("student_code", "CL001");
        row.put("student_name", "Credit Limit");
        row.put("student_email", "credit@example.test");
        row.put("semesterId", "semester-1");
        row.put("semester_name", "Credit limit semester");
        row.put("roundId", "round-1");
        row.put("round_name", "Credit limit round");
        row.put("round_id", "round-1");
        row.put("standard_limit", 28);
        row.put("requestedLimit", 30);
        row.put("requested_limit", 30);
        row.put("reason", "Cần vượt hạn mức cho môn thay thế học phần bị hoãn.");
        row.put("status", status);
        row.put("reviewedBy", "PENDING".equals(status) ? null : "admin-1");
        row.put("reviewedAt", null);
        row.put("reviewerNote", null);
        row.put("createdAt", null);
        row.put("updatedAt", null);
        return row;
    }
}
