package io.campuscore.restfulapi.thesis.repository;

import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Read-side boundary for thesis round existence checks. */
@Component
@Profile("persistence")
public class ThesisRoundReadPort {

    private final JdbcTemplate jdbc;

    public ThesisRoundReadPort(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void requireExisting(UUID roundId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.thesis_registration_round WHERE id = ?",
                Integer.class,
                roundId);
        if (count == null || count == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Thesis registration round not found");
        }
    }
}
