package io.campuscore.restfulapi.engagement.repository;

import io.campuscore.restfulapi.engagement.domain.ArticleMediaGallery;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArticleMediaGalleryRepository extends JpaRepository<ArticleMediaGallery, String> {

    List<ArticleMediaGallery> findByAnnouncementIdOrderByDisplayOrderAsc(String announcementId);

    List<ArticleMediaGallery> findByIsCoverTrue();

    List<ArticleMediaGallery> findAllByOrderByDisplayOrderAsc();
}
