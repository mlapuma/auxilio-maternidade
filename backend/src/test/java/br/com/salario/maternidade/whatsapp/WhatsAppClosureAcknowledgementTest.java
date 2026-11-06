package br.com.salario.maternidade.whatsapp;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WhatsAppClosureAcknowledgementTest {
    @Test
    void explicitStopGetsOneAcknowledgementAndClosedMessagesDoNotGenerateMenus() throws Exception {
        String phone = "5511999999999";
        var jdbc = mock(JdbcTemplate.class);
        var store = mock(WhatsAppConversationStore.class);
        var outbox = mock(WhatsAppReplyOutbox.class);
        var mapper = new ObjectMapper();
        var state = new AtomicReference<>(new WhatsAppConversationStore.Conversation(phone, WhatsAppConversationFlow.Step.ACTION_DONE, true));
        var seen = new HashSet<String>();
        when(jdbc.update(anyString(), any(), any(), any(), any(), any())).thenAnswer(i -> seen.add(i.getArgument(2)) ? 1 : 0);
        when(store.findByPhone(phone)).thenAnswer(i -> Optional.of(state.get()));
        doAnswer(i -> { state.set(i.getArgument(0)); return null; }).when(store).save(any());
        var service = new WhatsAppWebhookService(mapper, jdbc, new WhatsAppInboundMessageParser(mapper),
            new WhatsAppConversationProcessor(store), outbox);
        String stop = payload("wamid.stop", "SAIR");
        service.accept(stop);
        service.accept(stop);
        service.accept(payload("wamid.closed", "MENU"));
        verify(outbox, times(1)).enqueueClosureAck(eq("wamid.stop"), eq(phone), contains("encerrado"));
        verify(outbox, never()).enqueue(eq("wamid.closed"), anyString(), anyString());
        verify(outbox, never()).enqueueClosureAck(eq("wamid.closed"), anyString(), anyString());
        assertEquals(WhatsAppConversationFlow.Step.STOPPED, state.get().step());
        assertFalse(state.get().consent());
    }

    private static String payload(String id, String body) {
        return """
            {"object":"whatsapp_business_account","entry":[{"changes":[{"value":{"messages":[
            {"id":"%s","from":"5511999999999","type":"text","text":{"body":"%s"}}]}}]}]}
            """.formatted(id, body);
    }
}
