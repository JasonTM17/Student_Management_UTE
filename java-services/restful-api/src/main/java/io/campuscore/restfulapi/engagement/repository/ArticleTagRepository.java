package io.campuscore.restfulapi.engagement.repository;

import io.campuscore.restfulapi.engagement.domain.ArticleTag;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA Repository for ArticleTag.
 */
@Repository
public interface ArticleTagRepository extends JpaRepository<ArticleTag, String> {

    Optional<ArticleTag> findBySlug(String slug);

    List<ArticleTag> findTop20ByOrderByUsageCountDesc();
}
