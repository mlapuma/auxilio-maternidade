package br.com.salario.maternidade.whatsapp;

import java.util.List;
import java.util.Map;

/** Same questions and answer values used by the landing questionnaire. */
public final class WhatsAppQuestionnaire {
    private WhatsAppQuestionnaire() {}
    public record Question(String id, String label, String title, List<String> answers, List<String> labels) {}
    public static final List<Question> QUESTIONS = List.of(
        new Question("situacao", "Situação atual", "Qual é a sua situação neste momento?",
            List.of("Estou grávida", "Meu bebê já nasceu", "Adoção ou guarda para adoção", "Outra situação / preciso de orientação"),
            List.of("Estou grávida", "Meu bebê já nasceu", "Adoção ou guarda", "Preciso de orientação")),
        new Question("trabalho", "Trabalho", "Você está trabalhando atualmente?",
            List.of("Sim, com carteira assinada", "Sim, como MEI ou autônoma", "Sim, como doméstica ou trabalhadora rural", "Sim, como servidora pública", "Não estou trabalhando", "Outra situação / não sei informar"),
            List.of("Carteira assinada", "MEI ou autônoma", "Doméstica ou rural", "Servidora pública", "Não estou trabalhando", "Outra / não sei")),
        new Question("inss", "Vínculo com o INSS", "Você tem ou teve vínculo com o INSS?",
            List.of("Tenho vínculo ou contribuo atualmente", "Já trabalhei ou contribuí anteriormente", "Nunca contribuí / não sei se tenho vínculo", "Não tenho certeza"),
            List.of("Vínculo atual", "Vínculo anterior", "Nunca / não sei", "Não tenho certeza")),
        new Question("contribuicao", "Última contribuição", "Quando foi seu último vínculo ou contribuição?",
            List.of("Atualmente", "Há menos de 12 meses", "Entre 12 e 24 meses", "Há mais de 24 meses", "Nunca contribuí", "Não sei informar"),
            List.of("Atualmente", "Menos de 12 meses", "Entre 12 e 24 meses", "Mais de 24 meses", "Nunca contribuí", "Não sei informar")),
        new Question("semanas", "Semanas de gestação", "Quantas semanas de gestação você tem?",
            List.of("Até 12 semanas", "13 a 20 semanas", "21 a 27 semanas", "28 a 32 semanas", "33 a 36 semanas", "37 semanas ou mais", "Não sei informar"),
            List.of("Até 12 semanas", "13 a 20 semanas", "21 a 27 semanas", "28 a 32 semanas", "33 a 36 semanas", "37 semanas ou mais", "Não sei informar")),
        new Question("parto", "Nascimento", "Quando ocorreu o nascimento?",
            List.of("Há menos de 120 dias", "Entre 120 dias e 1 ano", "Há mais de 1 ano", "Não sei informar"),
            List.of("Menos de 120 dias", "120 dias a 1 ano", "Mais de 1 ano", "Não sei informar")),
        new Question("adocao", "Adoção ou guarda", "A adoção ou guarda judicial para adoção já ocorreu?",
            List.of("Sim", "Ainda não", "Não sei informar"), List.of("Sim", "Ainda não", "Não sei informar")),
        new Question("atendimento", "Análise individual", "Deseja receber uma análise individual do escritório?",
            List.of("Sim, quero orientação", "Não, por enquanto"), List.of("Sim, quero orientação", "Não, por enquanto"))
    );

    public static Question question(String id) {
        return QUESTIONS.stream().filter(q -> q.id().equals(id)).findFirst().orElseThrow();
    }

    public static String dependency(Map<String, String> answers) {
        return switch (answers.getOrDefault(question("situacao").title(), "")) {
            case "Estou grávida" -> "semanas";
            case "Meu bebê já nasceu" -> "parto";
            case "Adoção ou guarda para adoção" -> "adocao";
            default -> "";
        };
    }

    public static List<Question> active(Map<String, String> answers) {
        String dependent = dependency(answers);
        return QUESTIONS.stream().filter(q -> !List.of("semanas", "parto", "adocao").contains(q.id()) || q.id().equals(dependent)).toList();
    }

    public static boolean birth(Map<String, String> a) {
        return "Meu bebê já nasceu".equals(a.get(question("situacao").title()))
            || ("Adoção ou guarda para adoção".equals(a.get(question("situacao").title())) && "Sim".equals(a.get(question("adocao").title())));
    }
    public static boolean contribution(Map<String, String> a) {
        return List.of("Tenho vínculo ou contribuo atualmente", "Já trabalhei ou contribuí anteriormente").contains(a.getOrDefault(question("inss").title(), ""))
            || a.getOrDefault(question("trabalho").title(), "").startsWith("Sim,");
    }
    public static boolean wantsContact(Map<String, String> a) {
        return "Sim, quero orientação".equals(a.get(question("atendimento").title()));
    }
}
