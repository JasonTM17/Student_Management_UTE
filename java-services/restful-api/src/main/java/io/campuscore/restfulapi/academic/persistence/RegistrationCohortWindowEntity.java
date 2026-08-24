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
    public String getId() { return id; }
    public String getRoundId() { return roundId; }
    public String getCohortCode() { return cohortCode; }
    public int getPriorityRank() { return priorityRank; }
    public Instant getWindowStart() { return windowStart; }
    public Instant getWindowEnd() { return windowEnd; }
    public long getVersion() { return version; }
}
