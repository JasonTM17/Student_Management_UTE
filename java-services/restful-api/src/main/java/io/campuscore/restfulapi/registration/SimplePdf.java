package io.campuscore.restfulapi.registration;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

/** Deterministic, paginated PDF renderer with an embedded Vietnamese font subset. */
final class SimplePdf {
    private static final String FONT_RESOURCE = "/fonts/NotoSans-Vietnamese-subset.ttf.b64";
    private static final float FONT_SIZE = 10f;
    private static final float LEADING = 14f;
    private static final float MARGIN = 48f;
    private static final float MAX_TEXT_WIDTH = PDRectangle.A4.getWidth() - (2 * MARGIN);

    private SimplePdf() { }

    static byte[] render(String text) {
        String normalized = text == null ? "" : text.replace("\r", "");
        try (PDDocument document = new PDDocument();
             InputStream encodedFont = SimplePdf.class.getResourceAsStream(FONT_RESOURCE)) {
            if (encodedFont == null) throw new IllegalStateException("Registration PDF font resource is missing");
            byte[] fontBytes = Base64.getMimeDecoder().decode(encodedFont.readAllBytes());
            PDType0Font font = PDType0Font.load(document, new ByteArrayInputStream(fontBytes), true);
            List<String> lines = wrap(font, normalized);
            int linesPerPage = Math.max(1, (int) ((PDRectangle.A4.getHeight() - (2 * MARGIN)) / LEADING));

            for (int offset = 0; offset < Math.max(1, lines.size()); offset += linesPerPage) {
                PDPage page = new PDPage(PDRectangle.A4);
                document.addPage(page);
                int end = Math.min(lines.size(), offset + linesPerPage);
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    content.beginText();
                    content.setFont(font, FONT_SIZE);
                    content.newLineAtOffset(MARGIN, PDRectangle.A4.getHeight() - MARGIN);
                    if (lines.isEmpty()) {
                        content.showText("");
                    } else {
                        for (int index = offset; index < end; index++) {
                            content.showText(lines.get(index));
                            content.newLineAtOffset(0, -LEADING);
                        }
                    }
                    content.endText();
                }
            }

            document.getDocumentInformation().setProducer("CampusCore");
            document.getDocument().setDocumentID(documentId(normalized));
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.save(output);
            return output.toByteArray();
        } catch (IOException | IllegalArgumentException error) {
            throw new IllegalStateException("Registration PDF rendering failed", error);
        }
    }

    private static List<String> wrap(PDType0Font font, String text) throws IOException {
        List<String> result = new ArrayList<>();
        for (String rawLine : text.split("\n", -1)) {
            String line = supported(font, rawLine);
            if (line.isEmpty()) {
                result.add("");
                continue;
            }
            StringBuilder current = new StringBuilder();
            for (String word : line.split(" ", -1)) {
                String candidate = current.isEmpty() ? word : current + " " + word;
                if (!current.isEmpty() && width(font, candidate) > MAX_TEXT_WIDTH) {
                    result.add(current.toString());
                    current.setLength(0);
                }
                appendWrappedWord(font, result, current, word);
            }
            result.add(current.toString());
        }
        return result;
    }

    private static void appendWrappedWord(PDType0Font font, List<String> lines,
                                          StringBuilder current, String word) throws IOException {
        if (current.isEmpty() && width(font, word) <= MAX_TEXT_WIDTH) {
            current.append(word);
            return;
        }
        if (!current.isEmpty() && width(font, current + " " + word) <= MAX_TEXT_WIDTH) {
            current.append(' ').append(word);
            return;
        }
        for (int offset = 0; offset < word.length();) {
            int codePoint = word.codePointAt(offset);
            String character = new String(Character.toChars(codePoint));
            if (!current.isEmpty() && width(font, current + character) > MAX_TEXT_WIDTH) {
                lines.add(current.toString());
                current.setLength(0);
            }
            current.append(character);
            offset += Character.charCount(codePoint);
        }
    }

    private static float width(PDType0Font font, CharSequence text) throws IOException {
        return font.getStringWidth(text.toString()) * FONT_SIZE / 1000f;
    }

    private static String supported(PDType0Font font, String value) throws IOException {
        StringBuilder result = new StringBuilder(value.length());
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            String character = new String(Character.toChars(codePoint));
            try {
                font.encode(character);
                result.append(character);
            } catch (IllegalArgumentException unsupported) {
                result.append('?');
            }
            offset += Character.charCount(codePoint);
        }
        return result.toString();
    }

    private static COSArray documentId(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            COSString id = new COSString(Arrays.copyOf(digest, 16));
            COSArray ids = new COSArray();
            ids.add(id);
            ids.add(id);
            return ids;
        } catch (Exception impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
