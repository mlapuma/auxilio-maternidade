package br.com.salario.maternidade.whatsapp;

import java.util.Locale;

/**
 * Pure conversation state machine. It does not send messages, store personal
 * information, or make eligibility decisions.
 */
public class WhatsAppConversationFlow {
    public enum Step {
        START, CONSENT, BIRTH, WORK, HANDOFF, WAIT_TEAM, ADD_INFO, CORRECT_INFO, CORRECT_VALUE, CORRECT_CONFIRM, FAQ, STOPPED
    }

    public record Transition(Step nextStep, String reply, boolean requiresHuman, java.util.Map<String, Object> interactive) {
        public Transition(Step nextStep, String reply, boolean requiresHuman) {
            this(nextStep, reply, requiresHuman, null);
        }
    }

    public static final String MENU = """
        Como posso ajudar agora?
        1 — Complementar informações
        2 — Corrigir uma resposta
        3 — Dúvidas sobre o atendimento
        4 — Falar com a equipe
        5 — Consultar andamento
        Digite MENU para ver estas opções ou SAIR para encerrar.""";

    public static String normalized(String answer) {
        return java.text.Normalizer.normalize(answer == null ? "" : answer.strip(), java.text.Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
    }

    public static boolean menuCommand(String answer) {
        return java.util.Set.of("menu", "voltar", "0").contains(normalized(answer));
    }

    public static boolean teamCommand(String answer) {
        return java.util.Set.of("4", "equipe", "atendente", "falar com a equipe").contains(normalized(answer));
    }

    public Transition next(Step step, String answer) {
        String input = answer == null ? "" : answer.strip().toLowerCase(Locale.ROOT);
        if ("sair".equals(input) || "parar".equals(input) || "cancelar".equals(input)
                || "stop".equals(input) || "unsubscribe".equals(input)) {
            return new Transition(Step.STOPPED,
                "Atendimento automático encerrado. Você pode solicitar novo contato quando desejar.", false);
        }
        if ((step == Step.HANDOFF || step == Step.WAIT_TEAM || step == Step.ADD_INFO || step == Step.CORRECT_INFO || step == Step.CORRECT_VALUE || step == Step.CORRECT_CONFIRM || step == Step.FAQ)
                && menuCommand(answer)) {
            return new Transition(Step.HANDOFF, MENU, true);
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
                    "Obrigada pelas informações. A equipe poderá avaliar seu caso individualmente. Esta conversa não confirma direito ao benefício.\n\n" + MENU, true);
                default -> new Transition(Step.WORK, "Responda SIM, NÃO ou NÃO SEI.", false);
            };
            case HANDOFF -> switch (normalized(answer)) {
                case "1", "complementar", "complementar informacoes" -> new Transition(Step.ADD_INFO,
                    "Escreva o que deseja acrescentar ao atendimento. Não envie CPF, documentos ou informações de saúde. Digite MENU para voltar.", true);
                case "2", "corrigir", "corrigir resposta" -> new Transition(Step.CORRECT_INFO,
                    "Informe qual resposta deseja corrigir e o novo valor. A alteração será registrada para revisão da equipe. Não envie CPF, documentos ou informações de saúde. Digite MENU para voltar.", true);
                case "3", "duvida", "duvidas" -> new Transition(Step.FAQ,
                    "Sobre o que deseja saber?\n1 — Como funciona o atendimento\n2 — Documentos\n3 — Prazo de retorno\n4 — Falar com a equipe\nOu escreva sua dúvida. Digite MENU para voltar.", true);
                case "4", "equipe", "atendente", "falar com a equipe" -> new Transition(Step.WAIT_TEAM,
                    "Sua solicitação de contato foi registrada. A equipe poderá responder por aqui quando estiver disponível. Você pode acrescentar uma mensagem enquanto aguarda. Para outras opções, toque em Voltar ao menu.", true);
                case "5", "andamento", "status" -> new Transition(Step.HANDOFF,
                    "Consulte o andamento do atendimento com a equipe.\n\n" + MENU, true);
                case "", "oi", "ola" -> new Transition(Step.HANDOFF, MENU, true);
                default -> new Transition(Step.HANDOFF,
                    "Recebi sua mensagem e ela ficará no histórico para a equipe.\n\n" + MENU, true);
            };
            case WAIT_TEAM -> new Transition(Step.WAIT_TEAM,
                input.isBlank() || teamCommand(answer)
                    ? "Seu pedido de contato já está registrado e aguarda a equipe. Para outras opções, toque em Voltar ao menu."
                    : "Sua mensagem foi registrada no histórico do atendimento. A equipe poderá consultar e responder por aqui quando estiver disponível.", true);
            case ADD_INFO, CORRECT_INFO -> input.isBlank() || answer.length() > 4096
                ? new Transition(step, "Envie uma mensagem de até 4.096 caracteres ou digite MENU para voltar.", true)
                : new Transition(Step.HANDOFF,
                    (step == Step.ADD_INFO ? "Informações complementares registradas." : "Pedido de correção registrado para revisão da equipe. Sua resposta original foi preservada.")
                    + "\n\n" + MENU, true);
            case CORRECT_VALUE, CORRECT_CONFIRM -> new Transition(step, "Escolha uma opção da correção ou digite MENU para voltar.", true);
            case FAQ -> switch (normalized(answer)) {
                case "1", "como funciona" -> new Transition(Step.FAQ,
                    "O atendimento virtual registra suas informações e dúvidas. A equipe responsável faz a análise individual e orienta os próximos passos.\nDigite 2 para documentos, 3 para prazo, 4 para falar com a equipe ou MENU para voltar.", true);
                case "2", "documentos" -> new Transition(Step.FAQ,
                    "A equipe informará quais documentos são necessários e o canal adequado para envio. Não envie documentos ou dados sensíveis ao atendimento automático. Digite MENU para voltar ou 4 para falar com a equipe.", true);
                case "3", "prazo" -> new Transition(Step.FAQ,
                    "Ainda não há um prazo de retorno confirmado. Para solicitar uma atualização, digite 4. Digite MENU para voltar.", true);
                case "4", "equipe", "atendente", "falar com a equipe" -> new Transition(Step.WAIT_TEAM,
                    "Sua solicitação de contato foi registrada. A equipe poderá responder por aqui quando estiver disponível. Você pode acrescentar uma mensagem enquanto aguarda. Para outras opções, toque em Voltar ao menu.", true);
                case "" -> new Transition(Step.FAQ, "Escreva sua dúvida ou digite MENU para voltar.", true);
                default -> input.length() > 4096
                    ? new Transition(Step.FAQ, "Envie uma dúvida de até 4.096 caracteres ou digite MENU para voltar.", true)
                    : new Transition(Step.HANDOFF, "Sua dúvida foi registrada para a equipe responder.\n\n" + MENU, true);
            };
            case STOPPED -> "iniciar".equals(input) || "recomeçar".equals(input) || "recomecar".equals(input)
                ? new Transition(Step.CONSENT,
                    "Vamos iniciar um novo atendimento. Podemos registrar suas respostas para encaminhar sua solicitação à equipe? Responda SIM ou NÃO.", false)
                : new Transition(Step.STOPPED,
                    "Atendimento encerrado. Digite INICIAR para solicitar um novo atendimento.", false);
        };
    }
}
