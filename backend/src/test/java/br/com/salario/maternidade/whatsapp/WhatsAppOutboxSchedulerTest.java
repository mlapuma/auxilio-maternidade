package br.com.salario.maternidade.whatsapp;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WhatsAppOutboxSchedulerTest {
    @Test
    void disabledSchedulerDoesNotDispatch() {
        var dispatcher = mock(WhatsAppOutboxDispatcher.class);
        new WhatsAppOutboxScheduler(dispatcher, false, true, 5).poll();
        verifyNoInteractions(dispatcher);
    }

    @Test
    void enabledSchedulerDoesNotClaimWhenSendingDisabled() {
        var dispatcher = mock(WhatsAppOutboxDispatcher.class);
        new WhatsAppOutboxScheduler(dispatcher, true, false, 5).poll();
        verifyNoInteractions(dispatcher);
    }

    @Test
    void enabledSchedulerDispatchesOnceByDefault() {
        var dispatcher = mock(WhatsAppOutboxDispatcher.class);
        when(dispatcher.dispatchOne()).thenReturn(true);
        new WhatsAppOutboxScheduler(dispatcher, true, true, 1).poll();
        verify(dispatcher, times(1)).dispatchOne();
    }

    @Test
    void batchStopsWhenQueueBecomesEmpty() {
        var dispatcher = mock(WhatsAppOutboxDispatcher.class);
        when(dispatcher.dispatchOne()).thenReturn(true, true, false);
        new WhatsAppOutboxScheduler(dispatcher, true, true, 10).poll();
        verify(dispatcher, times(3)).dispatchOne();
    }

    @Test
    void batchNeverExceedsConfiguredMaximum() {
        var dispatcher = mock(WhatsAppOutboxDispatcher.class);
        when(dispatcher.dispatchOne()).thenReturn(true);
        new WhatsAppOutboxScheduler(dispatcher, true, true, 4).poll();
        verify(dispatcher, times(4)).dispatchOne();
    }

    @Test
    void invalidBatchSizeFailsFast() {
        var dispatcher = mock(WhatsAppOutboxDispatcher.class);
        assertThrows(IllegalArgumentException.class,
            () -> new WhatsAppOutboxScheduler(dispatcher, true, true, 0));
        assertThrows(IllegalArgumentException.class,
            () -> new WhatsAppOutboxScheduler(dispatcher, true, true, 101));
    }
}
