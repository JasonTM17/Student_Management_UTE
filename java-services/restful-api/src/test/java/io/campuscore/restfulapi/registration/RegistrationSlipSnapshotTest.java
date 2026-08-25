package io.campuscore.restfulapi.registration;

import static org.assertj.core.api.Assertions.assertThat;

import io.campuscore.restfulapi.academic.persistence.RegistrationSlipEntity;
import java.time.Instant;
import java.util.List;
import java.util.Base64;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

class RegistrationSlipSnapshotTest {
    @Test
    void payloadIsStableAndExistingSnapshotCanBeReused() {
        byte[] pdf = SimplePdf.render("Phiếu đăng ký ổn định\n");
        String payload = Base64.getEncoder().encodeToString(pdf);
        String hash = sha256(pdf);
        RegistrationSlipEntity slip = RegistrationSlipEntity.snapshot("slip-1", "student-1", "round-1",
                hash, payload, Instant.EPOCH);
        assertThat(slip.getSnapshotPayload()).isEqualTo(payload);
        slip.storePayload(payload, hash);
        assertThat(slip.getSnapshotPayload()).isEqualTo(payload);
    }

    @Test
    void legacyPayloadBackfillCannotChangeTheCanonicalChecksum() {
        RegistrationSlipEntity slip = RegistrationSlipEntity.snapshot("slip-1", "student-1", "round-1",
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef", Instant.EPOCH);
        String malformedPayload = Base64.getEncoder().encodeToString("not-a-pdf".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> slip.storePayload(malformedPayload,
                "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("REGISTRATION_SLIP_HASH_MISMATCH");
        assertThat(slip.getSnapshotPayload()).isNull();
    }

    @Test
    void slipChecksumMatchesDownloadedPdfBytes() throws Exception {
        String canonical = "Phiếu đăng ký học phần\nVòng đăng ký: round-1\n";
        byte[] pdf = SimplePdf.render(canonical);
        String checksum = sha256(pdf);
        assertThat(new RegistrationService.SlipResult(pdf, checksum).checksum()).isEqualTo(checksum);
        try (var document = Loader.loadPDF(pdf)) {
            assertThat(new PDFTextStripper().getText(document)).contains("Phiếu đăng ký học phần");
        }
    }

    @Test
    void dropReplayCarriesTypedFlag() {
        RegistrationService.DropResult replay = new RegistrationService.DropResult("enrollment-1", true);
        assertThat(replay.replayed()).isTrue();
        assertThat(replay.enrollmentId()).isEqualTo("enrollment-1");
    }

    @Test
    void canonicalSlipIncludesIdentityLecturerRoomAndEveryScheduleInStableOrder() {
        RegistrationDtos.RoundView round = new RegistrationDtos.RoundView("round-1", "semester-1", "OPEN",
                Instant.parse("2026-08-01T00:00:00Z"), Instant.parse("2026-09-01T00:00:00Z"),
                Instant.parse("2026-08-01T00:00:00Z"), Instant.parse("2026-09-15T00:00:00Z"),
                Instant.parse("2026-08-24T00:00:00Z"), "Asia/Ho_Chi_Minh", 28, 0L);
        RegistrationDtos.SectionView section = new RegistrationDtos.SectionView("section-1", "course-1", "CS101",
                "Algorithms", 3, "01", "Dr. Example", "A1 203", 40, 12, 28, "OPEN", true,
                List.of(new RegistrationDtos.ScheduleView("slot-2", 3, java.time.LocalTime.of(13, 0), java.time.LocalTime.of(15, 0), "room-2", "A1", "203"),
                        new RegistrationDtos.ScheduleView("slot-1", 1, java.time.LocalTime.of(7, 0), java.time.LocalTime.of(9, 0), "room-1", "A1", "203")), List.of());
        RegistrationDtos.EnrollmentView enrollment = new RegistrationDtos.EnrollmentView("enrollment-1", "section-1", "round-1", "ENROLLED", Instant.EPOCH, section);

        String canonical = RegistrationService.canonicalSlip("SV001", round, List.of(enrollment), Instant.EPOCH);
        org.junit.jupiter.api.Assertions.assertTrue(canonical.contains("Student: SV001"));
        org.junit.jupiter.api.Assertions.assertTrue(canonical.contains("Lecturer: Dr. Example"));
        org.junit.jupiter.api.Assertions.assertTrue(canonical.contains("Schedule: day=1 07:00-09:00"));
        org.junit.jupiter.api.Assertions.assertTrue(canonical.contains("Schedule: day=3 13:00-15:00"));
        org.junit.jupiter.api.Assertions.assertTrue(canonical.indexOf("day=1") < canonical.indexOf("day=3"));
    }

    private static String sha256(String value) {
        return sha256(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static String sha256(byte[] value) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
