package io.campuscore.thesis.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.campuscore.thesis.domain.CouncilMemberRole;
import io.campuscore.thesis.domain.ThesisCouncilMember;
import io.campuscore.thesis.domain.ThesisDefenseCouncil;
import io.campuscore.thesis.repository.ThesisCouncilMemberRepository;
import io.campuscore.thesis.repository.ThesisDefenseCouncilRepository;
import io.campuscore.thesis.web.DomainExceptions;
import io.campuscore.thesis.web.ThesisDtos.ScheduleCouncilRequest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ThesisCouncilServiceTest {

    @Mock
    private ThesisDefenseCouncilRepository councils;

    @Mock
    private ThesisCouncilMemberRepository members;

    @Mock
    private ThesisRoundService rounds;

    @Test
    void refusesToScheduleAThreeToFiveMemberCouncilGateWhenTooSmall() {
        ThesisDefenseCouncil council = new ThesisDefenseCouncil(UUID.randomUUID(), UUID.randomUUID());
        when(councils.findByIdForUpdate(council.getId())).thenReturn(council);
        when(members.findAllByCouncilIdForUpdate(council.getId())).thenReturn(List.of(
                new ThesisCouncilMember(council.getId(), UUID.randomUUID(), CouncilMemberRole.CHAIR, 1),
                new ThesisCouncilMember(council.getId(), UUID.randomUUID(), CouncilMemberRole.SECRETARY, 2)));

        ThesisCouncilService service = new ThesisCouncilService(councils, members, rounds);

        assertThatThrownBy(() -> service.schedule(
                        council.getId(),
                        new ScheduleCouncilRequest(Instant.now().plusSeconds(3600), "A-101")))
                .isInstanceOf(DomainExceptions.Conflict.class)
                .hasMessageContaining("between 3 and 5");
        verify(councils, never()).save(any());
    }
}
