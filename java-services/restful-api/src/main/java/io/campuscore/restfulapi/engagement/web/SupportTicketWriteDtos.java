package io.campuscore.restfulapi.engagement.web;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

/** Request records for feature-gated support-ticket write candidates. */
public final class SupportTicketWriteDtos {

    private SupportTicketWriteDtos() {
    }

    public static final class CreateSupportTicketRequest extends StrictBody {
        @NotNull
        @JsonProperty
        private String subject;
        @NotNull
        @JsonProperty
        private String description;
        @NotNull
        @JsonProperty
        private String category;
        @JsonProperty
        private String priority;

        public CreateSupportTicketRequest() {
        }

        public CreateSupportTicketRequest(String subject, String description, String category, String priority) {
            this.subject = subject;
            this.description = description;
            this.category = category;
            this.priority = priority;
        }

        public String subject() {
            return subject;
        }

        public String description() {
            return description;
        }

        public String category() {
            return category;
        }

        public String priority() {
            return priority;
        }
    }

    public static final class CreateTicketResponseRequest extends StrictBody {
        @NotNull
        @JsonProperty
        private String message;
        @JsonProperty
        private Boolean isInternal;

        public CreateTicketResponseRequest() {
        }

        public CreateTicketResponseRequest(String message, Boolean isInternal) {
            this.message = message;
            this.isInternal = isInternal;
        }

        public String message() {
            return message;
        }

        public Boolean isInternal() {
            return isInternal;
        }
    }

    public static final class UpdateSupportTicketRequest extends StrictBody {
        @JsonProperty
        private String subject;
        @JsonProperty
        private String description;
        @JsonProperty
        private String category;
        @JsonProperty
        private String priority;
        @JsonProperty
        private String status;

        public UpdateSupportTicketRequest() {
        }

        public UpdateSupportTicketRequest(
                String subject,
                String description,
                String category,
                String priority,
                String status) {
            this.subject = subject;
            this.description = description;
            this.category = category;
            this.priority = priority;
            this.status = status;
        }

        public String subject() {
            return subject;
        }

        public String description() {
            return description;
        }

        public String category() {
            return category;
        }

        public String priority() {
            return priority;
        }

        public String status() {
            return status;
        }
    }

    public static final class AssignSupportTicketRequest extends StrictBody {
        @NotNull
        @JsonProperty
        private String assignedTo;

        public AssignSupportTicketRequest() {
        }

        public AssignSupportTicketRequest(String assignedTo) {
            this.assignedTo = assignedTo;
        }

        public String assignedTo() {
            return assignedTo;
        }
    }

    public record DeleteSupportTicketResponse(String message) {
    }

    private abstract static class StrictBody {

        @JsonAnySetter
        void rejectUnknown(String field, Object ignored) {
            throw new IllegalArgumentException("Unexpected body property: " + field);
        }
    }
}
