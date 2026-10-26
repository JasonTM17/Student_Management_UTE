package io.campuscore.restfulapi.mail.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.campuscore.restfulapi.exception.handler.GlobalExceptionHandler;
import io.campuscore.restfulapi.mail.dto.MailDtos.AcademicNoticeRequest;
import io.campuscore.restfulapi.mail.dto.MailDtos.CourseItem;
import io.campuscore.restfulapi.mail.dto.MailDtos.CourseRegistrationRequest;
import io.campuscore.restfulapi.mail.dto.MailDtos.GradeAlertRequest;
import io.campuscore.restfulapi.mail.dto.MailDtos.GradeItem;
import io.campuscore.restfulapi.mail.dto.MailDtos.TestEmailRequest;
import io.campuscore.restfulapi.mail.service.EmailService;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class MailRequestValidationTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();
    private final ObjectMapper mapper = new ObjectMapper();
    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new MailController(mock(EmailService.class), mock(ObjectProvider.class)))
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void rejectsOutOfRangeValuesAndOversizedCollectionsAndText() {
        List<GradeItem> excessiveGrades = IntStream.range(0, 500)
                .mapToObj(index -> new GradeItem("SE001", "Course " + index, 3, 9.5, "A"))
                .toList();
        GradeAlertRequest excessiveGradeAlert = new GradeAlertRequest(
                "student@campuscore.edu", "Student", "24110001", "HK1", 40.0, 9.5,
                "GOOD", 101, "GOOD", excessiveGrades);

        assertViolations(excessiveGradeAlert, "gpa4", "conductScore", "grades");

        AcademicNoticeRequest oversizedNotice = new AcademicNoticeRequest(
                "student@campuscore.edu", null, null, "Notice", null, "x".repeat(100_000),
                null, null, null);
        assertViolations(oversizedNotice, "content");

        List<CourseItem> excessiveCourses = IntStream.range(0, 31)
                .mapToObj(index -> new CourseItem("SE001", "Course " + index, 3, null, null))
                .toList();
        CourseRegistrationRequest excessiveRegistration = new CourseRegistrationRequest(
                "student@campuscore.edu", "Student", "24110001", null, null, excessiveCourses, 61);

        assertViolations(excessiveRegistration, "courses", "totalCredits");
    }

    @Test
    void cascadesValidationIntoCourseAndGradeRows() {
        CourseRegistrationRequest invalidCourse = new CourseRegistrationRequest(
                "student@campuscore.edu", "Student", "24110001", null, null,
                List.of(new CourseItem(
                        "C".repeat(256), "N".repeat(256), 31, "L".repeat(256), "S".repeat(256))), 31);
        GradeAlertRequest invalidGrade = new GradeAlertRequest(
                "student@campuscore.edu", "Student", "24110001", null, 3.5, 8.5,
                null, 80, null,
                List.of(new GradeItem("C".repeat(256), "N".repeat(256), 31, 10.1, "L".repeat(101))));

        assertViolations(invalidCourse, "courses[0].code", "courses[0].name", "courses[0].credits",
                "courses[0].lecturer", "courses[0].schedule");
        assertViolations(invalidGrade, "grades[0].courseCode", "grades[0].courseName", "grades[0].credits",
                "grades[0].score10", "grades[0].scoreLetter");
    }

    @Test
    void enforcesRemainingStringAndHighlightCaps() {
        TestEmailRequest oversizedTestMail = new TestEmailRequest(
                "a".repeat(256), "n".repeat(256), "i".repeat(20_001));
        AcademicNoticeRequest oversizedNotice = new AcademicNoticeRequest(
                "student@campuscore.edu", "n".repeat(256), "c".repeat(256), "t".repeat(301),
                "a".repeat(256), "x".repeat(20_001),
                IntStream.range(0, 21).mapToObj(index -> "h".repeat(501)).toList(),
                "u".repeat(2_049), "t".repeat(501));
        CourseRegistrationRequest oversizedRegistration = new CourseRegistrationRequest(
                "student@campuscore.edu", "n".repeat(256), "i".repeat(256),
                "d".repeat(256), "s".repeat(256), List.of(new CourseItem("SE001", "Course", 3, null, null)), 3);
        GradeAlertRequest oversizedGradeAlert = new GradeAlertRequest(
                "student@campuscore.edu", "n".repeat(256), "i".repeat(256), "s".repeat(256),
                3.5, 8.5, "a".repeat(101), 80, "c".repeat(101), List.of());

        assertViolations(oversizedTestMail, "to", "recipientName", "introMessage");
        assertViolations(oversizedNotice, "recipientName", "category", "title", "author", "content",
                "actionUrl", "actionText");
        assertViolationStartingWith(oversizedNotice, "highlights");
        assertViolations(oversizedRegistration, "studentName", "studentId", "department", "semester");
        assertViolations(oversizedGradeAlert, "studentName", "studentId", "semester",
                "academicStanding", "conductRank");
    }

    @Test
    void acceptsTypicalUiPayloadsWithDecimalGradesAndOptionalValues() {
        AcademicNoticeRequest notice = new AcademicNoticeRequest(
                "student@campuscore.edu", "Student", "academic", "Registration deadline",
                "Training Office", "Please review the registration schedule.",
                List.of("Registration closes Friday", "Check your timetable"), null, "");
        CourseRegistrationRequest registration = new CourseRegistrationRequest(
                "student@campuscore.edu", "Student", "24110001", "Computer Science", "HK1",
                List.of(new CourseItem("SE001", "Programming", 3, null, ""),
                        new CourseItem("SE002", "Databases", 3, "Lecturer", "Mon 08:00")),
                6);
        GradeAlertRequest gradeAlert = new GradeAlertRequest(
                "student@campuscore.edu", "Student", "24110001", "HK1", 3.74, 8.91,
                "GOOD", 88, null,
                List.of(new GradeItem("SE001", "Programming", 3, 9.25, "A"),
                        new GradeItem("SE002", "Databases", 3, 8.5, "")));

        assertTrue(VALIDATOR.validate(notice).isEmpty());
        assertTrue(VALIDATOR.validate(registration).isEmpty());
        assertTrue(VALIDATOR.validate(gradeAlert).isEmpty());
    }

    @Test
    void controllerReturnsStandardValidationErrorEnvelope() throws Exception {
        GradeAlertRequest invalidRequest = new GradeAlertRequest(
                "student@campuscore.edu", "Student", "24110001", "HK1", 40.0, 9.5,
                null, 88, null, List.of(new GradeItem("SE001", "Programming", 3, 9.25, "A")));

        MvcResult result = mvc.perform(post("/api/v1/mail/grade-alert")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(invalidRequest)))
                .andReturn();

        assertEquals(400, result.getResponse().getStatus());
        Map<String, Object> envelope = mapper.readValue(result.getResponse().getContentAsString(),
                new TypeReference<>() { });
        assertEquals("VALIDATION_ERROR", envelope.get("code"));
        assertEquals("Request validation failed", envelope.get("message"));
        assertTrue(envelope.get("fields") instanceof Map<?, ?> fields && fields.containsKey("gpa4"));
        assertTrue(envelope.get("fieldErrors") instanceof List<?> fieldErrors
                && fieldErrors.stream().anyMatch(error -> error instanceof Map<?, ?> fieldError
                        && "gpa4".equals(fieldError.get("field"))));
    }

    private void assertViolations(Object request, String... expectedPaths) {
        Set<String> paths = VALIDATOR.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
        assertFalse(paths.isEmpty(), "expected a bounded mail request to be rejected");
        for (String expectedPath : expectedPaths) {
            assertTrue(paths.contains(expectedPath), () -> "missing violation for " + expectedPath + ": " + paths);
        }
    }

    private void assertViolationStartingWith(Object request, String expectedPrefix) {
        Set<String> paths = VALIDATOR.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
        assertTrue(paths.stream().anyMatch(path -> path.startsWith(expectedPrefix)),
                () -> "missing violation starting with " + expectedPrefix + ": " + paths);
    }
}
