package io.campuscore.restfulapi.thesis.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.campuscore.restfulapi.academic.service.AcademicEnrollmentReadService;
import io.campuscore.restfulapi.academic.service.AcademicSectionReadService;
import io.campuscore.restfulapi.academic.web.AcademicEnrollmentReadDtos.ClassroomSummary;
import io.campuscore.restfulapi.academic.web.AcademicEnrollmentReadDtos.CourseSummary;
import io.campuscore.restfulapi.academic.web.AcademicEnrollmentReadDtos.EnrollmentResponse;
import io.campuscore.restfulapi.academic.web.AcademicEnrollmentReadDtos.SectionScheduleResponse;
import io.campuscore.restfulapi.academic.web.AcademicEnrollmentReadDtos.SectionSummary;
import io.campuscore.restfulapi.academic.web.AcademicEnrollmentReadDtos.SemesterSummary;
import io.campuscore.restfulapi.academic.web.AcademicSectionReadDtos.LecturerScheduleResponse;
import io.campuscore.restfulapi.thesis.assistant.ThesisAssistantDtos.ChatRequest;
import io.campuscore.restfulapi.thesis.assistant.ThesisAssistantDtos.ChatResponse;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class AssistantPersonalContextAdvisorTest {

    private static final Instant OLD_TERM_START = Instant.parse("2025-09-01T00:00:00Z");
    private static final Instant CURRENT_TERM_START = Instant.parse("2026-08-17T00:00:00Z");

    private final AcademicEnrollmentReadService enrollmentService = mock(AcademicEnrollmentReadService.class);
    private final AcademicSectionReadService sectionService = mock(AcademicSectionReadService.class);
    private final AssistantPersonalContextAdvisor advisor =
            new AssistantPersonalContextAdvisor(enrollmentService, sectionService);

    @Test
    void detectsTimetableIntentsInVietnameseAndEnglish() {
        assertTrue(advisor.handles("Lịch học của tôi tuần này có những môn nào?"));
        assertTrue(advisor.handles("cho xem thời khóa biểu"));
        assertTrue(advisor.handles("Tuần này tôi dạy những gì? Lịch dạy của tôi"));
        assertTrue(advisor.handles("What is my schedule this week?"));
        assertTrue(advisor.handles("show my timetable"));
        assertTrue(advisor.handles("Lịch thứ 2 của tôi là khi nào?"));
        assertTrue(advisor.handles("thứ 2 học gì"));
        assertTrue(advisor.handles("lịch dạy thứ 3 của tôi"));
        assertTrue(advisor.handles("hôm nay tôi có tiết không"));
        assertTrue(advisor.handles("ngày mai tôi dạy những môn nào"));

        assertFalse(advisor.handles("Học phí kỳ này bao nhiêu?"));
        assertFalse(advisor.handles("lớp học phần SE401 còn chỗ không"));
        assertFalse(advisor.handles("quy chế đào tạo nói gì về điểm A?"));
        assertFalse(advisor.handles(null));
    }

    @Test
    void answersStudentScheduleForTheCurrentTermInVietnamese() {
        when(enrollmentService.findStudentEnrollments("student-profile", null)).thenReturn(List.of(
                enrollment("SE401", "Lập trình Java nâng cao", "Advanced Java", CURRENT_TERM_START,
                        List.of(new SectionScheduleResponse("s1", 2, "07:00", "09:30",
                                new ClassroomSummary("c1", "A", "101")))),
                enrollment("SE403", "Cấu trúc dữ liệu và giải thuật", "Data Structures", CURRENT_TERM_START,
                        List.of(new SectionScheduleResponse("s2", 2, "09:45", "11:45",
                                new ClassroomSummary("c2", "A", "103")))),
                enrollment("SE201", "Công nghệ phần mềm", "Software Engineering", OLD_TERM_START,
                        List.of(new SectionScheduleResponse("s3", 4, "13:00", "15:30",
                                new ClassroomSummary("c3", "B", "202"))))));

        ChatResponse response = advisor.answer(chatRequest("vi", "Lịch học của tôi tuần này?"), jwtStudent());

        assertNotNull(response);
        assertEquals("PERSONAL_CONTEXT", response.reasonCode());
        assertTrue(response.citations().isEmpty());
        String answer = response.answer();
        assertTrue(answer.contains("Lịch học cá nhân"), answer);
        assertTrue(answer.contains("Học kỳ hiện tại"), answer);
        assertTrue(answer.contains("Thứ Hai 07:00-09:30 — SE401 - Lập trình Java nâng cao (phòng A 101)"), answer);
        assertTrue(answer.contains("Thứ Hai 09:45-11:45 — SE403 - Cấu trúc dữ liệu và giải thuật (phòng A 103)"), answer);
        assertFalse(answer.contains("SE201"), "older-term enrollments must not leak into the current timetable");
    }

    @Test
    void answersStudentScheduleInEnglishWhenRequested() {
        when(enrollmentService.findStudentEnrollments("student-profile", null)).thenReturn(List.of(
                enrollment("SE401", "Lập trình Java nâng cao", "Advanced Java", CURRENT_TERM_START,
                        List.of(new SectionScheduleResponse("s1", 1, "13:00", "15:30",
                                new ClassroomSummary("c1", "A", "101"))))));

        ChatResponse response = advisor.answer(chatRequest("en", "my schedule"), jwtStudent());

        assertNotNull(response);
        assertTrue(response.answer().contains("Sunday 13:00-15:30 — SE401 - Advanced Java (room A 101)"),
                response.answer());
    }

    @Test
    void reportsNoActiveClassesWhenTheStudentHasNone() {
        when(enrollmentService.findStudentEnrollments("student-profile", null)).thenReturn(List.of(
                enrollmentWithStatus("SE401", "COMPLETED", CURRENT_TERM_START, List.of())));

        ChatResponse response = advisor.answer(chatRequest("vi", "lịch học của tôi?"), jwtStudent());

        assertNotNull(response);
        assertTrue(response.answer().contains("chưa có lớp học phần nào đang hoạt động"), response.answer());
    }

    @Test
    void returnsNullWithoutPersonalClaimsSoTheControllerFallsBackToRag() {
        Jwt guest = new Jwt("token", Instant.now(), Instant.now().plusSeconds(600),
                Map.of("alg", "HS256"), new HashMap<>(Map.of("sub", "someone", "roles", List.of("STUDENT"))));

        assertNull(advisor.answer(chatRequest("vi", "lịch học của tôi?"), guest));
    }

    @Test
    void answersStudentScheduleForSpecificDay() {
        when(enrollmentService.findStudentEnrollments("student-profile", null)).thenReturn(List.of(
                enrollment("SE401", "Lập trình Java nâng cao", "Advanced Java", CURRENT_TERM_START,
                        List.of(new SectionScheduleResponse("s1", 2, "07:00", "09:30",
                                new ClassroomSummary("c1", "A", "101")))),
                enrollment("SE403", "Cấu trúc dữ liệu và giải thuật", "Data Structures", CURRENT_TERM_START,
                        List.of(new SectionScheduleResponse("s2", 2, "09:45", "11:45",
                                new ClassroomSummary("c2", "A", "103")))),
                enrollment("SE201", "Công nghệ phần mềm", "Software Engineering", CURRENT_TERM_START,
                        List.of(new SectionScheduleResponse("s3", 5, "13:00", "15:30",
                                new ClassroomSummary("c3", "B", "202"))))));

        ChatResponse response = advisor.answer(chatRequest("vi", "Lịch thứ 2 của tôi là khi nào?"), jwtStudent());

        assertNotNull(response);
        String answer = response.answer();
        assertTrue(answer.contains("Lịch học Thứ Hai của bạn"), answer);
        assertTrue(answer.contains("Thứ Hai 07:00-09:30 — SE401 - Lập trình Java nâng cao (phòng A 101)"), answer);
        assertTrue(answer.contains("Thứ Hai 09:45-11:45 — SE403 - Cấu trúc dữ liệu và giải thuật (phòng A 103)"), answer);
        assertFalse(answer.contains("SE201"), "non-Monday classes must not be included when Monday was requested");
    }

    @Test
    void answersStudentNoClassesOnRequestedDay() {
        when(enrollmentService.findStudentEnrollments("student-profile", null)).thenReturn(List.of(
                enrollment("SE401", "Lập trình Java nâng cao", "Advanced Java", CURRENT_TERM_START,
                        List.of(new SectionScheduleResponse("s1", 2, "07:00", "09:30",
                                new ClassroomSummary("c1", "A", "101"))))));

        ChatResponse response = advisor.answer(chatRequest("vi", "Chủ nhật tôi có học không?"), jwtStudent());

        assertNotNull(response);
        assertTrue(response.answer().contains("không có lịch học vào Chủ Nhật"), response.answer());
    }

    @Test
    void answersLecturerScheduleForSpecificDay() {
        when(sectionService.findLecturerSchedule("lecturer-profile", null)).thenReturn(List.of(
                new LecturerScheduleResponse("id1", "sec1", "SE402-01", "SE402", "Phát triển ứng dụng web",
                        "Web Application Development", "Phát triển ứng dụng web", 3, 35, 13, "CNTT", "CNTT", "CNTT",
                        "OPEN", List.of(new io.campuscore.restfulapi.academic.web.AcademicSectionReadDtos
                                .SectionScheduleResponse("s1", 5, "13:00", "15:30", "A", "102",
                                new io.campuscore.restfulapi.academic.web.AcademicSectionReadDtos
                                        .ClassroomSummary("c1", "A", "102"))))));

        ChatResponse response = advisor.answer(chatRequest("vi", "thứ 5 tôi dạy môn nào?"), jwtLecturer());

        assertNotNull(response);
        String answer = response.answer();
        assertTrue(answer.contains("Lịch giảng dạy Thứ Năm của bạn"), answer);
        assertTrue(answer.contains("Thứ Năm 13:00-15:30 — SE402 - Phát triển ứng dụng web (phòng A 102)"), answer);
    }

    @Test
    void composesLecturerTeachingTimetable() {
        when(sectionService.findLecturerSchedule("lecturer-profile", null)).thenReturn(List.of(
                new LecturerScheduleResponse("id1", "sec1", "SE402-01", "SE402", "Phát triển ứng dụng web",
                        "Web Application Development", "Phát triển ứng dụng web", 3, 35, 13, "CNTT", "CNTT", "CNTT",
                        "OPEN", List.of(new io.campuscore.restfulapi.academic.web.AcademicSectionReadDtos
                                .SectionScheduleResponse("s1", 5, "13:00", "15:30", "A", "102",
                                new io.campuscore.restfulapi.academic.web.AcademicSectionReadDtos
                                        .ClassroomSummary("c1", "A", "102"))))));

        ChatResponse response = advisor.answer(chatRequest("vi", "lịch dạy của tôi tuần này?"), jwtLecturer());

        assertNotNull(response);
        assertTrue(response.answer().contains("Thứ Năm 13:00-15:30 — SE402 - Phát triển ứng dụng web (phòng A 102)"),
                response.answer());
    }

    private static ChatRequest chatRequest(String locale, String message) {
        return new ChatRequest(message, locale);
    }

    private static Jwt jwtStudent() {
        return jwt("studentId", "student-profile");
    }

    private static Jwt jwtLecturer() {
        return jwt("lecturerId", "lecturer-profile");
    }

    private static Jwt jwt(String claim, String value) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", "owner-user");
        claims.put("roles", List.of("STUDENT"));
        claims.put(claim, value);
        return new Jwt("token", Instant.now(), Instant.now().plusSeconds(600), Map.of("alg", "HS256"), claims);
    }

    private static EnrollmentResponse enrollment(
            String code, String name, String nameEn, Instant termStart,
            List<SectionScheduleResponse> schedules) {
        return enrollmentWithStatus(code, "ENROLLED", termStart, schedules, name, nameEn);
    }

    private static EnrollmentResponse enrollmentWithStatus(
            String code, String status, Instant termStart, List<SectionScheduleResponse> schedules) {
        return enrollmentWithStatus(code, status, termStart, schedules, "Course " + code, "Course " + code);
    }

    private static EnrollmentResponse enrollmentWithStatus(
            String code, String status, Instant termStart, List<SectionScheduleResponse> schedules,
            String name, String nameEn) {
        return new EnrollmentResponse(
                "enr-" + code,
                "student-profile",
                "section-" + code,
                "semester-" + termStart,
                status,
                termStart,
                null,
                "DRAFT",
                null,
                null,
                termStart,
                termStart,
                null,
                new SectionSummary(
                        "section-" + code,
                        code + "-01",
                        new CourseSummary("course-" + code, code, name, nameEn, name, 3),
                        new SemesterSummary("semester-" + termStart, "Học kỳ hiện tại", "Current term",
                                "Học kỳ hiện tại", termStart),
                        null,
                        45,
                        10,
                        "OPEN",
                        schedules),
                null);
    }
}
