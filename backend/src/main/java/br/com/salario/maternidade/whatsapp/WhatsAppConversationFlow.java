package br.com.salario.maternidade.whatsapp;

import java.util.Locale;

/**
 * Pure conversation state machine. It does not send messages, store personal
 * information, or make eligibility decisions.
 */
public class WhatsAppConversationFlow {
    public enum Step {
        START, CONSENT, BIRTH, WORK, HANDOFF, ACTION_DONE, FAQ_ANSWER, WAIT_TEAM, ADD_INFO, CORRECT_INFO, CORRECT_VALUE, CORRECT_CONFIRM, FAQ, STOPPED
    }

    public record Transition(Step nextStep, String reply, boolean requiresHuman, java.util.Map<String, Object> interactive, boolean closureAcknowledgement) {
        public Transition(Step nextStep, String reply, boolean requiresHuman) {
            this(nextStep, reply, requiresHuman, null, false);
        }
        public Transition(Step nextStep, String reply, boolean requiresHuman, java.util.Map<String, Object> interactive) {
            this(nextStep, reply, requiresHuman, interactive, false);
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

    public static boolean stopCommand(String answer) {
        return java.util.Set.of("sair", "parar", "cancelar", "stop", "unsubscribe").contains(normalized(answer));
    }

    public static String nextActions() {
        return "Toque em Voltar ao menu, Falar com a equipe ou Encerrar. Você também pode digitar MENU, EQUIPE ou SAIR.";
    }

    private static Transition waitForTeam() {
        return new Transition(Step.WAIT_TEAM,
            "Sua solicitação de contato foi registrada. A equipe poderá responder por aqui quando estiver disponível. Você pode acrescentar uma mensagem enquanto aguarda.", true);
    }

    public Transition next(Step step, String answer) {
        String input = answer == null ? "" : answer.strip().toLowerCase(Locale.ROOT);
        if ("cancelar".equals(normalized(answer)) && (step == Step.ADD_INFO || WhatsAppGuidedCorrection.isCorrection(step))) {
            return new Transition(Step.ACTION_DONE, "Operação cancelada. Nenhuma resposta do questionário foi alterada.\n\n" + nextActions(), true);
        }
        if (stopCommand(answer)) {
            return new Transition(Step.STOPPED,
                "Atendimento automático encerrado. Para retomar quando desejar, toque em Reiniciar ou digite INICIAR.", false, null, true);
        }
        if ((step == Step.HANDOFF || step == Step.ACTION_DONE || step == Step.FAQ_ANSWER || step == Step.WAIT_TEAM || step == Step.ADD_INFO || step == Step.CORRECT_INFO || step == Step.CORRECT_VALUE || step == Step.CORRECT_CONFIRM || step == Step.FAQ)
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
                    "Sem sua autorização, não continuaremos a coleta de informações. Atendimento encerrado. Para retomar, toque em Reiniciar ou digite INICIAR.", false, null, true);
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
                case "4", "equipe", "atendente", "falar com a equipe" -> waitForTeam();
                case "5", "andamento", "status" -> new Transition(Step.ACTION_DONE,
                    "Consulte o andamento do atendimento com a equipe.\n\n" + nextActions(), true);
                case "", "oi", "ola" -> new Transition(Step.HANDOFF, MENU, true);
                default -> input.matches("[0-9]+") || input.length() > 4096
                    ? new Transition(Step.HANDOFF, "Escolha uma das cinco opções do menu.", true)
                    : new Transition(Step.WAIT_TEAM, "Sua mensagem foi registrada para a equipe responder. Você pode acrescentar informações enquanto aguarda.", true);
            };
            case ACTION_DONE -> switch (normalized(answer)) {
                case "4", "equipe", "atendente", "falar com a equipe" -> waitForTeam();
                case "1", "complementar", "complementar informacoes" -> new Transition(Step.ADD_INFO,
                    "Escreva o que deseja acrescentar ao atendimento. Digite MENU para voltar ou CANCELAR para desistir.", true);
                case "2", "corrigir", "corrigir resposta" -> new Transition(Step.CORRECT_INFO,
                    "Selecione a resposta que deseja corrigir.", true);
                case "3", "duvidas", "outra duvida" -> new Transition(Step.FAQ,
                    "Escolha o assunto da sua dúvida ou escreva sua pergunta.", true);
                case "5", "status", "andamento" -> new Transition(Step.ACTION_DONE,
                    "Consulte o andamento do atendimento com a equipe.\n\n" + nextActions(), true);
                case "obrigado", "obrigada", "ok", "certo" -> new Transition(Step.ACTION_DONE,
                    "Por nada! Se precisar de mais alguma coisa, use os botões abaixo.", true);
                default -> input.isBlank() || input.matches("[0-9]+") || input.length() > 4096
                    ? new Transition(Step.ACTION_DONE, "Esta etapa foi concluída.\n\n" + nextActions(), true)
                    : new Transition(Step.WAIT_TEAM, "Sua mensagem foi registrada para a equipe responder. Você pode acrescentar informações enquanto aguarda.", true);
            };
            case FAQ_ANSWER -> teamCommand(answer) ? waitForTeam()
                : java.util.Set.of("duvidas", "outra duvida", "outra", "3").contains(normalized(answer))
                    ? new Transition(Step.FAQ, "Escolha outro assunto ou escreva sua dúvida. Digite MENU para voltar.", true)
                    : input.isBlank() || input.matches("[0-9]+") || input.length() > 4096
                        ? new Transition(Step.FAQ_ANSWER, "Toque em Outra dúvida, Falar com a equipe ou Voltar ao menu.", true)
                        : new Transition(Step.WAIT_TEAM, "Sua dúvida foi registrada para a equipe responder. Você pode acrescentar informações enquanto aguarda.", true);
            case WAIT_TEAM -> new Transition(Step.WAIT_TEAM,
                input.isBlank() || teamCommand(answer)
                    ? "Seu pedido de contato já está registrado e aguarda a equipe. Para outras opções, toque em Voltar ao menu."
                    : "Sua mensagem foi registrada no histórico do atendimento. A equipe poderá consultar e responder por aqui quando estiver disponível.", true);
            case ADD_INFO -> input.isBlank() || answer.length() > 4096
                ? new Transition(step, "Envie uma mensagem de até 4.096 caracteres ou digite MENU para voltar.", true)
                : new Transition(Step.ACTION_DONE, "Informações complementares registradas.\n\n" + nextActions(), true);
            case CORRECT_INFO, CORRECT_VALUE, CORRECT_CONFIRM -> new Transition(step, "Escolha uma opção da correção ou digite MENU para voltar.", true);
            case FAQ -> switch (normalized(answer)) {
                case "1", "como funciona" -> new Transition(Step.FAQ_ANSWER,
                    "O atendimento virtual registra suas informações e dúvidas. A equipe responsável faz a análise individual e orienta os próximos passos.", true);
                case "2", "documentos" -> new Transition(Step.FAQ_ANSWER,
                    "A equipe informará quais documentos são necessários e o canal adequado para envio. Não envie documentos ou dados sensíveis ao atendimento automático.", true);
                case "3", "prazo" -> new Transition(Step.FAQ_ANSWER,
                    "Ainda não há um prazo de retorno confirmado. Você pode solicitar uma atualização pelo botão Falar com a equipe.", true);
                case "4", "equipe", "atendente", "falar com a equipe" -> waitForTeam();
                case "" -> new Transition(Step.FAQ, "Escreva sua dúvida ou digite MENU para voltar.", true);
                default -> input.length() > 4096 || input.matches("[0-9]+")
                    ? new Transition(Step.FAQ, "Envie uma dúvida de até 4.096 caracteres ou digite MENU para voltar.", true)
                    : new Transition(Step.WAIT_TEAM, "Sua dúvida foi registrada para a equipe responder. Você pode acrescentar informações enquanto aguarda.", true);
            };
            case STOPPED -> "iniciar".equals(input) || "recomeçar".equals(input) || "recomecar".equals(input)
                ? new Transition(Step.CONSENT,
                    "Vamos iniciar um novo atendimento. Podemos registrar suas respostas para encaminhar sua solicitação à equipe? Responda SIM ou NÃO.", false)
                : new Transition(Step.STOPPED,
                    "Atendimento encerrado. Digite INICIAR para solicitar um novo atendimento.", false);
        };
    }
}
