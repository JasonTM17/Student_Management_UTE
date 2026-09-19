package io.campuscore.restfulapi.engagement.web;

import io.campuscore.restfulapi.engagement.domain.ArticleAttachment;
import io.campuscore.restfulapi.engagement.domain.ArticleCategory;
import io.campuscore.restfulapi.engagement.domain.ArticleMediaGallery;
import io.campuscore.restfulapi.engagement.domain.ArticleTag;
import io.campuscore.restfulapi.engagement.repository.ArticleAttachmentRepository;
import io.campuscore.restfulapi.engagement.repository.ArticleCategoryRepository;
import io.campuscore.restfulapi.engagement.repository.ArticleMediaGalleryRepository;
import io.campuscore.restfulapi.engagement.repository.ArticleTagRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Article Taxonomy & Media (JPA ORM)", description = "Quản lý danh mục, thẻ phân loại, thư viện ảnh đa phương tiện và tài liệu đính kèm bài viết bằng Spring Data JPA ORM")
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

    @Operation(summary = "Lấy danh sách chuyên mục bài viết", description = "Truy xuất danh sách tất cả các chuyên mục bài viết đang hoạt động, sắp xếp theo thứ tự hiển thị ưu tiên")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Truy xuất danh mục thành công")
    })
    @GetMapping("/article-categories")
    public List<ArticleCategory> getCategories() {
        return categoryRepository.findByIsActiveTrueOrderByDisplayOrderAsc();
    }

    @Operation(summary = "Tra cứu chuyên mục theo đường dẫn định danh (slug)", description = "Truy xuất thông tin chi tiết một chuyên mục bài viết bằng slug chuẩn SEO")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Tìm thấy chuyên mục"),
        @ApiResponse(responseCode = "404", description = "Không tìm thấy chuyên mục với slug tương ứng")
    })
    @GetMapping("/article-categories/{slug}")
    public ArticleCategory getCategoryBySlug(
            @Parameter(description = "Slug chuyên mục (vd: nghien-cuu-cong-nghe, hoc-bong-khen-thuong)", required = true)
            @PathVariable String slug) {
        return categoryRepository
                .findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found: " + slug));
    }

    @Operation(summary = "Lấy danh sách các thẻ bài viết phổ biến", description = "Truy xuất top 20 thẻ bài viết (tags) được gắn nhiều nhất trong hệ thống bài viết đào tạo")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Truy xuất danh sách thẻ thành công")
    })
    @GetMapping("/article-tags")
    public List<ArticleTag> getTags() {
        return tagRepository.findTop20ByOrderByUsageCountDesc();
    }

    @Operation(summary = "Tra cứu thẻ bài viết theo đường dẫn định danh (slug)", description = "Truy xuất thông tin chi tiết của một thẻ bài viết bằng slug")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Tìm thấy thẻ bài viết"),
        @ApiResponse(responseCode = "404", description = "Không tìm thấy thẻ bài viết với slug tương ứng")
    })
    @GetMapping("/article-tags/{slug}")
    public ArticleTag getTagBySlug(
            @Parameter(description = "Slug thẻ bài viết (vd: ai, robotics, kltn)", required = true)
            @PathVariable String slug) {
        return tagRepository
                .findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tag not found: " + slug));
    }

    @Operation(summary = "Lấy thư viện ảnh & media bài viết", description = "Truy xuất danh sách ảnh/video đa phương tiện minh họa, có thể lọc theo mã thông báo/bài viết cụ thể")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Truy xuất danh sách media thành công")
    })
    @GetMapping("/article-media-galleries")
    public List<ArticleMediaGallery> getMediaGalleries(
            @Parameter(description = "Mã bài viết/thông báo để lọc gallery tương ứng (tùy chọn)")
            @RequestParam(required = false) String announcementId) {
        if (announcementId != null && !announcementId.isBlank()) {
            return mediaGalleryRepository.findByAnnouncementIdOrderByDisplayOrderAsc(announcementId);
        }
        return mediaGalleryRepository.findAllByOrderByDisplayOrderAsc();
    }

    @Operation(summary = "Lấy danh mục tệp đính kèm văn bản", description = "Truy xuất danh sách tài liệu, quyết định, biểu mẫu công khai đính kèm bài viết")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Truy xuất danh sách tài liệu đính kèm thành công")
    })
    @GetMapping("/article-attachments")
    public List<ArticleAttachment> getAttachments(
            @Parameter(description = "Mã bài viết/thông báo để lọc tệp đính kèm (tùy chọn)")
            @RequestParam(required = false) String announcementId) {
        if (announcementId != null && !announcementId.isBlank()) {
            return attachmentRepository.findByAnnouncementId(announcementId);
        }
        return attachmentRepository.findByIsPublicTrue();
    }
}
