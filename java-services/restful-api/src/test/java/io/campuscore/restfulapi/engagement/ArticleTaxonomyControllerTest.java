package io.campuscore.restfulapi.engagement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.campuscore.restfulapi.engagement.domain.ArticleAttachment;
import io.campuscore.restfulapi.engagement.domain.ArticleCategory;
import io.campuscore.restfulapi.engagement.domain.ArticleMediaGallery;
import io.campuscore.restfulapi.engagement.domain.ArticleTag;
import io.campuscore.restfulapi.engagement.repository.ArticleAttachmentRepository;
import io.campuscore.restfulapi.engagement.repository.ArticleCategoryRepository;
import io.campuscore.restfulapi.engagement.repository.ArticleMediaGalleryRepository;
import io.campuscore.restfulapi.engagement.repository.ArticleTagRepository;
import io.campuscore.restfulapi.engagement.web.ArticleTaxonomyController;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class ArticleTaxonomyControllerTest {

    private ArticleCategoryRepository categoryRepository;
    private ArticleTagRepository tagRepository;
    private ArticleMediaGalleryRepository mediaGalleryRepository;
    private ArticleAttachmentRepository attachmentRepository;
    private ArticleTaxonomyController controller;

    @BeforeEach
    void setUp() {
        categoryRepository = mock(ArticleCategoryRepository.class);
        tagRepository = mock(ArticleTagRepository.class);
        mediaGalleryRepository = mock(ArticleMediaGalleryRepository.class);
        attachmentRepository = mock(ArticleAttachmentRepository.class);
        controller = new ArticleTaxonomyController(
                categoryRepository, tagRepository, mediaGalleryRepository, attachmentRepository);
    }

    @Test
    @DisplayName("getCategories should return active categories sorted by displayOrder")
    void getCategoriesReturnsActiveCategories() {
        ArticleCategory cat = new ArticleCategory(
                "cat-1", "RESEARCH_TECH", "Nghiên cứu & Công nghệ", "Research & Technology",
                "nghien-cuu-cong-nghe", "Desc", "indigo", "flask", 1, true);

        when(categoryRepository.findByIsActiveTrueOrderByDisplayOrderAsc()).thenReturn(List.of(cat));

        List<ArticleCategory> result = controller.getCategories();
        assertEquals(1, result.size());
        assertEquals("RESEARCH_TECH", result.get(0).getCode());
    }

    @Test
    @DisplayName("getCategoryBySlug should return category when found and 404 when missing")
    void getCategoryBySlugHandlesFoundAndMissing() {
        ArticleCategory cat = new ArticleCategory(
                "cat-2", "AWARDS_HONORS", "Học bổng & Khen thưởng", "Scholarships",
                "scholarships", "Desc", "amber", "trophy", 2, true);

        when(categoryRepository.findBySlug("scholarships")).thenReturn(Optional.of(cat));
        when(categoryRepository.findBySlug("missing")).thenReturn(Optional.empty());

        assertEquals(cat, controller.getCategoryBySlug("scholarships"));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> controller.getCategoryBySlug("missing"));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    @DisplayName("getTags should return top tags by usage count")
    void getTagsReturnsTopTags() {
        ArticleTag tag = new ArticleTag("tag-ai", "ai", "Trí tuệ nhân tạo", "Artificial Intelligence", 25);

        when(tagRepository.findTop20ByOrderByUsageCountDesc()).thenReturn(List.of(tag));

        List<ArticleTag> result = controller.getTags();
        assertEquals(1, result.size());
        assertEquals("ai", result.get(0).getSlug());
    }

    @Test
    @DisplayName("getMediaGalleries filters by announcementId when provided")
    void getMediaGalleriesFiltersByAnnouncement() {
        ArticleMediaGallery gallery = new ArticleMediaGallery();
        gallery.setId("gal-1");
        gallery.setAnnouncementId("ann-1");
        gallery.setMediaUrl("https://theses.campusute.io.vn/photo.jpg");
        gallery.setCaptionVi("Lễ tốt nghiệp");

        when(mediaGalleryRepository.findByAnnouncementIdOrderByDisplayOrderAsc("ann-1"))
                .thenReturn(List.of(gallery));
        when(mediaGalleryRepository.findAllByOrderByDisplayOrderAsc())
                .thenReturn(List.of(gallery));

        List<ArticleMediaGallery> filtered = controller.getMediaGalleries("ann-1");
        assertEquals(1, filtered.size());
        assertEquals("ann-1", filtered.get(0).getAnnouncementId());

        List<ArticleMediaGallery> all = controller.getMediaGalleries(null);
        assertEquals(1, all.size());
    }

    @Test
    @DisplayName("getAttachments filters by announcementId when provided")
    void getAttachmentsFiltersByAnnouncement() {
        ArticleAttachment attachment = new ArticleAttachment();
        attachment.setId("att-1");
        attachment.setAnnouncementId("ann-1");
        attachment.setFileName("thong_bao.pdf");

        when(attachmentRepository.findByAnnouncementId("ann-1"))
                .thenReturn(List.of(attachment));
        when(attachmentRepository.findByIsPublicTrue())
                .thenReturn(List.of(attachment));

        List<ArticleAttachment> filtered = controller.getAttachments("ann-1");
        assertEquals(1, filtered.size());
        assertEquals("thong_bao.pdf", filtered.get(0).getFileName());

        List<ArticleAttachment> allPublic = controller.getAttachments(null);
        assertEquals(1, allPublic.size());
    }
}
