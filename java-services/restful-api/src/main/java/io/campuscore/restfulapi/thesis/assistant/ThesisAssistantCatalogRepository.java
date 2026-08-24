package io.campuscore.restfulapi.thesis.assistant;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Fixed public catalog projection; never reuses personal academic DTOs. */
@Repository
@Profile("persistence")
public class ThesisAssistantCatalogRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public ThesisAssistantCatalogRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<CatalogDocument> search(String locale, List<String> terms, int limit) {
        List<String> usable = terms.stream().filter(term -> term.length() >= 2).distinct().limit(8).toList();
        if (usable.isEmpty()) return List.of();
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("locale", locale).addValue("limit", limit);
        List<String> clauses = new ArrayList<>();
        for (int i = 0; i < usable.size(); i++) {
            String key = "term" + i;
            params.addValue(key, "%" + usable.get(i) + "%");
            clauses.add("(LOWER(public_text) LIKE :" + key + ")");
        }
        // The UNION is intentionally composed from an allowlist of public columns.
        // It contains no user, student, lecturer, roster, enrollment, grade,
        // attendance, profile, notification, or contact fields.
        String sql = "SELECT entity_type, entity_id, title, public_text, updated_at FROM ("
                + "SELECT 'ACADEMIC_YEAR' entity_type, ay.\"id\" entity_id, CAST(ay.\"year\" AS VARCHAR) title, CAST(ay.\"year\" AS VARCHAR) public_text, ay.\"updatedAt\" updated_at FROM academic.\"AcademicYear\" ay WHERE ay.\"isCurrent\"=TRUE "
                + "UNION ALL SELECT 'SEMESTER', s.\"id\", COALESCE(s.\"nameEn\",s.\"name\"), CONCAT_WS(' ',s.\"name\",s.\"nameEn\",s.\"nameVi\",CAST(s.\"registrationStart\" AS VARCHAR),CAST(s.\"registrationEnd\" AS VARCHAR)), s.\"updatedAt\" FROM academic.\"Semester\" s WHERE s.\"status\" <> 'ARCHIVED' "
                + "UNION ALL SELECT 'DEPARTMENT', d.\"id\", COALESCE(d.\"nameEn\",d.\"name\"), CONCAT_WS(' ',d.\"code\",d.\"name\",d.\"nameEn\",d.\"nameVi\"), d.\"updatedAt\" FROM academic.\"Department\" d WHERE d.\"isActive\"=TRUE "
                + "UNION ALL SELECT 'COURSE', c.\"id\", COALESCE(c.\"nameEn\",c.\"name\"), CONCAT_WS(' ',c.\"code\",c.\"name\",c.\"nameEn\",c.\"nameVi\",CAST(c.\"credits\" AS VARCHAR)), c.\"updatedAt\" FROM academic.\"Course\" c WHERE c.\"isActive\"=TRUE "
                + "UNION ALL SELECT 'CURRICULUM', cur.\"id\", COALESCE(cur.\"nameEn\",cur.\"name\"), CONCAT_WS(' ',cur.\"code\",cur.\"name\",cur.\"nameEn\",cur.\"nameVi\"), cur.\"updatedAt\" FROM academic.\"Curriculum\" cur WHERE cur.\"isActive\"=TRUE "
                + "UNION ALL SELECT 'SECTION', sec.\"id\", CONCAT('Section ',sec.\"sectionNumber\"), CONCAT_WS(' ',sec.\"sectionNumber\",sec.\"status\",cl.\"building\",cl.\"roomNumber\",sch.\"dayOfWeek\",sch.\"startTime\",sch.\"endTime\"), sec.\"updatedAt\" FROM academic.\"Section\" sec LEFT JOIN academic.\"Classroom\" cl ON cl.\"id\"=sec.\"classroomId\" LEFT JOIN academic.\"SectionSchedule\" sch ON sch.\"sectionId\"=sec.\"id\" WHERE sec.\"status\" <> 'ARCHIVED'"
                + ") catalog WHERE " + String.join(" OR ", clauses)
                + " ORDER BY entity_type, entity_id LIMIT :limit";
        try {
            return jdbc.query(sql, params, this::map);
        } catch (DataAccessException unavailable) {
            // Catalog coverage is optional on legacy/test schemas. Fail closed and
            // let curated retrieval continue; never surface SQL details to clients.
            return List.of();
        }
    }

    private CatalogDocument map(ResultSet rs, int ignored) throws SQLException {
        return new CatalogDocument(rs.getString("entity_type"), rs.getString("entity_id"), rs.getString("title"),
                rs.getString("public_text"), rs.getTimestamp("updated_at"));
    }

    public record CatalogDocument(String entityType, String entityId, String title, String text, java.sql.Timestamp updatedAt) { }
}
