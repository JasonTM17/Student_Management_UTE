package io.campuscore.restfulapi.engagement.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Request records for feature-gated announcement write candidates. */
public final class AnnouncementWriteDtos {

    private static final Set<String> ANNOUNCEMENT_FIELDS = Set.of(
            "title",
            "content",
            "priority",
            "targetRoles",
            "targetYears",
            "isGlobal",
            "publishAt",
            "expiresAt",
            "semesterId",
            "sectionId",
            "lecturerId");

    private AnnouncementWriteDtos() {
    }

    public record CreateAnnouncementRequest(
            @NotNull String title,
            @NotNull String content,
            String priority,
            List<?> targetRoles,
            List<?> targetYears,
            Boolean isGlobal,
            Instant publishAt,
            Instant expiresAt,
            String semesterId,
            String sectionId,
            String lecturerId) {

        public static CreateAnnouncementRequest from(JsonNode body) {
            ObjectNode object = objectBody(body);
            ensureAllowedFields(object);
            return new CreateAnnouncementRequest(
                    stringValue(object, "title", false),
                    stringValue(object, "content", false),
                    stringValue(object, "priority", false),
                    listValue(object, "targetRoles"),
                    listValue(object, "targetYears"),
                    booleanValue(object, "isGlobal"),
                    instantValue(object, "publishAt"),
                    instantValue(object, "expiresAt"),
                    stringValue(object, "semesterId", true),
                    stringValue(object, "sectionId", true),
                    stringValue(object, "lecturerId", true));
        }
    }

    public record UpdateAnnouncementRequest(
            Set<String> presentFields,
            String title,
            String content,
            String priority,
            List<?> targetRoles,
            List<?> targetYears,
            Boolean isGlobal,
            Instant publishAt,
            Instant expiresAt,
            String semesterId,
            String sectionId,
            String lecturerId) {

        public static UpdateAnnouncementRequest from(JsonNode body) {
            ObjectNode object = objectBody(body);
            Set<String> presentFields = presentAllowedFields(object);
            return new UpdateAnnouncementRequest(
                    Collections.unmodifiableSet(presentFields),
                    stringValue(object, "title", false),
                    stringValue(object, "content", false),
                    stringValue(object, "priority", false),
                    listValue(object, "targetRoles"),
                    listValue(object, "targetYears"),
                    booleanValue(object, "isGlobal"),
                    instantValue(object, "publishAt"),
                    instantValue(object, "expiresAt"),
                    stringValue(object, "semesterId", true),
                    stringValue(object, "sectionId", true),
                    stringValue(object, "lecturerId", true));
        }

        public boolean has(String field) {
            return presentFields.contains(field);
        }
    }

    private static ObjectNode objectBody(JsonNode body) {
        if (body == null || body.isNull()) {
            throw new IllegalArgumentException("request body is required");
        }
        if (!(body instanceof ObjectNode object)) {
            throw new IllegalArgumentException("request body must be an object");
        }
        return object;
    }

    private static void ensureAllowedFields(ObjectNode body) {
        presentAllowedFields(body);
    }

    private static Set<String> presentAllowedFields(ObjectNode body) {
        Set<String> present = new LinkedHashSet<>();
        Iterator<String> fieldNames = body.fieldNames();
        while (fieldNames.hasNext()) {
            String field = fieldNames.next();
            if (!ANNOUNCEMENT_FIELDS.contains(field)) {
                throw new IllegalArgumentException("Unexpected body property: " + field);
            }
            present.add(field);
        }
        return present;
    }

    private static boolean has(ObjectNode body, String field) {
        return body.has(field);
    }

    private static String stringValue(ObjectNode body, String field, boolean allowNull) {
        if (!has(body, field)) {
            return null;
        }
        JsonNode value = body.get(field);
        if (value.isNull()) {
            if (allowNull) {
                return null;
            }
            throw new IllegalArgumentException(field + " must be a string");
        }
        if (!value.isTextual()) {
            throw new IllegalArgumentException(field + " must be a string");
        }
        return value.asText();
    }

    private static Boolean booleanValue(ObjectNode body, String field) {
        if (!has(body, field)) {
            return null;
        }
        JsonNode value = body.get(field);
        if (!value.isBoolean()) {
            throw new IllegalArgumentException(field + " must be a boolean");
        }
        return value.booleanValue();
    }

    private static List<?> listValue(ObjectNode body, String field) {
        if (!has(body, field)) {
            return null;
        }
        JsonNode value = body.get(field);
        if (!value.isArray()) {
            throw new IllegalArgumentException(field + " must be an array");
        }
        List<Object> values = new ArrayList<>();
        for (JsonNode item : value) {
            if (item.isTextual()) {
                values.add(item.asText());
            } else if (item.isIntegralNumber() && item.canConvertToInt()) {
                values.add(item.intValue());
            } else if (item.isNumber()) {
                values.add(item.decimalValue());
            } else if (item.isBoolean()) {
                values.add(item.booleanValue());
            } else {
                values.add(item);
            }
        }
        return List.copyOf(values);
    }

    private static Instant instantValue(ObjectNode body, String field) {
        if (!has(body, field)) {
            return null;
        }
        JsonNode value = body.get(field);
        if (value.isNull()) {
            return null;
        }
        if (!value.isTextual()) {
            throw new IllegalArgumentException(field + " must be an ISO-8601 timestamp");
        }
        try {
            return Instant.parse(value.asText());
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(field + " must be an ISO-8601 timestamp", exception);
        }
    }
}
