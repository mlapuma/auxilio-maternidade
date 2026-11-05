package br.com.salario.maternidade.whatsapp;

import java.util.Locale;

/**
 * Pure conversation state machine. It does not send messages, store personal
 * information, or make eligibility decisions.
 */
public class WhatsAppConversationFlow {
    public enum Step {
        START, CONSENT, BIRTH, WORK, HANDOFF, STOPPED
    }

    public record Transition(Step nextStep, String reply, boolean requiresHuman) {}

    public Transition next(Step step, String answer) {
        String input = answer == null ? "" : answer.strip().toLowerCase(Locale.ROOT);
        if ("sair".equals(input) || "parar".equals(input) || "cancelar".equals(input)
                || "stop".equals(input) || "unsubscribe".equals(input)) {
            return new Transition(Step.STOPPED,
                "Atendimento automático encerrado. Você pode solicitar novo contato quando desejar.", false);
        }
        return switch (step) {
            case START -> new Transition(Step.CONSENT,
                "Olá! Sou o atendimento virtual do escritório. Podemos registrar suas respostas para encaminhar sua solicitação à equipe? Responda SIM ou NÃO.", false);
            case CONSENT -> switch (input) {
                case "sim" -> new Transition(Step.BIRTH,
                    "Obrigada. O bebê já nasceu ou você ainda está gestante? Responda NASCEU ou GESTANTE.", false);
                case "não", "nao" -> new Transition(Step.STOPPED,
                    "Sem sua autorização, não continuaremos a coleta de informações. Atendimento encerrado.", false);
                default -> new Transition(Step.CONSENT, "Para continuar, responda SIM ou NÃO.", false);
            };
            case BIRTH -> switch (input) {
                case "nasceu", "gestante" -> new Transition(Step.WORK,
                    "Você está trabalhando ou contribuindo para o INSS atualmente? Responda SIM, NÃO ou NÃO SEI.", false);
                default -> new Transition(Step.BIRTH, "Responda NASCEU ou GESTANTE.", false);
            };
            case WORK -> switch (input) {
                case "sim", "não", "nao", "não sei", "nao sei" -> new Transition(Step.HANDOFF,
                    "Obrigada pelas informações. A equipe poderá avaliar seu caso individualmente. Esta conversa não confirma direito ao benefício.", true);
                default -> new Transition(Step.WORK, "Responda SIM, NÃO ou NÃO SEI.", false);
            };
            case HANDOFF -> new Transition(Step.HANDOFF,
                "Seu atendimento aguarda avaliação da equipe. Digite SAIR para encerrar.", true);
            case STOPPED -> "iniciar".equals(input) || "recomeçar".equals(input) || "recomecar".equals(input)
                ? new Transition(Step.CONSENT,
                    "Vamos iniciar um novo atendimento. Podemos registrar suas respostas para encaminhar sua solicitação à equipe? Responda SIM ou NÃO.", false)
                : new Transition(Step.STOPPED,
                    "Atendimento encerrado. Digite INICIAR para solicitar um novo atendimento.", false);
        };
    }
}
