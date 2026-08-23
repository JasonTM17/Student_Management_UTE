package io.campuscore.restfulapi.academic.service;

import io.campuscore.restfulapi.web.DomainException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("persistence")
public class LecturerMutationService {

    private static final String LECTURER = "\"academic\".\"Lecturer\"";

    private final NamedParameterJdbcTemplate jdbc;

    public LecturerMutationService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public Map<String, Object> create(Map<String, Object> input) {
        MapSqlParameterSource parameters = parameters(input);
        java.util.List<String> existing = jdbc.query(
                "SELECT \"id\" FROM " + LECTURER + " WHERE \"userId\" = :userId",
                parameters,
                (resultSet, ignored) -> resultSet.getString("id"));
        String id = existing.isEmpty() ? UUID.randomUUID().toString() : existing.get(0);
        parameters.addValue("id", id);
        try {
            if (existing.isEmpty()) {
                jdbc.update(
                        "INSERT INTO " + LECTURER
                                + " (\"id\", \"userId\", \"departmentId\", \"employeeId\", \"title\","
                                + " \"specialization\", \"office\", \"phone\", \"isActive\")"
                                + " VALUES (:id, :userId, :departmentId, :employeeId, :title,"
                                + " :specialization, :office, :phone, TRUE)",
                        parameters);
            } else {
                jdbc.update(
                        "UPDATE " + LECTURER
                                + " SET \"departmentId\" = :departmentId, \"employeeId\" = :employeeId,"
                                + " \"title\" = :title, \"specialization\" = :specialization,"
                                + " \"office\" = :office, \"phone\" = :phone, \"isActive\" = TRUE,"
                                + " \"updatedAt\" = CURRENT_TIMESTAMP WHERE \"id\" = :id",
                        parameters);
            }
        } catch (DataIntegrityViolationException exception) {
            throw problem(HttpStatus.CONFLICT, "LECTURER_CONFLICT", "User or employee ID already has a lecturer profile");
        }
        return find(id);
    }

    @Transactional
    public Map<String, Object> update(String id, Map<String, Object> input) {
        Map<String, String> columns = new LinkedHashMap<>();
        columns.put("departmentId", "\"departmentId\"");
        columns.put("title", "\"title\"");
        columns.put("specialization", "\"specialization\"");
        columns.put("office", "\"office\"");
        columns.put("phone", "\"phone\"");
        columns.put("isActive", "\"isActive\"");
        MapSqlParameterSource parameters = new MapSqlParameterSource("id", id);
        StringBuilder sql = new StringBuilder("UPDATE ").append(LECTURER).append(" SET ");
        boolean first = true;
        for (Map.Entry<String, String> column : columns.entrySet()) {
            if (!input.containsKey(column.getKey())) continue;
            if (!first) sql.append(", ");
            first = false;
            sql.append(column.getValue()).append(" = :").append(column.getKey());
            parameters.addValue(column.getKey(), input.get(column.getKey()));
        }
        if (first) throw new IllegalArgumentException("At least one editable lecturer field is required");
        sql.append(", \"updatedAt\" = CURRENT_TIMESTAMP WHERE \"id\" = :id");
        if (jdbc.update(sql.toString(), parameters) != 1) {
            throw problem(HttpStatus.NOT_FOUND, "LECTURER_NOT_FOUND", "Lecturer not found");
        }
        return find(id);
    }

    @Transactional
    public void delete(String id) {
        try {
            if (jdbc.update("DELETE FROM " + LECTURER + " WHERE \"id\" = :id", new MapSqlParameterSource("id", id)) != 1) {
                throw problem(HttpStatus.NOT_FOUND, "LECTURER_NOT_FOUND", "Lecturer not found");
            }
        } catch (DataIntegrityViolationException exception) {
            throw problem(HttpStatus.CONFLICT, "LECTURER_IN_USE", "Lecturer is assigned to a section");
        }
    }

    private Map<String, Object> find(String id) {
        return jdbc.queryForMap("SELECT * FROM " + LECTURER + " WHERE \"id\" = :id", new MapSqlParameterSource("id", id));
    }

    private static MapSqlParameterSource parameters(Map<String, Object> input) {
        return new MapSqlParameterSource()
                .addValue("userId", required(input, "userId"))
                .addValue("departmentId", required(input, "departmentId"))
                .addValue("employeeId", required(input, "employeeId"))
                .addValue("title", input.get("title"))
                .addValue("specialization", input.get("specialization"))
                .addValue("office", input.get("office"))
                .addValue("phone", input.get("phone"));
    }

    private static String required(Map<String, Object> input, String name) {
        Object value = input.get(name);
        if (value == null || value.toString().isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.toString().trim();
    }

    private static DomainException problem(HttpStatus status, String code, String message) {
        return new DomainException(status, code, message);
    }
}
