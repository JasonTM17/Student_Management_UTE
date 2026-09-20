package io.campuscore.restfulapi.engagement.web;

import io.campuscore.restfulapi.engagement.domain.ArticleAttachment;
import io.campuscore.restfulapi.engagement.domain.ArticleCategory;
import io.campuscore.restfulapi.engagement.domain.ArticleMediaGallery;
import io.campuscore.restfulapi.engagement.domain.ArticleTag;
import java.util.List;

/**
 * Wire-format read models for the article taxonomy, media gallery, and attachment routes.
 *
 * <p>The legacy routes serialize JPA entities directly, which couples the public contract to
 * the persistence mapping and leaks audit columns. Every record here is an explicit,
 * public-safe editorial projection: entity audit timestamps ({@code createdAt}/
 * {@code updatedAt}) and internal integrity data ({@code checksumSha256}) are deliberately
 * absent, and no record exposes a managed entity or a lazy association.
 */
public final class ArticleTaxonomyDtos {

    private ArticleTaxonomyDtos() {
    }

    /**
     * A public article category (chuyên mục) as rendered by news feeds and filters; drops the
     * {@code createdAt}/{@code updatedAt} audit columns carried by the {@code ArticleCategory} entity.
     */
    public record CategoryDto(
            String id,
            String code,
            String slug,
            String nameVi,
            String nameEn,
            String description,
            String colorTone,
            String iconType,
            int displayOrder,
            boolean isActive) {

        /**
         * Projects an {@link ArticleCategory} entity onto the public category contract.
         *
         * @param category managed article-category entity read from the persistence layer
         * @return the DTO representation, without audit columns
         */
        public static CategoryDto from(ArticleCategory category) {
            return new CategoryDto(
                    category.getId(),
                    category.getCode(),
                    category.getSlug(),
                    category.getNameVi(),
                    category.getNameEn(),
                    category.getDescription(),
                    category.getColorTone(),
                    category.getIconType(),
                    category.getDisplayOrder(),
                    category.isActive());
        }
    }

    /**
     * A popular article tag (thẻ bài viết) with its usage count; drops the {@code createdAt}
     * audit column carried by the {@code ArticleTag} entity.
     */
    public record TagDto(
            String id,
            String slug,
            String nameVi,
            String nameEn,
            int usageCount) {

        /**
         * Projects an {@link ArticleTag} entity onto the public tag contract.
         *
         * @param tag managed article-tag entity read from the persistence layer
         * @return the DTO representation, without audit columns
         */
        public static TagDto from(ArticleTag tag) {
            return new TagDto(
                    tag.getId(),
                    tag.getSlug(),
                    tag.getNameVi(),
                    tag.getNameEn(),
                    tag.getUsageCount());
        }
    }

    /**
     * One image/video/diagram asset of an article gallery, including the accessibility text and
     * layout hints the reader needs; drops the {@code createdAt} audit column.
     */
    public record MediaGalleryDto(
            String id,
            String announcementId,
            String mediaUrl,
            String thumbnailUrl,
            String captionVi,
            String captionEn,
            String altText,
            String mediaType,
            String aspectRatio,
            Integer width,
            Integer height,
            Long fileSizeBytes,
            int displayOrder,
            boolean isCover) {

        /**
         * Projects an {@link ArticleMediaGallery} entity onto the public gallery-item contract.
         *
         * @param gallery managed media-gallery entity read from the persistence layer
         * @return the DTO representation, without audit columns
         */
        public static MediaGalleryDto from(ArticleMediaGallery gallery) {
            return new MediaGalleryDto(
                    gallery.getId(),
                    gallery.getAnnouncementId(),
                    gallery.getMediaUrl(),
                    gallery.getThumbnailUrl(),
                    gallery.getCaptionVi(),
                    gallery.getCaptionEn(),
                    gallery.getAltText(),
                    gallery.getMediaType(),
                    gallery.getAspectRatio(),
                    gallery.getWidth(),
                    gallery.getHeight(),
                    gallery.getFileSizeBytes(),
                    gallery.getDisplayOrder(),
                    gallery.isCover());
        }
    }

    /**
     * One downloadable document attached to an article. The storage checksum
     * ({@code checksumSha256}), the {@code isPublic} access flag, and the {@code createdAt} audit
     * column stay server-side: the route already guarantees public rows and the checksum is an
     * internal integrity artifact, not reader-facing metadata.
     */
    public record AttachmentDto(
            String id,
            String announcementId,
            String fileName,
            String fileUrl,
            long fileSizeBytes,
            String mimeType,
            int downloadCount) {

        /**
         * Projects an {@link ArticleAttachment} entity onto the public attachment contract.
         *
         * @param attachment managed article-attachment entity read from the persistence layer
         * @return the DTO representation, without checksum, access flag, or audit columns
         */
        public static AttachmentDto from(ArticleAttachment attachment) {
            return new AttachmentDto(
                    attachment.getId(),
                    attachment.getAnnouncementId(),
                    attachment.getFileName(),
                    attachment.getFileUrl(),
                    attachment.getFileSizeBytes(),
                    attachment.getMimeType(),
                    attachment.getDownloadCount());
        }
    }

    /**
     * Pagination metadata shared by the paged list responses of this module, matching the
     * house {@code PageMeta} shape used by the announcement and academic read routes.
     *
     * @param total total number of rows matching the query
     * @param page one-based page index actually served
     * @param limit page size actually applied after clamping to the route maximum
     * @param totalPages number of pages available for the applied limit
     */
    public record PageMeta(long total, int page, int limit, int totalPages) {
    }

    /**
     * Paged media-gallery response: the {@code {data, meta}} envelope used by every other
     * paginated read route in this API.
     */
    public record MediaGalleryListResponse(List<MediaGalleryDto> data, PageMeta meta) {
    }

    /**
     * Paged attachment response using the same {@code {data, meta}} envelope as
     * {@link MediaGalleryListResponse}.
     */
    public record AttachmentListResponse(List<AttachmentDto> data, PageMeta meta) {
    }
}
