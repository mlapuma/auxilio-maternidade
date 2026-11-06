package br.com.salario.maternidade.whatsapp;

import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Advances a persisted conversation without sending messages externally.
 * The reply is returned for a future outbox-based delivery integration.
 */
@Service
public class WhatsAppConversationProcessor {
    private static final Pattern LANDING_CODE = Pattern.compile("(?iu)^c[oó]digo\\s+(SM-[A-F0-9]{16})$");
    private final WhatsAppConversationStore store;
    private final WhatsAppConversationFlow flow = new WhatsAppConversationFlow();
    private final WhatsAppGuidedCorrection correction;

    public WhatsAppConversationProcessor(WhatsAppConversationStore store) {
        this.store = store;
        this.correction = new WhatsAppGuidedCorrection(store);
    }

    public WhatsAppConversationFlow.Transition process(WhatsAppInboundMessage message) {
        var current = store.findByPhone(message.sender())
            .orElse(new WhatsAppConversationStore.Conversation(
                message.sender(), WhatsAppConversationFlow.Step.START, false));
        if (message.expectedStep() != null && message.expectedStep() != current.step()) {
            // An old button must not answer a different question or grant new consent.
            if (current.consent() && WhatsAppGuidedCorrection.isCorrection(current.step())) {
                return correction.process(message.sender(), current.step(), "");
            }
            return flow.next(current.step(), "");
        }
        var code = LANDING_CODE.matcher(message.text() == null ? "" : message.text().strip());
        if (current.step() != WhatsAppConversationFlow.Step.STOPPED && code.matches()
                && store.resumeLandingQuestionnaire(message.sender(), code.group(1).toUpperCase(Locale.ROOT))) {
            store.cancelPendingReplies(message.sender());
            return new WhatsAppConversationFlow.Transition(WhatsAppConversationFlow.Step.HANDOFF,
                "Olá! Recebemos seu questionário e vinculamos este WhatsApp ao atendimento. "
                + "Você não precisa responder novamente. Se desejar, envie informações complementares por aqui. "
                + "A equipe fará a análise individual; isto não confirma direito ao benefício.\n\n"
                + WhatsAppConversationFlow.MENU, true);
        }
        String normalizedInput = WhatsAppConversationFlow.normalized(message.text());
        boolean leaving = WhatsAppConversationFlow.menuCommand(message.text())
            || java.util.Set.of("sair", "parar", "cancelar", "stop", "unsubscribe").contains(normalizedInput);
        boolean enteringCorrection = current.step() == WhatsAppConversationFlow.Step.HANDOFF
            && java.util.Set.of("2", "corrigir", "corrigir resposta").contains(normalizedInput);
        var transition = current.consent() && !leaving && (enteringCorrection || WhatsAppGuidedCorrection.isCorrection(current.step()))
            ? (enteringCorrection ? correction.start(message.sender()) : correction.process(message.sender(), current.step(), message.text()))
            : flow.next(current.step(), message.text());
        if (leaving && WhatsAppGuidedCorrection.isCorrection(current.step())) store.clearCorrectionDraft(message.sender());
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
        if (consent && transition.nextStep() != WhatsAppConversationFlow.Step.STOPPED
                && !WhatsAppConversationFlow.menuCommand(message.text())) {
            if (current.step() == WhatsAppConversationFlow.Step.ADD_INFO
                    && transition.nextStep() == WhatsAppConversationFlow.Step.HANDOFF) {
                store.appendFollowUp(message.sender(), "complementos", message.text().strip());
            } else if ((current.step() == WhatsAppConversationFlow.Step.HANDOFF
                    || current.step() == WhatsAppConversationFlow.Step.FAQ)
                    && WhatsAppConversationFlow.teamCommand(message.text())) {
                store.appendFollowUp(message.sender(), "solicitacoes_equipe", "Solicitado contato da equipe");
            } else if (current.step() == WhatsAppConversationFlow.Step.FAQ
                    && transition.nextStep() == WhatsAppConversationFlow.Step.HANDOFF) {
                store.appendFollowUp(message.sender(), "duvidas", message.text().strip());
            }
            if (current.step() == WhatsAppConversationFlow.Step.HANDOFF
                    && java.util.Set.of("5", "andamento", "status").contains(WhatsAppConversationFlow.normalized(message.text()))) {
                String status = store.findLinkedLeadStatus(message.sender()).orElse("");
                String summary = switch (status) {
                    case "NOVO" -> "Sua solicitação foi recebida e aguarda atendimento da equipe.";
                    case "EM_ATENDIMENTO" -> "Seu atendimento está em andamento com a equipe.";
                    case "CONCLUIDO" -> "Seu atendimento foi marcado como concluído pela equipe. Isso não indica aprovação de benefício. Digite 4 se precisar de novo contato.";
                    default -> "Ainda não há um andamento disponível para este WhatsApp. Digite 4 para solicitar contato da equipe.";
                };
                transition = new WhatsAppConversationFlow.Transition(WhatsAppConversationFlow.Step.HANDOFF,
                    summary + "\n\n" + WhatsAppConversationFlow.MENU, true);
            }
        }
        // A new consent prompt supersedes older, unsent invitations.
        // The webhook will enqueue only the latest prompt after this transition.
        if (transition.nextStep() == WhatsAppConversationFlow.Step.CONSENT
                && current.step() == WhatsAppConversationFlow.Step.CONSENT) {
            store.cancelPendingConsentRequests(message.sender());
        }
        if (current.step() == WhatsAppConversationFlow.Step.CONSENT
                && transition.nextStep() == WhatsAppConversationFlow.Step.BIRTH) {
            store.cancelPendingConsentRequests(message.sender());
        }
        if (transition.nextStep() == WhatsAppConversationFlow.Step.STOPPED
                || (current.step() == WhatsAppConversationFlow.Step.STOPPED
                    && transition.nextStep() == WhatsAppConversationFlow.Step.CONSENT)) {
            store.clearScreeningAnswers(message.sender());
            store.unlinkLead(message.sender());
            store.cancelPendingReplies(message.sender());
        } else if (consent && current.step() == WhatsAppConversationFlow.Step.BIRTH
                && transition.nextStep() == WhatsAppConversationFlow.Step.WORK) {
            store.saveScreeningAnswer(message.sender(), "situacao_bebe", normalize(message.text()));
        } else if (consent && current.step() == WhatsAppConversationFlow.Step.WORK
                && transition.nextStep() == WhatsAppConversationFlow.Step.HANDOFF) {
            store.saveScreeningAnswer(message.sender(), "situacao_trabalho", normalize(message.text()));
        }
        if (consent && transition.nextStep() != WhatsAppConversationFlow.Step.STOPPED) {
            // New inbound messages make older, unsent triage prompts obsolete.
            // The webhook enqueues the new response after this transaction step.
            store.cancelPendingStandardReplies(message.sender());
            store.linkUniqueExistingLead(message.sender());
        }
        return transition;
    }
    private static String normalize(String answer) {
        return java.text.Normalizer.normalize(answer.strip(), java.text.Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "").toUpperCase(Locale.ROOT).replace(' ', '_');
    }
}
