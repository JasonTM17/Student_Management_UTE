package io.campuscore.restfulapi.thesis.service;

import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
import io.campuscore.restfulapi.exception.AppException;
import io.campuscore.restfulapi.web.DomainException;

/**
 * Feedback item 7: the report artifact may be an attached Word/PDF file.
 * Validation is content-based, not trust-based: the extension whitelist and
 * the magic-byte signature must agree, the size cap is enforced before the
 * bytes reach the database, and the client-supplied name is sanitized to a
 * display-only string (it is never used to build a storage path because the
 * storage key is generated server-side).
 */
public final class ReportFilePolicy {

    /** 20 MB — matches the servlet multipart cap configured for the API. */
    public static final long MAX_FILE_BYTES = 20L * 1024 * 1024;

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "doc", "docx");

    private ReportFilePolicy() {
    }

    public record ValidatedFile(String fileName, String contentType, String extension, long size, byte[] data) {
    }

    public static ValidatedFile validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new DomainException(HttpStatus.BAD_REQUEST, "FILE_REQUIRED",
                    "Attach the report document (.pdf, .doc or .docx)");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new DomainException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE",
                    "The report document must be 20 MB or smaller");
        }
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String extension = extensionOf(original);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new DomainException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_FILE_TYPE",
                    "Only .pdf, .doc and .docx report documents are accepted");
        }
        byte[] data;
        try {
            data = file.getBytes();
        } catch (java.io.IOException exception) {
            throw new DomainException(HttpStatus.BAD_REQUEST, "FILE_UNREADABLE",
                    "The uploaded document could not be read");
        }
        if (data.length > MAX_FILE_BYTES) {
            throw new DomainException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE",
                    "The report document must be 20 MB or smaller");
        }
        if (!signatureMatches(extension, data)) {
            throw new DomainException(HttpStatus.BAD_REQUEST, "INVALID_FILE_CONTENT",
                    "The document content does not match its .%s extension".formatted(extension));
        }
        return new ValidatedFile(sanitizeName(original), typeFor(extension), extension, data.length, data);
    }

    /** Display-only name: single path segment, printable, capped, extension preserved. */
    static String sanitizeName(String original) {
        String name = original;
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        StringBuilder cleaned = new StringBuilder();
        for (char candidate : name.toCharArray()) {
            if (candidate >= 32 && candidate != '"' && candidate != '\'' && candidate != '<' && candidate != '>'
                    && candidate != '&' && cleaned.length() < 200) {
                cleaned.append(candidate);
            }
        }
        String result = cleaned.toString().trim();
        if (result.isEmpty()) {
            result = "report." + extensionOf(original);
        }
        return result;
    }

    private static String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static boolean signatureMatches(String extension, byte[] data) {
        return switch (extension) {
            case "pdf" -> startsWith(data, new byte[] {'%', 'P', 'D', 'F'});
            // DOCX and every other OOXML part is a ZIP container.
            case "docx" -> startsWith(data, new byte[] {0x50, 0x4B, 0x03, 0x04})
                    || startsWith(data, new byte[] {0x50, 0x4B, 0x05, 0x06});
            // Legacy Word 97-2003 is an OLE2 compound file.
            case "doc" -> startsWith(data, new byte[] {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0,
                    (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1});
            default -> false;
        };
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int index = 0; index < prefix.length; index++) {
            if (data[index] != prefix[index]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Wukong finding: the client-controlled Content-Type was stored verbatim
     * and echoed on download, so a garbage value could break the download and
     * text/html leaned on disposition alone. The type now derives from the
     * validated extension and the browser's declared type is ignored.
     */
    private static String typeFor(String extension) {
        return switch (extension) {
            case "pdf" -> "application/pdf";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            default -> "application/msword";
        };
    }

    static AppException unsupported(String message) {
        return new DomainException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_FILE_TYPE", message);
    }
}
