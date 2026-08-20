package io.campuscore.restfulapi.engagement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.campuscore.restfulapi.engagement.repository.SupportTicketWriteRepository;
import io.campuscore.restfulapi.engagement.repository.SupportTicketWriteRepository.CreateTicketCommand;
import io.campuscore.restfulapi.engagement.service.SupportTicketWriteService;
import io.campuscore.restfulapi.engagement.service.SupportTicketWriteService.CurrentTicketUser;
import io.campuscore.restfulapi.engagement.web.SupportTicketReadDtos.SupportTicketResponse;
import io.campuscore.restfulapi.engagement.web.SupportTicketReadDtos.TicketUser;
import io.campuscore.restfulapi.engagement.web.SupportTicketWriteDtos.CreateSupportTicketRequest;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

class SupportTicketWriteServiceTest {

    @Test
    void createRetriesBoundedTicketNumberCollision() {
        SupportTicketWriteRepository repository = mock(SupportTicketWriteRepository.class);
        SupportTicketWriteService service = new SupportTicketWriteService(repository);
        when(repository.nextTicketSequence()).thenReturn(2L, 3L);
        when(repository.create(any(CreateTicketCommand.class)))
                .thenThrow(new DuplicateKeyException("duplicate ticketNumber"))
                .thenAnswer(invocation -> {
                    CreateTicketCommand command = invocation.getArgument(0);
                    return responseFor(command);
                });

        SupportTicketResponse response = service.create(
                new CurrentTicketUser("user-1", "student@campuscore.edu", "Student One"),
                new CreateSupportTicketRequest("Need help", "Cannot open dashboard", "TECHNICAL", null));

        assertEquals("TKT-00003", response.ticketNumber());
        assertEquals("MEDIUM", response.priority());
        verify(repository, times(2)).create(any(CreateTicketCommand.class));
    }

    private static SupportTicketResponse responseFor(CreateTicketCommand command) {
        return new SupportTicketResponse(
                command.id(),
                command.ticketNumber(),
                command.userId(),
                command.userEmail(),
                command.userDisplayName(),
                command.subject(),
                command.description(),
                command.category(),
                command.priority(),
                "OPEN",
                null,
                null,
                null,
                null,
                Instant.parse("2026-08-20T00:00:00Z"),
                Instant.parse("2026-08-20T00:00:00Z"),
                new TicketUser(command.userId(), command.userEmail(), command.userDisplayName()),
                List.of());
    }
}
