package io.campuscore.restfulapi.mail.web;

import io.campuscore.restfulapi.mail.repository.MailRecipientScopeRepository;
import io.campuscore.restfulapi.mail.repository.MailRecipientScopeRepository.ScopedRecipient;
import io.campuscore.restfulapi.mail.service.EmailService;
import io.campuscore.restfulapi.mail.web.MailDtos.AcademicNoticeRequest;
import io.campuscore.restfulapi.mail.web.MailDtos.CourseItem;
import io.campuscore.restfulapi.mail.web.MailDtos.CourseRegistrationRequest;
import io.campuscore.restfulapi.mail.web.MailDtos.GradeAlertRequest;
import io.campuscore.restfulapi.mail.web.MailDtos.GradeItem;
import io.campuscore.restfulapi.mail.web.MailDtos.MailDispatchResponse;
import io.campuscore.restfulapi.mail.web.MailDtos.TestEmailRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import io.campuscore.restfulapi.web.DomainException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/mail")
@Tag(name = "Mail Services", description = "Hệ thống gửi Email SMTP & Thymeleaf Templates")
public class MailController {

    private final EmailService emailService;
    private final ObjectProvider<MailRecipientScopeRepository> recipientScope;

    public MailController(EmailService emailService, ObjectProvider<MailRecipientScopeRepository> recipientScope) {
        this.emailService = emailService;
        this.recipientScope = recipientScope;
    }

    @PostMapping("/test")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    @Operation(summary = "Gửi email kiểm thử hệ thống SMTP Gmail")
    public ResponseEntity<MailDispatchResponse> sendTestEmail(@Valid @RequestBody(required = false) TestEmailRequest request) {
        // Default is an institution-owned sink; an explicitly passed `to` wins.
        String to = (request != null && request.to() != null && !request.to().isBlank())
                ? request.to()
                : "no-reply@campuscore.local";
        String name = (request != null && request.recipientName() != null)
                ? request.recipientName()
                : "Quản trị viên CampusUTE";
        String intro = (request != null) ? request.introMessage() : null;

        emailService.sendTestEmail(to, name, intro);

        return ResponseEntity.ok(new MailDispatchResponse(
                true,
                "Email kiểm thử đã được gửi thành công qua SMTP Gmail!",
                to,
                "test-verification",
                Instant.now()));
    }

    @PostMapping("/notice")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','LECTURER')")
    @Operation(summary = "Gửi thông báo học vụ chính thức")
    public ResponseEntity<MailDispatchResponse> sendNotice(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AcademicNoticeRequest request) {
        AcademicNoticeRequest effective = scopeNoticeRecipient(jwt, request);
        emailService.sendAcademicNotice(effective);
        return ResponseEntity.ok(new MailDispatchResponse(
                true,
                "Thông báo học vụ đã được gửi thành công!",
                effective.to(),
                "academic-announcement",
                Instant.now()));
    }

    @PostMapping("/registration")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','LECTURER')")
    @Operation(summary = "Gửi xác nhận đăng ký học phần & TKB")
    public ResponseEntity<MailDispatchResponse> sendRegistration(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CourseRegistrationRequest request) {
        CourseRegistrationRequest effective = scopeStudentRecipient(jwt, request);
        emailService.sendCourseRegistration(effective);
        return ResponseEntity.ok(new MailDispatchResponse(
                true,
                "Xác nhận đăng ký học phần đã được gửi thành công!",
                effective.to(),
                "course-registration",
                Instant.now()));
    }

    @PostMapping("/grade-alert")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','LECTURER')")
    @Operation(summary = "Gửi thông báo bảng điểm & điểm rèn luyện")
    public ResponseEntity<MailDispatchResponse> sendGradeAlert(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody GradeAlertRequest request) {
        GradeAlertRequest effective = scopeStudentRecipient(jwt, request);
        emailService.sendGradeAlert(effective);
        return ResponseEntity.ok(new MailDispatchResponse(
                true,
                "Báo cáo kết quả học tập & rèn luyện đã được gửi thành công!",
                effective.to(),
                "grade-alert",
                Instant.now()));
    }

    /**
     * Lecturer sends are bound to their own students: the recipient is resolved
     * from the request's student number/email and must be (or have been)
     * enrolled in a section taught by the caller. The address and identity are
     * then rebuilt from the database so the request body cannot redirect the
     * email or impersonate another student.
     */
    private ScopedRecipient requireScopedRecipient(Jwt jwt, String studentKey) {
        List<String> roles = jwt != null ? jwt.getClaimAsStringList("roles") : null;
        if (roles != null && (roles.contains("ADMIN") || roles.contains("SUPER_ADMIN"))) {
            return null;
        }
        String lecturerId = jwt != null ? jwt.getClaimAsString("lecturerId") : null;
        MailRecipientScopeRepository scope = recipientScope.getIfAvailable();
        if (lecturerId == null || lecturerId.isBlank() || scope == null) {
            throw new DomainException(HttpStatus.FORBIDDEN, "MAIL_SCOPE_FORBIDDEN",
                    "Tài khoản không có quyền gửi email học vụ cho người nhận này");
        }
        ScopedRecipient recipient = scope.findScopedRecipient(lecturerId, studentKey == null ? "" : studentKey.trim());
        if (recipient == null) {
            throw new DomainException(HttpStatus.FORBIDDEN, "MAIL_RECIPIENT_OUT_OF_SCOPE",
                    "Người nhận không phải sinh viên trong các lớp bạn phụ trách");
        }
        return recipient;
    }

    private CourseRegistrationRequest scopeStudentRecipient(Jwt jwt, CourseRegistrationRequest request) {
        ScopedRecipient recipient = requireScopedRecipient(jwt, request.studentId());
        if (recipient == null) {
            return request;
        }
        return new CourseRegistrationRequest(
                recipient.email(),
                recipient.fullName().isBlank() ? recipient.studentNumber() : recipient.fullName(),
                recipient.studentNumber(),
                request.department(),
                request.semester(),
                request.courses(),
                request.totalCredits());
    }

    private GradeAlertRequest scopeStudentRecipient(Jwt jwt, GradeAlertRequest request) {
        ScopedRecipient recipient = requireScopedRecipient(jwt, request.studentId());
        if (recipient == null) {
            return request;
        }
        return new GradeAlertRequest(
                recipient.email(),
                recipient.fullName().isBlank() ? recipient.studentNumber() : recipient.fullName(),
                recipient.studentNumber(),
                request.semester(),
                request.gpa4(),
                request.gpa10(),
                request.academicStanding(),
                request.conductScore(),
                request.conductRank(),
                request.grades());
    }

    private AcademicNoticeRequest scopeNoticeRecipient(Jwt jwt, AcademicNoticeRequest request) {
        ScopedRecipient recipient = requireScopedRecipient(jwt, request.to());
        if (recipient == null) {
            return request;
        }
        return new AcademicNoticeRequest(
                recipient.email(),
                recipient.fullName().isBlank() ? recipient.studentNumber() : recipient.fullName(),
                request.category(),
                request.title(),
                request.author(),
                request.content(),
                request.highlights(),
                request.actionUrl(),
                request.actionText());
    }

    @GetMapping(value = "/preview/{templateName}", produces = MediaType.TEXT_HTML_VALUE + ";charset=UTF-8")
    @PreAuthorize("hasAnyRole('STUDENT','LECTURER','ADMIN','SUPER_ADMIN','TRUONG_KHOA')")
    @Operation(summary = "Xem trước trực tiếp giao diện HTML email trên trình duyệt")
    public ResponseEntity<String> previewTemplate(@PathVariable String templateName) {
        // Wukong finding: the name flows into "mail/" + templateName, so only
        // plain template slugs are accepted — no paths, no traversal.
        if (!templateName.matches("[a-z0-9-]+")) {
            return ResponseEntity.notFound().build();
        }
        // WS-Admin round-11: an unknown but well-formed slug used to escape
        // into the template engine and surface as a 500 TemplateInputException.
        // Only the known preview templates exist; anything else is a 404.
        if (!PREVIEW_TEMPLATES.contains(templateName)) {
            return ResponseEntity.notFound().build();
        }
        Map<String, Object> sampleData = buildSampleData(templateName);
        String html = emailService.renderPreview(templateName, sampleData);
        return ResponseEntity.ok(html);
    }

    private static final java.util.Set<String> PREVIEW_TEMPLATES =
            java.util.Set.of("test-verification", "academic-announcement", "course-registration", "grade-alert",
                    "two-factor-code");

    private Map<String, Object> buildSampleData(String templateName) {
        Map<String, Object> data = new HashMap<>();
        String nowStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss dd/MM/yyyy"));
        String dateStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));

        switch (templateName) {
            case "test-verification" -> {
                data.put("subject", "CampusUTE - Xác nhận kết nối SMTP thành công");
                data.put("recipientName", "Nguyễn Tiến Sơn (Sinh viên)");
                data.put("introMessage", "Đây là thư kiểm thử để xác nhận dịch vụ email CampusUTE đã phản hồi thành công.");
                data.put("statusBadge", "KẾT NỐI SMTP THÀNH CÔNG");
                data.put("smtpProtocol", "Gmail SMTP (Port 587 - TLS)");
                data.put("senderEmail", "no-reply@campuscore.local");
                data.put("timestamp", nowStr);
                data.put("actionUrl", "https://www.campusute.io.vn");
                data.put("actionText", "Truy cập Cổng Đào tạo CampusUTE");
            }
            case "academic-announcement" -> {
                data.put("category", "THÔNG BÁO HỌC VỤ");
                data.put("title", "Kế hoạch Tổ chức Đăng ký Học phần Học kỳ 1 Năm học 2026-2027");
                data.put("author", "Phòng Đào tạo - Đại học Công Nghệ Kĩ thuật TP.HCM");
                data.put("publishDate", dateStr);
                data.put("recipientName", "Toàn thể Sinh viên Đại học Chính quy");
                data.put("content", "Phòng Đào tạo thông báo kế hoạch mở cổng đăng ký học phần đợt 1 cho các khóa 2023, 2024, 2025. Sinh viên lưu ý kiểm tra chuẩn đầu vào môn tiên quyết và hoàn thành nghĩa vụ học phí đúng hạn.");
                data.put("highlights", List.of(
                        "Đợt 1 (Đúng tiến độ): Từ 08:00 ngày 20/09/2026 đến 17:00 ngày 25/09/2026",
                        "Đợt 2 (Đăng ký bổ sung & Học cải thiện): Từ ngày 28/09/2026 đến ngày 02/10/2026",
                        "Hạn chót nộp đơn phúc khảo và hủy môn: 17:00 ngày 10/10/2026"
                ));
                data.put("actionUrl", "https://www.campusute.io.vn/dashboard/course-registration");
                data.put("actionText", "Đến Trang Đăng Ký Học Phần");
            }
            case "course-registration" -> {
                data.put("subject", "[CampusUTE] Phiếu Xác nhận Đăng ký Học phần");
                data.put("semester", "Học kỳ 1 Năm học 2026-2027");
                data.put("studentName", "Nguyễn Tiến Sơn");
                data.put("studentId", "22110001");
                data.put("department", "Khoa Công nghệ Thông tin");
                data.put("timestamp", nowStr);
                data.put("totalCredits", 15);
                data.put("courses", List.of(
                        new CourseItem("SE013", "Lập trình Web nâng cao với React & Node.js", 3, "TS. Hoàng Văn Nam", "Thứ 2 (Tiết 1-3) - A103"),
                        new CourseItem("SE014", "Kiến trúc Microservices & Hệ thống Phân tán", 3, "PGS.TS. Trần Văn Bình", "Thứ 3 (Tiết 6-8) - A104"),
                        new CourseItem("SE015", "Phát triển Ứng dụng Di động Đa nền tảng", 3, "ThS. Lê Thị Mai", "Thứ 4 (Tiết 8-10) - A105"),
                        new CourseItem("SE016", "An toàn Thông tin & Mật mã Ứng dụng", 3, "TS. Phạm Thanh Tùng", "Thứ 5 (Tiết 1-3) - B202"),
                        new CourseItem("SE017", "Học máy Ứng dụng & Trí tuệ Nhân tạo", 3, "TS. Nguyễn Quốc Dũng", "Thứ 6 (Tiết 6-8) - C301")
                ));
            }
            case "two-factor-code" -> {
                data.put("subject", "[CampusUTE] Mã xác thực hai yếu tố");
                data.put("recipientName", "Jason");
                data.put("accountEmail", "j***@gmail.com");
                data.put("requestedAt", nowStr);
                data.put("code", "638585");
                data.put("expiryMinutes", 10);
            }
            case "grade-alert" -> {
                data.put("subject", "[CampusUTE] Bảng điểm Học kỳ & Điểm rèn luyện");
                data.put("semester", "Học kỳ 2 Năm học 2025-2026");
                data.put("studentName", "Nguyễn Tiến Sơn");
                data.put("studentId", "22110001");
                data.put("gpa4", 3.82);
                data.put("gpa10", 9.15);
                data.put("academicStanding", "XUẤT SẮC");
                data.put("conductScore", 95);
                data.put("conductRank", "XUẤT SẮC");
                data.put("grades", List.of(
                        new GradeItem("SE001", "Nhập môn Lập trình", 3, 9.5, "A+"),
                        new GradeItem("SE002", "Cấu trúc Dữ liệu & Giải thuật", 4, 9.0, "A+"),
                        new GradeItem("SE003", "Cơ sở Dữ liệu Quan hệ & NoSQL", 3, 8.8, "A"),
                        new GradeItem("SE004", "Công nghệ Phần mềm Nâng cao", 3, 9.2, "A+"),
                        new GradeItem("SE005", "Mạng Máy tính & Truyền thông", 3, 8.5, "B+")
                ));
            }
            default -> {
                data.put("subject", "CampusUTE Notification");
                data.put("recipientName", "Sinh viên");
            }
        }
        return data;
    }
}
