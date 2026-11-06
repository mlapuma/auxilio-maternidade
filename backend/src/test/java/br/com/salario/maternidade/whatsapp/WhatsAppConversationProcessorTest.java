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
    void savesCorrectionsSeparatelyWithoutOverwritingQuestionnaire() {
        when(store.findByPhone(PHONE)).thenReturn(Optional.of(
            new WhatsAppConversationStore.Conversation(PHONE, WhatsAppConversationFlow.Step.CORRECT_INFO, true)));
        processor.process(new WhatsAppInboundMessage("wamid.correction", PHONE, "Corrigir a resposta sobre trabalho"));
        verify(store).appendFollowUp(PHONE, "correcoes", "Corrigir a resposta sobre trabalho");
        verify(store, never()).saveScreeningAnswer(anyString(), anyString(), anyString());
    }

    @Test
    void menuDoesNotBecomeAComplement() {
        when(store.findByPhone(PHONE)).thenReturn(Optional.of(
            new WhatsAppConversationStore.Conversation(PHONE, WhatsAppConversationFlow.Step.ADD_INFO, true)));
        processor.process(new WhatsAppInboundMessage("wamid.menu", PHONE, "MENU"));
        verify(store, never()).appendFollowUp(anyString(), anyString(), anyString());
    }

    @Test
    void recordsTeamRequestAndCustomQuestion() {
        when(store.findByPhone(PHONE)).thenReturn(Optional.of(
            new WhatsAppConversationStore.Conversation(PHONE, WhatsAppConversationFlow.Step.HANDOFF, true)),
            Optional.of(new WhatsAppConversationStore.Conversation(PHONE, WhatsAppConversationFlow.Step.FAQ, true)));
        processor.process(new WhatsAppInboundMessage("wamid.team", PHONE, "4"));
        processor.process(new WhatsAppInboundMessage("wamid.question", PHONE, "Posso complementar depois?"));
        verify(store).appendFollowUp(PHONE, "solicitacoes_equipe", "Solicitado contato da equipe");
        verify(store).appendFollowUp(PHONE, "duvidas", "Posso complementar depois?");
    }

    @Test
    void showsActualLeadStatusWithoutPromisingBenefitApproval() {
        when(store.findByPhone(PHONE)).thenReturn(Optional.of(
            new WhatsAppConversationStore.Conversation(PHONE, WhatsAppConversationFlow.Step.HANDOFF, true)));
        when(store.findLinkedLeadStatus(PHONE)).thenReturn(Optional.of("CONCLUIDO"));
        var result = processor.process(new WhatsAppInboundMessage("wamid.status", PHONE, "5"));
        assertTrue(result.reply().contains("não indica aprovação"));
        verify(store, never()).appendFollowUp(anyString(), anyString(), anyString());
    }

    @Test
    void stopInCorrectionDoesNotStoreTheCommandAsAnAnswer() {
        when(store.findByPhone(PHONE)).thenReturn(Optional.of(
            new WhatsAppConversationStore.Conversation(PHONE, WhatsAppConversationFlow.Step.CORRECT_INFO, true)));
        var result = processor.process(new WhatsAppInboundMessage("wamid.stop", PHONE, "SAIR"));
        assertEquals(WhatsAppConversationFlow.Step.STOPPED, result.nextStep());
        verify(store, never()).appendFollowUp(anyString(), anyString(), anyString());
    }

    @Test
    void resumesVerifiedLandingQuestionnaireWithoutRepeatingQuestions() {
        String protocol = "SM-0123456789ABCDEF";
        when(store.findByPhone(PHONE)).thenReturn(Optional.empty());
        when(store.resumeLandingQuestionnaire(PHONE, protocol)).thenReturn(true);
        var transition = processor.process(new WhatsAppInboundMessage("wamid.landing", PHONE, "Codigo " + protocol));
        assertEquals(WhatsAppConversationFlow.Step.HANDOFF, transition.nextStep());
        assertTrue(transition.reply().contains("não precisa responder novamente"));
        assertTrue(transition.requiresHuman());
        verify(store).cancelPendingReplies(PHONE);
        verify(store, never()).save(any());
    }

    @Test
    void unmatchedLandingCodeDoesNotGrantConsentOrRevealAnswers() {
        when(store.findByPhone(PHONE)).thenReturn(Optional.empty());
        var transition = processor.process(new WhatsAppInboundMessage("wamid.unknown", PHONE,
            "Codigo SM-0123456789ABCDEF"));
        assertEquals(WhatsAppConversationFlow.Step.CONSENT, transition.nextStep());
        verify(store).save(new WhatsAppConversationStore.Conversation(PHONE, WhatsAppConversationFlow.Step.CONSENT, false));
        verify(store, never()).cancelPendingReplies(PHONE);
    }

    @Test
    void landingCodeDoesNotOverridePreviousOptOut() {
        when(store.findByPhone(PHONE)).thenReturn(Optional.of(
            new WhatsAppConversationStore.Conversation(PHONE, WhatsAppConversationFlow.Step.STOPPED, false)));
        var transition = processor.process(new WhatsAppInboundMessage("wamid.stopped", PHONE,
            "Codigo SM-0123456789ABCDEF"));
        assertEquals(WhatsAppConversationFlow.Step.STOPPED, transition.nextStep());
        verify(store, never()).resumeLandingQuestionnaire(anyString(), anyString());
    }

    @Test
    void newConversationRequestsConsent() {
        when(store.findByPhone(PHONE)).thenReturn(Optional.empty());
        var transition = processor.process(new WhatsAppInboundMessage("wamid.1", PHONE, "Oi"));
        assertEquals(WhatsAppConversationFlow.Step.CONSENT, transition.nextStep());
        verify(store, never()).cancelPendingReplies(PHONE);
        verify(store, never()).unlinkLead(PHONE);
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
        verify(store).cancelPendingStandardReplies(PHONE);
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
        verify(store).unlinkLead(PHONE);
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
        verify(store, times(2)).cancelPendingStandardReplies(PHONE);
    }

    @Test
    void clearsAnswersOnStop() {
        when(store.findByPhone(PHONE)).thenReturn(Optional.of(
            new WhatsAppConversationStore.Conversation(PHONE, WhatsAppConversationFlow.Step.WORK, true)));
        processor.process(new WhatsAppInboundMessage("wamid.7", PHONE, "SAIR"));
        verify(store).clearScreeningAnswers(PHONE);
        verify(store).unlinkLead(PHONE);
        verify(store).cancelPendingReplies(PHONE);
        verify(store, never()).cancelPendingStandardReplies(PHONE);
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
