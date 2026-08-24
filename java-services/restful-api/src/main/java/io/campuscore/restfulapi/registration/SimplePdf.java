package io.campuscore.restfulapi.registration;

import java.nio.charset.StandardCharsets;

/** Tiny dependency-free PDF writer for deterministic registration slips. */
final class SimplePdf {
    private SimplePdf() { }

    static byte[] render(String text) {
        String safe = text.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)").replace("\r", "");
        String stream = "BT /F1 10 Tf 48 780 Td 14 TL (" + safe.replace("\n", ") Tj T* (") + ") Tj ET";
        String[] objects = {
            "<< /Type /Catalog /Pages 2 0 R >>",
            "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 5 0 R >> >> /Contents 4 0 R >>",
            "<< /Length " + stream.getBytes(StandardCharsets.US_ASCII).length + " >>\nstream\n" + stream + "\nendstream",
            "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>"
        };
        StringBuilder out = new StringBuilder("%PDF-1.4\n");
        int[] offsets = new int[objects.length + 1];
        for (int i = 0; i < objects.length; i++) { offsets[i + 1] = out.length(); out.append(i + 1).append(" 0 obj\n").append(objects[i]).append("\nendobj\n"); }
        int xref = out.length();
        out.append("xref\n0 ").append(objects.length + 1).append("\n0000000000 65535 f \n");
        for (int i = 1; i < offsets.length; i++) out.append(String.format("%010d 00000 n \n", offsets[i]));
        out.append("trailer\n<< /Size ").append(offsets.length).append(" /Root 1 0 R >>\nstartxref\n").append(xref).append("\n%%EOF\n");
        return out.toString().getBytes(StandardCharsets.US_ASCII);
    }
}
