package io.campuscore.restfulapi.thesis.assistant.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import io.campuscore.restfulapi.thesis.assistant.repository.ThesisAssistantPrerequisiteMapRepository;
import io.campuscore.restfulapi.thesis.assistant.rls.AssistantRlsBoundary;

/**
 * Rewrites the bilingual prerequisite-map documents (V97) from live {@code
 * academic."CourseRequirement"} rows whenever they drift. The CATALOG_SYNC
 * boundary binds the session GUCs and wraps the whole sync in one authoring
 * transaction — promote() requires it, and the RLS policies on the knowledge
 * tables only open for this scope.
 */
@Service
@Profile("persistence")
@AssistantRlsBoundary(access = AssistantRlsBoundary.Access.CATALOG_SYNC)
public class PrerequisiteMapSyncService {

    private static final String VI_BODY =
            "## Học phần tiên quyết là gì\n\n"
            + "- Học phần tiên quyết là môn bạn phải **hoàn thành và đạt** (không tính F/W) trước khi được đăng ký môn phía sau; cổng tự động chặn đăng ký khi còn thiếu.\n"
            + "- Bản đồ dưới đây liệt kê đầy đủ theo dữ liệu đăng ký của trường; hỏi theo mã môn (ví dụ: \"tiên quyết của SE421\") sẽ ra đúng danh sách.\n\n"
            + "## Danh sách chi tiết\n\n";
    private static final String VI_NOTES =
            "\n\n## Lưu ý\n\n- Ngưỡng điểm tối thiểu hiện áp dụng mức Đạt (D); môn chưa có điểm công bố chưa được tính là đã hoàn thành.\n"
            + "- Học phần song hành (học cùng kỳ) và quy định học trước chi tiết xem ở mục quy chế đăng ký học phần.";
    private static final String EN_BODY =
            "## What a prerequisite is\n\n"
            + "- A prerequisite is a course you must **complete with a passing grade** (F/W do not count) before registering the follow-up course; the portal blocks the registration automatically when it is missing.\n"
            + "- The map below lists every chain from the registration data; ask by course code (for example \"prerequisites of SE421\") for the exact list.\n\n"
            + "## Detailed list\n\n";
    private static final String EN_NOTES =
            "\n\n## Notes\n\n- The current minimum threshold is a pass (D); a course without a published grade does not count as completed yet.\n"
            + "- Corequisite pairs and the detailed prior-learning rules live in the registration regulations topic.";

    private final ThesisAssistantPrerequisiteMapRepository repository;
    private final SqlKnowledgeReleasePromoter promoter;

    public PrerequisiteMapSyncService(ThesisAssistantPrerequisiteMapRepository repository,
            SqlKnowledgeReleasePromoter promoter) {
        this.repository = repository;
        this.promoter = promoter;
    }

    /** Regenerates both map documents; returns how many were rewritten. */
    public int synchronizeMapDocuments() {
        String viBody = buildBody(true);
        String enBody = buildBody(false);
        int count = 0;
        count += rewriteIfDrifted("catalog-prerequisite-map-vi", viBody);
        count += rewriteIfDrifted("catalog-prerequisite-map-en", enBody);
        return count;
    }

    /**
     * Rewrites one map document when the live requirement rows produce a
     * different body. Returns 1 when a revision was appended (and promoted),
     * 0 when the document already matched.
     */
    private int rewriteIfDrifted(String slug, String body) {
        List<Map<String, Object>> docs = repository.findActiveDocuments(slug);
        if (docs.size() != 1) return 0;
        Map<String, Object> doc = docs.get(0);
        if (body.equals(doc.get("content"))) return 0;

        UUID documentId = (UUID) doc.get("id");
        int nextVersion = repository.maxVersion(documentId) + 1;

        // Archive the superseded revision, append the regenerated one, and
        // refresh the document's published content — the admin publish path
        // does the same, and without it every run would redetect the drift.
        repository.archivePublishedRevisions(documentId);
        repository.insertPublishedRevision(UUID.randomUUID(), documentId, nextVersion, slug, body, doc);

        UUID revisionId = repository.findRevisionId(documentId, nextVersion);
        repository.updateDocumentContent(documentId, body);
        promoter.promote(documentId, revisionId, "prerequisite-map-sync");
        return 1;
    }

    /** Same body the V97 migration generated, rebuilt from live rows. */
    private String buildBody(boolean vi) {
        String list = repository.viRequirementList();
        if (vi) return VI_BODY + list + VI_NOTES;
        String listEn = repository.enRequirementList();
        return EN_BODY + listEn + EN_NOTES;
    }
}
