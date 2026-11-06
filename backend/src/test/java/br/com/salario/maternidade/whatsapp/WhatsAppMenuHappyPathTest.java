package br.com.salario.maternidade.whatsapp;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static br.com.salario.maternidade.whatsapp.WhatsAppConversationFlow.Step.*;
import static br.com.salario.maternidade.whatsapp.WhatsAppConversationStore.*;

class WhatsAppMenuHappyPathTest {
    private static final String PHONE = "5511999999999";
    private final WhatsAppConversationStore store = mock(WhatsAppConversationStore.class);
    private final WhatsAppConversationProcessor processor = new WhatsAppConversationProcessor(store);
    private final AtomicReference<Conversation> state = new AtomicReference<>(new Conversation(PHONE, HANDOFF, true));
    private final AtomicReference<CorrectionDraft> draft = new AtomicReference<>();
    private int message;

    @BeforeEach
    void setup() {
        when(store.findByPhone(PHONE)).thenAnswer(i -> Optional.of(state.get()));
        doAnswer(i -> { state.set(i.getArgument(0)); return null; }).when(store).save(any());
        when(store.findLinkedLeadStatus(PHONE)).thenReturn(Optional.of("NOVO"));
        when(store.findCorrectionDraft(PHONE)).thenAnswer(i -> Optional.ofNullable(draft.get()));
        doAnswer(i -> { draft.set(i.getArgument(1)); return null; }).when(store).saveCorrectionDraft(eq(PHONE), any());
        doAnswer(i -> { draft.set(null); return null; }).when(store).clearCorrectionDraft(PHONE);
        Map<String, String> answers = new LinkedHashMap<>();
        for (var q : WhatsAppQuestionnaire.QUESTIONS) answers.put(q.title(), q.answers().get(0));
        answers.remove(WhatsAppQuestionnaire.question("parto").title());
        answers.remove(WhatsAppQuestionnaire.question("adocao").title());
        when(store.findSavedQuestionnaire(PHONE)).thenReturn(Optional.of(new SavedQuestionnaire(UUID.randomUUID(), answers)));
        when(store.applyQuestionnaireCorrection(eq(PHONE), any())).thenReturn(true);
    }

    private WhatsAppConversationFlow.Transition send(String text) {
        return processor.process(new WhatsAppInboundMessage("wamid.menu." + (++message), PHONE, text));
    }
    private WhatsAppConversationFlow.Transition click(String id) {
        var input = WhatsAppInteractiveOptions.parseChoice(id);
        return processor.process(new WhatsAppInboundMessage("wamid.click." + (++message), PHONE, input.answer(), input.expectedStep()));
    }
    private static void noMenu(WhatsAppConversationFlow.Transition result) {
        assertFalse(result.reply().contains(WhatsAppConversationFlow.MENU));
        assertNotEquals(HANDOFF, result.nextStep());
    }

    @Test
    void optionOneCompletesThenAllowsDeliberateNavigationOrTeamContact() {
        assertEquals(ADD_INFO, click("menu_add").nextStep());
        noMenu(send("Gostaria de acrescentar uma informação"));
        assertEquals(ACTION_DONE, state.get().step());
        verify(store).appendFollowUp(PHONE, "complementos", "Gostaria de acrescentar uma informação");
        assertEquals(HANDOFF, click("done_menu").nextStep());
        click("menu_add");
        send("Outro complemento");
        assertEquals(WAIT_TEAM, click("done_team").nextStep());
    }

    @Test
    void optionTwoConfirmsCorrectionWithoutReopeningMenu() {
        assertEquals(CORRECT_INFO, click("menu_correct").nextStep());
        assertEquals(CORRECT_VALUE, send("2").nextStep());
        assertEquals(CORRECT_CONFIRM, send("5").nextStep());
        var result = send("SIM");
        assertEquals(ACTION_DONE, result.nextStep());
        noMenu(result);
        verify(store).applyQuestionnaireCorrection(eq(PHONE), any());
        assertEquals(STOPPED, click("done_stop").nextStep());
    }

    @Test
    void optionThreeAnswersThenOffersAnotherQuestionWithoutRepeatingList() {
        assertEquals(FAQ, click("menu_faq").nextStep());
        var response = click("faq_documents");
        assertEquals(FAQ_ANSWER, response.nextStep());
        noMenu(response);
        assertEquals(FAQ, click("faq_answer_more").nextStep());
        click("faq_deadline");
        assertEquals(WAIT_TEAM, click("faq_answer_team").nextStep());
    }

    @Test
    void customQuestionWaitsForTeamAndIsRecordedAsQuestion() {
        click("menu_faq");
        var result = send("Qual é o próximo passo do meu atendimento?");
        assertEquals(WAIT_TEAM, result.nextStep());
        noMenu(result);
        verify(store).appendFollowUp(PHONE, "duvidas", "Qual é o próximo passo do meu atendimento?");
        noMenu(send("Tenho outra informação"));
    }

    @Test
    void optionFourWaitsAndOnlyExplicitMenuReturnsToMainMenu() {
        noMenu(click("menu_team"));
        assertEquals(WAIT_TEAM, state.get().step());
        noMenu(send("Aguardo retorno"));
        assertEquals(HANDOFF, click("wait_team_menu").nextStep());
    }

    @Test
    void optionFiveShowsActualStatusWithoutRepeatingMainMenu() {
        var result = click("menu_status");
        assertEquals(ACTION_DONE, result.nextStep());
        assertTrue(result.reply().contains("recebida e aguarda"));
        noMenu(result);
        verify(store).findLinkedLeadStatus(PHONE);
    }

    @Test
    void cancelDiscardsCorrectionInsteadOfEndingConversationOrRecordingControlText() {
        click("menu_correct");
        var result = send("CANCELAR");
        assertEquals(ACTION_DONE, result.nextStep());
        assertTrue(state.get().consent());
        assertNull(draft.get());
        noMenu(result);
        verify(store, never()).applyQuestionnaireCorrection(anyString(), any());
        verify(store, never()).appendFollowUp(anyString(), anyString(), eq("CANCELAR"));
    }

    @Test
    void oldMenuButtonDoesNotRestartAnAlreadyCompletedAction() {
        click("menu_status");
        var result = click("menu_status");
        assertEquals(ACTION_DONE, result.nextStep());
        noMenu(result);
        verify(store, times(1)).findLinkedLeadStatus(PHONE);
    }

    @Test
    void closedConversationRequiresExplicitRestartAndFreshConsent() {
        send("SAIR");
        assertFalse(state.get().consent());
        assertEquals(STOPPED, send("MENU").nextStep());
        assertEquals(CONSENT, click("stopped_restart").nextStep());
        assertFalse(state.get().consent());
        assertEquals(BIRTH, click("consent_yes").nextStep());
        assertTrue(state.get().consent());
        assertEquals(WORK, click("birth_born").nextStep());
        assertEquals(HANDOFF, click("work_unknown").nextStep());
    }

    @Test
    void freeTextAfterCompletedActionIsNotIgnoredOrSentBackToMenu() {
        click("menu_status");
        var response = send("Preciso de ajuda com o atendimento");
        assertEquals(WAIT_TEAM, response.nextStep());
        noMenu(response);
        verify(store).appendFollowUp(PHONE, "duvidas", "Preciso de ajuda com o atendimento");
    }

    @Test
    void cancelComplementDoesNotRecordTheWordCancelAsData() {
        click("menu_add");
        assertEquals(ACTION_DONE, send("cancelar").nextStep());
        assertTrue(state.get().consent());
        verify(store, never()).appendFollowUp(anyString(), anyString(), anyString());
    }

    @Test
    void oldGlobalStopAndMenuButtonsStillHonorExplicitNavigation() {
        click("menu_team");
        assertEquals(HANDOFF, click("done_menu").nextStep());
        click("menu_faq");
        var closed = click("done_stop");
        assertEquals(STOPPED, closed.nextStep());
        assertTrue(closed.closureAcknowledgement());
        assertFalse(state.get().consent());
    }
}
