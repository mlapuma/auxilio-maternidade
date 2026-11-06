package br.com.salario.maternidade.whatsapp;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static br.com.salario.maternidade.whatsapp.WhatsAppConversationFlow.Step.*;
import static br.com.salario.maternidade.whatsapp.WhatsAppConversationStore.*;

class WhatsAppGuidedCorrectionTest {
    private static final String PHONE = "5511999999999";
    private final WhatsAppConversationStore store = mock(WhatsAppConversationStore.class);
    private final WhatsAppGuidedCorrection correction = new WhatsAppGuidedCorrection(store);
    private final AtomicReference<CorrectionDraft> draft = new AtomicReference<>();
    private final Map<String, String> original = new LinkedHashMap<>();

    @BeforeEach
    void setup() {
        original.put(WhatsAppQuestionnaire.question("situacao").title(), "Estou grávida");
        original.put(WhatsAppQuestionnaire.question("trabalho").title(), "Não estou trabalhando");
        original.put(WhatsAppQuestionnaire.question("inss").title(), "Não tenho certeza");
        original.put(WhatsAppQuestionnaire.question("contribuicao").title(), "Não sei informar");
        original.put(WhatsAppQuestionnaire.question("semanas").title(), "13 a 20 semanas");
        original.put(WhatsAppQuestionnaire.question("atendimento").title(), "Sim, quero orientação");
        when(store.findSavedQuestionnaire(PHONE)).thenReturn(Optional.of(new SavedQuestionnaire(UUID.randomUUID(), original)));
        when(store.findCorrectionDraft(PHONE)).thenAnswer(i -> Optional.ofNullable(draft.get()));
        doAnswer(i -> { draft.set(i.getArgument(1)); return null; }).when(store).saveCorrectionDraft(eq(PHONE), any());
        doAnswer(i -> { draft.set(null); return null; }).when(store).clearCorrectionDraft(PHONE);
    }

    @Test
    void showsSavedAnswersAndRequiresConfirmationBeforeWriting() {
        var questionList = correction.start(PHONE);
        assertEquals(CORRECT_INFO, questionList.nextStep());
        assertTrue(questionList.reply().contains("Trabalho: Não estou trabalhando"));
        assertEquals("list", questionList.interactive().get("type"));
        assertEquals(CORRECT_VALUE, correction.process(PHONE, CORRECT_INFO, "2").nextStep());
        var confirm = correction.process(PHONE, CORRECT_VALUE, "1");
        assertEquals(CORRECT_CONFIRM, confirm.nextStep());
        assertTrue(confirm.reply().contains("Não estou trabalhando → Sim, com carteira assinada"));
        verify(store, never()).applyQuestionnaireCorrection(anyString(), any());
        when(store.applyQuestionnaireCorrection(eq(PHONE), any())).thenReturn(true);
        var result = correction.process(PHONE, CORRECT_CONFIRM, "SIM");
        assertEquals(HANDOFF, result.nextStep());
        verify(store).applyQuestionnaireCorrection(eq(PHONE), argThat(d ->
            d.candidate().get(WhatsAppQuestionnaire.question("trabalho").title()).equals("Sim, com carteira assinada")
            && d.original().get(WhatsAppQuestionnaire.question("trabalho").title()).equals("Não estou trabalhando")));
        verify(store).appendFollowUp(eq(PHONE), eq("correcoes"), contains("Trabalho:"));
        assertEquals("Não estou trabalhando", original.get(WhatsAppQuestionnaire.question("trabalho").title()));
    }

    @Test
    void changingPregnancyToBirthAsksMissingDependentQuestion() {
        correction.start(PHONE);
        correction.process(PHONE, CORRECT_INFO, "1");
        var next = correction.process(PHONE, CORRECT_VALUE, "2");
        assertEquals(CORRECT_VALUE, next.nextStep());
        assertEquals("parto", draft.get().questionId());
        assertTrue(next.reply().contains("Quando ocorreu o nascimento?"));
        assertFalse(draft.get().candidate().containsKey(WhatsAppQuestionnaire.question("semanas").title()));
        assertEquals(CORRECT_CONFIRM, correction.process(PHONE, CORRECT_VALUE, "1").nextStep());
        assertTrue(WhatsAppQuestionnaire.birth(draft.get().candidate()));
        verify(store, never()).applyQuestionnaireCorrection(anyString(), any());
    }

    @Test
    void staleButtonsCannotSelectAnAnswerInAnotherDraft() {
        correction.start(PHONE);
        var before = draft.get();
        String stale = "qc:ffffffffffffffff:q:trabalho";
        if (before.nonce().equals("ffffffffffffffff")) stale = "qc:0000000000000000:q:trabalho";
        assertEquals(CORRECT_INFO, correction.process(PHONE, CORRECT_INFO, stale).nextStep());
        assertSame(before, draft.get());
        verify(store, never()).applyQuestionnaireCorrection(anyString(), any());
    }

    @Test
    void cancelDiscardsDraftWithoutChangingOriginal() {
        correction.start(PHONE);
        correction.process(PHONE, CORRECT_INFO, "2");
        correction.process(PHONE, CORRECT_VALUE, "1");
        assertEquals(HANDOFF, correction.process(PHONE, CORRECT_CONFIRM, "MENU").nextStep());
        assertNull(draft.get());
        verify(store, never()).applyQuestionnaireCorrection(anyString(), any());
    }

    @Test
    void concurrentQuestionnaireChangeReloadsInsteadOfOverwriting() {
        correction.start(PHONE);
        correction.process(PHONE, CORRECT_INFO, "2");
        correction.process(PHONE, CORRECT_VALUE, "1");
        when(store.applyQuestionnaireCorrection(eq(PHONE), any())).thenReturn(false);
        assertEquals(CORRECT_INFO, correction.process(PHONE, CORRECT_CONFIRM, "SIM").nextStep());
        verify(store, never()).appendFollowUp(anyString(), anyString(), anyString());
    }

    @Test
    void noQuestionnaireDoesNotExposeAnyAnswers() {
        when(store.findSavedQuestionnaire(PHONE)).thenReturn(Optional.empty());
        var result = correction.start(PHONE);
        assertEquals(HANDOFF, result.nextStep());
        assertFalse(result.reply().contains("Não estou trabalhando"));
        verify(store, never()).saveCorrectionDraft(anyString(), any());
    }

    @Test
    void draftsSurviveServiceRestartAndInvalidChoiceDoesNotAdvance() {
        correction.start(PHONE);
        correction.process(PHONE, CORRECT_INFO, "2");
        var restarted = new WhatsAppGuidedCorrection(store);
        assertEquals(CORRECT_VALUE, restarted.process(PHONE, CORRECT_VALUE, "999").nextStep());
        assertEquals(CORRECT_CONFIRM, restarted.process(PHONE, CORRECT_VALUE, "1").nextStep());
    }

    @Test
    void generatedListsAndConfirmationRespectWhatsAppLimits() {
        for (int i = 1; i <= WhatsAppQuestionnaire.active(original).size(); i++) {
            validatePayload(correction.start(PHONE));
            validatePayload(correction.process(PHONE, CORRECT_INFO, Integer.toString(i)));
            validatePayload(correction.process(PHONE, CORRECT_VALUE, "1"));
        }
    }

    private static void validatePayload(WhatsAppConversationFlow.Transition transition) {
        var node = new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(transition.interactive());
        assertTrue(transition.reply().length() <= 1024);
        assertEquals(transition.reply(), node.path("body").path("text").asText());
        if (node.path("type").asText().equals("list")) {
            var rows = node.path("action").path("sections").get(0).path("rows");
            assertTrue(rows.size() <= 10);
            for (var row : rows) {
                assertTrue(row.path("title").asText().length() <= 24);
                assertTrue(row.path("description").asText().length() <= 72);
                assertNotNull(WhatsAppGuidedCorrection.parseAction(row.path("id").asText()));
            }
        } else {
            var buttons = node.path("action").path("buttons");
            assertTrue(buttons.size() <= 3);
            for (var button : buttons) assertTrue(button.path("reply").path("title").asText().length() <= 20);
        }
    }
}
