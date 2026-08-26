package io.campuscore.restfulapi.academic.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

@Entity
@Table(name = "RegistrationCohortWindow", schema = "academic")
public class RegistrationCohortWindowEntity {
    @Id private String id;
    @Column(name = "roundId", nullable = false) private String roundId;
    @Column(name = "cohortCode", nullable = false) private String cohortCode;
    @Column(name = "priorityRank", nullable = false) private int priorityRank;
    @Column(name = "windowStart", nullable = false) private Instant windowStart;
    @Column(name = "windowEnd", nullable = false) private Instant windowEnd;
    @Version @Column(name = "version") private long version;
    protected RegistrationCohortWindowEntity() { }
    public static RegistrationCohortWindowEntity create(String id, String roundId, String cohortCode,
            int priorityRank, Instant windowStart, Instant windowEnd) {
        RegistrationCohortWindowEntity entity = new RegistrationCohortWindowEntity();
        entity.id = require(id);
        entity.roundId = require(roundId);
        entity.cohortCode = require(cohortCode);
        if (priorityRank < 1) throw new IllegalArgumentException("priority rank must be positive");
        if (windowStart == null || windowEnd == null || !windowStart.isBefore(windowEnd)) {
            throw new IllegalArgumentException("cohort window must have a valid start and end");
        }
        entity.priorityRank = priorityRank;
        entity.windowStart = windowStart;
        entity.windowEnd = windowEnd;
        return entity;
    }
    private static String require(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("cohort window value is required");
        return value.trim();
    }
    public String getId() { return id; }
    public String getRoundId() { return roundId; }
    public String getCohortCode() { return cohortCode; }
    public int getPriorityRank() { return priorityRank; }
    public Instant getWindowStart() { return windowStart; }
    public Instant getWindowEnd() { return windowEnd; }
    public long getVersion() { return version; }
}
