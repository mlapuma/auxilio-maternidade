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
        verify(store, never()).cancelPendingReplies(PHONE);
        verify(store).save(new WhatsAppConversationStore.Conversation(
            PHONE, WhatsAppConversationFlow.Step.CONSENT, false));
    }

    @Test
    void repeatedConsentPromptSupersedesUnsentInvitations() {
        when(store.findByPhone(PHONE)).thenReturn(Optional.of(
            new WhatsAppConversationStore.Conversation(
                PHONE, WhatsAppConversationFlow.Step.CONSENT, false)));

        var transition = processor.process(
            new WhatsAppInboundMessage("wamid.repeat", PHONE, "talvez"));

        assertEquals(WhatsAppConversationFlow.Step.CONSENT, transition.nextStep());
        verify(store).cancelPendingConsentRequests(PHONE);
        verify(store, never()).cancelPendingReplies(PHONE);
        verify(store, never()).linkUniqueExistingLead(PHONE);
    }

    @Test
    void consentMovesForward() {
        when(store.findByPhone(PHONE)).thenReturn(Optional.of(
            new WhatsAppConversationStore.Conversation(PHONE, WhatsAppConversationFlow.Step.CONSENT, false)));
        processor.process(new WhatsAppInboundMessage("wamid.2", PHONE, "sim"));
        verify(store).save(new WhatsAppConversationStore.Conversation(
            PHONE, WhatsAppConversationFlow.Step.BIRTH, true));
        verify(store).cancelPendingConsentRequests(PHONE);
        verify(store, never()).cancelPendingReplies(PHONE);
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
        verify(store).cancelPendingReplies(PHONE);
        verify(store).save(new WhatsAppConversationStore.Conversation(
            PHONE, WhatsAppConversationFlow.Step.CONSENT, false));
    }
    @Test
    void persistsBirthAndWorkAnswersAfterConsent() {
        when(store.findByPhone(PHONE)).thenReturn(Optional.of(
            new WhatsAppConversationStore.Conversation(PHONE, WhatsAppConversationFlow.Step.BIRTH, true)),
            Optional.of(new WhatsAppConversationStore.Conversation(PHONE, WhatsAppConversationFlow.Step.WORK, true)));
        processor.process(new WhatsAppInboundMessage("wamid.5", PHONE, "GESTANTE"));
        processor.process(new WhatsAppInboundMessage("wamid.6", PHONE, "NÃO SEI"));
        verify(store).saveScreeningAnswer(PHONE, "situacao_bebe", "GESTANTE");
        verify(store).saveScreeningAnswer(PHONE, "situacao_trabalho", "NAO_SEI");
    }

    @Test
    void clearsAnswersOnStop() {
        when(store.findByPhone(PHONE)).thenReturn(Optional.of(
            new WhatsAppConversationStore.Conversation(PHONE, WhatsAppConversationFlow.Step.WORK, true)));
        processor.process(new WhatsAppInboundMessage("wamid.7", PHONE, "SAIR"));
        verify(store).clearScreeningAnswers(PHONE);
        verify(store).cancelPendingReplies(PHONE);
        verify(store, never()).deleteByPhone(anyString());
    }
    @Test
    void linksExistingLeadOnlyAfterConsent() {
        when(store.findByPhone(PHONE)).thenReturn(Optional.of(
            new WhatsAppConversationStore.Conversation(PHONE, WhatsAppConversationFlow.Step.CONSENT, false)));
        processor.process(new WhatsAppInboundMessage("wamid.8", PHONE, "sim"));
        verify(store).linkUniqueExistingLead(PHONE);
    }

    @Test
    void doesNotLinkLeadWhenConsentRefused() {
        when(store.findByPhone(PHONE)).thenReturn(Optional.of(
            new WhatsAppConversationStore.Conversation(PHONE, WhatsAppConversationFlow.Step.CONSENT, false)));
        processor.process(new WhatsAppInboundMessage("wamid.9", PHONE, "não"));
        verify(store, never()).linkUniqueExistingLead(anyString());
    }
}
