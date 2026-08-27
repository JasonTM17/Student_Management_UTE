package io.campuscore.restfulapi.thesis.assistant;

import io.campuscore.restfulapi.thesis.assistant.ThesisAssistantDtos.Citation;
import io.campuscore.restfulapi.web.DomainException;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.annotation.Transactional;

/** Transaction-owned persistence boundary for the V12 turn state machine. */
@Repository
@Profile("persistence")
public class ThesisAssistantTurnRepository {
    private static final long PRE_DISPATCH_LEASE_SECONDS = 60L;
    private static final long DISPATCH_LEASE_SECONDS = 90L;
    private static final long TOMBSTONE_DAYS = 97L;
    private static final long COMPLETED_RETENTION_DAYS = 90L;
    private final NamedParameterJdbcTemplate jdbc;
    private final boolean postgres;

    public ThesisAssistantTurnRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.postgres = databaseIsPostgres(jdbc);
    }

    @Transactional
    public Reservation reserve(String ownerId, UUID clientRequestId, String requestHash,
            UUID requestedConversationId, String locale, String leaseOwner, int retentionDays) {
        return reserve(ownerId, clientRequestId, requestHash, requestedConversationId, locale,
                leaseOwner, retentionDays, ignored -> { });
    }

    /**
     * Reservation entry point with a generation fence callback.  Recovery can
     * happen inline on a retry, so the caller must fence an in-process worker
     * before the transaction returns and a new generation is exposed.
     */
    @Transactional
    public Reservation reserve(String ownerId, UUID clientRequestId, String requestHash,
            UUID requestedConversationId, String locale, String leaseOwner, int retentionDays,
            Consumer<ExpiredLease> fence) {
        // A retry is also a recovery opportunity. Resolve an expired worker
        // lease before deciding that the idempotency key is still active.
        notifyFence(fence, recoverExpiredByRequest(ownerId, clientRequestId));
        TurnRow existing = findForUpdate(ownerId, clientRequestId);
        if (existing != null) return existingReservation(existing, requestHash, leaseOwner);

        UUID conversationId;
        boolean createdConversation = requestedConversationId == null;
        if (requestedConversationId == null) {
            conversationId = UUID.randomUUID();
            jdbc.update("INSERT INTO assistant.chat_conversation (id,owner_id,locale,state,expires_at) VALUES (:id,:owner,:locale,'PENDING',:expires)",
                    p().addValue("id", conversationId).addValue("owner", ownerId)
                            .addValue("locale", normalizeLocale(locale))
                            .addValue("expires", Timestamp.from(Instant.now().plusSeconds(Math.max(1, retentionDays) * 86_400L))));
        } else {
            List<UUID> owned = jdbc.query("SELECT id FROM assistant.chat_conversation WHERE id=:id AND owner_id=:owner AND state <> 'PURGED' AND expires_at>CURRENT_TIMESTAMP FOR UPDATE",
                    p().addValue("id", requestedConversationId).addValue("owner", ownerId), (rs, ignored) -> rs.getObject("id", UUID.class));
            if (owned.isEmpty()) throw problem(404, "CONVERSATION_NOT_FOUND", "Conversation not found");
            Integer active = jdbc.queryForObject("SELECT COUNT(*) FROM assistant.chat_turn_ledger WHERE owner_id=:owner AND conversation_id=:conversation AND state IN ('RESERVED','SNAPSHOT_READY','DISPATCHED')",
                    p().addValue("owner", ownerId).addValue("conversation", requestedConversationId), Integer.class);
            if (active != null && active > 0) throw problem(409, "TURN_IN_PROGRESS", "Conversation already has an active turn");
            conversationId = requestedConversationId;
        }

        UUID turnId = UUID.randomUUID();
        String insertLedger = "INSERT INTO assistant.chat_turn_ledger (turn_id,owner_id,client_request_id,request_hash,conversation_id,created_conversation,state,lease_owner,lease_generation,lease_expires_at,created_at,updated_at,tombstone_until) VALUES (:turn,:owner,:request,:hash,:conversation,:createdConversation,'RESERVED',:lease,1,:leaseExpires,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,:tombstoneUntil)"
                + (postgres ? " ON CONFLICT DO NOTHING" : "");
        try {
            int inserted = jdbc.update(insertLedger,
                    p().addValue("turn", turnId).addValue("owner", ownerId).addValue("request", clientRequestId)
                            .addValue("hash", requestHash).addValue("conversation", conversationId).addValue("createdConversation", createdConversation).addValue("lease", leaseOwner)
                            .addValue("leaseExpires", timestampAfterSeconds(PRE_DISPATCH_LEASE_SECONDS))
                            .addValue("tombstoneUntil", timestampAfterDays(TOMBSTONE_DAYS)));
            if (inserted == 0) {
                if (createdConversation) jdbc.update("DELETE FROM assistant.chat_conversation WHERE id=:id AND state='PENDING'", p().addValue("id", conversationId));
                TurnRow winner = findForUpdate(ownerId, clientRequestId);
                if (winner != null) return existingReservation(winner, requestHash, leaseOwner);
                Integer active = jdbc.queryForObject("SELECT COUNT(*) FROM assistant.chat_turn_ledger WHERE owner_id=:owner AND conversation_id=:conversation AND state IN ('RESERVED','SNAPSHOT_READY','DISPATCHED')",
                        p().addValue("owner", ownerId).addValue("conversation", conversationId), Integer.class);
                if (active != null && active > 0) throw problem(409, "TURN_IN_PROGRESS", "Conversation already has an active turn");
                throw problem(409, "TURN_RESERVATION_CONFLICT", "Turn reservation conflicted with another request");
            }
        } catch (DuplicateKeyException duplicate) {
            if (createdConversation) jdbc.update("DELETE FROM assistant.chat_conversation WHERE id=:id AND state='PENDING'", p().addValue("id", conversationId));
            TurnRow winner = findForUpdate(ownerId, clientRequestId);
            if (winner != null) return existingReservation(winner, requestHash, leaseOwner);
            Integer active = jdbc.queryForObject("SELECT COUNT(*) FROM assistant.chat_turn_ledger WHERE owner_id=:owner AND conversation_id=:conversation AND state IN ('RESERVED','SNAPSHOT_READY','DISPATCHED')",
                    p().addValue("owner", ownerId).addValue("conversation", conversationId), Integer.class);
            if (active != null && active > 0) throw problem(409, "TURN_IN_PROGRESS", "Conversation already has an active turn");
            throw duplicate;
        }
        return new Reservation(ReservationStatus.NEW, turnId, conversationId, 1L, createdConversation, null, null, false);
    }

    @Transactional
    public boolean markSnapshotReady(UUID turnId, String ownerId, long generation, String snapshotHash) {
        return markSnapshotReady(turnId, ownerId, generation, snapshotHash, ignored -> { });
    }

    @Transactional
    public boolean markSnapshotReady(UUID turnId, String ownerId, long generation, String snapshotHash,
            Consumer<ExpiredLease> fence) {
        int changed = jdbc.update("UPDATE assistant.chat_turn_ledger SET state='SNAPSHOT_READY',source_snapshot_hash=:snapshot,lease_expires_at=:leaseExpires,updated_at=CURRENT_TIMESTAMP WHERE turn_id=:turn AND owner_id=:owner AND lease_generation=:generation AND state='RESERVED' AND lease_expires_at>CURRENT_TIMESTAMP",
                p().addValue("turn", turnId).addValue("owner", ownerId).addValue("generation", generation).addValue("snapshot", snapshotHash)
                        .addValue("leaseExpires", timestampAfterSeconds(PRE_DISPATCH_LEASE_SECONDS)));
        if (changed == 1) return true;
        notifyFence(fence, recoverExpiredByTurn(turnId, ownerId, generation));
        return false;
    }

    @Transactional
    public DispatchDecision dispatch(UUID turnId, String ownerId, long generation, int userLimit, int globalLimit) {
        return dispatch(turnId, ownerId, generation, userLimit, globalLimit, ignored -> { });
    }

    @Transactional
    public DispatchDecision dispatch(UUID turnId, String ownerId, long generation, int userLimit, int globalLimit,
            Consumer<ExpiredLease> fence) {
        ExpiredLease recovered = recoverExpiredByTurn(turnId, ownerId, generation);
        notifyFence(fence, recovered);
        TurnRow row = findForUpdate(turnId, ownerId);
        if (row == null || row.leaseGeneration() != generation) {
            return new DispatchDecision(false, false, recovered == null ? "STALE_LEASE" : recovered.terminalState());
        }
        if (!"SNAPSHOT_READY".equals(row.state())) {
            // A provider handle is a one-shot side effect. A second caller may
            // observe the same generation while the first worker is streaming;
            // never report that already-dispatched state as permission to call
            // the provider again. The terminal state/replay path remains the
            // authority for the original worker.
            return new DispatchDecision(false, row.quotaReserved(),
                    "DISPATCHED".equals(row.state()) ? "ALREADY_DISPATCHED" : row.state());
        }
        LocalDate date = LocalDate.now(ZoneOffset.UTC);
        ensureBucket(date, ownerId, "USER");
        ensureBucket(date, "*", "GLOBAL");
        Integer user = lockedCount(date, ownerId, "USER");
        Integer global = lockedCount(date, "*", "GLOBAL");
        if (user == null || global == null || user >= Math.max(1, userLimit) || global >= Math.max(1, globalLimit)) {
            jdbc.update("UPDATE assistant.chat_turn_ledger SET state='FAILED_PRE_DISPATCH',terminal_reason='QUOTA_EXCEEDED',updated_at=CURRENT_TIMESTAMP WHERE turn_id=:turn AND owner_id=:owner AND state='SNAPSHOT_READY'",
                    p().addValue("turn", turnId).addValue("owner", ownerId));
            return new DispatchDecision(false, false, "QUOTA_EXCEEDED");
        }
        int changed = jdbc.update("UPDATE assistant.chat_turn_ledger SET state='DISPATCHED',dispatched_at=CURRENT_TIMESTAMP,quota_reserved=TRUE,lease_expires_at=:leaseExpires,updated_at=CURRENT_TIMESTAMP WHERE turn_id=:turn AND owner_id=:owner AND lease_generation=:generation AND state='SNAPSHOT_READY' AND lease_expires_at>CURRENT_TIMESTAMP",
                p().addValue("turn", turnId).addValue("owner", ownerId).addValue("generation", generation)
                        .addValue("leaseExpires", timestampAfterSeconds(DISPATCH_LEASE_SECONDS)));
        if (changed != 1) {
            ExpiredLease expired = recoverExpiredByTurn(turnId, ownerId, generation);
            notifyFence(fence, expired);
            return new DispatchDecision(false, false, expired == null ? "STALE_LEASE" : expired.terminalState());
        }
        // The state CAS is deliberately completed before charging the buckets. Both
        // updates are still in this short transaction, so any quota failure rolls the
        // dispatch marker back instead of charging an un-dispatched turn.
        incrementBucket(date, ownerId, "USER");
        incrementBucket(date, "*", "GLOBAL");
        try {
            String insertRegistry = "INSERT INTO assistant.provider_dispatch_registry(owner_id,client_request_id,lease_generation,state) SELECT owner_id,client_request_id,lease_generation,'DISPATCHED' FROM assistant.chat_turn_ledger WHERE turn_id=:turn"
                    + (postgres ? " ON CONFLICT (owner_id,client_request_id,lease_generation) DO NOTHING" : "");
            jdbc.update(insertRegistry,
                    p().addValue("turn", turnId));
        } catch (DuplicateKeyException ignored) {
            // A retried dispatch may already have published the same fenced handle.
        }
        return new DispatchDecision(true, true, "DISPATCHED");
    }

    @Transactional
    public TerminalResult complete(UUID turnId, String ownerId, long generation, String prompt,
            String model, String answer, boolean degraded, String reasonCode, List<Citation> citations) {
        return complete(turnId, ownerId, generation, prompt, model, answer, degraded, reasonCode, citations,
                ignored -> { });
    }

    @Transactional
    public TerminalResult complete(UUID turnId, String ownerId, long generation, String prompt,
            String model, String answer, boolean degraded, String reasonCode, List<Citation> citations,
            Consumer<ExpiredLease> fence) {
        // Completion and purge both acquire the conversation lock before the
        // turn lock. This avoids the ledger->conversation / conversation->ledger
        // inversion that can deadlock a final CAS against a privacy delete.
        TurnRow hint = findForUpdateReadByTurn(turnId, ownerId);
        if (hint == null) throw problem(404, "TURN_NOT_FOUND", "Turn not found");
        lockConversation(hint.conversationId(), ownerId);
        notifyFence(fence, recoverExpiredByTurn(turnId, ownerId, generation));
        TurnRow row = findForUpdate(turnId, ownerId);
        if (row == null) throw problem(404, "TURN_NOT_FOUND", "Turn not found");
        if ("COMPLETED".equals(row.state())) return terminalFromReplay(loadResult(row.resultMessageId(), ownerId, true));
        if ("FAILED_AMBIGUOUS".equals(row.state())) throw problem(409, "FAILED_AMBIGUOUS", "The provider outcome is ambiguous; automatic redispatch is disabled");
        if (!"DISPATCHED".equals(row.state()) && !"SNAPSHOT_READY".equals(row.state())) {
            throw problem(409, "TURN_NOT_ACTIVE", "Turn is no longer active");
        }
        int claimed = jdbc.update("UPDATE assistant.chat_turn_ledger SET state='COMPLETED',terminal_reason=:reason,updated_at=CURRENT_TIMESTAMP WHERE turn_id=:turn AND owner_id=:owner AND lease_generation=:generation AND state IN ('DISPATCHED','SNAPSHOT_READY') AND lease_expires_at>CURRENT_TIMESTAMP",
                p().addValue("turn", turnId).addValue("owner", ownerId).addValue("generation", generation).addValue("reason", reasonCode));
        if (claimed != 1) {
            ExpiredLease expired = recoverExpiredByTurn(turnId, ownerId, generation);
            notifyFence(fence, expired);
            if (expired != null && "FAILED_AMBIGUOUS".equals(expired.terminalState())) {
                throw problem(409, "FAILED_AMBIGUOUS", "The provider outcome is ambiguous; automatic redispatch is disabled");
            }
            throw problem(409, "TURN_TERMINAL_RACE", "Turn terminal state changed");
        }
        UUID userMessage = UUID.randomUUID();
        UUID assistantMessage = UUID.randomUUID();
        jdbc.update("INSERT INTO assistant.chat_message(id,conversation_id,turn_id,ordinal,role,content,model,degraded,reason_code) VALUES (:id,:conversation,:turn,0,'USER',:content,:model,FALSE,'RECEIVED')",
                p().addValue("id", userMessage).addValue("conversation", row.conversationId()).addValue("turn", turnId)
                        .addValue("content", AssistantInputGuard.normalizeMessage(prompt)).addValue("model", model));
        jdbc.update("INSERT INTO assistant.chat_message(id,conversation_id,turn_id,ordinal,role,content,model,degraded,reason_code) VALUES (:id,:conversation,:turn,1,'ASSISTANT',:content,:model,:degraded,:reason)",
                p().addValue("id", assistantMessage).addValue("conversation", row.conversationId()).addValue("turn", turnId)
                        .addValue("content", answer).addValue("model", model).addValue("degraded", degraded).addValue("reason", reasonCode));
        int ordinal = 0;
        for (Citation citation : citations == null ? List.<Citation>of() : citations) {
            jdbc.update("INSERT INTO assistant.chat_citation(id,message_id,document_id,slug,title,source,locale,excerpt,domain,source_kind,source_id,revision_id,revision_version,snapshot_hash,catalog_entity_type,catalog_entity_id,catalog_updated_at,ordinal) VALUES (:id,:message,:document,:slug,:title,:source,:locale,:excerpt,:domain,:kind,:sourceId,:revision,:version,:hash,:entityType,:entityId,:updated,:ordinal)",
                    citationParams(citation, assistantMessage).addValue("ordinal", ordinal++));
        }
        String title = title(prompt);
        jdbc.update("UPDATE assistant.chat_conversation SET state='ACTIVE',title=COALESCE(NULLIF(title,''),:title),updated_at=CURRENT_TIMESTAMP,expires_at=:expires WHERE id=:id AND owner_id=:owner",
                p().addValue("id", row.conversationId()).addValue("owner", ownerId).addValue("title", title)
                        .addValue("expires", timestampAfterDays(COMPLETED_RETENTION_DAYS)));
        jdbc.update("UPDATE assistant.chat_turn_ledger SET result_message_id=:message,updated_at=CURRENT_TIMESTAMP,tombstone_until=:tombstoneUntil WHERE turn_id=:turn AND owner_id=:owner",
                p().addValue("message", assistantMessage).addValue("turn", turnId).addValue("owner", ownerId)
                        .addValue("tombstoneUntil", timestampAfterDays(TOMBSTONE_DAYS)));
        jdbc.update("UPDATE assistant.provider_dispatch_registry SET state='COMPLETED' WHERE owner_id=:owner AND client_request_id=:request AND lease_generation=:generation",
                p().addValue("owner", ownerId).addValue("request", row.clientRequestId()).addValue("generation", generation));
        return new TerminalResult(row.conversationId(), assistantMessage, answer, model, degraded, reasonCode,
                citations == null ? List.of() : List.copyOf(citations), false, "COMPLETED");
    }

    @Transactional
    public CancelResult cancel(UUID turnId, String ownerId, UUID clientRequestId) {
        return cancel(turnId, ownerId, clientRequestId, ignored -> { });
    }

    /** Cancellation callback is deferred until the short CAS transaction commits. */
    @Transactional
    public CancelResult cancel(UUID turnId, String ownerId, UUID clientRequestId,
            Consumer<DispatchHandle> fence) {
        TurnRow hint = findForUpdateReadByTurn(turnId, ownerId);
        if (hint == null) return new CancelResult(false, "TURN_NOT_FOUND");
        lockConversation(hint.conversationId(), ownerId);
        TurnRow row = findForUpdate(turnId, ownerId);
        if (row == null) return new CancelResult(false, "TURN_NOT_FOUND");
        if ("COMPLETED".equals(row.state())) return new CancelResult(false, "COMPLETED");
        if ("PURGED".equals(row.state()) || "CANCELLED".equals(row.state())) return new CancelResult(false, row.state());
        // Expiry recovery owns the terminal transition once a lease has
        // elapsed.  In particular, a dispatched generation must become
        // FAILED_AMBIGUOUS before cancellation can claim the row; otherwise
        // a late Stop request could hide an unknown provider outcome and make
        // the same client key look safely cancellable.
        ExpiredLease expired = recoverExpiredByTurn(turnId, ownerId, row.leaseGeneration());
        if (expired != null) {
            notifyHandleFence(fence, new DispatchHandle(expired.clientRequestId(), expired.leaseGeneration()));
            return new CancelResult(false, expired.terminalState());
        }
        int changed = jdbc.update("UPDATE assistant.chat_turn_ledger SET state='CANCELLED',terminal_reason='CANCELLED',updated_at=CURRENT_TIMESTAMP,tombstone_until=:tombstoneUntil WHERE turn_id=:turn AND owner_id=:owner AND state IN ('RESERVED','SNAPSHOT_READY','DISPATCHED','FAILED_PRE_DISPATCH')",
                p().addValue("turn", turnId).addValue("owner", ownerId).addValue("tombstoneUntil", timestampAfterDays(TOMBSTONE_DAYS)));
        if (changed != 1) return new CancelResult(false, "TERMINAL_RACE");
        // Keep the provider registry fence generation-scoped.  A late cancel
        // for an older lease must never cancel a newer retry that reused the
        // same client request key.
        jdbc.update("UPDATE assistant.provider_dispatch_registry SET state='CANCELLED',cancelled_at=CURRENT_TIMESTAMP WHERE owner_id=:owner AND client_request_id=:request AND lease_generation=:generation AND state='DISPATCHED'",
                p().addValue("owner", ownerId).addValue("request", row.clientRequestId()).addValue("generation", row.leaseGeneration()));
        if (row.conversationId() != null) {
            jdbc.update("DELETE FROM assistant.chat_conversation WHERE id=:id AND owner_id=:owner AND state='PENDING' AND NOT EXISTS (SELECT 1 FROM assistant.chat_message WHERE conversation_id=:id)",
                    p().addValue("id", row.conversationId()).addValue("owner", ownerId));
        }
        notifyHandleFence(fence, new DispatchHandle(row.clientRequestId(), row.leaseGeneration()));
        return new CancelResult(true, "CANCELLED");
    }

    /**
     * Fences every active turn before a conversation's physical privacy delete.
     * The caller uses the returned handles to abort any in-process provider
     * readers; the database CAS remains the authority for completion races.
     */
    @Transactional
    public List<DispatchHandle> purgeConversation(UUID conversation, String ownerId) {
        if (conversation == null) return List.of();
        return purgeConversationInternal(conversation, ownerId, false, (ignored, handle) -> { });
    }

    @Transactional
    public List<DispatchHandle> purgeConversation(UUID conversation, String ownerId,
            BiConsumer<String, DispatchHandle> fence) {
        if (conversation == null) return List.of();
        return purgeConversationInternal(conversation, ownerId, false, fence);
    }

    /** Atomically fences active turns and physically deletes private history. */
    @Transactional
    public List<DispatchHandle> purgeAndDeleteConversation(UUID conversation, String ownerId) {
        if (conversation == null) return List.of();
        return purgeConversationInternal(conversation, ownerId, true, (ignored, handle) -> { });
    }

    @Transactional
    public List<DispatchHandle> purgeAndDeleteConversation(UUID conversation, String ownerId,
            BiConsumer<String, DispatchHandle> fence) {
        if (conversation == null) return List.of();
        return purgeConversationInternal(conversation, ownerId, true, fence);
    }

    /** Batch retention path; each conversation is deleted under the same owner/CAS boundary. */
    @Transactional
    public List<PurgedConversation> purgeExpiredConversations() {
        return purgeExpiredConversations((ignored, handle) -> { });
    }

    @Transactional
    public List<PurgedConversation> purgeExpiredConversations(BiConsumer<String, DispatchHandle> fence) {
        List<ExpiredConversation> expired = jdbc.query(
                "SELECT id,owner_id FROM assistant.chat_conversation WHERE expires_at<=CURRENT_TIMESTAMP AND state<>'PURGED' ORDER BY expires_at LIMIT 100 FOR UPDATE",
                p(), (rs, ignored) -> new ExpiredConversation(rs.getObject("id", UUID.class), rs.getString("owner_id")));
        List<PurgedConversation> deleted = new ArrayList<>();
        for (ExpiredConversation conversation : expired) {
            deleted.add(new PurgedConversation(conversation.id(), conversation.ownerId(),
                    purgeConversationInternal(conversation.id(), conversation.ownerId(), true, fence)));
        }
        return List.copyOf(deleted);
    }

    private List<DispatchHandle> purgeConversationInternal(UUID conversation, String ownerId, boolean deleteConversation,
            BiConsumer<String, DispatchHandle> fence) {
        List<UUID> ownedConversation = jdbc.query("SELECT id FROM assistant.chat_conversation WHERE id=:id AND owner_id=:owner FOR UPDATE",
                p().addValue("id", conversation).addValue("owner", ownerId),
                (rs, ignored) -> rs.getObject("id", UUID.class));
        if (ownedConversation.isEmpty() && deleteConversation) {
            throw problem(404, "CONVERSATION_NOT_FOUND", "Conversation not found");
        }
        List<DispatchHandle> handles = jdbc.query(
                "SELECT client_request_id,lease_generation FROM assistant.chat_turn_ledger WHERE owner_id=:owner AND conversation_id=:conversation AND state IN ('RESERVED','SNAPSHOT_READY','DISPATCHED') FOR UPDATE",
                p().addValue("owner", ownerId).addValue("conversation", conversation),
                (rs, ignored) -> new DispatchHandle(rs.getObject("client_request_id", UUID.class), rs.getLong("lease_generation")));
        jdbc.update("UPDATE assistant.chat_turn_ledger SET state='PURGED',terminal_reason='PURGED',purged_at=CURRENT_TIMESTAMP,tombstone_until=:tombstone WHERE owner_id=:owner AND conversation_id=:conversation AND state<>'PURGED'",
                p().addValue("owner", ownerId).addValue("conversation", conversation).addValue("tombstone", timestampAfterSeconds(7L * 86_400L)));
        jdbc.update("UPDATE assistant.provider_dispatch_registry r SET state='CANCELLED',cancelled_at=CURRENT_TIMESTAMP WHERE r.owner_id=:owner AND EXISTS (SELECT 1 FROM assistant.chat_turn_ledger l WHERE l.owner_id=r.owner_id AND l.client_request_id=r.client_request_id AND l.conversation_id=:conversation)",
                p().addValue("owner", ownerId).addValue("conversation", conversation));
        if (deleteConversation) {
            int deleted = jdbc.update("DELETE FROM assistant.chat_conversation WHERE id=:id AND owner_id=:owner",
                    p().addValue("id", conversation).addValue("owner", ownerId));
            if (deleted != 1) throw problem(404, "CONVERSATION_NOT_FOUND", "Conversation not found");
        }
        if (fence != null) handles.forEach(handle -> notifyHandleFence(fence, ownerId, handle));
        return List.copyOf(handles);
    }

    public ReplayResult replay(UUID turnId, String ownerId) {
        TurnRow row = find(turnId, ownerId);
        if (row == null) throw problem(404, "TURN_NOT_FOUND", "Turn not found");
        return loadResult(row.resultMessageId(), ownerId, true);
    }

    public TurnRow findByRequest(String ownerId, UUID clientRequestId) {
        return findForUpdateRead(ownerId, clientRequestId);
    }

    /**
     * Converts expired leases into explicit terminal states. Pre-dispatch work
     * is retryable without refunding anything; a dispatched provider attempt is
     * permanently ambiguous and is never automatically re-dispatched. The
     * generation bump fences any late worker that still holds the old lease.
     */
    @Transactional
    public List<ExpiredLease> recoverExpiredLeases() {
        return recoverExpiredLeases(ignored -> { });
    }

    @Transactional
    public List<ExpiredLease> recoverExpiredLeases(Consumer<ExpiredLease> fence) {
        List<LeaseRow> rows = jdbc.query(
                "SELECT owner_id,client_request_id,turn_id,lease_generation,state FROM assistant.chat_turn_ledger "
                        + "WHERE state IN ('RESERVED','SNAPSHOT_READY','DISPATCHED') AND lease_expires_at IS NOT NULL "
                        + "AND lease_expires_at<=CURRENT_TIMESTAMP ORDER BY lease_expires_at LIMIT 100 FOR UPDATE",
                p(), (rs, ignored) -> new LeaseRow(rs.getString("owner_id"), rs.getObject("client_request_id", UUID.class),
                        rs.getObject("turn_id", UUID.class), rs.getLong("lease_generation"), rs.getString("state")));
        List<ExpiredLease> recovered = new ArrayList<>();
        for (LeaseRow row : rows) {
            ExpiredLease lease = recoverIfExpired(row);
            if (lease != null) {
                recovered.add(lease);
                notifyFence(fence, lease);
            }
        }
        return List.copyOf(recovered);
    }

    private ExpiredLease recoverExpiredByRequest(String ownerId, UUID clientRequestId) {
        if (ownerId == null || clientRequestId == null) return null;
        List<LeaseRow> rows = jdbc.query(
                "SELECT owner_id,client_request_id,turn_id,lease_generation,state FROM assistant.chat_turn_ledger "
                        + "WHERE owner_id=:owner AND client_request_id=:request AND state IN ('RESERVED','SNAPSHOT_READY','DISPATCHED') "
                        + "AND lease_expires_at IS NOT NULL FOR UPDATE",
                p().addValue("owner", ownerId).addValue("request", clientRequestId),
                (rs, ignored) -> new LeaseRow(rs.getString("owner_id"), rs.getObject("client_request_id", UUID.class),
                        rs.getObject("turn_id", UUID.class), rs.getLong("lease_generation"), rs.getString("state")));
        return rows.isEmpty() ? null : recoverIfExpired(rows.get(0));
    }

    private ExpiredLease recoverExpiredByTurn(UUID turnId, String ownerId, long generation) {
        if (turnId == null || ownerId == null) return null;
        List<LeaseRow> rows = jdbc.query(
                "SELECT owner_id,client_request_id,turn_id,lease_generation,state FROM assistant.chat_turn_ledger "
                        + "WHERE turn_id=:turn AND owner_id=:owner AND lease_generation=:generation "
                        + "AND state IN ('RESERVED','SNAPSHOT_READY','DISPATCHED') AND lease_expires_at IS NOT NULL FOR UPDATE",
                p().addValue("turn", turnId).addValue("owner", ownerId).addValue("generation", generation),
                (rs, ignored) -> new LeaseRow(rs.getString("owner_id"), rs.getObject("client_request_id", UUID.class),
                        rs.getObject("turn_id", UUID.class), rs.getLong("lease_generation"), rs.getString("state")));
        return rows.isEmpty() ? null : recoverIfExpired(rows.get(0));
    }

    private static void notifyFence(Consumer<ExpiredLease> fence, ExpiredLease lease) {
        if (fence != null && lease != null) {
            afterCommit(() -> fence.accept(lease));
        }
    }

    private static void notifyHandleFence(Consumer<DispatchHandle> fence, DispatchHandle handle) {
        if (fence != null && handle != null) {
            afterCommit(() -> fence.accept(handle));
        }
    }

    private static void notifyHandleFence(BiConsumer<String, DispatchHandle> fence, String ownerId,
            DispatchHandle handle) {
        if (fence != null && handle != null) {
            afterCommit(() -> fence.accept(ownerId, handle));
        }
    }

    /**
     * A DB terminal CAS must not wait for a transport/SSE callback while its
     * row locks are held.  Deferring the in-memory fence until commit preserves
     * rollback semantics and removes the DB-lock -> provider-handle lock cycle.
     * Direct unit callers without an active Spring transaction execute it now.
     */
    private static void afterCommit(Runnable callback) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            callback.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                callback.run();
            }
        });
    }

    private ExpiredLease recoverIfExpired(LeaseRow row) {
        String terminalState = "DISPATCHED".equals(row.state()) ? "FAILED_AMBIGUOUS" : "FAILED_PRE_DISPATCH";
        int changed = jdbc.update(
                "UPDATE assistant.chat_turn_ledger SET state=:state,terminal_reason='LEASE_EXPIRED',lease_owner=NULL,"
                        + "lease_generation=lease_generation+:generationBump,lease_expires_at=NULL,updated_at=CURRENT_TIMESTAMP,"
                        + "tombstone_until=:tombstone WHERE turn_id=:turn AND owner_id=:owner AND lease_generation=:generation "
                        + "AND state=:previous AND lease_expires_at<=CURRENT_TIMESTAMP",
                p().addValue("state", terminalState).addValue("generationBump", "DISPATCHED".equals(row.state()) ? 1 : 0)
                        .addValue("tombstone", timestampAfterDays(TOMBSTONE_DAYS))
                        .addValue("turn", row.turnId()).addValue("owner", row.ownerId())
                        .addValue("generation", row.generation()).addValue("previous", row.state()));
        if (changed != 1) return null;
        // Mark the old provider registry key terminal. The in-memory
        // cancellation registry is fenced by the scheduled caller using the
        // returned old generation, while the DB CAS rejects that worker.
        jdbc.update("UPDATE assistant.provider_dispatch_registry SET state='CANCELLED',cancelled_at=CURRENT_TIMESTAMP "
                        + "WHERE owner_id=:owner AND client_request_id=:request AND lease_generation=:generation AND state='DISPATCHED'",
                p().addValue("owner", row.ownerId()).addValue("request", row.clientRequestId())
                        .addValue("generation", row.generation()));
        return new ExpiredLease(row.ownerId(), row.clientRequestId(), row.turnId(), row.generation(), terminalState);
    }

    @Transactional
    public int setFeedback(UUID messageId, String ownerId, String rating, String reason) {
        if (!List.of("UP", "DOWN").contains(rating) || (reason != null && !List.of("HELPFUL", "CLEAR", "INCORRECT", "OUTDATED", "NOT_RELEVANT", "UNSAFE").contains(reason))) {
            throw problem(400, "INVALID_FEEDBACK", "Feedback rating or reason is not supported");
        }
        Integer owned = jdbc.queryForObject("SELECT COUNT(*) FROM assistant.chat_message m JOIN assistant.chat_conversation c ON c.id=m.conversation_id WHERE m.id=:message AND c.owner_id=:owner AND m.role='ASSISTANT'",
                p().addValue("message", messageId).addValue("owner", ownerId), Integer.class);
        if (owned == null || owned != 1) throw problem(404, "MESSAGE_NOT_FOUND", "Assistant message not found");
        int changed = jdbc.update("UPDATE assistant.chat_message_feedback SET rating=:rating,reason=:reason,updated_at=CURRENT_TIMESTAMP WHERE message_id=:message AND owner_id=:owner",
                p().addValue("message", messageId).addValue("owner", ownerId).addValue("rating", rating).addValue("reason", reason));
        if (changed == 0) {
            try {
                String insertFeedback = "INSERT INTO assistant.chat_message_feedback(message_id,owner_id,rating,reason) VALUES (:message,:owner,:rating,:reason)"
                        + (postgres ? " ON CONFLICT (message_id,owner_id) DO NOTHING" : "");
                changed = jdbc.update(insertFeedback,
                        p().addValue("message", messageId).addValue("owner", ownerId).addValue("rating", rating).addValue("reason", reason));
            } catch (DuplicateKeyException duplicate) {
                changed = jdbc.update("UPDATE assistant.chat_message_feedback SET rating=:rating,reason=:reason,updated_at=CURRENT_TIMESTAMP WHERE message_id=:message AND owner_id=:owner",
                        p().addValue("message", messageId).addValue("owner", ownerId).addValue("rating", rating).addValue("reason", reason));
            }
        }
        return changed;
    }

    @Transactional
    public int deleteFeedback(UUID messageId, String ownerId) {
        int changed = jdbc.update("DELETE FROM assistant.chat_message_feedback WHERE message_id=:message AND owner_id=:owner AND :message IN (SELECT m.id FROM assistant.chat_message m JOIN assistant.chat_conversation c ON c.id=m.conversation_id WHERE c.owner_id=:owner AND m.role='ASSISTANT')",
                p().addValue("message", messageId).addValue("owner", ownerId));
        if (changed == 0) throw problem(404, "FEEDBACK_NOT_FOUND", "Feedback not found");
        return changed;
    }

    private Reservation existingReservation(TurnRow row, String requestHash, String leaseOwner) {
        if (!row.requestHash().equals(requestHash)) throw problem(409, "IDEMPOTENCY_CONFLICT", "Request key was already used with a different payload");
        return switch (row.state()) {
            case "COMPLETED" -> new Reservation(ReservationStatus.REPLAY, row.turnId(), row.conversationId(), row.leaseGeneration(), row.createdConversation(), row.resultMessageId(), row.state(), true);
            case "CANCELLED", "PURGED" -> throw problem(410, "TURN_PURGED", "Request key is no longer replayable");
            case "FAILED_AMBIGUOUS" -> new Reservation(ReservationStatus.AMBIGUOUS, row.turnId(), row.conversationId(), row.leaseGeneration(), row.createdConversation(), row.resultMessageId(), row.state(), false);
            case "FAILED_PRE_DISPATCH" -> reacquireFailedPreDispatch(row, leaseOwner);
            default -> new Reservation(ReservationStatus.ACTIVE, row.turnId(), row.conversationId(), row.leaseGeneration(), row.createdConversation(), null, row.state(), false);
        };
    }

    private Reservation reacquireFailedPreDispatch(TurnRow row, String leaseOwner) {
        long nextGeneration = row.leaseGeneration() + 1;
        int changed = jdbc.update("UPDATE assistant.chat_turn_ledger SET state='RESERVED',lease_owner=:lease,lease_generation=:generation,lease_expires_at=:leaseExpires,terminal_reason=NULL,source_snapshot_hash=NULL,quota_reserved=FALSE,updated_at=CURRENT_TIMESTAMP WHERE turn_id=:turn AND owner_id=:owner AND lease_generation=:previous AND state='FAILED_PRE_DISPATCH'",
                p().addValue("turn", row.turnId()).addValue("owner", row.ownerId()).addValue("previous", row.leaseGeneration())
                        .addValue("lease", leaseOwner).addValue("generation", nextGeneration)
                        .addValue("leaseExpires", timestampAfterSeconds(PRE_DISPATCH_LEASE_SECONDS)));
        if (changed != 1) throw problem(409, "TURN_TERMINAL_RACE", "Turn terminal state changed");
        return new Reservation(ReservationStatus.RETRYABLE, row.turnId(), row.conversationId(), nextGeneration, row.createdConversation(), null, "RESERVED", false);
    }

    private TurnRow findForUpdate(String ownerId, UUID requestId) {
        List<TurnRow> rows = jdbc.query("SELECT turn_id,owner_id,client_request_id,request_hash,conversation_id,created_conversation,state,lease_generation,quota_reserved,result_message_id FROM assistant.chat_turn_ledger WHERE owner_id=:owner AND client_request_id=:request FOR UPDATE",
                p().addValue("owner", ownerId).addValue("request", requestId), (rs, row) -> mapTurn(rs, row));
        return rows.isEmpty() ? null : rows.get(0);
    }

    private TurnRow findForUpdate(UUID turnId, String ownerId) {
        List<TurnRow> rows = jdbc.query("SELECT turn_id,owner_id,client_request_id,request_hash,conversation_id,created_conversation,state,lease_generation,quota_reserved,result_message_id FROM assistant.chat_turn_ledger WHERE turn_id=:turn AND owner_id=:owner FOR UPDATE",
                p().addValue("turn", turnId).addValue("owner", ownerId), (rs, row) -> mapTurn(rs, row));
        return rows.isEmpty() ? null : rows.get(0);
    }

    private TurnRow findForUpdateRead(String ownerId, UUID requestId) {
        List<TurnRow> rows = jdbc.query("SELECT turn_id,owner_id,client_request_id,request_hash,conversation_id,created_conversation,state,lease_generation,quota_reserved,result_message_id FROM assistant.chat_turn_ledger WHERE owner_id=:owner AND client_request_id=:request",
                p().addValue("owner", ownerId).addValue("request", requestId), (rs, row) -> mapTurn(rs, row));
        return rows.isEmpty() ? null : rows.get(0);
    }

    private TurnRow find(UUID turnId, String ownerId) { return findForUpdateReadByTurn(turnId, ownerId); }

    private TurnRow findForUpdateReadByTurn(UUID turnId, String ownerId) {
        List<TurnRow> rows = jdbc.query("SELECT turn_id,owner_id,client_request_id,request_hash,conversation_id,created_conversation,state,lease_generation,quota_reserved,result_message_id FROM assistant.chat_turn_ledger WHERE turn_id=:turn AND owner_id=:owner",
                p().addValue("turn", turnId).addValue("owner", ownerId), (rs, row) -> mapTurn(rs, row));
        return rows.isEmpty() ? null : rows.get(0);
    }

    private void lockConversation(UUID conversationId, String ownerId) {
        if (conversationId == null) return;
        jdbc.query("SELECT id FROM assistant.chat_conversation WHERE id=:id AND owner_id=:owner FOR UPDATE",
                p().addValue("id", conversationId).addValue("owner", ownerId),
                (rs, ignored) -> rs.getObject("id", UUID.class));
    }

    private ReplayResult loadResult(UUID messageId, String ownerId, boolean replayed) {
        if (messageId == null) throw problem(409, "TURN_RESULT_UNAVAILABLE", "Turn result is not available");
        List<ReplayResult> rows = jdbc.query("SELECT m.id,m.content,m.model,m.degraded,m.reason_code,m.conversation_id FROM assistant.chat_message m JOIN assistant.chat_conversation c ON c.id=m.conversation_id WHERE m.id=:message AND c.owner_id=:owner AND m.role='ASSISTANT'",
                p().addValue("message", messageId).addValue("owner", ownerId), (rs, ignored) -> new ReplayResult(
                        rs.getObject("conversation_id", UUID.class), rs.getObject("id", UUID.class), rs.getString("content"), rs.getString("model"), rs.getBoolean("degraded"), rs.getString("reason_code"), citations(messageId), replayed, "COMPLETED"));
        if (rows.isEmpty()) throw problem(410, "TURN_PURGED", "Turn result has been purged");
        return rows.get(0);
    }

    private List<Citation> citations(UUID messageId) {
        return jdbc.query("SELECT CAST(document_id AS VARCHAR) id,slug,title,source,locale,excerpt,domain,source_kind,source_id,revision_id,revision_version,snapshot_hash,catalog_entity_type,catalog_entity_id,CAST(catalog_updated_at AS VARCHAR) updated_at FROM assistant.chat_citation WHERE message_id=:message ORDER BY ordinal,id",
                p().addValue("message", messageId), (rs, ignored) -> new Citation(rs.getString("id"), rs.getString("slug"), rs.getString("title"), rs.getString("source"), rs.getString("locale"), rs.getString("excerpt"), rs.getString("domain"), rs.getString("source_kind"), rs.getString("source_id"), uuid(rs.getString("revision_id")), integer(rs, "revision_version"), rs.getString("snapshot_hash"), rs.getString("catalog_entity_type"), rs.getString("catalog_entity_id"), rs.getString("updated_at")));
    }

    private MapSqlParameterSource citationParams(Citation citation, UUID messageId) {
        return p().addValue("id", UUID.randomUUID()).addValue("message", messageId)
                .addValue("document", uuid(citation.id())).addValue("slug", safe(citation.slug()))
                .addValue("title", safe(citation.title())).addValue("source", safe(citation.source()))
                .addValue("locale", safe(citation.locale())).addValue("excerpt", safe(citation.excerpt()))
                .addValue("domain", safe(citation.domain())).addValue("kind", safe(citation.sourceKind()))
                .addValue("sourceId", safe(citation.sourceId())).addValue("revision", citation.revisionId())
                .addValue("version", citation.revisionVersion()).addValue("hash", safe(citation.snapshotHash()))
                .addValue("entityType", safe(citation.entityType())).addValue("entityId", safe(citation.entityId()))
                .addValue("updated", citation.updatedAt());
    }

    private void ensureBucket(LocalDate date, String owner, String scope) {
        try {
            String insertBucket = "INSERT INTO assistant.usage_bucket(bucket_date,owner_id,scope,request_count) VALUES (:date,:owner,:scope,0)"
                    + (postgres ? " ON CONFLICT (bucket_date,owner_id,scope) DO NOTHING" : "");
            jdbc.update(insertBucket,
                    p().addValue("date", date).addValue("owner", owner).addValue("scope", scope));
        } catch (DuplicateKeyException ignored) {
            // Another transaction created the bucket; the caller locks it next.
        }
    }

    private Integer lockedCount(LocalDate date, String owner, String scope) {
        return jdbc.queryForObject("SELECT request_count FROM assistant.usage_bucket WHERE bucket_date=:date AND owner_id=:owner AND scope=:scope FOR UPDATE",
                p().addValue("date", date).addValue("owner", owner).addValue("scope", scope), Integer.class);
    }

    private void incrementBucket(LocalDate date, String owner, String scope) {
        jdbc.update("UPDATE assistant.usage_bucket SET request_count=request_count+1 WHERE bucket_date=:date AND owner_id=:owner AND scope=:scope",
                p().addValue("date", date).addValue("owner", owner).addValue("scope", scope));
    }

    private static TurnRow mapTurn(ResultSet rs, int ignored) throws java.sql.SQLException {
        return new TurnRow(rs.getObject("turn_id", UUID.class), rs.getString("owner_id"), rs.getObject("client_request_id", UUID.class), rs.getString("request_hash"), rs.getObject("conversation_id", UUID.class), rs.getBoolean("created_conversation"), rs.getString("state"), rs.getLong("lease_generation"), rs.getBoolean("quota_reserved"), rs.getObject("result_message_id", UUID.class));
    }

    private static TerminalResult terminalFromReplay(ReplayResult replay) {
        return new TerminalResult(replay.conversationId(), replay.messageId(), replay.answer(), replay.model(),
                replay.degraded(), replay.reasonCode(), replay.citations(), true, replay.terminalStatus());
    }

    private static int integer(ResultSet rs, String column) throws java.sql.SQLException { int value = rs.getInt(column); return rs.wasNull() ? 0 : value; }
    private static UUID uuid(String value) { try { return value == null || value.isBlank() ? null : UUID.fromString(value); } catch (IllegalArgumentException ignored) { return null; } }
    private static String safe(String value) { return value == null ? "" : value; }
    private static Timestamp timestampAfterSeconds(long seconds) { return Timestamp.from(Instant.now().plusSeconds(seconds)); }
    private static Timestamp timestampAfterDays(long days) { return Timestamp.from(Instant.now().plusSeconds(days * 86_400L)); }
    private static String normalizeLocale(String locale) { return "en".equalsIgnoreCase(locale) ? "en" : "vi"; }
    private static boolean databaseIsPostgres(NamedParameterJdbcTemplate jdbc) {
        try {
            org.springframework.jdbc.core.JdbcTemplate template = jdbc.getJdbcTemplate();
            if (template == null) return false;
            String name = template.execute((org.springframework.jdbc.core.ConnectionCallback<String>) connection -> connection.getMetaData().getDatabaseProductName());
            return name != null && name.toLowerCase(java.util.Locale.ROOT).contains("postgres");
        } catch (DataAccessException ignored) {
            return false;
        }
    }
    private static String title(String prompt) { String value = AssistantInputGuard.normalizeMessage(prompt).replaceAll("\\s+", " "); return value.length() <= 80 ? value : value.substring(0, 77) + "..."; }
    private static MapSqlParameterSource p() { return new MapSqlParameterSource(); }
    private static DomainException problem(int status, String code, String message) { return new DomainException(org.springframework.http.HttpStatus.valueOf(status), code, message); }

    public enum ReservationStatus { NEW, REPLAY, ACTIVE, RETRYABLE, AMBIGUOUS }
    public record Reservation(ReservationStatus status, UUID turnId, UUID conversationId, long leaseGeneration, boolean createdConversation, UUID resultMessageId, String terminalState, boolean replayed) { }
    public record DispatchDecision(boolean dispatched, boolean quotaReserved, String reasonCode) { }
    public record CancelResult(boolean cancelled, String status) { }
    public record DispatchHandle(UUID clientRequestId, long leaseGeneration) { }
    public record ExpiredLease(String ownerId, UUID clientRequestId, UUID turnId, long leaseGeneration, String terminalState) { }
    public record ExpiredConversation(UUID id, String ownerId) { }
    public record PurgedConversation(UUID id, String ownerId, List<DispatchHandle> handles) { }
    public record TerminalResult(UUID conversationId, UUID messageId, String answer, String model, boolean degraded, String reasonCode, List<Citation> citations, boolean replayed, String terminalStatus) { }
    public record ReplayResult(UUID conversationId, UUID messageId, String answer, String model, boolean degraded, String reasonCode, List<Citation> citations, boolean replayed, String terminalStatus) { }
    public record TurnRow(UUID turnId, String ownerId, UUID clientRequestId, String requestHash, UUID conversationId, boolean createdConversation, String state, long leaseGeneration, boolean quotaReserved, UUID resultMessageId) { }
    private record LeaseRow(String ownerId, UUID clientRequestId, UUID turnId, long generation, String state) { }
}
