package io.campuscore.restfulapi.engagement.repository;

import io.campuscore.restfulapi.engagement.domain.ArticleTag;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA Repository for ArticleTag.
 */
public interface ArticleTagRepository extends JpaRepository<ArticleTag, String> {

    /**
     * Resolves a tag from its SEO slug.
     *
     * @param slug unique URL slug of the tag, for example {@code ai}
     * @return the tag with that slug, or empty when the slug is unknown
     */
    Optional<ArticleTag> findBySlug(String slug);

    /**
     * Reads the most-used tags for the public tag cloud.
     *
     * @return at most twenty tags, descending by {@code usageCount}
     */
    List<ArticleTag> findTop20ByOrderByUsageCountDesc();
}
