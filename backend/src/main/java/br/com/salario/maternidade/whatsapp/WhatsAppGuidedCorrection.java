package br.com.salario.maternidade.whatsapp;

import java.util.*;
import static br.com.salario.maternidade.whatsapp.WhatsAppConversationFlow.Step.*;
import static br.com.salario.maternidade.whatsapp.WhatsAppConversationStore.CorrectionDraft;
import static br.com.salario.maternidade.whatsapp.WhatsAppQuestionnaire.Question;

/** Draft changes are private to the verified conversation and applied only after confirmation. */
public final class WhatsAppGuidedCorrection {
    private final WhatsAppConversationStore store;
    public WhatsAppGuidedCorrection(WhatsAppConversationStore store) { this.store = store; }

    public static boolean isCorrection(WhatsAppConversationFlow.Step step) {
        return step == CORRECT_INFO || step == CORRECT_VALUE || step == CORRECT_CONFIRM;
    }

    public static WhatsAppInteractiveOptions.Input parseAction(String id) {
        String[] parts = id.split(":", 4);
        if (parts.length != 4 || !parts[0].equals("qc") || !parts[1].matches("[a-f0-9]{16}")
                || !parts[3].matches("[a-z0-9_]{1,24}")) return null;
        var step = switch (parts[2]) { case "q" -> CORRECT_INFO; case "a" -> CORRECT_VALUE; case "c" -> CORRECT_CONFIRM; default -> null; };
        return step == null ? null : new WhatsAppInteractiveOptions.Input(id, step);
    }

    public WhatsAppConversationFlow.Transition start(String phone) {
        var saved = store.findSavedQuestionnaire(phone);
        if (saved.isEmpty() || WhatsAppQuestionnaire.QUESTIONS.stream().noneMatch(q -> saved.get().answers().containsKey(q.title()))) {
            store.clearCorrectionDraft(phone);
            return plain(HANDOFF, "Não localizei um questionário vinculado a este WhatsApp. Digite 4 para solicitar ajuda da equipe.\n\n" + WhatsAppConversationFlow.MENU);
        }
        var draft = new CorrectionDraft(saved.get().triageId(), new LinkedHashMap<>(saved.get().answers()),
            new LinkedHashMap<>(saved.get().answers()), "", nonce());
        store.saveCorrectionDraft(phone, draft);
        return questions(draft);
    }

    public WhatsAppConversationFlow.Transition process(String phone, WhatsAppConversationFlow.Step step, String answer) {
        var found = store.findCorrectionDraft(phone);
        if (found.isEmpty()) return start(phone);
        var draft = found.get();
        String value = answer == null ? "" : answer.strip();
        if (value.startsWith("qc:")) {
            String[] parts = value.split(":", 4);
            var parsed = parseAction(value);
            if (parsed == null || parsed.expectedStep() != step || !parts[1].equals(draft.nonce())) return prompt(step, draft);
            value = parts[3];
        }
        if (WhatsAppConversationFlow.menuCommand(value)) {
            store.clearCorrectionDraft(phone);
            return plain(HANDOFF, WhatsAppConversationFlow.MENU);
        }
        if (step == CORRECT_INFO) {
            var active = WhatsAppQuestionnaire.active(draft.candidate());
            String selected = value;
            int index = number(value) - 1;
            if (index >= 0 && index < active.size()) selected = active.get(index).id();
            final String id = selected;
            var question = active.stream().filter(q -> q.id().equals(id)).findFirst();
            if (question.isEmpty()) return questions(draft);
            draft = new CorrectionDraft(draft.triageId(), draft.original(), draft.candidate(), id, nonce());
            store.saveCorrectionDraft(phone, draft);
            return values(draft);
        }
        if (step == CORRECT_VALUE) {
            Question question = WhatsAppQuestionnaire.question(draft.questionId());
            int index = number(value) - 1;
            if (index < 0 || index >= question.answers().size()) {
                String normalized = WhatsAppConversationFlow.normalized(value);
                index = -1;
                for (int i = 0; i < question.answers().size(); i++) {
                    if (WhatsAppConversationFlow.normalized(question.answers().get(i)).equals(normalized)) index = i;
                }
            }
            if (index < 0 || index >= question.answers().size()) return values(draft);
            var candidate = new LinkedHashMap<>(draft.candidate());
            candidate.put(question.title(), question.answers().get(index));
            String dependency = WhatsAppQuestionnaire.dependency(candidate);
            for (String dependent : List.of("semanas", "parto", "adocao")) {
                if (!dependent.equals(dependency)) candidate.remove(WhatsAppQuestionnaire.question(dependent).title());
            }
            draft = new CorrectionDraft(draft.triageId(), draft.original(), candidate, question.id(), nonce());
            if (!dependency.isEmpty() && !candidate.containsKey(WhatsAppQuestionnaire.question(dependency).title())) {
                draft = new CorrectionDraft(draft.triageId(), draft.original(), candidate, dependency, nonce());
                store.saveCorrectionDraft(phone, draft);
                return values(draft);
            }
            store.saveCorrectionDraft(phone, draft);
            return confirmation(draft);
        }
        if (step == CORRECT_CONFIRM) {
            if (Set.of("again", "outra", "voltar", "nao", "no").contains(WhatsAppConversationFlow.normalized(value))) return start(phone);
            if (!Set.of("yes", "sim", "confirmar", "1").contains(WhatsAppConversationFlow.normalized(value))) return confirmation(draft);
            if (draft.original().equals(draft.candidate())) {
                store.clearCorrectionDraft(phone);
                return plain(HANDOFF, "Nenhuma resposta foi alterada.\n\n" + WhatsAppConversationFlow.MENU);
            }
            if (!store.applyQuestionnaireCorrection(phone, draft)) {
                var refreshed = start(phone);
                String body = cut("O questionário mudou durante a correção. Confira as respostas atuais e escolha novamente.\n\n" + refreshed.reply(), 1024);
                java.util.Map<String, Object> interactive = refreshed.interactive() == null ? null : new HashMap<>(refreshed.interactive());
                if (interactive != null) interactive.put("body", Map.of("text", body));
                return new WhatsAppConversationFlow.Transition(refreshed.nextStep(), body, true, interactive);
            }
            store.appendFollowUp(phone, "correcoes", changes(draft));
            return plain(HANDOFF, "Correção confirmada e questionário atualizado. A versão anterior foi preservada no histórico para a equipe.\n\n" + WhatsAppConversationFlow.MENU);
        }
        return questions(draft);
    }

    private WhatsAppConversationFlow.Transition prompt(WhatsAppConversationFlow.Step step, CorrectionDraft draft) {
        return step == CORRECT_VALUE ? values(draft) : step == CORRECT_CONFIRM ? confirmation(draft) : questions(draft);
    }

    private WhatsAppConversationFlow.Transition questions(CorrectionDraft draft) {
        StringBuilder body = new StringBuilder("Seu questionário — escolha a resposta que deseja corrigir:\n");
        var rows = new ArrayList<Map<String, String>>();
        var active = WhatsAppQuestionnaire.active(draft.candidate());
        for (int i = 0; i < active.size(); i++) {
            var q = active.get(i);
            String current = draft.candidate().getOrDefault(q.title(), "Não informado");
            body.append(i + 1).append(". ").append(q.label()).append(": ").append(cut(current, 80)).append('\n');
            rows.add(Map.of("id", action(draft, "q", q.id()), "title", q.label(), "description", cut(current, 72)));
        }
        rows.add(Map.of("id", action(draft, "q", "menu"), "title", "Voltar ao menu"));
        return list(CORRECT_INFO, body + "Toque em Escolher resposta ou digite o número. Digite MENU para voltar.", rows, "Escolher resposta");
    }

    private WhatsAppConversationFlow.Transition values(CorrectionDraft draft) {
        var q = WhatsAppQuestionnaire.question(draft.questionId());
        StringBuilder body = new StringBuilder(q.title()).append("\nResposta atual: ")
            .append(cut(draft.original().getOrDefault(q.title(), "Não informado"), 120)).append("\nEscolha a nova resposta:\n");
        var rows = new ArrayList<Map<String, String>>();
        for (int i = 0; i < q.answers().size(); i++) {
            body.append(i + 1).append(". ").append(q.answers().get(i)).append('\n');
            rows.add(Map.of("id", action(draft, "a", Integer.toString(i + 1)), "title", q.labels().get(i), "description", cut(q.answers().get(i), 72)));
        }
        rows.add(Map.of("id", action(draft, "a", "menu"), "title", "Voltar ao menu"));
        return list(CORRECT_VALUE, body + "Você também pode digitar o número. A alteração só será salva após sua confirmação.", rows, "Escolher resposta");
    }

    private WhatsAppConversationFlow.Transition confirmation(CorrectionDraft draft) {
        String body = cut("Confirme as alterações:\n" + changes(draft) + "\nToque em Confirmar ou digite SIM. Para escolher outra resposta, digite OUTRA. Digite MENU para cancelar.", 1024);
        var buttons = List.of(
            Map.of("type", "reply", "reply", Map.of("id", action(draft, "c", "yes"), "title", "Confirmar")),
            Map.of("type", "reply", "reply", Map.of("id", action(draft, "c", "again"), "title", "Escolher outra")),
            Map.of("type", "reply", "reply", Map.of("id", action(draft, "c", "menu"), "title", "Voltar ao menu")));
        return new WhatsAppConversationFlow.Transition(CORRECT_CONFIRM, body, true,
            Map.of("type", "button", "body", Map.of("text", body), "action", Map.of("buttons", buttons)));
    }

    private static String changes(CorrectionDraft draft) {
        StringBuilder result = new StringBuilder();
        for (var q : WhatsAppQuestionnaire.QUESTIONS) {
            String before = draft.original().get(q.title()), after = draft.candidate().get(q.title());
            if (!Objects.equals(before, after)) result.append(q.label()).append(": ")
                .append(before == null ? "Não informado" : before).append(" → ")
                .append(after == null ? "Não se aplica à nova situação" : after).append('\n');
        }
        return result.length() == 0 ? "Nenhuma alteração.\n" : result.toString();
    }

    private static WhatsAppConversationFlow.Transition list(WhatsAppConversationFlow.Step step, String body, List<Map<String, String>> rows, String button) {
        body = cut(body, 1024);
        return new WhatsAppConversationFlow.Transition(step, body, true,
            Map.of("type", "list", "body", Map.of("text", body), "action", Map.of("button", button,
                "sections", List.of(Map.of("title", "Questionário", "rows", rows)))));
    }
    private static WhatsAppConversationFlow.Transition plain(WhatsAppConversationFlow.Step step, String body) {
        return new WhatsAppConversationFlow.Transition(step, body, true);
    }
    private static String action(CorrectionDraft draft, String phase, String value) { return "qc:" + draft.nonce() + ":" + phase + ":" + value; }
    private static String nonce() { return UUID.randomUUID().toString().replace("-", "").substring(0, 16); }
    private static int number(String value) { try { return Integer.parseInt(value); } catch (NumberFormatException e) { return -1; } }
    private static String cut(String value, int limit) { return value.length() <= limit ? value : value.substring(0, limit - 1) + "…"; }
}
