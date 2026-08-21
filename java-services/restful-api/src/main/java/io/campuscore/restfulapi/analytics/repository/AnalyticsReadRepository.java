package io.campuscore.restfulapi.analytics.repository;

import io.campuscore.restfulapi.analytics.web.AnalyticsReadDtos.AttendanceAnalyticsResponse;
import io.campuscore.restfulapi.analytics.web.AnalyticsReadDtos.EnrollmentBySemesterBucket;
import io.campuscore.restfulapi.analytics.web.AnalyticsReadDtos.InvoiceStatusBucket;
import io.campuscore.restfulapi.analytics.web.AnalyticsReadDtos.LecturerAnalyticsResponse;
import io.campuscore.restfulapi.analytics.web.AnalyticsReadDtos.LecturerSectionAnalyticsBucket;
import io.campuscore.restfulapi.analytics.web.AnalyticsReadDtos.NotificationTypeBucket;
import io.campuscore.restfulapi.analytics.web.AnalyticsReadDtos.PaymentStatusBucket;
import io.campuscore.restfulapi.analytics.web.AnalyticsReadDtos.ProviderFunnelBucket;
import io.campuscore.restfulapi.analytics.web.AnalyticsReadDtos.RecentAttentionNotification;
import io.campuscore.restfulapi.analytics.web.AnalyticsReadDtos.RegistrationPressureSection;
import io.campuscore.restfulapi.analytics.web.AnalyticsReadDtos.RevenueAnalyticsResponse;
import io.campuscore.restfulapi.analytics.web.AnalyticsReadDtos.SectionOccupancyBucket;
import io.campuscore.restfulapi.analytics.web.AnalyticsReadDtos.StudentYearBucket;
import io.campuscore.restfulapi.analytics.web.AnalyticsReadDtos.TopCourseBucket;
import io.campuscore.restfulapi.analytics.web.AnalyticsReadDtos.WaitlistStatusBucket;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Read adapter for the analytics service's Prisma public schema.
 *
 * <p>This candidate intentionally issues SELECT statements only. Observability
 * metrics and event consumers remain owned by the legacy analytics-service
 * until a separate parity/cutover gate proves them.</p>
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

    public long countStudentsByStatus(String status) {
        Long count = jdbc.getJdbcTemplate().queryForObject(
                "SELECT COUNT(*) FROM " + table("\"Student\"") + " WHERE \"status\" = ?",
                Long.class,
                status);
        return Objects.requireNonNullElse(count, 0L);
    }

    public List<StudentYearBucket> studentCountsByYear() {
        return jdbc.query(
                "SELECT \"year\", COUNT(\"id\") AS \"count\""
                        + " FROM " + table("\"Student\"")
                        + " GROUP BY \"year\" ORDER BY \"year\" ASC",
                (resultSet, ignored) -> new StudentYearBucket(
                        resultSet.getInt("year"),
                        resultSet.getLong("count")));
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

    public RevenueAnalyticsResponse revenueAnalytics(String semesterId) {
        MapSqlParameterSource parameters = new MapSqlParameterSource("semesterId", semesterId);
        String invoiceFilter = semesterId == null ? "" : " WHERE \"semesterId\" = :semesterId";
        RevenueInvoiceAggregate invoices = jdbc.queryForObject(
                "SELECT COALESCE(SUM(\"total\"), 0) AS \"totalInvoiced\","
                        + " COUNT(\"id\") AS \"invoiceCount\","
                        + " COALESCE(SUM(CASE WHEN \"status\" = 'PAID' THEN 1 ELSE 0 END), 0) AS \"paidInvoiceCount\","
                        + " COALESCE(SUM(CASE WHEN \"status\" <> 'PAID' THEN 1 ELSE 0 END), 0) AS \"pendingInvoiceCount\""
                        + " FROM " + table("\"Invoice\"")
                        + invoiceFilter,
                parameters,
                (resultSet, ignored) -> new RevenueInvoiceAggregate(
                        amount(resultSet, "totalInvoiced"),
                        resultSet.getLong("invoiceCount"),
                        resultSet.getLong("paidInvoiceCount"),
                        resultSet.getLong("pendingInvoiceCount")));
        BigDecimal paid = completedPaymentTotal(semesterId);
        BigDecimal invoiced = invoices == null ? BigDecimal.ZERO : invoices.totalInvoiced();
        return new RevenueAnalyticsResponse(
                invoiced,
                paid,
                invoiced.subtract(paid),
                invoices == null ? 0L : invoices.invoiceCount(),
                invoices == null ? 0L : invoices.paidInvoiceCount(),
                invoices == null ? 0L : invoices.pendingInvoiceCount());
    }

    public AttendanceAnalyticsResponse attendanceAnalytics(String semesterId) {
        MapSqlParameterSource parameters = new MapSqlParameterSource("semesterId", semesterId);
        String sectionJoin = semesterId == null
                ? ""
                : " JOIN " + table("\"Section\"") + " s ON s.\"id\" = a.\"sectionId\""
                        + " AND s.\"semesterId\" = :semesterId";
        AttendanceAggregate aggregate = jdbc.queryForObject(
                "SELECT COUNT(a.\"id\") AS \"totalRecords\","
                        + " COALESCE(SUM(CASE WHEN a.\"status\" = 'PRESENT' THEN 1 ELSE 0 END), 0) AS \"present\","
                        + " COALESCE(SUM(CASE WHEN a.\"status\" = 'ABSENT' THEN 1 ELSE 0 END), 0) AS \"absent\","
                        + " COALESCE(SUM(CASE WHEN a.\"status\" = 'LATE' THEN 1 ELSE 0 END), 0) AS \"late\","
                        + " COALESCE(SUM(CASE WHEN a.\"status\" = 'EXCUSED' THEN 1 ELSE 0 END), 0) AS \"excused\""
                        + " FROM " + table("\"Attendance\"") + " a"
                        + sectionJoin,
                parameters,
                (resultSet, ignored) -> new AttendanceAggregate(
                        resultSet.getLong("totalRecords"),
                        resultSet.getLong("present"),
                        resultSet.getLong("absent"),
                        resultSet.getLong("late"),
                        resultSet.getLong("excused")));
        if (aggregate == null) {
            return new AttendanceAnalyticsResponse(0, 0, 0, 0, 0, 0);
        }
        int attendanceRate = aggregate.totalRecords() > 0
                ? Math.toIntExact(Math.round(((aggregate.present() + aggregate.late()) * 100.0d)
                        / aggregate.totalRecords()))
                : 0;
        return new AttendanceAnalyticsResponse(
                aggregate.totalRecords(),
                aggregate.present(),
                aggregate.absent(),
                aggregate.late(),
                aggregate.excused(),
                attendanceRate);
    }

    public LecturerAnalyticsResponse lecturerAnalytics(String lecturerId) {
        MapSqlParameterSource parameters = new MapSqlParameterSource("lecturerId", lecturerId);
        Long totalSections = jdbc.queryForObject(
                "SELECT COUNT(\"id\") FROM " + table("\"Section\"")
                        + " WHERE \"lecturerId\" = :lecturerId",
                parameters,
                Long.class);
        Long totalStudents = jdbc.queryForObject(
                "SELECT COUNT(e.\"id\")"
                        + " FROM " + table("\"Enrollment\"") + " e"
                        + " JOIN " + table("\"Section\"") + " s ON s.\"id\" = e.\"sectionId\""
                        + " WHERE s.\"lecturerId\" = :lecturerId"
                        + " AND e.\"status\" IN ('CONFIRMED', 'PENDING')",
                parameters,
                Long.class);
        Long sectionsWithGrades = jdbc.queryForObject(
                "SELECT COUNT(e.\"id\")"
                        + " FROM " + table("\"Enrollment\"") + " e"
                        + " JOIN " + table("\"Section\"") + " s ON s.\"id\" = e.\"sectionId\""
                        + " WHERE s.\"lecturerId\" = :lecturerId"
                        + " AND e.\"gradeStatus\" = 'PUBLISHED'",
                parameters,
                Long.class);
        return new LecturerAnalyticsResponse(
                Objects.requireNonNullElse(totalSections, 0L),
                Objects.requireNonNullElse(totalStudents, 0L),
                Objects.requireNonNullElse(sectionsWithGrades, 0L));
    }

    public List<LecturerSectionAnalyticsBucket> lecturerSectionAnalytics(String lecturerId) {
        return jdbc.query(
                "SELECT s.\"id\" AS \"sectionId\", s.\"sectionNumber\", c.\"code\" AS \"courseCode\","
                        + " c.\"name\" AS \"courseName\", c.\"nameEn\" AS \"courseNameEn\","
                        + " c.\"nameVi\" AS \"courseNameVi\", sm.\"name\" AS \"semesterName\","
                        + " sm.\"nameEn\" AS \"semesterNameEn\", sm.\"nameVi\" AS \"semesterNameVi\","
                        + " s.\"capacity\", s.\"enrolledCount\", COUNT(e.\"id\") AS \"countedEnrollments\""
                        + " FROM " + table("\"Section\"") + " s"
                        + " JOIN " + table("\"Course\"") + " c ON c.\"id\" = s.\"courseId\""
                        + " JOIN " + table("\"Semester\"") + " sm ON sm.\"id\" = s.\"semesterId\""
                        + " LEFT JOIN " + table("\"Enrollment\"") + " e ON e.\"sectionId\" = s.\"id\""
                        + " AND e.\"status\" IN ('CONFIRMED', 'PENDING')"
                        + " WHERE s.\"lecturerId\" = :lecturerId"
                        + " GROUP BY s.\"id\", s.\"sectionNumber\", c.\"code\", c.\"name\", c.\"nameEn\", c.\"nameVi\","
                        + " sm.\"name\", sm.\"nameEn\", sm.\"nameVi\", s.\"capacity\", s.\"enrolledCount\""
                        + " ORDER BY s.\"sectionNumber\" ASC, s.\"id\" ASC",
                new MapSqlParameterSource("lecturerId", lecturerId),
                (resultSet, ignored) -> lecturerSectionAnalyticsBucket(resultSet));
    }

    public List<EnrollmentBySemesterBucket> enrollmentsBySemester() {
        return jdbc.query(
                "SELECT s.\"id\" AS \"semesterId\", s.\"name\" AS \"semesterName\","
                        + " s.\"nameEn\" AS \"semesterNameEn\", s.\"nameVi\" AS \"semesterNameVi\","
                        + " ay.\"year\" AS \"academicYear\", COUNT(e.\"id\") AS \"enrollmentCount\""
                        + " FROM " + table("\"Semester\"") + " s"
                        + " JOIN " + table("\"AcademicYear\"") + " ay ON ay.\"id\" = s.\"academicYearId\""
                        + " JOIN " + table("\"Enrollment\"") + " e ON e.\"semesterId\" = s.\"id\""
                        + " WHERE e.\"status\" IN ('CONFIRMED', 'COMPLETED')"
                        + " GROUP BY s.\"id\", s.\"name\", s.\"nameEn\", s.\"nameVi\", ay.\"year\", s.\"startDate\""
                        + " ORDER BY s.\"startDate\" DESC"
                        + " LIMIT 10",
                (resultSet, ignored) -> new EnrollmentBySemesterBucket(
                        resultSet.getString("semesterId"),
                        resultSet.getString("semesterName"),
                        resultSet.getString("semesterNameEn"),
                        resultSet.getString("semesterNameVi"),
                        resultSet.getInt("academicYear"),
                        resultSet.getLong("enrollmentCount")));
    }

    public List<EnrollmentTrendActivity> enrollmentTrendActivities(Instant oldestBucket) {
        return jdbc.query(
                "SELECT \"enrolledAt\", \"status\""
                        + " FROM " + table("\"Enrollment\"")
                        + " WHERE \"enrolledAt\" >= :oldestBucket",
                Map.of("oldestBucket", LocalDateTime.ofInstant(oldestBucket, ZoneOffset.UTC)),
                (resultSet, ignored) -> new EnrollmentTrendActivity(
                        instant(resultSet, "enrolledAt"),
                        resultSet.getString("status")));
    }

    public List<SectionOccupancyBucket> sectionOccupancy() {
        return jdbc.query(
                "SELECT s.\"id\" AS \"sectionId\", s.\"sectionNumber\", c.\"code\" AS \"courseCode\","
                        + " c.\"name\" AS \"courseName\", c.\"nameEn\" AS \"courseNameEn\","
                        + " c.\"nameVi\" AS \"courseNameVi\", sm.\"name\" AS \"semesterName\","
                        + " sm.\"nameEn\" AS \"semesterNameEn\", sm.\"nameVi\" AS \"semesterNameVi\","
                        + " s.\"capacity\", s.\"enrolledCount\", COUNT(e.\"id\") AS \"countedEnrollments\""
                        + " FROM " + table("\"Section\"") + " s"
                        + " JOIN " + table("\"Course\"") + " c ON c.\"id\" = s.\"courseId\""
                        + " JOIN " + table("\"Semester\"") + " sm ON sm.\"id\" = s.\"semesterId\""
                        + " LEFT JOIN " + table("\"Enrollment\"") + " e ON e.\"sectionId\" = s.\"id\""
                        + " AND e.\"status\" IN ('CONFIRMED', 'PENDING')"
                        + " GROUP BY s.\"id\", s.\"sectionNumber\", c.\"code\", c.\"name\", c.\"nameEn\", c.\"nameVi\","
                        + " sm.\"name\", sm.\"nameEn\", sm.\"nameVi\", s.\"capacity\", s.\"enrolledCount\""
                        + " ORDER BY s.\"enrolledCount\" DESC"
                        + " LIMIT 20",
                (resultSet, ignored) -> sectionOccupancyBucket(resultSet));
    }

    public List<RegistrationPressureSection> registrationPressureSections() {
        return jdbc.query(
                "SELECT s.\"id\" AS \"sectionId\", s.\"sectionNumber\", c.\"code\" AS \"courseCode\","
                        + " c.\"name\" AS \"courseName\", c.\"nameEn\" AS \"courseNameEn\","
                        + " c.\"nameVi\" AS \"courseNameVi\", sm.\"name\" AS \"semesterName\","
                        + " sm.\"nameEn\" AS \"semesterNameEn\", sm.\"nameVi\" AS \"semesterNameVi\","
                        + " s.\"capacity\", s.\"enrolledCount\","
                        + " COUNT(DISTINCT e.\"id\") AS \"countedEnrollments\","
                        + " COUNT(DISTINCT w.\"id\") AS \"waitlistCount\""
                        + " FROM " + table("\"Section\"") + " s"
                        + " JOIN " + table("\"Course\"") + " c ON c.\"id\" = s.\"courseId\""
                        + " JOIN " + table("\"Semester\"") + " sm ON sm.\"id\" = s.\"semesterId\""
                        + " LEFT JOIN " + table("\"Enrollment\"") + " e ON e.\"sectionId\" = s.\"id\""
                        + " AND e.\"status\" IN ('CONFIRMED', 'PENDING')"
                        + " LEFT JOIN " + table("\"Waitlist\"") + " w ON w.\"sectionId\" = s.\"id\""
                        + " AND w.\"status\" = 'ACTIVE'"
                        + " GROUP BY s.\"id\", s.\"sectionNumber\", c.\"code\", c.\"name\", c.\"nameEn\", c.\"nameVi\","
                        + " sm.\"name\", sm.\"nameEn\", sm.\"nameVi\", s.\"capacity\", s.\"enrolledCount\"",
                (resultSet, ignored) -> registrationPressureSection(resultSet));
    }

    public List<WaitlistStatusBucket> waitlistStatusBuckets() {
        return jdbc.query(
                "SELECT \"status\", COUNT(\"id\") AS \"count\""
                        + " FROM " + table("\"Waitlist\"")
                        + " GROUP BY \"status\" ORDER BY \"status\" ASC",
                (resultSet, ignored) -> new WaitlistStatusBucket(
                        resultSet.getString("status"),
                        resultSet.getLong("count")));
    }

    public long countActiveRegistrationSemesters() {
        Long count = jdbc.getJdbcTemplate().queryForObject(
                "SELECT COUNT(*) FROM " + table("\"Semester\"")
                        + " WHERE \"status\" IN ('REGISTRATION_OPEN', 'ADD_DROP_OPEN')",
                Long.class);
        return Objects.requireNonNullElse(count, 0L);
    }

    public List<TopCourseBucket> topCourses(int limit) {
        return jdbc.query(
                "SELECT c.\"id\" AS \"courseId\", c.\"code\" AS \"courseCode\","
                        + " c.\"name\" AS \"courseName\", c.\"nameEn\" AS \"courseNameEn\","
                        + " c.\"nameVi\" AS \"courseNameVi\", c.\"credits\","
                        + " COUNT(DISTINCT s.\"id\") AS \"sectionCount\", COUNT(e.\"id\") AS \"totalEnrollments\""
                        + " FROM " + table("\"Course\"") + " c"
                        + " LEFT JOIN " + table("\"Section\"") + " s ON s.\"courseId\" = c.\"id\""
                        + " LEFT JOIN " + table("\"Enrollment\"") + " e ON e.\"sectionId\" = s.\"id\""
                        + " AND e.\"status\" IN ('CONFIRMED', 'PENDING')"
                        + " GROUP BY c.\"id\", c.\"code\", c.\"name\", c.\"nameEn\", c.\"nameVi\", c.\"credits\""
                        + " ORDER BY \"totalEnrollments\" DESC"
                        + " LIMIT :limit",
                Map.of("limit", limit),
                (resultSet, ignored) -> new TopCourseBucket(
                        resultSet.getString("courseId"),
                        resultSet.getString("courseCode"),
                        resultSet.getString("courseName"),
                        resultSet.getString("courseNameEn"),
                        resultSet.getString("courseNameVi"),
                        resultSet.getInt("credits"),
                        resultSet.getLong("sectionCount"),
                        resultSet.getLong("totalEnrollments")));
    }

    public Map<String, Long> completedLetterGradeCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        jdbc.query(
                "SELECT \"letterGrade\", COUNT(\"id\") AS \"count\""
                        + " FROM " + table("\"Enrollment\"")
                        + " WHERE \"status\" = 'COMPLETED' AND \"letterGrade\" IS NOT NULL"
                        + " GROUP BY \"letterGrade\"",
                (resultSet, ignored) -> new GradeCount(
                        resultSet.getString("letterGrade"),
                        resultSet.getLong("count")))
                .forEach(bucket -> counts.put(bucket.grade(), bucket.count()));
        return counts;
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

    private static SectionOccupancyBucket sectionOccupancyBucket(ResultSet resultSet) throws SQLException {
        int capacity = resultSet.getInt("capacity");
        long countedEnrollments = resultSet.getLong("countedEnrollments");
        long storedEnrollments = resultSet.getLong("enrolledCount");
        long enrolledCount = countedEnrollments > 0 ? countedEnrollments : storedEnrollments;
        int occupancyRate = capacity > 0
                ? Math.toIntExact(Math.round((enrolledCount * 100.0d) / capacity))
                : 0;
        return new SectionOccupancyBucket(
                resultSet.getString("sectionId"),
                resultSet.getString("sectionNumber"),
                resultSet.getString("courseCode"),
                resultSet.getString("courseName"),
                resultSet.getString("courseNameEn"),
                resultSet.getString("courseNameVi"),
                resultSet.getString("semesterName"),
                resultSet.getString("semesterNameEn"),
                resultSet.getString("semesterNameVi"),
                capacity,
                enrolledCount,
                occupancyRate);
    }

    private static LecturerSectionAnalyticsBucket lecturerSectionAnalyticsBucket(ResultSet resultSet)
            throws SQLException {
        int capacity = resultSet.getInt("capacity");
        long countedEnrollments = resultSet.getLong("countedEnrollments");
        long storedEnrollments = resultSet.getLong("enrolledCount");
        long enrolledCount = countedEnrollments > 0 ? countedEnrollments : storedEnrollments;
        int occupancyRate = capacity > 0
                ? Math.toIntExact(Math.round((enrolledCount * 100.0d) / capacity))
                : 0;
        return new LecturerSectionAnalyticsBucket(
                resultSet.getString("sectionId"),
                resultSet.getString("sectionNumber"),
                resultSet.getString("courseCode"),
                resultSet.getString("courseName"),
                resultSet.getString("courseNameEn"),
                resultSet.getString("courseNameVi"),
                resultSet.getString("semesterName"),
                resultSet.getString("semesterNameEn"),
                resultSet.getString("semesterNameVi"),
                capacity,
                enrolledCount,
                occupancyRate);
    }

    private static RegistrationPressureSection registrationPressureSection(ResultSet resultSet) throws SQLException {
        int capacity = resultSet.getInt("capacity");
        long countedEnrollments = resultSet.getLong("countedEnrollments");
        long storedEnrollments = resultSet.getLong("enrolledCount");
        long enrolledCount = countedEnrollments > 0 ? countedEnrollments : storedEnrollments;
        int occupancyRate = capacity > 0
                ? Math.toIntExact(Math.round((enrolledCount * 100.0d) / capacity))
                : 0;
        return new RegistrationPressureSection(
                resultSet.getString("sectionId"),
                resultSet.getString("sectionNumber"),
                resultSet.getString("courseCode"),
                resultSet.getString("courseName"),
                resultSet.getString("courseNameEn"),
                resultSet.getString("courseNameVi"),
                resultSet.getString("semesterName"),
                resultSet.getString("semesterNameEn"),
                resultSet.getString("semesterNameVi"),
                capacity,
                enrolledCount,
                resultSet.getLong("waitlistCount"),
                occupancyRate);
    }

    private static java.time.Instant instant(ResultSet resultSet, String column) throws SQLException {
        LocalDateTime value = resultSet.getObject(column, LocalDateTime.class);
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    private BigDecimal completedPaymentTotal(String semesterId) {
        if (semesterId == null) {
            BigDecimal value = jdbc.getJdbcTemplate().queryForObject(
                    "SELECT COALESCE(SUM(\"amount\"), 0) FROM " + table("\"Payment\"")
                            + " WHERE \"status\" = 'COMPLETED'",
                    BigDecimal.class);
            return value == null ? BigDecimal.ZERO : value;
        }
        BigDecimal value = jdbc.queryForObject(
                "SELECT COALESCE(SUM(p.\"amount\"), 0)"
                        + " FROM " + table("\"Payment\"") + " p"
                        + " JOIN " + table("\"Invoice\"") + " i ON i.\"id\" = p.\"invoiceId\""
                        + " WHERE p.\"status\" = 'COMPLETED' AND i.\"semesterId\" = :semesterId",
                new MapSqlParameterSource("semesterId", semesterId),
                BigDecimal.class);
        return value == null ? BigDecimal.ZERO : value;
    }

    private record GradeCount(String grade, long count) {
    }

    private record RevenueInvoiceAggregate(
            BigDecimal totalInvoiced,
            long invoiceCount,
            long paidInvoiceCount,
            long pendingInvoiceCount) {
    }

    private record AttendanceAggregate(
            long totalRecords,
            long present,
            long absent,
            long late,
            long excused) {
    }

    public record EnrollmentTrendActivity(Instant enrolledAt, String status) {
    }
}
