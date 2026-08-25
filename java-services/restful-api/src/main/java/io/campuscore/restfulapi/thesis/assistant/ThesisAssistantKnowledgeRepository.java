package io.campuscore.restfulapi.thesis.assistant;

import io.campuscore.restfulapi.thesis.assistant.persistence.AssistantKnowledgeRevisionEntity;
import io.campuscore.restfulapi.thesis.assistant.persistence.AssistantKnowledgeRevisionJpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

/** JPA-backed lexical retrieval over reviewed, published knowledge revisions. */
@Repository
@Profile("persistence")
public class ThesisAssistantKnowledgeRepository {

    private final AssistantKnowledgeRevisionJpaRepository revisions;

    public ThesisAssistantKnowledgeRepository(AssistantKnowledgeRevisionJpaRepository revisions) {
        this.revisions = revisions;
    }

    public List<KnowledgeDocument> search(String locale, List<String> terms, int limit) {
        List<String> usableTerms = terms.stream()
                .filter(term -> term.length() >= 2)
                .map(term -> term.toLowerCase(Locale.ROOT))
                .distinct()
                .limit(8)
                .toList();
        if (usableTerms.isEmpty() || limit <= 0) {
            return List.of();
        }

        String requestedLocale = "en".equalsIgnoreCase(locale) ? "en" : "vi";
        return revisions.findPublicByLocale(requestedLocale).stream()
                .filter(revision -> containsAnyTerm(revision, usableTerms))
                .limit(limit)
                .map(ThesisAssistantKnowledgeRepository::map)
                .toList();
    }

    private static boolean containsAnyTerm(
            AssistantKnowledgeRevisionEntity revision,
            List<String> terms) {
        String searchable = (revision.getTitle() + " " + revision.getContent())
                .toLowerCase(Locale.ROOT);
        return terms.stream().anyMatch(searchable::contains);
    }

    private static KnowledgeDocument map(AssistantKnowledgeRevisionEntity revision) {
        return new KnowledgeDocument(
                revision.getDocumentId().toString(),
                revision.getSlug(),
                revision.getLocale(),
                revision.getTitle(),
                revision.getContent(),
                revision.getSource(),
                null,
                null,
                null,
                revision.getId(),
                revision.getVersion());
    }

    public record KnowledgeDocument(
            String id,
            String slug,
            String locale,
            String title,
            String content,
            String source,
            String catalogEntityType,
            String catalogEntityId,
            Instant catalogUpdatedAt,
            UUID revisionId,
            Integer revisionVersion) {
        public KnowledgeDocument(String id, String slug, String locale, String title, String content, String source) {
            this(id, slug, locale, title, content, source, null, null, null, null, null);
        }

        public KnowledgeDocument(
                String id,
                String slug,
                String locale,
                String title,
                String content,
                String source,
                String catalogEntityType,
                String catalogEntityId,
                Instant catalogUpdatedAt) {
            this(
                    id,
                    slug,
                    locale,
                    title,
                    content,
                    source,
                    catalogEntityType,
                    catalogEntityId,
                    catalogUpdatedAt,
                    null,
                    null);
        }
    }
}
