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

    List<ArticleCategory> findByIsActiveTrueOrderByDisplayOrderAsc();

    Optional<ArticleCategory> findBySlug(String slug);

    Optional<ArticleCategory> findByCode(String code);
}
