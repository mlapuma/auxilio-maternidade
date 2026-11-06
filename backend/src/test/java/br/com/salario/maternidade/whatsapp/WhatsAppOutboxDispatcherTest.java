package br.com.salario.maternidade.whatsapp;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WhatsAppOutboxDispatcherTest {
    @Test
    void usesPersistedDynamicChoicesInsteadOfGenericStepMenu() {
        String payload = "{\"type\":\"list\"}";
        when(store.claimOne()).thenReturn(Optional.of(new WhatsAppOutboxDispatchStore.PendingReply(
            id, "5511999999999", "Escolha a resposta", "wamid.inbound", "CORRECT_INFO", payload)));
        when(store.stillEligible(id)).thenReturn(true);
        assertTrue(dispatcher.dispatchOne());
        verify(client).sendInteractivePayload("5511999999999", payload);
        verify(client, never()).sendInteractiveReply(anyString(), anyString(), anyString());
        verify(store).markSent(id);
    }
    @Test
    void dispatchesInteractiveReplyUsingPersistedQuestion() {
        when(store.claimOne()).thenReturn(Optional.of(new WhatsAppOutboxDispatchStore.PendingReply(
            id, "5511999999999", "O bebê já nasceu?", "wamid.inbound", "BIRTH")));
        when(store.stillEligible(id)).thenReturn(true);
        assertTrue(dispatcher.dispatchOne());
        verify(client).sendInteractiveReply("5511999999999", "O bebê já nasceu?", "BIRTH");
        verify(client, never()).sendText(anyString(), anyString());
        verify(store).markSent(id);
    }
    @Test
    void showsTypingBeforeSendingEligibleReply() {
        when(store.claimOne()).thenReturn(Optional.of(
            new WhatsAppOutboxDispatchStore.PendingReply(id, "5511999999999", "Olá", "wamid.inbound")));
        when(store.stillEligible(id)).thenReturn(true);
        assertTrue(dispatcher.dispatchOne());
        var order = inOrder(client, store);
        order.verify(client).sendTypingIndicator("5511999999999", "wamid.inbound");
        order.verify(client).sendText("5511999999999", "Olá");
        order.verify(store).markSent(id);
    }

    @Test
    void indicatorFailureDoesNotPreventReplyOrRequireReview() {
        when(store.claimOne()).thenReturn(Optional.of(
            new WhatsAppOutboxDispatchStore.PendingReply(id, "5511999999999", "Olá", "wamid.inbound")));
        when(store.stillEligible(id)).thenReturn(true);
        doThrow(new IllegalStateException("indicator unavailable")).when(client)
            .sendTypingIndicator("5511999999999", "wamid.inbound");
        assertTrue(dispatcher.dispatchOne());
        verify(client).sendText("5511999999999", "Olá");
        verify(store).markSent(id);
        verify(store, never()).markForReview(id);
    }

    @Test
    void revokedConsentDoesNotShowTyping() {
        when(store.claimOne()).thenReturn(Optional.of(
            new WhatsAppOutboxDispatchStore.PendingReply(id, "5511999999999", "Olá", "wamid.inbound")));
        when(store.stillEligible(id)).thenReturn(false);
        assertTrue(dispatcher.dispatchOne());
        verifyNoInteractions(client);
    }
    private final WhatsAppOutboxDispatchStore store = mock(WhatsAppOutboxDispatchStore.class);
    private final WhatsAppCloudApiClient client = mock(WhatsAppCloudApiClient.class);
    private final WhatsAppOutboxDispatcher dispatcher = new WhatsAppOutboxDispatcher(store, client);
    private final UUID id = UUID.randomUUID();

    @Test
    void noPendingMessagesMeansNoSend() {
        when(store.claimOne()).thenReturn(Optional.empty());
        assertFalse(dispatcher.dispatchOne());
        verify(store).expireStalePendingReplies();
        verify(store).flagStuckSendingForReview();
        verifyNoInteractions(client);
    }

    @Test
    void revokedConsentCancelsClaimedMessageWithoutCallingMeta() {
        when(store.claimOne()).thenReturn(Optional.of(
            new WhatsAppOutboxDispatchStore.PendingReply(id, "5511999999999", "Olá")));
        when(store.stillEligible(id)).thenReturn(false);

        assertTrue(dispatcher.dispatchOne());

        verify(store).cancelClaimedReply(id);
        verify(store, never()).markSent(id);
        verify(store, never()).markForReview(id);
        verifyNoInteractions(client);
    }

    @Test
    void successfulSendMarksSent() {
        when(store.claimOne()).thenReturn(Optional.of(
            new WhatsAppOutboxDispatchStore.PendingReply(id, "5511999999999", "Olá")));
        when(store.stillEligible(id)).thenReturn(true);
        assertTrue(dispatcher.dispatchOne());
        verify(client).sendText("5511999999999", "Olá");
        verify(store).markSent(id);
        verify(store, never()).markForReview(id);
    }

    @Test
    void failedSendRequiresManualReviewWithoutRetry() {
        when(store.claimOne()).thenReturn(Optional.of(
            new WhatsAppOutboxDispatchStore.PendingReply(id, "5511999999999", "Olá")));
        doThrow(new IllegalStateException("disabled")).when(client)
            .sendText("5511999999999", "Olá");
        when(store.stillEligible(id)).thenReturn(true);
        assertTrue(dispatcher.dispatchOne());
        verify(client, times(1)).sendText("5511999999999", "Olá");
        verify(store).markForReview(id);
        verify(store, never()).markSent(id);
    }
    @Test
    void persistenceFailureAfterMetaSubmissionRequiresReviewWithoutResending() {
        when(store.claimOne()).thenReturn(Optional.of(
            new WhatsAppOutboxDispatchStore.PendingReply(id, "5511999999999", "Olá")));
        when(store.stillEligible(id)).thenReturn(true);
        doThrow(new IllegalStateException("database unavailable")).when(store).markSent(id);

        assertTrue(dispatcher.dispatchOne());

        verify(client, times(1)).sendText("5511999999999", "Olá");
        verify(store).markSent(id);
        verify(store).markForReview(id);
    }

    @Test
    void eligibilityCheckFailureDoesNotCallMeta() {
        when(store.claimOne()).thenReturn(Optional.of(
            new WhatsAppOutboxDispatchStore.PendingReply(id, "5511999999999", "Olá")));
        when(store.stillEligible(id)).thenThrow(new IllegalStateException("database unavailable"));

        assertTrue(dispatcher.dispatchOne());

        verifyNoInteractions(client);
        verify(store).markForReview(id);
    }
}
