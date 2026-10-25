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
import io.campuscore.restfulapi.engagement.service.AnnouncementStudentNotifier;
import io.campuscore.restfulapi.engagement.service.AnnouncementWriteService;
import io.campuscore.restfulapi.engagement.dto.AnnouncementReadDtos.AnnouncementResponse;
import io.campuscore.restfulapi.engagement.dto.AnnouncementWriteDtos.CreateAnnouncementRequest;
import io.campuscore.restfulapi.engagement.dto.AnnouncementWriteDtos.LifecycleRequest;
import io.campuscore.restfulapi.engagement.dto.AnnouncementWriteDtos.UpdateAnnouncementRequest;
import io.campuscore.restfulapi.exception.DomainException;
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
    void lecturerCannotTargetAnotherDepartmentOnCreate() throws Exception {
        Fixture fixture = fixture();
        when(fixture.announcements.sectionBelongsToLecturer("section-1", "lecturer-1")).thenReturn(true);
        when(fixture.announcements.findLecturerDepartmentId("lecturer-1")).thenReturn(Optional.of("dept-it"));

        DomainException error = assertThrows(DomainException.class, () -> fixture.service.create(
                "user-1", "Lecturer", List.of("LECTURER"), "lecturer-1",
                createRequest(List.of("STUDENT"), false, "section-1", List.of("dept-cs"))));

        assertEquals("LECTURER_ANNOUNCEMENT_FORBIDDEN", error.code());
    }

    @Test
    void lecturerCanTargetOwnDepartmentOrNoDepartmentOnCreate() throws Exception {
        Fixture fixture = fixture();
        when(fixture.announcements.sectionBelongsToLecturer("section-1", "lecturer-1")).thenReturn(true);
        when(fixture.announcements.findLecturerDepartmentId("lecturer-1")).thenReturn(Optional.of("dept-it"));
        when(fixture.announcements.create(any())).thenReturn(announcement("lecturer-1"));

        fixture.service.create("user-1", "Lecturer", List.of("LECTURER"), "lecturer-1",
                createRequest(List.of("STUDENT"), false, "section-1", List.of("dept-it")));
        fixture.service.create("user-1", "Lecturer", List.of("LECTURER"), "lecturer-1",
                createRequest(List.of("STUDENT"), false, "section-1", List.of()));

        verify(fixture.announcements, org.mockito.Mockito.times(2)).create(any());
    }

    @Test
    void lecturerCannotTargetAnotherFacultyOnCreate() throws Exception {
        Fixture fixture = fixture();
        when(fixture.announcements.sectionBelongsToLecturer("section-1", "lecturer-1")).thenReturn(true);
        when(fixture.announcements.findLecturerFacultyId("lecturer-1")).thenReturn(Optional.of("faculty-it"));

        DomainException error = assertThrows(DomainException.class, () -> fixture.service.create(
                "user-1", "Lecturer", List.of("LECTURER"), "lecturer-1",
                createRequest(List.of("STUDENT"), false, "section-1", List.of(), List.of("faculty-cs"))));

        assertEquals("LECTURER_ANNOUNCEMENT_FORBIDDEN", error.code());
    }

    @Test
    void lecturerCanTargetOwnFacultyOrNoFacultyOnCreate() throws Exception {
        Fixture fixture = fixture();
        when(fixture.announcements.sectionBelongsToLecturer("section-1", "lecturer-1")).thenReturn(true);
        when(fixture.announcements.findLecturerFacultyId("lecturer-1")).thenReturn(Optional.of("faculty-it"));
        when(fixture.announcements.create(any())).thenReturn(announcement("lecturer-1"));

        fixture.service.create("user-1", "Lecturer", List.of("LECTURER"), "lecturer-1",
                createRequest(List.of("STUDENT"), false, "section-1", List.of(), List.of("faculty-it")));
        fixture.service.create("user-1", "Lecturer", List.of("LECTURER"), "lecturer-1",
                createRequest(List.of("STUDENT"), false, "section-1", List.of(), List.of()));

        verify(fixture.announcements, org.mockito.Mockito.times(2)).create(any());
    }

    @Test
    void adminCanTargetAnyDepartmentOnCreate() throws Exception {
        Fixture fixture = fixture();
        when(fixture.announcements.create(any())).thenReturn(announcement(null));

        fixture.service.create("admin-1", "Admin", List.of("ADMIN"), null,
                createRequest(List.of("STUDENT"), false, null, List.of("dept-cs")));

        verify(fixture.announcements).create(any());
    }

    @Test
    void lecturerCannotUpdateAnotherLecturersAnnouncementOrActWithoutProfile() throws Exception {
        Fixture fixture = fixture();
        when(fixture.announcements.findByIdForUpdate("notice-1"))
                .thenReturn(Optional.of(announcement("lecturer-2")));
        UpdateAnnouncementRequest request = new UpdateAnnouncementRequest(
                Set.of("title", "reason", "expectedVersion"), "Updated", null, null, null, null,
                null, null, null, null, null, null, null, null, "Correction", 0);

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
                null, null, null, null, null, null, null, null, "Correction", 0);

        fixture.service.update(
                "user-1", "Lecturer", List.of("LECTURER"), "lecturer-1", "notice-1", request);

        verify(fixture.announcements).update(any());
    }

    @Test
    void lecturerCannotSetAnotherDepartmentOnUpdate() throws Exception {
        Fixture fixture = fixture();
        AnnouncementResponse ownAnnouncement = announcement("lecturer-1");
        when(fixture.announcements.findByIdForUpdate("notice-1")).thenReturn(Optional.of(ownAnnouncement));
        when(fixture.announcements.sectionBelongsToLecturer("section-1", "lecturer-1")).thenReturn(true);
        when(fixture.announcements.findLecturerDepartmentId("lecturer-1")).thenReturn(Optional.of("dept-it"));
        UpdateAnnouncementRequest request = new UpdateAnnouncementRequest(
                Set.of("targetDepartments", "reason", "expectedVersion"), null, null, null, null, null,
                List.of("dept-cs"), null, null, null, null, null, null, null, "Correction", 0);

        DomainException error = assertThrows(DomainException.class, () -> fixture.service.update(
                "user-1", "Lecturer", List.of("LECTURER"), "lecturer-1", "notice-1", request));

        assertEquals("LECTURER_ANNOUNCEMENT_FORBIDDEN", error.code());
    }

    @Test
    void lecturerCannotKeepStoredOtherDepartmentWhenUpdatingAnotherField() throws Exception {
        Fixture fixture = fixture();
        AnnouncementResponse storedOtherDepartment = announcement("lecturer-1", List.of("dept-cs"));
        when(fixture.announcements.findByIdForUpdate("notice-1")).thenReturn(Optional.of(storedOtherDepartment));
        when(fixture.announcements.sectionBelongsToLecturer("section-1", "lecturer-1")).thenReturn(true);
        when(fixture.announcements.findLecturerDepartmentId("lecturer-1")).thenReturn(Optional.of("dept-it"));
        UpdateAnnouncementRequest request = new UpdateAnnouncementRequest(
                Set.of("title", "reason", "expectedVersion"), "Updated", null, null, null, null,
                null, null, null, null, null, null, null, null, "Correction", 0);

        assertThrows(DomainException.class, () -> fixture.service.update(
                "user-1", "Lecturer", List.of("LECTURER"), "lecturer-1", "notice-1", request));
    }

    @Test
    void lecturerCanUpdateWithOwnOrEmptyDepartmentAudience() throws Exception {
        Fixture fixture = fixture();
        AnnouncementResponse ownAnnouncement = announcement("lecturer-1");
        when(fixture.announcements.findByIdForUpdate("notice-1")).thenReturn(Optional.of(ownAnnouncement));
        when(fixture.announcements.sectionBelongsToLecturer("section-1", "lecturer-1")).thenReturn(true);
        when(fixture.announcements.findLecturerDepartmentId("lecturer-1")).thenReturn(Optional.of("dept-it"));
        when(fixture.announcements.update(any())).thenReturn(1);
        when(fixture.announcements.findById("notice-1")).thenReturn(Optional.of(ownAnnouncement));
        UpdateAnnouncementRequest ownDepartment = new UpdateAnnouncementRequest(
                Set.of("targetDepartments", "reason", "expectedVersion"), null, null, null, null, null,
                List.of("dept-it"), null, null, null, null, null, null, null, "Correction", 0);
        UpdateAnnouncementRequest noDepartment = new UpdateAnnouncementRequest(
                Set.of("targetDepartments", "reason", "expectedVersion"), null, null, null, null, null,
                List.of(), null, null, null, null, null, null, null, "Correction", 0);

        fixture.service.update("user-1", "Lecturer", List.of("LECTURER"), "lecturer-1", "notice-1", ownDepartment);
        fixture.service.update("user-1", "Lecturer", List.of("LECTURER"), "lecturer-1", "notice-1", noDepartment);

        verify(fixture.announcements, org.mockito.Mockito.times(2)).update(any());
    }

    @Test
    void lecturerCannotSetAnotherFacultyOnUpdate() throws Exception {
        Fixture fixture = fixture();
        AnnouncementResponse ownAnnouncement = announcement("lecturer-1");
        when(fixture.announcements.findByIdForUpdate("notice-1")).thenReturn(Optional.of(ownAnnouncement));
        when(fixture.announcements.sectionBelongsToLecturer("section-1", "lecturer-1")).thenReturn(true);
        when(fixture.announcements.findLecturerFacultyId("lecturer-1")).thenReturn(Optional.of("faculty-it"));
        UpdateAnnouncementRequest request = new UpdateAnnouncementRequest(
                Set.of("targetFaculties", "reason", "expectedVersion"), null, null, null, null, null,
                null, List.of("faculty-cs"), null, null, null, null, null, null, "Correction", 0);

        DomainException error = assertThrows(DomainException.class, () -> fixture.service.update(
                "user-1", "Lecturer", List.of("LECTURER"), "lecturer-1", "notice-1", request));

        assertEquals("LECTURER_ANNOUNCEMENT_FORBIDDEN", error.code());
    }

    @Test
    void lecturerCanUpdateWithOwnOrEmptyFacultyAudience() throws Exception {
        Fixture fixture = fixture();
        AnnouncementResponse ownAnnouncement = announcement("lecturer-1");
        when(fixture.announcements.findByIdForUpdate("notice-1")).thenReturn(Optional.of(ownAnnouncement));
        when(fixture.announcements.sectionBelongsToLecturer("section-1", "lecturer-1")).thenReturn(true);
        when(fixture.announcements.findLecturerFacultyId("lecturer-1")).thenReturn(Optional.of("faculty-it"));
        when(fixture.announcements.update(any())).thenReturn(1);
        when(fixture.announcements.findById("notice-1")).thenReturn(Optional.of(ownAnnouncement));
        UpdateAnnouncementRequest ownFaculty = new UpdateAnnouncementRequest(
                Set.of("targetFaculties", "reason", "expectedVersion"), null, null, null, null, null,
                null, List.of("faculty-it"), null, null, null, null, null, null, "Correction", 0);
        UpdateAnnouncementRequest noFaculty = new UpdateAnnouncementRequest(
                Set.of("targetFaculties", "reason", "expectedVersion"), null, null, null, null, null,
                null, List.of(), null, null, null, null, null, null, "Correction", 0);

        fixture.service.update("user-1", "Lecturer", List.of("LECTURER"), "lecturer-1", "notice-1", ownFaculty);
        fixture.service.update("user-1", "Lecturer", List.of("LECTURER"), "lecturer-1", "notice-1", noFaculty);

        verify(fixture.announcements, org.mockito.Mockito.times(2)).update(any());
    }

    @Test
    void lecturerCanArchiveAndDeleteOwnAnnouncement() throws Exception {
        Fixture fixture = fixture();
        AnnouncementResponse own = announcement("lecturer-1");
        when(fixture.announcements.findByIdForUpdate("notice-1")).thenReturn(Optional.of(own));
        when(fixture.announcements.archive(any())).thenReturn(1);
        when(fixture.announcements.findById("notice-1")).thenReturn(Optional.of(own));
        LifecycleRequest lifecycle = new LifecycleRequest("Done", 0);

        fixture.service.archive("user-1", "Lecturer", List.of("LECTURER"), "lecturer-1", "notice-1", lifecycle);
        fixture.service.delete("user-1", "Lecturer", List.of("LECTURER"), "lecturer-1", "notice-1");

        verify(fixture.announcements, org.mockito.Mockito.times(2)).archive(any());
    }

    @Test
    void lecturerCannotArchiveRestoreOrDeleteAnotherLecturersAnnouncement() throws Exception {
        Fixture fixture = fixture();
        when(fixture.announcements.findByIdForUpdate("notice-1"))
                .thenReturn(Optional.of(announcement("lecturer-2")));
        AnnouncementResponse archived = announcement("lecturer-2");
        when(fixture.announcements.findByIdForUpdate("notice-arc"))
                .thenReturn(Optional.of(archived));
        LifecycleRequest lifecycle = new LifecycleRequest("Done", 0);

        assertThrows(DomainException.class, () -> fixture.service.archive(
                "user-1", "Lecturer", List.of("LECTURER"), "lecturer-1", "notice-1", lifecycle));
        assertThrows(DomainException.class, () -> fixture.service.restore(
                "user-1", "Lecturer", List.of("LECTURER"), "lecturer-1", "notice-arc", lifecycle));
        assertThrows(DomainException.class, () -> fixture.service.delete(
                "user-1", "Lecturer", List.of("LECTURER"), "lecturer-1", "notice-1"));
        // A lecturer without a bound profile can never claim ownership.
        assertThrows(DomainException.class, () -> fixture.service.archive(
                "user-1", "Lecturer", List.of("LECTURER"), null, "notice-1", lifecycle));
    }

    @Test
    void adminLifecycleCallsKeepWorkingThroughTheLegacySignatures() throws Exception {
        Fixture fixture = fixture();
        AnnouncementResponse notice = announcement("lecturer-2");
        when(fixture.announcements.findByIdForUpdate("notice-1")).thenReturn(Optional.of(notice));
        when(fixture.announcements.archive(any())).thenReturn(1);
        when(fixture.announcements.findById("notice-1")).thenReturn(Optional.of(notice));
        LifecycleRequest lifecycle = new LifecycleRequest("Done", 0);

        // Admin lifecycle over another lecturer's notice stays allowed.
        fixture.service.archive("admin-1", "Admin", "notice-1", lifecycle);
        fixture.service.delete("admin-1", "Admin", "notice-1");

        verify(fixture.announcements, org.mockito.Mockito.times(2)).archive(any());
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
                    "Notice", unsafe, "NORMAL", List.of("STUDENT"), List.of(), List.of(), List.of(), false,
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
                "NORMAL", List.of("STUDENT"), List.of(), List.of(), List.of(), false, null, null, null, null, null);

        fixture.service.create("user-1", "Admin", List.of("ADMIN"), null, request);

        verify(fixture.announcements).create(any());
    }

    private static CreateAnnouncementRequest createRequest(List<String> roles, boolean global, String sectionId) {
        return createRequest(roles, global, sectionId, List.of(), List.of());
    }

    private static CreateAnnouncementRequest createRequest(
            List<String> roles, boolean global, String sectionId, List<String> departments) {
        return createRequest(roles, global, sectionId, departments, List.of());
    }

    private static CreateAnnouncementRequest createRequest(
            List<String> roles, boolean global, String sectionId,
            List<String> departments, List<String> faculties) {
        return new CreateAnnouncementRequest("Notice", "Content", "NORMAL", roles, List.of(), departments,
                faculties, global, null, null, null, sectionId, null);
    }

    private static Fixture fixture() throws Exception {
        AnnouncementWriteRepository announcements = mock(AnnouncementWriteRepository.class);
        AnnouncementAuditRepository audits = mock(AnnouncementAuditRepository.class);
        ObjectMapper mapper = mock(ObjectMapper.class);
        when(mapper.writeValueAsString(any())).thenReturn("{}");
        return new Fixture(announcements, new AnnouncementWriteService(
                announcements, audits, mock(AnnouncementStudentNotifier.class), mapper));
    }

    private static AnnouncementResponse announcement(String lecturerId) {
        return announcement(lecturerId, List.of(), List.of());
    }

    private static AnnouncementResponse announcement(String lecturerId, List<String> departments) {
        return announcement(lecturerId, departments, List.of());
    }

    private static AnnouncementResponse announcement(
            String lecturerId, List<String> departments, List<String> faculties) {
        Instant now = Instant.parse("2026-09-10T00:00:00Z");
        return new AnnouncementResponse("notice-1", "Notice", "Content", "NORMAL", List.of("STUDENT"),
                List.of(), departments, faculties, false, null, null, "user-1", null, null, "section-1",
                null, null, null, lecturerId, null, now, now, 0, null, null, null, null, null);
    }

    private record Fixture(AnnouncementWriteRepository announcements, AnnouncementWriteService service) {
    }
}
