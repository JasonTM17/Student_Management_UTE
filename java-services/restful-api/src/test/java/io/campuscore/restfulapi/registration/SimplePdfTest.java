package io.campuscore.restfulapi.registration;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.MessageDigest;
import java.util.HexFormat;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

class SimplePdfTest {
    @Test
    void rendersDeterministicallyAndAsPdf() throws Exception {
        String source = "Phiếu đăng ký học phần\nSinh viên: Nguyễn Tiến Sơn\nMôn học: Cấu trúc dữ liệu\n";
        byte[] first = SimplePdf.render(source);
        byte[] second = SimplePdf.render(source);
        assertArrayEquals(first, second);
        assertTrue(new String(first, 0, 8, java.nio.charset.StandardCharsets.US_ASCII).startsWith("%PDF-1."));
        assertTrue(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(first)).length() == 64);
        try (var document = Loader.loadPDF(first)) {
            String extracted = new PDFTextStripper().getText(document);
            assertTrue(extracted.contains("Phiếu đăng ký học phần"));
            assertTrue(extracted.contains("Nguyễn Tiến Sơn"));
            assertTrue(extracted.contains("Cấu trúc dữ liệu"));
        }
    }

    @Test
    void paginatesLongSlipContent() throws Exception {
        String source = "Dòng tiếng Việt: Đăng ký học phần\n".repeat(160);
        byte[] pdf = SimplePdf.render(source);
        try (var document = Loader.loadPDF(pdf)) {
            assertTrue(document.getNumberOfPages() >= 3);
            assertTrue(new PDFTextStripper().getText(document).contains("Đăng ký học phần"));
        }
    }
}
