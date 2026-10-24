package io.campuscore.restfulapi.auth.job;

import io.campuscore.restfulapi.auth.repository.PasswordResetTokenRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Retention sweep for {@code campuscore_auth."PasswordResetToken"} (V114).
 * Used tokens and expired links are already refused by the consume path, so
 * dropping them cannot resurrect a usable credential — the seven-day grace
 * keeps recent rows available for audit/forensics before deletion.
 */
@Component
@Profile("persistence")
@ConditionalOnProperty(prefix = "auth.password-reset.retention", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class PasswordResetTokenRetentionJob {
    private static final Logger log =
            LoggerFactory.getLogger(PasswordResetTokenRetentionJob.class);

    /** Grace window: consumed or expired rows stay seven days, then are deleted. */
    static final Duration RETENTION_GRACE = Duration.ofDays(7);

    private final PasswordResetTokenRepository tokens;
    private final Clock clock;

    /** No Clock bean exists in the context; the sweep only needs wall time. */
    @Autowired
    public PasswordResetTokenRetentionJob(PasswordResetTokenRepository tokens) {
        this(tokens, Clock.systemUTC());
    }

    PasswordResetTokenRetentionJob(PasswordResetTokenRepository tokens, Clock clock) {
        this.tokens = tokens;
        this.clock = clock;
    }

    /** Runs daily at 03:41 server time (off-peak, fixed schedule, no overlap). */
    @Scheduled(cron = "${auth.password-reset.retention.cron:0 41 3 * * *}")
    public void purgeStaleTokens() {
        Instant cutoff = clock.instant().minus(RETENTION_GRACE);
        int removed = tokens.purgeStale(cutoff);
        if (removed > 0) {
            log.info("PASSWORD_RESET_TOKEN_RETENTION removed={} cutoff={}", removed, cutoff);
        }
    }
}
