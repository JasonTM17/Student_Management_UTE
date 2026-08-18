package io.campuscore.thesis.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.campuscore.thesis.domain.ThesisGroup;
import io.campuscore.thesis.domain.ThesisGroupMember;
import io.campuscore.thesis.repository.ThesisGroupMemberRepository;
import io.campuscore.thesis.repository.ThesisGroupRepository;
import io.campuscore.thesis.web.DomainExceptions;
import io.campuscore.thesis.web.ThesisDtos.AddMemberRequest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ThesisGroupServiceTest {

    @Mock
    private ThesisGroupRepository groups;

    @Mock
    private ThesisGroupMemberRepository members;

    @Mock
    private ThesisRoundService rounds;

    @Mock
    private ThesisTopicService topics;

    @Test
    void refusesTheFourthMember() {
        ThesisGroup group = new ThesisGroup(UUID.randomUUID(), UUID.randomUUID());
        UUID actor = group.getLeaderStudentId();
        UUID roundId = group.getRoundId();
        when(groups.findByIdForUpdate(group.getId())).thenReturn(group);
        when(members.findAllByGroupIdForUpdate(group.getId())).thenReturn(List.of(
                new ThesisGroupMember(group.getId(), roundId, UUID.randomUUID(), 1, true),
                new ThesisGroupMember(group.getId(), roundId, UUID.randomUUID(), 2, false),
                new ThesisGroupMember(group.getId(), roundId, UUID.randomUUID(), 3, false)));

        ThesisGroupService service = new ThesisGroupService(groups, members, rounds, topics);

        assertThatThrownBy(() -> service.addMember(
                        group.getId(),
                        new AddMemberRequest(UUID.randomUUID()),
                        actor))
                .isInstanceOf(DomainExceptions.Conflict.class)
                .hasMessageContaining("at most 3");
        verify(members, never()).save(any());
    }
}
