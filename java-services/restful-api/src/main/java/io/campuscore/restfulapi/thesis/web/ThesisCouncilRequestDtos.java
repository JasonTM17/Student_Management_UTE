package io.campuscore.restfulapi.thesis.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Typed request bodies for the council management routes. Field names match
 * the JSON keys the console already sends (thesisApi council calls) and the
 * values are forwarded to {@code ThesisCouncilService} exactly like the
 * previous raw-map handling, so the wire contract is preserved.
 *
 * <p>Identifiers stay {@code String} (with a UUID pattern where the route
 * needs one) because Jackson would fail a typed UUID with a generic
 * {@code INVALID_REQUEST} body, while the API contract (and the governance
 * integration tests) expect {@code VALIDATION_ERROR} for malformed input.
 */
public final class ThesisCouncilRequestDtos {

    private ThesisCouncilRequestDtos() {
    }

    /** Council creation payload for a thesis round. */
    public record CreateCouncilRequest(
            @NotBlank(message = "roundId is required")
            @Pattern(regexp = UUID_PATTERN, message = "roundId must be a UUID")
            String roundId,
            @Size(max = 200, message = "name must contain at most 200 characters")
            String name) {
    }

    /** Council membership payload; the role is validated by the service. */
    public record AddCouncilMemberRequest(
            @NotBlank(message = "lecturerId is required")
            String lecturerId,
            @Size(max = 60, message = "memberRole must contain at most 60 characters")
            String memberRole) {
    }

    /** Topic assignment payload for a council. */
    public record AssignTopicRequest(
            @NotBlank(message = "topicId is required")
            @Pattern(regexp = UUID_PATTERN, message = "topicId must be a UUID")
            String topicId) {
    }

    /** Component score payload; the score range is enforced by the service. */
    public record SubmitScoreRequest(
            @Size(max = 60, message = "component must contain at most 60 characters")
            String component,
            @Pattern(
                    regexp = "-?\\d+(\\.\\d+)?([eE][+-]?\\d+)?",
                    message = "score must be numeric")
            String score) {
    }

    private static final String UUID_PATTERN =
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";
}
