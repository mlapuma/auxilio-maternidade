package br.com.salario.maternidade.whatsapp;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Disabled by default. Enable only after consent, customer-service window,
 * rate limits and end-to-end staging checks have been verified.
 */
@Component
public class WhatsAppOutboxScheduler {
    private final WhatsAppOutboxDispatcher dispatcher;
    private final boolean enabled;
    private final boolean sendingEnabled;
    private final int maxPerPoll;

    public WhatsAppOutboxScheduler(WhatsAppOutboxDispatcher dispatcher,
            @Value("${WHATSAPP_OUTBOX_SCHEDULER_ENABLED:false}") boolean enabled,
            @Value("${WHATSAPP_SENDING_ENABLED:false}") boolean sendingEnabled,
            @Value("${WHATSAPP_OUTBOX_MAX_PER_POLL:1}") int maxPerPoll) {
        if (maxPerPoll < 1 || maxPerPoll > 100) {
            throw new IllegalArgumentException("WHATSAPP_OUTBOX_MAX_PER_POLL must be between 1 and 100");
        }
        this.dispatcher = dispatcher;
        this.enabled = enabled;
        this.sendingEnabled = sendingEnabled;
        this.maxPerPoll = maxPerPoll;
    }

    @Scheduled(fixedDelayString = "${WHATSAPP_OUTBOX_POLL_INTERVAL_MS:30000}")
    public void poll() {
        // Never claim messages when the Cloud API sender is disabled.
        if (!enabled || !sendingEnabled) return;
        for (int i = 0; i < maxPerPoll; i++) {
            // Stop immediately on an empty queue; avoid needless DB polling.
            if (!dispatcher.dispatchOne()) break;
        }
    }
}
