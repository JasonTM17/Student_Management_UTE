package io.campuscore.restfulapi.academic.registration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class RegistrationPdfRendererTest {

    private final RegistrationPdfRenderer renderer = new RegistrationPdfRenderer();

    @Test
    void rendersValidPdfDocumentWithStandardStructure() {
        byte[] pdfBytes = renderer.render("student-123", "sem-2026-1", "enr-abc", "sec-xyz");

        assertThat(pdfBytes).isNotNull();
        assertThat(pdfBytes.length).isGreaterThan(100);

        String pdfString = new String(pdfBytes, StandardCharsets.ISO_8859_1);

        // Header check
        assertThat(pdfString).startsWith("%PDF-1.4\n");

        // Object checks
        assertThat(pdfString).contains("1 0 obj");
        assertThat(pdfString).contains("<</Type/Catalog/Pages 2 0 R>>");
        assertThat(pdfString).contains("2 0 obj");
        assertThat(pdfString).contains("<</Type/Pages/Count 1/Kids[3 0 R]>>");
        assertThat(pdfString).contains("3 0 obj");
        assertThat(pdfString).contains("/MediaBox[0 0 612 792]");
        assertThat(pdfString).contains("4 0 obj");
        assertThat(pdfString).contains("stream\n");
        assertThat(pdfString).contains("endstream");
        assertThat(pdfString).contains("5 0 obj");
        assertThat(pdfString).contains("6 0 obj");

        // Content checks
        assertThat(pdfString).contains("student-123");
        assertThat(pdfString).contains("sem-2026-1");
        assertThat(pdfString).contains("enr-abc");
        assertThat(pdfString).contains("sec-xyz");
        assertThat(pdfString).contains("CampusCore registration slip student=student-123 semester=sem-2026-1 enrollment=enr-abc section=sec-xyz");
        assertThat(pdfString).contains("TRUONG DAI HOC SU PHAM KY THUAT TP. HO CHI MINH");

        // Standard xref table and trailer checks
        assertThat(pdfString).contains("xref\r\n0 7\r\n");
        assertThat(pdfString).contains("0000000000 65535 f\r\n");
        assertThat(pdfString).contains("trailer\r\n<</Size 7/Root 1 0 R>>\r\n");
        assertThat(pdfString).contains("startxref\r\n");
        assertThat(pdfString).endsWith("%%EOF\r\n");

        // Verify exact byte offsets in xref
        int startxrefPos = pdfString.indexOf("startxref\r\n") + "startxref\r\n".length();
        int eofPos = pdfString.indexOf("\r\n%%EOF", startxrefPos);
        int xrefOffset = Integer.parseInt(pdfString.substring(startxrefPos, eofPos));
        assertThat(pdfString.indexOf("xref\r\n")).isEqualTo(xrefOffset);

        // Verify each object offset points to "X 0 obj"
        int xrefEntriesStart = pdfString.indexOf("0000000000 65535 f\r\n") + "0000000000 65535 f\r\n".length();
        for (int i = 1; i <= 6; i++) {
            String entry = pdfString.substring(xrefEntriesStart + (i - 1) * 20, xrefEntriesStart + i * 20);
            int objOffset = Integer.parseInt(entry.substring(0, 10));
            assertThat(pdfString.substring(objOffset, objOffset + 7)).isEqualTo(i + " 0 obj");
        }
    }

    @Test
    void handlesNullOrSpecialCharactersGracefully() {
        byte[] pdfBytes = renderer.render("sv/01 (special)", "sem\\fall", "enr (1)", "sec/A");
        assertThat(pdfBytes).isNotNull();
        String pdfString = new String(pdfBytes, StandardCharsets.ISO_8859_1);
        assertThat(pdfString).contains("%%EOF\r\n");
        assertThat(pdfString).contains("CampusCore registration slip");
    }

    @Test
    void generatedPdfCanBeParsedByStandardPdfParsers() throws Exception {
        byte[] pdfBytes = renderer.render("student-test", "semester-test", "enr-test", "sec-test");
        java.nio.file.Path tempFile = java.nio.file.Files.createTempFile("slip-test", ".pdf");
        try {
            java.nio.file.Files.write(tempFile, pdfBytes);
            Process process = new ProcessBuilder("python", "-c",
                    "import pypdf; reader = pypdf.PdfReader('" + tempFile.toString().replace("\\", "/") + "'); "
                            + "assert len(reader.pages) == 1; "
                            + "text = reader.pages[0].extract_text(); "
                            + "assert 'TRUONG DAI HOC SU PHAM KY THUAT' in text; "
                            + "print('Parsed successfully!')")
                    .redirectErrorStream(true)
                    .start();
            int exitCode = process.waitFor();
            assertThat(exitCode).isEqualTo(0);
        } finally {
            java.nio.file.Files.deleteIfExists(tempFile);
        }
    }

    @Test
    void rendersEnrichedSlipWithMultipleCoursesAndVietnameseText() throws Exception {
        java.util.List<RegistrationPdfRenderer.CourseItem> courses = java.util.List.of(
                new RegistrationPdfRenderer.CourseItem("SE402", "Phát triển ứng dụng Web", "01", 3, "enr-1", "sec-1"),
                new RegistrationPdfRenderer.CourseItem("CS201", "Cấu trúc dữ liệu & Giải thuật", "02", 4, "enr-2", "sec-2"),
                new RegistrationPdfRenderer.CourseItem("MA101", "Đại số tuyến tính", "01", 3, "enr-3", "sec-3")
        );

        byte[] pdfBytes = renderer.renderEnriched(
                "stu-01",
                "22110001",
                "Nguyễn Văn An",
                "sem-2026-1",
                "Học kỳ 1 Năm học 2026 - 2027",
                "enr-1",
                "sec-1",
                courses
        );

        assertThat(pdfBytes).isNotNull();
        String pdfString = new String(pdfBytes, StandardCharsets.ISO_8859_1);
        assertThat(pdfString).startsWith("%PDF-1.4\n");
        assertThat(pdfString).contains("22110001");
        assertThat(pdfString).contains("Nguyen Van An");
        assertThat(pdfString).contains("Hoc ky 1 Nam hoc 2026 - 2027");
        assertThat(pdfString).contains("Phat trien ung dung Web");
        assertThat(pdfString).contains("Cau truc du lieu");
        assertThat(pdfString).contains("Dai so tuyen tinh");
        assertThat(pdfString).contains("Tong so tin chi dang ky: 10 TC");

        // Parse with pypdf
        java.nio.file.Path tempFile = java.nio.file.Files.createTempFile("enriched-slip-test", ".pdf");
        try {
            java.nio.file.Files.write(tempFile, pdfBytes);
            Process process = new ProcessBuilder("python", "-c",
                    "import pypdf; reader = pypdf.PdfReader('" + tempFile.toString().replace("\\", "/") + "'); "
                            + "assert len(reader.pages) == 1; "
                            + "text = reader.pages[0].extract_text(); "
                            + "assert '22110001' in text; "
                            + "assert 'Nguyen Van An' in text; "
                            + "assert 'Phat trien ung dung Web' in text; "
                            + "assert '10 TC' in text; "
                            + "print('Enriched slip parsed successfully!')")
                    .redirectErrorStream(true)
                    .start();
            int exitCode = process.waitFor();
            assertThat(exitCode).isEqualTo(0);
        } finally {
            java.nio.file.Files.deleteIfExists(tempFile);
        }
    }
}
