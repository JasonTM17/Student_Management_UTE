package io.campuscore.restfulapi.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Guards the contract every admin list search depends on: the value bound to
 * {@code :search} is a lowered, escaped, bounded {@code %term%} pattern, or
 * {@code null} when no filter applies.
 */
class LikePatternTest {

    @Test
    @DisplayName("wildcards and the escape character survive as literal text")
    void wildcardsBecomeLiteralPatterns() {
        assertThat(LikePattern.contains("SE401")).isEqualTo("%se401%");
        assertThat(LikePattern.contains("50%")).isEqualTo("%50\\%%");
        assertThat(LikePattern.contains("a_b")).isEqualTo("%a\\_b%");
        assertThat(LikePattern.contains("back\\slash")).isEqualTo("%back\\\\slash%");
    }

    @Test
    @DisplayName("absent, empty and whitespace-only terms bind no filter at all")
    void blankTermsAreNotFilters() {
        assertThat(LikePattern.contains(null)).isNull();
        assertThat(LikePattern.contains("")).isNull();
        assertThat(LikePattern.contains("   ")).isNull();
    }

    @Test
    @DisplayName("an over-long term is truncated to the bounded prefix")
    void longTermsStayBounded() {
        String bounded = LikePattern.contains("a".repeat(LikePattern.MAX_TERM_LENGTH * 3));

        assertThat(bounded).isEqualTo("%" + "a".repeat(LikePattern.MAX_TERM_LENGTH) + "%");
    }
}
