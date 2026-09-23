package io.campuscore.restfulapi.mail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
}
