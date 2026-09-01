package io.campuscore.restfulapi.engagement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.campuscore.restfulapi.engagement.repository.AnnouncementAuditRepository;
import io.campuscore.restfulapi.engagement.repository.AnnouncementAuditRepository.AuditCommand;
import io.campuscore.restfulapi.engagement.repository.AnnouncementWriteRepository;
import io.campuscore.restfulapi.engagement.service.AnnouncementWriteService;
import io.campuscore.restfulapi.engagement.web.AnnouncementReadDtos.AnnouncementResponse;
import io.campuscore.restfulapi.engagement.web.AnnouncementWriteDtos.LifecycleRequest;
import io.campuscore.restfulapi.engagement.web.AnnouncementWriteDtos.UpdateAnnouncementRequest;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

/** Regression tests for serializing a mutation with the audit snapshot it records. */
class AnnouncementWriteServiceConcurrencyTest {

    private static final Instant CREATED_AT = Instant.parse("2026-08-20T00:00:00Z");

    @Test
    void updateLocksTheRowBeforeCasAndAuditsTheVersionItWrote() throws Exception {
        AnnouncementWriteRepository announcements = mock(AnnouncementWriteRepository.class);
        AnnouncementAuditRepository audits = mock(AnnouncementAuditRepository.class);
        ObjectMapper objectMapper = mapper();
        AnnouncementWriteService service = new AnnouncementWriteService(announcements, audits, objectMapper);
        AnnouncementResponse before = announcement("Original", 0, null);
        AnnouncementResponse after = announcement("Updated", 1, null);
        when(announcements.findByIdForUpdate("notice-1")).thenReturn(Optional.of(before));
        when(announcements.findById("notice-1")).thenReturn(Optional.of(after));
        when(announcements.update(any())).thenReturn(1);

        service.update(
                "admin-1",
                "admin-1@campuscore.edu",
                "notice-1",
                new UpdateAnnouncementRequest(
                        Set.of("title", "reason", "expectedVersion"),
                        "Updated",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "Sửa nội dung",
                        0));

        InOrder order = inOrder(announcements);
        order.verify(announcements).findByIdForUpdate("notice-1");
        order.verify(announcements).update(any());
        order.verify(announcements).findById("notice-1");
        org.mockito.ArgumentCaptor<AuditCommand> audit =
                org.mockito.ArgumentCaptor.forClass(AuditCommand.class);
        verify(audits).append(audit.capture());
        assertEquals(1, audit.getValue().version());
    }

    @Test
    void lifecycleMutationsLockTheRowBeforeRecordingTheirAfterSnapshot() throws Exception {
        AnnouncementWriteRepository announcements = mock(AnnouncementWriteRepository.class);
        AnnouncementAuditRepository audits = mock(AnnouncementAuditRepository.class);
        AnnouncementWriteService service = new AnnouncementWriteService(announcements, audits, mapper());
        AnnouncementResponse active = announcement("Active", 0, null);
        AnnouncementResponse archived = announcement("Active", 1, CREATED_AT.plusSeconds(1));
        AnnouncementResponse restored = announcement("Active", 2, null);
        when(announcements.findByIdForUpdate("notice-1")).thenReturn(Optional.of(active), Optional.of(archived));
        when(announcements.findById("notice-1")).thenReturn(Optional.of(archived), Optional.of(restored));
        when(announcements.archive(any())).thenReturn(1);
        when(announcements.restore(any())).thenReturn(1);

        service.archive("admin-1", "admin-1@campuscore.edu", "notice-1", new LifecycleRequest("Ẩn bài", 0));
        service.restore("admin-1", "admin-1@campuscore.edu", "notice-1", new LifecycleRequest("Hiện lại", 1));

        InOrder order = inOrder(announcements);
        order.verify(announcements).findByIdForUpdate("notice-1");
        order.verify(announcements).archive(any());
        order.verify(announcements).findById("notice-1");
        order.verify(announcements).findByIdForUpdate("notice-1");
        order.verify(announcements).restore(any());
        order.verify(announcements).findById("notice-1");
        org.mockito.ArgumentCaptor<AuditCommand> audit =
                org.mockito.ArgumentCaptor.forClass(AuditCommand.class);
        verify(audits, org.mockito.Mockito.times(2)).append(audit.capture());
        assertEquals(List.of(1, 2), audit.getAllValues().stream().map(AuditCommand::version).toList());
    }

    @Test
    void legacyDeleteLocksTheRowBeforeSoftArchiving() throws Exception {
        AnnouncementWriteRepository announcements = mock(AnnouncementWriteRepository.class);
        AnnouncementAuditRepository audits = mock(AnnouncementAuditRepository.class);
        AnnouncementWriteService service = new AnnouncementWriteService(announcements, audits, mapper());
        AnnouncementResponse active = announcement("Active", 0, null);
        AnnouncementResponse archived = announcement("Active", 1, CREATED_AT.plusSeconds(1));
        when(announcements.findByIdForUpdate("notice-1")).thenReturn(Optional.of(active));
        when(announcements.findById("notice-1")).thenReturn(Optional.of(archived));
        when(announcements.archive(any())).thenReturn(1);

        service.delete("admin-1", "admin-1@campuscore.edu", "notice-1");

        InOrder order = inOrder(announcements);
        order.verify(announcements).findByIdForUpdate("notice-1");
        order.verify(announcements).archive(any());
        order.verify(announcements).findById("notice-1");
    }

    private static ObjectMapper mapper() throws Exception {
        ObjectMapper mapper = mock(ObjectMapper.class);
        when(mapper.writeValueAsString(any())).thenReturn("{}");
        return mapper;
    }

    private static AnnouncementResponse announcement(String title, int version, Instant archivedAt) {
        return new AnnouncementResponse(
                "notice-1",
                title,
                "Content",
                "NORMAL",
                List.of("STUDENT"),
                List.of(1),
                false,
                null,
                null,
                "publisher-1",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                CREATED_AT,
                CREATED_AT.plusSeconds(version),
                version,
                archivedAt,
                archivedAt == null ? null : "admin-1",
                null,
                null,
                null);
    }
}
