package br.com.salario.maternidade.whatsapp;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Disabled by default. Enable only after consent, customer-service window,
 * rate limits and end-to-end staging checks have been verified.
 */
@Component
public class WhatsAppOutboxScheduler {
    private static final Logger log = LoggerFactory.getLogger(WhatsAppOutboxScheduler.class);
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

    @PostConstruct
    public void logConfiguration() {
        log.info("WhatsApp outbox scheduler initialized: enabled={}, sendingEnabled={}, maxPerPoll={}",
            enabled, sendingEnabled, maxPerPoll);
        if (enabled && !sendingEnabled) {
            log.warn("WhatsApp outbox scheduler is enabled but sending is disabled; no messages will be dispatched");
        }
    }

    @Scheduled(fixedDelayString = "${WHATSAPP_OUTBOX_POLL_INTERVAL_MS:30000}")
    public void poll() {
        // Never claim messages when the Cloud API sender is disabled.
        if (!enabled || !sendingEnabled) return;
        int processed = 0;
        for (int i = 0; i < maxPerPoll; i++) {
            // Stop immediately on an empty queue; avoid needless DB polling.
            if (!dispatcher.dispatchOne()) break;
            processed++;
        }
        if (processed > 0) {
            log.info("WhatsApp outbox poll processed {} message(s)", processed);
        } else {
            log.debug("WhatsApp outbox poll found no eligible messages");
        }
    }
}
