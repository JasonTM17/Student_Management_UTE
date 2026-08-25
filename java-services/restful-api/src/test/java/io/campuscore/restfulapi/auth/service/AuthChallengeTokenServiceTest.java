package io.campuscore.restfulapi.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AuthChallengeTokenServiceTest {

    @Test
    void issuesOpaqueHighEntropyTokenWithParseableChallengeIdentity() {
        var issued = AuthChallengeTokenService.issue();

        assertThat(issued.rawToken()).doesNotContain(issued.tokenHash());
        assertThat(AuthChallengeTokenService.challengeId(issued.rawToken()))
                .contains(issued.challengeId());
        assertThat(AuthChallengeTokenService.matches(issued.rawToken(), issued.tokenHash())).isTrue();
        assertThat(AuthChallengeTokenService.matches(issued.rawToken() + "x", issued.tokenHash())).isFalse();
        assertThat(AuthChallengeTokenService.challengeId("not-a-token")).isEmpty();
    }
}
