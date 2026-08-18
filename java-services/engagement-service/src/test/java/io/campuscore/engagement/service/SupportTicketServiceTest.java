package io.campuscore.engagement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.campuscore.engagement.api.EngagementDtos;
import io.campuscore.engagement.domain.SupportTicket;
import io.campuscore.engagement.repository.SupportTicketRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SupportTicketServiceTest {

    @Mock
    private SupportTicketRepository tickets;

    @Test
    void createsTicketFromRequest() {
        UUID userId = UUID.randomUUID();
        EngagementDtos.CreateTicketRequest request = new EngagementDtos.CreateTicketRequest(
                "Cannot access grades",
                "Grades page returns error",
                "TECHNICAL",
                "HIGH");

        when(tickets.save(any(SupportTicket.class))).thenAnswer(invocation -> {
            SupportTicket ticket = invocation.getArgument(0);
            return ticket;
        });

        SupportTicketService service = new SupportTicketService(tickets);
        var response = service.create(request, userId, "student@example.edu", "Lan Nguyen");

        assertThat(response.subject()).isEqualTo("Cannot access grades");
        assertThat(response.status()).isEqualTo("OPEN");
        assertThat(response.priority()).isEqualTo("HIGH");
        assertThat(response.userEmail()).isEqualTo("student@example.edu");
    }

    @Test
    void assignsTicketToAgent() {
        UUID ticketId = UUID.randomUUID();
        UUID assigneeId = UUID.randomUUID();
        SupportTicket ticket = new SupportTicket(
                UUID.randomUUID(),
                "student@example.edu",
                "Lan Nguyen",
                "Subject",
                "Description",
                "TECHNICAL",
                SupportTicket.Priority.MEDIUM);
        when(tickets.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(tickets.save(any(SupportTicket.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SupportTicketService service = new SupportTicketService(tickets);
        var response = service.assign(ticketId, assigneeId);

        assertThat(response.assignedTo()).isEqualTo(assigneeId);
        assertThat(response.status()).isEqualTo("IN_PROGRESS");
    }

    @Test
    void addsResponseToTicket() {
        UUID ticketId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        SupportTicket ticket = new SupportTicket(
                UUID.randomUUID(),
                "student@example.edu",
                "Lan Nguyen",
                "Subject",
                "Description",
                "TECHNICAL",
                SupportTicket.Priority.MEDIUM);
        when(tickets.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(tickets.save(any(SupportTicket.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SupportTicketService service = new SupportTicketService(tickets);
        var response = service.addResponse(ticketId, userId, "agent@example.edu", "Agent", "Reply", false);

        assertThat(response.status()).isEqualTo("OPEN");
    }
}
