package br.com.salario.maternidade.whatsapp;

import org.springframework.stereotype.Service;

/**
 * Dispatches at most one queued reply per invocation.
 * No scheduler or HTTP endpoint invokes this service yet.
 */
@Service
public class WhatsAppOutboxDispatcher {
    private final WhatsAppOutboxDispatchStore store;
    private final WhatsAppCloudApiClient client;

    public WhatsAppOutboxDispatcher(WhatsAppOutboxDispatchStore store,
            WhatsAppCloudApiClient client) {
        this.store = store;
        this.client = client;
    }

    public boolean dispatchOne() {
        store.expireStalePendingReplies();
        var claimed = store.claimOne();
        if (claimed.isEmpty()) return false;

        var reply = claimed.get();
        try {
            client.sendText(reply.phone(), reply.body());
            store.markSent(reply.id());
        } catch (RuntimeException ex) {
            // A timeout may occur after Meta accepted a message.
            // Never retry blindly; reconcile with Meta delivery records.
            store.markForReview(reply.id());
        }
        return true;
    }
}
