package io.campuscore.restfulapi.academic.registration;

import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

/** Minimal PDF bytes for a registration slip. No third-party renderer. */
@Component
public class RegistrationPdfRenderer {

    public byte[] render(String studentId, String semesterId, String enrollmentId, String sectionId) {
        String text = "CampusCore registration slip student="
                + safe(studentId)
                + " semester="
                + safe(semesterId)
                + " enrollment="
                + safe(enrollmentId)
                + " section="
                + safe(sectionId);
        String stream = "BT /F1 12 Tf 72 720 Td (" + text.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")
                + ") Tj ET\n";
        StringBuilder pdf = new StringBuilder();
        pdf.append("%PDF-1.4\n");
        pdf.append("1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n");
        pdf.append("2 0 obj<</Type/Pages/Count 1/Kids[3 0 R]>>endobj\n");
        pdf.append("3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 612 792]/Contents 4 0 R")
                .append("/Resources<</Font<</F1 5 0 R>>>>>>endobj\n");
        pdf.append("4 0 obj<</Length ")
                .append(stream.getBytes(StandardCharsets.US_ASCII).length)
                .append(">>stream\n")
                .append(stream)
                .append("endstream\nendobj\n");
        pdf.append("5 0 obj<</Type/Font/Subtype/Type1/BaseFont/Helvetica>>endobj\n");
        pdf.append("trailer<</Root 1 0 R>>\n%%EOF\n");
        return pdf.toString().getBytes(StandardCharsets.US_ASCII);
    }

    private static String safe(String value) {
        return value == null ? "" : value.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
