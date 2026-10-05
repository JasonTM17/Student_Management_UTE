package io.campuscore.restfulapi.mail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.campuscore.restfulapi.mail.repository.MailRecipientScopeRepository;
import io.campuscore.restfulapi.mail.repository.MailRecipientScopeRepository.ScopedRecipient;
import io.campuscore.restfulapi.mail.service.EmailService;
import io.campuscore.restfulapi.mail.web.MailController;
import io.campuscore.restfulapi.mail.web.MailDtos.AcademicNoticeRequest;
import io.campuscore.restfulapi.mail.web.MailDtos.CourseItem;
import io.campuscore.restfulapi.mail.web.MailDtos.CourseRegistrationRequest;
import io.campuscore.restfulapi.mail.web.MailDtos.GradeAlertRequest;
import io.campuscore.restfulapi.mail.web.MailDtos.GradeItem;
import io.campuscore.restfulapi.mail.web.MailDtos.MailDispatchResponse;
import io.campuscore.restfulapi.mail.web.MailDtos.TestEmailRequest;
import io.campuscore.restfulapi.web.DomainException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;

class MailControllerTest {

    private EmailService emailService;
    private MailRecipientScopeRepository scopeRepository;
    private MailController controller;

    @BeforeEach
    void setUp() {
        emailService = mock(EmailService.class);
        scopeRepository = mock(MailRecipientScopeRepository.class);
        ObjectProvider<MailRecipientScopeRepository> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(scopeRepository);
        controller = new MailController(emailService, provider);
    }

    private static Jwt jwtWithRoles(List<String> roles, String lecturerId) {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaimAsStringList("roles")).thenReturn(roles);
        when(jwt.getClaimAsString("lecturerId")).thenReturn(lecturerId);
        return jwt;
    }

    private static Jwt adminJwt() {
        return jwtWithRoles(List.of("ADMIN"), null);
    }

    private static Jwt lecturerJwt() {
        return jwtWithRoles(List.of("LECTURER"), "lec-1");
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
    void sendNoticeInvokesServiceForAdmin() {
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
        ResponseEntity<MailDispatchResponse> response = controller.sendNotice(adminJwt(), request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().success());
        verify(emailService).sendAcademicNotice(request);
    }

    @Test
    void lecturerNoticeIsBoundToScopedStudent() {
        // The lecturer may address a student of their own sections; the real
        // mailbox and identity come from the database, not the request body.
        when(scopeRepository.findScopedRecipient("lec-1", "victim@external.example"))
                .thenReturn(new ScopedRecipient("sv.real@campuscore.edu", "Nguyễn Văn A", "22110099"));
        AcademicNoticeRequest request = new AcademicNoticeRequest(
                "victim@external.example",
                "Fake Name",
                "HỌC VỤ",
                "Tiêu đề",
                "Phòng ĐT",
                "Nội dung",
                List.of("Mốc 1"),
                "https://campusute.io.vn",
                "Xem"
        );

        ResponseEntity<MailDispatchResponse> response = controller.sendNotice(lecturerJwt(), request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        ArgumentCaptor<AcademicNoticeRequest> captor = ArgumentCaptor.forClass(AcademicNoticeRequest.class);
        verify(emailService).sendAcademicNotice(captor.capture());
        assertEquals("sv.real@campuscore.edu", captor.getValue().to());
        assertEquals("Nguyễn Văn A", captor.getValue().recipientName());
        assertEquals("sv.real@campuscore.edu", response.getBody().recipient());
    }

    @Test
    void lecturerNoticeToOutOfScopeAddressIsRefused() {
        // Abuse case: a lecturer must not be able to send institution-branded
        // mail to an address that is not one of their students.
        when(scopeRepository.findScopedRecipient("lec-1", "anyone@external.example")).thenReturn(null);
        AcademicNoticeRequest request = new AcademicNoticeRequest(
                "anyone@external.example",
                "Ai đó",
                "HỌC VỤ",
                "Tiêu đề",
                "Phòng ĐT",
                "Nội dung",
                List.of("Mốc 1"),
                "https://evil.example",
                "Xem"
        );

        DomainException exception = assertThrows(DomainException.class,
                () -> controller.sendNotice(lecturerJwt(), request));

        assertEquals(HttpStatus.FORBIDDEN, exception.status());
        assertEquals("MAIL_RECIPIENT_OUT_OF_SCOPE", exception.code());
        verify(emailService, never()).sendAcademicNotice(any());
    }

    @Test
    void lecturerWithoutClaimCannotSend() {
        Jwt jwt = jwtWithRoles(List.of("LECTURER"), null);
        AcademicNoticeRequest request = new AcademicNoticeRequest(
                "student@campuscore.edu", "SV", "HỌC VỤ", "T", "A", "C",
                List.of(), null, null);

        DomainException exception = assertThrows(DomainException.class,
                () -> controller.sendNotice(jwt, request));

        assertEquals(HttpStatus.FORBIDDEN, exception.status());
        assertEquals("MAIL_SCOPE_FORBIDDEN", exception.code());
    }

    @Test
    void lecturerRegistrationOverridesForgedIdentity() {
        // The request claims one student/address but the scoped resolution
        // rebuilds the recipient and identity from enrolment records.
        when(scopeRepository.findScopedRecipient("lec-1", "22110001"))
                .thenReturn(new ScopedRecipient("sv@campuscore.edu", "Trần Thị B", "22110001"));
        CourseRegistrationRequest request = new CourseRegistrationRequest(
                "attacker@external.example",
                "Tên Giả Mạo",
                "22110001",
                "CNTT",
                "HK1",
                List.of(new CourseItem("SE013", "Web", 3, "GV", "T2")),
                3
        );

        ResponseEntity<MailDispatchResponse> response = controller.sendRegistration(lecturerJwt(), request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        ArgumentCaptor<CourseRegistrationRequest> captor = ArgumentCaptor.forClass(CourseRegistrationRequest.class);
        verify(emailService).sendCourseRegistration(captor.capture());
        assertEquals("sv@campuscore.edu", captor.getValue().to());
        assertEquals("Trần Thị B", captor.getValue().studentName());
        assertEquals("22110001", captor.getValue().studentId());
    }

    @Test
    void lecturerSendNeverFallsBackToRequestIdentity() {
        // When the student record has no display name, the mail must carry the
        // resolved student number — never the request-supplied name.
        when(scopeRepository.findScopedRecipient("lec-1", "22110001"))
                .thenReturn(new ScopedRecipient("sv@campuscore.edu", "", "22110001"));
        CourseRegistrationRequest request = new CourseRegistrationRequest(
                "student@campuscore.edu",
                "Tên Giả Mạo",
                "22110001",
                "CNTT",
                "HK1",
                List.of(new CourseItem("SE013", "Web", 3, "GV", "T2")),
                3
        );

        controller.sendRegistration(lecturerJwt(), request);

        ArgumentCaptor<CourseRegistrationRequest> captor = ArgumentCaptor.forClass(CourseRegistrationRequest.class);
        verify(emailService).sendCourseRegistration(captor.capture());
        assertEquals("22110001", captor.getValue().studentName());
    }

    @Test
    void lecturerRegistrationForForeignStudentIsRefused() {
        when(scopeRepository.findScopedRecipient("lec-1", "99999999")).thenReturn(null);
        CourseRegistrationRequest request = new CourseRegistrationRequest(
                "student@campuscore.edu",
                "Sơn",
                "99999999",
                "CNTT",
                "HK1",
                List.of(new CourseItem("SE013", "Web", 3, "GV", "T2")),
                3
        );

        DomainException exception = assertThrows(DomainException.class,
                () -> controller.sendRegistration(lecturerJwt(), request));

        assertEquals(HttpStatus.FORBIDDEN, exception.status());
        assertEquals("MAIL_RECIPIENT_OUT_OF_SCOPE", exception.code());
        verify(emailService, never()).sendCourseRegistration(any());
    }

    @Test
    void lecturerGradeAlertOverridesForgedRecipient() {
        when(scopeRepository.findScopedRecipient("lec-1", "22110001"))
                .thenReturn(new ScopedRecipient("sv@campuscore.edu", "Trần Thị B", "22110001"));
        GradeAlertRequest request = new GradeAlertRequest(
                "attacker@external.example",
                "Tên Giả",
                "22110001",
                "HK1",
                3.8,
                9.0,
                "XUẤT SẮC",
                95,
                "XUẤT SẮC",
                List.of(new GradeItem("SE001", "LT", 3, 9.5, "A+"))
        );

        ResponseEntity<MailDispatchResponse> response = controller.sendGradeAlert(lecturerJwt(), request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        ArgumentCaptor<GradeAlertRequest> captor = ArgumentCaptor.forClass(GradeAlertRequest.class);
        verify(emailService).sendGradeAlert(captor.capture());
        assertEquals("sv@campuscore.edu", captor.getValue().to());
        assertEquals("Trần Thị B", captor.getValue().studentName());
    }

    @Test
    void sendRegistrationInvokesServiceForAdmin() {
        CourseRegistrationRequest request = new CourseRegistrationRequest(
                "student@campuscore.edu",
                "Sơn",
                "22110001",
                "CNTT",
                "HK1",
                List.of(new CourseItem("SE013", "Web", 3, "GV", "T2")),
                3
        );
        ResponseEntity<MailDispatchResponse> response = controller.sendRegistration(adminJwt(), request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().success());
        verify(emailService).sendCourseRegistration(request);
    }

    @Test
    void sendGradeAlertInvokesServiceForAdmin() {
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
        ResponseEntity<MailDispatchResponse> response = controller.sendGradeAlert(adminJwt(), request);

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
