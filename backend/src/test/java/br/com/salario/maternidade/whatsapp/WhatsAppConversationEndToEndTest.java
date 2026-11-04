package br.com.salario.maternidade.whatsapp;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Full conversation flow with a stateful repository double, without requiring
 * external WhatsApp credentials or a live database.
 */
class WhatsAppConversationEndToEndTest {
    private static final String PHONE = "5511999999999";

    @Test
    void completesConsentAndScreeningThenStopsAndRestartsWithoutReusingAnswers() {
        WhatsAppConversationStore store = mock(WhatsAppConversationStore.class);
        AtomicReference<WhatsAppConversationStore.Conversation> state = new AtomicReference<>();
        List<String> answers = new ArrayList<>();
        when(store.findByPhone(PHONE)).thenAnswer(inv -> Optional.ofNullable(state.get()));
        doAnswer(inv -> {
            state.set(inv.getArgument(0));
            return null;
        }).when(store).save(any(WhatsAppConversationStore.Conversation.class));
        doAnswer(inv -> {
            answers.add(inv.getArgument(1) + "=" + inv.getArgument(2));
            return null;
        }).when(store).saveScreeningAnswer(eq(PHONE), anyString(), anyString());
        doAnswer(inv -> {
            answers.clear();
            return null;
        }).when(store).clearScreeningAnswers(PHONE);

        var processor = new WhatsAppConversationProcessor(store);
        assertEquals(WhatsAppConversationFlow.Step.CONSENT, send(processor, 1, "Oi").nextStep());
        assertFalse(state.get().consent());
        verify(store, never()).linkUniqueExistingLead(anyString());

        assertEquals(WhatsAppConversationFlow.Step.BIRTH, send(processor, 2, "SIM").nextStep());
        assertTrue(state.get().consent());
        assertEquals(WhatsAppConversationFlow.Step.WORK, send(processor, 3, "GESTANTE").nextStep());
        assertEquals(WhatsAppConversationFlow.Step.HANDOFF, send(processor, 4, "NÃO SEI").nextStep());
        assertEquals(List.of("situacao_bebe=GESTANTE", "situacao_trabalho=NAO_SEI"), answers);
        verify(store, times(3)).linkUniqueExistingLead(PHONE);

        assertEquals(WhatsAppConversationFlow.Step.STOPPED, send(processor, 5, "SAIR").nextStep());
        assertFalse(state.get().consent());
        assertTrue(answers.isEmpty());
        assertEquals(WhatsAppConversationFlow.Step.CONSENT, send(processor, 6, "INICIAR").nextStep());
        assertFalse(state.get().consent());
        assertTrue(answers.isEmpty());
        verify(store, times(3)).linkUniqueExistingLead(PHONE);
        assertEquals(WhatsAppConversationFlow.Step.BIRTH, send(processor, 7, "SIM").nextStep());
        verify(store, times(4)).linkUniqueExistingLead(PHONE);
    }

    @Test
    void refusalDoesNotSaveAnswersOrAssociateLead() {
        WhatsAppConversationStore store = mock(WhatsAppConversationStore.class);
        when(store.findByPhone(PHONE)).thenReturn(Optional.empty(), Optional.of(
            new WhatsAppConversationStore.Conversation(PHONE, WhatsAppConversationFlow.Step.CONSENT, false)));
        var processor = new WhatsAppConversationProcessor(store);
        assertEquals(WhatsAppConversationFlow.Step.CONSENT, send(processor, 1, "Oi").nextStep());
        assertEquals(WhatsAppConversationFlow.Step.STOPPED, send(processor, 2, "NÃO").nextStep());
        verify(store, never()).saveScreeningAnswer(anyString(), anyString(), anyString());
        verify(store, never()).linkUniqueExistingLead(anyString());
    }

    private static WhatsAppConversationFlow.Transition send(
            WhatsAppConversationProcessor processor, int number, String text) {
        return processor.process(new WhatsAppInboundMessage("wamid.flow." + number, PHONE, text));
    }
}
