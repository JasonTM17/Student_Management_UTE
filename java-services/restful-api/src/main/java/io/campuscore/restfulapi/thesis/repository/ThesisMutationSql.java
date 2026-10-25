package io.campuscore.restfulapi.thesis.repository;

import io.campuscore.restfulapi.exception.DomainException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Single-row helpers shared by the thesis mutation repositories: a parameter
 * source factory and the {@code queryForMap} wrapper that maps "no row" to the
 * module's not-found DomainException instead of Spring's raw
 * EmptyResultDataAccessException.
 */
final class ThesisMutationSql {

    private ThesisMutationSql() {
    }

    static MapSqlParameterSource params() {
        return new MapSqlParameterSource();
    }

    static java.util.Map<String, Object> one(
            NamedParameterJdbcTemplate jdbc,
            String sql,
            MapSqlParameterSource parameters,
            String code,
            String message) {
        try {
            return jdbc.queryForMap(sql, parameters);
        } catch (EmptyResultDataAccessException exception) {
            throw new DomainException(HttpStatus.NOT_FOUND, code, message);
        }
    }
}
