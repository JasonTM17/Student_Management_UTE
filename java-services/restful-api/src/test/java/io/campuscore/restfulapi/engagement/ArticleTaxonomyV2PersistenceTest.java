package io.campuscore.restfulapi.engagement;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * The v2 article-taxonomy routes must serialize public-safe DTOs instead of JPA entities and
 * must page the two collections that used to scan whole tables. The legacy entity routes stay
 * untouched, so the audit columns they leak are asserted here as the deliberate difference
 * between the two generations (expand phase of the expand-contract migration).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "persistence"})
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:article_taxonomy_v2;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
class ArticleTaxonomyV2PersistenceTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mvc;

    @BeforeEach
    void prepareTaxonomyTables() {
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS \"engagement\"");
        // Every /api/v1/** request passes AccountStateFilter, which reads the account row from
        // the database, so the User table and one ACTIVE user must exist for a token to be served.
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS \"campuscore_auth\"");
        jdbc.execute("DROP TABLE IF EXISTS \"campuscore_auth\".\"User\"");
        jdbc.execute("""
                CREATE TABLE "campuscore_auth"."User" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "email" VARCHAR(320) NOT NULL,
                    "status" VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
                    "mustChangePassword" BOOLEAN NOT NULL DEFAULT FALSE,
                    "createdAt" TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    "updatedAt" TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbc.update("""
                INSERT INTO "campuscore_auth"."User" ("id", "email", "status", "mustChangePassword")
                VALUES ('portal-admin-1', 'portal-admin-1@campuscore.edu', 'ACTIVE', FALSE)
                """);
        // Schema mirrors V19__enterprise_article_ecosystem_and_strict_constraints.sql so the
        // entity queries and the check constraints behave exactly as in production.
        jdbc.execute("DROP TABLE IF EXISTS \"engagement\".\"ArticleCategory\"");
        jdbc.execute("""
                CREATE TABLE "engagement"."ArticleCategory" (
                    "id" VARCHAR(60) PRIMARY KEY,
                    "code" VARCHAR(40) UNIQUE NOT NULL,
                    "nameVi" VARCHAR(120) NOT NULL,
                    "nameEn" VARCHAR(120) NOT NULL,
                    "slug" VARCHAR(80) UNIQUE NOT NULL,
                    "description" VARCHAR(500),
                    "colorTone" VARCHAR(30) NOT NULL DEFAULT 'blue',
                    "iconType" VARCHAR(40) NOT NULL DEFAULT 'newspaper',
                    "displayOrder" INTEGER NOT NULL DEFAULT 0,
                    "isActive" BOOLEAN NOT NULL DEFAULT TRUE,
                    "createdAt" TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    "updatedAt" TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbc.execute("DROP TABLE IF EXISTS \"engagement\".\"ArticleMediaGallery\"");
        jdbc.execute("""
                CREATE TABLE "engagement"."ArticleMediaGallery" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "announcementId" VARCHAR(120) NOT NULL,
                    "mediaUrl" VARCHAR(500) NOT NULL,
                    "thumbnailUrl" VARCHAR(500),
                    "captionVi" VARCHAR(500) NOT NULL,
                    "captionEn" VARCHAR(500),
                    "altText" VARCHAR(255) NOT NULL,
                    "mediaType" VARCHAR(30) NOT NULL DEFAULT 'IMAGE',
                    "aspectRatio" VARCHAR(20) NOT NULL DEFAULT '16:9',
                    "width" INTEGER,
                    "height" INTEGER,
                    "fileSizeBytes" BIGINT,
                    "displayOrder" INTEGER NOT NULL DEFAULT 0,
                    "isCover" BOOLEAN NOT NULL DEFAULT FALSE,
                    "createdAt" TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbc.execute("DROP TABLE IF EXISTS \"engagement\".\"ArticleAttachment\"");
        jdbc.execute("""
                CREATE TABLE "engagement"."ArticleAttachment" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "announcementId" VARCHAR(120) NOT NULL,
                    "fileName" VARCHAR(255) NOT NULL,
                    "fileUrl" VARCHAR(500) NOT NULL,
                    "fileSizeBytes" BIGINT NOT NULL,
                    "mimeType" VARCHAR(120) NOT NULL,
                    "checksumSha256" VARCHAR(64),
                    "downloadCount" INTEGER NOT NULL DEFAULT 0,
                    "isPublic" BOOLEAN NOT NULL DEFAULT TRUE,
                    "createdAt" TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);

        insertCategory("cat-first", "RESEARCH_TECH", "nghien-cuu-cong-nghe", 1);
        insertCategory("cat-second", "AWARDS_HONORS", "hoc-bong-khen-thuong", 2);
        insertCategory("cat-hidden", "ARCHIVED_NEWS", "luu-tru", 3);
        jdbc.update("UPDATE \"engagement\".\"ArticleCategory\" SET \"isActive\" = FALSE WHERE \"id\" = 'cat-hidden'");

        insertGallery("gal-1", "ann-1", 1);
        insertGallery("gal-2", "ann-1", 2);
        insertGallery("gal-3", "ann-1", 3);
        insertGallery("gal-other", "ann-2", 4);
        for (int order = 0; order < 250; order++) {
            insertGallery("gal-big-" + order, "ann-big", order);
        }

        insertAttachment("att-1", "ann-1", 1);
        insertAttachment("att-private", "ann-1", false, 2);
    }

    private void insertCategory(String id, String code, String slug, int displayOrder) {
        jdbc.update("""
                INSERT INTO "engagement"."ArticleCategory" (
                    "id", "code", "nameVi", "nameEn", "slug", "description",
                    "colorTone", "iconType", "displayOrder", "isActive", "createdAt", "updatedAt")
                VALUES (?, ?, ?, ?, ?, ?, 'indigo', 'flask', ?, TRUE,
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, id, code, "Danh mục " + id, "Category " + id, slug, "Mô tả " + id, displayOrder);
    }

    private void insertGallery(String id, String announcementId, int displayOrder) {
        jdbc.update("""
                INSERT INTO "engagement"."ArticleMediaGallery" (
                    "id", "announcementId", "mediaUrl", "thumbnailUrl", "captionVi", "captionEn",
                    "altText", "mediaType", "aspectRatio", "width", "height", "fileSizeBytes",
                    "displayOrder", "isCover", "createdAt")
                VALUES (?, ?, ?, ?, ?, ?, ?, 'IMAGE', '16:9', 1600, 900, 102400, ?, FALSE, CURRENT_TIMESTAMP)
                """, id, announcementId, "https://cdn.campusute.io.vn/" + id + ".jpg",
                "https://cdn.campusute.io.vn/" + id + "-thumb.jpg",
                "Chú thích " + id, "Caption " + id, "Alt " + id, displayOrder);
    }

    private void insertAttachment(String id, String announcementId, int createdAtSecond) {
        insertAttachment(id, announcementId, true, createdAtSecond);
    }

    // Distinct createdAt values keep the paging order of the attachment route deterministic:
    // ArticleAttachment has no displayOrder column, so v2 pages it by creation time.
    private void insertAttachment(
            String id,
            String announcementId,
            boolean isPublic,
            int createdAtSecond) {
        jdbc.update("""
                INSERT INTO "engagement"."ArticleAttachment" (
                    "id", "announcementId", "fileName", "fileUrl", "fileSizeBytes", "mimeType",
                    "checksumSha256", "downloadCount", "isPublic", "createdAt")
                VALUES (?, ?, ?, ?, 204800, 'application/pdf', ?, 7, ?,
                        DATEADD('SECOND', ?, TIMESTAMP '2026-08-20 00:00:00'))
                """, id, announcementId, id + ".pdf", "https://cdn.campusute.io.vn/" + id + ".pdf",
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef", isPublic,
                createdAtSecond);
    }

    /**
     * The taxonomy routes (both generations) sit behind the authenticated baseline of
     * {@code SecurityConfig}; this token carries an active account so the account-state
     * filter admits the request.
     *
     * @return MockMvc post-processor that attaches an active portal admin JWT
     */
    private static RequestPostProcessor portalUser() {
        return jwt().jwt(token -> token
                        .subject("portal-admin-1")
                        .claim("email", "portal-admin-1@campuscore.edu")
                        .claim("roles", List.of("ADMIN")))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    @Test
    @DisplayName("v2 categories return DTOs without the entity audit columns the v1 route leaks")
    void v2CategoriesHideEntityAuditColumns() throws Exception {
        mvc.perform(get("/api/v1/article-taxonomy/v2/categories").with(portalUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value("cat-first"))
                .andExpect(jsonPath("$[0].code").value("RESEARCH_TECH"))
                .andExpect(jsonPath("$[0].slug").value("nghien-cuu-cong-nghe"))
                .andExpect(jsonPath("$[0].nameVi").value("Danh mục cat-first"))
                .andExpect(jsonPath("$[0].displayOrder").value(1))
                .andExpect(jsonPath("$[0].isActive").value(true))
                // Audit internals of ArticleCategory must never reach the wire.
                .andExpect(jsonPath("$[0].createdAt").doesNotExist())
                .andExpect(jsonPath("$[0].updatedAt").doesNotExist());

        // The frozen legacy route still exposes them, which is the contract being retired later.
        mvc.perform(get("/api/v1/article-categories").with(portalUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("cat-first"))
                .andExpect(jsonPath("$[0].updatedAt").exists());
    }

    @Test
    @DisplayName("v2 categories feed is readable anonymously: the homepage labels articles without a login")
    void v2CategoriesFeedIsPublic() throws Exception {
        mvc.perform(get("/api/v1/article-taxonomy/v2/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].code").value("RESEARCH_TECH"))
                .andExpect(jsonPath("$[0].createdAt").doesNotExist());

        // Sibling taxonomy reads keep their authenticated contract.
        mvc.perform(get("/api/v1/article-taxonomy/v2/categories/hoc-bong-khen-thuong"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("v2 category lookup by slug returns the DTO, unknown slug stays 404")
    void v2CategoryBySlugReturnsDto() throws Exception {
        mvc.perform(get("/api/v1/article-taxonomy/v2/categories/hoc-bong-khen-thuong").with(portalUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("cat-second"))
                .andExpect(jsonPath("$.createdAt").doesNotExist())
                .andExpect(jsonPath("$.updatedAt").doesNotExist());

        mvc.perform(get("/api/v1/article-taxonomy/v2/categories/khong-ton-tai").with(portalUser()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("v2 media galleries page by displayOrder inside the house {data, meta} envelope")
    void v2MediaGalleriesArePaged() throws Exception {
        mvc.perform(get("/api/v1/article-taxonomy/v2/media-galleries")
                        .with(portalUser())
                        .queryParam("announcementId", "ann-1")
                        .queryParam("page", "2")
                        .queryParam("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value("gal-3"))
                .andExpect(jsonPath("$.data[0].mediaUrl").value("https://cdn.campusute.io.vn/gal-3.jpg"))
                .andExpect(jsonPath("$.data[0].createdAt").doesNotExist())
                .andExpect(jsonPath("$.meta.total").value(3))
                .andExpect(jsonPath("$.meta.page").value(2))
                .andExpect(jsonPath("$.meta.limit").value(2))
                .andExpect(jsonPath("$.meta.totalPages").value(2));
    }

    @Test
    @DisplayName("v2 media galleries default to 50 rows and cap an oversized page at 200")
    void v2MediaGalleriesCapPageSize() throws Exception {
        mvc.perform(get("/api/v1/article-taxonomy/v2/media-galleries")
                        .with(portalUser())
                        .queryParam("announcementId", "ann-big"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(50))
                .andExpect(jsonPath("$.meta.limit").value(50))
                .andExpect(jsonPath("$.meta.total").value(250))
                .andExpect(jsonPath("$.meta.totalPages").value(5));

        mvc.perform(get("/api/v1/article-taxonomy/v2/media-galleries")
                        .with(portalUser())
                        .queryParam("announcementId", "ann-big")
                        .queryParam("limit", "100000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(200))
                .andExpect(jsonPath("$.meta.limit").value(200))
                .andExpect(jsonPath("$.meta.total").value(250))
                .andExpect(jsonPath("$.meta.totalPages").value(2));
    }

    @Test
    @DisplayName("v2 attachments page public rows and drop checksum, access flag, and audit column")
    void v2AttachmentsHideInternalFields() throws Exception {
        mvc.perform(get("/api/v1/article-taxonomy/v2/attachments")
                        .with(portalUser())
                        .queryParam("announcementId", "ann-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].fileName").value("att-1.pdf"))
                .andExpect(jsonPath("$.data[0].downloadCount").value(7))
                .andExpect(jsonPath("$.data[0].checksumSha256").doesNotExist())
                .andExpect(jsonPath("$.data[0].createdAt").doesNotExist())
                .andExpect(jsonPath("$.meta.total").value(2));

        // Without an article filter only public rows are served, and the page stays bounded.
        mvc.perform(get("/api/v1/article-taxonomy/v2/attachments")
                        .with(portalUser())
                        .queryParam("limit", "9999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value("att-1"))
                .andExpect(jsonPath("$.data[0].isPublic").doesNotExist())
                .andExpect(jsonPath("$.meta.limit").value(200));
    }
}
