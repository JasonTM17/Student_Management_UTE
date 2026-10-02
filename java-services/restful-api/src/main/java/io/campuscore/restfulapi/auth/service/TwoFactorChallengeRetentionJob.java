package io.campuscore.restfulapi.auth.service;

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

import io.campuscore.restfulapi.auth.repository.TwoFactorChallengeRepository;

/**
 * Retention sweep for {@code campuscore_auth."TwoFactorChallenge"} (V85/V92).
 * Every 2FA login, enable and disable inserts one challenge row, and before
 * this job nothing ever deleted them — the table grew without bound while
 * retaining per-login attempt history. Daily the sweep removes rows that were
 * consumed before the cutoff or expired unconsumed before it; expired
 * challenges are already refused by {@code requireUsableChallenge}, so dropping
 * them cannot resurrect a usable code. One day of grace keeps recent rows
 * available for audit/forensics before deletion.
 */
@Component
@Profile("persistence")
@ConditionalOnProperty(prefix = "auth.two-factor.retention", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class TwoFactorChallengeRetentionJob {
    private static final Logger log = LoggerFactory.getLogger(TwoFactorChallengeRetentionJob.class);

    /** Grace window: consumed or expired rows stay one day, then are deleted. */
    static final Duration RETENTION_GRACE = Duration.ofDays(1);

    private final TwoFactorChallengeRepository challenges;
    private final Clock clock;

    /** No Clock bean exists in the context; the sweep only needs wall time. */
    @Autowired
    public TwoFactorChallengeRetentionJob(TwoFactorChallengeRepository challenges) {
        this(challenges, Clock.systemUTC());
    }

    TwoFactorChallengeRetentionJob(TwoFactorChallengeRepository challenges, Clock clock) {
        this.challenges = challenges;
        this.clock = clock;
    }

    /** Runs daily at 03:17 server time (off-peak, fixed schedule, no overlap). */
    @Scheduled(cron = "${auth.two-factor.retention.cron:0 17 3 * * *}")
    public void purgeStaleChallenges() {
        Instant cutoff = clock.instant().minus(RETENTION_GRACE);
        int removed = challenges.purgeStale(cutoff);
        if (removed > 0) {
            log.info("TWO_FACTOR_CHALLENGE_RETENTION removed={} cutoff={}", removed, cutoff);
        }
    }
}
