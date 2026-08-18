package io.campuscore.thesis.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class ThesisRoundTest {

    @Test
    void followsTheRegistrationLifecycle() {
        Instant start = Instant.now().minusSeconds(60);
        ThesisRegistrationRound round = new ThesisRegistrationRound(
                "2026 graduation thesis",
                "NCKH",
                start,
                start.plusSeconds(3600),
                null,
                null,
                null);

        round.openRegistration();
        round.closeRegistration();
        round.publishProposals();

        assertThat(round.getStatus()).isEqualTo(RoundStatus.PROPOSALS_PUBLISHED);
    }

    @Test
    void rejectsInvalidTransitions() {
        ThesisRegistrationRound round = new ThesisRegistrationRound(
                "Round",
                "TL",
                Instant.now().minusSeconds(60),
                Instant.now().plusSeconds(3600),
                null,
                null,
                null);

        assertThatThrownBy(round::closeRegistration)
                .isInstanceOf(IllegalStateException.class);
        assertThat(round.getStatus()).isEqualTo(RoundStatus.DRAFT);
    }
}
