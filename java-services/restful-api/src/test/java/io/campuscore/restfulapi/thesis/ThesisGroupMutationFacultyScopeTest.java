package io.campuscore.restfulapi.thesis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.campuscore.restfulapi.thesis.service.ThesisMutationService;
import io.campuscore.restfulapi.thesis.dto.ThesisMutationDtos.ProgressRequest;
import io.campuscore.restfulapi.thesis.model.GroupStatus;
import io.campuscore.restfulapi.exception.DomainException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

/** A governance read denial must not be bypassed through a group mutation. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "persistence"})
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:thesis_group_write_scope;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.flyway.enabled=true", "spring.flyway.locations=classpath:db/migration-h2"
})
@Transactional
class ThesisGroupMutationFacultyScopeTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ThesisMutationService mutations;
    private UUID round;
    private String deanUser;

    @BeforeEach
    void fixtures() {
        jdbc.update("INSERT INTO academic.\"Faculty\" (\"id\",\"name\",\"isActive\") VALUES ('gw-foreign-faculty','Foreign',TRUE)");
        jdbc.update("INSERT INTO academic.\"Department\" (\"id\",\"name\",\"code\",\"facultyId\",\"isActive\") VALUES ('gw-foreign-dept','Foreign','GW-F','gw-foreign-faculty',TRUE),('gw-sibling-dept','Sibling','GW-S','faculty-demo',TRUE)");
        jdbc.update("INSERT INTO academic.\"Curriculum\" (\"id\",\"name\",\"departmentId\") VALUES ('gw-own-curr','Own','department-demo'),('gw-foreign-curr','Foreign','gw-foreign-dept')");
        round=UUID.randomUUID();
        Instant now=Instant.now();
        jdbc.update("INSERT INTO thesis.thesis_registration_round (id,name,thesis_type,lecturer_submit_start,lecturer_submit_end,registration_start,registration_end,status) VALUES (?,'Write scope','KLTN',?,?,?,?,'REGISTRATION_OPEN')",round,Timestamp.from(now.minusSeconds(14400)),Timestamp.from(now.minusSeconds(10800)),Timestamp.from(now.minusSeconds(3600)),Timestamp.from(now.plusSeconds(3600)));
        ThesisAuthorityFixture.lecturer(jdbc,"gw-dean");
        deanUser="thesis-fixture-lecturer-gw-dean";
        ThesisAuthorityFixture.role(jdbc,deanUser,"TRUONG_KHOA");
    }

    @ParameterizedTest
    @ValueSource(strings={"add","remove","topic","progress","approve","reject"})
    void deanCannotMutateOrDiscloseAnUnassignedForeignGroup(String operation) throws Exception {
        UUID foreign=group(topic("gw-foreign-dept"),"gw-foreign-curr");
        String member=jdbc.queryForObject("SELECT student_id FROM thesis.thesis_group_member WHERE group_id=? AND member_order=2",String.class,foreign);
        UUID ownTopic=topic("department-demo");
        MockHttpServletRequestBuilder request=switch(operation) {
            case "add" -> post("/api/v1/thesis/groups/{id}/members",foreign).content("{\"displayName\":\"Denied external\"}");
            case "remove" -> delete("/api/v1/thesis/groups/{id}/members/{studentId}",foreign,member);
            case "topic" -> post("/api/v1/thesis/groups/{id}/topic",foreign).content("{\"topicId\":\""+ownTopic+"\"}");
            case "progress" -> patch("/api/v1/thesis/groups/{id}/progress",foreign).content("{\"status\":\"CANCELLED\"}");
            case "approve" -> post("/api/v1/thesis/groups/{id}/approve",foreign);
            default -> post("/api/v1/thesis/groups/{id}/reject",foreign).content("{\"reason\":\"Denied review\"}");
        };
        mvc.perform(request.contentType(MediaType.APPLICATION_JSON).with(dean())).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT status FROM thesis.thesis_group WHERE id=?",String.class,foreign)).isEqualTo("SUBMITTED");
        assertThat(jdbc.queryForObject("SELECT approval_status FROM thesis.thesis_group WHERE id=?",String.class,foreign)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM thesis.thesis_group_member WHERE group_id=?",Integer.class,foreign)).isEqualTo(3);
    }

    @ParameterizedTest
    @ValueSource(strings={"department-demo","gw-sibling-dept","topicless"})
    void deanRetainsOwnFacultyRosterProgressAndReview(String department) throws Exception {
        UUID selected=topic(department.equals("topicless")?"department-demo":department);
        UUID id=group(department.equals("topicless")?null:selected,"gw-own-curr");
        mvc.perform(post("/api/v1/thesis/groups/{id}/members",id).with(dean()).contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"Own external\"}")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/thesis/groups/{id}/topic",id).with(dean()).contentType(MediaType.APPLICATION_JSON).content("{\"topicId\":\""+selected+"\"}")).andExpect(status().isOk());
        mvc.perform(patch("/api/v1/thesis/groups/{id}/progress",id).with(dean()).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"SUBMITTED\"}")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/thesis/groups/{id}/approve",id).with(dean()).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Faculty approval\"}")).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT approval_status FROM thesis.thesis_group WHERE id=?",String.class,id)).isEqualTo("APPROVED");
    }

    @Test
    void deanCannotRetargetOwnGroupToAnUnassignedForeignTopic() throws Exception {
        UUID own=topic("department-demo"),foreign=topic("gw-foreign-dept"),id=group(own,"gw-own-curr");
        mvc.perform(post("/api/v1/thesis/groups/{id}/topic",id).with(dean()).contentType(MediaType.APPLICATION_JSON).content("{\"topicId\":\""+foreign+"\"}")).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT topic_id FROM thesis.thesis_group WHERE id=?",UUID.class,id)).isEqualTo(own);
    }

    @Test
    void assignedDeanSupervisorKeepsRosterTopicAndReviewButNotTheLifecycle() throws Exception {
        UUID selected=topic("gw-foreign-dept"),id=group(selected,"gw-foreign-curr");
        jdbc.update("INSERT INTO thesis.thesis_topic_supervisor (id,topic_id,lecturer_id,supervisor_order) VALUES (?,?,'gw-dean',1)",UUID.randomUUID(),selected);
        mvc.perform(post("/api/v1/thesis/groups/{id}/members",id).with(dean()).contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"Assigned external\"}")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/thesis/groups/{id}/topic",id).with(dean()).contentType(MediaType.APPLICATION_JSON).content("{\"topicId\":\""+selected+"\"}")).andExpect(status().isOk());
        mvc.perform(patch("/api/v1/thesis/groups/{id}/progress",id).with(dean()).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"SUBMITTED\"}")).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/thesis/groups/{id}/approve",id).with(dean())).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT approval_status FROM thesis.thesis_group WHERE id=?",String.class,id)).isEqualTo("APPROVED");
    }

    @ParameterizedTest
    @ValueSource(strings={"ADMIN","SUPER_ADMIN"})
    void globalAdministratorsKeepForeignGroupAuthority(String role) throws Exception {
        UUID id=group(topic("gw-foreign-dept"),"gw-foreign-curr");
        mvc.perform(patch("/api/v1/thesis/groups/{id}/progress",id).with(ThesisAuthorityFixture.role(jdbc,"gw-global-"+role,role)).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"SUBMITTED\"}")).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings={"missing","inactive","borrowed"})
    void deanNeedsActiveSubjectBoundFacultyEvenForAnOwnGroup(String state) {
        UUID id=group(topic("department-demo"),"gw-own-curr");
        if(state.equals("inactive"))jdbc.update("UPDATE campuscore_auth.\"Lecturer\" SET \"isActive\"=FALSE WHERE \"id\"='gw-dean'");
        Jwt actor=Jwt.withTokenValue("fixture").header("alg","none").subject(state.equals("borrowed")?"gw-unrelated":deanUser).claim("roles",List.of("TRUONG_KHOA","LECTURER")).claim("lecturerId",state.equals("missing")?"gw-missing":"gw-dean").build();
        assertThatThrownBy(()->mutations.updateProgress(id,new ProgressRequest(GroupStatus.SUBMITTED),actor)).isInstanceOfSatisfying(DomainException.class,error->assertThat(error.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    private RequestPostProcessor dean() {return ThesisAuthorityFixture.role(jdbc,deanUser,"TRUONG_KHOA");}
    private UUID topic(String department) {
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO thesis.thesis_topic (id,round_id,department_id,title,description,max_groups,status,created_by) VALUES (?,?,?,'Write scope topic','Fixture',5,'PUBLISHED',?)",id,round,department,deanUser);
        return id;
    }
    private UUID group(UUID topic,String curriculum) {
        UUID id=UUID.randomUUID();String leader="gw-student-"+UUID.randomUUID();
        jdbc.update("INSERT INTO thesis.thesis_group (id,round_id,leader_student_id,topic_id,status,approval_status) VALUES (?,?,?,?,'SUBMITTED','PENDING')",id,round,leader,topic);
        for(int i=1;i<=3;i++) {
            String profile=i==1?leader:"gw-student-"+UUID.randomUUID();
            ThesisAuthorityFixture.student(jdbc,profile);
            jdbc.update("UPDATE campuscore_auth.\"Student\" SET \"curriculumId\"=? WHERE \"id\"=?",curriculum,profile);
            jdbc.update("INSERT INTO thesis.thesis_group_member (id,group_id,round_id,student_id,member_order,is_leader) VALUES (?,?,?,?,?,?)",UUID.randomUUID(),id,round,profile,i,i==1);
        }
        return id;
    }
}
