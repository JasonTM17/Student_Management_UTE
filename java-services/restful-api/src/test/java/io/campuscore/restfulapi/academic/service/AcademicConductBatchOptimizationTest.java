package io.campuscore.restfulapi.academic.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.campuscore.restfulapi.academic.web.AcademicConductDtos.StudentConductSummaryDto;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class AcademicConductBatchOptimizationTest {

    private JdbcTemplate jdbc;
    private AcademicConductService service;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:conduct_batch_test_" + System.nanoTime() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        jdbc = new JdbcTemplate(dataSource);
        NamedParameterJdbcTemplate namedJdbc = new NamedParameterJdbcTemplate(dataSource);

        jdbc.execute("CREATE SCHEMA IF NOT EXISTS \"academic\"");
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS \"campuscore_auth\"");

        jdbc.execute("""
            CREATE TABLE "campuscore_auth"."User" (
                "id" VARCHAR(64) PRIMARY KEY,
                "firstName" VARCHAR(64),
                "lastName" VARCHAR(64)
            )
        """);

        jdbc.execute("""
            CREATE TABLE "academic"."Semester" (
                "id" VARCHAR(64) PRIMARY KEY,
                "name" VARCHAR(128),
                "startDate" DATE
            )
        """);

        jdbc.execute("""
            CREATE TABLE "academic"."Student" (
                "id" VARCHAR(64) PRIMARY KEY,
                "userId" VARCHAR(64),
                "studentId" VARCHAR(32)
            )
        """);

        jdbc.execute("""
            CREATE TABLE academic.conduct_semester_score (
                id VARCHAR(64) PRIMARY KEY,
                student_id VARCHAR(64),
                semester_id VARCHAR(64),
                criteria1_score NUMERIC(5,2),
                criteria2_score NUMERIC(5,2),
                criteria3_score NUMERIC(5,2),
                criteria4_score NUMERIC(5,2),
                criteria5_score NUMERIC(5,2),
                total_score NUMERIC(5,2),
                classification VARCHAR(64),
                classification_vi VARCHAR(64),
                status VARCHAR(32),
                evaluator_name VARCHAR(128)
            )
        """);

        jdbc.execute("""
            CREATE TABLE academic.conduct_activity (
                id VARCHAR(64) PRIMARY KEY,
                student_id VARCHAR(64),
                semester_id VARCHAR(64),
                title VARCHAR(256),
                category VARCHAR(64),
                points NUMERIC(5,2),
                activity_date DATE,
                organizer VARCHAR(128),
                certificate_url VARCHAR(256)
            )
        """);

        service = new AcademicConductService(namedJdbc);
    }

    @Test
    void studentSummaryBatchFetchesAndGroupsActivitiesAcrossMultipleSemesters() {
        // Setup semesters
        jdbc.update("INSERT INTO \"academic\".\"Semester\" (\"id\", \"name\", \"startDate\") VALUES (?, ?, ?)", "sem-1", "Học kỳ 1 2025-2026", Date.valueOf("2025-09-01"));
        jdbc.update("INSERT INTO \"academic\".\"Semester\" (\"id\", \"name\", \"startDate\") VALUES (?, ?, ?)", "sem-2", "Học kỳ 2 2025-2026", Date.valueOf("2026-02-01"));

        // Setup user and student
        jdbc.update("INSERT INTO \"campuscore_auth\".\"User\" (\"id\", \"firstName\", \"lastName\") VALUES (?, ?, ?)",
                "usr-01", "Van A", "Nguyen");
        jdbc.update("INSERT INTO \"academic\".\"Student\" (\"id\", \"userId\", \"studentId\") VALUES (?, ?, ?)",
                "stu-01", "usr-01", "22110001");

        // Setup 2 semesters of conduct scores
        jdbc.update("""
            INSERT INTO academic.conduct_semester_score
            (id, student_id, semester_id, criteria1_score, criteria2_score, criteria3_score, criteria4_score, criteria5_score, total_score, classification, classification_vi, status, evaluator_name)
            VALUES (?, ?, ?, 18, 22, 18, 18, 9, 85, 'GOOD', 'Tốt', 'CONFIRMED', 'GVCN')
        """, "score-1", "stu-01", "sem-1");

        jdbc.update("""
            INSERT INTO academic.conduct_semester_score
            (id, student_id, semester_id, criteria1_score, criteria2_score, criteria3_score, criteria4_score, criteria5_score, total_score, classification, classification_vi, status, evaluator_name)
            VALUES (?, ?, ?, 20, 24, 19, 20, 10, 93, 'EXCELLENT', 'Xuất sắc', 'CONFIRMED', 'GVCN')
        """, "score-2", "stu-01", "sem-2");

        // Setup 3 activities: 2 in sem-1, 1 in sem-2
        jdbc.update("""
            INSERT INTO academic.conduct_activity
            (id, student_id, semester_id, title, category, points, activity_date, organizer, certificate_url)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, "act-1", "stu-01", "sem-1", "Hội thảo AI", "ACADEMIC", new BigDecimal("5.0"), Date.valueOf(LocalDate.of(2025, 9, 15)), "Khoa CNTT", "http://cert/1");

        jdbc.update("""
            INSERT INTO academic.conduct_activity
            (id, student_id, semester_id, title, category, points, activity_date, organizer, certificate_url)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, "act-2", "stu-01", "sem-1", "Hiến máu nhân đạo", "VOLUNTEER", new BigDecimal("10.0"), Date.valueOf(LocalDate.of(2025, 10, 20)), "Đoàn trường", "http://cert/2");

        jdbc.update("""
            INSERT INTO academic.conduct_activity
            (id, student_id, semester_id, title, category, points, activity_date, organizer, certificate_url)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, "act-3", "stu-01", "sem-2", "Mùa hè xanh", "VOLUNTEER", new BigDecimal("15.0"), Date.valueOf(LocalDate.of(2026, 3, 10)), "Hội Sinh viên", "http://cert/3");

        StudentConductSummaryDto summary = service.studentSummary("stu-01");

        assertNotNull(summary);
        assertEquals("stu-01", summary.studentId());
        assertEquals("22110001", summary.studentCode());
        assertEquals("Nguyen Van A", summary.fullName());
        assertEquals(2, summary.history().size());

        // Semester 1 has 2 activities
        var sem1Score = summary.history().stream().filter(s -> s.semesterId().equals("sem-1")).findFirst().orElseThrow();
        assertEquals(2, sem1Score.activities().size());

        // Semester 2 has 1 activity
        var sem2Score = summary.history().stream().filter(s -> s.semesterId().equals("sem-2")).findFirst().orElseThrow();
        assertEquals(1, sem2Score.activities().size());

        // Cumulative average (85 + 93) / 2 = 89.0 -> "Tốt"
        assertEquals(new BigDecimal("89.0"), summary.cumulativeAverageScore());
        assertEquals("Tốt", summary.cumulativeClassificationVi());
    }
}
