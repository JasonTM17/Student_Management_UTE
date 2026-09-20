package io.campuscore.restfulapi.engagement.repository;

import io.campuscore.restfulapi.engagement.domain.ArticleCategory;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA Repository for ArticleCategory.
 */
@Repository
public interface ArticleCategoryRepository extends JpaRepository<ArticleCategory, String> {

    /**
     * Reads the categories that are visible to readers, in administrator display order.
     *
     * @return every active category, ascending by {@code displayOrder}
     */
    List<ArticleCategory> findByIsActiveTrueOrderByDisplayOrderAsc();

    /**
     * Resolves a category from its SEO slug.
     *
     * @param slug unique URL slug of the category, for example {@code nghien-cuu-cong-nghe}
     * @return the category with that slug, or empty when the slug is unknown
     */
    Optional<ArticleCategory> findBySlug(String slug);

    /**
     * Resolves a category from its stable machine code.
     *
     * @param code unique code of the category, for example {@code RESEARCH_TECH}
     * @return the category with that code, or empty when the code is unknown
     */
    Optional<ArticleCategory> findByCode(String code);
}
