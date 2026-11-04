package br.com.salario.maternidade.whatsapp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WhatsAppReplyOutboxTest {
    @Test
    void enqueuesReplyForNewIncomingMessage() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        WhatsAppConversationProcessor processor = mock(WhatsAppConversationProcessor.class);
        WhatsAppReplyOutbox outbox = mock(WhatsAppReplyOutbox.class);
        when(jdbc.update(anyString(), any(), any(), any(), any(), any())).thenReturn(1);
        when(processor.process(any())).thenReturn(
            new WhatsAppConversationFlow.Transition(
                WhatsAppConversationFlow.Step.CONSENT, "Responda SIM ou NÃO", false));
        var service = new WhatsAppWebhookService(new ObjectMapper(), jdbc,
            new WhatsAppInboundMessageParser(new ObjectMapper()), processor, outbox);
        service.accept("""
            {"object":"whatsapp_business_account","entry":[{"changes":[{"value":{
              "messages":[{"id":"wamid.123","from":"5511999999999","type":"text",
              "text":{"body":"Oi"}}]}}]}]}
            """);
        verify(outbox).enqueue("wamid.123", "5511999999999", "Responda SIM ou NÃO", true);
    }

    @Test
    void duplicateDoesNotQueueAnotherReply() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        WhatsAppConversationProcessor processor = mock(WhatsAppConversationProcessor.class);
        WhatsAppReplyOutbox outbox = mock(WhatsAppReplyOutbox.class);
        var service = new WhatsAppWebhookService(new ObjectMapper(), jdbc,
            new WhatsAppInboundMessageParser(new ObjectMapper()), processor, outbox);
        service.accept("""
            {"object":"whatsapp_business_account","entry":[{"changes":[{"value":{
              "messages":[{"id":"wamid.123","from":"5511999999999","type":"text",
              "text":{"body":"Oi"}}]}}]}]}
            """);
        verifyNoInteractions(processor, outbox);
    }
}
