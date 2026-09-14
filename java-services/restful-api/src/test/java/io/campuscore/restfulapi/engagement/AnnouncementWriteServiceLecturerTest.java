package io.campuscore.restfulapi.engagement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.campuscore.restfulapi.engagement.repository.AnnouncementAuditRepository;
import io.campuscore.restfulapi.engagement.repository.AnnouncementWriteRepository;
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

class AnnouncementWriteServiceLecturerTest {

    @Test
    void lecturerCanCreateStudentAnnouncementForOwnedSection() throws Exception {
        Fixture fixture = fixture();
        when(fixture.announcements.sectionBelongsToLecturer("section-1", "lecturer-1")).thenReturn(true);
        when(fixture.announcements.create(any())).thenReturn(announcement("lecturer-1"));

        fixture.service.create("user-1", "Lecturer", List.of("LECTURER"), "lecturer-1",
                createRequest(List.of("STUDENT"), false, "section-1"));

        verify(fixture.announcements).create(any());
    }

    @Test
    void lecturerCannotCreateGlobalOrCrossSectionAnnouncement() throws Exception {
        Fixture fixture = fixture();
        when(fixture.announcements.sectionBelongsToLecturer("section-1", "lecturer-1")).thenReturn(true);

        assertThrows(DomainException.class, () -> fixture.service.create(
                "user-1", "Lecturer", List.of("LECTURER"), "lecturer-1",
                createRequest(List.of("STUDENT"), true, "section-1")));
        assertThrows(DomainException.class, () -> fixture.service.create(
                "user-1", "Lecturer", List.of("LECTURER"), "lecturer-1",
                createRequest(List.of("STUDENT"), false, "other-section")));
    }

    @Test
    void lecturerCannotUpdateAnotherLecturersAnnouncementOrActWithoutProfile() throws Exception {
        Fixture fixture = fixture();
        when(fixture.announcements.findByIdForUpdate("notice-1"))
                .thenReturn(Optional.of(announcement("lecturer-2")));
        UpdateAnnouncementRequest request = new UpdateAnnouncementRequest(
                Set.of("title", "reason", "expectedVersion"), "Updated", null, null, null, null,
                null, null, null, null, null, null, "Correction", 0);

        assertThrows(DomainException.class, () -> fixture.service.update(
                "user-1", "Lecturer", List.of("LECTURER"), "lecturer-1", "notice-1", request));
        assertThrows(DomainException.class, () -> fixture.service.update(
                "user-1", "Lecturer", List.of("LECTURER"), null, "notice-1", request));
    }

    @Test
    void lecturerCanUpdateOwnStudentAnnouncementForOwnedSection() throws Exception {
        Fixture fixture = fixture();
        AnnouncementResponse ownAnnouncement = announcement("lecturer-1");
        when(fixture.announcements.findByIdForUpdate("notice-1"))
                .thenReturn(Optional.of(ownAnnouncement));
        when(fixture.announcements.sectionBelongsToLecturer("section-1", "lecturer-1")).thenReturn(true);
        when(fixture.announcements.update(any())).thenReturn(1);
        when(fixture.announcements.findById("notice-1")).thenReturn(Optional.of(ownAnnouncement));
        UpdateAnnouncementRequest request = new UpdateAnnouncementRequest(
                Set.of("title", "reason", "expectedVersion"), "Updated", null, null, null, null,
                null, null, null, null, null, null, "Correction", 0);

        fixture.service.update(
                "user-1", "Lecturer", List.of("LECTURER"), "lecturer-1", "notice-1", request);

        verify(fixture.announcements).update(any());
    }

    @Test
    void executableAnnouncementContentIsRefusedAtTheWriteBoundary() throws Exception {
        Fixture fixture = fixture();

        // Every payload below reached the database before the write gate existed,
        // because only the frontend renderer tried (and failed) to strip it.
        for (String unsafe : List.of(
                "<p>ok</p><script>alert(1)</script>",
                "<img src=x/onerror=alert(1)>",
                "<svg/onload=alert(1)>",
                "<div onclick=\"alert(1)\">x</div>",
                "<iframe src=\"https://evil.example\"></iframe>",
                "<a href=\"javascript:alert(1)\">x</a>",
                "<a href=\"data:text/html;base64,PHNjcmlwdD4=\">x</a>")) {
            CreateAnnouncementRequest request = new CreateAnnouncementRequest(
                    "Notice", unsafe, "NORMAL", List.of("STUDENT"), List.of(), false,
                    null, null, null, null, null);
            DomainException error = assertThrows(DomainException.class, () -> fixture.service.create(
                    "user-1", "Admin", List.of("ADMIN"), null, request), unsafe);
            assertEquals("UNSAFE_ANNOUNCEMENT_CONTENT", error.code(), unsafe);
        }
    }

    @Test
    void formattedAnnouncementContentStillPassesTheWriteBoundary() throws Exception {
        Fixture fixture = fixture();
        when(fixture.announcements.create(any())).thenReturn(announcement(null));
        CreateAnnouncementRequest request = new CreateAnnouncementRequest(
                "Notice",
                "<p>Học phần <strong>tiên quyết</strong></p>"
                        + "<img src=\"data:image/png;base64,iVBORw0KGgo=\" alt=\"sơ đồ\">",
                "NORMAL", List.of("STUDENT"), List.of(), false, null, null, null, null, null);

        fixture.service.create("user-1", "Admin", List.of("ADMIN"), null, request);

        verify(fixture.announcements).create(any());
    }

    private static CreateAnnouncementRequest createRequest(List<String> roles, boolean global, String sectionId) {
        return new CreateAnnouncementRequest("Notice", "Content", "NORMAL", roles, List.of(), global,
                null, null, null, sectionId, null);
    }

    private static Fixture fixture() throws Exception {
        AnnouncementWriteRepository announcements = mock(AnnouncementWriteRepository.class);
        AnnouncementAuditRepository audits = mock(AnnouncementAuditRepository.class);
        ObjectMapper mapper = mock(ObjectMapper.class);
        when(mapper.writeValueAsString(any())).thenReturn("{}");
        return new Fixture(announcements, new AnnouncementWriteService(announcements, audits, mapper));
    }

    private static AnnouncementResponse announcement(String lecturerId) {
        Instant now = Instant.parse("2026-09-10T00:00:00Z");
        return new AnnouncementResponse("notice-1", "Notice", "Content", "NORMAL", List.of("STUDENT"),
                List.of(), false, null, null, "user-1", null, null, "section-1",
                null, null, null, lecturerId, null, now, now, 0, null, null, null, null, null);
    }

    private record Fixture(AnnouncementWriteRepository announcements, AnnouncementWriteService service) {
    }
}
