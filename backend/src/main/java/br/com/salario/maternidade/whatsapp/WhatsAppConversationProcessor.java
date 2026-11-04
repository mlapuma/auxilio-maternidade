package br.com.salario.maternidade.whatsapp;

import java.util.Locale;
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
            consent = false;
        }
        store.save(new WhatsAppConversationStore.Conversation(
            message.sender(), transition.nextStep(), consent));
        if (current.step() == WhatsAppConversationFlow.Step.CONSENT
                && transition.nextStep() == WhatsAppConversationFlow.Step.BIRTH) {
            store.cancelPendingConsentRequests(message.sender());
        }
        if (transition.nextStep() == WhatsAppConversationFlow.Step.STOPPED
                || (current.step() == WhatsAppConversationFlow.Step.STOPPED
                    && transition.nextStep() == WhatsAppConversationFlow.Step.CONSENT)) {
            store.clearScreeningAnswers(message.sender());
            store.cancelPendingReplies(message.sender());
        } else if (consent && current.step() == WhatsAppConversationFlow.Step.BIRTH
                && transition.nextStep() == WhatsAppConversationFlow.Step.WORK) {
            store.saveScreeningAnswer(message.sender(), "situacao_bebe", normalize(message.text()));
        } else if (consent && current.step() == WhatsAppConversationFlow.Step.WORK
                && transition.nextStep() == WhatsAppConversationFlow.Step.HANDOFF) {
            store.saveScreeningAnswer(message.sender(), "situacao_trabalho", normalize(message.text()));
        }
        if (consent && transition.nextStep() != WhatsAppConversationFlow.Step.STOPPED) {
            store.linkUniqueExistingLead(message.sender());
        }
        return transition;
    }
    private static String normalize(String answer) {
        return java.text.Normalizer.normalize(answer.strip(), java.text.Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "").toUpperCase(Locale.ROOT).replace(' ', '_');
    }
}
