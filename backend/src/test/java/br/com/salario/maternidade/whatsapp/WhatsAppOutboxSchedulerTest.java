package br.com.salario.maternidade.whatsapp;

import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;

class WhatsAppOutboxSchedulerTest {
    @Test
    void disabledSchedulerDoesNotDispatch() {
        var dispatcher = mock(WhatsAppOutboxDispatcher.class);
        new WhatsAppOutboxScheduler(dispatcher, false, true).poll();
        verifyNoInteractions(dispatcher);
    }

    @Test
    void enabledSchedulerDoesNotClaimWhenSendingDisabled() {
        var dispatcher = mock(WhatsAppOutboxDispatcher.class);
        new WhatsAppOutboxScheduler(dispatcher, true, false).poll();
        verifyNoInteractions(dispatcher);
    }

    @Test
    void enabledSchedulerDispatchesOncePerTick() {
        var dispatcher = mock(WhatsAppOutboxDispatcher.class);
        new WhatsAppOutboxScheduler(dispatcher, true, true).poll();
        verify(dispatcher, times(1)).dispatchOne();
    }
}
