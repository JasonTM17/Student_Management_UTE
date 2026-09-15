package io.campuscore.restfulapi.mail.service;

import io.campuscore.restfulapi.mail.config.MailConfig;
import io.campuscore.restfulapi.mail.web.MailDtos.AcademicNoticeRequest;
import io.campuscore.restfulapi.mail.web.MailDtos.CourseRegistrationRequest;
import io.campuscore.restfulapi.mail.web.MailDtos.GradeAlertRequest;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Service
public class EmailServiceImpl implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailServiceImpl.class);
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss dd/MM/yyyy");

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final MailConfig mailConfig;

    public EmailServiceImpl(
            JavaMailSender mailSender,
            TemplateEngine templateEngine,
            MailConfig mailConfig) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.mailConfig = mailConfig;
    }

    @Override
    public void sendHtmlEmail(String to, String subject, String templateName, Map<String, Object> variables) {
        if (!mailConfig.isEnabled()) {
            log.warn("Email service is disabled. Skipping dispatch to {}", to);
            return;
        }

        try {
            String htmlBody = renderPreview(templateName, variables);

            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    mimeMessage,
                    MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
                    StandardCharsets.UTF_8.name());

            helper.setFrom(new InternetAddress(
                    mailConfig.getFrom(),
                    mailConfig.getSenderName(),
                    StandardCharsets.UTF_8.name()));
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);

            mailSender.send(mimeMessage);
            log.info("Email sent successfully to {} [Subject: {}, Template: {}]", to, subject, templateName);
        } catch (MessagingException | UnsupportedEncodingException e) {
            log.error("Failed to construct or send email to {}: {}", to, e.getMessage(), e);
            throw new RuntimeException("Lỗi gửi email: " + e.getMessage(), e);
        }
    }

    @Async
    @Override
    public void sendAsyncHtmlEmail(String to, String subject, String templateName, Map<String, Object> variables) {
        try {
            sendHtmlEmail(to, subject, templateName, variables);
        } catch (Exception e) {
            log.error("Async email dispatch failed for recipient {}: {}", to, e.getMessage(), e);
        }
    }

    @Override
    public String renderPreview(String templateName, Map<String, Object> variables) {
        Context context = new Context(Locale.forLanguageTag("vi"));
        if (variables != null) {
            context.setVariables(variables);
        }
        String resolvedTemplate = templateName.startsWith("mail/") ? templateName : "mail/" + templateName;
        return templateEngine.process(resolvedTemplate, context);
    }

    @Override
    public void sendTestEmail(String to, String recipientName, String introMessage) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("subject", "CampusUTE - Xác nhận kết nối SMTP thành công");
        variables.put("recipientName", recipientName != null && !recipientName.isBlank() ? recipientName : "Quý Thầy/Cô & Sinh viên");
        variables.put("introMessage", introMessage != null && !introMessage.isBlank()
                ? introMessage
                : "Hệ thống Quản lý Đào tạo CampusUTE thông báo: Cấu hình SMTP Gmail với công nghệ mẫu giao diện Thymeleaf đã được thiết lập và kích hoạt thành công!");
        variables.put("statusBadge", "● KẾT NỐI SMTP THÀNH CÔNG");
        variables.put("smtpProtocol", "Gmail SMTP (Port 587 - TLS)");
        variables.put("senderEmail", mailConfig.getFrom());
        variables.put("timestamp", LocalDateTime.now().format(DATE_TIME_FORMATTER));
        variables.put("actionUrl", "https://www.campusute.io.vn");
        variables.put("actionText", "Truy cập Cổng Đào tạo CampusUTE");

        sendHtmlEmail(to, "CampusUTE - Xác nhận kiểm thử dịch vụ Email SMTP", "test-verification", variables);
    }

    @Override
    public void sendAcademicNotice(AcademicNoticeRequest request) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("category", request.category() != null ? request.category() : "THÔNG BÁO HỌC VỤ");
        variables.put("title", request.title());
        variables.put("author", request.author() != null ? request.author() : "Phòng Đào tạo");
        variables.put("publishDate", LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        variables.put("recipientName", request.recipientName() != null ? request.recipientName() : "Sinh viên");
        variables.put("content", request.content());
        variables.put("highlights", request.highlights());
        variables.put("actionUrl", request.actionUrl() != null ? request.actionUrl() : "https://www.campusute.io.vn/dashboard/announcements");
        variables.put("actionText", request.actionText() != null ? request.actionText() : "Xem chi tiết trên Cổng Đào tạo");

        String subject = "[CampusUTE] " + request.title();
        sendHtmlEmail(request.to(), subject, "academic-announcement", variables);
    }

    @Override
    public void sendCourseRegistration(CourseRegistrationRequest request) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("subject", "[CampusUTE] Xác nhận Đăng ký Học phần " + (request.semester() != null ? request.semester() : ""));
        variables.put("semester", request.semester() != null ? request.semester() : "Học kỳ 1 Năm học 2026-2027");
        variables.put("studentName", request.studentName());
        variables.put("studentId", request.studentId());
        variables.put("department", request.department() != null ? request.department() : "Khoa Công nghệ Thông tin");
        variables.put("timestamp", LocalDateTime.now().format(DATE_TIME_FORMATTER));
        variables.put("courses", request.courses());
        variables.put("totalCredits", request.totalCredits());

        String subject = "[CampusUTE] Xác nhận Đăng ký Học phần - " + request.studentId() + " (" + request.studentName() + ")";
        sendHtmlEmail(request.to(), subject, "course-registration", variables);
    }

    @Override
    public void sendGradeAlert(GradeAlertRequest request) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("subject", "[CampusUTE] Thông báo Bảng điểm & Điểm rèn luyện - " + request.studentId());
        variables.put("semester", request.semester() != null ? request.semester() : "Học kỳ 1 Năm học 2026-2027");
        variables.put("studentName", request.studentName());
        variables.put("studentId", request.studentId());
        variables.put("gpa4", request.gpa4());
        variables.put("gpa10", request.gpa10());
        variables.put("academicStanding", request.academicStanding() != null ? request.academicStanding() : "XUẤT SẮC");
        variables.put("conductScore", request.conductScore());
        variables.put("conductRank", request.conductRank() != null ? request.conductRank() : "XUẤT SẮC");
        variables.put("grades", request.grades());

        String subject = "[CampusUTE] Thông báo Kết quả Học tập & Rèn luyện - " + request.studentId();
        sendHtmlEmail(request.to(), subject, "grade-alert", variables);
    }
}
