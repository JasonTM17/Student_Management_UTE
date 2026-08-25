package io.campuscore.restfulapi.thesis.assistant.persistence;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssistantKnowledgeRevisionJpaRepository
        extends JpaRepository<AssistantKnowledgeRevisionEntity, UUID> {

    List<AssistantKnowledgeRevisionEntity> findByDocumentIdOrderByVersionDesc(UUID documentId);

    List<AssistantKnowledgeRevisionEntity> findByDocumentIdAndStateOrderByVersionDesc(UUID documentId, String state);

    @Query("select r from AssistantKnowledgeRevisionEntity r, AssistantKnowledgeDocumentEntity d "
            + "where r.documentId = d.id and r.state = 'PUBLISHED' "
            + "and d.active = true and d.visibility = 'PUBLIC' "
            + "and r.locale in (:locale, 'both') "
            + "order by case when r.locale = :locale then 0 else 1 end, "
            + "r.priority asc, r.publishedAt desc, r.slug asc")
    List<AssistantKnowledgeRevisionEntity> findPublicByLocale(@Param("locale") String locale);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from AssistantKnowledgeRevisionEntity r "
            + "where r.documentId = :documentId and r.state = 'DRAFT' and r.createdBy = :actor "
            + "order by r.version desc")
    List<AssistantKnowledgeRevisionEntity> findOwnDraftsForUpdate(
            @Param("documentId") UUID documentId, @Param("actor") String actor);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from AssistantKnowledgeRevisionEntity r "
            + "where r.documentId = :documentId and r.state = 'PENDING_REVIEW' and r.createdBy <> :actor "
            + "order by r.version desc")
    List<AssistantKnowledgeRevisionEntity> findPendingForOtherReviewer(
            @Param("documentId") UUID documentId, @Param("actor") String actor);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from AssistantKnowledgeRevisionEntity r "
            + "where r.documentId = :documentId and r.state = 'PUBLISHED'")
    List<AssistantKnowledgeRevisionEntity> findPublishedForUpdate(@Param("documentId") UUID documentId);

    @Query("select coalesce(max(r.version), 0) from AssistantKnowledgeRevisionEntity r where r.documentId = :documentId")
    int nextVersionBase(@Param("documentId") UUID documentId);

    Optional<AssistantKnowledgeRevisionEntity> findFirstByDocumentIdOrderByVersionDesc(UUID documentId);
}
