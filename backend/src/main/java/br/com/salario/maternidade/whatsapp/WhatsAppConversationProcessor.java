package br.com.salario.maternidade.whatsapp;

import org.springframework.stereotype.Service;

/**
 * Advances a persisted conversation without sending messages externally.
 * The reply is returned for a future outbox-based delivery integration.
 */
@Service
public class WhatsAppConversationProcessor {
    private final WhatsAppConversationStore store;
    private final WhatsAppConversationFlow flow = new WhatsAppConversationFlow();

    public WhatsAppConversationProcessor(WhatsAppConversationStore store) {
        this.store = store;
    }

    public WhatsAppConversationFlow.Transition process(WhatsAppInboundMessage message) {
        var current = store.findByPhone(message.sender())
            .orElse(new WhatsAppConversationStore.Conversation(
                message.sender(), WhatsAppConversationFlow.Step.START, false));
        var transition = flow.next(current.step(), message.text());
        boolean consent = current.consent();
        if (current.step() == WhatsAppConversationFlow.Step.CONSENT
                && transition.nextStep() == WhatsAppConversationFlow.Step.BIRTH) {
            consent = true;
        }
        if (transition.nextStep() == WhatsAppConversationFlow.Step.STOPPED) {
            store.deleteByPhone(message.sender());
        } else {
            store.save(new WhatsAppConversationStore.Conversation(
                message.sender(), transition.nextStep(), consent));
        }
        return transition;
    }
}
