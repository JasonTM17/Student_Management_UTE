package io.campuscore.restfulapi.registration;

import static org.assertj.core.api.Assertions.assertThat;

import io.campuscore.restfulapi.academic.persistence.RegistrationSlipEntity;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class RegistrationSlipSnapshotTest {
    @Test
    void payloadIsStableAndExistingSnapshotCanBeReused() {
        String payload = Base64.getEncoder().encodeToString("%PDF-1.4\ncanonical".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        RegistrationSlipEntity slip = RegistrationSlipEntity.snapshot("slip-1", "student-1", "round-1",
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef", payload, Instant.EPOCH);
        assertThat(slip.getSnapshotPayload()).isEqualTo(payload);
        slip.storePayload("different", "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        assertThat(slip.getSnapshotPayload()).isEqualTo(payload);
    }

    @Test
    void legacyPayloadBackfillCannotChangeTheCanonicalChecksum() {
        RegistrationSlipEntity slip = RegistrationSlipEntity.snapshot("slip-1", "student-1", "round-1",
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef", Instant.EPOCH);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> slip.storePayload("payload",
                "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("REGISTRATION_SLIP_HASH_MISMATCH");
        assertThat(slip.getSnapshotPayload()).isNull();
    }

    @Test
    void slipChecksumPrintedInPdfMatchesPublicHeaderValue() {
        String canonical = "CampusCore Registration Slip\nround-1\n";
        String checksum = sha256(canonical);
        byte[] pdf = SimplePdf.render(canonical + "SHA-256: " + checksum + "\n");
        assertThat(new String(pdf, java.nio.charset.StandardCharsets.US_ASCII)).contains(checksum);
        assertThat(new RegistrationService.SlipResult(pdf, checksum).checksum()).isEqualTo(checksum);
    }

    @Test
    void dropReplayCarriesTypedFlag() {
        RegistrationService.DropResult replay = new RegistrationService.DropResult("enrollment-1", true);
        assertThat(replay.replayed()).isTrue();
        assertThat(replay.enrollmentId()).isEqualTo("enrollment-1");
    }

    private static String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
