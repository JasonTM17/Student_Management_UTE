package io.campuscore.restfulapi.academic.repository;

import java.util.Collection;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Acquires Student rows before a grade-write path takes its Section lock. */
@Repository
@Profile("persistence")
public class GradeMutationLockRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public GradeMutationLockRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void lockStudentsForEnrollments(String sectionId, Collection<String> enrollmentIds) {
        if (enrollmentIds == null || enrollmentIds.isEmpty()) return;
        jdbc.queryForList(
                "SELECT student.\"id\" FROM academic.\"Student\" student"
                        + " WHERE student.\"id\" IN (SELECT enrollment.\"studentId\""
                        + " FROM academic.\"Enrollment\" enrollment"
                        + " WHERE enrollment.\"sectionId\" = :sectionId AND enrollment.\"id\" IN (:ids))"
                        + " ORDER BY student.\"id\" FOR UPDATE",
                new MapSqlParameterSource().addValue("sectionId", sectionId).addValue("ids", enrollmentIds));
    }
}
