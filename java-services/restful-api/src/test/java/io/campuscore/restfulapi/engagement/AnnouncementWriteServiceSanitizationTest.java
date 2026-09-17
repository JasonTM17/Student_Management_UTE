package io.campuscore.restfulapi.engagement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.campuscore.restfulapi.engagement.repository.AnnouncementAuditRepository;
import io.campuscore.restfulapi.engagement.repository.AnnouncementAuditRepository.AuditCommand;
import io.campuscore.restfulapi.engagement.repository.AnnouncementWriteRepository;
import io.campuscore.restfulapi.engagement.repository.AnnouncementWriteRepository.CreateAnnouncementCommand;
import io.campuscore.restfulapi.engagement.repository.AnnouncementWriteRepository.UpdateAnnouncementCommand;
import io.campuscore.restfulapi.engagement.service.AnnouncementWriteService;
import io.campuscore.restfulapi.engagement.web.AnnouncementReadDtos.AnnouncementResponse;
import io.campuscore.restfulapi.engagement.web.AnnouncementWriteDtos.CreateAnnouncementRequest;
import io.campuscore.restfulapi.engagement.web.AnnouncementWriteDtos.UpdateAnnouncementRequest;
import io.campuscore.restfulapi.web.DomainException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * RT-P2-5 write-boundary proving tests: the sanitizer runs on create/update
 * only (history is never backfilled), the stored value is the sanitized one,
 * the removed-element count is observable in the audit reason, and the length
 * cap the client derives its image budget from still holds.
 */
class AnnouncementWriteServiceSanitizationTest {

    @Test
    void markdownSchemeBypassPayloadIsStoredNeutralizedNotRejected() {
        // `java\tscript:` evades the ACTIVE_CONTENT blacklist (interior tab)
        // but must not reach the database with its href intact.
        Fixture fixture = new Fixture();
        when(fixture.announcements.create(any())).thenReturn(announcement());
        CreateAnnouncementRequest request = new CreateAnnouncementRequest(
                "Notice", "<a href=\"java\tscript:alert(1)\">x</a>", "NORMAL",
                List.of("STUDENT"), List.of(), false, null, null, null, null, null);

        fixture.service.create("user-1", "Admin", List.of("ADMIN"), null, request);

        ArgumentCaptor<CreateAnnouncementCommand> captor =
                ArgumentCaptor.forClass(CreateAnnouncementCommand.class);
        verify(fixture.announcements).create(captor.capture());
        assertFalse(captor.getValue().content().toLowerCase().contains("javascript"),
                () -> "unsanitized content stored: " + captor.getValue().content());
    }

    @Test
    void updateReportsRemovedElementsInTheAuditReason() {
        Fixture fixture = new Fixture();
        when(fixture.announcements.findByIdForUpdate("notice-1"))
                .thenReturn(Optional.of(announcement()));
        when(fixture.announcements.update(any())).thenReturn(1);
        when(fixture.announcements.findById("notice-1")).thenReturn(Optional.of(announcement()));
        UpdateAnnouncementRequest request = new UpdateAnnouncementRequest(
                Set.of("content", "reason", "expectedVersion"), null,
                // Chosen to evade the ACTIVE_CONTENT blacklist (interior tab in
                // the scheme, unknown tag) so the sanitize path is exercised;
                // an <iframe> would be hard-rejected before sanitizing.
                "<p>nội dung</p><xss>độc</xss><a href=\"java\tscript:alert(1)\">x</a>",
                null, null, null, null, null, null, null, null, null, "Correction", 0);

        fixture.service.update("user-1", "Admin", List.of("ADMIN"), null, "notice-1", request);

        ArgumentCaptor<UpdateAnnouncementCommand> updateCaptor =
                ArgumentCaptor.forClass(UpdateAnnouncementCommand.class);
        verify(fixture.announcements).update(updateCaptor.capture());
        assertEquals("<p>nội dung</p>độc<a>x</a>", updateCaptor.getValue().content().value());

        ArgumentCaptor<AuditCommand> auditCaptor = ArgumentCaptor.forClass(AuditCommand.class);
        verify(fixture.audits).append(auditCaptor.capture());
        assertTrue(
                auditCaptor.getValue().reason().contains("sanitizer removed 1 element(s)"),
                () -> "audit reason did not report sanitization: " + auditCaptor.getValue().reason());
    }

    @Test
    void cleanContentSurvivesWithoutAnAuditSuffix() {
        Fixture fixture = new Fixture();
        when(fixture.announcements.findByIdForUpdate("notice-1"))
                .thenReturn(Optional.of(announcement()));
        when(fixture.announcements.update(any())).thenReturn(1);
        when(fixture.announcements.findById("notice-1")).thenReturn(Optional.of(announcement()));
        UpdateAnnouncementRequest request = new UpdateAnnouncementRequest(
                Set.of("content", "reason", "expectedVersion"), null,
                "<p>nội dung sạch</p>", null, null, null, null, null, null, null, null,
                null, "Copyedit", 0);

        fixture.service.update("user-1", "Admin", List.of("ADMIN"), null, "notice-1", request);

        ArgumentCaptor<AuditCommand> auditCaptor = ArgumentCaptor.forClass(AuditCommand.class);
        verify(fixture.audits).append(auditCaptor.capture());
        assertEquals("Copyedit", auditCaptor.getValue().reason());
    }

    @Test
    void overLimitContentIsStillRefusedBeforeSanitization() {
        // The client image cap (lib/announcement-limits.ts) is derived from
        // this server cap; the backstop itself must not move silently.
        Fixture fixture = new Fixture();
        CreateAnnouncementRequest request = new CreateAnnouncementRequest(
                "Notice", "x".repeat(200_001), "NORMAL", List.of("STUDENT"), List.of(),
                false, null, null, null, null, null);

        DomainException error = assertThrows(DomainException.class,
                () -> fixture.service.create("user-1", "Admin", List.of("ADMIN"), null, request));
        assertEquals("ANNOUNCEMENT_CONTENT_TOO_LONG", error.code());
    }

    private static AnnouncementResponse announcement() {
        Instant now = Instant.parse("2026-09-10T00:00:00Z");
        return new AnnouncementResponse("notice-1", "Notice", "Content", "NORMAL", List.of("STUDENT"),
                List.of(), false, null, null, "user-1", null, null, "section-1",
                null, null, null, null, null, now, now, 0, null, null, null, null, null);
    }

    private static final class Fixture {
        private final AnnouncementWriteRepository announcements = mock(AnnouncementWriteRepository.class);
        private final AnnouncementAuditRepository audits = mock(AnnouncementAuditRepository.class);
        private final AnnouncementWriteService service;

        private Fixture() {
            ObjectMapper mapper = mock(ObjectMapper.class);
            try {
                when(mapper.writeValueAsString(any())).thenReturn("{}");
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
            this.service = new AnnouncementWriteService(announcements, audits, mapper);
        }
    }
}
