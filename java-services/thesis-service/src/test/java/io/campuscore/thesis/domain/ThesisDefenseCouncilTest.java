package io.campuscore.thesis.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ThesisDefenseCouncilTest {

    @Test
    void cannotOpenScoringBeforeScheduling() {
        ThesisDefenseCouncil council = new ThesisDefenseCouncil(UUID.randomUUID(), UUID.randomUUID());

        assertThatThrownBy(council::openScoring)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void followsScheduledScoringAndFinalizedStates() {
        ThesisDefenseCouncil council = new ThesisDefenseCouncil(UUID.randomUUID(), UUID.randomUUID());
        council.schedule(Instant.now().plusSeconds(3600), "A-101");
        council.openScoring();
        council.finalizeCouncil();

        assertThat(council.getStatus()).isEqualTo(CouncilStatus.FINALIZED);
    }
}
