package io.campuscore.restfulapi.thesis.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.campuscore.restfulapi.academic.registration.RegistrationService;
import io.campuscore.restfulapi.academic.service.AcademicAttendanceReadService;
import io.campuscore.restfulapi.academic.service.AcademicConductService;
import io.campuscore.restfulapi.academic.service.AcademicEnrollmentReadService;
import io.campuscore.restfulapi.academic.service.AcademicSectionReadService;
import io.campuscore.restfulapi.academic.web.AcademicAttendanceReadDtos;
import io.campuscore.restfulapi.academic.web.AcademicEnrollmentReadDtos;
import io.campuscore.restfulapi.academic.web.AcademicEnrollmentReadDtos.ClassroomSummary;
import io.campuscore.restfulapi.academic.web.AcademicEnrollmentReadDtos.CourseSummary;
import io.campuscore.restfulapi.academic.web.AcademicEnrollmentReadDtos.EnrollmentResponse;
import io.campuscore.restfulapi.academic.web.AcademicEnrollmentReadDtos.SectionScheduleResponse;
import io.campuscore.restfulapi.academic.web.AcademicEnrollmentReadDtos.SectionSummary;
import io.campuscore.restfulapi.academic.web.AcademicEnrollmentReadDtos.SemesterSummary;
import io.campuscore.restfulapi.academic.web.AcademicSectionReadDtos.LecturerGradingSectionResponse;
import io.campuscore.restfulapi.academic.web.AcademicSectionReadDtos.LecturerScheduleResponse;
import io.campuscore.restfulapi.thesis.assistant.ThesisAssistantDtos.ChatRequest;
import io.campuscore.restfulapi.thesis.assistant.ThesisAssistantDtos.ChatResponse;
import io.campuscore.restfulapi.thesis.service.ThesisLecturerWorkloadService;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
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
    void examTimetableQuestionsNeverResolveAsPersonalSchedule() {
        // "Lịch thi cuối kỳ" shares the "lịch" noun with personal timetables
        // but is a public knowledge topic — the advisor must stay out of it so
        // the asker's own class rows are never presented as the exam schedule.
        assertFalse(advisor.handles("Lịch thi cuối kỳ khi nào?"));
        assertFalse(advisor.handles("lịch thi kết thúc học phần học kỳ này"));
        assertFalse(advisor.handles("kỳ thi cuối kỳ tổ chức ở đâu?"));
        assertFalse(advisor.handles("When is the final exam schedule?"));
        // Bare "lịch" without a qualifier is not a personal-timetable intent
        // either; it belongs to the knowledge path like other ambiguous asks.
        assertFalse(advisor.handles("lịch thi cuối kỳ"));
        // The personal timetable intents that already worked must keep working
        // after the bare-noun fix.
        assertTrue(advisor.handles("Lịch của tôi thế nào?"));
        assertTrue(advisor.handles("xem lịch học của tôi"));
    }

    @Test
    void detectsThesisWorkloadIntentsInVietnameseAndEnglish() {
        assertTrue(advisor.handles("Tôi có đề tài đồ án nào đang hướng dẫn?"));
        assertTrue(advisor.handles("đề tài khóa luận của tôi"));
        assertTrue(advisor.handles("tôi đang hướng dẫn đề tài nào"));
        assertTrue(advisor.handles("What thesis topics do I supervise?"));
        assertTrue(advisor.handles("my thesis topics"));

        // General thesis policy questions must NOT be intercepted as personal workload
        assertFalse(advisor.handles("Điều kiện làm khóa luận tốt nghiệp là gì?"));
        assertFalse(advisor.handles("Điều kiện đăng ký đề tài là gì?"));
        assertFalse(advisor.handles("Quy định điểm GPA để làm KLTN?"));
        assertFalse(advisor.handles("Các đề tài khóa trước về Trí tuệ nhân tạo là gì?"));
        assertFalse(advisor.handles("Cho tôi xem các đề tài khóa trước để tham khảo"));
        assertFalse(advisor.handles("Đề tài khóa trước đạt điểm xuất sắc của khoa CNTT"));
        assertFalse(advisor.handles("Kho lưu trữ đề tài khóa luận của trường"));
    }

    @Test
    void answersLecturerThesisWorkloadInVietnamese() {
        ThesisLecturerWorkloadService workloadService = mock(ThesisLecturerWorkloadService.class);
        AssistantPersonalContextAdvisor thesisAdvisor =
                new AssistantPersonalContextAdvisor(enrollmentService, sectionService, workloadService, null, null);

        var topic = new ThesisLecturerWorkloadService.SupervisedTopic(
                UUID.randomUUID(), "Hệ thống AI gợi ý học tập", "PUBLISHED",
                UUID.randomUUID(), "Đợt 1 KLTN 2026", "REGISTRATION_OPEN",
                null, 2, 1);
        var council = new ThesisLecturerWorkloadService.CouncilAssignment(
                UUID.randomUUID(), "Hội đồng 01 - KTPM", "CHAIR",
                UUID.randomUUID(), "Đợt 1 KLTN 2026", "REGISTRATION_OPEN",
                null, null, 5);
        when(workloadService.workload("lecturer-profile")).thenReturn(
                new ThesisLecturerWorkloadService.LecturerWorkload(List.of(topic), List.of(council), List.of()));

        ChatResponse response = thesisAdvisor.answer(chatRequest("vi", "Tôi có đề tài đồ án nào đang hướng dẫn?"), jwtLecturer());

        assertNotNull(response);
        assertEquals("PERSONAL_CONTEXT", response.reasonCode());
        String answer = response.answer();
        assertTrue(answer.contains("Hệ thống AI gợi ý học tập"));
        assertTrue(answer.contains("Đợt 1 KLTN 2026"));
        assertTrue(answer.contains("Hội đồng 01 - KTPM"));
        assertTrue(answer.contains("CHAIR"));
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
    void streamsTheAlreadyComputedAnswerWithoutReadingPersonalRecordsAgain() {
        when(enrollmentService.findStudentEnrollments("student-profile", null)).thenReturn(List.of(
                enrollment("SE401", "Lập trình Java nâng cao", "Advanced Java", CURRENT_TERM_START,
                        List.of(new SectionScheduleResponse("s1", 2, "07:00", "09:30",
                                new ClassroomSummary("c1", "A", "101"))))));

        ChatRequest request = chatRequest("vi", "Lịch học của tôi tuần này?");
        ChatResponse response = advisor.answer(request, jwtStudent());
        List<ThesisAssistantService.StreamEvent> events = new ArrayList<>();

        advisor.stream(response, request, events::add);

        assertTrue(events.stream().anyMatch(event -> event instanceof ThesisAssistantService.StreamReplace replace
                && replace.text().equals(response.answer())
                && "PERSONAL_CONTEXT".equals(replace.reasonCode())));
        assertTrue(events.stream().anyMatch(event -> event instanceof ThesisAssistantService.StreamDone done
                && "PERSONAL_CONTEXT".equals(done.reasonCode())
                && !done.degraded()));
        org.mockito.Mockito.verify(enrollmentService).findStudentEnrollments("student-profile", null);
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
    void returnsProfessionalFallbackWhenPersonalDataSourceIsUnavailable() {
        when(enrollmentService.findStudentEnrollments("student-profile", null))
                .thenThrow(new DataAccessResourceFailureException("academic schema unavailable"));

        ChatResponse response = advisor.answer(chatRequest("vi", "Cho toi xem lich hoc"), jwtStudent());

        assertNotNull(response);
        assertTrue(response.degraded());
        assertEquals("PERSONAL_CONTEXT_UNAVAILABLE", response.reasonCode());
        assertTrue(response.citations().isEmpty());
        assertTrue(response.answer().contains("chưa xem được dữ liệu cá nhân"), response.answer());
    }

    @Test
    void detectsGradesAndConductIntentsInVietnameseAndEnglish() {
        assertTrue(advisor.handles("điểm của tôi thế nào?"));
        assertTrue(advisor.handles("cho xem bảng điểm của tôi"));
        assertTrue(advisor.handles("GPA của tôi bao nhiêu?"));
        assertTrue(advisor.handles("kết quả học tập của tôi"));
        assertTrue(advisor.handles("what are my grades?"));
        assertTrue(advisor.handles("show my gpa"));
        assertTrue(advisor.handles("điểm rèn luyện của tôi mấy điểm?"));
        assertTrue(advisor.handles("DRL của tôi"));

        // Policy and non-personal questions stay on the knowledge path.
        assertFalse(advisor.handles("quy chế đào tạo nói gì về điểm A?"));
        assertFalse(advisor.handles("Quy định điểm GPA để làm KLTN?"));
        assertFalse(advisor.handles("Học phí kỳ này bao nhiêu?"));
        assertFalse(advisor.handles("điểm chuẩn ngành CNTT năm ngoái"));
    }

    @Test
    void detectsEnrollmentListIntentsInVietnamese() {
        assertTrue(advisor.handles("Học kỳ này tôi đang đăng ký những lớp học phần nào?"));
        assertTrue(advisor.handles("tôi đã đăng ký những lớp nào"));
        assertTrue(advisor.handles("tôi dang ky nhung lop nao roi?"));
        assertTrue(advisor.handles("what classes am I registered in?"));

        // Schedule wording stays handled by this advisor's timetable branch
        // (pre-existing SCHEDULE_INTENT) rather than the new enrollment-list one.
        assertTrue(advisor.handles("lịch học tuần này của tôi"));
        // How-to / policy wording about registration stays on the knowledge path.
        assertFalse(advisor.handles("tôi muốn biết cách đăng ký học phần"));
        assertFalse(advisor.handles("cho tôi hướng dẫn đăng ký học phần với"));
        assertFalse(advisor.handles("lớp học phần SE401 còn chỗ không"));
    }

    @Test
    void answersCurrentTermEnrollmentListInVietnamese() {
        when(enrollmentService.findStudentEnrollments("student-profile", null)).thenReturn(List.of(
                enrollment("SE401", "Lập trình Java nâng cao", "Advanced Java", CURRENT_TERM_START,
                        List.of(new SectionScheduleResponse("s1", 2, "07:00", "09:30",
                                new ClassroomSummary("c1", "A", "101")))),
                enrollment("SE403", "Cấu trúc dữ liệu và giải thuật", "Data Structures", CURRENT_TERM_START,
                        List.of()),
                enrollment("SE201", "Công nghệ phần mềm", "Software Engineering", OLD_TERM_START,
                        List.of(new SectionScheduleResponse("s3", 4, "13:00", "15:30",
                                new ClassroomSummary("c3", "B", "202"))))));

        ChatResponse response = advisor.answer(
                chatRequest("vi", "Học kỳ này tôi đang đăng ký những lớp học phần nào?"), jwtStudent());

        assertNotNull(response);
        assertEquals("PERSONAL_CONTEXT", response.reasonCode());
        assertTrue(response.citations().isEmpty());
        String answer = response.answer();
        assertTrue(answer.contains("Học kỳ này bạn đang đăng ký 2 lớp (6 tín chỉ theo dữ liệu đăng ký của bạn)"), answer);
        assertTrue(answer.contains("SE401 - Lập trình Java nâng cao — lớp SE401-01 — trạng thái ENROLLED"), answer);
        assertTrue(answer.contains("SE403 - Cấu trúc dữ liệu và giải thuật — lớp SE403-01"), answer);
        assertFalse(answer.contains("SE201"), "older-term enrollments must not leak into the current list");
        assertTrue(answer.contains("tính trực tiếp từ hồ sơ đăng ký học phần"), answer);
    }

    @Test
    void enrollmentListCreditsComeFromTheRegistrationSnapshotWhenAvailable() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        RegistrationService registrationService = mock(RegistrationService.class);
        AssistantPersonalContextAdvisor ledgerAdvisor = new AssistantPersonalContextAdvisor(
                enrollmentService, sectionService, null, jdbc, null, registrationService);
        when(enrollmentService.findStudentEnrollments("student-profile", null)).thenReturn(List.of(
                enrollment("SE013", "Lập trình Web nâng cao", "Advanced Web", CURRENT_TERM_START, List.of()),
                enrollment("SE014", "Kiến trúc Microservices", "Microservices", CURRENT_TERM_START, List.of())));
        // Credits must match the registration summary endpoint: the CURRENT
        // course credits of each enrolled section (3 + 3 = 6), never the stale
        // enrollment snapshot.
        ChatResponse response = ledgerAdvisor.answer(
                chatRequest("vi", "Học kỳ này tôi đang đăng ký những lớp học phần nào?"), jwtStudent());

        assertTrue(response.answer().contains("(6 tín chỉ theo dữ liệu đăng ký của bạn)"),
                response.answer());
        assertFalse(response.answer().contains("(15 tín chỉ"), response.answer());
    }

    @Test
    void answersFriendlyEmptyMessageWhenNoCurrentTermEnrollment() {
        when(enrollmentService.findStudentEnrollments("student-profile", null)).thenReturn(List.of(
                enrollmentWithStatus("SE401", "COMPLETED", CURRENT_TERM_START, List.of())));

        ChatResponse response = advisor.answer(chatRequest("vi", "tôi đã đăng ký những lớp nào"), jwtStudent());

        assertNotNull(response);
        assertEquals("PERSONAL_CONTEXT", response.reasonCode());
        assertTrue(response.answer().contains("chưa đăng ký lớp học phần nào trong học kỳ hiện tại"),
                response.answer());
    }

    @Test
    void answersStudentGradesFromRealRows() {
        when(enrollmentService.findStudentGrades("student-profile", null)).thenReturn(List.of(
                new AcademicEnrollmentReadDtos.GradeSummary(
                        "g1", "SE401", "Lập trình Java nâng cao", "Advanced Java",
                        "Lập trình Java nâng cao", 3, "SE401-01", "ThS. Demo",
                        "sem-2026a", "Semester A", "Học kỳ A", "sem-2026a",
                        new java.math.BigDecimal("8.5"), new java.math.BigDecimal("9.0"),
                        new java.math.BigDecimal("8.7"), "A", "PUBLISHED", "COMPLETED"),
                new AcademicEnrollmentReadDtos.GradeSummary(
                        "g2", "SE403", "Cấu trúc dữ liệu và giải thuật", "Data Structures",
                        "Cấu trúc dữ liệu và giải thuật", 3, "SE403-01", "TS. Demo",
                        "sem-2026a", "Semester A", "Học kỳ A", "sem-2026a",
                        new java.math.BigDecimal("7.0"), new java.math.BigDecimal("6.5"),
                        new java.math.BigDecimal("6.7"), null, "PENDING", "ENROLLED")));

        ChatResponse response = advisor.answer(chatRequest("vi", "điểm của tôi thế nào?"), jwtStudent());

        assertNotNull(response);
        assertEquals("PERSONAL_CONTEXT", response.reasonCode());
        String answer = response.answer();
        assertTrue(answer.contains("Kết quả học tập của bạn"), answer);
        // D-Q3: cumulative GPA4 + credits are computed IN CODE from the grade
        // rows (only lettered rows count), never read from a model or a summary.
        assertTrue(answer.contains("Tích lũy: 3 tín chỉ, GPA 4.00 (thang 4)"), answer);
        assertTrue(answer.contains("Học kỳ gần nhất (Học kỳ A): 3 tín chỉ, GPA 4.00 (thang 4)"), answer);
        assertTrue(answer.contains("SE401"), answer);
        assertTrue(answer.contains("8.7 (A)"), answer);
        assertTrue(answer.contains("chưa công bố"), answer);
    }

    @Test
    void gradesAnswerSeparatesCumulativeFromLatestSemester() {
        // Newest semester first, matching the repository order
        // (academic year DESC, semester startDate DESC). The newest term is
        // still in progress (no letters) and must not shadow the last graded one.
        when(enrollmentService.findStudentGrades("student-profile", null)).thenReturn(List.of(
                gradeRow("s0", "SE013", 3, null, "sem-cur", "HK1 2026-2027"),
                gradeRow("s1x", "SE501", 3, "A", "sem-b", "HK2 2025-2026"),
                gradeRow("s1y", "SE502", 3, "B", "sem-b", "HK2 2025-2026"),
                gradeRow("s1w", "SE503", 2, "F", "sem-b", "HK2 2025-2026"),
                gradeRow("s2z", "SE101", 3, "C", "sem-a", "HK1 2025-2026")));

        ChatResponse response = advisor.answer(chatRequest("vi", "GPA của tôi bao nhiêu?"), jwtStudent());

        String answer = response.answer();
        // Cumulative: (4*3 + 3*3 + 0*2 + 2*3) / 11 = 27/11 = 2.45; earned (non-F) = 9.
        assertTrue(answer.contains("Tích lũy: 9 tín chỉ, GPA 2.45 (thang 4)"), answer);
        // Latest semester WITH published grades only: (4*3 + 3*3 + 0*2) / 8 = 21/8 = 2.63; earned = 6.
        assertTrue(answer.contains("Học kỳ gần nhất (HK2 2025-2026): 6 tín chỉ, GPA 2.63 (thang 4)"), answer);
        assertFalse(answer.contains("Học kỳ gần nhất (HK1 2026-2027)"),
                "an in-progress term must not be reported as the latest graded semester");
    }

    @Test
    void answersNoGradesMessageWhenTheStudentHasNone() {
        when(enrollmentService.findStudentGrades("student-profile", null)).thenReturn(List.of());

        ChatResponse response = advisor.answer(chatRequest("vi", "bảng điểm của tôi"), jwtStudent());

        assertNotNull(response);
        assertTrue(response.answer().contains("chưa có điểm học phần nào"), response.answer());
    }

    @Test
    void answersStudentConductFromRealRows() {
        AcademicConductService conductService = mock(AcademicConductService.class);
        AssistantPersonalContextAdvisor conductAdvisor =
                new AssistantPersonalContextAdvisor(enrollmentService, sectionService, null, null, conductService);
        var semesterScore = new io.campuscore.restfulapi.academic.web.AcademicConductDtos.ConductSemesterScoreDto(
                "cs1", "sem-2026a", "Học kỳ A",
                new java.math.BigDecimal("20"), new java.math.BigDecimal("25"),
                new java.math.BigDecimal("8"), new java.math.BigDecimal("22"),
                new java.math.BigDecimal("10"), new java.math.BigDecimal("85"),
                "Tốt", "Tốt", "FINALIZED", null,
                List.of(), List.of());
        when(conductService.studentSummary("student-profile")).thenReturn(
                new io.campuscore.restfulapi.academic.web.AcademicConductDtos.StudentConductSummaryDto(
                        "student-profile", "SV001", "Nguyễn Văn A",
                        new java.math.BigDecimal("85.0"), "Tốt",
                        semesterScore,
                        List.of(semesterScore)));

        ChatResponse response = conductAdvisor.answer(
                chatRequest("vi", "điểm rèn luyện của tôi mấy điểm?"), jwtStudent());

        assertNotNull(response);
        assertEquals("PERSONAL_CONTEXT", response.reasonCode());
        String answer = response.answer();
        assertTrue(answer.contains("Điểm rèn luyện của bạn"), answer);
        assertTrue(answer.contains("85.0 — Tốt"), answer);
        assertTrue(answer.contains("Học kỳ A"), answer);
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

    @Test
    void detectsCreditsRemainingIntents() {
        assertTrue(advisor.handles("Tôi còn bao nhiêu tín chỉ được đăng ký nữa?"));
        // Verb-first order from the production audit (xrole-2): the old
        // quantity-first pattern missed it and the KB path answered "no data".
        assertTrue(advisor.handles("Học kỳ này tôi còn được đăng ký bao nhiêu tín chỉ nữa?"));
        assertTrue(advisor.handles("con duoc dang ky bao nhieu tin chi nua"));
        assertTrue(advisor.handles("con lai bao nhieu tin chi"));
        assertTrue(advisor.handles("còn thiếu mấy tín chỉ"));
        assertTrue(advisor.handles("hạn mức tín chỉ của tôi"));
        assertTrue(advisor.handles("how many credits do I have left?"));
        // Policy wording still stays on the knowledge path.
        assertFalse(advisor.handles("Quy trình xin nâng hạn mức tín chỉ?"));

        // Policy / how-to wording stays on the knowledge path.
        assertFalse(advisor.handles("cách đăng ký học phần"));
        assertFalse(advisor.handles("quy trình xin nâng hạn mức tín chỉ như thế nào?"));
        assertFalse(advisor.handles("học phí kỳ này bao nhiêu?"));
    }

    @Test
    void answersCreditsRemainingFromTheRegistrationSummaryPath() {
        RegistrationService registrationService = mock(RegistrationService.class);
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        AssistantPersonalContextAdvisor summaryAdvisor = new AssistantPersonalContextAdvisor(
                enrollmentService, sectionService, null, jdbc, null, registrationService);
        when(registrationService.summary("student-profile", null)).thenReturn(
                new io.campuscore.restfulapi.academic.registration.RegistrationDtos.SummaryResponse(
                        "round-1", 30, 15, 15, List.of("e1", "e2", "e3", "e4", "e5")));
        // The approval provenance is asserted only when an APPROVED
        // application exists; the ledger is queried before the wording.
        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Integer.class)))
                .thenReturn(1);

        ChatResponse response = summaryAdvisor.answer(
                chatRequest("vi", "Tôi còn bao nhiêu tín chỉ được đăng ký nữa?"), jwtStudent());

        assertNotNull(response);
        assertEquals("PERSONAL_CONTEXT", response.reasonCode());
        assertTrue(response.citations().isEmpty());
        String answer = response.answer();
        assertTrue(answer.contains("Đã đăng ký: 15 tín chỉ"), answer);
        assertTrue(answer.contains("Hạn mức: 30 tín chỉ"), answer);
        assertTrue(answer.contains("Còn lại có thể đăng ký: 15 tín chỉ"), answer);
        // limit 30 with an approved application: the note names the approval.
        assertTrue(answer.contains("đơn xin nâng hạn mức đã được duyệt"), answer);
        assertTrue(answer.contains("28"), answer);
        assertTrue(answer.contains("hồ sơ đăng ký học phần"), answer);
    }

    @Test
    void creditsRemainingAtTheStandardLimitOmitsTheApprovedNote() {
        RegistrationService registrationService = mock(RegistrationService.class);
        AssistantPersonalContextAdvisor summaryAdvisor = new AssistantPersonalContextAdvisor(
                enrollmentService, sectionService, null, null, null, registrationService);
        when(registrationService.summary("student-profile", null)).thenReturn(
                new io.campuscore.restfulapi.academic.registration.RegistrationDtos.SummaryResponse(
                        "round-1", 28, 15, 13, List.of("e1")));

        ChatResponse response = summaryAdvisor.answer(
                chatRequest("vi", "còn thiếu mấy tín chỉ nữa là chạm hạn mức?"), jwtStudent());

        assertTrue(response.answer().contains("Còn lại có thể đăng ký: 13 tín chỉ"), response.answer());
        assertFalse(response.answer().contains("phê duyệt"), response.answer());
    }

    @Test
    void creditsRemainingWithoutTheSummaryPathFallsBackToKnowledge() {
        assertNull(advisor.answer(chatRequest("vi", "Tôi còn bao nhiêu tín chỉ được đăng ký nữa?"), jwtStudent()));
    }

    @Test
    void detectsSectionDetailIntents() {
        assertTrue(advisor.handles("Lớp SE013 học phòng nào, giờ nào?"));
        assertTrue(advisor.handles("lớp SE014-01 học thứ mấy, ở phòng nào?"));
        assertTrue(advisor.handles("Lịch của lớp SE015 thế nào?"));

        // Code without a room/time question stays off this intent (and off the
        // advisor entirely — no other intent claims "còn chỗ" questions).
        assertFalse(advisor.handles("lớp học phần SE401 còn chỗ trống không"));
    }

    @Test
    void answersRegisteredSectionDetailFromTheTimetableData() {
        when(enrollmentService.findStudentEnrollments("student-profile", null)).thenReturn(List.of(
                enrollment("SE013", "Lập trình Web nâng cao với React & Node.js",
                        "Advanced Web with React & Node.js", CURRENT_TERM_START,
                        List.of(new SectionScheduleResponse("s1", 2, "09:45", "12:15",
                                new ClassroomSummary("c1", "A", "103"))))));

        ChatResponse response = advisor.answer(
                chatRequest("vi", "Lớp SE013 học phòng nào, giờ nào?"), jwtStudent());

        assertNotNull(response);
        assertEquals("PERSONAL_CONTEXT", response.reasonCode());
        String answer = response.answer();
        assertTrue(answer.contains("Thông tin lớp SE013"), answer);
        assertTrue(answer.contains("Thứ Hai 09:45-12:15"), answer);
        assertTrue(answer.contains("(phòng A 103)"), answer);
        assertTrue(answer.contains("theo thời khóa biểu đã đăng ký của bạn"), answer);
        assertFalse(answer.contains("không tìm thấy"), answer);
    }

    @Test
    void answersCatalogSectionThatTheStudentHasNotRegistered() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        AssistantPersonalContextAdvisor catalogAdvisor = new AssistantPersonalContextAdvisor(
                enrollmentService, sectionService, null, jdbc, null, null);
        when(enrollmentService.findStudentEnrollments("student-profile", null)).thenReturn(List.of(
                enrollment("SE401", "Lập trình Java nâng cao", "Advanced Java", CURRENT_TERM_START, List.of())));
        Map<String, Object> row = new HashMap<>();
        row.put("section_number", "SE099-01");
        row.put("course_code", "SE099");
        row.put("course_name", "Học máy ứng dụng");
        row.put("course_name_vi", "Học máy ứng dụng");
        row.put("course_name_en", "Applied Machine Learning");
        row.put("semester_name", "Học kỳ hiện tại");
        row.put("schedule_day", 3);
        row.put("schedule_start", "07:00");
        row.put("schedule_end", "09:30");
        row.put("room_building", "B");
        row.put("room_number", "204");
        when(jdbc.queryForList(anyString(), any(MapSqlParameterSource.class))).thenReturn(List.of(row));

        ChatResponse response = catalogAdvisor.answer(
                chatRequest("vi", "Lớp SE099 học phòng nào, giờ nào?"), jwtStudent());

        String answer = response.answer();
        assertTrue(answer.contains("SE099"), answer);
        assertTrue(answer.contains("có trong danh mục"), answer);
        assertTrue(answer.contains("chưa đăng ký lớp này"), answer);
        assertTrue(answer.contains("Thứ Ba 07:00-09:30"), answer);
        assertTrue(answer.contains("(phòng B 204)"), answer);
    }

    @Test
    void unknownSectionCodeSaysSoAndPointsToRegistrationWithoutClaimingAbsence() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        AssistantPersonalContextAdvisor catalogAdvisor = new AssistantPersonalContextAdvisor(
                enrollmentService, sectionService, null, jdbc, null, null);
        when(enrollmentService.findStudentEnrollments("student-profile", null)).thenReturn(List.of());
        when(jdbc.queryForList(anyString(), any(MapSqlParameterSource.class))).thenReturn(List.of());

        ChatResponse response = catalogAdvisor.answer(
                chatRequest("vi", "Lớp XX999 học phòng nào, giờ nào?"), jwtStudent());

        String answer = response.answer();
        assertTrue(answer.contains("không tìm thấy mã lớp XX999"), answer);
        assertTrue(answer.contains("Đăng ký học phần"), answer);
    }

    @Test
    void detectsThesisNounLuanVanAndAnswersOnlyFromRealRows() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        AssistantPersonalContextAdvisor thesisAdvisor = new AssistantPersonalContextAdvisor(
                enrollmentService, sectionService, null, jdbc, null, null);

        assertTrue(thesisAdvisor.handles("Đồ án/luận văn của tôi đang tiến triển thế nào?"));
        assertTrue(thesisAdvisor.handles("luan van cua toi the nao roi"));

        // D-Q6: with no registration rows the answer says so — nothing invented.
        when(jdbc.queryForList(anyString(), any(MapSqlParameterSource.class))).thenReturn(List.of());
        ChatResponse empty = thesisAdvisor.answer(
                chatRequest("vi", "luận văn của tôi đang tiến triển thế nào?"), jwtStudent());
        assertNotNull(empty);
        assertTrue(empty.answer().contains("Bạn chưa đăng ký đồ án/luận văn trong đợt nào"), empty.answer());
        assertTrue(empty.answer().contains("Khóa luận tốt nghiệp"), empty.answer());
        assertFalse(empty.answer().contains("PENDING"), empty.answer());

        // With rows, every shown value comes from those rows.
        Map<String, Object> row = new HashMap<>();
        row.put("group_id", "g1");
        row.put("group_status", "ACTIVE");
        row.put("approval_status", "APPROVED");
        row.put("topic_id", "t1");
        row.put("topic_title", "Hệ thống gợi ý học tập");
        row.put("topic_status", "APPROVED");
        row.put("final_score", null);
        row.put("round_name", "Đợt 1 KLTN 2026-2027");
        row.put("round_status", "REGISTRATION_OPEN");
        when(jdbc.queryForList(anyString(), any(MapSqlParameterSource.class))).thenReturn(List.of(row));
        ChatResponse grounded = thesisAdvisor.answer(
                chatRequest("vi", "luận văn của tôi đang tiến triển thế nào?"), jwtStudent());
        assertTrue(grounded.answer().contains("Đợt 1 KLTN 2026-2027"), grounded.answer());
        assertTrue(grounded.answer().contains("Hệ thống gợi ý học tập"), grounded.answer());
        assertTrue(grounded.answer().contains("APPROVED"), grounded.answer());
    }

    // ------------------------------------------------------------------
    // giang-vien-1-5: the five verified lecturer phrasings that were wrongly
    // rejected to RAG although the APIs hold the asker's own data.
    // ------------------------------------------------------------------

    @Test
    void detectsLecturerPersonalIntents() {
        assertTrue(advisor.handles("Kỳ này tôi phụ trách dạy những lớp học phần nào?"));
        assertTrue(advisor.handles("Khối lượng hướng dẫn của tôi hiện tại là bao nhiêu?"));
        assertTrue(advisor.handles("Điểm học phần tôi phụ trách hiện đã có chưa?"));
        assertTrue(advisor.handles("Tôi đang hướng dẫn tổng cộng bao nhiêu sinh viên?"));
        assertTrue(advisor.handles("Sinh viên lớp SE401 hôm nay có ai vắng mặt không?"));

        // Public knowledge topics stay off the personal path: exam timetables,
        // admission cut-offs, and policy wordings (supervision cap, attendance
        // rules) must never be answered from the asker's own rows.
        assertFalse(advisor.handles("lịch thi cuối kỳ"));
        assertFalse(advisor.handles("Em hỏi điểm chuẩn ngành X"));
        assertFalse(advisor.handles("Trường quy định bao nhiêu sinh viên hướng dẫn tối đa?"));
        assertFalse(advisor.handles("Quy định chuyên cần lớp SE401 như thế nào?"));
    }

    @Test
    void answersLecturerSectionListFromTeachingAssignments() {
        when(sectionService.findLecturerSchedule("lecturer-profile", null)).thenReturn(List.of(
                new LecturerScheduleResponse("id1", "sec1", "SE402-01", "SE402", "Phát triển ứng dụng web",
                        "Web Application Development", "Phát triển ứng dụng web", 3, 35, 13, "CNTT", "CNTT", "CNTT",
                        "OPEN", List.of(new io.campuscore.restfulapi.academic.web.AcademicSectionReadDtos
                                .SectionScheduleResponse("s1", 5, "13:00", "15:30", "A", "102",
                                new io.campuscore.restfulapi.academic.web.AcademicSectionReadDtos
                                        .ClassroomSummary("c1", "A", "102"))))));

        ChatResponse response = advisor.answer(
                chatRequest("vi", "Kỳ này tôi phụ trách dạy những lớp học phần nào?"), jwtLecturer());

        // Regression: before the fix the enrollment-list branch returned null
        // for a lecturer and the question fell to RAG despite 16 assigned rows.
        assertNotNull(response);
        assertEquals("PERSONAL_CONTEXT", response.reasonCode());
        assertTrue(response.answer().contains("SE402"), response.answer());
    }

    @Test
    void answersLecturerWorkloadSummaryWithoutInternalTerms() {
        ThesisLecturerWorkloadService workloadService = mock(ThesisLecturerWorkloadService.class);
        AssistantPersonalContextAdvisor workloadAdvisor =
                new AssistantPersonalContextAdvisor(enrollmentService, sectionService, workloadService, null, null);
        var topicA = new ThesisLecturerWorkloadService.SupervisedTopic(
                UUID.randomUUID(), "Đề tài A", "PUBLISHED",
                UUID.randomUUID(), "Đợt 1 KLTN 2026", "REGISTRATION_OPEN", null, 3, 1);
        var topicB = new ThesisLecturerWorkloadService.SupervisedTopic(
                UUID.randomUUID(), "Đề tài B", "PUBLISHED",
                UUID.randomUUID(), "Đợt 1 KLTN 2026", "REGISTRATION_OPEN", null, 2, 0);
        when(workloadService.workload("lecturer-profile")).thenReturn(
                new ThesisLecturerWorkloadService.LecturerWorkload(List.of(topicA, topicB), List.of(), List.of()));

        ChatResponse response = workloadAdvisor.answer(
                chatRequest("vi", "Khối lượng hướng dẫn của tôi hiện tại là bao nhiêu?"), jwtLecturer());

        assertNotNull(response);
        assertEquals("PERSONAL_CONTEXT", response.reasonCode());
        String answer = response.answer();
        // Totals are summed in code over the topics: 3 + 2 = 5 groups, 1 pending.
        assertTrue(answer.contains("tổng cộng 5 nhóm sinh viên trên 2 đề tài"), answer);
        assertTrue(answer.contains("1 nhóm đang chờ duyệt"), answer);
        // The group number is a GROUP count — the wording must never claim a
        // raw student headcount, and no internal field name may leak.
        assertFalse(answer.contains("groupCount"), answer);
        assertTrue(answer.contains("Đề tài A"), answer);
    }

    @Test
    void answersLecturerGradingStatusFromAssignedSections() {
        when(sectionService.findLecturerGradingSections("lecturer-profile", null)).thenReturn(List.of(
                new LecturerGradingSectionResponse("id1", "sec1", "SE402-01", "SE402",
                        "Phát triển ứng dụng web", "Web Application Development", "Phát triển ứng dụng web",
                        3, "CNTT", "CNTT", "CNTT",
                        "HK1 2026-2027", "HK1 2026-2027", "HK1 2026-2027", "HK1 2026-2027",
                        35L, 20L, 12L, "PARTIAL", true)));

        ChatResponse response = advisor.answer(
                chatRequest("vi", "Điểm học phần tôi phụ trách hiện đã có chưa?"), jwtLecturer());

        assertNotNull(response);
        assertEquals("PERSONAL_CONTEXT", response.reasonCode());
        String answer = response.answer();
        assertTrue(answer.contains("20/35 SV đã có điểm"), answer);
        assertTrue(answer.contains("12 đã công bố"), answer);
        assertTrue(answer.contains("PARTIAL"), answer);
    }

    @Test
    void answersLecturerGradingWithoutAssignmentHonestly() {
        when(sectionService.findLecturerGradingSections("lecturer-profile", null)).thenReturn(List.of());

        ChatResponse response = advisor.answer(
                chatRequest("vi", "Điểm học phần tôi phụ trách hiện đã có chưa?"), jwtLecturer());

        assertNotNull(response);
        assertTrue(response.answer().contains("chưa được phân công nhập điểm"), response.answer());
    }

    @Test
    void answersLecturerAttendanceAbsencesForToday() {
        AcademicAttendanceReadService attendanceService = mock(AcademicAttendanceReadService.class);
        AssistantPersonalContextAdvisor attendanceAdvisor = new AssistantPersonalContextAdvisor(
                enrollmentService, sectionService, null, null, null, null, attendanceService);
        when(sectionService.findLecturerSchedule("lecturer-profile", null)).thenReturn(List.of(
                new LecturerScheduleResponse("id1", "sec-401", "SE401-01", "SE401",
                        "Lập trình Java nâng cao", "Advanced Java", "Lập trình Java nâng cao",
                        3, 45, 40, "CNTT", "CNTT", "CNTT", "OPEN", List.of())));
        when(attendanceService.findLecturerAttendance(eq("lecturer-profile"), eq("sec-401"), anyString()))
                .thenReturn(List.of(new AcademicAttendanceReadDtos.AttendanceResponse(
                        "att1", "student-1", "sec-401", Instant.now(), "ABSENT", "Không lý do", Instant.now(),
                        new AcademicAttendanceReadDtos.StudentSummary("student-1", "SV001",
                                new AcademicAttendanceReadDtos.UserSummary("u1", "sv001@campuscore.edu",
                                        "Văn A", "Nguyễn")),
                        new AcademicAttendanceReadDtos.SectionSummary("sec-401", "SE401-01", "sem1",
                                new AcademicAttendanceReadDtos.CourseSummary("c1", "SE401",
                                        "Lập trình Java nâng cao", null, null)))));

        ChatResponse response = attendanceAdvisor.answer(
                chatRequest("vi", "Sinh viên lớp SE401 hôm nay có ai vắng mặt không?"), jwtLecturer());

        assertNotNull(response);
        assertEquals("PERSONAL_CONTEXT", response.reasonCode());
        String answer = response.answer();
        assertTrue(answer.contains("Sinh viên vắng mặt lớp SE401"), answer);
        assertTrue(answer.contains("Nguyễn Văn A (SV001)"), answer);
        assertTrue(answer.contains("Không lý do"), answer);
    }

    @Test
    void answersLecturerAttendanceWithoutAbsencesAndWithoutData() {
        AcademicAttendanceReadService attendanceService = mock(AcademicAttendanceReadService.class);
        AssistantPersonalContextAdvisor attendanceAdvisor = new AssistantPersonalContextAdvisor(
                enrollmentService, sectionService, null, null, null, null, attendanceService);
        when(sectionService.findLecturerSchedule("lecturer-profile", null)).thenReturn(List.of(
                new LecturerScheduleResponse("id1", "sec-401", "SE401-01", "SE401",
                        "Lập trình Java nâng cao", "Advanced Java", "Lập trình Java nâng cao",
                        3, 45, 40, "CNTT", "CNTT", "CNTT", "OPEN", List.of())));
        when(attendanceService.findLecturerAttendance(eq("lecturer-profile"), eq("sec-401"), anyString()))
                .thenReturn(List.of(attendanceRow("PRESENT")));

        ChatResponse allPresent = attendanceAdvisor.answer(
                chatRequest("vi", "Sinh viên lớp SE401 hôm nay có ai vắng mặt không?"), jwtLecturer());
        assertTrue(allPresent.answer().contains("Không có sinh viên nào vắng mặt"), allPresent.answer());

        when(attendanceService.findLecturerAttendance(eq("lecturer-profile"), eq("sec-401"), anyString()))
                .thenReturn(List.of());
        ChatResponse noData = attendanceAdvisor.answer(
                chatRequest("vi", "Sinh viên lớp SE401 hôm nay có ai vắng mặt không?"), jwtLecturer());
        assertTrue(noData.answer().contains("chưa thấy dữ liệu điểm danh"), noData.answer());
    }

    @Test
    void lecturerAttendanceForAnUnassignedCodeSaysSoWithoutGuessing() {
        AcademicAttendanceReadService attendanceService = mock(AcademicAttendanceReadService.class);
        AssistantPersonalContextAdvisor attendanceAdvisor = new AssistantPersonalContextAdvisor(
                enrollmentService, sectionService, null, null, null, null, attendanceService);
        when(sectionService.findLecturerSchedule("lecturer-profile", null)).thenReturn(List.of(
                new LecturerScheduleResponse("id1", "sec-401", "SE401-01", "SE401",
                        "Lập trình Java nâng cao", "Advanced Java", "Lập trình Java nâng cao",
                        3, 45, 40, "CNTT", "CNTT", "CNTT", "OPEN", List.of())));

        ChatResponse response = attendanceAdvisor.answer(
                chatRequest("vi", "Sinh viên lớp SE999 hôm nay có ai vắng mặt không?"), jwtLecturer());

        assertNotNull(response);
        String answer = response.answer();
        assertTrue(answer.contains("không tìm thấy lớp SE999"), answer);
        org.mockito.Mockito.verify(attendanceService, org.mockito.Mockito.never())
                .findLecturerAttendance(anyString(), anyString(), anyString());
    }

    /**
     * Objection (1) gate — MANDATORY: a student-actor JWT carries no lecturerId,
     * so all three lecturer branches must return null (fall back to RAG)
     * WITHOUT calling any read service. The services throw 403 on a missing
     * profile claim and answer() only catches DataAccessException, so an
     * unguarded call would turn the question into an HTTP error.
     */
    @Test
    void studentActorNeverTriggersLecturerPersonalBranches() {
        ThesisLecturerWorkloadService workloadService = mock(ThesisLecturerWorkloadService.class);
        AcademicAttendanceReadService attendanceService = mock(AcademicAttendanceReadService.class);
        AssistantPersonalContextAdvisor guardedAdvisor = new AssistantPersonalContextAdvisor(
                enrollmentService, sectionService, workloadService, null, null, null, attendanceService);
        Jwt studentActor = new Jwt("token", Instant.now(), Instant.now().plusSeconds(600),
                Map.of("alg", "HS256"), new HashMap<>(Map.of(
                        "sub", "someone", "roles", List.of("STUDENT"), "studentId", "student-profile")));

        assertNull(guardedAdvisor.answer(
                chatRequest("vi", "Khối lượng hướng dẫn của tôi hiện tại là bao nhiêu?"), studentActor));
        assertNull(guardedAdvisor.answer(
                chatRequest("vi", "Điểm học phần tôi phụ trách hiện đã có chưa?"), studentActor));
        assertNull(guardedAdvisor.answer(
                chatRequest("vi", "Sinh viên lớp SE401 hôm nay có ai vắng mặt không?"), studentActor));

        org.mockito.Mockito.verifyNoInteractions(workloadService, sectionService, attendanceService);
    }

    // ------------------------------------------------------------------
    // ca-nhan-1: graduation-credit-gap questions must be answered from the
    // student's own transcript, not hijacked into the semester credit limit.
    // ------------------------------------------------------------------

    @Test
    void answersGraduationCreditsRemainingFromRealGrades() {
        when(enrollmentService.findStudentGrades("student-profile", null)).thenReturn(List.of(
                gradeRow("g1", "SE101", 60, "A", "sem-a", "HK1 2025-2026"),
                gradeRow("g2", "SE102", 40, "B", "sem-a", "HK1 2025-2026")));

        ChatResponse response = advisor.answer(
                chatRequest("vi", "Còn thiếu bao nhiêu tín chỉ để đủ 143 tín chỉ tốt nghiệp?"), jwtStudent());

        assertNotNull(response);
        assertEquals("PERSONAL_CONTEXT", response.reasonCode());
        String answer = response.answer();
        assertTrue(answer.contains("còn thiếu 43"), answer);
        assertTrue(answer.contains("100/143"), answer);
        // Regression: the semester registration-budget answer must not leak in.
        assertFalse(answer.contains("Hạn mức đăng ký"), answer);
    }

    @Test
    void graduationRequirementAlreadyMetReportsCompletion() {
        when(enrollmentService.findStudentGrades("student-profile", null)).thenReturn(List.of(
                gradeRow("g1", "SE101", 60, "A", "sem-a", "HK1 2025-2026"),
                gradeRow("g2", "SE102", 40, "B", "sem-a", "HK1 2025-2026")));

        // Requirement 90 below the accumulated 100 — no gap may be invented.
        ChatResponse response = advisor.answer(
                chatRequest("vi", "Còn thiếu bao nhiêu tín chỉ để đủ 90 tín chỉ tốt nghiệp?"), jwtStudent());

        String answer = response.answer();
        assertTrue(answer.contains("đã tích lũy đủ 100 tín chỉ"), answer);
        assertTrue(answer.contains("đáp ứng mức 90 tín chỉ tốt nghiệp"), answer);
        assertFalse(answer.contains("còn thiếu"), answer);
    }

    @Test
    void graduationCreditsWithoutPublishedGradesSaysSo() {
        when(enrollmentService.findStudentGrades("student-profile", null)).thenReturn(List.of());

        ChatResponse response = advisor.answer(
                chatRequest("vi", "Còn thiếu bao nhiêu tín chỉ để đủ 143 tín chỉ tốt nghiệp?"), jwtStudent());

        assertNotNull(response);
        assertTrue(response.answer().contains("chưa có tín chỉ tích lũy nào được công bố"), response.answer());
    }

    @Test
    void singleDigitCreditCountsStayOffTheGraduationIntent() {
        // A one-digit number does not match the 2-3 digit graduation pattern...
        assertFalse(advisor.handles("còn thiếu 7 tín chỉ để đủ tốt nghiệp"));
        // ... and without a graduation number the question keeps the semester
        // registration-budget intent (existing behavior).
        assertTrue(advisor.handles("Tôi còn bao nhiêu tín chỉ được đăng ký nữa?"));
    }

    @Test
    void graduationCreditsWithLecturerActorFallsBackToKnowledge() {
        assertNull(advisor.answer(
                chatRequest("vi", "Còn thiếu bao nhiêu tín chỉ để đủ 143 tín chỉ tốt nghiệp?"), jwtLecturer()));
        org.mockito.Mockito.verifyNoInteractions(enrollmentService);
    }

    // ------------------------------------------------------------------
    // xrole-6 (production audit): the verb-first teaching-list phrasing
    // "Học kỳ này tôi phụ trách những lớp nào?" was rejected because the
    // entry gate never accepted "phụ trách" BEFORE the lớp noun; it fell
    // to the RAG path although the lecturer timetable API has the rows.
    // ------------------------------------------------------------------

    @Test
    void detectsVerbFirstTeachingListPhrasing() {
        assertTrue(advisor.handles("Học kỳ này tôi phụ trách những lớp nào?"));
        assertTrue(advisor.handles("hoc ky nay toi phu trach nhung lop nao"));
        assertTrue(advisor.handles("Kỳ này tôi phụ trách lớp học phần nào?"));
        // A public "who is in charge of this class" question has no
        // interrogative after the lớp noun and no first-person pronoun —
        // it must stay on the knowledge path, not open the personal gate.
        assertFalse(advisor.handles("Giáo viên phụ trách lớp này là ai?"));
        // Wukong round-4: the third-person form WITHOUT the "nào" tail is the
        // same public question — the new branch must not swallow it either.
        assertFalse(advisor.handles("Giáo viên phụ trách lớp nào?"));
        assertFalse(advisor.handles("Khoa nào phụ trách lớp học phần nào trong học kỳ?"));
    }

    @Test
    void workloadIntentStaysOffHowToAndAdviceWording() {
        // Naming supervision groups inside a how-to/advice question is a
        // knowledge ask — the workload list answers "what is my workload",
        // never "should I" or "where is the page".
        assertFalse(advisor.handles("Tôi duyệt nhóm hướng dẫn ở trang nào?"));
        assertFalse(advisor.handles("Tôi có nên lập nhóm hướng dẫn mới không?"));
        assertTrue(advisor.handles("Những nhóm sinh viên nào tôi đang hướng dẫn?"));
    }

    @Test
    void answersVerbFirstTeachingListFromLecturerAssignments() {
        when(sectionService.findLecturerSchedule("lecturer-profile", null)).thenReturn(List.of(
                new LecturerScheduleResponse("id1", "sec1", "SE402-01", "SE402", "Phát triển ứng dụng web",
                        "Web Application Development", "Phát triển ứng dụng web", 3, 35, 13, "CNTT", "CNTT", "CNTT",
                        "OPEN", List.of(new io.campuscore.restfulapi.academic.web.AcademicSectionReadDtos
                                .SectionScheduleResponse("s1", 5, "13:00", "15:30", "A", "102",
                                new io.campuscore.restfulapi.academic.web.AcademicSectionReadDtos
                                        .ClassroomSummary("c1", "A", "102"))))));

        ChatResponse response = advisor.answer(
                chatRequest("vi", "Học kỳ này tôi phụ trách những lớp nào?"), jwtLecturer());

        assertNotNull(response);
        assertEquals("PERSONAL_CONTEXT", response.reasonCode());
        assertTrue(response.answer().contains("SE402"), response.answer());
        assertTrue(response.answer().contains("Phát triển ứng dụng web"), response.answer());
    }

    // ------------------------------------------------------------------
    // xrole-7 (production audit): "Những nhóm sinh viên nào tôi đang hướng
    // dẫn?" was rejected although the same session's "Khối lượng hướng dẫn
    // của tôi..." answered correctly — the workload intent only accepted
    // quantity-first phrasings. It must route to the EXISTING workload
    // composer (topics + group counts), never inventing student names.
    // ------------------------------------------------------------------

    @Test
    void detectsGroupSupervisionPhrasingAsWorkloadIntent() {
        assertTrue(advisor.handles("Những nhóm sinh viên nào tôi đang hướng dẫn?"));
        assertTrue(advisor.handles("nhung nhom sinh vien nao toi dang huong dan"));
        assertTrue(advisor.handles("Tôi đang hướng dẫn những nhóm nào?"));
        // Policy wording about the supervision cap stays on the knowledge path.
        assertFalse(advisor.handles("Trường quy định bao nhiêu nhóm hướng dẫn tối đa?"));
    }

    @Test
    void answersGroupSupervisionPhrasingFromTheExistingWorkloadComposer() {
        ThesisLecturerWorkloadService workloadService = mock(ThesisLecturerWorkloadService.class);
        AssistantPersonalContextAdvisor workloadAdvisor =
                new AssistantPersonalContextAdvisor(enrollmentService, sectionService, workloadService, null, null);
        var topicA = new ThesisLecturerWorkloadService.SupervisedTopic(
                UUID.randomUUID(), "Đề tài A", "PUBLISHED",
                UUID.randomUUID(), "Đợt 1 KLTN 2026", "REGISTRATION_OPEN", null, 3, 1);
        var topicB = new ThesisLecturerWorkloadService.SupervisedTopic(
                UUID.randomUUID(), "Đề tài B", "PUBLISHED",
                UUID.randomUUID(), "Đợt 1 KLTN 2026", "REGISTRATION_OPEN", null, 2, 0);
        when(workloadService.workload("lecturer-profile")).thenReturn(
                new ThesisLecturerWorkloadService.LecturerWorkload(List.of(topicA, topicB), List.of(), List.of()));

        ChatResponse response = workloadAdvisor.answer(
                chatRequest("vi", "Những nhóm sinh viên nào tôi đang hướng dẫn?"), jwtLecturer());

        assertNotNull(response);
        assertEquals("PERSONAL_CONTEXT", response.reasonCode());
        String answer = response.answer();
        // Grounded in the workload rows: totals summed in code, per-topic group
        // counts echoed. No student name may appear because the composer never
        // has one — the answer must not invent any.
        assertTrue(answer.contains("tổng cộng 5 nhóm sinh viên trên 2 đề tài"), answer);
        assertTrue(answer.contains("Đề tài A"), answer);
        assertTrue(answer.contains("Số nhóm: 3"), answer);
        assertTrue(answer.contains("1 nhóm đang chờ duyệt"), answer);
        // A student actor without a lecturer profile still falls back to RAG.
        Jwt studentActor = jwt("studentId", "student-profile");
        assertNull(workloadAdvisor.answer(
                chatRequest("vi", "Những nhóm sinh viên nào tôi đang hướng dẫn?"), studentActor));
    }

    // ------------------------------------------------------------------
    // xrole-4 (production audit): "Nhóm luận văn của tôi là nhóm nào, có
    // những ai?" only returned the topic + PENDING — the composer must also
    // name the group: leader role, member roster, headcount vs the 3–4
    // requirement, and the approval status WITH its stored reason. Every
    // value is echoed from the group read path rows only.
    // ------------------------------------------------------------------

    @Test
    void answersThesisGroupWithLeaderRoleRosterSizeAndApprovalReason() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        AssistantPersonalContextAdvisor thesisAdvisor = new AssistantPersonalContextAdvisor(
                enrollmentService, sectionService, null, jdbc, null, null);
        Map<String, Object> groupRow = new HashMap<>();
        groupRow.put("group_id", "g1");
        groupRow.put("group_status", "ACTIVE");
        groupRow.put("approval_status", "PENDING");
        groupRow.put("leader_student_id", "student-profile");
        groupRow.put("rejection_reason", "Chưa đủ xác nhận của giảng viên phản biện");
        groupRow.put("topic_title", "Hệ thống gợi ý học tập");
        groupRow.put("round_name", "Đợt 1 KLTN 2026-2027");
        // Roster rows mirror the ThesisGroupReadRepository join (Student + User).
        Map<String, Object> me = thesisMemberRow("g1", "student-profile", true, 1, "Nguyễn", "An");
        Map<String, Object> other1 = thesisMemberRow("g1", "student-b", false, 2, "Trần", "Bình");
        Map<String, Object> other2 = thesisMemberRow("g1", "student-c", false, 3, "Lê", "Cường");
        when(jdbc.queryForList(anyString(), any(MapSqlParameterSource.class)))
                .thenAnswer(invocation -> ((String) invocation.getArgument(0)).contains("campuscore_auth")
                        ? List.of(me, other1, other2)
                        : List.of(groupRow));

        ChatResponse response = thesisAdvisor.answer(
                chatRequest("vi", "Nhóm luận văn của tôi là nhóm nào, có những ai?"), jwtStudent());

        assertNotNull(response);
        assertEquals("PERSONAL_CONTEXT", response.reasonCode());
        String answer = response.answer();
        assertTrue(answer.contains("Đợt 1 KLTN 2026-2027"), answer);
        assertTrue(answer.contains("Hệ thống gợi ý học tập"), answer);
        // Headcount vs the 3–4 requirement, computed from the real member rows.
        assertTrue(answer.contains("Nhóm: 3 thành viên (yêu cầu 3-4)"), answer);
        assertTrue(answer.contains("đủ số lượng theo yêu cầu"), answer);
        // The asker is the group leader per leader_student_id / is_leader.
        assertTrue(answer.contains("Vai trò của bạn: Nhóm trưởng"), answer);
        assertTrue(answer.contains("Nguyễn An (nhóm trưởng)"), answer);
        assertTrue(answer.contains("Trần Bình"), answer);
        assertTrue(answer.contains("Lê Cường"), answer);
        // Status plus the stored reason — the raw approval_status is kept,
        // the reason is echoed only because the row has one.
        assertTrue(answer.contains("Trạng thái duyệt: PENDING"), answer);
        assertTrue(answer.contains("lý do: Chưa đủ xác nhận của giảng viên phản biện"), answer);
    }

    @Test
    void thesisGroupBelowMinimumStatesTheShortfallAndPlainMembership() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        AssistantPersonalContextAdvisor thesisAdvisor = new AssistantPersonalContextAdvisor(
                enrollmentService, sectionService, null, jdbc, null, null);
        Map<String, Object> groupRow = new HashMap<>();
        groupRow.put("group_id", "g2");
        groupRow.put("group_status", "DRAFT");
        groupRow.put("approval_status", "PENDING");
        groupRow.put("leader_student_id", "student-leader");
        groupRow.put("rejection_reason", null);
        groupRow.put("topic_title", "Nền tảng quản lý thư viện");
        groupRow.put("round_name", "Đợt 1 KLTN 2026-2027");
        // The asker is a plain member; only two of the required three exist.
        Map<String, Object> leader = thesisMemberRow("g2", "student-leader", true, 1, "Phạm", "Dũng");
        Map<String, Object> asker = thesisMemberRow("g2", "student-profile", false, 2, "Hoàng", "Mai");
        when(jdbc.queryForList(anyString(), any(MapSqlParameterSource.class)))
                .thenAnswer(invocation -> ((String) invocation.getArgument(0)).contains("campuscore_auth")
                        ? List.of(leader, asker)
                        : List.of(groupRow));

        ChatResponse response = thesisAdvisor.answer(
                chatRequest("vi", "Nhóm luận văn của tôi là nhóm nào, có những ai?"), jwtStudent());

        String answer = response.answer();
        assertTrue(answer.contains("Nhóm: 2 thành viên (yêu cầu 3-4)"), answer);
        assertTrue(answer.contains("còn thiếu 1 so với tối thiểu 3"), answer);
        assertTrue(answer.contains("Vai trò của bạn: Thành viên"), answer);
        assertFalse(answer.contains("Vai trò của bạn: Nhóm trưởng"), answer);
        assertTrue(answer.contains("Phạm Dũng (nhóm trưởng)"), answer);
        // No reason line is invented when rejection_reason is null.
        assertFalse(answer.contains("lý do:"), answer);
    }

    // ------------------------------------------------------------------
    // xrole-15 (production audit): PERSONAL_CONTEXT responses returned
    // clientRequestId/requestId null, so the client could not correlate the
    // intercepted answer with its pending request. Both the normal and the
    // unavailable personal answers must echo request.clientRequestId().
    // ------------------------------------------------------------------

    @Test
    void personalAnswerEchoesTheClientRequestId() {
        when(enrollmentService.findStudentEnrollments("student-profile", null)).thenReturn(List.of(
                enrollment("SE401", "Lập trình Java nâng cao", "Advanced Java", CURRENT_TERM_START,
                        List.of(new SectionScheduleResponse("s1", 2, "07:00", "09:30",
                                new ClassroomSummary("c1", "A", "101"))))));
        UUID clientRequestId = UUID.randomUUID();

        ChatResponse response = advisor.answer(
                new ChatRequest("Lịch học của tôi tuần này?", "vi", clientRequestId, null), jwtStudent());

        assertNotNull(response);
        assertEquals("PERSONAL_CONTEXT", response.reasonCode());
        assertEquals(clientRequestId, response.clientRequestId());
        assertNotNull(response.requestId());
    }

    @Test
    void unavailablePersonalAnswerStillEchoesTheClientRequestId() {
        when(enrollmentService.findStudentEnrollments("student-profile", null))
                .thenThrow(new DataAccessResourceFailureException("academic schema unavailable"));
        UUID clientRequestId = UUID.randomUUID();

        ChatResponse response = advisor.answer(
                new ChatRequest("Lịch học của tôi tuần này?", "vi", clientRequestId, null), jwtStudent());

        assertEquals("PERSONAL_CONTEXT_UNAVAILABLE", response.reasonCode());
        assertEquals(clientRequestId, response.clientRequestId());
    }

    private static Map<String, Object> thesisMemberRow(
            String groupId, String studentId, boolean leader, int order, String lastName, String firstName) {
        Map<String, Object> row = new HashMap<>();
        row.put("group_id", groupId);
        row.put("student_id", studentId);
        row.put("is_leader", leader);
        row.put("member_order", order);
        row.put("is_external", false);
        row.put("display_name", null);
        row.put("student_number", "SV00" + order);
        row.put("first_name", firstName);
        row.put("last_name", lastName);
        return row;
    }

    private static AcademicAttendanceReadDtos.AttendanceResponse attendanceRow(String status) {
        return new AcademicAttendanceReadDtos.AttendanceResponse(
                "att-" + status, "student-1", "sec-401", Instant.now(), status, null, Instant.now(),
                new AcademicAttendanceReadDtos.StudentSummary("student-1", "SV001",
                        new AcademicAttendanceReadDtos.UserSummary("u1", "sv001@campuscore.edu",
                                "Văn A", "Nguyễn")),
                new AcademicAttendanceReadDtos.SectionSummary("sec-401", "SE401-01", "sem1",
                        new AcademicAttendanceReadDtos.CourseSummary("c1", "SE401",
                                "Lập trình Java nâng cao", null, null)));
    }

    private static AcademicEnrollmentReadDtos.GradeSummary gradeRow(
            String id, String courseCode, int credits, String letter, String semesterId, String semesterName) {
        return new AcademicEnrollmentReadDtos.GradeSummary(
                id, courseCode, "Học phần " + courseCode, "Course " + courseCode,
                "Học phần " + courseCode, credits, courseCode + "-01", "GV Demo",
                semesterName, semesterName, semesterName, semesterId,
                null, null,
                new java.math.BigDecimal("7.0"), letter, "PUBLISHED", "COMPLETED");
    }

    // ------------------------------------------------------------------
    // Production audit (chatbot-production-audit, run dwfrun-94cb7693): the
    // three HIGH findings plus the interception gaps they exposed.
    // ------------------------------------------------------------------

    @Test
    void interceptsDayWithClassPhrasingsOnThePersonalPath() {
        // Audit ca-nhan Q2 / giang-vien Q7: "hôm nay tôi có lớp (học) không"
        // used to miss SCHEDULE_INTENT (no "lớp" in the noun group) and reach
        // RAG, where an exhausted quota silently answered with regulations.
        assertTrue(advisor.handles("Hôm nay tôi có lớp học không?"));
        assertTrue(advisor.handles("Hôm nay tôi có lớp nào không?"));
        assertTrue(advisor.handles("Thứ Hai hàng tuần tôi có môn nào, học mấy giờ, ở phòng nào?"));
    }

    @Test
    void interceptsPossessiveGradesPhrasingWithGap() {
        // Audit ca-nhan Q6: "Điểm các môn của tôi trong học kỳ 2..." — the
        // possessive can sit a few words after "điểm".
        assertTrue(advisor.handles("Điểm các môn của tôi trong học kỳ 2 năm học 2025-2026 như thế nào?"));
        // Public wording without the first-person possessive stays knowledge.
        assertFalse(advisor.handles("Điểm các môn học được tính theo thang nào?"));
    }

    @Test
    void gradesAnswerCumulativeMatchesTheTranscriptSummary() {
        // HIGH audit finding: the chat counted every retake attempt (57
        // credits / GPA 3.07 in production) while the transcript page shows
        // best attempt per course (38 / 3.11). The composer must render the
        // summary's numbers, not its own accumulation.
        when(enrollmentService.findStudentGrades("student-profile", null)).thenReturn(List.of(
                gradeRow("g1", "SE401", 3, "B", "sem-2", "HK2 2025-2026"),
                gradeRow("g2", "SE401", 3, "A", "sem-2", "HK2 2025-2026"),
                gradeRow("g3", "SE407", 3, "B+", "sem-1", "HK1 2025-2026")));
        when(enrollmentService.findStudentTranscript("student-profile")).thenReturn(
                new AcademicEnrollmentReadDtos.TranscriptResponse(
                        new AcademicEnrollmentReadDtos.TranscriptSummary(
                                new java.math.BigDecimal("3.17"), 6, 6,
                                new AcademicEnrollmentReadDtos.TranscriptBasisNote(
                                        "GPA và tín chỉ tính theo điểm tốt nhất mỗi môn (chính sách học lại)",
                                        "GPA and credits use your best attempt per course (retake policy)")),
                        List.of()));

        ChatResponse response = advisor.answer(
                chatRequest("vi", "GPA của tôi hiện tại là bao nhiêu?"), jwtStudent());

        String answer = response.answer();
        assertTrue(answer.contains("Tích lũy: 6 tín chỉ, GPA 3.17 (thang 4)"), answer);
        // 6 = best-per-course from the summary; the all-attempt figure would
        // be 9 — the old bug.
        assertFalse(answer.contains("9 tín chỉ"), "the all-attempt accumulation must not survive");
        assertTrue(answer.contains("bản tóm tắt Bảng điểm"), answer);
    }

    @Test
    void interceptsAndAnswersAccumulatedCreditsQuestion() {
        // Audit ca-nhan Q11: "Tôi đã tích lũy được bao nhiêu tín chỉ?" was
        // answered with the credit-LIMIT regulation although the transcript
        // summary holds the number.
        assertTrue(advisor.handles("Tôi đã tích lũy được bao nhiêu tín chỉ?"));
        when(enrollmentService.findStudentTranscript("student-profile")).thenReturn(
                new AcademicEnrollmentReadDtos.TranscriptResponse(
                        new AcademicEnrollmentReadDtos.TranscriptSummary(
                                new java.math.BigDecimal("3.11"), 38, 40,
                                new AcademicEnrollmentReadDtos.TranscriptBasisNote(
                                        "GPA và tín chỉ tính theo điểm tốt nhất mỗi môn (chính sách học lại)",
                                        "GPA and credits use your best attempt per course (retake policy)")),
                        List.of()));

        ChatResponse response = advisor.answer(
                chatRequest("vi", "Tôi đã tích lũy được bao nhiêu tín chỉ?"), jwtStudent());

        assertNotNull(response);
        assertEquals("PERSONAL_CONTEXT", response.reasonCode());
        assertTrue(response.answer().contains("tích lũy được 38 tín chỉ"), response.answer());
        assertFalse(response.answer().contains("Giới hạn tín chỉ"), response.answer());
    }

    @Test
    void answersLecturerTeachingCreditsFromAssignedSections() {
        // HIGH audit finding: "Học kỳ này tôi dạy tất cả bao nhiêu tín chỉ?"
        // reached the LLM, which claimed the data did not exist although
        // /sections/my/schedule carries every section's credits.
        assertTrue(advisor.handles("Học kỳ này tôi dạy tất cả bao nhiêu tín chỉ?"));
        assertFalse(advisor.handles("Quy định số tín chỉ giảng viên phải dạy mỗi học kỳ là bao nhiêu?"));
        when(sectionService.findLecturerSchedule("lecturer-profile", null)).thenReturn(List.of(
                lecturerSection("sec1", "SE401", "Lập trình web", 3),
                lecturerSection("sec2", "SE402", "Cơ sở dữ liệu", 3),
                lecturerSection("sec3", "SE409", "An toàn thông tin", 4)));

        ChatResponse response = advisor.answer(
                chatRequest("vi", "Học kỳ này tôi dạy tất cả bao nhiêu tín chỉ?"), jwtLecturer());

        assertNotNull(response);
        assertEquals("PERSONAL_CONTEXT", response.reasonCode());
        assertTrue(response.answer().contains("3 lớp học phần"), response.answer());
        assertTrue(response.answer().contains("10 tín chỉ"), response.answer());
    }

    @Test
    void adviseeRosterListsNamesInsteadOfWorkloadCounts() {
        // Audit giang-vien Q5: "Tôi đang hướng dẫn những sinh viên nào?"
        // printed the workload boilerplate without a single name.
        ThesisLecturerWorkloadService workloadService = mock(ThesisLecturerWorkloadService.class);
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        AssistantPersonalContextAdvisor rosterAdvisor =
                new AssistantPersonalContextAdvisor(enrollmentService, sectionService, workloadService, jdbc, null);
        assertTrue(rosterAdvisor.handles("Tôi đang hướng dẫn những sinh viên nào?"));

        Map<String, Object> member = new HashMap<>();
        member.put("topic_title", "Hệ thống quản lý sinh viên");
        member.put("student_id", "student-user-9");
        member.put("first_name", "Minh Anh");
        member.put("last_name", "Nguyễn");
        member.put("student_number", "20140123");
        member.put("is_external", false);
        when(jdbc.queryForList(anyString(), any(MapSqlParameterSource.class))).thenReturn(List.of(member));

        ChatResponse response = rosterAdvisor.answer(
                chatRequest("vi", "Tôi đang hướng dẫn những sinh viên nào?"), jwtLecturer());

        assertNotNull(response);
        assertEquals("PERSONAL_CONTEXT", response.reasonCode());
        assertTrue(response.answer().contains("Hệ thống quản lý sinh viên"), response.answer());
        assertTrue(response.answer().contains("Nguyễn Minh Anh"), response.answer());
        // The internal user id must never surface in user-facing copy.
        assertFalse(response.answer().contains("student-user-9"), response.answer());
    }

    @Test
    void creditsAnswerStatesRaisedLimitWithoutInventingProvenance() {
        // Audit ca-nhan Q4 (low): the composer asserted "Phòng Đào tạo phê
        // duyệt" from the limit number alone. With no approved application
        // visible the wording must stay neutral.
        RegistrationService registrationService = mock(RegistrationService.class);
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        AssistantPersonalContextAdvisor creditsAdvisor = new AssistantPersonalContextAdvisor(
                enrollmentService, sectionService, null, jdbc, null, registrationService);
        when(registrationService.summary("student-profile", null)).thenReturn(
                new io.campuscore.restfulapi.academic.registration.RegistrationDtos.SummaryResponse(
                        "round-1", 30, 16, 14, List.of()));
        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Integer.class)))
                .thenReturn(0);

        ChatResponse response = creditsAdvisor.answer(
                chatRequest("vi", "Kỳ này tôi còn được đăng ký bao nhiêu tín chỉ nữa?"), jwtStudent());

        assertTrue(response.answer().contains("Hạn mức áp dụng cho đợt đăng ký hiện tại là 30 tín chỉ"),
                response.answer());
        assertFalse(response.answer().contains("phê duyệt"), response.answer());
    }

    @Test
    void todayClassExistenceQuestionsRouteToDayTimetable() {
        // Audit ca-nhan Q2 + giang-vien Q7 (M6 battery FAIL): "có lớp (học)
        // không" must answer the ASKED DAY, not the full enrollment list —
        // and the lecturer variant must not fall through to RAG NO_MATCH.
        when(enrollmentService.findStudentEnrollments("student-profile", null)).thenReturn(List.of(
                enrollment("SE013", "Nhập môn lập trình", "Introduction to Programming",
                        CURRENT_TERM_START,
                        List.of(new SectionScheduleResponse("sch-1", 3, "07:00", "09:30",
                                new io.campuscore.restfulapi.academic.web.AcademicEnrollmentReadDtos
                                        .ClassroomSummary("room-1", "A", "103"))))));
        when(enrollmentService.findStudentTranscript("student-profile")).thenReturn(null);

        ChatResponse student = advisor.answer(
                chatRequest("vi", "Hôm nay tôi có lớp học không?"), jwtStudent());
        assertNotNull(student);
        assertEquals("PERSONAL_CONTEXT", student.reasonCode());
        // Either the today list or the honest empty message — never a dump.
        assertTrue(student.answer().contains("Lịch học Thứ") || student.answer().contains("không có lịch học vào"),
                student.answer());

        when(sectionService.findLecturerSchedule("lecturer-profile", null)).thenReturn(List.of(
                lecturerSection("sec-today", "SE402", "Cơ sở dữ liệu", 3)));
        ChatResponse teacher = advisor.answer(
                chatRequest("vi", "Hôm nay tôi có lớp nào không?"), jwtLecturer());
        assertNotNull(teacher, "the existence question must be intercepted, not answered by RAG");
        assertEquals("PERSONAL_CONTEXT", teacher.reasonCode());
        assertTrue(teacher.answer().contains("Lịch giảng dạy") || teacher.answer().contains("ca giảng dạy"),
                teacher.answer());
    }

    @Test
    void interceptsPendingGradeCountAndHybridDiacriticGrades() {
        // Audit quét toàn hệ thống chatbot-1/chatbot-2 (both verified):
        // "điem cua toi" (đ kept, tone dropped) missed GRADES_INTENT because
        // CASE_INSENSITIVE never folds đ↔d, and the pending-grade count
        // question fell to the prerequisite KB while 5 in-progress courses
        // were awaiting grades.
        assertTrue(advisor.handles("điem cua toi"));
        assertTrue(advisor.handles("Điểm của tôi"));
        assertTrue(advisor.handles("Tôi học còn bao nhiêu môn chưa có điểm?"));
        assertTrue(advisor.handles("How many of my courses still have no grade?"));
        // Round-2 sweep chat-3: Vietnamese drops the pronoun — the bare form
        // is still the asker's own pending count.
        assertTrue(advisor.handles("còn bao nhiêu môn chưa có điểm"));
        // Public rule wording stays on the knowledge path.
        assertFalse(advisor.handles("Học phần chưa có điểm công bố được tính thế nào?"));
    }

    @Test
    void pendingGradesAnswerCountsUngradedActiveCourses() {
        when(enrollmentService.findStudentEnrollments("student-profile", null)).thenReturn(List.of(
                enrollmentWithGradeStatus("SE013", "ENROLLED", "NOT_GRADED"),
                enrollmentWithGradeStatus("SE014", "ENROLLED", "NOT_GRADED"),
                enrollmentWithGradeStatus("SE410", "ENROLLED", "PUBLISHED")));

        ChatResponse response = advisor.answer(
                chatRequest("vi", "Tôi học còn bao nhiêu môn chưa có điểm?"), jwtStudent());

        assertNotNull(response);
        assertEquals("PERSONAL_CONTEXT", response.reasonCode());
        assertTrue(response.answer().contains("còn 2 môn chưa có điểm công bố"), response.answer());
        assertTrue(response.answer().contains("SE013"), response.answer());
        assertTrue(response.answer().contains("SE014"), response.answer());
        assertFalse(response.answer().contains("SE410"), response.answer());
    }

    private static EnrollmentResponse enrollmentWithGradeStatus(String code, String status, String gradeStatus) {
        EnrollmentResponse base = enrollment(code, "Học phần " + code, "Course " + code, CURRENT_TERM_START, List.of());
        return new EnrollmentResponse(
                base.id(), base.studentId(), base.sectionId(), base.semesterId(), base.status(),
                base.enrolledAt(), base.droppedAt(), gradeStatus, base.finalGrade(), base.letterGrade(),
                base.createdAt(), base.updatedAt(), base.student(), base.section(), base.semester());
    }

    private static LecturerScheduleResponse lecturerSection(
            String sectionId, String courseCode, String title, int credits) {
        return new LecturerScheduleResponse("id-" + sectionId, sectionId, sectionId + "-01", courseCode,
                title, title, title, credits, 40, 12, "CNTT", "ICT", "CNTT", "OPEN",
                List.of(new io.campuscore.restfulapi.academic.web.AcademicSectionReadDtos
                        .SectionScheduleResponse("sch-" + sectionId, 2, "07:00", "09:30", "A", "101",
                        new io.campuscore.restfulapi.academic.web.AcademicSectionReadDtos
                                .ClassroomSummary("room-" + sectionId, "A", "101"))));
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
