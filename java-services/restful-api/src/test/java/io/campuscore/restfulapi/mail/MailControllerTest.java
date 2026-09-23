package io.campuscore.restfulapi.mail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.campuscore.restfulapi.mail.service.EmailService;
import io.campuscore.restfulapi.mail.web.MailController;
import io.campuscore.restfulapi.mail.web.MailDtos.AcademicNoticeRequest;
import io.campuscore.restfulapi.mail.web.MailDtos.CourseItem;
import io.campuscore.restfulapi.mail.web.MailDtos.CourseRegistrationRequest;
import io.campuscore.restfulapi.mail.web.MailDtos.GradeAlertRequest;
import io.campuscore.restfulapi.mail.web.MailDtos.GradeItem;
import io.campuscore.restfulapi.mail.web.MailDtos.MailDispatchResponse;
import io.campuscore.restfulapi.mail.web.MailDtos.TestEmailRequest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class MailControllerTest {

    private EmailService emailService;
    private MailController controller;

    @BeforeEach
    void setUp() {
        emailService = mock(EmailService.class);
        controller = new MailController(emailService);
    }

    @Test
    void sendTestEmailInvokesService() {
        TestEmailRequest request = new TestEmailRequest("admin@campuscore.local", "Admin", "Hello");
        ResponseEntity<MailDispatchResponse> response = controller.sendTestEmail(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().success());
        assertEquals("admin@campuscore.local", response.getBody().recipient());
        verify(emailService).sendTestEmail("admin@campuscore.local", "Admin", "Hello");
    }

    @Test
    void sendTestEmailFallsBackToInstitutionDefaultRecipient() {
        // Regression: the default used to be a personal Gmail inbox. With no
        // recipient supplied, dispatch must go to the institution-owned sink.
        ResponseEntity<MailDispatchResponse> response = controller.sendTestEmail(null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("no-reply@campuscore.local", response.getBody().recipient());
        verify(emailService).sendTestEmail("no-reply@campuscore.local", "Quản trị viên CampusUTE", null);
    }

    @Test
    void sendNoticeInvokesService() {
        AcademicNoticeRequest request = new AcademicNoticeRequest(
                "student@campuscore.edu",
                "Sinh viên",
                "HỌC VỤ",
                "Tiêu đề",
                "Phòng ĐT",
                "Nội dung",
                List.of("Mốc 1"),
                "https://campusute.io.vn",
                "Xem"
        );
        ResponseEntity<MailDispatchResponse> response = controller.sendNotice(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().success());
        verify(emailService).sendAcademicNotice(request);
    }

    @Test
    void sendRegistrationInvokesService() {
        CourseRegistrationRequest request = new CourseRegistrationRequest(
                "student@campuscore.edu",
                "Sơn",
                "22110001",
                "CNTT",
                "HK1",
                List.of(new CourseItem("SE013", "Web", 3, "GV", "T2")),
                3
        );
        ResponseEntity<MailDispatchResponse> response = controller.sendRegistration(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().success());
        verify(emailService).sendCourseRegistration(request);
    }

    @Test
    void sendGradeAlertInvokesService() {
        GradeAlertRequest request = new GradeAlertRequest(
                "student@campuscore.edu",
                "Sơn",
                "22110001",
                "HK1",
                3.8,
                9.0,
                "XUẤT SẮC",
                95,
                "XUẤT SẮC",
                List.of(new GradeItem("SE001", "LT", 3, 9.5, "A+"))
        );
        ResponseEntity<MailDispatchResponse> response = controller.sendGradeAlert(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().success());
        verify(emailService).sendGradeAlert(request);
    }

    @Test
    void previewTemplateReturnsHtml() {
        when(emailService.renderPreview(anyString(), any())).thenReturn("<html><body>Preview</body></html>");

        ResponseEntity<String> response = controller.previewTemplate("test-verification");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("Preview"));
    }
}
