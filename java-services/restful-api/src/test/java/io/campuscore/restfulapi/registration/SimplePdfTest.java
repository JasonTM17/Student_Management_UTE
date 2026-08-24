package io.campuscore.restfulapi.registration;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class SimplePdfTest {
    @Test
    void rendersDeterministicallyAndAsPdf() throws Exception {
        byte[] first = SimplePdf.render("CampusCore\nSHA-256: abc\n");
        byte[] second = SimplePdf.render("CampusCore\nSHA-256: abc\n");
        assertArrayEquals(first, second);
        assertTrue(new String(first, 0, 8, java.nio.charset.StandardCharsets.US_ASCII).startsWith("%PDF-1."));
        assertTrue(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(first)).length() == 64);
    }
}
