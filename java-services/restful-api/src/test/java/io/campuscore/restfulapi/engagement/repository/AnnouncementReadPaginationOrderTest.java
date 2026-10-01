package io.campuscore.restfulapi.engagement.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Round-3 thesis-1: seeded announcement rows share createdAt in bulk (12
 * identical timestamps), and the OFFSET windows over a tie-only rank dropped
 * records from full crawls and duplicated ids across adjacent pages. Every
 * announcement feed ORDER BY must end in a unique id tiebreaker.
 */
class AnnouncementReadPaginationOrderTest {

    @Test
    void userFeedOrderEndsInAnIdTiebreaker() {
        assertThat(AnnouncementReadRepository.USER_ORDER_CLAUSE)
                .startsWith(" ORDER BY \"createdAt\" DESC")
                .endsWith("\"id\" DESC");
    }

    @Test
    void displayOrderClauseEndsInAnIdTiebreaker() {
        // The same clause serves the admin list and the public feed.
        assertThat(AnnouncementReadRepository.DISPLAY_ORDER_CLAUSE).endsWith(", \"id\" DESC");
    }
}
