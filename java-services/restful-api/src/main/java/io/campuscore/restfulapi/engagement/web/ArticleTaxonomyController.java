package io.campuscore.restfulapi.engagement.web;

import io.campuscore.restfulapi.engagement.domain.ArticleAttachment;
import io.campuscore.restfulapi.engagement.domain.ArticleCategory;
import io.campuscore.restfulapi.engagement.domain.ArticleMediaGallery;
import io.campuscore.restfulapi.engagement.domain.ArticleTag;
import io.campuscore.restfulapi.engagement.repository.ArticleAttachmentRepository;
import io.campuscore.restfulapi.engagement.repository.ArticleCategoryRepository;
import io.campuscore.restfulapi.engagement.repository.ArticleMediaGalleryRepository;
import io.campuscore.restfulapi.engagement.repository.ArticleTagRepository;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Spring Data JPA ORM REST Controller for Article Taxonomy, Media Gallery, and Attachments.
 */
@RestController
@Profile("persistence")
@RequestMapping("/api/v1")
public class ArticleTaxonomyController {

    private final ArticleCategoryRepository categoryRepository;
    private final ArticleTagRepository tagRepository;
    private final ArticleMediaGalleryRepository mediaGalleryRepository;
    private final ArticleAttachmentRepository attachmentRepository;

    public ArticleTaxonomyController(
            ArticleCategoryRepository categoryRepository,
            ArticleTagRepository tagRepository,
            ArticleMediaGalleryRepository mediaGalleryRepository,
            ArticleAttachmentRepository attachmentRepository) {
        this.categoryRepository = categoryRepository;
        this.tagRepository = tagRepository;
        this.mediaGalleryRepository = mediaGalleryRepository;
        this.attachmentRepository = attachmentRepository;
    }

    @GetMapping("/article-categories")
    public List<ArticleCategory> getCategories() {
        return categoryRepository.findByIsActiveTrueOrderByDisplayOrderAsc();
    }

    @GetMapping("/article-categories/{slug}")
    public ArticleCategory getCategoryBySlug(@PathVariable String slug) {
        return categoryRepository
                .findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found: " + slug));
    }

    @GetMapping("/article-tags")
    public List<ArticleTag> getTags() {
        return tagRepository.findTop20ByOrderByUsageCountDesc();
    }

    @GetMapping("/article-tags/{slug}")
    public ArticleTag getTagBySlug(@PathVariable String slug) {
        return tagRepository
                .findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tag not found: " + slug));
    }

    @GetMapping("/article-media-galleries")
    public List<ArticleMediaGallery> getMediaGalleries(@RequestParam(required = false) String announcementId) {
        if (announcementId != null && !announcementId.isBlank()) {
            return mediaGalleryRepository.findByAnnouncementIdOrderByDisplayOrderAsc(announcementId);
        }
        return mediaGalleryRepository.findAllByOrderByDisplayOrderAsc();
    }

    @GetMapping("/article-attachments")
    public List<ArticleAttachment> getAttachments(@RequestParam(required = false) String announcementId) {
        if (announcementId != null && !announcementId.isBlank()) {
            return attachmentRepository.findByAnnouncementId(announcementId);
        }
        return attachmentRepository.findByIsPublicTrue();
    }
}
