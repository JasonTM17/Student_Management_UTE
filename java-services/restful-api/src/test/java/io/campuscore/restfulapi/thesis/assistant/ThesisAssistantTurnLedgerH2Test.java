package io.campuscore.restfulapi.thesis.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.campuscore.restfulapi.thesis.assistant.ThesisAssistantDtos.Citation;
import io.campuscore.restfulapi.web.DomainException;
import java.util.List;
import java.util.UUID;
import java.sql.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** H2 parity oracle for the V12 ledger CAS and fencing invariants. */
@SpringBootTest
@ActiveProfiles({"test", "persistence"})
class ThesisAssistantTurnLedgerH2Test {

    @Autowired private ThesisAssistantTurnRepository turns;
    @Autowired private ThesisAssistantRepository legacyHistory;
    @Autowired private NamedParameterJdbcTemplate jdbc;

    @Test
    void reserveSnapshotDispatchAndCompleteCommitOneUserAndAssistant() {
        String owner = "ledger-owner-" + UUID.randomUUID();
        UUID key = UUID.randomUUID();
        ThesisAssistantTurnRepository.Reservation reservation = turns.reserve(owner, key,
                AssistantInputGuard.canonicalHash("topic", "en", null), null, "en", "test-lease", 90);
        assertEquals(ThesisAssistantTurnRepository.ReservationStatus.NEW, reservation.status());
        assertTrue(turns.markSnapshotReady(reservation.turnId(), owner, reservation.leaseGeneration(), "a".repeat(64)));
        ThesisAssistantTurnRepository.DispatchDecision dispatch = turns.dispatch(reservation.turnId(), owner, reservation.leaseGeneration(), 20, 200);
        assertTrue(dispatch.dispatched());
        ThesisAssistantTurnRepository.TerminalResult result = turns.complete(reservation.turnId(), owner, reservation.leaseGeneration(),
                "topic", "curated-lexical-rag", "grounded answer", true, "PROVIDER_DISABLED", List.of());
        assertEquals("COMPLETED", result.terminalStatus());
        Integer users = jdbc.queryForObject("SELECT COUNT(*) FROM assistant.chat_message WHERE turn_id=:turn AND role='USER'", p("turn", reservation.turnId()), Integer.class);
        Integer assistants = jdbc.queryForObject("SELECT COUNT(*) FROM assistant.chat_message WHERE turn_id=:turn AND role='ASSISTANT'", p("turn", reservation.turnId()), Integer.class);
        assertEquals(1, users);
        assertEquals(1, assistants);
    }

    @Test
    void anAlreadyDispatchedGenerationCannotDispatchProviderTwice() {
        String owner = "duplicate-dispatch-" + UUID.randomUUID();
        UUID key = UUID.randomUUID();
        ThesisAssistantTurnRepository.Reservation reservation = turns.reserve(owner, key,
                reservationHash("duplicate", "en"), null, "en", "lease", 90);
        assertTrue(turns.markSnapshotReady(reservation.turnId(), owner, reservation.leaseGeneration(), "a".repeat(64)));
        assertTrue(turns.dispatch(reservation.turnId(), owner, reservation.leaseGeneration(), 20, 200).dispatched());

        ThesisAssistantTurnRepository.DispatchDecision duplicate = turns.dispatch(
                reservation.turnId(), owner, reservation.leaseGeneration(), 20, 200);
        assertTrue(!duplicate.dispatched());
        assertEquals("ALREADY_DISPATCHED", duplicate.reasonCode());
        assertEquals(1, jdbc.queryForObject(
                "SELECT request_count FROM assistant.usage_bucket WHERE owner_id=:owner AND scope='USER'",
                p("owner", owner), Integer.class));
    }

    @Test
    void failedPreDispatchCanBeReacquiredWithFencedGeneration() {
        String owner = "quota-owner-" + UUID.randomUUID();
        UUID key = UUID.randomUUID();
        ThesisAssistantTurnRepository.Reservation reservation = turns.reserve(owner, key, AssistantInputGuard.canonicalHash("topic", "en", null), null, "en", "lease-a", 90);
        turns.markSnapshotReady(reservation.turnId(), owner, reservation.leaseGeneration(), "b".repeat(64));
        assertEquals("QUOTA_EXCEEDED", turns.dispatch(reservation.turnId(), owner, reservation.leaseGeneration(), 1, 1).reasonCode());
        ThesisAssistantTurnRepository.Reservation retry = turns.reserve(owner, key, reservationHash("topic", "en"), null, "en", "lease-b", 90);
        assertEquals(ThesisAssistantTurnRepository.ReservationStatus.RETRYABLE, retry.status());
        assertEquals(reservation.leaseGeneration() + 1, retry.leaseGeneration());
        assertTrue(turns.markSnapshotReady(retry.turnId(), owner, retry.leaseGeneration(), "c".repeat(64)));
    }

    @Test
    void cancellationWinsAndLeavesNoMessagesOrCitations() {
        String owner = "cancel-owner-" + UUID.randomUUID();
        UUID key = UUID.randomUUID();
        ThesisAssistantTurnRepository.Reservation reservation = turns.reserve(owner, key, reservationHash("cancel", "vi"), null, "vi", "lease", 90);
        assertTrue(turns.markSnapshotReady(reservation.turnId(), owner, reservation.leaseGeneration(), "d".repeat(64)));
        ThesisAssistantTurnRepository.CancelResult cancelled = turns.cancel(reservation.turnId(), owner, key);
        assertTrue(cancelled.cancelled());
        assertEquals(0, count("SELECT COUNT(*) FROM assistant.chat_message WHERE turn_id=:turn", p("turn", reservation.turnId())));
        assertEquals(0, count("SELECT COUNT(*) FROM assistant.chat_citation c JOIN assistant.chat_message m ON m.id=c.message_id WHERE m.turn_id=:turn", p("turn", reservation.turnId())));
    }

    @Test
    void disconnectBeforeWorkerReservationLeavesDurableTombstoneAndBlocksLaterWorker() {
        String owner = "cancel-before-reserve-" + UUID.randomUUID();
        UUID key = UUID.randomUUID();
        String hash = reservationHash("cancel-before-reserve", "en");

        ThesisAssistantTurnRepository.CancelResult cancelled = turns.cancelBeforeReservation(owner, key, hash);

        assertTrue(cancelled.cancelled());
        assertEquals("CANCELLED", jdbc.queryForObject(
                "SELECT state FROM assistant.chat_turn_ledger WHERE owner_id=:owner AND client_request_id=:key",
                p("owner", owner).addValue("key", key), String.class));
        assertEquals(0, count("SELECT COUNT(*) FROM assistant.chat_message WHERE turn_id IN (SELECT turn_id FROM assistant.chat_turn_ledger WHERE owner_id=:owner AND client_request_id=:key)",
                p("owner", owner).addValue("key", key)));

        assertThrows(RuntimeException.class,
                () -> turns.reserve(owner, key, hash, null, "en", "worker-after-disconnect", 90));
        assertEquals(0, count("SELECT COUNT(*) FROM assistant.usage_bucket WHERE owner_id=:owner AND request_count>0",
                p("owner", owner)));
    }

    @Test
    void disconnectWithReusedRequestKeyAndDifferentPayloadCannotCancelOriginalTurn() {
        String owner = "cancel-hash-" + UUID.randomUUID();
        UUID key = UUID.randomUUID();
        String originalHash = reservationHash("original", "en");
        ThesisAssistantTurnRepository.Reservation reservation = turns.reserve(
                owner, key, originalHash, null, "en", "lease", 90);

        DomainException conflict = assertThrows(DomainException.class,
                () -> turns.cancelBeforeReservation(owner, key, reservationHash("different", "en")));

        assertEquals("IDEMPOTENCY_CONFLICT", conflict.code());
        assertEquals("RESERVED", jdbc.queryForObject(
                "SELECT state FROM assistant.chat_turn_ledger WHERE turn_id=:turn",
                p("turn", reservation.turnId()), String.class));
    }

    @Test
    void ownerAndPayloadArePartOfTheIdempotencyFence() {
        String owner = "owner-a-" + UUID.randomUUID();
        String other = "owner-b-" + UUID.randomUUID();
        UUID key = UUID.randomUUID();
        String hash = reservationHash("same", "en");
        ThesisAssistantTurnRepository.Reservation first = turns.reserve(owner, key, hash, null, "en", "lease", 90);
        assertThrows(RuntimeException.class, () -> turns.reserve(owner, key, reservationHash("different", "en"), null, "en", "lease", 90));
        assertEquals(null, turns.findByRequest(other, key));
        assertNotEquals(first.turnId(), UUID.randomUUID());
    }

    @Test
    void citationFailureRollsBackTerminalClaimAndMessages() {
        String owner = "rollback-owner-" + UUID.randomUUID();
        UUID key = UUID.randomUUID();
        ThesisAssistantTurnRepository.Reservation reservation = turns.reserve(owner, key, reservationHash("rollback", "en"), null, "en", "lease", 90);
        turns.markSnapshotReady(reservation.turnId(), owner, reservation.leaseGeneration(), "e".repeat(64));
        assertThrows(NullPointerException.class, () -> turns.complete(reservation.turnId(), owner, reservation.leaseGeneration(), "rollback", "model", "answer", false, "ANSWERED", java.util.Arrays.asList((Citation) null)));
        assertEquals("SNAPSHOT_READY", jdbc.queryForObject("SELECT state FROM assistant.chat_turn_ledger WHERE turn_id=:turn", p("turn", reservation.turnId()), String.class));
        assertEquals(0, count("SELECT COUNT(*) FROM assistant.chat_message WHERE turn_id=:turn", p("turn", reservation.turnId())));
    }

    @Test
    void expiredPreDispatchLeaseIsReacquiredWithANewGeneration() {
        String owner = "expiry-pre-" + UUID.randomUUID();
        UUID key = UUID.randomUUID();
        ThesisAssistantTurnRepository.Reservation reservation = turns.reserve(owner, key,
                reservationHash("expiry", "en"), null, "en", "lease-old", 90);
        jdbc.update("UPDATE assistant.chat_turn_ledger SET lease_expires_at=:expired WHERE turn_id=:turn",
                p("turn", reservation.turnId()).addValue("expired", Timestamp.from(Instant.now().minusSeconds(1))));

        ThesisAssistantTurnRepository.Reservation retry = turns.reserve(owner, key,
                reservationHash("expiry", "en"), null, "en", "lease-new", 90);
        assertEquals(ThesisAssistantTurnRepository.ReservationStatus.RETRYABLE, retry.status());
        assertEquals(reservation.leaseGeneration() + 1, retry.leaseGeneration());
        assertTrue(!turns.markSnapshotReady(retry.turnId(), owner, reservation.leaseGeneration(), "0".repeat(64)));
        assertTrue(turns.markSnapshotReady(retry.turnId(), owner, retry.leaseGeneration(), "f".repeat(64)));
        assertEquals("SNAPSHOT_READY", jdbc.queryForObject(
                "SELECT state FROM assistant.chat_turn_ledger WHERE turn_id=:turn", p("turn", reservation.turnId()), String.class));
    }

    @Test
    void expiredDispatchedLeaseBecomesAmbiguousAndCannotRedispatch() {
        String owner = "expiry-dispatch-" + UUID.randomUUID();
        UUID key = UUID.randomUUID();
        ThesisAssistantTurnRepository.Reservation reservation = turns.reserve(owner, key,
                reservationHash("dispatch expiry", "en"), null, "en", "lease", 90);
        assertTrue(turns.markSnapshotReady(reservation.turnId(), owner, reservation.leaseGeneration(), "1".repeat(64)));
        assertTrue(turns.dispatch(reservation.turnId(), owner, reservation.leaseGeneration(), 20, 200).dispatched());
        jdbc.update("UPDATE assistant.chat_turn_ledger SET lease_expires_at=:expired WHERE turn_id=:turn",
                p("turn", reservation.turnId()).addValue("expired", Timestamp.from(Instant.now().minusSeconds(1))));

        List<ThesisAssistantTurnRepository.ExpiredLease> recovered = turns.recoverExpiredLeases();
        assertTrue(recovered.stream().anyMatch(lease -> lease.turnId().equals(reservation.turnId())
                && "FAILED_AMBIGUOUS".equals(lease.terminalState())));
        assertEquals("FAILED_AMBIGUOUS", jdbc.queryForObject(
                "SELECT state FROM assistant.chat_turn_ledger WHERE turn_id=:turn", p("turn", reservation.turnId()), String.class));
        assertEquals(1, count("SELECT request_count FROM assistant.usage_bucket WHERE owner_id=:owner AND scope='USER'", p("owner", owner)));
        ThesisAssistantTurnRepository.Reservation replay = turns.reserve(owner, key,
                reservationHash("dispatch expiry", "en"), null, "en", "retry", 90);
        assertEquals(ThesisAssistantTurnRepository.ReservationStatus.AMBIGUOUS, replay.status());
    }

    @Test
    void cancelAfterDispatchedExpiryRecoversAmbiguousAndFencesOldGeneration() {
        String owner = "cancel-expiry-" + UUID.randomUUID();
        UUID key = UUID.randomUUID();
        ThesisAssistantTurnRepository.Reservation reservation = turns.reserve(owner, key,
                reservationHash("cancel expiry", "en"), null, "en", "lease", 90);
        assertTrue(turns.markSnapshotReady(reservation.turnId(), owner, reservation.leaseGeneration(), "a".repeat(64)));
        assertTrue(turns.dispatch(reservation.turnId(), owner, reservation.leaseGeneration(), 20, 200).dispatched());

        AssistantCancellationRegistry registry = new AssistantCancellationRegistry();
        registry.register(owner, key, reservation.leaseGeneration());
        jdbc.update("UPDATE assistant.chat_turn_ledger SET lease_expires_at=:expired WHERE turn_id=:turn",
                p("turn", reservation.turnId()).addValue("expired", Timestamp.from(Instant.now().minusSeconds(1))));

        ThesisAssistantTurnRepository.CancelResult cancelled = turns.cancel(reservation.turnId(), owner, key,
                handle -> registry.fence(owner, handle.clientRequestId(), handle.leaseGeneration()));
        assertFalse(cancelled.cancelled());
        assertEquals("FAILED_AMBIGUOUS", cancelled.status());
        assertEquals("FAILED_AMBIGUOUS", jdbc.queryForObject(
                "SELECT state FROM assistant.chat_turn_ledger WHERE turn_id=:turn", p("turn", reservation.turnId()), String.class));
        assertEquals(reservation.leaseGeneration() + 1, jdbc.queryForObject(
                "SELECT lease_generation FROM assistant.chat_turn_ledger WHERE turn_id=:turn", p("turn", reservation.turnId()), Long.class));
        assertEquals(1, count("SELECT request_count FROM assistant.usage_bucket WHERE owner_id=:owner AND scope='USER'", p("owner", owner)));
        assertFalse(registry.emitIfActive(owner, key, reservation.leaseGeneration(),
                () -> { throw new AssertionError("expired dispatched generation emitted after cancel recovery"); }));
        assertEquals(ThesisAssistantTurnRepository.ReservationStatus.AMBIGUOUS,
                turns.reserve(owner, key, reservationHash("cancel expiry", "en"), null, "en", "retry", 90).status());
    }

    @Test
    void directDispatchRetryAfterExpiryCannotRedispatchBeforeTheSweep() {
        String owner = "expiry-direct-" + UUID.randomUUID();
        UUID key = UUID.randomUUID();
        ThesisAssistantTurnRepository.Reservation reservation = turns.reserve(owner, key,
                reservationHash("direct expiry", "en"), null, "en", "lease", 90);
        assertTrue(turns.markSnapshotReady(reservation.turnId(), owner, reservation.leaseGeneration(), "9".repeat(64)));
        assertTrue(turns.dispatch(reservation.turnId(), owner, reservation.leaseGeneration(), 20, 200).dispatched());
        jdbc.update("UPDATE assistant.chat_turn_ledger SET lease_expires_at=:expired WHERE turn_id=:turn",
                p("turn", reservation.turnId()).addValue("expired", Timestamp.from(Instant.now().minusSeconds(1))));

        ThesisAssistantTurnRepository.DispatchDecision staleRetry = turns.dispatch(
                reservation.turnId(), owner, reservation.leaseGeneration(), 20, 200);
        assertTrue(!staleRetry.dispatched());
        assertEquals("FAILED_AMBIGUOUS", staleRetry.reasonCode());
        assertEquals("FAILED_AMBIGUOUS", jdbc.queryForObject(
                "SELECT state FROM assistant.chat_turn_ledger WHERE turn_id=:turn", p("turn", reservation.turnId()), String.class));
    }

    @Test
    void inlineRetryRecoveryFencesTheExpiredProviderGenerationBeforeReturning() {
        String owner = "expiry-inline-fence-" + UUID.randomUUID();
        UUID key = UUID.randomUUID();
        ThesisAssistantTurnRepository.Reservation reservation = turns.reserve(owner, key,
                reservationHash("inline fence", "en"), null, "en", "lease-old", 90);
        assertTrue(turns.markSnapshotReady(reservation.turnId(), owner, reservation.leaseGeneration(), "a".repeat(64)));
        assertTrue(turns.dispatch(reservation.turnId(), owner, reservation.leaseGeneration(), 20, 200).dispatched());

        AssistantCancellationRegistry registry = new AssistantCancellationRegistry();
        registry.register(owner, key, reservation.leaseGeneration());
        jdbc.update("UPDATE assistant.chat_turn_ledger SET lease_expires_at=:expired WHERE turn_id=:turn",
                p("turn", reservation.turnId()).addValue("expired", Timestamp.from(Instant.now().minusSeconds(1))));

        List<ThesisAssistantTurnRepository.ExpiredLease> fenced = new java.util.ArrayList<>();
        ThesisAssistantTurnRepository.Reservation retry = turns.reserve(owner, key,
                reservationHash("inline fence", "en"), null, "en", "lease-new", 90,
                lease -> {
                    fenced.add(lease);
                    registry.fence(lease.ownerId(), lease.clientRequestId(), lease.leaseGeneration());
                });

        assertEquals(ThesisAssistantTurnRepository.ReservationStatus.AMBIGUOUS, retry.status());
        assertEquals(1, fenced.size());
        assertFalse(registry.emitIfActive(owner, key, reservation.leaseGeneration(),
                () -> { throw new AssertionError("expired provider generation emitted after inline recovery"); }));
    }

    @Test
    void physicalConversationDeleteKeepsLedgerTombstoneAndRemovesMessagesAtomically() {
        String owner = "purge-owner-" + UUID.randomUUID();
        UUID key = UUID.randomUUID();
        ThesisAssistantTurnRepository.Reservation reservation = turns.reserve(owner, key,
                reservationHash("purge", "vi"), null, "vi", "lease", 90);
        assertTrue(turns.markSnapshotReady(reservation.turnId(), owner, reservation.leaseGeneration(), "2".repeat(64)));
        assertTrue(turns.dispatch(reservation.turnId(), owner, reservation.leaseGeneration(), 20, 200).dispatched());
        turns.complete(reservation.turnId(), owner, reservation.leaseGeneration(), "purge", "model", "answer", false, "ANSWERED", List.of());

        turns.purgeAndDeleteConversation(reservation.conversationId(), owner);
        assertEquals(0, count("SELECT COUNT(*) FROM assistant.chat_conversation WHERE id=:conversation", p("conversation", reservation.conversationId())));
        assertEquals(0, count("SELECT COUNT(*) FROM assistant.chat_message WHERE turn_id=:turn", p("turn", reservation.turnId())));
        assertEquals("PURGED", jdbc.queryForObject("SELECT state FROM assistant.chat_turn_ledger WHERE turn_id=:turn",
                p("turn", reservation.turnId()), String.class));
        assertThrows(RuntimeException.class, () -> turns.reserve(owner, key,
                reservationHash("purge", "vi"), null, "vi", "retry", 90));
    }

    @Test
    void retentionPurgesExpiredConversationThroughTurnBoundary() {
        String owner = "retention-owner-" + UUID.randomUUID();
        UUID key = UUID.randomUUID();
        ThesisAssistantTurnRepository.Reservation reservation = turns.reserve(owner, key,
                reservationHash("retention", "vi"), null, "vi", "lease", 90);
        jdbc.update("UPDATE assistant.chat_conversation SET expires_at=:expired WHERE id=:conversation",
                p("conversation", reservation.conversationId()).addValue("expired", Timestamp.from(Instant.now().minusSeconds(1))));

        List<ThesisAssistantTurnRepository.PurgedConversation> purged = turns.purgeExpiredConversations();
        assertTrue(purged.stream().anyMatch(item -> item.id().equals(reservation.conversationId())));
        assertEquals(0, count("SELECT COUNT(*) FROM assistant.chat_conversation WHERE id=:conversation", p("conversation", reservation.conversationId())));
        assertEquals("PURGED", jdbc.queryForObject("SELECT state FROM assistant.chat_turn_ledger WHERE turn_id=:turn",
                p("turn", reservation.turnId()), String.class));
    }

    @Test
    void legacyRetentionCannotDeleteAnExpiredConversationWithAnActiveTurn() {
        String owner = "legacy-retention-active-" + UUID.randomUUID();
        UUID key = UUID.randomUUID();
        ThesisAssistantTurnRepository.Reservation reservation = turns.reserve(owner, key,
                reservationHash("legacy active", "vi"), null, "vi", "lease", 90);
        assertTrue(turns.markSnapshotReady(reservation.turnId(), owner, reservation.leaseGeneration(), "b".repeat(64)));
        assertTrue(turns.dispatch(reservation.turnId(), owner, reservation.leaseGeneration(), 20, 200).dispatched());
        jdbc.update("UPDATE assistant.chat_conversation SET expires_at=:expired WHERE id=:conversation",
                p("conversation", reservation.conversationId()).addValue("expired", Timestamp.from(Instant.now().minusSeconds(1))));

        assertEquals(0, legacyHistory.purgeExpired());
        assertEquals(1, count("SELECT COUNT(*) FROM assistant.chat_conversation WHERE id=:conversation",
                p("conversation", reservation.conversationId())));
        assertEquals("DISPATCHED", jdbc.queryForObject("SELECT state FROM assistant.chat_turn_ledger WHERE turn_id=:turn",
                p("turn", reservation.turnId()), String.class));
    }

    private static String reservationHash(String message, String locale) {
        return AssistantInputGuard.canonicalHash(message, locale, null);
    }

    private static MapSqlParameterSource p(String key, Object value) { return new MapSqlParameterSource().addValue(key, value); }
    private int count(String sql, MapSqlParameterSource params) { return jdbc.queryForObject(sql, params, Integer.class); }
}
