package io.campuscore.restfulapi.mail.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.time.Instant;
import java.util.List;

public final class MailDtos {

    private MailDtos() {}

    public record TestEmailRequest(
            @Email(message = "Địa chỉ email không hợp lệ")
            @NotBlank(message = "Email người nhận không được để trống")
            String to,
            String recipientName,
            String introMessage
    ) {}

    public record AcademicNoticeRequest(
            @Email(message = "Địa chỉ email không hợp lệ")
            @NotBlank(message = "Email người nhận không được để trống")
            String to,
            String recipientName,
            String category,
            @NotBlank(message = "Tiêu đề thông báo không được để trống")
            String title,
            String author,
            @NotBlank(message = "Nội dung thông báo không được để trống")
            String content,
            List<String> highlights,
            String actionUrl,
            String actionText
    ) {}

    public record CourseItem(
            String code,
            String name,
            int credits,
            String lecturer,
            String schedule
    ) {}

    public record CourseRegistrationRequest(
            @Email(message = "Địa chỉ email không hợp lệ")
            @NotBlank(message = "Email người nhận không được để trống")
            String to,
            @NotBlank(message = "Tên sinh viên không được để trống")
            String studentName,
            @NotBlank(message = "Mã số sinh viên không được để trống")
            String studentId,
            String department,
            String semester,
            @NotEmpty(message = "Danh sách môn học không được để trống")
            List<CourseItem> courses,
            int totalCredits
    ) {}

    public record GradeItem(
            String courseCode,
            String courseName,
            int credits,
            double score10,
            String scoreLetter
    ) {}

    public record GradeAlertRequest(
            @Email(message = "Địa chỉ email không hợp lệ")
            @NotBlank(message = "Email người nhận không được để trống")
            String to,
            @NotBlank(message = "Tên sinh viên không được để trống")
            String studentName,
            @NotBlank(message = "MSSV không được để trống")
            String studentId,
            String semester,
            double gpa4,
            double gpa10,
            String academicStanding,
            int conductScore,
            String conductRank,
            List<GradeItem> grades
    ) {}

    public record MailDispatchResponse(
            boolean success,
            String message,
            String recipient,
            String template,
            Instant dispatchedAt
    ) {}
}
