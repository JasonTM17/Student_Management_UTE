package io.campuscore.restfulapi.academic.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Query;
import java.time.Instant;
import java.util.Locale;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

/**
 * JPA-native insert-if-absent boundary for registration ledgers. It avoids a
 * caught unique violation poisoning the surrounding transaction while keeping
 * Flyway as the schema authority.
 */
@Repository
@Profile("persistence")
public class RegistrationJpaMutationGateway {
    private final EntityManager entityManager;
    private final EntityManagerFactory entityManagerFactory;

    public RegistrationJpaMutationGateway(EntityManager entityManager,
                                           EntityManagerFactory entityManagerFactory) {
        this.entityManager = entityManager;
        this.entityManagerFactory = entityManagerFactory;
    }

    public boolean insertOperationIfAbsent(String id, String studentId, String idempotencyKey,
                                           String requestHash, String operationType, Instant now) {
        String sql = isPostgres()
                ? "INSERT INTO academic.\"EnrollmentOperation\" (\"id\",\"studentId\",\"idempotencyKey\",\"canonicalRequestHash\",\"operationType\",\"state\",\"createdAt\",\"updatedAt\",\"version\") "
                    + "VALUES (:id,:studentId,:idempotencyKey,:requestHash,:operationType,'PROCESSING',:now,:now,0) "
                    + "ON CONFLICT (\"studentId\",\"idempotencyKey\") DO NOTHING"
                : "MERGE INTO academic.\"EnrollmentOperation\" AS target "
                    + "USING (VALUES (:id,:studentId,:idempotencyKey,:requestHash,:operationType,:now)) "
                    + "AS source(\"id\",\"studentId\",\"idempotencyKey\",\"requestHash\",\"operationType\",\"now\") "
                    + "ON target.\"studentId\"=source.\"studentId\" AND target.\"idempotencyKey\"=source.\"idempotencyKey\" "
                    + "WHEN NOT MATCHED THEN INSERT (\"id\",\"studentId\",\"idempotencyKey\",\"canonicalRequestHash\",\"operationType\",\"state\",\"createdAt\",\"updatedAt\",\"version\") "
                    + "VALUES (source.\"id\",source.\"studentId\",source.\"idempotencyKey\",source.\"requestHash\",source.\"operationType\",'PROCESSING',source.\"now\",source.\"now\",0)";
        return bind(entityManager.createNativeQuery(sql), id, studentId, idempotencyKey,
                requestHash, operationType, now).executeUpdate() == 1;
    }

    public boolean insertSlipIfAbsent(String id, String studentId, String roundId,
                                      String contentHash, String snapshotPayload, Instant generatedAt) {
        String sql = isPostgres()
                ? "INSERT INTO academic.\"RegistrationSlip\" (\"id\",\"studentId\",\"roundId\",\"contentHash\",\"snapshotPayload\",\"generatedAt\") "
                    + "VALUES (:id,:studentId,:roundId,:contentHash,:snapshotPayload,:generatedAt) "
                    + "ON CONFLICT (\"studentId\",\"roundId\") DO NOTHING"
                : "MERGE INTO academic.\"RegistrationSlip\" AS target "
                    + "USING (VALUES (:id,:studentId,:roundId,:contentHash,:snapshotPayload,:generatedAt)) "
                    + "AS source(\"id\",\"studentId\",\"roundId\",\"contentHash\",\"snapshotPayload\",\"generatedAt\") "
                    + "ON target.\"studentId\"=source.\"studentId\" AND target.\"roundId\"=source.\"roundId\" "
                    + "WHEN NOT MATCHED THEN INSERT (\"id\",\"studentId\",\"roundId\",\"contentHash\",\"snapshotPayload\",\"generatedAt\") "
                    + "VALUES (source.\"id\",source.\"studentId\",source.\"roundId\",source.\"contentHash\",source.\"snapshotPayload\",source.\"generatedAt\")";
        Query query = entityManager.createNativeQuery(sql)
                .setParameter("id", id)
                .setParameter("studentId", studentId)
                .setParameter("roundId", roundId)
                .setParameter("contentHash", contentHash)
                .setParameter("snapshotPayload", snapshotPayload)
                .setParameter("generatedAt", generatedAt);
        return query.executeUpdate() == 1;
    }

    private static Query bind(Query query, String id, String studentId, String idempotencyKey,
                              String requestHash, String operationType, Instant now) {
        return query.setParameter("id", id)
                .setParameter("studentId", studentId)
                .setParameter("idempotencyKey", idempotencyKey)
                .setParameter("requestHash", requestHash)
                .setParameter("operationType", operationType)
                .setParameter("now", now);
    }

    private boolean isPostgres() {
        try {
            SessionFactoryImplementor factory = entityManagerFactory.unwrap(SessionFactoryImplementor.class);
            return factory.getJdbcServices().getDialect().getClass().getName()
                    .toLowerCase(Locale.ROOT).contains("postgres");
        } catch (RuntimeException ignored) {
            return entityManagerFactory.getProperties().values().stream()
                    .map(String::valueOf)
                    .anyMatch(value -> value.toLowerCase(Locale.ROOT).contains("postgres"));
        }
    }
}
