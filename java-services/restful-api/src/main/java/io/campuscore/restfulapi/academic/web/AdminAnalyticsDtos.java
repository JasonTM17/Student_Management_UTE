package io.campuscore.restfulapi.academic.web;

import java.util.List;

/**
 * Response records for the administrator analytics overview. Field names are
 * the frontend contract for the admin dashboard and must not change.
 */
public final class AdminAnalyticsDtos {

    private AdminAnalyticsDtos() {
    }

    /**
     * One department row. Students are counted through the curriculum a
     * student belongs to (curriculum.departmentId) — the only department link
     * the student table carries.
     */
    public record DepartmentStat(
            String code,
            String name,
            long students,
            long courses,
            long sections) {
    }

    /**
     * One semester row, chronologically ascending.
     *
     * <p>Definitions (kept identical between SQL and docs):
     * <ul>
     * <li>{@code count} — enrollment rows for the semester excluding
     * DROPPED/CANCELLED (the same rule the announcement feed uses to decide
     * whether a seat is still held).</li>
     * <li>{@code completionRate} — percentage of those enrollments whose grade
     * is final: status COMPLETED or gradeStatus PUBLISHED/APPEALED (the same
     * "graded" definition AcademicEnrollmentReadRepository uses for student
     * grade visibility), rounded half-up to one decimal.</li>
     * <li>{@code activeStudents} — distinct students holding at least one of
     * those enrollments.</li>
     * </ul>
     */
    public record SemesterTrend(
            String semester,
            long count,
            double completionRate,
            long activeStudents) {
    }

    /** Lecturer count grouped by the academic rank/title column. */
    public record FacultyRank(String rank, long count) {
    }

    /** Graded enrollment count per letter grade band (A+, A, ..., F). */
    public record GradeDistributionEntry(String grade, long count) {
    }

    /** Campus-wide totals for the dashboard header tiles. */
    public record Totals(long students, long lecturers, long courses, long enrollments) {
    }

    public record AnalyticsOverview(
            List<DepartmentStat> departments,
            List<SemesterTrend> semesterTrends,
            List<FacultyRank> facultyRanks,
            List<GradeDistributionEntry> gradeDistribution,
            Totals totals) {
    }
}
