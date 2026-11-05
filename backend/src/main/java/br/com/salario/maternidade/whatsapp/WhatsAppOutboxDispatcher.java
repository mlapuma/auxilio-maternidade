package br.com.salario.maternidade.whatsapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;

/**
 * Dispatches at most one queued reply per invocation.
 * Invoked by the outbox scheduler when explicitly enabled.
 */
@Service
public class WhatsAppOutboxDispatcher {
    private static final Logger log = LoggerFactory.getLogger(WhatsAppOutboxDispatcher.class);
    private final WhatsAppOutboxDispatchStore store;
    private final WhatsAppCloudApiClient client;

    public WhatsAppOutboxDispatcher(WhatsAppOutboxDispatchStore store,
            WhatsAppCloudApiClient client) {
        this.store = store;
        this.client = client;
    }

    public boolean dispatchOne() {
        store.expireStalePendingReplies();
        store.flagStuckSendingForReview();
        var claimed = store.claimOne();
        if (claimed.isEmpty()) return false;

        var reply = claimed.get();
        // Separate eligibility, API submission and persistence failures so a
        // REVIEW record can be diagnosed without exposing tokens or message text.
        try {
            if (!store.stillEligible(reply.id())) {
                store.cancelClaimedReply(reply.id());
                log.info("WhatsApp outbox cancelled: id={} (no longer eligible)", reply.id());
                return true;
            }
        } catch (RuntimeException ex) {
            logFailure("eligibility", reply.id(), ex);
            store.markForReview(reply.id());
            return true;
        }

        try {
            client.sendText(reply.phone(), reply.body());
        } catch (RuntimeException ex) {
            // An HTTP/network failure can be ambiguous: Meta may have accepted
            // the request. Do not automatically retry this outbox record.
            logFailure("meta_submission", reply.id(), ex);
            store.markForReview(reply.id());
            return true;
        }

        try {
            store.markSent(reply.id());
            log.info("WhatsApp outbox submitted to Meta: id={}", reply.id());
        } catch (RuntimeException ex) {
            // The external send succeeded, but recording it locally failed.
            // Retain the record for manual reconciliation, never resend blindly.
            logFailure("persist_sent", reply.id(), ex);
            store.markForReview(reply.id());
        }
        return true;
    }

    private static void logFailure(String phase, java.util.UUID id, RuntimeException ex) {
        // Do not log exception messages or HTTP response bodies: providers and
        // proxies can include sensitive request metadata in those fields.
        if (ex instanceof RestClientResponseException responseException) {
            log.error("WhatsApp outbox failed: id={}, phase={}, httpStatus={}, exceptionType={}",
                id, phase, responseException.getStatusCode().value(), ex.getClass().getSimpleName());
        } else {
            log.error("WhatsApp outbox failed: id={}, phase={}, exceptionType={}",
                id, phase, ex.getClass().getSimpleName());
        }
    }
}
