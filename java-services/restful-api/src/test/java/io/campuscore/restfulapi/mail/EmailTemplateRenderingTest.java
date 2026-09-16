package io.campuscore.restfulapi.mail;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.campuscore.restfulapi.mail.web.MailDtos.CourseItem;
import io.campuscore.restfulapi.mail.web.MailDtos.GradeItem;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

class EmailTemplateRenderingTest {

    private SpringTemplateEngine templateEngine;

    @BeforeEach
    void setUp() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(false);

        templateEngine = new SpringTemplateEngine();
        templateEngine.setTemplateResolver(resolver);
    }

    @Test
    void rendersTestVerificationTemplate() {
        Context context = new Context(Locale.forLanguageTag("vi"));
        Map<String, Object> vars = new HashMap<>();
        vars.put("subject", "CampusUTE - Xác nhận kiểm thử dịch vụ Email");
        vars.put("recipientName", "Nguyễn Tiến Sơn");
        vars.put("introMessage", "Dịch vụ gửi email SMTP qua Google Cloud Engine đã kết nối thành công.");
        vars.put("statusBadge", "● KẾT NỐI SMTP THÀNH CÔNG");
        vars.put("smtpProtocol", "Gmail SMTP (Port 587 - TLS)");
        vars.put("senderEmail", "conbocuoi1721@gmail.com");
        vars.put("timestamp", "09:00:00 15/09/2026");
        vars.put("actionUrl", "https://www.campusute.io.vn");
        vars.put("actionText", "Truy cập Cổng Đào tạo");
        context.setVariables(vars);

        String html = templateEngine.process("mail/test-verification", context);

        assertNotNull(html);
        assertTrue(html.contains("CampusUTE Academic"));
        assertTrue(html.contains("Nguyễn Tiến Sơn"));
        assertTrue(html.contains("KẾT NỐI SMTP THÀNH CÔNG"));
        assertTrue(html.contains("conbocuoi1721@gmail.com"));
        assertTrue(html.contains("TRƯỜNG ĐẠI HỌC CÔNG NGHỆ KĨ THUẬT TP. HỒ CHÍ MINH") || html.contains("CÔNG NGHỆ KĨ THUẬT"));
    }

    @Test
    void rendersAcademicAnnouncementTemplate() {
        Context context = new Context(Locale.forLanguageTag("vi"));
        Map<String, Object> vars = new HashMap<>();
        vars.put("category", "THÔNG BÁO HỌC VỤ");
        vars.put("title", "Kế hoạch Đăng ký Học phần Học kỳ 1");
        vars.put("author", "Phòng Đào tạo HCMUTE");
        vars.put("publishDate", "15/09/2026");
        vars.put("recipientName", "Sinh viên Nguyễn Tiến Sơn");
        vars.put("content", "Phòng Đào tạo thông báo kế hoạch mở cổng đăng ký học phần đợt 1.");
        vars.put("highlights", List.of("Đợt 1: từ 20/09 đến 25/09/2026", "Hạn nộp học phí: 10/10/2026"));
        vars.put("actionUrl", "https://www.campusute.io.vn/dashboard/announcements");
        vars.put("actionText", "Xem chi tiết");
        context.setVariables(vars);

        String html = templateEngine.process("mail/academic-announcement", context);

        assertNotNull(html);
        assertTrue(html.contains("THÔNG BÁO HỌC VỤ"));
        assertTrue(html.contains("Kế hoạch Đăng ký Học phần Học kỳ 1"));
        assertTrue(html.contains("Đợt 1: từ 20/09 đến 25/09/2026"));
        assertTrue(html.contains("Phòng Đào tạo"));
    }

    @Test
    void academicAnnouncementTemplateEscapesInlineHtml() {
        // The announcement body is authored as rich text by admins; the mail
        // render must treat it as text so a stored <script> can never execute
        // in a mail client.
        Context context = new Context(Locale.forLanguageTag("vi"));
        Map<String, Object> vars = new HashMap<>();
        vars.put("category", "THÔNG BÁO HỌC VỤ");
        vars.put("title", "Kiểm tra escape <script>alert('xss')</script>");
        vars.put("author", "Phòng Đào tạo HCMUTE");
        vars.put("publishDate", "15/09/2026");
        vars.put("recipientName", "Sinh viên Nguyễn Tiến Sơn");
        vars.put("content", "<script>alert('stored-xss')</script><img src=x onerror=alert(1)>Nội dung bình thường.");
        vars.put("highlights", List.of("Đợt 1: từ 20/09 đến 25/09/2026"));
        vars.put("actionUrl", "https://www.campusute.io.vn/dashboard/announcements");
        vars.put("actionText", "Xem chi tiết");
        context.setVariables(vars);

        String html = templateEngine.process("mail/academic-announcement", context);

        assertNotNull(html);
        assertFalse(html.contains("<script>alert('stored-xss')"), "inline script must be escaped, not rendered");
        assertFalse(html.contains("<img src=x onerror="), "event-handler HTML must be escaped, not rendered");
        assertTrue(html.contains("&lt;script&gt;"), "the payload must appear as escaped text");
        assertTrue(html.contains("Nội dung bình thường."));
    }

    @Test
    void rendersCourseRegistrationTemplate() {
        Context context = new Context(Locale.forLanguageTag("vi"));
        Map<String, Object> vars = new HashMap<>();
        vars.put("subject", "[CampusUTE] Phiếu Xác nhận Đăng ký Học phần");
        vars.put("semester", "Học kỳ 1 Năm học 2026-2027");
        vars.put("studentName", "Nguyễn Tiến Sơn");
        vars.put("studentId", "22110001");
        vars.put("department", "Khoa Công nghệ Thông tin");
        vars.put("timestamp", "15/09/2026 09:15");
        vars.put("totalCredits", 6);
        vars.put("courses", List.of(
                new CourseItem("SE013", "Lập trình Web nâng cao", 3, "TS. Hoàng Văn Nam", "Thứ 2 (1-3)"),
                new CourseItem("SE014", "Kiến trúc Microservices", 3, "PGS.TS. Trần Văn Bình", "Thứ 3 (6-8)")
        ));
        context.setVariables(vars);

        String html = templateEngine.process("mail/course-registration", context);

        assertNotNull(html);
        assertTrue(html.contains("PHIẾU XÁC NHẬN ĐĂNG KÝ HỌC PHẦN"));
        assertTrue(html.contains("Nguyễn Tiến Sơn"));
        assertTrue(html.contains("22110001"));
        assertTrue(html.contains("SE013"));
        assertTrue(html.contains("Lập trình Web nâng cao"));
        assertTrue(html.contains("6 TÍN CHỈ"));
    }

    @Test
    void rendersGradeAlertTemplate() {
        Context context = new Context(Locale.forLanguageTag("vi"));
        Map<String, Object> vars = new HashMap<>();
        vars.put("subject", "[CampusUTE] Bảng điểm Học kỳ");
        vars.put("semester", "Học kỳ 2 Năm học 2025-2026");
        vars.put("studentName", "Nguyễn Tiến Sơn");
        vars.put("studentId", "22110001");
        vars.put("gpa4", 3.85);
        vars.put("gpa10", 9.20);
        vars.put("academicStanding", "XUẤT SẮC");
        vars.put("conductScore", 96);
        vars.put("conductRank", "XUẤT SẮC");
        vars.put("grades", List.of(
                new GradeItem("SE001", "Nhập môn Lập trình", 3, 9.5, "A+"),
                new GradeItem("SE002", "Cấu trúc Dữ liệu", 4, 9.0, "A+")
        ));
        context.setVariables(vars);

        String html = templateEngine.process("mail/grade-alert", context);

        assertNotNull(html);
        assertTrue(html.contains("KẾT QUẢ HỌC VỤ & RÈN LUYỆN")
                || html.contains("KẾT QUẢ HỌC VỤ &amp; RÈN LUYỆN"));
        assertTrue(html.contains("3.85") || html.contains("3,85"));
        assertTrue(html.contains("9.20") || html.contains("9,20"));
        assertTrue(html.contains("96"));
        assertTrue(html.contains("SE001"));
        assertTrue(html.contains("Nhập môn Lập trình"));
    }

    @Test
    void emailTemplatesShareResponsiveEmailSafeLayout() throws IOException {
        String fragments = readTemplate("fragments.html");

        assertTrue(fragments.contains("@media screen and (max-width: 640px)"));
        assertTrue(fragments.contains("prefers-reduced-motion"));
        assertFalse(fragments.contains("linear-gradient"));
        assertFalse(fragments.contains("display: flex"));
        assertFalse(fragments.contains("backdrop-filter"));

        for (String templateName : List.of(
                "academic-announcement.html",
                "course-registration.html",
                "grade-alert.html",
                "test-verification.html")) {
            String template = readTemplate(templateName);
            assertTrue(template.contains("th:replace=\"~{mail/fragments :: emailHead"), templateName);
            assertTrue(template.contains("role=\"presentation\""), templateName);
            assertFalse(template.contains("linear-gradient"), templateName);
            assertFalse(template.contains("display: flex"), templateName);
            assertFalse(template.contains("backdrop-filter"), templateName);
            assertFalse(template.contains("📌"), templateName);
            assertFalse(template.contains("📎"), templateName);
            assertFalse(template.contains("🔒"), templateName);
        }
    }

    private String readTemplate(String templateName) throws IOException {
        String resourceName = "templates/mail/" + templateName;
        try (var input = getClass().getClassLoader().getResourceAsStream(resourceName)) {
            assertNotNull(input, resourceName);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
