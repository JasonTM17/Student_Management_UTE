package io.campuscore.restfulapi.engagement.repository;

import io.campuscore.restfulapi.engagement.domain.ArticleAttachment;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA Repository for article attachments (tài liệu, quyết định, biểu mẫu đính kèm).
 */
public interface ArticleAttachmentRepository extends JpaRepository<ArticleAttachment, String> {

    /**
     * Reads every publicly downloadable attachment.
     *
     * @return all public attachments, in storage order
     */
    List<ArticleAttachment> findByIsPublicTrue();

    /**
     * Reads one page of the publicly downloadable attachments.
     *
     * @param pageable page index, size, and sort applied by the caller
     * @return one page of public attachments plus the total row count
     */
    Page<ArticleAttachment> findByIsPublicTrue(Pageable pageable);

    /**
     * Reads only the publicly downloadable attachments of one article. Scoped
     * feed queries must not expose rows flagged non-public.
     *
     * @param announcementId id of the announcement/article that owns the attachment
     * @return the article's public attachments, in storage order
     */
    List<ArticleAttachment> findByAnnouncementIdAndIsPublicTrue(String announcementId);

    /**
     * Reads one page of an article's publicly downloadable attachments.
     *
     * @param announcementId id of the announcement/article that owns the attachment
     * @param pageable page index, size, and sort applied by the caller
     * @return one page of the article's public attachments plus the total row count
     */
    Page<ArticleAttachment> findByAnnouncementIdAndIsPublicTrue(String announcementId, Pageable pageable);
}
