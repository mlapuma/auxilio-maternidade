package br.com.salario.maternidade.whatsapp;

import java.util.List;
import java.util.Map;
import static br.com.salario.maternidade.whatsapp.WhatsAppConversationFlow.Step;

/** Stable action IDs are distinct for each question, including repeated yes/no answers. */
public final class WhatsAppInteractiveOptions {
    private WhatsAppInteractiveOptions() {}
    public record Choice(String id, String title, String answer) {}
    public record Input(String answer, Step expectedStep) {}

    public static List<Choice> choices(Step step) {
        return switch (step) {
            case CONSENT -> List.of(new Choice("consent_yes", "Sim", "sim"), new Choice("consent_no", "Não", "não"));
            case BIRTH -> List.of(new Choice("birth_born", "Já nasceu", "nasceu"), new Choice("birth_pregnant", "Estou gestante", "gestante"));
            case WORK -> List.of(new Choice("work_yes", "Sim", "sim"), new Choice("work_no", "Não", "não"), new Choice("work_unknown", "Não sei", "não sei"));
            case HANDOFF -> List.of(new Choice("menu_add", "Complementar informações", "1"),
                new Choice("menu_correct", "Corrigir resposta", "2"), new Choice("menu_faq", "Dúvidas do atendimento", "3"),
                new Choice("menu_team", "Falar com a equipe", "4"), new Choice("menu_status", "Consultar andamento", "5"));
            case FAQ -> List.of(new Choice("faq_process", "Como funciona", "1"), new Choice("faq_documents", "Documentos", "2"),
                new Choice("faq_deadline", "Prazo de retorno", "3"), new Choice("faq_team", "Falar com a equipe", "4"),
                new Choice("faq_menu", "Voltar ao menu", "menu"));
            case ADD_INFO -> List.of(new Choice("add_menu", "Voltar ao menu", "menu"));
            case CORRECT_INFO -> List.of(new Choice("correct_menu", "Voltar ao menu", "menu"));
            case WAIT_TEAM -> List.of(new Choice("wait_team_menu", "Voltar ao menu", "menu"));
            case ACTION_DONE -> List.of(new Choice("done_menu", "Voltar ao menu", "menu"),
                new Choice("done_team", "Falar com a equipe", "4"), new Choice("done_stop", "Encerrar", "sair"));
            case FAQ_ANSWER -> List.of(new Choice("faq_answer_more", "Outra dúvida", "duvidas"),
                new Choice("faq_answer_team", "Falar com a equipe", "4"), new Choice("faq_answer_menu", "Voltar ao menu", "menu"));
            case STOPPED -> List.of(new Choice("stopped_restart", "Reiniciar", "iniciar"));
            default -> List.of();
        };
    }

    public static Input parseChoice(String id) {
        for (Step step : Step.values()) {
            for (Choice choice : choices(step)) {
                if (choice.id().equals(id)) return new Input(choice.answer(), step);
            }
        }
        return null;
    }

    public static Map<String, Object> payload(Step step, String body) {
        var choices = choices(step);
        if (choices.isEmpty() || body.length() > 1024) return null;
        if (choices.size() <= 3) {
            return Map.of("type", "button", "body", Map.of("text", body), "action", Map.of("buttons",
                choices.stream().map(c -> Map.of("type", "reply", "reply", Map.of("id", c.id(), "title", c.title()))).toList()));
        }
        return Map.of("type", "list", "body", Map.of("text", body), "action",
            Map.of("button", "Escolher opção", "sections", List.of(Map.of("title", "Atendimento", "rows",
                choices.stream().map(c -> Map.of("id", c.id(), "title", c.title())).toList()))));
    }
}
