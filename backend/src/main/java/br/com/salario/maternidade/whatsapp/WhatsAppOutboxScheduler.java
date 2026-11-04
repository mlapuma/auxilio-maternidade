package br.com.salario.maternidade.whatsapp;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Disabled by default. Enable only after Meta window, consent, and privacy
 * checks have been verified in an end-to-end staging environment.
 */
@Component
public class WhatsAppOutboxScheduler {
    private final WhatsAppOutboxDispatcher dispatcher;
    private final boolean enabled;
    private final boolean sendingEnabled;

    public WhatsAppOutboxScheduler(WhatsAppOutboxDispatcher dispatcher,
            @Value("${WHATSAPP_OUTBOX_SCHEDULER_ENABLED:false}") boolean enabled,
            @Value("${WHATSAPP_SENDING_ENABLED:false}") boolean sendingEnabled) {
        this.dispatcher = dispatcher;
        this.enabled = enabled;
        this.sendingEnabled = sendingEnabled;
    }

    @Scheduled(fixedDelayString = "${WHATSAPP_OUTBOX_POLL_INTERVAL_MS:30000}")
    public void poll() {
        // Never claim messages when the Cloud API sender is disabled:
        // claiming would incorrectly move them to manual review.
        if (!enabled || !sendingEnabled) return;
        dispatcher.dispatchOne();
    }
}
