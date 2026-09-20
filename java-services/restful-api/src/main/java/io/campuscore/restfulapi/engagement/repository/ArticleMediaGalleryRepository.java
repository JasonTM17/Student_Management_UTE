package io.campuscore.restfulapi.engagement.repository;

import io.campuscore.restfulapi.engagement.domain.ArticleMediaGallery;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA Repository for the article media gallery (ảnh/video minh họa bài viết).
 */
public interface ArticleMediaGalleryRepository extends JpaRepository<ArticleMediaGallery, String> {

    /**
     * Reads every media item of one article in display order.
     *
     * @param announcementId id of the announcement/article that owns the gallery
     * @return the article's media items, ascending by {@code displayOrder}
     */
    List<ArticleMediaGallery> findByAnnouncementIdOrderByDisplayOrderAsc(String announcementId);

    /**
     * Reads the media items of one article as a bounded page.
     *
     * @param announcementId id of the announcement/article that owns the gallery
     * @param pageable page index, size, and sort applied by the caller
     * @return one page of the article's media items plus the total row count
     */
    Page<ArticleMediaGallery> findByAnnouncementId(String announcementId, Pageable pageable);

    /**
     * Reads one page of the campus-wide gallery, ordered and sized by the caller.
     *
     * @param pageable page index, size, and sort applied by the caller
     * @return one page of gallery items plus the total row count
     */
    @Override
    Page<ArticleMediaGallery> findAll(Pageable pageable);

    /**
     * Reads every gallery item flagged as an article cover image.
     *
     * @return all cover images, in storage order
     */
    List<ArticleMediaGallery> findByIsCoverTrue();

    /**
     * Reads the whole campus-wide gallery in display order.
     *
     * @return every gallery item, ascending by {@code displayOrder}
     */
    List<ArticleMediaGallery> findAllByOrderByDisplayOrderAsc();
}
