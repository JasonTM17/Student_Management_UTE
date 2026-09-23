package io.campuscore.restfulapi.security;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.campuscore.restfulapi.mail.service.EmailService;
import io.campuscore.restfulapi.thesis.domain.ApprovalStatus;
import io.campuscore.restfulapi.thesis.domain.GroupStatus;
import io.campuscore.restfulapi.thesis.domain.ThesisTopic;
import io.campuscore.restfulapi.thesis.domain.TopicStatus;
import io.campuscore.restfulapi.thesis.repository.ThesisRoundReadPort;
import io.campuscore.restfulapi.thesis.repository.ThesisTopicRepository;
import io.campuscore.restfulapi.thesis.service.ThesisGroupReadService;
import io.campuscore.restfulapi.thesis.service.ThesisTopicService;
import io.campuscore.restfulapi.thesis.web.ThesisGroupReadDtos.GroupResponse;
import io.campuscore.restfulapi.web.ApiErrorWriter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.server.ResponseStatusException;

/**
 * Challenger 1 Empirical Test Suite:
 * Rigorously challenges RBAC permissions across Super Admin, Admin, Lecturer, Student, and Guest roles
 * for Thesis Assistant, Thesis Groups, Thesis Topics, and Mail services,
 * and verifies ApiErrorWriter error payload contract across all security boundary failures.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "persistence"})
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:restful_api_challenger_security;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration-h2"
})
class RbacAndApiErrorSecurityChallengeTest {

    private static final UUID ROUND_ID = UUID.fromString("22222222-2222-2222-2222-222222222101");
    private static final UUID GROUP_ID = UUID.fromString("33333333-3333-3333-3333-333333333301");

    private static final String CHAT_REQUEST_BODY = """
            {"message":"Email: challenger@campuscore.edu",
             "locale":"vi",
             "clientRequestId":"00000000-0000-4000-8000-000000000099"}
            """;

    private static final String VALID_TEST_MAIL = """
            {"to":"superadmin@campuscore.edu",
             "recipientName":"SuperAdmin",
             "introMessage":"RBAC verification probe"}
            """;

    private static final String VALID_NOTICE_MAIL = """
            {"to":"student@campuscore.edu",
             "recipientName":"Student A",
             "category":"Học vụ",
             "title":"Thông báo học kỳ mới",
             "author":"Phòng Đào tạo",
             "content":"Nội dung thông báo học kỳ",
             "highlights":["Lịch thi","Lịch đăng ký"]}
            """;

    private static final String VALID_REGISTRATION_MAIL = """
            {"to":"student@campuscore.edu",
             "studentName":"Student A",
             "studentId":"SV9999",
             "semester":"HK1 2026",
             "totalCredits":3,
             "courses":[{"code":"CS101","name":"Nhap mon lap trinh","credits":3,"lecturer":"GV A","schedule":"Thu 2"}]}
            """;

    private static final String VALID_GRADE_ALERT = """
            {"to":"student@campuscore.edu",
             "studentName":"Student A",
             "studentId":"SV9999",
             "semester":"HK1 2026",
             "gpa4":3.8,
             "gpa10":8.5,
             "academicStanding":"EXCELLENT",
             "conductScore":90,
             "conductRank":"EXCELLENT",
             "grades":[]}
            """;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @MockitoBean
    private EmailService emailService;

    @MockitoBean
    private ThesisGroupReadService groupReadService;

    // --- Helpers for JWT Principals ---

    private RequestPostProcessor superAdminJwt() {
        return jwt().jwt(token -> token
                        .subject("challenger-super-admin")
                        .claim("roles", List.of("SUPER_ADMIN")))
                .authorities(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"));
    }

    private RequestPostProcessor adminJwt() {
        return jwt().jwt(token -> token
                        .subject("challenger-admin")
                        .claim("roles", List.of("ADMIN")))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private RequestPostProcessor lecturerJwt() {
        return jwt().jwt(token -> token
                        .subject("challenger-lecturer")
                        .claim("roles", List.of("LECTURER")))
                .authorities(new SimpleGrantedAuthority("ROLE_LECTURER"));
    }

    private RequestPostProcessor studentJwt() {
        return jwt().jwt(token -> token
                        .subject("challenger-student")
                        .claim("roles", List.of("STUDENT")))
                .authorities(new SimpleGrantedAuthority("ROLE_STUDENT"));
    }

    private RequestPostProcessor guestJwt() {
        return jwt().jwt(token -> token
                        .subject("challenger-guest")
                        .claim("roles", List.of("GUEST")))
                .authorities(new SimpleGrantedAuthority("ROLE_GUEST"));
    }

    // =========================================================================
    // 1. RBAC SUPER ADMIN PERMISSION CHALLENGE
    // =========================================================================
    @Nested
    @DisplayName("1. RBAC Super Admin Endpoints Access")
    class SuperAdminRbacTests {

        @Test
        @DisplayName("Super Admin can access Thesis Assistant chat & conversations endpoints")
        void superAdminCanAccessAssistantEndpoints() throws Exception {
            // Test both /api/v1/thesis/assistant/chat and alias /api/v1/assistant/chat
            mvc.perform(post("/api/v1/thesis/assistant/chat")
                            .with(superAdminJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(CHAT_REQUEST_BODY))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.reasonCode").value("SENSITIVE_EMAIL"));

            mvc.perform(post("/api/v1/assistant/chat")
                            .with(superAdminJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(CHAT_REQUEST_BODY))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.reasonCode").value("SENSITIVE_EMAIL"));

            mvc.perform(get("/api/v1/thesis/assistant/conversations")
                            .with(superAdminJwt()))
                    .andExpect(status().isOk());

            mvc.perform(get("/api/v1/assistant/conversations")
                            .with(superAdminJwt()))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Super Admin can access Thesis Groups list and detail")
        void superAdminCanAccessThesisGroups() throws Exception {
            GroupResponse dummy = new GroupResponse(
                    GROUP_ID, ROUND_ID, "leader-01", null,
                    GroupStatus.DRAFT, ApprovalStatus.PENDING, null,
                    List.of(), List.of());

            when(groupReadService.list(eq(ROUND_ID), any(), any(), any())).thenReturn(List.of(dummy));
            when(groupReadService.get(eq(GROUP_ID), any(), any(), any())).thenReturn(dummy);

            mvc.perform(get("/api/v1/thesis/groups")
                            .param("roundId", ROUND_ID.toString())
                            .with(superAdminJwt()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").value(GROUP_ID.toString()));

            mvc.perform(get("/api/v1/thesis/groups/{id}", GROUP_ID)
                            .with(superAdminJwt()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(GROUP_ID.toString()));
        }

        @Test
        @DisplayName("Super Admin can access all Mail endpoints (/test, /notice, /registration, /grade-alert)")
        void superAdminCanAccessAllMailEndpoints() throws Exception {
            mvc.perform(post("/api/v1/mail/test")
                            .with(superAdminJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_TEST_MAIL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
            verify(emailService).sendTestEmail(any(), any(), any());

            mvc.perform(post("/api/v1/mail/notice")
                            .with(superAdminJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_NOTICE_MAIL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
            verify(emailService).sendAcademicNotice(any());

            mvc.perform(post("/api/v1/mail/registration")
                            .with(superAdminJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_REGISTRATION_MAIL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
            verify(emailService).sendCourseRegistration(any());

            mvc.perform(post("/api/v1/mail/grade-alert")
                            .with(superAdminJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_GRADE_ALERT))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
            verify(emailService).sendGradeAlert(any());
        }
    }

    // =========================================================================
    // 2. RBAC ROLE ISOLATION & RESTRICTION CHALLENGE
    // =========================================================================
    @Nested
    @DisplayName("2. Role Isolation & Boundary Restrictions")
    class RoleIsolationTests {

        @Test
        @DisplayName("Student is strictly forbidden from all Mail send endpoints")
        void studentIsForbiddenFromMailEndpoints() throws Exception {
            mvc.perform(post("/api/v1/mail/test")
                            .with(studentJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_TEST_MAIL))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

            mvc.perform(post("/api/v1/mail/notice")
                            .with(studentJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_NOTICE_MAIL))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

            mvc.perform(post("/api/v1/mail/registration")
                            .with(studentJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_REGISTRATION_MAIL))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

            mvc.perform(post("/api/v1/mail/grade-alert")
                            .with(studentJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_GRADE_ALERT))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

            verify(emailService, never()).sendTestEmail(any(), any(), any());
            verify(emailService, never()).sendAcademicNotice(any());
            verify(emailService, never()).sendCourseRegistration(any());
            verify(emailService, never()).sendGradeAlert(any());
        }

        @Test
        @DisplayName("Lecturer is forbidden from /mail/test but permitted on academic mails")
        void lecturerRestrictedFromTestMailOnly() throws Exception {
            mvc.perform(post("/api/v1/mail/test")
                            .with(lecturerJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_TEST_MAIL))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

            verify(emailService, never()).sendTestEmail(any(), any(), any());

            mvc.perform(post("/api/v1/mail/notice")
                            .with(lecturerJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_NOTICE_MAIL))
                    .andExpect(status().isOk());

            mvc.perform(post("/api/v1/mail/registration")
                            .with(lecturerJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_REGISTRATION_MAIL))
                    .andExpect(status().isOk());

            mvc.perform(post("/api/v1/mail/grade-alert")
                            .with(lecturerJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_GRADE_ALERT))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Disallowed role (GUEST) is forbidden from Assistant, Groups, and Mail")
        void guestRoleForbiddenFromProtectedEndpoints() throws Exception {
            mvc.perform(post("/api/v1/thesis/assistant/chat")
                            .with(guestJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(CHAT_REQUEST_BODY))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

            mvc.perform(get("/api/v1/thesis/groups")
                            .param("roundId", ROUND_ID.toString())
                            .with(guestJwt()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

            mvc.perform(post("/api/v1/mail/test")
                            .with(guestJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_TEST_MAIL))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }
    }

    // =========================================================================
    // 3. THESIS TOPIC SERVICE SUPER ADMIN & ROLE ARCHIVE VISIBILITY
    // =========================================================================
    @Nested
    @DisplayName("3. ThesisTopicService Role-Based Topic Visibility")
    class ThesisTopicVisibilityUnitChallenge {

        private ThesisTopicRepository topicRepo = mock(ThesisTopicRepository.class);
        private ThesisRoundReadPort roundPort = mock(ThesisRoundReadPort.class);
        private ThesisTopicService topicService = new ThesisTopicService(topicRepo, roundPort);

        private ThesisTopic createTopic(String id, String owner, TopicStatus status) {
            ThesisTopic t = new ThesisTopic(ROUND_ID, "IT", "Topic " + id, "Desc", 3, owner);
            try {
                var idField = ThesisTopic.class.getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(t, UUID.fromString(id));

                var statusField = ThesisTopic.class.getDeclaredField("status");
                statusField.setAccessible(true);
                statusField.set(t, status);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return t;
        }

        private Jwt testJwt(String sub, String role) {
            return Jwt.withTokenValue("mock-jwt")
                    .header("alg", "HS256")
                    .subject(sub)
                    .claim("roles", List.of(role))
                    .build();
        }

        @Test
        @DisplayName("Super Admin receives full list of DRAFT topics; Student receives 0; Other Lecturer receives 0")
        void topicListVisibilityChallenge() {
            ThesisTopic draftTopic = createTopic("44444444-4444-4444-4444-444444444401", "lecturer-owner", TopicStatus.DRAFT);
            when(topicRepo.findAllByRoundIdAndStatusOrderByTitle(eq(ROUND_ID), eq(TopicStatus.DRAFT)))
                    .thenReturn(List.of(draftTopic));

            // Super Admin gets all drafts
            List<?> superAdminList = topicService.list(ROUND_ID, TopicStatus.DRAFT, testJwt("super-user", "SUPER_ADMIN"));
            assertEquals(1, superAdminList.size());

            // Admin gets all drafts
            List<?> adminList = topicService.list(ROUND_ID, TopicStatus.DRAFT, testJwt("admin-user", "ADMIN"));
            assertEquals(1, adminList.size());

            // Owner Lecturer gets their own draft
            List<?> ownerList = topicService.list(ROUND_ID, TopicStatus.DRAFT, testJwt("lecturer-owner", "LECTURER"));
            assertEquals(1, ownerList.size());

            // Non-owner Lecturer gets empty
            List<?> otherLecturerList = topicService.list(ROUND_ID, TopicStatus.DRAFT, testJwt("lecturer-other", "LECTURER"));
            assertEquals(0, otherLecturerList.size());

            // Student gets empty
            List<?> studentList = topicService.list(ROUND_ID, TopicStatus.DRAFT, testJwt("student-user", "STUDENT"));
            assertEquals(0, studentList.size());
        }

        @Test
        @DisplayName("Super Admin can get hidden topic; Non-owner non-admin gets 404 NOT_FOUND")
        void topicGetVisibilityChallenge() {
            UUID topicId = UUID.fromString("44444444-4444-4444-4444-444444444402");
            ThesisTopic draftTopic = createTopic(topicId.toString(), "lecturer-owner", TopicStatus.DRAFT);
            when(topicRepo.findById(topicId)).thenReturn(Optional.of(draftTopic));

            // Super Admin can read
            assertEquals(topicId, topicService.get(topicId, testJwt("super-user", "SUPER_ADMIN")).id());

            // Admin can read
            assertEquals(topicId, topicService.get(topicId, testJwt("admin-user", "ADMIN")).id());

            // Owner Lecturer can read
            assertEquals(topicId, topicService.get(topicId, testJwt("lecturer-owner", "LECTURER")).id());

            // Non-owner student throws 404
            ResponseStatusException exStudent = assertThrows(ResponseStatusException.class,
                    () -> topicService.get(topicId, testJwt("student-user", "STUDENT")));
            assertEquals(HttpStatus.NOT_FOUND, exStudent.getStatusCode());

            // Non-owner lecturer throws 404
            ResponseStatusException exLecturer = assertThrows(ResponseStatusException.class,
                    () -> topicService.get(topicId, testJwt("lecturer-other", "LECTURER")));
            assertEquals(HttpStatus.NOT_FOUND, exLecturer.getStatusCode());
        }
    }

    // =========================================================================
    // 4. API ERROR WRITER STATUS PAYLOAD CHALLENGE
    // =========================================================================
    @Nested
    @DisplayName("4. ApiErrorWriter Status Payload Verification")
    class ApiErrorWriterChallenge {

        @Test
        @DisplayName("401 Unauthorized returns integer status: 401 across filters")
        void unauthenticatedRequestReturnsInteger401Status() throws Exception {
            mvc.perform(get("/api/v1/thesis/assistant/conversations"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.status").isNumber())
                    .andExpect(jsonPath("$.status", is(401)))
                    .andExpect(jsonPath("$.code", is("UNAUTHENTICATED")))
                    .andExpect(jsonPath("$.message", is("Authentication is required")))
                    .andExpect(jsonPath("$.path", is("/api/v1/thesis/assistant/conversations")))
                    .andExpect(jsonPath("$.timestamp").isNotEmpty())
                    .andExpect(jsonPath("$.fields").isMap());
        }

        @Test
        @DisplayName("403 Forbidden returns integer status: 403 across security handlers")
        void forbiddenRequestReturnsInteger403Status() throws Exception {
            mvc.perform(post("/api/v1/mail/test")
                            .with(studentJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_TEST_MAIL))
                    .andExpect(status().isForbidden())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.status").isNumber())
                    .andExpect(jsonPath("$.status", is(403)))
                    .andExpect(jsonPath("$.code", is("ACCESS_DENIED")))
                    .andExpect(jsonPath("$.message", is("Access denied")))
                    .andExpect(jsonPath("$.path", is("/api/v1/mail/test")))
                    .andExpect(jsonPath("$.timestamp").isNotEmpty())
                    .andExpect(jsonPath("$.fields").isMap());
        }

        @Test
        @DisplayName("ApiErrorWriter direct invocation verifies exact Integer type across HTTP status matrix")
        void directApiErrorWriterHttpStatusMatrix() throws Exception {
            ApiErrorWriter writer = new ApiErrorWriter(mapper);

            HttpStatus[] testStatuses = {
                    HttpStatus.BAD_REQUEST,
                    HttpStatus.UNAUTHORIZED,
                    HttpStatus.FORBIDDEN,
                    HttpStatus.NOT_FOUND,
                    HttpStatus.CONFLICT,
                    HttpStatus.TOO_MANY_REQUESTS,
                    HttpStatus.INTERNAL_SERVER_ERROR
            };

            for (HttpStatus testStatus : testStatuses) {
                MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/probe");
                request.setAttribute(io.campuscore.restfulapi.security.RequestIdFilter.ATTRIBUTE, "req-test-99");
                MockHttpServletResponse response = new MockHttpServletResponse();

                writer.write(request, response, testStatus, "TEST_ERR", "Test error message");

                assertEquals(testStatus.value(), response.getStatus());
                assertTrue(response.getContentType().startsWith(MediaType.APPLICATION_JSON_VALUE));

                Map<String, Object> payload = mapper.readValue(response.getContentAsString(), new TypeReference<>() {});

                // Assert that status is present and is an Integer matching testStatus.value()
                assertNotNull(payload.get("status"));
                assertInstanceOf(Integer.class, payload.get("status"));
                assertEquals(testStatus.value(), ((Integer) payload.get("status")).intValue());
                assertEquals("TEST_ERR", payload.get("code"));
                assertEquals("Test error message", payload.get("message"));
                assertEquals("/api/v1/probe", payload.get("path"));
                assertEquals("req-test-99", payload.get("requestId"));
                assertNotNull(payload.get("timestamp"));
            }
        }
    }

    // =========================================================================
    // 5. HTTP METHOD MISMATCH
    // =========================================================================
    @Nested
    @DisplayName("5. Method-not-allowed answers 405 with the error envelope, never 500")
    class MethodNotAllowedTests {

        @Test
        @DisplayName("POST on the GET-only attendance summary route returns 405 METHOD_NOT_ALLOWED")
        void postOnGetOnlyRouteIsMethodNotAllowed() throws Exception {
            mvc.perform(post("/api/v1/attendance/my/summary").with(studentJwt()))
                    .andExpect(status().isMethodNotAllowed())
                    .andExpect(jsonPath("$.status").value(405))
                    .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
                    .andExpect(jsonPath("$.requestId").exists());
        }

        @Test
        @DisplayName("DELETE on the POST-only login route returns 405 for an authenticated caller")
        void deleteOnPostOnlyRouteIsMethodNotAllowed() throws Exception {
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .delete("/api/v1/auth/login").with(studentJwt()))
                    .andExpect(status().isMethodNotAllowed())
                    .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
        }
    }
}
