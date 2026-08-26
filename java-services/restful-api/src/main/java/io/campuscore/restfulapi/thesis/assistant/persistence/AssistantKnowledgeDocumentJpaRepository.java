package io.campuscore.restfulapi.thesis.assistant.persistence;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssistantKnowledgeDocumentJpaRepository
        extends JpaRepository<AssistantKnowledgeDocumentEntity, UUID> {

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from AssistantKnowledgeDocumentEntity d where d.id = :id")
    Optional<AssistantKnowledgeDocumentEntity> findLockedById(@Param("id") UUID id);

    @Query("select d from AssistantKnowledgeDocumentEntity d "
            + "where d.active = true and d.visibility = 'PUBLIC' "
            + "and d.locale in (:locale, 'both') "
            + "order by case when d.locale = :locale then 0 else 1 end, d.priority asc, d.updatedAt desc, d.slug asc")
    List<AssistantKnowledgeDocumentEntity> findPublicByLocale(@Param("locale") String locale);
}
