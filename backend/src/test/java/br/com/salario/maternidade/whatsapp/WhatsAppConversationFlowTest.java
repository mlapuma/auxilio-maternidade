package br.com.salario.maternidade.whatsapp;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static br.com.salario.maternidade.whatsapp.WhatsAppConversationFlow.Step.*;

class WhatsAppConversationFlowTest {
    private final WhatsAppConversationFlow flow = new WhatsAppConversationFlow();

    @Test
    void handoffOffersActionsInsteadOfOnlyWaiting() {
        assertEquals(ADD_INFO, flow.next(HANDOFF, "1").nextStep());
        assertEquals(CORRECT_INFO, flow.next(HANDOFF, "2").nextStep());
        assertEquals(FAQ, flow.next(HANDOFF, "3").nextStep());
        assertTrue(flow.next(HANDOFF, "4").reply().contains("solicitação de contato"));
        assertTrue(flow.next(HANDOFF, "menu").reply().contains("5 — Consultar andamento"));
    }

    @Test
    void recordsComplementAndCorrectionRequestsThenReturnsToMenu() {
        assertEquals(HANDOFF, flow.next(ADD_INFO, "Informação adicional").nextStep());
        var corrected = flow.next(CORRECT_INFO, "Minha resposta anterior estava incorreta");
        assertEquals(HANDOFF, corrected.nextStep());
        assertTrue(corrected.reply().contains("original foi preservada"));
        assertEquals(CORRECT_INFO, flow.next(CORRECT_INFO, " ").nextStep());
        assertEquals(ADD_INFO, flow.next(ADD_INFO, "a".repeat(4097)).nextStep());
    }

    @Test
    void faqAnswersOperationalQuestionsAndEscalatesOtherQuestions() {
        assertEquals(FAQ, flow.next(FAQ, "1").nextStep());
        assertTrue(flow.next(FAQ, "2").reply().contains("Não envie documentos"));
        assertTrue(flow.next(FAQ, "3").reply().contains("prazo de retorno confirmado"));
        assertEquals(HANDOFF, flow.next(FAQ, "4").nextStep());
        assertTrue(flow.next(FAQ, "Tenho direito ao benefício?").reply().contains("equipe responder"));
    }

    @Test
    void menuReturnsWithoutRecordingOrGettingStuckInSubmenus() {
        for (var step : new WhatsAppConversationFlow.Step[]{ADD_INFO, CORRECT_INFO, FAQ}) {
            assertEquals(HANDOFF, flow.next(step, "MENU").nextStep());
            assertEquals(HANDOFF, flow.next(step, "voltar").nextStep());
        }
    }

    @Test
    void requestsConsentBeforeAnyScreening() {
        var first = flow.next(START, "");
        assertEquals(CONSENT, first.nextStep());
        assertFalse(first.requiresHuman());
    }

    @Test
    void refusalEndsConversation() {
        assertEquals(STOPPED, flow.next(CONSENT, "NÃO").nextStep());
    }

    @Test
    void affirmativeConsentStartsScreening() {
        assertEquals(BIRTH, flow.next(CONSENT, "SIM").nextStep());
        assertEquals(WORK, flow.next(BIRTH, "GESTANTE").nextStep());
        var last = flow.next(WORK, "NÃO SEI");
        assertEquals(HANDOFF, last.nextStep());
        assertTrue(last.requiresHuman());
        assertTrue(last.reply().contains("não confirma direito"));
    }

    @Test
    void invalidAnswersDoNotAdvance() {
        assertEquals(CONSENT, flow.next(CONSENT, "talvez").nextStep());
        assertEquals(BIRTH, flow.next(BIRTH, "outro").nextStep());
    }

    @Test
    void stopCommandWorksFromEveryStep() {
        for (var step : WhatsAppConversationFlow.Step.values()) {
            assertEquals(STOPPED, flow.next(step, "SAIR").nextStep());
            assertEquals(STOPPED, flow.next(step, "STOP").nextStep());
            assertEquals(STOPPED, flow.next(step, "unsubscribe").nextStep());
        }
    }
    @Test
    void stoppedConversationRequiresExplicitRestart() {
        assertEquals(STOPPED, flow.next(STOPPED, "oi").nextStep());
        assertEquals(CONSENT, flow.next(STOPPED, "INICIAR").nextStep());
        assertEquals(CONSENT, flow.next(STOPPED, "recomeçar").nextStep());
    }
}
