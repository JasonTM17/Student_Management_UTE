package io.campuscore.restfulapi.notification.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

/** Disabled-by-default notification write DTOs for the strangler candidate. */
public final class NotificationWriteDtos {

    private static final Set<String> UPDATE_FIELDS = Set.of(
            "userId",
            "title",
            "message",
            "type",
            "link");

    private NotificationWriteDtos() {
    }

    public record UpdateNotificationRequest(
            Set<String> presentFields,
            String userId,
            String title,
            String message,
            String type,
            String link) {

        public static UpdateNotificationRequest from(JsonNode body) {
            ObjectNode object = objectBody(body);
            Set<String> presentFields = presentAllowedFields(object);
            return new UpdateNotificationRequest(
                    Collections.unmodifiableSet(presentFields),
                    stringValue(object, "userId", false),
                    stringValue(object, "title", false),
                    stringValue(object, "message", false),
                    stringValue(object, "type", false),
                    stringValue(object, "link", true));
        }

        public boolean has(String field) {
            return presentFields.contains(field);
        }
    }

    public record MarkAllReadResponse(int updated) {
    }

    public record DeleteNotificationResponse(String message) {
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

    private static Set<String> presentAllowedFields(ObjectNode body) {
        Set<String> present = new LinkedHashSet<>();
        Iterator<String> fieldNames = body.fieldNames();
        while (fieldNames.hasNext()) {
            String field = fieldNames.next();
            if (!UPDATE_FIELDS.contains(field)) {
                throw new IllegalArgumentException("Unexpected body property: " + field);
            }
            present.add(field);
        }
        return present;
    }

    private static String stringValue(ObjectNode body, String field, boolean allowNull) {
        if (!body.has(field)) {
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
}
