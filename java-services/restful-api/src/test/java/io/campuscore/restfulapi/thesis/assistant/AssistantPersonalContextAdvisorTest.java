package io.campuscore.restfulapi.thesis.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
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
import java.text.Normalizer;
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
    void enrollmentListIntentMatchesTheUiSuggestionChipPhrasing() {
        // The first student suggestion chip sends the bare phrase "Lớp tôi
        // đang học" — no interrogative and no "đăng ký" word. It must still
        // resolve to the asker's own enrollment list instead of falling to
        // the general path, which previously produced a fabricated "the
        // portal has not published per-student class info" answer.
        assertTrue(advisor.handles("Lớp tôi đang học"));
        assertTrue(advisor.handles("các lớp tôi đang học kỳ này"));
        assertTrue(advisor.handles("môn mình đang học"));
        assertTrue(advisor.handles("những học phần em đang theo học"));
        assertTrue(advisor.handles("tôi đang học những lớp nào"));
        assertTrue(advisor.handles("các môn của tôi"));
        assertTrue(advisor.handles("my classes"));
        assertTrue(advisor.handles("my current courses"));
        assertTrue(advisor.handles("which classes am I taking"));
        assertTrue(advisor.handles("classes I'm taking this semester"));

        // The lecturer suggestion chips share the class-list intent shape:
        // they route to the lecturer teaching answer instead of dying at the
        // general path.
        assertTrue(advisor.handles("Các lớp tôi giảng dạy"));
        assertTrue(advisor.handles("môn mình dạy kỳ này"));
        assertTrue(advisor.handles("classes I teach"));
        assertTrue(advisor.handles("Which sections do I teach?"));

        // Existing enrollment-list phrasings keep matching.
        assertTrue(advisor.handles("Tôi đã đăng ký những môn nào?"));

        // Non-personal or policy/how-to phrasing must stay on the knowledge
        // path: no first-person gate, or an explicit how-to/policy intent.
        assertFalse(advisor.handles("lớp đang học gì hôm nay"));
        assertFalse(advisor.handles("cách đăng ký học phần"));
        assertFalse(advisor.handles("điều kiện đăng ký lớp học phần"));

        // Wukong counterexamples: a first-person TOKEN elsewhere in the
        // message must not impersonate the possessor inside the phrase —
        // "toi" from "tối nay", a vocative "em", or a policy question built
        // like the progressive branch.
        assertFalse(advisor.handles("Toi nay lop dang hoc may gio?"));
        assertFalse(advisor.handles("Em oi, lop dang hoc co bi nghi khong?"));
        assertFalse(advisor.handles("Chính sách cho phép em đang học tối đa mấy môn?"));
        assertFalse(advisor.handles("Theo quy chế em đang học được bao nhiêu môn mỗi kỳ?"));

        // Wukong re-verify: third-person subjects wearing a first-person
        // token — a possessive pronoun inside the span is position, not
        // ownership, and the verb-first order must bind the possessor
        // immediately before "đang".
        assertFalse(advisor.handles("Lớp em ấy đang học có khó không?"));
        assertFalse(advisor.handles("Bạn tôi đang học lớp này có khó không?"));
        assertFalse(advisor.handles("Toi nay dang hoc mon gi?"));
        assertFalse(advisor.handles("Em oi, dang hoc mon nay co kho khong?"));
        assertFalse(advisor.handles("Anh ấy đang học lớp nào? Tôi cũng muốn biết."));

        // Wukong round-2: the veto must be construction-level, not a token
        // list — possessive insertion ("bạn của tôi"), uncovered relations
        // ("em tôi"), standalone third-person subjects ("nó đang học"),
        // uncovered false-friends ("tối qua"), and third-person phrasings
        // entering through OTHER intent doors (day timetable, grades).
        assertFalse(advisor.handles("Bạn của tôi đang học môn này có khó không?"));
        assertFalse(advisor.handles("Em tôi đang học lớp này có khó không?"));
        assertFalse(advisor.handles("Nó đang học lớp nào? Tôi cũng muốn biết."));
        // "toi qua" is genuinely ambiguous — "tôi qua" (I passed) vs "tối
        // qua" (last night). The false-friend arm keeps "qua" only under the
        // accented tối|tới, so unaccented input resolves to the pronoun and
        // serves the asker's OWN rows (fail-soft, same contract as "ba
        // tôi"/"con tôi"); the accented twin still vetoes below.
        assertTrue(advisor.handles("Toi qua lop dang hoc mon gi?"));
        assertFalse(advisor.handles("Tối qua lớp đang học môn gì?"));
        assertFalse(advisor.handles("Thứ 3 lớp em ấy đang học môn gì?"));
        assertFalse(advisor.handles("Điểm của em ấy thế nào?"));

        // Wukong round-3: without UNICODE_CHARACTER_CLASS the \b anchors used
        // ASCII \w, so accented finals (đó/sẽ/có/ông/đứa) were dead code and
        // "em/ba" matched mid-word inside xem/bang/điem. These pins exercise
        // the accented spellings users actually type, uncovered relations,
        // pronoun-free "của X" possesses, and English third-person.
        assertFalse(advisor.handles("Nó sẽ học lop nao? Toi cung muon biet."));
        assertFalse(advisor.handles("Bạn đó dang hoc mon gi? Toi muon biet."));
        assertFalse(advisor.handles("Ông ấy dang hoc mon gi? Toi muon biet."));
        assertFalse(advisor.handles("Chú tôi đang học lớp này? Tôi muốn biết."));
        assertFalse(advisor.handles("Sinh vien ay dang hoc lop nao?"));
        assertFalse(advisor.handles("Lịch học của thầy"));
        assertFalse(advisor.handles("thời khóa biểu của giảng viên"));
        assertFalse(advisor.handles("his schedule"));
        assertFalse(advisor.handles("my friend's classes"));
        assertFalse(advisor.handles("Nó hoc lop nao?"));

        // The same repair must NOT veto real first-person phrasings whose
        // tokens merely resemble relation/pronoun substrings.
        assertTrue(advisor.handles("cho em xem lich hoc"));
        assertTrue(advisor.handles("bang diem cua toi"));
        assertTrue(advisor.handles("bay gio toi dang hoc mon gi?"));

        // Wukong round-4 (FALSIFIED -> repaired): surviving impersonation
        // holes and over-blocks in the token-list construction.
        // (a) "của X" missed most non-self kinship and bare pronouns.
        assertFalse(advisor.handles("Lịch học của mẹ"));
        assertFalse(advisor.handles("Thời khóa biểu của chồng"));
        assertFalse(advisor.handles("lịch học của nó"));
        assertFalse(advisor.handles("điểm của họ"));
        // (b) pronoun BEFORE the relation — the gap rule only looked
        // relation -> pronoun.
        assertFalse(advisor.handles("Cho tôi xem bảng điểm của mẹ"));
        assertFalse(advisor.handles("Cho tôi xem GPA của nó"));
        // (c) bare relation + verb subjects — rule E only knew pronouns.
        // Wukong round-9 F3: bare "bạn" is OUT of arm E — addressing the bot
        // it is the second person, and keeping it vetoed real request frames
        // ("bạn có thể cho tôi xem điểm"). The residual reading ("bạn" =
        // a friend) serves the asker's own rows with honest labels — the
        // accepted fail-soft miss; "bạn tôi/thân/của bạn" still veto below.
        assertTrue(advisor.handles("Bạn đang học lớp nào? Tôi muốn biết."));
        assertFalse(advisor.handles("Giảng viên đang dạy môn gì? Cho tôi xem."));
        assertFalse(advisor.handles("Sinh vien dang hoc mon gi? Toi muon biet."));
        assertFalse(advisor.handles("Tôi muốn biết mẹ đang dạy môn nào."));
        // (d) verb whitelist missed đi/mới — subject->verb adjacency only.
        assertFalse(advisor.handles("Nó đi học lớp nào? Tôi muốn biết."));
        assertFalse(advisor.handles("Nó mới đăng ký lớp nào? Tôi muốn xem."));
        // (e) unaccented compound kinship: qualifier broke strict-C.
        assertFalse(advisor.handles("Ban than toi dang hoc lop nao?"));
        assertFalse(advisor.handles("Chi gai toi dang hoc mon gi?"));
        assertFalse(advisor.handles("Nguoi yeu toi dang hoc mon gi?"));
        assertFalse(advisor.handles("Co giao toi dang day mon gi?"));
        assertFalse(advisor.handles("Ong xa toi dang day mon gi?"));
        assertFalse(advisor.handles("Me ke toi dang hoc mon gi?"));
        assertFalse(advisor.handles("Giao vien toi dang day mon gi?"));
        // (f) unaccented compound + demonstrative.
        assertFalse(advisor.handles("Sinh vien ay dang hoc lop nao? Toi muon biet."));
        // (g) possessive spans wider than the 12-char cap.
        assertFalse(advisor.handles("Bạn thân thiết của tôi đang học lớp nào?"));
        assertFalse(advisor.handles("Giáo viên chủ nhiệm tôi đang dạy môn gì?"));
        // (h) EN relations outside friend/family.
        assertFalse(advisor.handles("my professor's schedule"));
        assertFalse(advisor.handles("my teacher's timetable"));

        // Round-4 over-blocks that must now pass through to the personal
        // path: vocative address, the "anh em" collective, comma-separated
        // vocatives, the day/dạy demonstrative collision, and the counter-
        // word "con số".
        assertTrue(advisor.handles("anh ơi cho em xem điểm của em"));
        assertTrue(advisor.handles("chị ơi cho em xem lịch học"));
        assertTrue(advisor.handles("bạn ơi, tôi đang học lớp nào"));
        assertTrue(advisor.handles("thầy ơi em muốn xem bảng điểm"));
        assertTrue(advisor.handles("em ơi cho em xem điểm của em"));
        assertTrue(advisor.handles("Cac mon em day"));
        assertTrue(advisor.handles("Cho anh em xem lịch học"));
        assertTrue(advisor.handles("Con số của tôi đang học lớp nào"));
        assertTrue(advisor.handles("mẹ, tôi đang học lớp nào"));
        assertTrue(advisor.handles("mẹ ơi, tôi đang học lớp nào"));

        // Kongming round-4: homograph collisions — "ba" is also "thứ ba"
        // (Tuesday) and the numeral 3; "con toi" is unaccented "còn tôi".
        // Both were dropped from the veto because the miss is fail-soft
        // (the advisor only ever serves the ASKER's own rows).
        assertTrue(advisor.handles("thứ ba tôi có lớp không?"));
        assertTrue(advisor.handles("lịch thứ ba của tôi"));
        assertTrue(advisor.handles("ba môn tôi đang học"));
        assertTrue(advisor.handles("con toi con lai bao nhieu tin chi"));
        // The collective "anh em" must still lose to an explicit possessor.
        assertFalse(advisor.handles("anh em tôi đang học lớp nào"));
        assertFalse(advisor.handles("chị em tôi đang học lớp nào"));
        // Fail-soft residual (documented trade-off): bare "ba tôi"/"con tôi"
        // kinship now serves the asker's own rows instead of vetoing.
        assertTrue(advisor.handles("ba tôi đang học lớp nào"));
        assertTrue(advisor.handles("con tôi đang học lớp nào"));
        // A concrete section code keeps the public-catalog door open even
        // when a "của X" possessive names a person.
        assertTrue(advisor.handles("lịch của lớp SE013 của thầy"));
        // Vocative self-reference frames: the comma break already stops the
        // relation→pronoun gap — "Cho em hỏi, em đang học" is the asker.
        assertTrue(advisor.handles("Cho em hỏi, em đang học môn nào?"));
        // Strip-then-check keeps a REAL pronoun outside the false-friend
        // span ("tối nay" is not a day word, so this answers the list).
        assertTrue(advisor.handles("Tối nay tôi có lớp gì?"));

        // Wukong round-5 (FALSIFIED -> repaired): E-subject homographs —
        // unaccented di/ma/cha/bo/mo are far more often the common words
        // đi/mà/chả/bỏ/mở than kinship, and vetoed the asker's own
        // questions. Same homograph policy as ba/con (miss is fail-soft).
        assertTrue(advisor.handles("em di hoc lop nao"));
        assertTrue(advisor.handles("em cha hoc mon nao"));
        assertTrue(advisor.handles("bo mon nay toi con hoc mon nao"));
        assertTrue(advisor.handles("truong mo dang ky mon nao cho toi"));
        // "X mà tôi V" is a relative clause — "ma" must NOT veto (it was
        // deliberately kept out of the strict-adjacency arm as well).
        assertTrue(advisor.handles("lop ma toi dang hoc mon gi"));
        // Collective "anh em minh" = we — the strip removes the span
        // before the bare "em" can pair with "mình".
        assertTrue(advisor.handles("anh em minh dang hoc lop nao"));
        assertTrue(advisor.handles("anh em mình đang học lớp nào"));
        // But the explicit possessor still wins: "anh em tôi" = my siblings.
        assertFalse(advisor.handles("anh em tôi đang học lớp nào"));
        // "giáo" compounds are coursework nouns, not teachers — the veto
        // must stay off while a genuine personal intent still fires.
        assertTrue(advisor.handles("giáo trình tôi đang học môn nào"));
        assertTrue(advisor.handles("giao an toi dang soan mon nao"));
        // ...while real teacher references keep the veto.
        assertFalse(advisor.handles("giáo viên tôi đang dạy môn gì"));
        assertFalse(advisor.handles("cô giáo tôi đang dạy môn gì"));
        // Round-5 impersonation escapes that must now veto: negation
        // first-word, "người yêu/bạn bè" compound subjects, possessive
        // spans over the old 15-char cap, and "của X" gaps.
        assertFalse(advisor.handles("me khong hoc lop nao, toi muon biet"));
        assertFalse(advisor.handles("nguoi yeu dang hoc lop nao, toi muon biet"));
        assertFalse(advisor.handles("ban be dang hoc lop nao, toi muon biet"));
        assertFalse(advisor.handles("bạn thân thiết nhất của tôi đang học lớp nào"));
        assertFalse(advisor.handles("bang diem cua be toi"));
        assertFalse(advisor.handles("diem cua thim toi"));
        assertFalse(advisor.handles("lich hoc cua gia dinh toi"));
        assertFalse(advisor.handles("lich hoc cua truong toi"));
        assertFalse(advisor.handles("my kid's grades"));
        assertFalse(advisor.handles("my partner's schedule"));
        assertFalse(advisor.handles("my child's classes"));
        // Fail-soft residual: "ma toi" (my mom, unaccented) escapes because
        // "mà tôi" is a real self construction — serves the asker's own
        // rows, same documented trade-off as "ba tôi"/"con tôi".
        assertTrue(advisor.handles("ma toi dang hoc lop nao"));
        // Strict-adjacency unaccented kinship: "dì/cậu/mợ tôi" veto while
        // their verb homographs cannot produce this word order.
        assertFalse(advisor.handles("di toi dang day mon gi"));
        assertFalse(advisor.handles("cau toi dang hoc lop nao"));
        // "đưa tôi" (give me) is a self request — "dua" stays out.
        assertTrue(advisor.handles("dua toi xem lich hoc"));
        // Unicode normalization: a decomposed (NFD) message must route
        // identically to its precomposed form — IMEs and paste paths can
        // deliver either.
        assertTrue(advisor.handles(
                Normalizer.normalize("Lớp tôi đang học", Normalizer.Form.NFD)));
        assertTrue(advisor.handles(
                Normalizer.normalize("Tối nay tôi có lớp gì?", Normalizer.Form.NFD)));
        assertFalse(advisor.handles(
                Normalizer.normalize("Lịch học của mẹ", Normalizer.Form.NFD)));
        // Kongming review pins — unaccented coverage gaps and false-friend
        // collisions fixed on the same snapshot.
        // "chị em mình" unaccented twin is the same collective "we".
        assertTrue(advisor.handles("chi em minh dang hoc lop nao"));
        // Unaccented day-first schedule phrasings ("thứ/hôm nay … có lớp").
        assertTrue(advisor.handles("thu 2 toi co lop khong"));
        assertTrue(advisor.handles("hom nay toi co lop khong"));
        // "tôi dạy" / "tôi qua" are real first-person phrases — the bare
        // "toi" false-friend arm must not strip them.
        assertTrue(advisor.handles("toi day mon gi"));
        assertTrue(advisor.handles("toi qua duoc mon nao"));
        assertTrue(advisor.handles("cac mon toi day"));
        // "em … em" self-echo ("cho em hỏi, em đang học") — the second "em"
        // is the asker, not a sibling.
        assertTrue(advisor.handles("cho em hoi em dang hoc lop nao"));
        // Controls: the real third-person and time-word cases still veto.
        assertFalse(advisor.handles("em toi dang hoc lop nao"));
        assertFalse(advisor.handles("Toi nay lop dang hoc may gio?"));
        assertFalse(advisor.handles("tối nay lớp đang học mấy giờ"));
        // "thi lại" (retake exam) is a public exam topic even when
        // unaccented — the enrollment door must stay shut.
        assertFalse(advisor.handles("thi lai mon nao cua toi"));
        // Section/group letters are not vocative particles: "sinh viên A"
        // must keep the "của sinh viên" third-person veto armed (live probe
        // found the bare-a/à vocative strip silenced it).
        assertFalse(advisor.handles("Lịch học của sinh viên A"));
        assertFalse(advisor.handles("Điểm của lớp trưởng A"));
        // Lecturer grading-status phrasing stays on the knowledge path
        // today (documented residual — never wired to the personal path).
        assertFalse(advisor.handles("Sinh viên nào chưa có điểm?"));

        // Wukong round-6 (FALSIFIED -> repaired), second pass:
        // Collective forms beyond "anh em mình" — "anh em bon/chung minh"
        // and "anh chị em minh" all mean "we"; the strip leaves the real
        // pronoun, so the asker's own list still answers.
        assertTrue(advisor.handles("anh em bon minh dang hoc lop nao"));
        assertTrue(advisor.handles("anh em chung minh dang hoc lop nao"));
        assertTrue(advisor.handles("anh chị em minh dang hoc lop nao"));
        assertTrue(advisor.handles("chi em tui minh dang hoc lop nao"));
        // "X có biết" is the politeness frame "do you know" — the question
        // still asks about the asker ("bạn có biết TÔI đang học lớp nào").
        assertTrue(advisor.handles("ban co biet toi dang hoc lop nao"));
        assertTrue(advisor.handles("bạn có biết tôi đang học lớp nào"));
        // Dropped E-subject homographs: "đường đi học" (the road to
        // school), "hạn đăng ký" (the deadline), "câu nào" (which
        // sentence), "đưa tôi" (hand me) are not kinship subjects.
        assertTrue(advisor.handles("duong di hoc xa, thu 2 toi co mon gi"));
        // "hạn đăng ký" asks WHEN registration closes — a public calendar
        // question, not the asker's class list ("hạn mức" stays personal).
        assertFalse(advisor.handles("han dang ky mon nao cho toi"));
        assertFalse(advisor.handles("deadline đăng ký môn của tôi"));
        // Opinion/advice questions revived by the unicode \b fix must not
        // answer with the asker's registrations — "which subject is
        // hardest / easiest to pass" is general advice (F5).
        assertFalse(advisor.handles("cho tôi hỏi môn gì khó nhất"));
        assertFalse(advisor.handles("môn nào dễ qua"));
        assertFalse(advisor.handles("học phần nào nên học"));
        assertFalse(advisor.handles("môn gì dễ đậu nhất"));
        // ...while a personal list phrasing that merely LOOKS near the
        // opinion shape stays personal.
        assertTrue(advisor.handles("cho em hỏi em đang học môn gì"));

        // Wukong round-7 (FALSIFIED -> repaired):
        // The deadline guard's bare "han" matched inside "học phAn",
        // "thÁNg", "hẰNg" — ordinary enrollment phrasing died at the
        // knowledge path. Context wording keeps "hạn đăng ký" public.
        assertTrue(advisor.handles("toi dang hoc phan nao"));
        assertTrue(advisor.handles("tháng này tôi học môn gì"));
        assertTrue(advisor.handles("hang ngay toi co mon gi"));
        assertTrue(advisor.handles("hạn mức đăng ký còn lại của tôi"));
        // Politeness frames beyond "ơi/ạ/có biết" — request verbs
        // (giúp/cho/hãy), vocative "à", collective "hai đứa/bọn em/
        // tụi em mình" — all still ask about the asker.
        assertTrue(advisor.handles("anh em oi minh dang hoc lop nao"));
        assertTrue(advisor.handles("em mình đang học lớp nào"));
        assertTrue(advisor.handles("hai đứa mình đang học lớp nào"));
        assertTrue(advisor.handles("bọn em mình đang học lớp nào"));
        assertTrue(advisor.handles("tụi em mình đang học lớp nào"));
        assertTrue(advisor.handles("anh à cho em xem bảng điểm"));
        assertTrue(advisor.handles("chị giúp em xem bảng điểm"));
        assertTrue(advisor.handles("anh giúp em xem lịch học"));
        assertTrue(advisor.handles("bạn cho tôi xem lịch học"));
        assertTrue(advisor.handles("em giúp tôi xem lịch học"));
        assertTrue(advisor.handles("cho em xem tôi đang học môn gì"));
        // Own-noun English possessives keep "my" — "my class's schedule"
        // is the asker's own timetable, never a third person.
        assertTrue(advisor.handles("my class's schedule"));
        // Conversational-opener phrasing keeps its personal route — the
        // advisor claims it, and the service tier no longer hijacks it.
        assertTrue(advisor.handles("Cảm ơn, lịch học của tôi"));
        assertTrue(advisor.handles("Thanks, what is my schedule?"));
        // Third-person escapes closed: relation vocative "cô tôi", staff
        // roles after "của", Southern "ảnh/bả", demonstrative "thằng đó",
        // the zero-width-joined "của\u200Bmẹ", the curly-apostrophe
        // "Nam’s", and the partner-possessive all still veto.
        assertFalse(advisor.handles("lịch học của cô tôi"));
        assertFalse(advisor.handles("điểm của gia sư"));
        assertFalse(advisor.handles("lịch học của trợ giảng"));
        assertFalse(advisor.handles("điểm của cố vấn"));
        assertFalse(advisor.handles("lịch học của hiệu trưởng"));
        assertFalse(advisor.handles("lịch học của ảnh"));
        assertFalse(advisor.handles("điểm của bả"));
        assertFalse(advisor.handles("lịch học của thằng đó"));
        assertFalse(advisor.handles("điểm của cụ ấy"));
        assertFalse(advisor.handles("lịch học của\u200Bmẹ"));
        assertFalse(advisor.handles("Nam’s schedule"));
        assertFalse(advisor.handles("my partner's schedule"));

        // Wukong round-8 (FALSIFIED -> repaired):
        // M1 — the "em <kinship>" family: em gái/em trai/em họ/em út/em
        // ruột/em chồng/em vợ/em này are unambiguous non-self relations,
        // but bare "em" filled the first-person pronoun slot in the intent
        // arms and served the asker's own rows for "little sister's class".
        assertFalse(advisor.handles("lớp em gái đang học"));
        assertFalse(advisor.handles("môn em trai đang học"));
        assertFalse(advisor.handles("học phần em họ đang học"));
        assertFalse(advisor.handles("lớp em út đang học"));
        assertFalse(advisor.handles("lớp em này đang học"));
        assertFalse(advisor.handles("lịch học của em họ"));
        assertFalse(advisor.handles("thời khóa biểu của em út"));
        assertFalse(advisor.handles("điểm của em họ"));
        assertFalse(advisor.handles("bảng điểm của em út"));
        assertFalse(advisor.handles("đề tài của em họ"));
        assertFalse(advisor.handles("luận văn của em ruột"));
        // M2 — uncovered possessive relations + collective prefixes; the
        // pronoun-free schedule intent made arm-F the sole guard.
        assertFalse(advisor.handles("lịch học của đàn anh"));
        assertFalse(advisor.handles("tkb của chủ nhiệm"));
        assertFalse(advisor.handles("lịch dạy của sư phụ"));
        assertFalse(advisor.handles("lịch học của nhóm trưởng"));
        assertFalse(advisor.handles("tkb cua truong nhom"));
        assertFalse(advisor.handles("lịch học của tổ trưởng"));
        assertFalse(advisor.handles("lịch của bọn chúng"));
        assertFalse(advisor.handles("lịch học của hai đứa"));
        assertFalse(advisor.handles("lịch của cả nhà"));
        assertFalse(advisor.handles("lịch học của ai đó"));
        // M3 — arm-E subject/verb gaps: accented thím/cụ/bé/thằng,
        // two-word kinship, and start/direction verbs.
        assertFalse(advisor.handles("thím đang dạy môn gì, tôi muốn biết"));
        assertFalse(advisor.handles("bà ngoại đang dạy môn gì? tôi muốn biết"));
        assertFalse(advisor.handles("mẹ bắt đầu học môn nào? tôi muốn biết"));
        assertFalse(advisor.handles("em gái đang học lớp nào"));
        assertFalse(advisor.handles("con trai tôi đang học gì"));
        assertFalse(advisor.handles("ông nội vào lớp nào"));
        // M4 — unaccented/in-law "con" compounds read the trailing pronoun
        // as the possessive.
        assertFalse(advisor.handles("lớp con gai toi dang hoc"));
        assertFalse(advisor.handles("lớp con de toi dang hoc"));
        assertFalse(advisor.handles("điểm của con gai toi"));
        assertFalse(advisor.handles("lớp con dâu tôi đang học"));
        // M5 — Zl/Zp/Cc/filler separators glued the possessive exactly like
        // ZWSP did: "của\u2028mẹ" (U+2028 soft break) defeated arm-F.
        assertFalse(advisor.handles("lịch học của\u2028mẹ"));
        assertFalse(advisor.handles("lịch học của\u2029mẹ"));
        // L4 — non-ASCII apostrophes beyond U+0027/U+2019.
        assertFalse(advisor.handles("Nam\u02BCs schedule"));
        assertFalse(advisor.handles("Nam\u2032s schedule"));
        // L7 — Southern compound vocatives address the assistant, not a
        // third party.
        assertTrue(advisor.handles("chú em ơi tôi đang học môn gì"));
        assertTrue(advisor.handles("cô em oi cho em xem bang diem"));
        // Round-8 repaired over-blocks must stay personal.
        assertTrue(advisor.handles("bai toi dang hoc mon nao"));
        assertTrue(advisor.handles("Học kỳ 1 năm 2025 tôi học môn gì?"));

        // ---- Wukong round-9 pins ----
        // F1 — unaccented "thu N" day tokens must route (and parse a day —
        // exercised end-to-end by the "thu 3" answer pin below).
        assertTrue(advisor.handles("thu 3 toi co lop gi"));
        assertTrue(advisor.handles("thu nam toi day mon gi"));
        // F2 — "của tháng" (of the month) is not a person.
        assertTrue(advisor.handles("lich hoc cua thang nay"));
        // Round-9 self-find: under CASE_INSENSITIVE|UNICODE_CASE, \p{Lu}
        // also matched lowercase, so the named-person arm vetoed EVERY
        // noun after "của" — objects, time words, "của môn/kỳ". The
        // (?-i:…) scope restores real uppercase matching; these objects
        // must keep routing to the asker's own data.
        assertTrue(advisor.handles("lich hoc cua mon toi dang hoc"));
        assertTrue(advisor.handles("lich hoc cua ky nay"));
        assertTrue(advisor.handles("lịch học của tháng này"));
        // …while a mid-sentence CapitalizedName still names a person.
        assertFalse(advisor.handles("diem cua Nam the nao"));
        // F3 — "bạn có (thể) …" request frames address the bot; the modal
        // chain strips cleanly now and bare "bạn" no longer vets.
        assertTrue(advisor.handles("Bạn có thể cho tôi xem điểm của tôi không?"));
        assertTrue(advisor.handles("Bạn có lịch học của tôi không?"));
        assertTrue(advisor.handles("Bạn có điểm của tôi không?"));
        assertTrue(advisor.handles("Bạn có thể cho tôi xem lịch học"));
        assertTrue(advisor.handles("bạn có biết tôi đang học lớp nào"));
        // F4 — F-only roles and institutions now veto as subjects/possessives.
        assertFalse(advisor.handles("Đồng nghiệp tôi đang học lớp nào"));
        assertFalse(advisor.handles("Trợ giảng tôi đang dạy môn nào"));
        assertFalse(advisor.handles("Sếp tôi đang học môn gì"));
        assertFalse(advisor.handles("đồng nghiệp đang dạy môn nào"));
        assertFalse(advisor.handles("Lịch học của Nam"));
        assertFalse(advisor.handles("Điểm rèn luyện của Nam"));
        assertFalse(advisor.handles("lịch dạy của khoa"));
        assertFalse(advisor.handles("lịch học của phòng đào tạo"));
        // F5 — policy wording must not open the schedule door.
        assertFalse(advisor.handles("Quy định về lịch học kỳ này thế nào?"));
        assertFalse(advisor.handles("Theo quy chế, tiết học tối đa mấy buổi?"));
        // F6 — advice word order: pronoun bridge and verb-first forms.
        assertFalse(advisor.handles("tôi nên học môn nào"));
        assertFalse(advisor.handles("môn nào tôi nên học"));
        assertFalse(advisor.handles("em nên đăng ký môn nào"));
        // F7 — bare "exam" must not veto grade phrasings.
        assertTrue(advisor.handles("what are my exam scores"));
        assertTrue(advisor.handles("show my exam grades"));
        // F10 — colloquial self pronouns.
        assertTrue(advisor.handles("tớ đang học lớp nào"));
        assertTrue(advisor.handles("tui dang hoc lop nao"));
        assertTrue(advisor.handles("mến đang học lớp nào"));
        // "cháu" stays the documented fail-soft residual: it is the ordinary
        // third-person noun, so the self-reading misses (knowledge path).
        assertFalse(advisor.handles("cháu đang học lớp nào"));
        // F12 — pronoun-stem contractions are not possessives.
        assertTrue(advisor.handles("let's check my grades"));

        // ---- Wukong round-10 pins ----
        // F1 — a CapitalizedName spelled like a first-person pronoun still
        // names ANOTHER person: the exemption only protects lowercase-typed
        // pronouns, so "của Minh"/"của Em" veto to the knowledge path
        // instead of serving the asker's own transcript.
        assertFalse(advisor.handles("diem cua Minh"));
        assertFalse(advisor.handles("lich hoc cua Minh"));
        assertFalse(advisor.handles("lich hoc cua Em"));
        // "của em <Name>" is the idiomatic sibling-naming frame — the bare
        // "em" exemption used to swallow the following name token.
        assertFalse(advisor.handles("diem cua em Lan"));
        assertFalse(advisor.handles("lich hoc cua em Hung"));
        // Wukong round-11 follow-up: the relation+name frame generalizes —
        // "của anh Tuấn"/"của chị Lan"/"của thầy Hùng"/"của bạn Minh" also
        // name someone else, not the asker.
        assertFalse(advisor.handles("lich hoc cua anh Tuan"));
        assertFalse(advisor.handles("lich hoc cua chi Lan"));
        assertFalse(advisor.handles("lich hoc cua thay Hung"));
        assertFalse(advisor.handles("lich hoc cua ban Minh"));
        assertFalse(advisor.handles("lịch học của cô Hồng"));
        // …while genuine lowercase self-references keep resolving personal.
        assertTrue(advisor.handles("diem cua minh"));
        assertTrue(advisor.handles("lich hoc cua em"));
        assertTrue(advisor.handles("điểm của mình"));
    }

    @Test
    void dayQualifiedClassPhrasingAnswersTheDayTimetableNotTheWholeList() {
        when(enrollmentService.findStudentEnrollments("student-profile", null)).thenReturn(List.of(
                enrollment("SE401", "Lập trình Java nâng cao", "Advanced Java", CURRENT_TERM_START,
                        List.of(new SectionScheduleResponse("s1", 3, "07:00", "09:30",
                                new ClassroomSummary("c1", "A", "101")))),
                enrollment("SE403", "Cấu trúc dữ liệu và giải thuật", "Data Structures", CURRENT_TERM_START,
                        List.of(new SectionScheduleResponse("s2", 5, "09:45", "11:45",
                                new ClassroomSummary("c2", "A", "103"))))));

        ChatResponse response = advisor.answer(
                chatRequest("vi", "Thứ 3 lớp tôi đang học"), jwtStudent());

        assertNotNull(response);
        String answer = response.answer();
        assertTrue(answer.contains("Lịch học Thứ Ba"), answer);
        assertTrue(answer.contains("SE401"), answer);
        assertFalse(answer.contains("SE403"), "day-qualified phrasing must keep its day filter");

        // Wukong round-9 F1: the unaccented twin "thu 3" passed the intent
        // gate but parsed no day, answering the whole list — it must keep
        // the same day filter.
        ChatResponse unaccented = advisor.answer(
                chatRequest("vi", "thu 3 toi co lop gi"), jwtStudent());
        assertNotNull(unaccented);
        String unaccentedAnswer = unaccented.answer();
        assertTrue(unaccentedAnswer.contains("Thứ Ba"), unaccentedAnswer);
        assertTrue(unaccentedAnswer.contains("SE401"), unaccentedAnswer);
        assertFalse(unaccentedAnswer.contains("SE403"),
                "unaccented 'thu N' must keep the same day filter");
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
    void weeklyCourseAndRoomQuestionsAnswerTheTimetableInsteadOfTheEnrollmentList() {
        when(enrollmentService.findStudentEnrollments("student-profile", null)).thenReturn(List.of(
                enrollment("SE401", "Lập trình Java nâng cao", "Advanced Java", CURRENT_TERM_START,
                        List.of(new SectionScheduleResponse("s1", 2, "07:00", "09:30",
                                new ClassroomSummary("c1", "A", "101"))))));

        for (String question : List.of(
                "Tuần này tôi học những môn nào, ở phòng nào?",
                "Tuan nay toi hoc nhung mon nao, o phong nao?",
                "Tuần này tôi học những môn nào?",
                "Tôi học những môn nào, ở phòng nào?",
                "Which classes am I taking this week, in which room?",
                "What courses am I taking this week?")) {
            assertTrue(advisor.handles(question), question);
            ChatResponse response = advisor.answer(chatRequest("vi", question), jwtStudent());
            assertNotNull(response, question);
            assertEquals("PERSONAL_CONTEXT", response.reasonCode());
            assertTrue(response.answer().contains("Lịch học cá nhân"), response.answer());
            assertTrue(response.answer().contains("Thứ Hai 07:00-09:30"), response.answer());
            assertTrue(response.answer().contains("phòng A 101"), response.answer());
            assertFalse(response.answer().contains("ENROLLED"), response.answer());
        }
    }

    @Test
    void timetableDetailRoutingPreservesPlainEnrollmentAndPublicQuestions() {
        when(enrollmentService.findStudentEnrollments("student-profile", null)).thenReturn(List.of(
                enrollment("SE401", "Lập trình Java nâng cao", "Advanced Java", CURRENT_TERM_START,
                        List.of(new SectionScheduleResponse("s1", 2, "07:00", "09:30",
                                new ClassroomSummary("c1", "A", "101"))))));

        ChatResponse enrollmentList = advisor.answer(
                chatRequest("vi", "Tôi đã đăng ký những môn nào?"), jwtStudent());
        assertNotNull(enrollmentList);
        assertTrue(enrollmentList.answer().contains("SE401"), enrollmentList.answer());
        assertFalse(enrollmentList.answer().contains("Lịch học cá nhân"), enrollmentList.answer());

        assertFalse(advisor.handles("Tuần này học những môn nào, ở phòng nào?"));
        assertFalse(advisor.handles("Tuần này tôi đăng ký môn học như thế nào?"));
        assertFalse(advisor.handles("Tuần này tôi thi những môn nào, ở phòng thi nào?"));
    }

    @Test
    void streamsTheAlreadyComputedAnswerWithoutReadingPersonalRecordsAgain() {
        when(enrollmentService.findStudentEnrollments("student-profile", null)).thenReturn(List.of(
                enrollment("SE401", "Lập trình Java nâng cao", "Advanced Java", CURRENT_TERM_START,
                        List.of(new SectionScheduleResponse("s1", 2, "07:00", "09:30",
                                new ClassroomSummary("c1", "A", "101"))))));

        UUID clientRequestId = UUID.randomUUID();
        ChatRequest request = new ChatRequest("Lịch học của tôi tuần này?", "vi", clientRequestId, null);
        ChatResponse response = advisor.answer(request, jwtStudent());
        List<ThesisAssistantService.StreamEvent> events = new ArrayList<>();

        advisor.stream(response, request, events::add);

        // Kongming F10: JSON/SSE parity — the streamed meta must echo the
        // clientRequestId exactly like the JSON response does.
        assertTrue(events.stream().anyMatch(event -> event instanceof ThesisAssistantService.StreamMeta meta
                && clientRequestId.equals(meta.clientRequestId())));
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
    void returnsAnHonestNoContextAnswerWithoutPersonalClaims() {
        // F13: a personal-intent question from an actor with no student/lecturer
        // claim used to fall through to the corpus and come back as a fabricated
        // schedule-shaped reply. Now it gets an honest PERSONAL_CONTEXT answer.
        Jwt guest = new Jwt("token", Instant.now(), Instant.now().plusSeconds(600),
                Map.of("alg", "HS256"), new HashMap<>(Map.of("sub", "someone", "roles", List.of("STUDENT"))));

        ChatResponse response = advisor.answer(chatRequest("vi", "lịch học của tôi?"), guest);
        assertNotNull(response);
        assertEquals("PERSONAL_CONTEXT", response.reasonCode());
        assertTrue(response.answer().contains("chưa gắn hồ sơ"), response.answer());
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
                        "OPEN", "sem-hk2", "Học kỳ 2 năm học 2025-2026", "Semester 2, 2025-2026", "Học kỳ 2 năm học 2025-2026", CURRENT_TERM_START, List.of(new io.campuscore.restfulapi.academic.web.AcademicSectionReadDtos
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
    void lecturerScheduleScopesToTheNamedAndNewestSemester() {
        // Kongming F1: the schedule/credits answers used to mix every
        // semester under a "kỳ này" label. A named semester filters to it;
        // a bare question takes the newest semester and names it honestly.
        when(sectionService.findLecturerSchedule("lecturer-profile", null)).thenReturn(List.of(
                lecturerSection("sec-hk1", "SE301", "Cơ sở dữ liệu", 3,
                        "sem-hk1", "Học kỳ 1 năm học 2025-2026", OLD_TERM_START),
                lecturerSection("sec-hk2", "SE402", "Phát triển ứng dụng web", 3)));

        ChatResponse named = advisor.answer(
                chatRequest("vi", "Học kỳ 1 năm học 2025-2026 tôi giảng dạy những lớp nào?"), jwtLecturer());
        assertNotNull(named);
        assertTrue(named.answer().contains("SE301"), named.answer());
        assertFalse(named.answer().contains("SE402"), named.answer());

        ChatResponse bare = advisor.answer(
                chatRequest("vi", "lịch dạy của tôi tuần này?"), jwtLecturer());
        assertNotNull(bare);
        assertTrue(bare.answer().contains("SE402"), bare.answer());
        assertFalse(bare.answer().contains("SE301"), "unnamed question must take the newest semester only");
        assertTrue(bare.answer().contains("Học kỳ 2 năm học 2025-2026"),
                "the label must name the semester the rows belong to");

        ChatResponse credits = advisor.answer(
                chatRequest("vi", "Học kỳ 1 năm học 2025-2026 tôi dạy bao nhiêu tín chỉ?"), jwtLecturer());
        assertNotNull(credits);
        assertTrue(credits.answer().contains("Học kỳ 1 năm học 2025-2026"), credits.answer());
        assertTrue(credits.answer().contains("SE301"), credits.answer());
        assertFalse(credits.answer().contains("SE402"), credits.answer());
    }

    @Test
    void lecturerDayQualifiedAnswerDisclosesTheScopedSemester() {
        // Kongming F1: the day-qualified lecturer header used to drop the
        // termName the full-list branch carries — the common phrasing
        // ("thứ 5 tôi dạy môn nào?") lost the scope disclosure.
        when(sectionService.findLecturerSchedule("lecturer-profile", null)).thenReturn(List.of(
                lecturerSection("sec-hk2", "SE402", "Phát triển ứng dụng web", 3)));

        ChatResponse response = advisor.answer(
                chatRequest("vi", "thứ 2 tôi dạy môn nào?"), jwtLecturer());

        assertNotNull(response);
        assertTrue(response.answer().contains("Học kỳ 2 năm học 2025-2026"), response.answer());
    }

    @Test
    void cancelledSectionsDoNotCountTowardTheNewestSemesterScope() {
        // Kongming F3: a CANCELLED newest-semester row used to win the
        // "newest" computation and present a cancelled section as the
        // current teaching load.
        when(sectionService.findLecturerSchedule("lecturer-profile", null)).thenReturn(List.of(
                lecturerSection("sec-hk1", "SE301", "Cơ sở dữ liệu", 3,
                        "sem-hk1", "Học kỳ 1 năm học 2025-2026", OLD_TERM_START),
                cancelledLecturerSection("sec-cancelled", "SE999", "Môn đã hủy", 3,
                        "sem-hk2", "Học kỳ 2 năm học 2025-2026", CURRENT_TERM_START)));

        ChatResponse response = advisor.answer(
                chatRequest("vi", "lịch dạy của tôi tuần này?"), jwtLecturer());

        assertNotNull(response);
        assertTrue(response.answer().contains("SE301"), response.answer());
        assertFalse(response.answer().contains("SE999"), "CANCELLED rows must leave the scope entirely");
    }

    @Test
    void studentNamedSemesterPlusDayAnswersTheNamedTerm() {
        // Kongming F2: a named semester + day question used to answer the
        // CURRENT term's day instead of the named term's.
        when(enrollmentService.findStudentEnrollments("student-profile", null)).thenReturn(List.of(
                enrollmentWithSemester("SE301", "Cơ sở dữ liệu", "sem-hk1",
                        "Học kỳ 1 năm học 2025-2026", OLD_TERM_START,
                        List.of(new SectionScheduleResponse("s-old", 3, "07:00", "09:30",
                                new ClassroomSummary("c-old", "A", "101")))),
                enrollment("SE402", "Phát triển ứng dụng web", "Web Dev", CURRENT_TERM_START,
                        List.of(new SectionScheduleResponse("s-new", 3, "13:00", "15:30",
                                new ClassroomSummary("c-new", "B", "202"))))));

        ChatResponse named = advisor.answer(
                chatRequest("vi", "Học kỳ 1 năm học 2025-2026 thứ 3 tôi học môn gì?"), jwtStudent());
        assertNotNull(named);
        assertTrue(named.answer().contains("SE301"), named.answer());
        assertFalse(named.answer().contains("SE402"), "named semester must not leak the current term");

        ChatResponse miss = advisor.answer(
                chatRequest("vi", "Học kỳ 3 năm học 2024-2025 thứ 3 tôi học môn gì?"), jwtStudent());
        assertNotNull(miss);
        assertTrue(miss.answer().contains("không tìm thấy"), miss.answer());
        assertFalse(miss.answer().contains("SE301"), "named miss must not fall back to another term");
    }

    @Test
    void existenceDayTeachingPhrasingPrefersTheLecturerTimetable() {
        // "thứ 2 tôi có lớp dạy không?" — a dual-profile user asking
        // about TEACHING must not get the student timetable (Wukong A7).
        when(sectionService.findLecturerSchedule("lecturer-profile", null)).thenReturn(List.of(
                lecturerSection("sec1", "SE402", "Phát triển ứng dụng web", 3)));
        when(enrollmentService.findStudentEnrollments("student-profile", null)).thenReturn(List.of(
                enrollment("SE401", "Lập trình Java nâng cao", "Advanced Java", CURRENT_TERM_START,
                        List.of(new SectionScheduleResponse("s1", 2, "07:00", "09:30",
                                new ClassroomSummary("c1", "A", "101"))))));

        ChatResponse response = advisor.answer(
                chatRequest("vi", "thứ 2 tôi có lớp dạy không?"), jwtDualProfile());
        assertNotNull(response);
        assertTrue(response.answer().contains("giảng dạy"), response.answer());
        assertTrue(response.answer().contains("SE402"), response.answer());
        assertFalse(response.answer().contains("SE401"), "student timetable must not answer a teaching question");
    }

    @Test
    void composesLecturerTeachingTimetable() {
        when(sectionService.findLecturerSchedule("lecturer-profile", null)).thenReturn(List.of(
                new LecturerScheduleResponse("id1", "sec1", "SE402-01", "SE402", "Phát triển ứng dụng web",
                        "Web Application Development", "Phát triển ứng dụng web", 3, 35, 13, "CNTT", "CNTT", "CNTT",
                        "OPEN", "sem-hk2", "Học kỳ 2 năm học 2025-2026", "Semester 2, 2025-2026", "Học kỳ 2 năm học 2025-2026", CURRENT_TERM_START, List.of(new io.campuscore.restfulapi.academic.web.AcademicSectionReadDtos
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
        // "đăng ký" phrasing of the same personal budget question — observed
        // falling to the KB path while the "tín chỉ" twin answered correctly.
        assertTrue(advisor.handles("hạn mức đăng ký còn lại của tôi"));
        assertTrue(advisor.handles("han muc dang ky con lai"));
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
    void normativeQuestionsStayOnTheKnowledgePathEvenWithCodesOrNumbers() {
        // C7: policy wording next to a personal-intent pattern must not be
        // intercepted — these ask the RULE, not the asker's own record.
        assertFalse(advisor.handles("Quy định tính điểm rèn luyện như thế nào?"));
        assertFalse(advisor.handles("Theo quy chế cần đủ 143 tín chỉ tốt nghiệp phải không?"));
        assertFalse(advisor.handles("Quy định sĩ số phòng học của lớp SE013 tối đa bao nhiêu?"));
        // And the personal phrasing of the same topics still routes correctly.
        assertTrue(advisor.handles("điểm rèn luyện của tôi mấy điểm?"));
        assertTrue(advisor.handles("Còn thiếu bao nhiêu tín chỉ để đủ 143 tín chỉ tốt nghiệp?"));
        assertTrue(advisor.handles("Lớp SE013 học phòng nào, giờ nào?"));
    }

    @Test
    void wukongN2CounterexamplesStayOnTheKnowledgePath() {
        // Wukong round-2 falsification: each of these slipped past the policy
        // gate and was answered with the asker's personal record.
        assertFalse(advisor.handles("Điểm rèn luyện có bao nhiêu mức xếp loại?"));
        assertFalse(advisor.handles("Xếp loại rèn luyện gồm bao nhiêu loại?"));
        assertFalse(advisor.handles("Điểm rèn luyện tính theo thang 100 điểm?"));
        assertFalse(advisor.handles("Sinh viên bị kỷ luật trừ mấy điểm rèn luyện?"));
        assertFalse(advisor.handles("Sinh viên cần đủ 130 tín chỉ mới được tốt nghiệp đúng không?"));
        assertFalse(advisor.handles("Cách đổi phòng học cho lớp SE013-01?"));
        assertFalse(advisor.handles("Thủ tục mượn phòng cho lớp SE013?"));
    }

    @Test
    void sectionDetailServesThePublicCatalogToClaimlessActors() {
        // C7/Kongming: a section-code question carries public catalog data —
        // an actor with no student profile gets the published schedule instead
        // of a bare "no profile" denial.
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        AssistantPersonalContextAdvisor catalogAdvisor = new AssistantPersonalContextAdvisor(
                enrollmentService, sectionService, null, jdbc, null, null);
        Map<String, Object> row = new HashMap<>();
        row.put("section_number", "SE099-01");
        row.put("course_code", "SE099");
        row.put("course_name_vi", "Học máy ứng dụng");
        row.put("schedule_day", 3);
        row.put("schedule_start", "07:00");
        row.put("schedule_end", "09:30");
        row.put("room_building", "B");
        row.put("room_number", "204");
        when(jdbc.queryForList(anyString(), any(MapSqlParameterSource.class))).thenReturn(List.of(row));
        Jwt guest = new Jwt("token", Instant.now(), Instant.now().plusSeconds(600),
                Map.of("alg", "HS256"), new HashMap<>(Map.of("sub", "admin-1", "roles", List.of("ADMIN"))));

        ChatResponse response = catalogAdvisor.answer(
                chatRequest("vi", "Lớp SE099 học phòng nào, giờ nào?"), guest);

        assertNotNull(response);
        String answer = response.answer();
        assertTrue(answer.contains("có trong danh mục học phần"), answer);
        assertTrue(answer.contains("Thứ Ba 07:00-09:30"), answer);
        assertTrue(answer.contains("(phòng B 204)"), answer);
        assertFalse(answer.contains("chưa gắn hồ sơ"), answer);
        assertFalse(answer.contains("chưa đăng ký"), answer);
        org.mockito.Mockito.verifyNoInteractions(enrollmentService);
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
                        "OPEN", "sem-hk2", "Học kỳ 2 năm học 2025-2026", "Semester 2, 2025-2026", "Học kỳ 2 năm học 2025-2026", CURRENT_TERM_START, List.of(new io.campuscore.restfulapi.academic.web.AcademicSectionReadDtos
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
        when(sectionService.findLecturerSchedule("lecturer-profile", null)).thenReturn(List.of(
                lecturerSection("sec1", "SE402", "Phát triển ứng dụng web", 3)));
        when(sectionService.findLecturerGradingSections("lecturer-profile", "sem-hk2")).thenReturn(List.of(
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
    void lecturerGradingFollowsTheSharedSemesterScope() {
        // Kongming F1: the grading list used to span every semester — the
        // repository is now asked for the scoped (newest) semester, and a
        // named semester resolves to its own id.
        when(sectionService.findLecturerSchedule("lecturer-profile", null)).thenReturn(List.of(
                lecturerSection("sec-hk1", "SE301", "Cơ sở dữ liệu", 3,
                        "sem-hk1", "Học kỳ 1 năm học 2025-2026", OLD_TERM_START),
                lecturerSection("sec-hk2", "SE402", "Phát triển ứng dụng web", 3)));
        when(sectionService.findLecturerGradingSections("lecturer-profile", "sem-hk2")).thenReturn(List.of(
                new LecturerGradingSectionResponse("id2", "sec-hk2", "SE402-01", "SE402",
                        "Phát triển ứng dụng web", "Web Application Development", "Phát triển ứng dụng web",
                        3, "CNTT", "CNTT", "CNTT",
                        "HK2 2025-2026", "HK2 2025-2026", "HK2 2025-2026", "HK2 2025-2026",
                        35L, 20L, 12L, "PARTIAL", true)));

        ChatResponse newest = advisor.answer(
                chatRequest("vi", "Điểm học phần tôi phụ trách hiện đã có chưa?"), jwtLecturer());
        assertNotNull(newest);
        assertTrue(newest.answer().contains("SE402"), newest.answer());
        org.mockito.Mockito.verify(sectionService)
                .findLecturerGradingSections("lecturer-profile", "sem-hk2");

        when(sectionService.findLecturerGradingSections("lecturer-profile", "sem-hk1")).thenReturn(List.of(
                new LecturerGradingSectionResponse("id1", "sec-hk1", "SE301-01", "SE301",
                        "Cơ sở dữ liệu", "Databases", "Cơ sở dữ liệu",
                        3, "CNTT", "CNTT", "CNTT",
                        "HK1 2025-2026", "HK1 2025-2026", "HK1 2025-2026", "HK1 2025-2026",
                        30L, 30L, 30L, "PUBLISHED", false)));
        ChatResponse named = advisor.answer(
                chatRequest("vi", "Học kỳ 1 năm học 2025-2026 điểm học phần tôi phụ trách đã có chưa?"), jwtLecturer());
        assertNotNull(named);
        assertTrue(named.answer().contains("SE301"), named.answer());
        org.mockito.Mockito.verify(sectionService)
                .findLecturerGradingSections("lecturer-profile", "sem-hk1");
    }

    @Test
    void answersLecturerGradingWithoutAssignmentHonestly() {
        // Wukong round-10 F2: a lecturer with NO sections must not fall back
        // to an unscoped grading query — the empty scope is the honest
        // "nothing assigned" answer, and the repository is never called.
        ChatResponse response = advisor.answer(
                chatRequest("vi", "Điểm học phần tôi phụ trách hiện đã có chưa?"), jwtLecturer());

        assertNotNull(response);
        assertTrue(response.answer().contains("chưa được phân công nhập điểm"), response.answer());
        org.mockito.Mockito.verify(sectionService, org.mockito.Mockito.never())
                .findLecturerGradingSections(org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.nullable(String.class));
    }

    @Test
    void answersLecturerAttendanceAbsencesForToday() {
        AcademicAttendanceReadService attendanceService = mock(AcademicAttendanceReadService.class);
        AssistantPersonalContextAdvisor attendanceAdvisor = new AssistantPersonalContextAdvisor(
                enrollmentService, sectionService, null, null, null, null, attendanceService);
        when(sectionService.findLecturerSchedule("lecturer-profile", null)).thenReturn(List.of(
                new LecturerScheduleResponse("id1", "sec-401", "SE401-01", "SE401",
                        "Lập trình Java nâng cao", "Advanced Java", "Lập trình Java nâng cao",
                        3, 45, 40, "CNTT", "CNTT", "CNTT", "OPEN", "sem-hk2", "Học kỳ 2 năm học 2025-2026", "Semester 2, 2025-2026", "Học kỳ 2 năm học 2025-2026", CURRENT_TERM_START, List.of())));
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
                        3, 45, 40, "CNTT", "CNTT", "CNTT", "OPEN", "sem-hk2", "Học kỳ 2 năm học 2025-2026", "Semester 2, 2025-2026", "Học kỳ 2 năm học 2025-2026", CURRENT_TERM_START, List.of())));
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
                        3, 45, 40, "CNTT", "CNTT", "CNTT", "OPEN", "sem-hk2", "Học kỳ 2 năm học 2025-2026", "Semester 2, 2025-2026", "Học kỳ 2 năm học 2025-2026", CURRENT_TERM_START, List.of())));

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
     * so all three lecturer branches must answer with the honest no-profile
     * message WITHOUT calling any read service. The services throw 403 on a
     * missing profile claim and answer() only catches DataAccessException, so
     * an unguarded call would turn the question into an HTTP error.
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

        // Claim-missing lecturer intents now answer honestly (F13) — the gate
        // still fires BEFORE any read service is touched.
        assertTrue(guardedAdvisor.answer(
                chatRequest("vi", "Khối lượng hướng dẫn của tôi hiện tại là bao nhiêu?"), studentActor)
                .answer().contains("chưa gắn hồ sơ"));
        assertTrue(guardedAdvisor.answer(
                chatRequest("vi", "Điểm học phần tôi phụ trách hiện đã có chưa?"), studentActor)
                .answer().contains("chưa gắn hồ sơ"));
        assertTrue(guardedAdvisor.answer(
                chatRequest("vi", "Sinh viên lớp SE401 hôm nay có ai vắng mặt không?"), studentActor)
                .answer().contains("chưa gắn hồ sơ"));

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
    void graduationCreditsWithLecturerActorAnswersHonestly() {
        ChatResponse response = advisor.answer(
                chatRequest("vi", "Còn thiếu bao nhiêu tín chỉ để đủ 143 tín chỉ tốt nghiệp?"), jwtLecturer());
        assertNotNull(response);
        assertTrue(response.answer().contains("chưa gắn hồ sơ"), response.answer());
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
                        "OPEN", "sem-hk2", "Học kỳ 2 năm học 2025-2026", "Semester 2, 2025-2026", "Học kỳ 2 năm học 2025-2026", CURRENT_TERM_START, List.of(new io.campuscore.restfulapi.academic.web.AcademicSectionReadDtos
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
        // A student actor without a lecturer profile gets the honest
        // no-context answer, not a corpus reply (F13).
        Jwt studentActor = jwt("studentId", "student-profile");
        ChatResponse honest = workloadAdvisor.answer(
                chatRequest("vi", "Những nhóm sinh viên nào tôi đang hướng dẫn?"), studentActor);
        assertNotNull(honest);
        assertTrue(honest.answer().contains("chưa gắn hồ sơ"), honest.answer());
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
        // Headcount vs the 1–3 requirement, computed from the real member rows.
        assertTrue(answer.contains("Nhóm: 3 thành viên (yêu cầu 1-3)"), answer);
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
    void thesisGroupOfTwoMeetsTheOneToThreeRequirementAndStatesPlainMembership() {
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
        // The asker is a plain member; two members satisfy the 1–3 rule.
        Map<String, Object> leader = thesisMemberRow("g2", "student-leader", true, 1, "Phạm", "Dũng");
        Map<String, Object> asker = thesisMemberRow("g2", "student-profile", false, 2, "Hoàng", "Mai");
        when(jdbc.queryForList(anyString(), any(MapSqlParameterSource.class)))
                .thenAnswer(invocation -> ((String) invocation.getArgument(0)).contains("campuscore_auth")
                        ? List.of(leader, asker)
                        : List.of(groupRow));

        ChatResponse response = thesisAdvisor.answer(
                chatRequest("vi", "Nhóm luận văn của tôi là nhóm nào, có những ai?"), jwtStudent());

        String answer = response.answer();
        assertTrue(answer.contains("Nhóm: 2 thành viên (yêu cầu 1-3)"), answer);
        assertTrue(answer.contains("đủ số lượng theo yêu cầu"), answer);
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
        when(enrollmentService.findStudentTranscript(eq("student-profile"), anyList())).thenReturn(
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
        return lecturerSection(sectionId, courseCode, title, credits,
                "sem-hk2", "Học kỳ 2 năm học 2025-2026", CURRENT_TERM_START);
    }

    private static LecturerScheduleResponse lecturerSection(
            String sectionId, String courseCode, String title, int credits,
            String semesterId, String semesterName, Instant semesterStart) {
        return new LecturerScheduleResponse("id-" + sectionId, sectionId, sectionId + "-01", courseCode,
                title, title, title, credits, 40, 12, "CNTT", "ICT", "CNTT", "OPEN",
                semesterId, semesterName, semesterName, semesterName, semesterStart,
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

    /**
     * Kongming round-4: a dual-profile JWT carries BOTH claims — the
     * teaching-list hint must win over the studentId branch so a
     * student+lecturer never gets an enrollment list for a teaching
     * question, and must also win over the named-semester transcript
     * branch ("Học kỳ 1 ... tôi giảng dạy những lớp nào?").
     */
    private static Jwt jwtDualProfile() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", "owner-user");
        claims.put("roles", List.of("STUDENT", "LECTURER"));
        claims.put("studentId", "student-profile");
        claims.put("lecturerId", "lecturer-profile");
        return new Jwt("token", Instant.now(), Instant.now().plusSeconds(600), Map.of("alg", "HS256"), claims);
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

    private static EnrollmentResponse enrollmentWithSemester(
            String code, String name, String semesterId, String semesterName, Instant termStart,
            List<SectionScheduleResponse> schedules) {
        return new EnrollmentResponse(
                "enr-" + code,
                "student-profile",
                "section-" + code,
                semesterId,
                "ENROLLED",
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
                        new CourseSummary("course-" + code, code, name, name, name, 3),
                        new SemesterSummary(semesterId, semesterName, semesterName, semesterName, termStart),
                        null,
                        45,
                        10,
                        "OPEN",
                        schedules),
                null);
    }

    private static LecturerScheduleResponse cancelledLecturerSection(
            String sectionId, String courseCode, String title, int credits,
            String semesterId, String semesterName, Instant semesterStart) {
        LecturerScheduleResponse open = lecturerSection(
                sectionId, courseCode, title, credits, semesterId, semesterName, semesterStart);
        return new LecturerScheduleResponse(open.id(), open.sectionId(), open.sectionNumber(),
                open.courseCode(), open.courseName(), open.courseNameEn(), open.courseNameVi(),
                open.credits(), open.capacity(), open.enrolledCount(), open.departmentName(),
                open.departmentNameEn(), open.departmentNameVi(), "CANCELLED",
                open.semesterId(), open.semesterName(), open.semesterNameEn(), open.semesterNameVi(),
                open.semesterStartDate(), open.schedules());
    }

    /**
     * Round-3 chat fixes (cb3-2..cb3-6): natural EN phrasings and the
     * named-semester VI question used to fall to the KB path, where the model
     * denied the data existed while the portal's own endpoints showed it. Each
     * phrasing must route into the personal-context advisor; curriculum
     * requirement wording stays on the knowledge path.
     */
    @Test
    void round3ChatPhrasingsResolveAsPersonalContext() {
        assertTrue(advisor.handles("How many credits remain?"));
        assertTrue(advisor.handles("Do I have class today?"));
        assertTrue(advisor.handles("What classes do I have today?"));
        assertTrue(advisor.handles("Which classes am I taking?"));
        assertTrue(advisor.handles("Học kỳ 1 năm học 2025-2026 tôi học những môn nào?"));
        assertTrue(advisor.handles("Học kỳ 1 năm học 2025-2026 GPA của tôi là bao nhiêu?"));
        assertTrue(advisor.handles("What is my GPA for semester 2 2025-2026?"));
        // Requirement wording is a policy question, not a registration listing.
        assertFalse(advisor.handles("What courses do I need to graduate?"));
    }

    @Test
    void dualProfileTeachingQuestionAnswersFromLecturerSchedule() {
        // Kongming round-4: the advertised dual-profile ordering was
        // unexercised — every jwt() fixture set exactly one claim. A
        // student+lecturer asking a teaching question must get the
        // teaching schedule, including when a semester is named (the
        // named-semester branch used to steal it first).
        when(sectionService.findLecturerSchedule("lecturer-profile", null)).thenReturn(List.of(
                lecturerSection("sec-dual", "SE402", "Cơ sở dữ liệu", 3,
                        "sem-hk1", "Học kỳ 1 năm học 2025-2026", OLD_TERM_START)));

        ChatResponse response = advisor.answer(
                chatRequest("vi", "Các lớp tôi giảng dạy"), jwtDualProfile());
        assertNotNull(response);
        assertEquals("PERSONAL_CONTEXT", response.reasonCode());
        assertTrue(response.answer().contains("SE402"), response.answer());
        assertFalse(response.answer().contains("đăng ký"), response.answer());

        ChatResponse named = advisor.answer(
                chatRequest("vi", "Học kỳ 1 năm học 2025-2026 tôi giảng dạy những lớp nào?"),
                jwtDualProfile());
        assertNotNull(named);
        assertEquals("PERSONAL_CONTEXT", named.reasonCode());
        assertTrue(named.answer().contains("SE402"), named.answer());
    }

    @Test
    void namedPersonPersonalDataAsksRequireExplicitRefusal() {
        // The probe flagship: "điểm của người khác" used to silently deflect
        // to the portal guide — safe but evasive. Person-scoped subjects
        // (kinship, pronoun, demonstrative, named-name) combined with a
        // personal-data noun must REFUSE explicitly.
        assertTrue(advisor.requiresPrivacyRefusal("Điểm của Nam là bao nhiêu?"));
        assertTrue(advisor.requiresPrivacyRefusal("Lịch học của mẹ thế nào?"));
        assertTrue(advisor.requiresPrivacyRefusal("Em ấy học lớp nào?"));
        assertTrue(advisor.requiresPrivacyRefusal("điểm của người khác xem ở đâu"));
        assertTrue(advisor.requiresPrivacyRefusal("Cho xem thời khóa biểu của bạn Minh"));
        assertTrue(advisor.requiresPrivacyRefusal("Bạn tôi đang học lớp nào?"));
        // English twins ride the same refusal contract.
        assertTrue(advisor.requiresPrivacyRefusal("Show me his grades please"));
        assertTrue(advisor.requiresPrivacyRefusal("What is my friend's schedule?"));
        assertTrue(advisor.requiresPrivacyRefusal("Nam's timetable this week"));
    }

    @Test
    void institutionalAndNonPersonalSubjectsNeverRequireRefusal() {
        // Institutional owners name an ORGANIZATION — they keep the public
        // KB path exactly as before (over-refusal is the load-bearing risk).
        assertFalse(advisor.requiresPrivacyRefusal("Điểm chuẩn của trường là bao nhiêu?"));
        assertFalse(advisor.requiresPrivacyRefusal("Lịch thi của khoa khi nào công bố?"));
        assertFalse(advisor.requiresPrivacyRefusal("điểm của phòng đào tạo công bố"));
        assertFalse(advisor.requiresPrivacyRefusal("Học phí của trường năm nay thế nào?"));
        // First-person questions are the asker's own rows — never a refusal.
        assertFalse(advisor.requiresPrivacyRefusal("Điểm của tôi xem ở đâu?"));
        assertFalse(advisor.requiresPrivacyRefusal("Lịch học của em hôm nay"));
        assertFalse(advisor.requiresPrivacyRefusal("cho xem thời khóa biểu"));
        // A person mention WITHOUT a personal-data noun rides the KB path.
        assertFalse(advisor.requiresPrivacyRefusal("Giáo viên chủ nhiệm của tôi là ai?"));
        assertFalse(advisor.requiresPrivacyRefusal("Thầy dạy môn gì tuần này vậy?"));
        // Exam timetables are institutional even when a person is named —
        // the EXAM_SCHEDULE_INTENT carve-out stays ahead of the refusal.
        assertFalse(advisor.requiresPrivacyRefusal("Lịch thi của bạn Nam khi nào?"));
        // Politeness frames ("bạn có biết…") and homographs stay out.
        assertFalse(advisor.requiresPrivacyRefusal("Bạn có biết lịch học của tôi không?"));
        assertFalse(advisor.requiresPrivacyRefusal(null));
        assertFalse(advisor.requiresPrivacyRefusal(""));
    }

    @Test
    void privacyRefusalResponseCarriesTheRejectShapeWithoutPersonalData() {
        ChatResponse refusal = advisor.privacyRefusal(chatRequest("vi", "Điểm của Nam là bao nhiêu?"));
        assertNotNull(refusal);
        assertEquals("PRIVACY_REFUSAL", refusal.reasonCode());
        // A deliberate answer, not a degraded one — but the terminal shape
        // mirrors the guard rejections (SSE parity).
        assertFalse(refusal.degraded());
        assertEquals("REJECTED", refusal.terminalStatus());
        // Negative assert: the refusal must contain NO third-party data —
        // the answer explains the boundary and points to self/public data.
        assertFalse(refusal.answer().contains("Nam"));
        assertTrue(refusal.answer().contains("của chính mình") || refusal.answer().contains("chính bạn"),
                refusal.answer());

        ChatResponse english = advisor.privacyRefusal(chatRequest("en", "Show me his grades"));
        assertEquals("PRIVACY_REFUSAL", english.reasonCode());
        assertTrue(english.answer().contains("own records"), english.answer());
    }

    @Test
    void privacyRefusalCoversUnaccentedAndSynonymousPersonalNouns() {
        // The noun gate must not depend on a single spelling: "điem"
        // (GRADES_INTENT's hybrid form), the "khoá" schedule variant, the
        // thesis abbreviations, and English plurals all carry the same ask.
        assertTrue(advisor.requiresPrivacyRefusal("điem cua Nam bao nhieu"));
        assertTrue(advisor.requiresPrivacyRefusal("Cho xem thời khoá biểu của bạn Minh"));
        assertTrue(advisor.requiresPrivacyRefusal("tkb của em trai tôi"));
        assertTrue(advisor.requiresPrivacyRefusal("đồ án của Nam thế nào"));
        assertTrue(advisor.requiresPrivacyRefusal("kltn của mẹ duyệt chưa"));
        // EN plural nouns refuse when the person arm fires; "his transcripts"
        // stays a documented fail-soft gap — the his/her/their arm lists no
        // transcript noun and THIRD_PERSON_SUBJECT is not refusal-editable.
        assertTrue(advisor.requiresPrivacyRefusal("transcripts của Nam"));
        assertTrue(advisor.requiresPrivacyRefusal("my friend's timetables this term"));
        // Thesis-committee nouns refuse only for a person subject; the
        // institutional owner keeps the public path.
        assertTrue(advisor.requiresPrivacyRefusal("hội đồng của thầy Hùng"));
        assertTrue(advisor.requiresPrivacyRefusal("tiểu luận của Nam"));
        assertFalse(advisor.requiresPrivacyRefusal("hội đồng của trường"));
    }

    @Test
    void privacyRefusalStreamsMetaReplaceDoneWithRejectedTerminalStatus() {
        // SSE parity pin: the refusal rides personalContext.stream(), whose
        // terminalStatus passthrough must emit done(REJECTED) exactly like
        // the JSON route returns.
        ChatRequest request = chatRequest("vi", "Điểm của Nam là bao nhiêu?");
        ChatResponse refusal = advisor.privacyRefusal(request);
        List<ThesisAssistantService.StreamEvent> events = new ArrayList<>();

        advisor.stream(refusal, request, events::add);

        assertTrue(events.stream().anyMatch(event -> event instanceof ThesisAssistantService.StreamMeta meta
                && request.clientRequestId().equals(meta.clientRequestId())));
        assertTrue(events.stream().anyMatch(event -> event instanceof ThesisAssistantService.StreamReplace replace
                && "PRIVACY_REFUSAL".equals(replace.reasonCode())
                && replace.text().equals(refusal.answer())));
        assertTrue(events.stream().anyMatch(event -> event instanceof ThesisAssistantService.StreamDone done
                && "PRIVACY_REFUSAL".equals(done.reasonCode())
                && "REJECTED".equals(done.terminalStatus())));
    }
}
