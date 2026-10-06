package io.campuscore.restfulapi.engagement;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.campuscore.restfulapi.engagement.repository.AnnouncementAuditRepository;
import io.campuscore.restfulapi.engagement.repository.AnnouncementWriteRepository;
import io.campuscore.restfulapi.engagement.service.AnnouncementStudentNotifier;
import io.campuscore.restfulapi.engagement.service.AnnouncementWriteService;
import io.campuscore.restfulapi.engagement.service.ScheduledAnnouncementNotifyJob;
import io.campuscore.restfulapi.engagement.web.AnnouncementReadDtos.AnnouncementResponse;
import io.campuscore.restfulapi.engagement.web.AnnouncementWriteDtos.CreateAnnouncementRequest;
import io.campuscore.restfulapi.engagement.web.AnnouncementWriteDtos.UpdateAnnouncementRequest;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

/** Marker contract for the scheduled-announcement notification refire (V104). */
class ScheduledAnnouncementNotifyTest {

    private static final Instant NOW = Instant.parse("2026-10-07T00:00:00Z");

    @Test
    void createStampsNotifiedOnlyWhenVisibleNow() throws Exception {
        AnnouncementWriteRepository announcements = mock(AnnouncementWriteRepository.class);
        AnnouncementWriteService service = service(announcements, mock(AnnouncementStudentNotifier.class));
        when(announcements.create(any())).thenReturn(announcement(null));

        service.create("admin-1", "Admin", List.of("ADMIN"), null, createRequest(null));

        verify(announcements).markNotified(eq("notice-1"), any());
    }

    @Test
    void createScheduledLeavesMarkerNullForTheJob() throws Exception {
        AnnouncementWriteRepository announcements = mock(AnnouncementWriteRepository.class);
        AnnouncementStudentNotifier notifier = mock(AnnouncementStudentNotifier.class);
        AnnouncementWriteService service = service(announcements, notifier);
        Instant future = NOW.plusSeconds(3600);
        when(announcements.create(any())).thenReturn(announcement(future));

        service.create("admin-1", "Admin", List.of("ADMIN"), null, createRequest(future));

        verify(announcements, never()).markNotified(any(), any());
    }

    @Test
    void failedFanOutLeavesMarkerNullSoTheJobRetries() throws Exception {
        AnnouncementWriteRepository announcements = mock(AnnouncementWriteRepository.class);
        AnnouncementStudentNotifier notifier = mock(AnnouncementStudentNotifier.class);
        AnnouncementWriteService service = service(announcements, notifier);
        when(announcements.create(any())).thenReturn(announcement(null));
        when(notifier.fanOutToActiveStudents(any())).thenThrow(new RuntimeException("inbox down"));

        service.create("admin-1", "Admin", List.of("ADMIN"), null, createRequest(null));

        verify(announcements, never()).markNotified(any(), any());
    }

    @Test
    void reschedulingPublishAtIntoFutureReArmsTheMarker() throws Exception {
        AnnouncementWriteRepository announcements = mock(AnnouncementWriteRepository.class);
        AnnouncementWriteService service = service(announcements, mock(AnnouncementStudentNotifier.class));
        AnnouncementResponse existing = announcement(null);
        when(announcements.findByIdForUpdate("notice-1")).thenReturn(Optional.of(existing));
        when(announcements.findById("notice-1")).thenReturn(Optional.of(existing));
        when(announcements.update(any())).thenReturn(1);
        UpdateAnnouncementRequest request = new UpdateAnnouncementRequest(
                Set.of("publishAt", "reason", "expectedVersion"),
                null, null, null, null, null, null,
                NOW.plusSeconds(7200), null, null, null, null, "Reschedule", 0);

        service.update("admin-1", "admin@campuscore.edu", List.of("ADMIN"), null, "notice-1", request);

        verify(announcements).clearNotifiedAt("notice-1");
    }

    @Test
    void jobFansOutAndStampsEachDueAnnouncement() {
        AnnouncementWriteRepository announcements = mock(AnnouncementWriteRepository.class);
        AnnouncementStudentNotifier notifier = mock(AnnouncementStudentNotifier.class);
        ScheduledAnnouncementNotifyJob job = new ScheduledAnnouncementNotifyJob(announcements, notifier);
        when(announcements.findDueForNotification(any(), anyInt()))
                .thenReturn(List.of(announcement(NOW.minusSeconds(60))));

        job.refireDueAnnouncements();

        InOrder order = inOrder(notifier, announcements);
        order.verify(notifier).fanOutToActiveStudents(any());
        order.verify(announcements).markNotified(eq("notice-1"), any());
    }

    @Test
    void jobSkipsStampWhenFanOutThrowsSoRowRetriesNextTick() {
        AnnouncementWriteRepository announcements = mock(AnnouncementWriteRepository.class);
        AnnouncementStudentNotifier notifier = mock(AnnouncementStudentNotifier.class);
        ScheduledAnnouncementNotifyJob job = new ScheduledAnnouncementNotifyJob(announcements, notifier);
        when(announcements.findDueForNotification(any(), anyInt()))
                .thenReturn(List.of(announcement(NOW.minusSeconds(60))));
        when(notifier.fanOutToActiveStudents(any())).thenThrow(new RuntimeException("inbox down"));

        job.refireDueAnnouncements();

        verify(announcements, never()).markNotified(any(), any());
    }

    private static AnnouncementWriteService service(
            AnnouncementWriteRepository announcements, AnnouncementStudentNotifier notifier) throws Exception {
        ObjectMapper mapper = mock(ObjectMapper.class);
        when(mapper.writeValueAsString(any())).thenReturn("{}");
        return new AnnouncementWriteService(
                announcements, mock(AnnouncementAuditRepository.class), notifier, mapper);
    }

    private static CreateAnnouncementRequest createRequest(Instant publishAt) {
        return new CreateAnnouncementRequest(
                "Notice", "<p>Body</p>", "NORMAL", List.of("STUDENT"), List.of(), false,
                publishAt, null, null, null, null);
    }

    private static AnnouncementResponse announcement(Instant publishAt) {
        return new AnnouncementResponse(
                "notice-1", "Title", "Content", "NORMAL",
                List.of("STUDENT"), List.of(1), false,
                publishAt, null, "publisher-1",
                null, null, null, null, null, null, null, null,
                NOW, NOW, 0, null, null, null, null, null);
    }
}
