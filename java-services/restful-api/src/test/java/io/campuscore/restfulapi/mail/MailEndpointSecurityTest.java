package io.campuscore.restfulapi.mail;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import io.campuscore.restfulapi.mail.repository.MailRecipientScopeRepository;
import io.campuscore.restfulapi.mail.repository.MailRecipientScopeRepository.ScopedRecipient;
import io.campuscore.restfulapi.mail.service.EmailService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Finding A of the LTWeb audit: /api/v1/mail/** used to sit in the permitAll
 * list, so anyone could trigger arbitrary-recipient mail (an open relay in
 * practice). Sending is now a staff action and previews require a login.
 * EmailService is mocked so the boundary — not SMTP delivery — is what runs.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "persistence"})
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:restful_api_mail_security;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration-h2"
})
class MailEndpointSecurityTest {

    private static final String VALID_GRADE_ALERT = """
            {"to":"sv@campuscore.edu","studentName":"Nguyen Van A","studentId":"SV1234",
             "semester":"HK1 2026","gpa4":3.5,"gpa10":8.0,"academicStanding":"GOOD",
             "conductScore":85,"conductRank":"GOOD","grades":[]}""";

    private static final String VALID_TEST_MAIL =
            "{\"to\":\"staff@campuscore.edu\",\"recipientName\":\"Staff\",\"introMessage\":\"probe\"}";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private EmailService emailService;

    @MockitoBean
    private MailRecipientScopeRepository recipientScope;

    @Test
    void anonymousSendIsRejected() throws Exception {
        mvc.perform(post("/api/v1/mail/grade-alert")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_GRADE_ALERT))
                .andExpect(status().isUnauthorized());
        verify(emailService, never()).sendGradeAlert(any());
    }

    @Test
    void studentRoleNeverReachesTheMailService() throws Exception {
        mvc.perform(post("/api/v1/mail/grade-alert")
                        .with(studentJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_GRADE_ALERT))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/mail/test")
                        .with(studentJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_TEST_MAIL))
                .andExpect(status().isForbidden());
        verify(emailService, never()).sendGradeAlert(any());
        verify(emailService, never()).sendTestEmail(any(), any(), any());
    }

    @Test
    void lecturerMaySendGradeAlertsButNotSystemTests() throws Exception {
        mvc.perform(post("/api/v1/mail/test")
                        .with(lecturerJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_TEST_MAIL))
                .andExpect(status().isForbidden());
        verify(emailService, never()).sendTestEmail(any(), any(), any());

        // The lecturer scopes the alert to a student of their own sections;
        // the service resolves the recipient from enrolment records.
        when(recipientScope.findScopedRecipient(eq("mail-lecturer-id"), eq("SV1234")))
                .thenReturn(new ScopedRecipient("sv@campuscore.edu", "Nguyen Van A", "SV1234"));
        mvc.perform(post("/api/v1/mail/grade-alert")
                        .with(lecturerJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_GRADE_ALERT))
                .andExpect(status().isOk());
        verify(emailService).sendGradeAlert(any());
    }

    @Test
    void lecturerCannotSendToAddressOutsideTheirSections() throws Exception {
        // Abuse case pinned at the boundary: an arbitrary recipient address
        // never reaches the mail service for a lecturer caller.
        when(recipientScope.findScopedRecipient(eq("mail-lecturer-id"), any())).thenReturn(null);
        mvc.perform(post("/api/v1/mail/grade-alert")
                        .with(lecturerJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_GRADE_ALERT))
                .andExpect(status().isForbidden());
        verify(emailService, never()).sendGradeAlert(any());
    }

    @Test
    void anonymousPreviewIsRejected() throws Exception {
        mvc.perform(get("/api/v1/mail/preview/grade-alert"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void superAdminMaySendTestEmailAndGradeAlerts() throws Exception {
        mvc.perform(post("/api/v1/mail/test")
                        .with(superAdminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_TEST_MAIL))
                .andExpect(status().isOk());
        verify(emailService).sendTestEmail(any(), any(), any());

        mvc.perform(post("/api/v1/mail/grade-alert")
                        .with(superAdminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_GRADE_ALERT))
                .andExpect(status().isOk());
        verify(emailService).sendGradeAlert(any());
    }

    private RequestPostProcessor studentJwt() {
        return jwt().jwt(token -> token
                        .subject("mail-student-user")
                        .claim("roles", List.of("STUDENT")))
                .authorities(new SimpleGrantedAuthority("ROLE_STUDENT"));
    }

    private RequestPostProcessor lecturerJwt() {
        return jwt().jwt(token -> token
                        .subject("mail-lecturer-user")
                        .claim("lecturerId", "mail-lecturer-id")
                        .claim("roles", List.of("LECTURER")))
                .authorities(new SimpleGrantedAuthority("ROLE_LECTURER"));
    }

    private RequestPostProcessor superAdminJwt() {
        return jwt().jwt(token -> token
                        .subject("mail-super-admin-user")
                        .claim("roles", List.of("SUPER_ADMIN")))
                .authorities(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"));
    }
}
