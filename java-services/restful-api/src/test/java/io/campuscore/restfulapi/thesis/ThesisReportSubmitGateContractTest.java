package io.campuscore.restfulapi.thesis;

import static org.assertj.core.api.Assertions.assertThat;

import io.campuscore.restfulapi.thesis.web.ThesisMutationController;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.multipart.MultipartFile;

/**
 * LEC-P2-5: the two report-SUBMIT gates used to advertise
 * ADMIN/TRUONG_KHOA/LECTURER while ThesisReportService.requireWriteAccess
 * 403s everyone except the student group leader — a lying contract. The
 * submit gates must name only STUDENT; the GET/download matrix keeps staff.
 */
class ThesisReportSubmitGateContractTest {

    @Test
    void reportSubmitGatesAdvertiseOnlyTheStudentRole() throws Exception {
        assertThat(gateOf("submitReport", UUID.class, Map.class, Jwt.class))
                .isEqualTo("hasRole('STUDENT')");
        assertThat(gateOf("submitReportFile", UUID.class, MultipartFile.class, String.class,
                String.class, Jwt.class))
                .isEqualTo("hasRole('STUDENT')");
    }

    @Test
    void reportReadAndDownloadGatesKeepTheStaffMatrix() throws Exception {
        // Deliberately unchanged by the LEC-P2-5 fix.
        assertThat(gateOf("getReport", UUID.class, Jwt.class)).contains("ADMIN");
        assertThat(gateOf("downloadReportFile", UUID.class, Jwt.class)).contains("ADMIN");
    }

    private static String gateOf(String methodName, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        PreAuthorize annotation = ThesisMutationController.class
                .getDeclaredMethod(methodName, parameterTypes)
                .getAnnotation(PreAuthorize.class);
        return annotation == null ? null : annotation.value();
    }
}
