package io.campuscore.restfulapi.mail.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class MailDtos {

    private MailDtos() {}

    public record TestEmailRequest(
            @Email(message = "Địa chỉ email không hợp lệ")
            @NotBlank(message = "Email người nhận không được để trống")
            @Size(max = 255)
            String to,
            @Size(max = 255)
            String recipientName,
            @Size(max = 20_000)
            String introMessage
    ) {}

    public record AcademicNoticeRequest(
            @Email(message = "Địa chỉ email không hợp lệ")
            @NotBlank(message = "Email người nhận không được để trống")
            @Size(max = 255)
            String to,
            @Size(max = 255)
            String recipientName,
            @Size(max = 255)
            String category,
            @NotBlank(message = "Tiêu đề thông báo không được để trống")
            @Size(max = 300)
            String title,
            @Size(max = 255)
            String author,
            @NotBlank(message = "Nội dung thông báo không được để trống")
            @Size(max = 20_000)
            String content,
            @Size(max = 20)
            List<@Size(max = 500) String> highlights,
            @Size(max = 2_048)
            String actionUrl,
            @Size(max = 500)
            String actionText
    ) {}

    public record CourseItem(
            @Size(max = 255)
            String code,
            @Size(max = 255)
            String name,
            @Min(0)
            @Max(30)
            int credits,
            @Size(max = 255)
            String lecturer,
            @Size(max = 255)
            String schedule
    ) {}

    public record CourseRegistrationRequest(
            @Email(message = "Địa chỉ email không hợp lệ")
            @NotBlank(message = "Email người nhận không được để trống")
            @Size(max = 255)
            String to,
            @NotBlank(message = "Tên sinh viên không được để trống")
            @Size(max = 255)
            String studentName,
            @NotBlank(message = "Mã số sinh viên không được để trống")
            @Size(max = 255)
            String studentId,
            @Size(max = 255)
            String department,
            @Size(max = 255)
            String semester,
            @NotEmpty(message = "Danh sách môn học không được để trống")
            @Size(max = 30)
            List<@NotNull @Valid CourseItem> courses,
            @Min(0)
            @Max(60)
            int totalCredits
    ) {}

    public record GradeItem(
            @Size(max = 255)
            String courseCode,
            @Size(max = 255)
            String courseName,
            @Min(0)
            @Max(30)
            int credits,
            @DecimalMin("0.0")
            @DecimalMax("10.0")
            double score10,
            @Size(max = 100)
            String scoreLetter
    ) {}

    public record GradeAlertRequest(
            @Email(message = "Địa chỉ email không hợp lệ")
            @NotBlank(message = "Email người nhận không được để trống")
            @Size(max = 255)
            String to,
            @NotBlank(message = "Tên sinh viên không được để trống")
            @Size(max = 255)
            String studentName,
            @NotBlank(message = "MSSV không được để trống")
            @Size(max = 255)
            String studentId,
            @Size(max = 255)
            String semester,
            @DecimalMin("0.0")
            @DecimalMax("4.0")
            double gpa4,
            @DecimalMin("0.0")
            @DecimalMax("10.0")
            double gpa10,
            @Size(max = 100)
            String academicStanding,
            @Min(0)
            @Max(100)
            int conductScore,
            @Size(max = 100)
            String conductRank,
            @Size(max = 60)
            List<@NotNull @Valid GradeItem> grades
    ) {}

    public record MailDispatchResponse(
            boolean success,
            String message,
            String recipient,
            String template,
            Instant dispatchedAt
    ) {}
}
