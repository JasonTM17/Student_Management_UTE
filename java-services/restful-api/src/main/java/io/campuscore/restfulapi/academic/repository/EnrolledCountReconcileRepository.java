package io.campuscore.restfulapi.academic.repository;

import io.campuscore.restfulapi.academic.model.DriftedSection;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Statements backing {@code EnrolledCountReconcileJob}: drift detection over
 * {@code academic."Section"."enrolledCount"} and the optional counter repair.
 */
@Repository
@Profile("persistence")
public class EnrolledCountReconcileRepository {

    /** Same active-enrollment definition as RegistrationService seat accounting. */
    static final String ACTIVE_STATUS_PREDICATE = "('ENROLLED', 'PENDING', 'CONFIRMED', 'COMPLETED')";

    private final NamedParameterJdbcTemplate jdbc;

    public EnrolledCountReconcileRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<DriftedSection> findDriftedSections() {
        return jdbc.query(
                "SELECT section.\"id\", section.\"enrolledCount\" AS stored_count, COUNT(enrollment.\"id\") AS true_count "
                        + "FROM academic.\"Section\" section "
                        + "LEFT JOIN academic.\"Enrollment\" enrollment "
                        + "  ON enrollment.\"sectionId\" = section.\"id\" "
                        + " AND enrollment.\"status\" IN " + ACTIVE_STATUS_PREDICATE + " "
                        + "GROUP BY section.\"id\", section.\"enrolledCount\" "
                        + "HAVING section.\"enrolledCount\" <> COUNT(enrollment.\"id\")",
                Map.of(), (rs, row) -> new DriftedSection(
                        rs.getString("id"), rs.getInt("stored_count"), rs.getLong("true_count")));
    }

    public int repairEnrolledCounts(List<String> sectionIds) {
        return jdbc.update(
                "UPDATE academic.\"Section\" section "
                        + "SET \"enrolledCount\" = ("
                        + "  SELECT COUNT(*) FROM academic.\"Enrollment\" enrollment "
                        + "  WHERE enrollment.\"sectionId\" = section.\"id\" "
                        + "    AND enrollment.\"status\" IN " + ACTIVE_STATUS_PREDICATE + "), "
                        + "\"updatedAt\" = CURRENT_TIMESTAMP "
                        + "WHERE section.\"id\" IN (:ids)",
                new MapSqlParameterSource("ids", sectionIds));
    }
}
