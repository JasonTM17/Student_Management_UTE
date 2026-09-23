package io.campuscore.restfulapi.mail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.campuscore.restfulapi.mail.config.MailConfig;
import io.campuscore.restfulapi.mail.service.EmailServiceImpl;
import io.campuscore.restfulapi.mail.web.MailDtos.AcademicNoticeRequest;
import io.campuscore.restfulapi.mail.web.MailDtos.CourseItem;
import io.campuscore.restfulapi.mail.web.MailDtos.CourseRegistrationRequest;
import io.campuscore.restfulapi.mail.web.MailDtos.GradeAlertRequest;
import io.campuscore.restfulapi.mail.web.MailDtos.GradeItem;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

class EmailServiceTest {

    private JavaMailSender mailSender;
    private EmailServiceImpl emailService;
    private MailConfig mailConfig;

    @BeforeEach
    void setUp() {
        mailSender = mock(JavaMailSender.class);
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));

        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(false);

        SpringTemplateEngine templateEngine = new SpringTemplateEngine();
        templateEngine.setTemplateResolver(resolver);

        mailConfig = new MailConfig();
        mailConfig.setFrom("no-reply@campuscore.local");
        mailConfig.setSenderName("CampusUTE - Cổng Đào Tạo HCMUTE");
        mailConfig.setEnabled(true);

        emailService = new EmailServiceImpl(mailSender, templateEngine, mailConfig);
    }

    @Test
    void sendsTestEmailProperly() throws Exception {
        emailService.sendTestEmail("test@example.com", "Test User", "Test message");

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, times(1)).send(captor.capture());

        MimeMessage sent = captor.getValue();
        assertNotNull(sent);
        assertEquals("CampusUTE - Xác nhận kiểm thử dịch vụ Email SMTP", sent.getSubject());
    }

    @Test
    void sendsAcademicNoticeProperly() throws Exception {
        AcademicNoticeRequest request = new AcademicNoticeRequest(
                "student@example.com",
                "Sinh viên A",
                "HỌC VỤ",
                "Thông báo Lịch thi Học kỳ 1",
                "Phòng Đào tạo",
                "Nội dung thông báo lịch thi chi tiết.",
                List.of("Môn 1: 20/12", "Môn 2: 22/12"),
                "https://www.campusute.io.vn",
                "Xem chi tiết"
        );

        emailService.sendAcademicNotice(request);

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, times(1)).send(captor.capture());

        MimeMessage sent = captor.getValue();
        assertEquals("[CampusUTE] Thông báo Lịch thi Học kỳ 1", sent.getSubject());
    }

    @Test
    void sendsCourseRegistrationProperly() throws Exception {
        CourseRegistrationRequest request = new CourseRegistrationRequest(
                "student@example.com",
                "Nguyễn Tiến Sơn",
                "22110001",
                "Khoa CNTT",
                "HK1 (2026-2027)",
                List.of(new CourseItem("SE013", "Web", 3, "GV", "T2")),
                3
        );

        emailService.sendCourseRegistration(request);

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, times(1)).send(captor.capture());

        MimeMessage sent = captor.getValue();
        assertEquals("[CampusUTE] Xác nhận Đăng ký Học phần - 22110001 (Nguyễn Tiến Sơn)", sent.getSubject());
    }

    @Test
    void sendsGradeAlertProperly() throws Exception {
        GradeAlertRequest request = new GradeAlertRequest(
                "student@example.com",
                "Nguyễn Tiến Sơn",
                "22110001",
                "HK2 (2025-2026)",
                3.85,
                9.20,
                "XUẤT SẮC",
                95,
                "XUẤT SẮC",
                List.of(new GradeItem("SE001", "Lập trình", 3, 9.5, "A+"))
        );

        emailService.sendGradeAlert(request);

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, times(1)).send(captor.capture());

        MimeMessage sent = captor.getValue();
        assertEquals("[CampusUTE] Thông báo Kết quả Học tập & Rèn luyện - 22110001", sent.getSubject());
    }

    @Test
    void rendersAllMailLayoutsWithReadableBrandAndSafeAnnouncementText() {
        String verification = emailService.renderPreview("test-verification", Map.of(
                "subject", "Kiểm tra email", "recipientName", "Sinh viên mẫu"));
        String announcement = emailService.renderPreview("academic-announcement", Map.of(
                "title", "Lịch đăng ký học phần", "content", "Dòng một\nDòng hai <script>alert(1)</script>"));
        String registration = emailService.renderPreview("course-registration", Map.of(
                "subject", "Xác nhận đăng ký", "studentName", "Sinh viên mẫu",
                "studentId", "SV001", "totalCredits", 3,
                "courses", List.of(new CourseItem("SE001", "Lập trình", 3, "GV", "T2"))));
        String grades = emailService.renderPreview("grade-alert", Map.of(
                "subject", "Kết quả học tập", "studentName", "Sinh viên mẫu", "studentId", "SV001",
                "gpa4", 3.5, "gpa10", 8.8, "conductScore", 90,
                "grades", List.of(new GradeItem("SE001", "Lập trình", 3, 8.8, "A"))));

        for (String html : List.of(verification, announcement, registration, grades)) {
            assertTrue(html.contains("CampusUTE"));
            assertTrue(html.contains("@media screen and (max-width: 640px)"));
            assertTrue(html.contains("class=\"email-shell\""));
        }
        assertTrue(registration.contains("Đăng ký học phần đã được ghi nhận"));
        assertTrue(announcement.contains("white-space:pre-line"));
        assertTrue(announcement.contains("&lt;script&gt;"));
    }
}
