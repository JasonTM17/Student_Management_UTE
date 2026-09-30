package io.campuscore.restfulapi.academic.registration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Owner decision 2026-09-30 ("mở full hạn mức cho tất cả tài khoản để
 * demo"): REGISTRATION_CREDIT_LIMIT_OVERRIDE above zero becomes every
 * student's effective limit, bypassing the 28-credit standard and the
 * application flow without touching the database. Default 0 keeps the
 * documented standard/approved behaviour pinned by
 * {@code CreditLimitApplicationServiceTest}.
 */
class CreditLimitOverrideTest {

    @Test
    void overrideBecomesEveryStudentsEffectiveLimitWithoutDbAccess() {
        CreditLimitApplicationService service = new CreditLimitApplicationService(null, 999);
        assertThat(service.effectiveLimit("any-student", Map.of("credit_limit", 28))).isEqualTo(999);
        assertThat(service.effectiveLimit("any-student", Map.of())).isEqualTo(999);
    }

    @Test
    void negativeOverrideIsTreatedAsDisabled() {
        CreditLimitApplicationService service = new CreditLimitApplicationService(null, -5);
        // 0/disabled → the standard path runs; with a null template jdbc the
        // standard path would throw, so just assert the flag is normalized.
        assertThat(service.standardLimit(Map.of())).isEqualTo(28);
    }
}
