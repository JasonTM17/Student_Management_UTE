package io.campuscore.auth.service;

import io.campuscore.auth.web.AuthUserResponse;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class LegacyAuthUserRepository {

    private static final String USER_QUERY = """
            select u.id, u.email, u."firstName", u."lastName", u.phone, u.gender::text as gender,
                   u."dateOfBirth", u.address, u.avatar, u.status::text as status, u."createdAt",
                   s.id as student_id, l.id as lecturer_id
            from auth."User" u
            left join auth."Student" s on s."userId" = u.id
            left join auth."Lecturer" l on l."userId" = u.id
            where u.id = ? and u.status = 'ACTIVE'
            """;

    private final JdbcTemplate jdbc;

    public LegacyAuthUserRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public AuthUserResponse findActiveById(UUID userId) {
        List<UserRow> users = jdbc.query(USER_QUERY, new UserRowMapper(), userId);
        if (users.isEmpty()) {
            return null;
        }
        UserRow user = users.getFirst();
        return new AuthUserResponse(
                user.id(), user.email(), user.firstName(), user.lastName(), user.phone(), user.gender(),
                user.dateOfBirth(), user.address(), user.avatar(), user.status(), user.createdAt(),
                roles(userId), permissions(userId), user.studentId(), user.lecturerId());
    }

    private List<String> roles(UUID userId) {
        return jdbc.queryForList("""
                select r.name::text
                from auth."UserRole" ur
                join auth."Role" r on r.id = ur."roleId"
                where ur."userId" = ?
                order by r.name
                """, String.class, userId);
    }

    private List<String> permissions(UUID userId) {
        return jdbc.queryForList("""
                select distinct p.module || ':' || p.action
                from auth."UserRole" ur
                join auth."RolePermission" rp on rp."roleId" = ur."roleId"
                join auth."Permission" p on p.id = rp."permissionId"
                where ur."userId" = ?
                order by p.module || ':' || p.action
                """, String.class, userId);
    }

    private static final class UserRowMapper implements RowMapper<UserRow> {
        @Override
        public UserRow mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new UserRow(
                    rs.getObject("id", UUID.class),
                    rs.getString("email"),
                    rs.getString("firstName"),
                    rs.getString("lastName"),
                    rs.getString("phone"),
                    rs.getString("gender"),
                    rs.getTimestamp("dateOfBirth") == null ? null : rs.getTimestamp("dateOfBirth").toInstant(),
                    rs.getString("address"),
                    rs.getString("avatar"),
                    rs.getString("status"),
                    rs.getTimestamp("createdAt").toInstant(),
                    rs.getObject("student_id", UUID.class),
                    rs.getObject("lecturer_id", UUID.class));
        }
    }

    private record UserRow(
            UUID id,
            String email,
            String firstName,
            String lastName,
            String phone,
            String gender,
            java.time.Instant dateOfBirth,
            String address,
            String avatar,
            String status,
            java.time.Instant createdAt,
            UUID studentId,
            UUID lecturerId) {
    }
}
