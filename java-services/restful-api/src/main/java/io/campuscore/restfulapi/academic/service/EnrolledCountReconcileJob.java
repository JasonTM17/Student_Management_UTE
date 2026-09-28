package io.campuscore.restfulapi.academic.service;

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Wave 1.4: reconciles the denormalized {@code academic."Section"."enrolledCount"}
 * counter against the real number of active enrollments.
 *
 * <p>The counter is maintained transactionally by the registration service, but
 * seed migrations, manual data fixes, or a future bug can let it drift. Drift is
 * dangerous in two directions: a too-low counter overbooks a section (seat
 * guard is {@code enrolledCount < capacity}) and a too-high counter hides real
 * seats. Every hour the job compares the counter with
 * {@code COUNT(active enrollment)} — the same "active" definition the
 * registration flow uses ({@code status IN ('ENROLLED','PENDING','CONFIRMED')})
 * — logs a WARN listing the drifted sections, and, when
 * {@code academic.enrolled-count.repair=true}, updates the counters to the true
 * values. Repair defaults to false so an operator sees the drift before any
 * automatic write.</p>
 */
@Component
@Profile("persistence")
@ConditionalOnProperty(prefix = "academic.enrolled-count", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class EnrolledCountReconcileJob {
    private static final Logger log = LoggerFactory.getLogger(EnrolledCountReconcileJob.class);

    /** Same active-enrollment definition as RegistrationService seat accounting. */
    static final String ACTIVE_STATUS_PREDICATE = "('ENROLLED', 'PENDING', 'CONFIRMED')";
    private static final int LOGGED_SECTION_LIMIT = 20;

    private final NamedParameterJdbcTemplate jdbc;
    private final boolean repair;

    @Autowired
    public EnrolledCountReconcileJob(NamedParameterJdbcTemplate jdbc,
            @Value("${academic.enrolled-count.repair:false}") boolean repair) {
        this.jdbc = jdbc;
        this.repair = repair;
    }

    boolean repairEnabled() {
        return repair;
    }

    @Scheduled(fixedDelayString = "${academic.enrolled-count.reconcile-delay-ms:3600000}",
            initialDelayString = "${academic.enrolled-count.reconcile-initial-delay-ms:120000}")
    public void reconcile() {
        try {
            List<DriftedSection> drifted = jdbc.query(
                    "SELECT section.\"id\", section.\"enrolledCount\" AS stored_count, COUNT(enrollment.\"id\") AS true_count "
                            + "FROM academic.\"Section\" section "
                            + "LEFT JOIN academic.\"Enrollment\" enrollment "
                            + "  ON enrollment.\"sectionId\" = section.\"id\" "
                            + " AND enrollment.\"status\" IN " + ACTIVE_STATUS_PREDICATE + " "
                            + "GROUP BY section.\"id\", section.\"enrolledCount\" "
                            + "HAVING section.\"enrolledCount\" <> COUNT(enrollment.\"id\")",
                    Map.of(), (rs, row) -> new DriftedSection(
                            rs.getString("id"), rs.getInt("stored_count"), rs.getLong("true_count")));
            if (drifted.isEmpty()) {
                return;
            }
            String ids = drifted.stream()
                    .map(section -> section.sectionId())
                    .limit(LOGGED_SECTION_LIMIT)
                    .reduce((left, right) -> left + ", " + right)
                    .orElse("");
            long suffix = drifted.size() > LOGGED_SECTION_LIMIT ? drifted.size() - (long) LOGGED_SECTION_LIMIT : 0;
            log.warn("enrolledCount drift on {} section(s) (stored != active enrollments), ids=[{}]{}; repair={}",
                    drifted.size(), ids, suffix > 0 ? " (+" + suffix + " more not logged)" : "",
                    repair ? "auto-repairing" : "disabled");
            if (repair) {
                List<String> driftedIds = drifted.stream().map(DriftedSection::sectionId).toList();
                int repaired = jdbc.update(
                        "UPDATE academic.\"Section\" section "
                                + "SET \"enrolledCount\" = ("
                                + "  SELECT COUNT(*) FROM academic.\"Enrollment\" enrollment "
                                + "  WHERE enrollment.\"sectionId\" = section.\"id\" "
                                + "    AND enrollment.\"status\" IN " + ACTIVE_STATUS_PREDICATE + "), "
                                + "\"updatedAt\" = CURRENT_TIMESTAMP "
                                + "WHERE section.\"id\" IN (:ids)",
                        new MapSqlParameterSource("ids", driftedIds));
                log.warn("enrolledCount repair corrected {} section counter(s)", repaired);
            }
        } catch (Exception failure) {
            log.warn("Failed to reconcile section enrolledCount: {}", failure.getMessage(), failure);
        }
    }

    record DriftedSection(String sectionId, int storedCount, long trueCount) { }
}
