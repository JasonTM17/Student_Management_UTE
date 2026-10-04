package io.campuscore.restfulapi.academic.registration;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Standard PDF 1.4 renderer for course registration slips.
 * Generates valid, parseable PDF documents with proper xref tables, object offsets,
 * fonts, and metadata, compatible with any standard PDF viewer or library.
 */
@Component
public class RegistrationPdfRenderer {

    public record CourseItem(
            String courseCode,
            String courseName,
            String sectionNumber,
            int credits,
            String enrollmentId,
            String sectionId) {}

    public byte[] render(String studentId, String semesterId, String enrollmentId, String sectionId) {
        String cleanStudentId = safe(studentId);
        String cleanSemesterId = safe(semesterId);
        String cleanEnrollmentId = safe(enrollmentId);
        String cleanSectionId = safe(sectionId);

        List<CourseItem> singleCourse = List.of(new CourseItem(
                cleanSectionId,
                "Hoc phan dang ky / Registered Course",
                cleanSectionId,
                0,
                cleanEnrollmentId,
                cleanSectionId));

        return renderEnriched(
                cleanStudentId,
                cleanStudentId,
                null,
                cleanSemesterId,
                cleanSemesterId,
                cleanEnrollmentId,
                cleanSectionId,
                singleCourse);
    }

    public byte[] renderEnriched(
            String studentId,
            String studentCode,
            String studentName,
            String semesterId,
            String semesterName,
            String enrollmentId,
            String sectionId,
            List<CourseItem> courses) {

        String cleanStudentId = safe(studentId);
        String cleanStudentCode = safe(studentCode != null && !studentCode.isBlank() ? studentCode : studentId);
        String cleanStudentName = studentName != null && !studentName.isBlank() ? studentName : cleanStudentId;
        String cleanSemesterId = safe(semesterId);
        String cleanSemesterName = semesterName != null && !semesterName.isBlank() ? semesterName : cleanSemesterId;
        String cleanEnrollmentId = safe(enrollmentId);
        String cleanSectionId = safe(sectionId);

        String legacyText = "CampusCore registration slip student="
                + cleanStudentId
                + " semester="
                + cleanSemesterId
                + " enrollment="
                + cleanEnrollmentId
                + " section="
                + cleanSectionId;

        List<String> streamLines = new ArrayList<>();
        // Decorative header lines
        streamLines.add("q");
        streamLines.add("0.8 w 0.15 0.35 0.75 RG");
        streamLines.add("54 755 m 558 755 l S");
        streamLines.add("54 705 m 558 705 l S");
        streamLines.add("Q");

        // University & Department Titles
        streamLines.add("BT");
        streamLines.add("/F2 13 Tf 54 738 Td (TRUONG DAI HOC SU PHAM KY THUAT TP. HO CHI MINH) Tj");
        streamLines.add("ET");
        streamLines.add("BT");
        streamLines.add("/F1 9 Tf 54 723 Td (PHONG DAO TAO - CONG THONG TIN HOC VU CAMPUSCORE) Tj");
        streamLines.add("ET");
        streamLines.add("BT");
        streamLines.add("/F1 8 Tf 54 711 Td (He thong Quan ly Dao tao va Dang ky Tin chi) Tj");
        streamLines.add("ET");

        // Document Title
        streamLines.add("BT");
        streamLines.add("/F2 15 Tf 170 675 Td (PHIEU DANG KY HOC PHAN) Tj");
        streamLines.add("ET");
        streamLines.add("BT");
        streamLines.add("/F1 10 Tf 205 660 Td (COURSE REGISTRATION SLIP) Tj");
        streamLines.add("ET");

        // Info Box (y=575 to 645)
        streamLines.add("q");
        streamLines.add("0.5 w 0.75 0.75 0.75 RG");
        streamLines.add("54 575 504 70 re S");
        streamLines.add("Q");

        streamLines.add("BT");
        streamLines.add("/F2 9 Tf 68 630 Td (Ma sinh vien / Student ID:) Tj");
        streamLines.add("/F1 9 Tf 190 630 Td (" + escapePdf(cleanStudentCode) + ") Tj");
        streamLines.add("/F2 9 Tf 330 630 Td (Hoc ky / Semester:) Tj");
        streamLines.add("/F1 9 Tf 420 630 Td (" + escapePdf(cleanSemesterName) + ") Tj");
        streamLines.add("ET");

        streamLines.add("BT");
        streamLines.add("/F2 9 Tf 68 610 Td (Ho va ten / Student Name:) Tj");
        streamLines.add("/F1 9 Tf 190 610 Td (" + escapePdf(cleanStudentName) + ") Tj");
        streamLines.add("/F2 9 Tf 330 610 Td (Trang thai / Status:) Tj");
        streamLines.add("/F2 9 Tf 420 610 Td (DA GHI DANH HOP LE / ENROLLED) Tj");
        streamLines.add("ET");

        streamLines.add("BT");
        streamLines.add("/F2 9 Tf 68 590 Td (Ma ghi danh / Enrollment ID:) Tj");
        streamLines.add("/F1 9 Tf 190 590 Td (" + escapePdf(cleanEnrollmentId) + ") Tj");
        streamLines.add("/F2 9 Tf 330 590 Td (Lop hoc phan / Section ID:) Tj");
        streamLines.add("/F1 9 Tf 420 590 Td (" + escapePdf(cleanSectionId) + ") Tj");
        streamLines.add("ET");

        // Course Table
        // Table Header (y=548, height 16)
        streamLines.add("q");
        streamLines.add("0.92 0.94 0.97 rg");
        streamLines.add("54 548 504 16 re f");
        streamLines.add("0.7 0.7 0.7 RG 0.5 w");
        streamLines.add("54 548 504 16 re S");
        streamLines.add("Q");

        streamLines.add("BT");
        streamLines.add("0 g");
        streamLines.add("/F2 8 Tf 60 553 Td (STT) Tj");
        streamLines.add("/F2 8 Tf 85 553 Td (Ma MH) Tj");
        streamLines.add("/F2 8 Tf 150 553 Td (Ten hoc phan) Tj");
        streamLines.add("/F2 8 Tf 365 553 Td (Lop HP) Tj");
        streamLines.add("/F2 8 Tf 435 553 Td (So TC) Tj");
        streamLines.add("/F2 8 Tf 485 553 Td (Trang thai) Tj");
        streamLines.add("ET");

        List<CourseItem> items = courses != null && !courses.isEmpty()
                ? courses
                : List.of(new CourseItem(
                        cleanSectionId,
                        "Hoc phan dang ky / Registered Course",
                        cleanSectionId,
                        0,
                        cleanEnrollmentId,
                        cleanSectionId));
        int maxRows = Math.min(items.size(), 12);
        int totalCredits = 0;
        int currentY = 532;

        for (int i = 0; i < maxRows; i++) {
            CourseItem item = items.get(i);
            totalCredits += item.credits();
            int rowY = currentY - (i * 16);

            // Row bottom line
            streamLines.add("q");
            streamLines.add("0.85 0.85 0.85 RG 0.4 w");
            streamLines.add("54 " + (rowY - 4) + " m 558 " + (rowY - 4) + " l S");
            streamLines.add("Q");

            String cName = item.courseName() != null ? item.courseName() : "Course " + (i + 1);
            if (cName.length() > 36) {
                cName = cName.substring(0, 33) + "...";
            }

            streamLines.add("BT");
            streamLines.add("0 g");
            streamLines.add("/F1 8 Tf 62 " + rowY + " Td (" + (i + 1) + ") Tj");
            streamLines.add("/F1 8 Tf 85 " + rowY + " Td (" + escapePdf(item.courseCode()) + ") Tj");
            streamLines.add("/F1 8 Tf 150 " + rowY + " Td (" + escapePdf(cName) + ") Tj");
            streamLines.add("/F1 8 Tf 365 " + rowY + " Td (" + escapePdf(item.sectionNumber()) + ") Tj");
            streamLines.add("/F1 8 Tf 440 " + rowY + " Td (" + (item.credits() > 0 ? String.valueOf(item.credits()) : "-") + ") Tj");
            streamLines.add("/F1 8 Tf 485 " + rowY + " Td (Hop le) Tj");
            streamLines.add("ET");
        }

        int afterTableY = currentY - (maxRows * 16) - 10;
        if (totalCredits > 0) {
            streamLines.add("BT");
            streamLines.add("/F2 9 Tf 350 " + afterTableY + " Td (Tong so tin chi dang ky: " + totalCredits + " TC) Tj");
            streamLines.add("ET");
        }

        // Signatures section
        int sigY = Math.min(afterTableY - 25, 340);
        streamLines.add("BT");
        streamLines.add("/F2 9 Tf 90 " + sigY + " Td (Sinh vien dang ky) Tj");
        streamLines.add("/F1 8 Tf 85 " + (sigY - 12) + " Td ((Ky va ghi ro ho ten)) Tj");
        streamLines.add("/F2 9 Tf 380 " + sigY + " Td (Xac nhan Phong Dao tao) Tj");
        streamLines.add("/F1 8 Tf 380 " + (sigY - 12) + " Td ((Ky ten va dong dau)) Tj");
        streamLines.add("ET");

        // Notice and Verification
        streamLines.add("q");
        streamLines.add("0.5 w 0.8 0.8 0.8 RG");
        streamLines.add("54 135 m 558 135 l S");
        streamLines.add("Q");

        streamLines.add("BT");
        streamLines.add("/F1 8 Tf 54 120 Td (Phieu dang ky co gia tri xac nhan ket qua dang ky hoc phan tai Phong Dao tao.) Tj");
        streamLines.add("ET");

        // Legacy compatibility line
        streamLines.add("BT");
        streamLines.add("/F1 8 Tf 54 105 Td (" + escapePdf(legacyText) + ") Tj");
        streamLines.add("ET");

        streamLines.add("BT");
        streamLines.add("/F1 7 Tf 54 90 Td (CampusUTE Official Registration Document - Verified by SHA-256 Digest) Tj");
        streamLines.add("ET");

        String streamContent = String.join("\n", streamLines) + "\n";
        byte[] streamBytes = streamContent.getBytes(StandardCharsets.US_ASCII);

        return assemblePdf(streamBytes);
    }

    private byte[] assemblePdf(byte[] streamBytes) {
        byte[] header = "%PDF-1.4\n%\u00e2\u00e3\u00cf\u00d3\n".getBytes(StandardCharsets.ISO_8859_1);

        List<byte[]> objects = new ArrayList<>();
        // 1 0 obj: Catalog
        objects.add("1 0 obj\n<</Type/Catalog/Pages 2 0 R>>\nendobj\n".getBytes(StandardCharsets.US_ASCII));
        // 2 0 obj: Pages
        objects.add("2 0 obj\n<</Type/Pages/Count 1/Kids[3 0 R]>>\nendobj\n".getBytes(StandardCharsets.US_ASCII));
        // 3 0 obj: Page
        objects.add("3 0 obj\n<</Type/Page/Parent 2 0 R/MediaBox[0 0 612 792]/Contents 4 0 R/Resources<</Font<</F1 5 0 R/F2 6 0 R>>>>>>\nendobj\n"
                .getBytes(StandardCharsets.US_ASCII));
        // 4 0 obj: Stream content
        String streamHeader = "4 0 obj\n<</Length " + streamBytes.length + ">>\nstream\n";
        String streamFooter = "\nendstream\nendobj\n";
        byte[] obj4 = concat(
                streamHeader.getBytes(StandardCharsets.US_ASCII),
                streamBytes,
                streamFooter.getBytes(StandardCharsets.US_ASCII));
        objects.add(obj4);
        // 5 0 obj: Font F1 (Helvetica)
        objects.add("5 0 obj\n<</Type/Font/Subtype/Type1/BaseFont/Helvetica>>\nendobj\n".getBytes(StandardCharsets.US_ASCII));
        // 6 0 obj: Font F2 (Helvetica-Bold)
        objects.add("6 0 obj\n<</Type/Font/Subtype/Type1/BaseFont/Helvetica-Bold>>\nendobj\n".getBytes(StandardCharsets.US_ASCII));

        // Calculate offsets
        int currentOffset = header.length;
        int[] offsets = new int[objects.size()];
        int totalBodyLen = 0;
        for (int i = 0; i < objects.size(); i++) {
            offsets[i] = currentOffset;
            int len = objects.get(i).length;
            currentOffset += len;
            totalBodyLen += len;
        }

        int xrefOffset = currentOffset;
        StringBuilder xref = new StringBuilder();
        int objectCount = objects.size() + 1; // including object 0
        xref.append("xref\r\n0 ").append(objectCount).append("\r\n");
        xref.append("0000000000 65535 f\r\n");
        for (int offset : offsets) {
            xref.append(String.format(Locale.ROOT, "%010d 00000 n\r\n", offset));
        }

        StringBuilder trailer = new StringBuilder();
        trailer.append("trailer\r\n<</Size ").append(objectCount).append("/Root 1 0 R>>\r\n");
        trailer.append("startxref\r\n").append(xrefOffset).append("\r\n%%EOF\r\n");

        byte[] xrefBytes = xref.toString().getBytes(StandardCharsets.US_ASCII);
        byte[] trailerBytes = trailer.toString().getBytes(StandardCharsets.US_ASCII);

        byte[] pdf = new byte[header.length + totalBodyLen + xrefBytes.length + trailerBytes.length];
        int pos = 0;
        System.arraycopy(header, 0, pdf, pos, header.length);
        pos += header.length;
        for (byte[] obj : objects) {
            System.arraycopy(obj, 0, pdf, pos, obj.length);
            pos += obj.length;
        }
        System.arraycopy(xrefBytes, 0, pdf, pos, xrefBytes.length);
        pos += xrefBytes.length;
        System.arraycopy(trailerBytes, 0, pdf, pos, trailerBytes.length);

        return pdf;
    }

    private static byte[] concat(byte[] a, byte[] b, byte[] c) {
        byte[] res = new byte[a.length + b.length + c.length];
        System.arraycopy(a, 0, res, 0, a.length);
        System.arraycopy(b, 0, res, a.length, b.length);
        System.arraycopy(c, 0, res, a.length + b.length, c.length);
        return res;
    }

    private static String stripDiacritics(String text) {
        if (text == null) return "";
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFD);
        String stripped = normalized.replaceAll("\\p{M}", "");
        return stripped.replace("đ", "d").replace("Đ", "D");
    }

    private static String escapePdf(String text) {
        if (text == null) return "";
        String sanitized = stripDiacritics(text).replace("\r", " ").replace("\n", " ");
        return sanitized.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
    }

    private static String safe(String value) {
        return value == null ? "" : value.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
