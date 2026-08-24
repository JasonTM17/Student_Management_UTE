package io.campuscore.restfulapi.academic.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

/** JPA write boundary for a configurable enrollment round. */
@Entity
@Table(name = "RegistrationRound", schema = "academic")
public class RegistrationRoundEntity {
    @Id
    private String id;
    @Column(name = "semesterId", nullable = false)
    private String semesterId;
    @Column(name = "status", nullable = false, length = 40)
    private String status;
    @Column(name = "registrationStart", nullable = false)
    private Instant registrationStart;
    @Column(name = "registrationEnd", nullable = false)
    private Instant registrationEnd;
    @Column(name = "addDropStart", nullable = false)
    private Instant addDropStart;
    @Column(name = "addDropEnd", nullable = false)
    private Instant addDropEnd;
    @Column(name = "maxCredits", nullable = false)
    private int maxCredits;
    @Column(name = "institutionTimeZone", nullable = false)
    private String institutionTimeZone;
    @Version
    @Column(name = "version")
    private long version;

    protected RegistrationRoundEntity() { }
    public static RegistrationRoundEntity create(String id, String semesterId, String status,
            Instant registrationStart, Instant registrationEnd, Instant addDropStart, Instant addDropEnd,
            int maxCredits, String institutionTimeZone) {
        RegistrationRoundEntity e = new RegistrationRoundEntity();
        e.id = require(id); e.semesterId = require(semesterId); e.status = require(status);
        e.registrationStart = registrationStart; e.registrationEnd = registrationEnd;
        e.addDropStart = addDropStart; e.addDropEnd = addDropEnd; e.maxCredits = maxCredits;
        e.institutionTimeZone = require(institutionTimeZone); return e;
    }
    public void update(String status, Instant registrationStart, Instant registrationEnd,
            Instant addDropStart, Instant addDropEnd, int maxCredits, String institutionTimeZone) {
        this.status = require(status); this.registrationStart = registrationStart; this.registrationEnd = registrationEnd;
        this.addDropStart = addDropStart; this.addDropEnd = addDropEnd; this.maxCredits = maxCredits;
        this.institutionTimeZone = require(institutionTimeZone);
    }
    private static String require(String value) { if (value == null || value.isBlank()) throw new IllegalArgumentException("registration round value is required"); return value; }
    public String getId() { return id; }
    public String getSemesterId() { return semesterId; }
    public String getStatus() { return status; }
    public Instant getRegistrationStart() { return registrationStart; }
    public Instant getRegistrationEnd() { return registrationEnd; }
    public Instant getAddDropStart() { return addDropStart; }
    public Instant getAddDropEnd() { return addDropEnd; }
    public int getMaxCredits() { return maxCredits; }
    public String getInstitutionTimeZone() { return institutionTimeZone; }
    public long getVersion() { return version; }
}
