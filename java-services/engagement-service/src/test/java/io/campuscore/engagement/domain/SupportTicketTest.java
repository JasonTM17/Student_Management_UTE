package io.campuscore.engagement.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class SupportTicketTest {

    @Test
    void initializesWithOpenStatusAndGeneratedTicketNumber() {
        SupportTicket ticket = new SupportTicket(
                UUID.randomUUID(),
                "student@example.edu",
                "Lan Nguyen",
                "Need help with enrollment",
                "Cannot register for course ABC",
                "ACADEMIC",
                SupportTicket.Priority.HIGH);

        assertThat(ticket.getStatus()).isEqualTo(SupportTicket.Status.OPEN);
        assertThat(ticket.getPriority()).isEqualTo(SupportTicket.Priority.HIGH);
        assertThat(ticket.getTicketNumber()).isNull();
    }

    @Test
    void transitionsToInProgressOnAssignment() {
        SupportTicket ticket = new SupportTicket(
                UUID.randomUUID(),
                "student@example.edu",
                "Lan Nguyen",
                "Subject",
                "Description",
                "TECHNICAL",
                SupportTicket.Priority.MEDIUM);

        UUID assigneeId = UUID.randomUUID();
        ticket.assign(assigneeId, "Support Agent");

        assertThat(ticket.getStatus()).isEqualTo(SupportTicket.Status.IN_PROGRESS);
        assertThat(ticket.getAssignedTo()).isEqualTo(assigneeId);
        assertThat(ticket.getAssignedToDisplayName()).isEqualTo("Support Agent");
    }

    @Test
    void attachesResponseToTicket() {
        SupportTicket ticket = new SupportTicket(
                UUID.randomUUID(),
                "student@example.edu",
                "Lan Nguyen",
                "Subject",
                "Description",
                "TECHNICAL",
                SupportTicket.Priority.MEDIUM);

        TicketResponse response = new TicketResponse(UUID.randomUUID(), "agent@example.edu", "Agent", "Reply", false);
        ticket.addResponse(response);

        assertThat(ticket.getResponses()).hasSize(1);
        assertThat(response.getTicket()).isSameAs(ticket);
    }
}
