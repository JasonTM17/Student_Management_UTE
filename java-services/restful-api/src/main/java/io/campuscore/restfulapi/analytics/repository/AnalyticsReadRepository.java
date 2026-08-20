package io.campuscore.restfulapi.analytics.repository;

import io.campuscore.restfulapi.analytics.web.AnalyticsReadDtos.InvoiceStatusBucket;
import io.campuscore.restfulapi.analytics.web.AnalyticsReadDtos.NotificationTypeBucket;
import io.campuscore.restfulapi.analytics.web.AnalyticsReadDtos.PaymentStatusBucket;
import io.campuscore.restfulapi.analytics.web.AnalyticsReadDtos.ProviderFunnelBucket;
import io.campuscore.restfulapi.analytics.web.AnalyticsReadDtos.RecentAttentionNotification;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Read adapter for the analytics service's Prisma public schema.
 *
 * <p>This candidate intentionally issues SELECT statements only. Trends,
 * attendance, lecturer analytics, cockpit composition, observability metrics
 * and event consumers remain owned by the legacy analytics-service until a
 * separate parity/cutover gate proves them.</p>
 */
@Repository
@Profile("persistence")
@ConditionalOnProperty(prefix = "migration.analytics-read", name = "enabled", havingValue = "true")
public class AnalyticsReadRepository {

    private static final String SCHEMA = "\"public\"";
    private final NamedParameterJdbcTemplate jdbc;

    public AnalyticsReadRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long countStudents() {
        return count("\"Student\"");
    }

    public long countLecturers() {
        return count("\"Lecturer\"");
    }

    public long countCourses() {
        return count("\"Course\"");
    }

    public long countSections() {
        return count("\"Section\"");
    }

    public long countEnrollments() {
        return count("\"Enrollment\"");
    }

    public long countDepartments() {
        return count("\"Department\"");
    }

    public long countFaculties() {
        return count("\"Faculty\"");
    }

    public long countAcademicYears() {
        return count("\"AcademicYear\"");
    }

    public long countSemesters() {
        return count("\"Semester\"");
    }

    public long countClassrooms() {
        return count("\"Classroom\"");
    }

    public List<InvoiceStatusBucket> invoiceStatusBuckets() {
        return jdbc.query(
                "SELECT \"status\", COUNT(\"id\") AS \"count\", COALESCE(SUM(\"total\"), 0) AS \"amount\""
                        + " FROM " + table("\"Invoice\"")
                        + " GROUP BY \"status\" ORDER BY \"status\" ASC",
                (resultSet, ignored) -> new InvoiceStatusBucket(
                        resultSet.getString("status"),
                        resultSet.getLong("count"),
                        amount(resultSet, "amount")));
    }

    public List<PaymentStatusBucket> paymentStatusBuckets() {
        return jdbc.query(
                "SELECT \"status\", COUNT(\"id\") AS \"count\", COALESCE(SUM(\"amount\"), 0) AS \"amount\""
                        + " FROM " + table("\"Payment\"")
                        + " GROUP BY \"status\" ORDER BY \"status\" ASC",
                (resultSet, ignored) -> new PaymentStatusBucket(
                        resultSet.getString("status"),
                        resultSet.getLong("count"),
                        amount(resultSet, "amount")));
    }

    public List<ProviderFunnelBucket> providerFunnelBuckets() {
        return jdbc.query(
                "SELECT \"method\", \"status\", COUNT(\"id\") AS \"count\","
                        + " COALESCE(SUM(\"amount\"), 0) AS \"amount\""
                        + " FROM " + table("\"Payment\"")
                        + " GROUP BY \"method\", \"status\" ORDER BY \"method\" ASC, \"status\" ASC",
                (resultSet, ignored) -> new ProviderFunnelBucket(
                        resultSet.getString("method"),
                        resultSet.getString("status"),
                        resultSet.getLong("count"),
                        amount(resultSet, "amount")));
    }

    public long countNotifications() {
        return count("\"Notification\"");
    }

    public long countUnreadNotifications() {
        Long count = jdbc.getJdbcTemplate().queryForObject(
                "SELECT COUNT(*) FROM " + table("\"Notification\"") + " WHERE \"isRead\" = FALSE",
                Long.class);
        return Objects.requireNonNullElse(count, 0L);
    }

    public List<NotificationTypeBucket> notificationTypeBuckets() {
        return jdbc.query(
                "SELECT \"type\", COUNT(\"id\") AS \"count\""
                        + " FROM " + table("\"Notification\"")
                        + " GROUP BY \"type\" ORDER BY \"type\" ASC",
                (resultSet, ignored) -> new NotificationTypeBucket(
                        resultSet.getString("type"),
                        resultSet.getLong("count")));
    }

    public List<RecentAttentionNotification> recentAttentionNotifications() {
        return jdbc.query(
                "SELECT \"id\", \"title\", \"message\", \"type\", \"createdAt\""
                        + " FROM " + table("\"Notification\"")
                        + " WHERE \"type\" IN ('ERROR', 'WARNING')"
                        + " ORDER BY \"createdAt\" DESC"
                        + " LIMIT 5",
                (resultSet, ignored) -> new RecentAttentionNotification(
                        resultSet.getString("id"),
                        resultSet.getString("title"),
                        resultSet.getString("message"),
                        resultSet.getString("type"),
                        instant(resultSet, "createdAt")));
    }

    private long count(String tableName) {
        Long count = jdbc.getJdbcTemplate().queryForObject(
                "SELECT COUNT(*) FROM " + table(tableName),
                Long.class);
        return Objects.requireNonNullElse(count, 0L);
    }

    private static String table(String tableName) {
        return SCHEMA + "." + tableName;
    }

    private static BigDecimal amount(ResultSet resultSet, String column) throws SQLException {
        BigDecimal value = resultSet.getBigDecimal(column);
        return value == null ? BigDecimal.ZERO : value;
    }

    private static java.time.Instant instant(ResultSet resultSet, String column) throws SQLException {
        LocalDateTime value = resultSet.getObject(column, LocalDateTime.class);
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}
