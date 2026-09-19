package io.campuscore.restfulapi.auth.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Typed request bodies for the admin user management routes. Field names match
 * the JSON keys the admin console already sends (usersApi.create / usersApi.update)
 * and the keys AdminUserMutationService reads, so converting to the service's
 * input map preserves the existing contract byte for byte.
 *
 * <p>Only non-null values are copied into the service input map: update
 * semantics treat an absent field as "unchanged".
 */
public final class AdminUserRequestDtos {

    private AdminUserRequestDtos() {
    }

    /** Account issuance payload. Profile fields depend on the requested role. */
    public record AdminUserCreateRequest(
            @NotBlank(message = "email is required")
            @Email(message = "email must be a valid address")
            @Size(max = 320, message = "email must contain at most 320 characters")
            String email,
            @NotBlank(message = "firstName is required")
            @Size(max = 120, message = "firstName must contain at most 120 characters")
            String firstName,
            @NotBlank(message = "lastName is required")
            @Size(max = 120, message = "lastName must contain at most 120 characters")
            String lastName,
            @Size(max = 60, message = "role must contain at most 60 characters")
            String role,
            @Size(max = 120, message = "studentId must contain at most 120 characters")
            String studentId,
            Object year,
            @Size(max = 120, message = "curriculumId must contain at most 120 characters")
            String curriculumId,
            @Size(max = 120, message = "employeeId must contain at most 120 characters")
            String employeeId,
            @Size(max = 120, message = "departmentId must contain at most 120 characters")
            String departmentId) {

        public Map<String, Object> toInput() {
            Map<String, Object> input = new LinkedHashMap<>();
            putIfPresent(input, "email", email);
            putIfPresent(input, "firstName", firstName);
            putIfPresent(input, "lastName", lastName);
            putIfPresent(input, "role", role);
            putIfPresent(input, "studentId", studentId);
            if (year != null) {
                // The console sends the cohort year from a select (a JSON
                // string); the service parses it. Copied verbatim so both a
                // number and a string keep working exactly as before.
                input.put("year", year);
            }
            putIfPresent(input, "curriculumId", curriculumId);
            putIfPresent(input, "employeeId", employeeId);
            putIfPresent(input, "departmentId", departmentId);
            return input;
        }
    }

    /** Profile/role/status payload. Absent fields keep their current value. */
    public record AdminUserUpdateRequest(
            @Size(max = 120, message = "firstName must contain at most 120 characters")
            String firstName,
            @Size(max = 120, message = "lastName must contain at most 120 characters")
            String lastName,
            @Size(max = 60, message = "role must contain at most 60 characters")
            String role,
            @Size(max = 40, message = "phone must contain at most 40 characters")
            String phone,
            @Size(max = 40, message = "status must contain at most 40 characters")
            String status) {

        public Map<String, Object> toInput() {
            Map<String, Object> input = new LinkedHashMap<>();
            putIfPresent(input, "firstName", firstName);
            putIfPresent(input, "lastName", lastName);
            putIfPresent(input, "role", role);
            putIfPresent(input, "phone", phone);
            putIfPresent(input, "status", status);
            return input;
        }
    }

    private static void putIfPresent(Map<String, Object> input, String key, String value) {
        // Blank strings mean "not provided" exactly like the previous raw map
        // handling (text() substitutes the fallback), so they are dropped too.
        if (value != null && !value.isBlank()) {
            input.put(key, value.trim());
        }
    }
}
