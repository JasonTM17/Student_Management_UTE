package io.campuscore.restfulapi.thesis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.campuscore.restfulapi.testsupport.AccountAuthorityFixture;
import io.campuscore.restfulapi.testsupport.CurrentAccountJwt;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Real HTTP/current-account regressions for proposal approval and protected archival. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "persistence"})
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:restful_api_topic_rubric;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.flyway.enabled=true", "spring.flyway.locations=classpath:db/migration-h2"
})
class ThesisTopicRubricGovernanceTest {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc mvc;
    private UUID roundId;

    @BeforeEach
    void prepare() {
        jdbc.update("DELETE FROM thesis.thesis_group_report");
        jdbc.update("DELETE FROM thesis.thesis_topic_score");
        jdbc.update("DELETE FROM thesis.thesis_council_topic");
        jdbc.update("DELETE FROM thesis.thesis_council_member");
        jdbc.update("DELETE FROM thesis.thesis_council");
        jdbc.update("DELETE FROM thesis.thesis_group_member");
        jdbc.update("DELETE FROM thesis.thesis_group");
        jdbc.update("DELETE FROM thesis.thesis_topic_supervisor");
        jdbc.update("DELETE FROM thesis.thesis_topic");
        jdbc.update("DELETE FROM thesis.thesis_registration_round");
        jdbc.update("DELETE FROM campuscore_auth.\"Lecturer\" WHERE \"id\" LIKE 'rtg-%'");
        jdbc.update("DELETE FROM campuscore_auth.\"Student\" WHERE \"id\" LIKE 'rtg-%'");
        jdbc.update("DELETE FROM campuscore_auth.\"User\" WHERE \"id\" LIKE '%rtg-%'");
        if (jdbc.queryForObject("SELECT COUNT(*) FROM academic.\"Department\" WHERE \"id\"='rtg-other'", Integer.class) == 0) {
            jdbc.update("INSERT INTO academic.\"Department\" (\"id\",\"name\",\"code\",\"facultyId\",\"isActive\") "
                    + "SELECT 'rtg-other','Other rubric department','RTG',\"facultyId\",TRUE FROM academic.\"Department\" WHERE \"id\"='department-demo'");
        }
        roundId = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("INSERT INTO thesis.thesis_registration_round (id,name,thesis_type,lecturer_submit_start,lecturer_submit_end,registration_start,registration_end,status) "
                        + "VALUES (?, 'Rubric topic governance','KLTN',?,?,?,?,'PROPOSAL_OPEN')", roundId,
                Timestamp.from(now.minusSeconds(3600)), Timestamp.from(now.plusSeconds(3600)),
                Timestamp.from(now.plusSeconds(7200)), Timestamp.from(now.plusSeconds(14400)));
    }

    @Test
    void lecturerCanProposeButCannotApproveTheirOwnDraft() throws Exception {
        RequestPostProcessor owner = staff("rtg-owner", "department-demo", "LECTURER");
        mvc.perform(post("/api/v1/thesis/topics").with(owner).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roundId\":\"" + roundId + "\",\"departmentId\":\"department-demo\",\"title\":\"Awaiting review\",\"description\":\"Rubric proposal\",\"maxGroups\":2}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DRAFT"));
        UUID id = jdbc.queryForObject("SELECT id FROM thesis.thesis_topic WHERE title='Awaiting review'", UUID.class);
        mvc.perform(post("/api/v1/thesis/topics/{id}/publish", id).with(owner)).andExpect(status().isForbidden());
        assertThat(topicStatus(id)).isEqualTo("DRAFT");
    }

    @Test
    void standaloneDepartmentHeadReadsOnlyTheirDepartmentAndCanReadSupervisors() throws Exception {
        UUID own = topic("department-demo", "DRAFT");
        UUID other = topic("rtg-other", "DRAFT");
        RequestPostProcessor head = staff("rtg-head", "department-demo", "TRUONG_BO_MON");
        mvc.perform(get("/api/v1/thesis/rounds").with(head)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/thesis/topics").param("roundId", roundId.toString()).param("status", "DRAFT").with(head))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(own.toString()));
        mvc.perform(get("/api/v1/thesis/topics/{id}", own).with(head)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/thesis/topics/{id}", other).with(head)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/thesis/topics/{id}/supervisors", own).with(head))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/v1/thesis/topics/{id}/supervisors", other).with(head)).andExpect(status().isNotFound());
    }

    @Test
    void departmentHeadCanApproveAfterSubmissionWindowButBeforeCatalogCloses() throws Exception {
        UUID id = topic("department-demo", "DRAFT");
        jdbc.update("UPDATE thesis.thesis_registration_round SET lecturer_submit_end=? WHERE id=?",
                Timestamp.from(Instant.now().minusSeconds(30)), roundId);
        mvc.perform(post("/api/v1/thesis/topics/{id}/publish", id)
                        .with(staff("rtg-head", "department-demo", "TRUONG_BO_MON")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PUBLISHED"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM campuscore_audit.\"AdminAudit\" WHERE \"entityId\"=? AND \"action\"='THESIS_TOPIC_APPROVED'",
                Integer.class, id.toString())).isEqualTo(1);
    }

    @Test
    void supervisorReplacementRejectsLecturerWhoseAccountIsInactive() throws Exception {
        UUID id = topic("department-demo", "DRAFT");
        staff("rtg-inactive-supervisor", "department-demo", "LECTURER");
        jdbc.update("UPDATE campuscore_auth.\"User\" SET \"status\"='INACTIVE' WHERE \"id\" = "
                + "(SELECT \"userId\" FROM campuscore_auth.\"Lecturer\" WHERE \"id\"='rtg-inactive-supervisor')");

        mvc.perform(put("/api/v1/thesis/topics/{id}/supervisors", id)
                        .with(staff("rtg-head", "department-demo", "TRUONG_BO_MON"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"supervisorIds\":[\"rtg-inactive-supervisor\"]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LECTURER_ACCOUNT_REQUIRED"));

        assertThat(jdbc.queryForList(
                "SELECT lecturer_id FROM thesis.thesis_topic_supervisor WHERE topic_id=? ORDER BY supervisor_order",
                String.class, id)).containsExactly("rtg-owner");
    }

    @Test
    void topicCannotBePublishedWhenItsSupervisorAccountIsInactive() throws Exception {
        UUID id = topic("department-demo", "DRAFT");
        jdbc.update("UPDATE campuscore_auth.\"User\" SET \"status\"='INACTIVE' WHERE \"id\" = "
                + "(SELECT \"userId\" FROM campuscore_auth.\"Lecturer\" WHERE \"id\"='rtg-owner')");

        mvc.perform(post("/api/v1/thesis/topics/{id}/publish", id)
                        .with(staff("rtg-head", "department-demo", "TRUONG_BO_MON")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TOPIC_SUPERVISOR_REQUIRED"));

        assertThat(topicStatus(id)).isEqualTo("DRAFT");
    }

    @Test
    void crossDepartmentAndInactiveHeadCannotApprove() throws Exception {
        UUID id = topic("rtg-other", "DRAFT");
        RequestPostProcessor head = staff("rtg-head", "department-demo", "TRUONG_BO_MON");
        mvc.perform(post("/api/v1/thesis/topics/{id}/publish", id).with(head)).andExpect(status().isForbidden());
        UUID own = topic("department-demo", "DRAFT");
        jdbc.update("UPDATE campuscore_auth.\"Lecturer\" SET \"isActive\"=FALSE WHERE \"id\"='rtg-head'");
        // A deactivated profile revokes the token itself: the lecturerId claim
        // no longer matches, so the account-state filter rejects before the
        // publish gate is ever reached.
        mvc.perform(post("/api/v1/thesis/topics/{id}/publish", own).with(head)).andExpect(status().isUnauthorized());
        assertThat(topicStatus(id)).isEqualTo("DRAFT");
        assertThat(topicStatus(own)).isEqualTo("DRAFT");
    }

    @Test
    void unapprovedTopicCannotBeSelectedAndApprovedTopicCan() throws Exception {
        UUID id = topic("department-demo", "DRAFT");
        RequestPostProcessor student = ThesisAuthorityFixture.student(jdbc, "rtg-student");
        UUID group = UUID.randomUUID();
        jdbc.update("INSERT INTO thesis.thesis_group (id,round_id,leader_student_id,status,approval_status) VALUES (?,?,'rtg-student','DRAFT','PENDING')", group, roundId);
        jdbc.update("INSERT INTO thesis.thesis_group_member (id,group_id,round_id,student_id,member_order,is_leader) VALUES (?,?,?,'rtg-student',1,TRUE)", UUID.randomUUID(), group, roundId);
        jdbc.update("UPDATE thesis.thesis_registration_round SET status='REGISTRATION_OPEN',lecturer_submit_start=?,lecturer_submit_end=?,registration_start=?,registration_end=? WHERE id=?",
                Timestamp.from(Instant.now().minusSeconds(10800)), Timestamp.from(Instant.now().minusSeconds(7200)),
                Timestamp.from(Instant.now().minusSeconds(3600)), Timestamp.from(Instant.now().plusSeconds(3600)), roundId);
        mvc.perform(post("/api/v1/thesis/groups/{id}/topic", group).with(student).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\":\"" + id + "\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TOPIC_NOT_PUBLISHED"));
        jdbc.update("UPDATE thesis.thesis_registration_round SET status='PROPOSAL_OPEN' WHERE id=?", roundId);
        mvc.perform(post("/api/v1/thesis/topics/{id}/publish", id).with(staff("rtg-head", "department-demo", "TRUONG_BO_MON")))
                .andExpect(status().isOk());
        jdbc.update("UPDATE thesis.thesis_registration_round SET status='REGISTRATION_OPEN' WHERE id=?", roundId);
        mvc.perform(post("/api/v1/thesis/groups/{id}/topic", group).with(student).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\":\"" + id + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.topicId").value(id.toString()));
    }

    @Test
    void ownerArchivesUnusedDraftAndStudentsCannotEnumerateIt() throws Exception {
        UUID id = topic("department-demo", "DRAFT");
        mvc.perform(post("/api/v1/thesis/topics/{id}/archive", id).with(staff("rtg-owner", "department-demo", "LECTURER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ARCHIVED"));
        RequestPostProcessor student = ThesisAuthorityFixture.student(jdbc, "rtg-student");
        mvc.perform(get("/api/v1/thesis/topics/{id}", id).with(student)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/thesis/topics").param("roundId", roundId.toString()).with(student))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM thesis.thesis_topic_supervisor WHERE topic_id=?", Integer.class, id)).isEqualTo(1);
    }

    @Test
    void departmentHeadArchivesUnusedPublishedTopicButCannotArchiveAnotherDepartment() throws Exception {
        UUID own = topic("department-demo", "PUBLISHED");
        UUID other = topic("rtg-other", "PUBLISHED");
        RequestPostProcessor head = staff("rtg-head", "department-demo", "TRUONG_BO_MON");
        mvc.perform(get("/api/v1/thesis/topics").param("roundId", roundId.toString()).with(head))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(post("/api/v1/thesis/topics/{id}/archive", own).with(head)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/thesis/topics/{id}/archive", other).with(head)).andExpect(status().isForbidden());
        assertThat(topicStatus(other)).isEqualTo("PUBLISHED");
    }

    @Test
    void archiveCannotRemoveAnAssignedOrGradedTopicsHistory() throws Exception {
        UUID assigned = topic("department-demo", "PUBLISHED");
        UUID graded = topic("department-demo", "PUBLISHED");
        jdbc.update("INSERT INTO thesis.thesis_group (id,round_id,leader_student_id,topic_id,status,approval_status) VALUES (?,?,'rtg-historical',?,'CANCELLED','REJECTED')", UUID.randomUUID(), roundId, assigned);
        jdbc.update("UPDATE thesis.thesis_topic SET final_score=8.5,result_status='GRADED' WHERE id=?", graded);
        RequestPostProcessor owner = staff("rtg-owner", "department-demo", "LECTURER");
        for (UUID id : new UUID[]{assigned, graded}) {
            mvc.perform(post("/api/v1/thesis/topics/{id}/archive", id).with(owner))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TOPIC_IN_USE"));
            assertThat(topicStatus(id)).isEqualTo("PUBLISHED");
        }
    }

    @Test
    void unrelatedLecturerAndStudentCannotArchive() throws Exception {
        UUID id = topic("department-demo", "DRAFT");
        mvc.perform(post("/api/v1/thesis/topics/{id}/archive", id).with(staff("rtg-other-owner", "department-demo", "LECTURER")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/thesis/topics/{id}/archive", id).with(ThesisAuthorityFixture.student(jdbc, "rtg-student")))
                .andExpect(status().isForbidden());
        assertThat(topicStatus(id)).isEqualTo("DRAFT");
    }

    private UUID topic(String department, String state) {
        staff("rtg-owner", "department-demo", "LECTURER");
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO thesis.thesis_topic (id,round_id,department_id,title,description,max_groups,status,created_by) VALUES (?,?,?,'Rubric topic','Awaiting governance',2,?,'thesis-fixture-lecturer-rtg-owner')", id, roundId, department, state);
        jdbc.update("INSERT INTO thesis.thesis_topic_supervisor (id,topic_id,lecturer_id,supervisor_order) VALUES (?,?,'rtg-owner',1)", UUID.randomUUID(), id);
        return id;
    }

    private RequestPostProcessor staff(String id, String department, String role) {
        ThesisAuthorityFixture.lecturer(jdbc, id);
        jdbc.update("UPDATE campuscore_auth.\"Lecturer\" SET \"departmentId\"=? WHERE \"id\"=?", department, id);
        String subject = jdbc.queryForObject("SELECT \"userId\" FROM campuscore_auth.\"Lecturer\" WHERE \"id\"=?", String.class, id);
        AccountAuthorityFixture.replaceRoles(jdbc, subject, role);
        return CurrentAccountJwt.currentJwt(jdbc, subject);
    }

    private String topicStatus(UUID id) {
        return jdbc.queryForObject("SELECT status FROM thesis.thesis_topic WHERE id=?", String.class, id);
    }
}
