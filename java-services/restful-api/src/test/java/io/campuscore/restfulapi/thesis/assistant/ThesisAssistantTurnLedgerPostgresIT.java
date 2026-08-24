package io.campuscore.restfulapi.thesis.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import io.campuscore.restfulapi.web.DomainException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * PostgreSQL authority tests. They are intentionally enabled only when the
 * caller supplies an isolated ephemeral JDBC URL; no developer volume is ever
 * selected implicitly.
 */
@EnabledIfEnvironmentVariable(named = "ASSISTANT_POSTGRES_URL", matches = "jdbc:postgresql:.+")
@SpringBootTest(properties = {
        "spring.flyway.locations=classpath:db/migration",
        "deepseek.enabled=false",
        "spring.jpa.hibernate.ddl-auto=none"
})
@ActiveProfiles({"test", "persistence"})
class ThesisAssistantTurnLedgerPostgresIT {
    @Autowired ThesisAssistantTurnRepository turns;
    @Autowired ThesisAssistantRepository legacyHistory;
    @Autowired NamedParameterJdbcTemplate jdbc;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("ASSISTANT_POSTGRES_URL"));
        registry.add("spring.datasource.username", () -> valueOr("ASSISTANT_POSTGRES_USER", "postgres"));
        registry.add("spring.datasource.password", () -> valueOr("ASSISTANT_POSTGRES_PASSWORD", "postgres"));
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
    }

    @Test
    void sameKeyConcurrentPersistsOneExchangeAndOneQuotaReservation() throws Exception {
        String owner = "pg-concurrent-" + UUID.randomUUID();
        UUID request = UUID.randomUUID();
        String hash = hash(request);
        CountDownLatch ready = new CountDownLatch(8);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Future<ThesisAssistantTurnRepository.Reservation>> futures = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                assertTrue(start.await(10, TimeUnit.SECONDS));
                return tx(() -> turns.reserve(owner, request, hash, null, "vi", "pg-worker-" + UUID.randomUUID(), 90));
            }));
        }
        assertTrue(ready.await(10, TimeUnit.SECONDS));
        start.countDown();
        for (Future<?> future : futures) future.get(20, TimeUnit.SECONDS);
        pool.shutdownNow();

        assertEquals(1, count("SELECT COUNT(*) FROM assistant.chat_turn_ledger WHERE owner_id=:owner", owner));
        assertEquals(1, count("SELECT COUNT(*) FROM assistant.chat_conversation WHERE owner_id=:owner", owner));
        ThesisAssistantTurnRepository.TurnRow row = turns.findByRequest(owner, request);
        assertNotNull(row);
        int globalBefore = count("SELECT request_count FROM assistant.usage_bucket WHERE owner_id=:owner AND scope='GLOBAL'", "*");
        assertTrue(tx(() -> turns.markSnapshotReady(row.turnId(), owner, row.leaseGeneration(), "a".repeat(64))));
        assertTrue(tx(() -> turns.dispatch(row.turnId(), owner, row.leaseGeneration(), 20, 200)).dispatched());
        tx(() -> turns.complete(row.turnId(), owner, row.leaseGeneration(), "prompt", "model", "answer", false, "ANSWERED", List.of()));
        assertEquals(1, count("SELECT request_count FROM assistant.usage_bucket WHERE owner_id=:owner AND scope='USER'", owner));
        assertEquals(globalBefore + 1, count("SELECT request_count FROM assistant.usage_bucket WHERE owner_id=:owner AND scope='GLOBAL'", "*"));
        assertEquals(2, count("SELECT COUNT(*) FROM assistant.chat_message m JOIN assistant.chat_turn_ledger l ON l.turn_id=m.turn_id WHERE l.owner_id=:owner", owner));
    }

    @Test
    void dispatchedGenerationIsOneShotEvenWhenDispatchIsRetried() {
        String owner = "pg-dispatch-once-" + UUID.randomUUID();
        UUID request = UUID.randomUUID();
        var reservation = tx(() -> turns.reserve(owner, request, hash(request), null, "vi", "once", 90));
        assertTrue(tx(() -> turns.markSnapshotReady(reservation.turnId(), owner, reservation.leaseGeneration(), hash(request))));
        assertTrue(tx(() -> turns.dispatch(reservation.turnId(), owner, reservation.leaseGeneration(), 20, 200)).dispatched());

        var duplicate = tx(() -> turns.dispatch(reservation.turnId(), owner, reservation.leaseGeneration(), 20, 200));
        assertTrue(!duplicate.dispatched());
        assertEquals("ALREADY_DISPATCHED", duplicate.reasonCode());
        assertEquals(1, count("SELECT request_count FROM assistant.usage_bucket WHERE owner_id=:owner AND scope='USER'", owner));
    }

    @Test
    void ownerScopedUuidCannotCrossCancel() {
        UUID request = UUID.randomUUID();
        var a = tx(() -> turns.reserve("pg-owner-a-" + request, request, hash(request), null, "vi", "a", 90));
        var b = tx(() -> turns.reserve("pg-owner-b-" + request, request, hash(request), null, "vi", "b", 90));
        var cancelled = tx(() -> turns.cancel(a.turnId(), a.createdConversation() ? "pg-owner-a-" + request : "", request));
        assertTrue(cancelled.cancelled());
        var bRow = turns.findByRequest("pg-owner-b-" + request, request);
        assertEquals("RESERVED", bRow.state());
    }

    @Test
    void twentyOneAttemptsAcceptOnlyTwentyUnderUserQuota() {
        String owner = "pg-quota-" + UUID.randomUUID();
        int dispatched = 0;
        int quotaRejected = 0;
        for (int i = 0; i < 21; i++) {
            final int attempt = i;
            UUID request = UUID.randomUUID();
            var reservation = tx(() -> turns.reserve(owner, request, hash(request), null, "vi", "quota-" + attempt, 90));
            tx(() -> turns.markSnapshotReady(reservation.turnId(), owner, reservation.leaseGeneration(), hash(request)));
            var decision = tx(() -> turns.dispatch(reservation.turnId(), owner, reservation.leaseGeneration(), 20, 200));
            if (decision.dispatched()) dispatched++;
            if ("QUOTA_EXCEEDED".equals(decision.reasonCode())) quotaRejected++;
        }
        assertEquals(20, dispatched);
        assertEquals(1, quotaRejected);
        assertEquals(20, count("SELECT request_count FROM assistant.usage_bucket WHERE owner_id=:owner AND scope='USER'", owner));
    }

    @Test
    void cancellationAndCompletionHaveOneTerminalWinner() throws Exception {
        String owner = "pg-race-" + UUID.randomUUID();
        UUID request = UUID.randomUUID();
        var reservation = tx(() -> turns.reserve(owner, request, hash(request), null, "vi", "race", 90));
        tx(() -> turns.markSnapshotReady(reservation.turnId(), owner, reservation.leaseGeneration(), hash(request)));
        tx(() -> turns.dispatch(reservation.turnId(), owner, reservation.leaseGeneration(), 20, 200));
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Future<?> cancel = pool.submit(() -> { await(start); return tx(() -> turns.cancel(reservation.turnId(), owner, request)); });
        Future<?> complete = pool.submit(() -> { await(start); return tx(() -> turns.complete(reservation.turnId(), owner, reservation.leaseGeneration(), "prompt", "model", "answer", false, "ANSWERED", List.of())); });
        start.countDown();
        try { cancel.get(20, TimeUnit.SECONDS); } catch (Exception ignored) { }
        try { complete.get(20, TimeUnit.SECONDS); } catch (Exception ignored) { }
        pool.shutdownNow();
        String state = jdbc.queryForObject("SELECT state FROM assistant.chat_turn_ledger WHERE turn_id=:turn", java.util.Map.of("turn", reservation.turnId()), String.class);
        assertTrue("CANCELLED".equals(state) || "COMPLETED".equals(state));
        Integer messageCount = jdbc.queryForObject("SELECT COUNT(*) FROM assistant.chat_message WHERE turn_id=:turn", java.util.Map.of("turn", reservation.turnId()), Integer.class);
        int messages = messageCount == null ? 0 : messageCount;
        assertTrue(messages == 0 || messages == 2);
    }

    @Test
    void completionAndPhysicalDeleteShareConversationFirstLockOrder() throws Exception {
        String owner = "pg-delete-race-" + UUID.randomUUID();
        UUID request = UUID.randomUUID();
        var reservation = tx(() -> turns.reserve(owner, request, hash(request), null, "vi", "delete-race", 90));
        assertTrue(tx(() -> turns.markSnapshotReady(reservation.turnId(), owner, reservation.leaseGeneration(), hash(request))));
        assertTrue(tx(() -> turns.dispatch(reservation.turnId(), owner, reservation.leaseGeneration(), 20, 200)).dispatched());

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Future<?> completion = pool.submit(() -> {
            await(start);
            try {
                return turns.complete(reservation.turnId(), owner, reservation.leaseGeneration(),
                        "delete race", "model", "answer", false, "ANSWERED", List.of());
            } catch (DomainException expectedTerminalRace) {
                assertTrue(List.of("TURN_NOT_FOUND", "TURN_NOT_ACTIVE", "TURN_TERMINAL_RACE", "FAILED_AMBIGUOUS")
                        .contains(expectedTerminalRace.code()));
                return null;
            }
        });
        Future<?> purge = pool.submit(() -> {
            await(start);
            return turns.purgeAndDeleteConversation(reservation.conversationId(), owner);
        });
        start.countDown();

        // A lock-order regression manifests as a timeout here. Do not swallow
        // arbitrary database failures: only the documented terminal race is
        // accepted when the purge wins the conversation-first CAS.
        completion.get(10, TimeUnit.SECONDS);
        purge.get(10, TimeUnit.SECONDS);
        pool.shutdownNow();

        String state = jdbc.queryForObject("SELECT state FROM assistant.chat_turn_ledger WHERE turn_id=:turn",
                java.util.Map.of("turn", reservation.turnId()), String.class);
        assertTrue("PURGED".equals(state) || "COMPLETED".equals(state));
    }

    @Test
    void expiredDispatchedLeaseIsFencedAndMarkedAmbiguousWithoutRefund() {
        String owner = "pg-expiry-" + UUID.randomUUID();
        UUID request = UUID.randomUUID();
        var reservation = tx(() -> turns.reserve(owner, request, hash(request), null, "vi", "expiry", 90));
        assertTrue(tx(() -> turns.markSnapshotReady(reservation.turnId(), owner, reservation.leaseGeneration(), hash(request))));
        assertTrue(tx(() -> turns.dispatch(reservation.turnId(), owner, reservation.leaseGeneration(), 20, 200)).dispatched());
        jdbc.update("UPDATE assistant.chat_turn_ledger SET lease_expires_at=:expired WHERE turn_id=:turn",
                new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
                        .addValue("turn", reservation.turnId())
                        .addValue("expired", Timestamp.from(Instant.now().minusSeconds(1))));

        List<ThesisAssistantTurnRepository.ExpiredLease> recovered = tx(() -> turns.recoverExpiredLeases());
        assertTrue(recovered.stream().anyMatch(lease -> lease.turnId().equals(reservation.turnId())
                && "FAILED_AMBIGUOUS".equals(lease.terminalState())));
        assertEquals("FAILED_AMBIGUOUS", jdbc.queryForObject(
                "SELECT state FROM assistant.chat_turn_ledger WHERE turn_id=:turn",
                java.util.Map.of("turn", reservation.turnId()), String.class));
        assertEquals(1, count("SELECT request_count FROM assistant.usage_bucket WHERE owner_id=:owner AND scope='USER'", owner));
        assertEquals(ThesisAssistantTurnRepository.ReservationStatus.AMBIGUOUS,
                tx(() -> turns.reserve(owner, request, hash(request), null, "vi", "retry", 90)).status());
    }

    @Test
    void cancelAfterDispatchedExpiryRecoversAmbiguousAndFencesOldGeneration() {
        String owner = "pg-cancel-expiry-" + UUID.randomUUID();
        UUID request = UUID.randomUUID();
        var reservation = tx(() -> turns.reserve(owner, request, hash(request), null, "vi", "cancel-expiry", 90));
        assertTrue(tx(() -> turns.markSnapshotReady(reservation.turnId(), owner, reservation.leaseGeneration(), hash(request))));
        assertTrue(tx(() -> turns.dispatch(reservation.turnId(), owner, reservation.leaseGeneration(), 20, 200)).dispatched());

        AssistantCancellationRegistry registry = new AssistantCancellationRegistry();
        registry.register(owner, request, reservation.leaseGeneration());
        jdbc.update("UPDATE assistant.chat_turn_ledger SET lease_expires_at=:expired WHERE turn_id=:turn",
                new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
                        .addValue("turn", reservation.turnId())
                        .addValue("expired", Timestamp.from(Instant.now().minusSeconds(1))));

        var cancelled = tx(() -> turns.cancel(reservation.turnId(), owner, request,
                handle -> registry.fence(owner, handle.clientRequestId(), handle.leaseGeneration())));
        assertFalse(cancelled.cancelled());
        assertEquals("FAILED_AMBIGUOUS", cancelled.status());
        assertEquals("FAILED_AMBIGUOUS", jdbc.queryForObject(
                "SELECT state FROM assistant.chat_turn_ledger WHERE turn_id=:turn",
                java.util.Map.of("turn", reservation.turnId()), String.class));
        assertEquals(reservation.leaseGeneration() + 1, jdbc.queryForObject(
                "SELECT lease_generation FROM assistant.chat_turn_ledger WHERE turn_id=:turn",
                java.util.Map.of("turn", reservation.turnId()), Long.class));
        assertEquals(1, count("SELECT request_count FROM assistant.usage_bucket WHERE owner_id=:owner AND scope='USER'", owner));
        assertFalse(registry.emitIfActive(owner, request, reservation.leaseGeneration(),
                () -> { throw new AssertionError("expired dispatched generation emitted after cancel recovery"); }));
        assertEquals(ThesisAssistantTurnRepository.ReservationStatus.AMBIGUOUS,
                tx(() -> turns.reserve(owner, request, hash(request), null, "vi", "retry", 90)).status());
    }

    @Test
    void expiryFenceCallbackRunsAfterCommitWithoutHoldingTheLedgerLock() throws Exception {
        String owner = "pg-after-commit-fence-" + UUID.randomUUID();
        UUID request = UUID.randomUUID();
        var reservation = tx(() -> turns.reserve(owner, request, hash(request), null, "vi", "after-commit", 90));
        assertTrue(tx(() -> turns.markSnapshotReady(reservation.turnId(), owner, reservation.leaseGeneration(), hash(request))));
        assertTrue(tx(() -> turns.dispatch(reservation.turnId(), owner, reservation.leaseGeneration(), 20, 200)).dispatched());
        jdbc.update("UPDATE assistant.chat_turn_ledger SET lease_expires_at=:expired WHERE turn_id=:turn",
                new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
                        .addValue("turn", reservation.turnId())
                        .addValue("expired", Timestamp.from(Instant.now().minusSeconds(1))));

        CountDownLatch callbackEntered = new CountDownLatch(1);
        CountDownLatch releaseCallback = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> retry = pool.submit(() -> tx(() -> turns.reserve(owner, request, hash(request), null, "vi", "retry", 90,
                    lease -> {
                        callbackEntered.countDown();
                        await(releaseCallback);
                    })));
            assertTrue(callbackEntered.await(10, TimeUnit.SECONDS));

            // The callback is deliberately blocked.  A committed CAS must
            // still release the PostgreSQL row lock before that callback runs.
            Future<String> state = pool.submit(() -> jdbc.queryForObject(
                    "SELECT state FROM assistant.chat_turn_ledger WHERE turn_id=:turn",
                    java.util.Map.of("turn", reservation.turnId()), String.class));
            assertEquals("FAILED_AMBIGUOUS", state.get(5, TimeUnit.SECONDS));
            releaseCallback.countDown();
            retry.get(10, TimeUnit.SECONDS);
        } finally {
            releaseCallback.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void cancellationFenceCallbackRunsAfterCommitBeforeAProviderGenerationCanContinue() throws Exception {
        String owner = "pg-cancel-after-commit-" + UUID.randomUUID();
        UUID request = UUID.randomUUID();
        var reservation = tx(() -> turns.reserve(owner, request, hash(request), null, "vi", "cancel-after-commit", 90));
        assertTrue(tx(() -> turns.markSnapshotReady(reservation.turnId(), owner, reservation.leaseGeneration(), hash(request))));
        assertTrue(tx(() -> turns.dispatch(reservation.turnId(), owner, reservation.leaseGeneration(), 20, 200)).dispatched());

        AssistantCancellationRegistry registry = new AssistantCancellationRegistry();
        registry.register(owner, request, reservation.leaseGeneration());
        CountDownLatch callbackEntered = new CountDownLatch(1);
        CountDownLatch releaseCallback = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> cancellation = pool.submit(() -> tx(() -> turns.cancel(reservation.turnId(), owner, request,
                    handle -> {
                        callbackEntered.countDown();
                        await(releaseCallback);
                        registry.fence(owner, handle.clientRequestId(), handle.leaseGeneration());
                    })));
            assertTrue(callbackEntered.await(10, TimeUnit.SECONDS));

            // The CAS is committed before the after-commit callback is allowed
            // to wait on the provider-handle fence. A second connection must
            // therefore observe CANCELLED while the callback is paused.
            Future<String> state = pool.submit(() -> jdbc.queryForObject(
                    "SELECT state FROM assistant.chat_turn_ledger WHERE turn_id=:turn",
                    java.util.Map.of("turn", reservation.turnId()), String.class));
            assertEquals("CANCELLED", state.get(5, TimeUnit.SECONDS));
            releaseCallback.countDown();
            cancellation.get(10, TimeUnit.SECONDS);
            assertTrue(!registry.emitIfActive(owner, request, reservation.leaseGeneration(),
                    () -> { throw new AssertionError("cancelled provider generation emitted after the fence"); }));
        } finally {
            releaseCallback.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void legacyRetentionSkipsAnExpiredConversationWhileItsTurnIsActive() {
        String owner = "pg-legacy-retention-" + UUID.randomUUID();
        UUID request = UUID.randomUUID();
        var reservation = tx(() -> turns.reserve(owner, request, hash(request), null, "vi", "legacy-retention", 90));
        assertTrue(tx(() -> turns.markSnapshotReady(reservation.turnId(), owner, reservation.leaseGeneration(), hash(request))));
        assertTrue(tx(() -> turns.dispatch(reservation.turnId(), owner, reservation.leaseGeneration(), 20, 200)).dispatched());
        jdbc.update("UPDATE assistant.chat_conversation SET expires_at=:expired WHERE id=:conversation",
                new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
                        .addValue("conversation", reservation.conversationId())
                        .addValue("expired", Timestamp.from(Instant.now().minusSeconds(1))));

        assertEquals(0, legacyHistory.purgeExpired());
        assertEquals(1, count("SELECT COUNT(*) FROM assistant.chat_conversation WHERE owner_id=:owner", owner));
        assertEquals("DISPATCHED", jdbc.queryForObject("SELECT state FROM assistant.chat_turn_ledger WHERE turn_id=:turn",
                java.util.Map.of("turn", reservation.turnId()), String.class));
    }

    private <T> T tx(Callable<T> callable) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            try { return callable.call(); }
            catch (RuntimeException exception) { throw exception; }
            catch (Exception exception) { throw new IllegalStateException(exception); }
        });
    }

    private int count(String sql, String owner) {
        Integer value = jdbc.queryForObject(sql, java.util.Map.of("owner", owner), Integer.class);
        return value == null ? 0 : value;
    }

    private static void await(CountDownLatch latch) {
        try { assertTrue(latch.await(10, TimeUnit.SECONDS)); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException(exception); }
    }

    private static String hash(UUID request) { return ("%032d".formatted(request.getLeastSignificantBits() & Long.MAX_VALUE)).repeat(2).substring(0, 64); }
    private static String valueOr(String name, String fallback) { String value = System.getenv(name); return value == null || value.isBlank() ? fallback : value; }
}
