package io.campuscore.restfulapi.academic.repository;

import io.campuscore.restfulapi.academic.web.AdminAnalyticsDtos.DepartmentStat;
import io.campuscore.restfulapi.academic.web.AdminAnalyticsDtos.FacultyRank;
import io.campuscore.restfulapi.academic.web.AdminAnalyticsDtos.GradeDistributionEntry;
import io.campuscore.restfulapi.academic.web.AdminAnalyticsDtos.SemesterTrend;
import io.campuscore.restfulapi.academic.web.AdminAnalyticsDtos.Totals;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Read-only aggregate queries for the administrator analytics overview.
 * Every statement is standard SQL so it runs identically on PostgreSQL
 * (production) and H2 (tests).
 */
@Repository
@Profile("persistence")
public class AdminAnalyticsRepository {

    /**
     * Canonical letter-grade ordering, mirroring the official HCMUTE bands
     * AcademicEnrollmentReadService uses for its 4.0 conversion. Unknown or
     * legacy letters are appended after these.
     */
    static final List<String> GRADE_BAND_ORDER = List.of(
            "A+", "A", "A-", "B+", "B", "B-", "C+", "C", "C-", "D+", "D", "D-", "F");

    private static final RowMapper<DepartmentStat> DEPARTMENT_MAPPER =
            (rs, ignored) -> new DepartmentStat(
                    rs.getString("code"),
                    rs.getString("name"),
                    rs.getLong("students"),
                    rs.getLong("courses"),
                    rs.getLong("sections"));

    private static final RowMapper<SemesterTrend> SEMESTER_MAPPER =
            (rs, ignored) -> {
                long enrollments = rs.getLong("enrollments");
                long graded = rs.getLong("gradedEnrollments");
                double rate = BigDecimal.valueOf(graded * 100L)
                        .divide(BigDecimal.valueOf(Math.max(enrollments, 1L)), 1, RoundingMode.HALF_UP)
                        .doubleValue();
                return new SemesterTrend(
                        rs.getString("name"),
                        enrollments,
                        rate,
                        rs.getLong("activeStudents"));
            };

    private static final RowMapper<FacultyRank> FACULTY_RANK_MAPPER =
            (rs, ignored) -> new FacultyRank(rs.getString("rank"), rs.getLong("count"));

    private static final RowMapper<GradeDistributionEntry> GRADE_MAPPER =
            (rs, ignored) -> new GradeDistributionEntry(
                    rs.getString("grade"), rs.getLong("count"));

    private final NamedParameterJdbcTemplate jdbc;

    public AdminAnalyticsRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Departments with student (via curriculum), course and section counts, largest first. */
    public List<DepartmentStat> departmentStats() {
        return jdbc.query("""
                SELECT d."code" AS code,
                       COALESCE(d."nameVi", d."name") AS name,
                       (SELECT COUNT(*) FROM academic."Student" s
                         JOIN academic."Curriculum" c ON c."id" = s."curriculumId"
                         WHERE c."departmentId" = d."id") AS students,
                       (SELECT COUNT(*) FROM academic."Course" cr
                         WHERE cr."departmentId" = d."id") AS courses,
                       (SELECT COUNT(*) FROM academic."Section" sec
                         JOIN academic."Course" cr2 ON cr2."id" = sec."courseId"
                         WHERE cr2."departmentId" = d."id") AS sections
                FROM academic."Department" d
                ORDER BY students DESC, d."code"
                """, DEPARTMENT_MAPPER);
    }

    /**
     * The most recent semesters with enrollment aggregates. Rows come back
     * newest-first so the service can take the last N and reverse them into a
     * chronological trend.
     */
    public List<SemesterTrend> semesterTrends(int limit) {
        return jdbc.query("""
                SELECT s."name" AS name,
                       COUNT(e."id") AS enrollments,
                       SUM(CASE WHEN e."status" = 'COMPLETED'
                                  OR e."gradeStatus" IN ('PUBLISHED', 'APPEALED')
                                THEN 1 ELSE 0 END) AS gradedEnrollments,
                       COUNT(DISTINCT e."studentId") AS activeStudents
                FROM academic."Semester" s
                JOIN academic."Enrollment" e ON e."semesterId" = s."id"
                    AND e."status" NOT IN ('DROPPED', 'CANCELLED')
                GROUP BY s."id", s."name", s."startDate"
                ORDER BY s."startDate" DESC
                LIMIT :limit
                """,
                new MapSqlParameterSource("limit", Math.max(1, limit)),
                SEMESTER_MAPPER);
    }

    /** Lecturer count per academic rank/title. Rows with a blank title group as UNSPECIFIED. */
    public List<FacultyRank> facultyRanks() {
        return jdbc.query("""
                SELECT COALESCE(NULLIF(TRIM("title"), ''), 'UNSPECIFIED') AS rank,
                       COUNT(*) AS count
                FROM academic."Lecturer"
                GROUP BY COALESCE(NULLIF(TRIM("title"), ''), 'UNSPECIFIED')
                ORDER BY count DESC, rank
                """, FACULTY_RANK_MAPPER);
    }

    /** Graded enrollment counts per letter band, canonical order first then legacy letters. */
    public List<GradeDistributionEntry> gradeDistribution() {
        List<GradeDistributionEntry> rows = jdbc.query("""
                SELECT "letterGrade" AS grade, COUNT(*) AS count
                FROM academic."Enrollment"
                WHERE "letterGrade" IS NOT NULL
                GROUP BY "letterGrade"
                """, GRADE_MAPPER);
        return rows.stream()
                .sorted((left, right) -> {
                    int leftOrder = GRADE_BAND_ORDER.indexOf(left.grade());
                    int rightOrder = GRADE_BAND_ORDER.indexOf(right.grade());
                    if (leftOrder < 0) {
                        leftOrder = GRADE_BAND_ORDER.size();
                    }
                    if (rightOrder < 0) {
                        rightOrder = GRADE_BAND_ORDER.size();
                    }
                    int byBand = Integer.compare(leftOrder, rightOrder);
                    return byBand != 0 ? byBand : left.grade().compareTo(right.grade());
                })
                .toList();
    }

    /** Campus-wide totals for the header tiles. */
    public Totals totals() {
        long students = requireCount("SELECT COUNT(*) FROM academic.\"Student\"");
        long lecturers = requireCount("SELECT COUNT(*) FROM academic.\"Lecturer\"");
        long courses = requireCount("SELECT COUNT(*) FROM academic.\"Course\"");
        long enrollments = requireCount(
                "SELECT COUNT(*) FROM academic.\"Enrollment\" "
                        + "WHERE \"status\" NOT IN ('DROPPED', 'CANCELLED')");
        return new Totals(students, lecturers, courses, enrollments);
    }

    private long requireCount(String sql) {
        Long count = jdbc.getJdbcOperations().queryForObject(sql, Long.class);
        return count == null ? 0L : count;
    }
}
