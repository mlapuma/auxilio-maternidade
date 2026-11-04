package br.com.salario.maternidade.whatsapp;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WhatsAppConversationProcessorTest {
    private final WhatsAppConversationStore store = mock(WhatsAppConversationStore.class);
    private final WhatsAppConversationProcessor processor = new WhatsAppConversationProcessor(store);
    private static final String PHONE = "5511999999999";

    @Test
    void newConversationRequestsConsent() {
        when(store.findByPhone(PHONE)).thenReturn(Optional.empty());
        var transition = processor.process(new WhatsAppInboundMessage("wamid.1", PHONE, "Oi"));
        assertEquals(WhatsAppConversationFlow.Step.CONSENT, transition.nextStep());
        verify(store).save(new WhatsAppConversationStore.Conversation(
            PHONE, WhatsAppConversationFlow.Step.CONSENT, false));
    }

    @Test
    void consentMovesForward() {
        when(store.findByPhone(PHONE)).thenReturn(Optional.of(
            new WhatsAppConversationStore.Conversation(PHONE, WhatsAppConversationFlow.Step.CONSENT, false)));
        processor.process(new WhatsAppInboundMessage("wamid.2", PHONE, "sim"));
        verify(store).save(new WhatsAppConversationStore.Conversation(
            PHONE, WhatsAppConversationFlow.Step.BIRTH, true));
    }

    @Test
    void refusalStopsConversationWithoutDeletingIt() {
        when(store.findByPhone(PHONE)).thenReturn(Optional.of(
            new WhatsAppConversationStore.Conversation(PHONE, WhatsAppConversationFlow.Step.CONSENT, false)));
        processor.process(new WhatsAppInboundMessage("wamid.3", PHONE, "não"));
        verify(store).save(new WhatsAppConversationStore.Conversation(
            PHONE, WhatsAppConversationFlow.Step.STOPPED, false));
        verify(store, never()).deleteByPhone(anyString());
    }
    @Test
    void stoppedConversationCanRestartWithoutPreviousConsent() {
        when(store.findByPhone(PHONE)).thenReturn(Optional.of(
            new WhatsAppConversationStore.Conversation(PHONE, WhatsAppConversationFlow.Step.STOPPED, false)));
        var transition = processor.process(new WhatsAppInboundMessage("wamid.4", PHONE, "INICIAR"));
        assertEquals(WhatsAppConversationFlow.Step.CONSENT, transition.nextStep());
        verify(store).save(new WhatsAppConversationStore.Conversation(
            PHONE, WhatsAppConversationFlow.Step.CONSENT, false));
    }
}
