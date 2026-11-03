package br.com.salario.maternidade.whatsapp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WhatsAppWebhookConversationIntegrationTest {
    private static final String PAYLOAD = """
        {"object":"whatsapp_business_account","entry":[{"changes":[{"value":{
          "messages":[{"id":"wamid.123","from":"5511999999999","type":"text",
          "text":{"body":"Oi"}}]}}]}]}
        """;

    @Test
    void processesOnlyNewlyInsertedMessages() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        WhatsAppConversationProcessor processor = mock(WhatsAppConversationProcessor.class);
        var service = new WhatsAppWebhookService(new ObjectMapper(), jdbc,
            new WhatsAppInboundMessageParser(new ObjectMapper()), processor);
        when(jdbc.update(anyString(), any(), any(), any(), any(), any())).thenReturn(1);
        service.accept(PAYLOAD);
        verify(processor).process(new WhatsAppInboundMessage("wamid.123", "5511999999999", "Oi"));
    }

    @Test
    void duplicateEventDoesNotAdvanceConversation() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        WhatsAppConversationProcessor processor = mock(WhatsAppConversationProcessor.class);
        var service = new WhatsAppWebhookService(new ObjectMapper(), jdbc,
            new WhatsAppInboundMessageParser(new ObjectMapper()), processor);
        service.accept(PAYLOAD);
        verifyNoInteractions(processor);
    }
}
