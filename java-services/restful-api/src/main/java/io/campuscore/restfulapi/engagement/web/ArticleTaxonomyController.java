package io.campuscore.restfulapi.engagement.web;

import io.campuscore.restfulapi.engagement.domain.ArticleAttachment;
import io.campuscore.restfulapi.engagement.domain.ArticleCategory;
import io.campuscore.restfulapi.engagement.domain.ArticleMediaGallery;
import io.campuscore.restfulapi.engagement.domain.ArticleTag;
import io.campuscore.restfulapi.engagement.repository.ArticleAttachmentRepository;
import io.campuscore.restfulapi.engagement.repository.ArticleCategoryRepository;
import io.campuscore.restfulapi.engagement.repository.ArticleMediaGalleryRepository;
import io.campuscore.restfulapi.engagement.repository.ArticleTagRepository;
import io.campuscore.restfulapi.engagement.web.ArticleTaxonomyDtos.AttachmentDto;
import io.campuscore.restfulapi.engagement.web.ArticleTaxonomyDtos.AttachmentListResponse;
import io.campuscore.restfulapi.engagement.web.ArticleTaxonomyDtos.CategoryDto;
import io.campuscore.restfulapi.engagement.web.ArticleTaxonomyDtos.MediaGalleryDto;
import io.campuscore.restfulapi.engagement.web.ArticleTaxonomyDtos.MediaGalleryListResponse;
import io.campuscore.restfulapi.engagement.web.ArticleTaxonomyDtos.PageMeta;
import io.campuscore.restfulapi.engagement.web.ArticleTaxonomyDtos.TagDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Spring Data JPA ORM REST Controller for Article Taxonomy, Media Gallery, and Attachments.
 *
 * <p>Two generations of routes coexist by design (expand–contract):
 * <ul>
 *   <li>the legacy routes serialize the JPA entities directly and stay frozen for existing
 *       clients — they are removed only in a later breaking release;</li>
 *   <li>the {@code /api/v1/article-taxonomy/v2/*} routes serialize explicit
 *       {@link ArticleTaxonomyDtos} records and page the two unbounded collections
 *       (gallery and attachment scans) through Spring Data {@link PageRequest}.</li>
 * </ul>
 */
@Tag(name = "Article Taxonomy & Media (JPA ORM)", description = "Quản lý danh mục, thẻ phân loại, thư viện ảnh đa phương tiện và tài liệu đính kèm bài viết bằng Spring Data JPA ORM")
@RestController
@Profile("persistence")
@RequestMapping("/api/v1")
public class ArticleTaxonomyController {

    /** Page size applied when a v2 paged route is called without {@code limit}. */
    private static final int DEFAULT_PAGE_SIZE = 50;

    /** Largest page a v2 paged route serves; larger requests are clamped instead of scanned. */
    private static final int MAX_PAGE_SIZE = 200;

    private static final String DEFAULT_PAGE = "1";
    private static final String DEFAULT_PAGE_SIZE_TEXT = "50";

    private final ArticleCategoryRepository categoryRepository;
    private final ArticleTagRepository tagRepository;
    private final ArticleMediaGalleryRepository mediaGalleryRepository;
    private final ArticleAttachmentRepository attachmentRepository;

    /**
     * Wires the controller to the four taxonomy repositories.
     *
     * @param categoryRepository reader of article categories
     * @param tagRepository reader of article tags
     * @param mediaGalleryRepository reader of article media items
     * @param attachmentRepository reader of article attachments
     */
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

    /**
     * Legacy (v1) category feed that serializes the {@link ArticleCategory} entity as-is.
     *
     * @return every active category, ascending by display order, including audit columns
     */
    @Operation(summary = "Lấy danh sách chuyên mục bài viết", description = "Truy xuất danh sách tất cả các chuyên mục bài viết đang hoạt động, sắp xếp theo thứ tự hiển thị ưu tiên")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Truy xuất danh mục thành công")
    })
    @GetMapping("/article-categories")
    public List<ArticleCategory> getCategories() {
        return categoryRepository.findByIsActiveTrueOrderByDisplayOrderAsc();
    }

    /**
     * Legacy (v1) single-category lookup that serializes the {@link ArticleCategory} entity.
     *
     * @param slug SEO slug of the category to look up
     * @return the matching category, including audit columns
     * @throws ResponseStatusException with status 404 when no active or inactive category uses that slug
     */
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

    /**
     * Legacy (v1) tag cloud feed that serializes the {@link ArticleTag} entity as-is.
     *
     * @return the twenty most-used tags, descending by usage count, including audit columns
     */
    @Operation(summary = "Lấy danh sách các thẻ bài viết phổ biến", description = "Truy xuất top 20 thẻ bài viết (tags) được gắn nhiều nhất trong hệ thống bài viết đào tạo")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Truy xuất danh sách thẻ thành công")
    })
    @GetMapping("/article-tags")
    public List<ArticleTag> getTags() {
        return tagRepository.findTop20ByOrderByUsageCountDesc();
    }

    /**
     * Legacy (v1) single-tag lookup that serializes the {@link ArticleTag} entity.
     *
     * @param slug SEO slug of the tag to look up
     * @return the matching tag, including audit columns
     * @throws ResponseStatusException with status 404 when the tag slug is unknown
     */
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

    /**
     * Legacy (v1) gallery feed. It serializes entities and is unbounded when no article is
     * given; prefer {@link #getMediaGalleriesV2(String, int, int)}.
     *
     * @param announcementId optional article id that scopes the gallery to one article
     * @return every matching media item in display order, including audit columns
     */
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

    /**
     * Legacy (v1) attachment feed. It serializes entities and scans every public row when no
     * article is given; prefer {@link #getAttachmentsV2(String, int, int)}.
     *
     * @param announcementId optional article id that scopes the attachments to one article
     * @return every matching attachment, including audit columns and the internal checksum
     */
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

    /**
     * v2 category feed: the same rows as {@link #getCategories()} projected onto the public
     * {@link CategoryDto} record, so entity audit columns never reach the wire.
     *
     * @return every active category as a DTO, ascending by display order
     */
    @Operation(summary = "[v2] Lấy danh sách chuyên mục dạng DTO", description = "Truy xuất danh mục bài viết đang hoạt động dưới dạng bản ghi công khai, không kèm cột kiểm toán (createdAt/updatedAt) của entity")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Truy xuất danh mục thành công")
    })
    @GetMapping("/article-taxonomy/v2/categories")
    public List<CategoryDto> getCategoriesV2() {
        return categoryRepository.findByIsActiveTrueOrderByDisplayOrderAsc().stream()
                .map(CategoryDto::from)
                .toList();
    }

    /**
     * v2 single-category lookup returning a DTO instead of the managed entity.
     *
     * @param slug SEO slug of the category to look up
     * @return the matching category as a DTO, without audit columns
     * @throws ResponseStatusException with status 404 when the category slug is unknown
     */
    @Operation(summary = "[v2] Tra cứu chuyên mục theo slug (DTO)", description = "Truy xuất một chuyên mục bài viết bằng slug chuẩn SEO, trả về bản ghi công khai không lộ entity")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Tìm thấy chuyên mục"),
        @ApiResponse(responseCode = "404", description = "Không tìm thấy chuyên mục với slug tương ứng")
    })
    @GetMapping("/article-taxonomy/v2/categories/{slug}")
    public CategoryDto getCategoryBySlugV2(
            @Parameter(description = "Slug chuyên mục (vd: nghien-cuu-cong-nghe, hoc-bong-khen-thuong)", required = true)
            @PathVariable String slug) {
        return CategoryDto.from(categoryRepository
                .findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found: " + slug)));
    }

    /**
     * v2 tag cloud feed returning {@link TagDto} records.
     *
     * @return the twenty most-used tags as DTOs, descending by usage count
     */
    @Operation(summary = "[v2] Lấy danh sách thẻ bài viết phổ biến (DTO)", description = "Truy xuất top 20 thẻ bài viết được dùng nhiều nhất dưới dạng bản ghi công khai")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Truy xuất danh sách thẻ thành công")
    })
    @GetMapping("/article-taxonomy/v2/tags")
    public List<TagDto> getTagsV2() {
        return tagRepository.findTop20ByOrderByUsageCountDesc().stream()
                .map(TagDto::from)
                .toList();
    }

    /**
     * v2 single-tag lookup returning a DTO instead of the managed entity.
     *
     * @param slug SEO slug of the tag to look up
     * @return the matching tag as a DTO, without the audit column
     * @throws ResponseStatusException with status 404 when the tag slug is unknown
     */
    @Operation(summary = "[v2] Tra cứu thẻ bài viết theo slug (DTO)", description = "Truy xuất một thẻ bài viết bằng slug, trả về bản ghi công khai không lộ entity")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Tìm thấy thẻ bài viết"),
        @ApiResponse(responseCode = "404", description = "Không tìm thấy thẻ bài viết với slug tương ứng")
    })
    @GetMapping("/article-taxonomy/v2/tags/{slug}")
    public TagDto getTagBySlugV2(
            @Parameter(description = "Slug thẻ bài viết (vd: ai, robotics, kltn)", required = true)
            @PathVariable String slug) {
        return TagDto.from(tagRepository
                .findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tag not found: " + slug)));
    }

    /**
     * v2 gallery feed: bounded Spring Data page of {@link MediaGalleryDto} records wrapped in
     * the house {@code {data, meta}} envelope, replacing the unbounded entity scan of
     * {@link #getMediaGalleries(String)}.
     *
     * @param announcementId optional article id that scopes the gallery to one article
     * @param page one-based page index, values below one are clamped to one
     * @param limit desired page size; values below one fall back to the
     * {@value #DEFAULT_PAGE_SIZE}-row default and anything above {@value #MAX_PAGE_SIZE} is capped
     * @return one page of gallery DTOs (ordered by display order) plus pagination metadata
     */
    @Operation(summary = "[v2] Thư viện ảnh & media có phân trang (DTO)", description = "Truy xuất danh sách ảnh/video minh họa theo từng trang (mặc định 50 mục, sắp xếp theo displayOrder), có thể lọc theo mã bài viết")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Truy xuất danh sách media thành công")
    })
    @GetMapping("/article-taxonomy/v2/media-galleries")
    public MediaGalleryListResponse getMediaGalleriesV2(
            @Parameter(description = "Mã bài viết/thông báo để lọc gallery tương ứng (tùy chọn)")
            @RequestParam(required = false) String announcementId,
            @Parameter(description = "Số trang phân trang (bắt đầu từ 1)")
            @RequestParam(defaultValue = DEFAULT_PAGE) int page,
            @Parameter(description = "Số lượng mục trên một trang (tối đa 200)")
            @RequestParam(defaultValue = DEFAULT_PAGE_SIZE_TEXT) int limit) {
        PageRequest pageRequest = pageRequest(page, limit, "displayOrder");
        Page<ArticleMediaGallery> result = announcementId == null || announcementId.isBlank()
                ? mediaGalleryRepository.findAll(pageRequest)
                : mediaGalleryRepository.findByAnnouncementId(announcementId, pageRequest);
        return new MediaGalleryListResponse(
                result.map(MediaGalleryDto::from).getContent(),
                pageMeta(result));
    }

    /**
     * v2 attachment feed: bounded Spring Data page of {@link AttachmentDto} records wrapped in
     * the house {@code {data, meta}} envelope, replacing the unbounded public-attachment scan of
     * {@link #getAttachments(String)}. Attachment has no {@code displayOrder} column, so pages
     * are ordered by creation time.
     *
     * @param announcementId optional article id that scopes the attachments to one article
     * @param page one-based page index, values below one are clamped to one
     * @param limit desired page size; values below one fall back to the
     * {@value #DEFAULT_PAGE_SIZE}-row default and anything above {@value #MAX_PAGE_SIZE} is capped
     * @return one page of attachment DTOs plus pagination metadata
     */
    @Operation(summary = "[v2] Tệp đính kèm có phân trang (DTO)", description = "Truy xuất tài liệu/quyết định/biểu mẫu công khai theo từng trang (mặc định 50 mục, sắp xếp theo thời gian tạo vì entity không có displayOrder), có thể lọc theo mã bài viết")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Truy xuất danh sách tài liệu đính kèm thành công")
    })
    @GetMapping("/article-taxonomy/v2/attachments")
    public AttachmentListResponse getAttachmentsV2(
            @Parameter(description = "Mã bài viết/thông báo để lọc tệp đính kèm (tùy chọn)")
            @RequestParam(required = false) String announcementId,
            @Parameter(description = "Số trang phân trang (bắt đầu từ 1)")
            @RequestParam(defaultValue = DEFAULT_PAGE) int page,
            @Parameter(description = "Số lượng tệp trên một trang (tối đa 200)")
            @RequestParam(defaultValue = DEFAULT_PAGE_SIZE_TEXT) int limit) {
        PageRequest pageRequest = pageRequest(page, limit, "createdAt");
        Page<ArticleAttachment> result = announcementId == null || announcementId.isBlank()
                ? attachmentRepository.findByIsPublicTrue(pageRequest)
                : attachmentRepository.findByAnnouncementId(announcementId, pageRequest);
        return new AttachmentListResponse(
                result.map(AttachmentDto::from).getContent(),
                pageMeta(result));
    }

    /**
     * Builds the Spring Data paging request shared by the v2 collection routes. Out-of-range
     * paging is clamped rather than rejected: unlike the announcement routes, these routes only
     * replace an unbounded full-table scan, so a too-large request must stay bounded instead of
     * turning into a client error.
     *
     * @param page one-based page index supplied by the caller, values below one become one
     * @param limit requested page size; values below one fall back to the
     * {@value #DEFAULT_PAGE_SIZE}-row default and anything above {@value #MAX_PAGE_SIZE} is capped
     * @param sortProperty entity property the page is ordered by, ascending
     * @return the zero-based {@link PageRequest} understood by the repositories
     */
    private static PageRequest pageRequest(int page, int limit, String sortProperty) {
        int safePage = Math.max(page, 1);
        int safeLimit = Math.min(limit < 1 ? DEFAULT_PAGE_SIZE : limit, MAX_PAGE_SIZE);
        return PageRequest.of(safePage - 1, safeLimit, Sort.by(Sort.Order.asc(sortProperty)));
    }

    /**
     * Converts a Spring Data page into the house {@code meta} block used by every paginated
     * read route, reporting the page index back as one-based and the size actually applied.
     *
     * @param page repository page whose totals are projected onto the response envelope
     * @return pagination metadata matching the served page
     */
    private static PageMeta pageMeta(Page<?> page) {
        return new PageMeta(
                page.getTotalElements(),
                page.getNumber() + 1,
                page.getSize(),
                page.getTotalPages());
    }
}
