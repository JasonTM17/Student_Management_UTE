package io.campuscore.restfulapi.engagement.repository;

import io.campuscore.restfulapi.engagement.domain.ArticleAttachment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArticleAttachmentRepository extends JpaRepository<ArticleAttachment, String> {

    List<ArticleAttachment> findByAnnouncementId(String announcementId);

    List<ArticleAttachment> findByIsPublicTrue();
}
