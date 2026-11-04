package br.com.salario.maternidade.whatsapp;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static br.com.salario.maternidade.whatsapp.WhatsAppConversationFlow.Step.*;

class WhatsAppConversationFlowTest {
    private final WhatsAppConversationFlow flow = new WhatsAppConversationFlow();

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
        }
    }
    @Test
    void stoppedConversationRequiresExplicitRestart() {
        assertEquals(STOPPED, flow.next(STOPPED, "oi").nextStep());
        assertEquals(CONSENT, flow.next(STOPPED, "INICIAR").nextStep());
        assertEquals(CONSENT, flow.next(STOPPED, "recomeçar").nextStep());
    }
}
