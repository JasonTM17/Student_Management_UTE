package io.campuscore.restfulapi.mail.service;

import io.campuscore.restfulapi.mail.web.MailDtos.AcademicNoticeRequest;
import io.campuscore.restfulapi.mail.web.MailDtos.CourseRegistrationRequest;
import io.campuscore.restfulapi.mail.web.MailDtos.GradeAlertRequest;
import java.util.Map;

public interface EmailService {

    /**
     * Send an HTML email synchronously using a Thymeleaf template.
     */
    void sendHtmlEmail(String to, String subject, String templateName, Map<String, Object> variables);

    /**
     * Send an HTML email asynchronously.
     */
    void sendAsyncHtmlEmail(String to, String subject, String templateName, Map<String, Object> variables);

    /**
     * Render a Thymeleaf template into HTML string for preview or inspection.
     */
    String renderPreview(String templateName, Map<String, Object> variables);

    /**
     * Send system verification test email.
     */
    void sendTestEmail(String to, String recipientName, String introMessage);

    /**
     * Send official academic notice email.
     */
    void sendAcademicNotice(AcademicNoticeRequest request);

    /**
     * Send course registration confirmation receipt.
     */
    void sendCourseRegistration(CourseRegistrationRequest request);

    /**
     * Send semester grade & conduct score report.
     */
    void sendGradeAlert(GradeAlertRequest request);
}
