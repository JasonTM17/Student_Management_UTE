package io.campuscore.restfulapi.auth.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Sends account lifecycle mail only after the challenge transaction commits. */
@Component
@Profile("persistence")
public class AuthMailEventListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthMailEventListener.class);

    private final AuthMailDelivery delivery;

    public AuthMailEventListener(AuthMailDelivery delivery) {
        this.delivery = delivery;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMailRequested(AuthMailEvent event) {
        try {
            delivery.send(event);
        } catch (RuntimeException exception) {
            // The API has already committed the one-time challenge. Keep the
            // failure observable without leaking token or recipient contents.
            LOGGER.warn(
                    "EMAIL_DELIVERY_UNAVAILABLE purpose={} failureType={}",
                    event.purpose(),
                    exception.getClass().getSimpleName());
        }
    }
}
