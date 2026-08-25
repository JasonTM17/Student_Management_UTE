package io.campuscore.restfulapi.thesis.assistant;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import io.campuscore.restfulapi.thesis.assistant.persistence.AssistantJpaGateway;
import io.campuscore.restfulapi.thesis.assistant.persistence.AssistantJpaGateway.JpaRow;
import io.campuscore.restfulapi.thesis.assistant.persistence.AssistantJpaGateway.Parameters;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Profile("persistence")
public class ThesisAssistantRepository {
    private final AssistantJpaGateway jpa;
    private final boolean postgres;

    public ThesisAssistantRepository(AssistantJpaGateway jpa) {
        this.jpa = jpa;
        this.postgres = jpa.isPostgres();
    }

    @Transactional
    public UUID ensureConversation(String ownerId, String requestedId, String locale, int retentionDays) {
        if (requestedId != null && !requestedId.isBlank()) {
            UUID id = UUID.fromString(requestedId);
            Integer count = jpa.queryForObject("SELECT COUNT(*) FROM assistant.chat_conversation WHERE id=:id AND owner_id=:owner AND state IN ('ACTIVE','PENDING') AND expires_at > CURRENT_TIMESTAMP",
                    p().addValue("id", id).addValue("owner", ownerId), Integer.class);
            if (count != null && count == 1) return id;
            throw new IllegalArgumentException("conversation not found");
        }
        UUID id = UUID.randomUUID();
        Instant expiresAt = Instant.now().plus(Math.max(1, retentionDays), ChronoUnit.DAYS);
        jpa.update("INSERT INTO assistant.chat_conversation (id, owner_id, locale, state, expires_at) VALUES (:id,:owner,:locale,'PENDING',:expires)",
                p().addValue("id", id).addValue("owner", ownerId).addValue("locale", normalizeLocale(locale))
                        // Bind a timestamp explicitly so Postgres and H2 share
                        // the same retention behavior through native JPA SQL.
                        .addValue("expires", Timestamp.from(expiresAt)));
        return id;
    }

    @Transactional
    public UUID appendMessage(UUID conversationId, String role, String content, String model, boolean degraded, String reasonCode) {
        UUID id = UUID.randomUUID();
        jpa.update("INSERT INTO assistant.chat_message (id,conversation_id,role,content,model,degraded,reason_code) VALUES (:id,:conversation,:role,:content,:model,:degraded,:reason)",
                p().addValue("id", id).addValue("conversation", conversationId).addValue("role", role).addValue("content", content)
                        .addValue("model", model).addValue("degraded", degraded).addValue("reason", reasonCode));
        jpa.update("UPDATE assistant.chat_conversation SET state=CASE WHEN :role='ASSISTANT' THEN 'ACTIVE' ELSE state END,updated_at=CURRENT_TIMESTAMP WHERE id=:id",
                p().addValue("id", conversationId).addValue("role", role));
        return id;
    }

    @Transactional
    public void appendCitations(UUID messageId, List<ThesisAssistantDtos.Citation> citations) {
        for (ThesisAssistantDtos.Citation citation : citations) {
            jpa.update("INSERT INTO assistant.chat_citation (id,message_id,document_id,slug,title,source,locale,excerpt) VALUES (:id,:message,:document,:slug,:title,:source,:locale,:excerpt)",
                    p().addValue("id", UUID.randomUUID()).addValue("message", messageId)
                            .addValue("document", parseUuid(citation.id())).addValue("slug", citation.slug())
                            .addValue("title", citation.title()).addValue("source", citation.source())
                            .addValue("locale", citation.locale()).addValue("excerpt", citation.excerpt()));
        }
    }

    @Transactional
    public int purgeExpired() {
        List<Conversation> expired = jpa.query("SELECT id,owner_id FROM assistant.chat_conversation WHERE expires_at <= CURRENT_TIMESTAMP AND state <> 'PURGED' ORDER BY expires_at LIMIT 100",
                new Parameters(), (rs, row) -> new Conversation(rs.getObject("id", UUID.class), null, null, null, null, rs.getString("owner_id")));
        int deleted = 0;
        for (Conversation conversation : expired) {
            // Legacy cleanup shares the V12 tables with the turn state
            // machine. Re-check under the conversation lock before deleting:
            // a late/old cleanup pass must never remove a conversation whose
            // provider turn is still RESERVED, SNAPSHOT_READY, or DISPATCHED.
            // The lock order (conversation -> ledger) also matches the V12
            // completion/cancel/purge paths.
            List<UUID> locked = jpa.query("SELECT id FROM assistant.chat_conversation WHERE id=:id AND owner_id=:owner AND expires_at <= CURRENT_TIMESTAMP AND state <> 'PURGED' FOR UPDATE",
                    p().addValue("id", conversation.id()).addValue("owner", conversation.ownerId()),
                    (rs, row) -> rs.getObject("id", UUID.class));
            if (locked.isEmpty()) continue;
            Integer active = jpa.queryForObject("SELECT COUNT(*) FROM assistant.chat_turn_ledger WHERE owner_id=:owner AND conversation_id=:id AND state IN ('RESERVED','SNAPSHOT_READY','DISPATCHED')",
                    p().addValue("id", conversation.id()).addValue("owner", conversation.ownerId()), Integer.class);
            if (active != null && active > 0) continue;
            deleted += deleteConversation(conversation.id(), conversation.ownerId());
        }
        List<BucketKey> oldBuckets = jpa.query("SELECT bucket_date,owner_id,scope FROM assistant.usage_bucket WHERE bucket_date < CURRENT_DATE - 90 ORDER BY bucket_date,owner_id,scope LIMIT 100",
                new Parameters(), (rs, row) -> new BucketKey(rs.getObject("bucket_date", LocalDate.class), rs.getString("owner_id"), rs.getString("scope")));
        for (BucketKey bucket : oldBuckets) {
            jpa.update("DELETE FROM assistant.usage_bucket WHERE bucket_date=:date AND owner_id=:owner AND scope=:scope",
                    p().addValue("date", bucket.date()).addValue("owner", bucket.ownerId()).addValue("scope", bucket.scope()));
        }
        jpa.update("DELETE FROM assistant.chat_turn_ledger WHERE state='PURGED' AND tombstone_until < CURRENT_TIMESTAMP AND NOT EXISTS (SELECT 1 FROM assistant.chat_conversation c WHERE c.id=assistant.chat_turn_ledger.conversation_id)", new Parameters());
        // Registry rows are only fencing evidence while their ledger tombstone
        // exists. Remove terminal registry keys after the same retention point;
        // never remove a still-dispatched handle implicitly.
        jpa.update("DELETE FROM assistant.provider_dispatch_registry r WHERE r.state <> 'DISPATCHED' AND NOT EXISTS (SELECT 1 FROM assistant.chat_turn_ledger l WHERE l.owner_id=r.owner_id AND l.client_request_id=r.client_request_id AND l.lease_generation=r.lease_generation)", new Parameters());
        return deleted;
    }

    @Transactional
    public boolean consumeQuota(String ownerId, int userLimit, int globalLimit) {
        LocalDate today = LocalDate.now(java.time.ZoneOffset.UTC);
        ensureBucket(today, ownerId, "USER");
        ensureBucket(today, "*", "GLOBAL");
        Integer userCount = lockedCount(today, ownerId, "USER");
        Integer globalCount = lockedCount(today, "*", "GLOBAL");
        if (userCount == null || globalCount == null
                || userCount >= Math.max(1, userLimit) || globalCount >= Math.max(1, globalLimit)) {
            return false;
        }
        jpa.update("UPDATE assistant.usage_bucket SET request_count=request_count+1 WHERE bucket_date=:date AND owner_id=:owner AND scope=:scope",
                p().addValue("date", today).addValue("owner", ownerId).addValue("scope", "USER"));
        jpa.update("UPDATE assistant.usage_bucket SET request_count=request_count+1 WHERE bucket_date=:date AND owner_id=:owner AND scope=:scope",
                p().addValue("date", today).addValue("owner", "*").addValue("scope", "GLOBAL"));
        return true;
    }

    private void ensureBucket(LocalDate date, String owner, String scope) {
        String insertBucket = postgres
                ? "INSERT INTO assistant.usage_bucket(bucket_date,owner_id,scope,request_count) VALUES (:date,:owner,:scope,0) ON CONFLICT (bucket_date,owner_id,scope) DO NOTHING"
                : "MERGE INTO assistant.usage_bucket AS target USING (VALUES (:date,:owner,:scope)) AS source(bucket_date,owner_id,scope) ON target.bucket_date=source.bucket_date AND target.owner_id=source.owner_id AND target.scope=source.scope WHEN NOT MATCHED THEN INSERT (bucket_date,owner_id,scope,request_count) VALUES (source.bucket_date,source.owner_id,source.scope,0)";
        jpa.update(insertBucket,
                p().addValue("date", date).addValue("owner", owner).addValue("scope", scope));
    }

    private Integer lockedCount(LocalDate date, String owner, String scope) {
        return jpa.queryForObject("SELECT request_count FROM assistant.usage_bucket WHERE bucket_date=:date AND owner_id=:owner AND scope=:scope FOR UPDATE",
                p().addValue("date", date).addValue("owner", owner).addValue("scope", scope), Integer.class);
    }

    public List<Conversation> conversations(String ownerId) {
        return conversations(ownerId, 20, null).data();
    }

    public List<Message> messages(UUID conversationId, String ownerId) {
        return messagesPage(conversationId, ownerId, 50, null).data();
    }

    @Transactional
    public int deleteConversation(UUID id, String ownerId) {
        int exists = jpa.update("UPDATE assistant.chat_turn_ledger SET state='PURGED',terminal_reason='PURGED',purged_at=CURRENT_TIMESTAMP,tombstone_until=:tombstone WHERE owner_id=:owner AND conversation_id=:id AND state <> 'PURGED'",
                p().addValue("id", id).addValue("owner", ownerId).addValue("tombstone", Timestamp.from(Instant.now().plus(7, ChronoUnit.DAYS))));
        jpa.update("UPDATE assistant.provider_dispatch_registry r SET state='CANCELLED',cancelled_at=CURRENT_TIMESTAMP WHERE r.owner_id=:owner AND EXISTS (SELECT 1 FROM assistant.chat_turn_ledger l WHERE l.owner_id=r.owner_id AND l.client_request_id=r.client_request_id AND l.conversation_id=:id)",
                p().addValue("id", id).addValue("owner", ownerId));
        return jpa.update("DELETE FROM assistant.chat_conversation WHERE id=:id AND owner_id=:owner", p().addValue("id", id).addValue("owner", ownerId));
    }

    public ConversationPage conversations(String ownerId, int requestedLimit, String cursor) {
        int limit = Math.max(1, Math.min(requestedLimit <= 0 ? 20 : requestedLimit, 50));
        Cursor boundary = decodeCursor(cursor);
        String predicate = boundary == null ? "" : " AND (updated_at < :cursorTime OR (updated_at=:cursorTime AND id < :cursorId))";
        Parameters params = p().addValue("owner", ownerId).addValue("limit", limit + 1);
        if (boundary != null) params.addValue("cursorTime", Timestamp.from(boundary.instant())).addValue("cursorId", boundary.id());
        List<Conversation> rows = jpa.query("SELECT id,title,locale,created_at,updated_at,owner_id FROM assistant.chat_conversation WHERE owner_id=:owner AND state='ACTIVE' AND expires_at>CURRENT_TIMESTAMP" + predicate + " ORDER BY updated_at DESC,id DESC LIMIT :limit",
                params, (rs, row) -> new Conversation(rs.getObject("id", UUID.class), rs.getString("title"), rs.getString("locale"), rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant(), rs.getString("owner_id")));
        boolean more = rows.size() > limit;
        if (more) rows = new ArrayList<>(rows.subList(0, limit));
        String next = more && !rows.isEmpty() ? encodeCursor(rows.get(rows.size() - 1).updatedAt(), rows.get(rows.size() - 1).id()) : null;
        return new ConversationPage(List.copyOf(rows), next);
    }

    public MessagePage messagesPage(UUID conversationId, String ownerId, int requestedLimit, String cursor) {
        int limit = Math.max(1, Math.min(requestedLimit <= 0 ? 50 : requestedLimit, 200));
        Cursor boundary = decodeCursor(cursor);
        String predicate = boundary == null ? "" : " AND (m.created_at < :cursorTime OR (m.created_at=:cursorTime AND m.id < :cursorId))";
        Parameters params = p().addValue("id", conversationId).addValue("owner", ownerId).addValue("limit", limit + 1);
        if (boundary != null) params.addValue("cursorTime", Timestamp.from(boundary.instant())).addValue("cursorId", boundary.id());
        List<Message> newestFirst = jpa.query("SELECT m.id,m.role,m.content,m.model,m.degraded,m.reason_code,m.created_at,(SELECT f.rating FROM assistant.chat_message_feedback f WHERE f.message_id=m.id AND f.owner_id=:owner) feedback FROM assistant.chat_message m JOIN assistant.chat_conversation c ON c.id=m.conversation_id WHERE c.id=:id AND c.owner_id=:owner AND c.state='ACTIVE' AND c.expires_at>CURRENT_TIMESTAMP" + predicate + " ORDER BY m.created_at DESC,m.id DESC LIMIT :limit",
                params, (rs, row) -> new Message(rs.getObject("id", UUID.class), rs.getString("role"), rs.getString("content"), rs.getString("model"), rs.getBoolean("degraded"), rs.getString("reason_code"), rs.getTimestamp("created_at").toInstant(), List.of(), rs.getString("feedback")));
        boolean more = newestFirst.size() > limit;
        if (more) newestFirst = new ArrayList<>(newestFirst.subList(0, limit));
        List<Message> ordered = new ArrayList<>(newestFirst);
        java.util.Collections.reverse(ordered);
        hydrateCitations(ordered);
        String next = more && !ordered.isEmpty() ? encodeCursor(ordered.get(0).createdAt(), ordered.get(0).id()) : null;
        return new MessagePage(List.copyOf(ordered), next);
    }

    private void hydrateCitations(List<Message> messages) {
        if (messages.isEmpty()) return;
        List<UUID> ids = messages.stream().map(Message::id).toList();
        Map<UUID, List<ThesisAssistantDtos.Citation>> byMessage = new LinkedHashMap<>();
        jpa.query("SELECT message_id,CAST(document_id AS VARCHAR) id,slug,title,source,locale,excerpt,domain,source_kind,source_id,revision_id,revision_version,snapshot_hash,catalog_entity_type,catalog_entity_id,CAST(catalog_updated_at AS VARCHAR) updated_at FROM assistant.chat_citation WHERE message_id IN (:ids) ORDER BY message_id,ordinal,id",
                new Parameters("ids", ids), (rs, row) -> {
                    UUID messageId = rs.getObject("message_id", UUID.class);
                    byMessage.computeIfAbsent(messageId, ignored -> new ArrayList<>()).add(new ThesisAssistantDtos.Citation(rs.getString("id"), rs.getString("slug"), rs.getString("title"), rs.getString("source"), rs.getString("locale"), rs.getString("excerpt"), rs.getString("domain"), rs.getString("source_kind"), rs.getString("source_id"), parseUuid(rs.getString("revision_id")), nullableInt(rs, "revision_version"), rs.getString("snapshot_hash"), rs.getString("catalog_entity_type"), rs.getString("catalog_entity_id"), rs.getString("updated_at")));
                    return messageId;
                });
        for (int i = 0; i < messages.size(); i++) {
            Message message = messages.get(i);
            messages.set(i, new Message(message.id(), message.role(), message.content(), message.model(), message.degraded(), message.reasonCode(), message.createdAt(), List.copyOf(byMessage.getOrDefault(message.id(), List.of())), message.feedback()));
        }
    }

    private static String encodeCursor(Instant instant, UUID id) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString((instant.toEpochMilli() + ":" + id).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    private static Cursor decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) return null;
        try {
            String value = new String(Base64.getUrlDecoder().decode(cursor), java.nio.charset.StandardCharsets.UTF_8);
            int split = value.indexOf(':');
            if (split < 1) return null;
            return new Cursor(Instant.ofEpochMilli(Long.parseLong(value.substring(0, split))), UUID.fromString(value.substring(split + 1)));
        } catch (RuntimeException ignored) { return null; }
    }
    private static UUID parseUuid(String value) { try { return value == null || value.isBlank() ? null : UUID.fromString(value); } catch (IllegalArgumentException ignored) { return null; } }
    private static Integer nullableInt(JpaRow rs, String column) { int value = rs.getInt(column); return rs.wasNull() ? null : value; }

    public record Conversation(UUID id, String title, String locale, Instant createdAt, Instant updatedAt, @com.fasterxml.jackson.annotation.JsonIgnore String ownerId) {
        public Conversation(UUID id, String title, String locale, Instant createdAt, Instant updatedAt) { this(id, title, locale, createdAt, updatedAt, null); }
    }
    public record Message(UUID id, String role, String content, String model, boolean degraded, String reasonCode, Instant createdAt, List<ThesisAssistantDtos.Citation> citations, String feedback) {
        public Message(UUID id, String role, String content, String model, boolean degraded, String reasonCode, Instant createdAt, List<ThesisAssistantDtos.Citation> citations) {
            this(id, role, content, model, degraded, reasonCode, createdAt, citations, null);
        }
    }
    public record ConversationPage(List<Conversation> data, String nextCursor) { }
    public record MessagePage(List<Message> data, String nextCursor) { }
    private record Cursor(Instant instant, UUID id) { }
    private record BucketKey(LocalDate date, String ownerId, String scope) { }
    private static Parameters p() { return new Parameters(); }
    private static String normalizeLocale(String locale) { return "en".equalsIgnoreCase(locale) ? "en" : "vi"; }
}
