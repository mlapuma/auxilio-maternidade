package br.com.salario.maternidade.whatsapp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Validates webhook orchestration without external Meta credentials or a live DB. */
class WhatsAppWebhookPipelineTest {
    private static final String PHONE = "5511999999999";
    private static final String PAYLOAD = """
        {"object":"whatsapp_business_account","entry":[{"changes":[{"value":{
          "messages":[{"id":"wamid.pipeline.1","from":"5511999999999",
          "type":"text","text":{"body":"SIM"}}]}}]}]}
        """;

    @Test
    void newMessageAdvancesOnceAndRecordsHistoryAndQueuesReply() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        WhatsAppConversationProcessor processor = mock(WhatsAppConversationProcessor.class);
        WhatsAppMessageHistoryStore history = mock(WhatsAppMessageHistoryStore.class);
        WhatsAppReplyOutbox outbox = mock(WhatsAppReplyOutbox.class);
        var inbound = new WhatsAppInboundMessage("wamid.pipeline.1", PHONE, "SIM");
        when(jdbc.update(anyString(), any(), any(), any(), any(), any()))
            .thenReturn(1, 0);
        when(processor.process(inbound)).thenReturn(
            new WhatsAppConversationFlow.Transition(
                WhatsAppConversationFlow.Step.BIRTH, "Qual a situação do bebê?", false));
        var service = new WhatsAppWebhookService(new ObjectMapper(), jdbc,
            new WhatsAppInboundMessageParser(new ObjectMapper()), processor, outbox, history);

        service.accept(PAYLOAD);
        service.accept(PAYLOAD);

        verify(processor, times(1)).process(inbound);
        verify(history, times(1)).recordInbound(inbound);
        verify(outbox, times(1)).enqueue(
            "wamid.pipeline.1", PHONE, "Qual a situação do bebê?");
    }

    @Test
    void duplicateIdsInsideOnePayloadAdvanceConversationOnlyOnce() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        WhatsAppConversationProcessor processor = mock(WhatsAppConversationProcessor.class);
        WhatsAppMessageHistoryStore history = mock(WhatsAppMessageHistoryStore.class);
        WhatsAppReplyOutbox outbox = mock(WhatsAppReplyOutbox.class);
        var inbound = new WhatsAppInboundMessage("wamid.same", PHONE, "SIM");
        when(jdbc.update(anyString(), any(), any(), any(), any(), any()))
            .thenReturn(1, 0);
        when(processor.process(inbound)).thenReturn(
            new WhatsAppConversationFlow.Transition(
                WhatsAppConversationFlow.Step.BIRTH, "Qual a situação do bebê?", false));
        var service = new WhatsAppWebhookService(new ObjectMapper(), jdbc,
            new WhatsAppInboundMessageParser(new ObjectMapper()), processor, outbox, history);

        service.accept("""
            {"object":"whatsapp_business_account","entry":[{"changes":[{"value":{
              "messages":[
                {"id":"wamid.same","from":"5511999999999","type":"text","text":{"body":"SIM"}},
                {"id":"wamid.same","from":"5511999999999","type":"text","text":{"body":"SIM"}}
              ]}}]}]}
            """);

        verify(processor, times(1)).process(inbound);
        verify(history, times(1)).recordInbound(inbound);
        verify(outbox, times(1)).enqueue("wamid.same", PHONE, "Qual a situação do bebê?");
    }

    @Test
    void duplicateEventDoesNotRecordHistoryOrQueueReply() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        WhatsAppConversationProcessor processor = mock(WhatsAppConversationProcessor.class);
        WhatsAppMessageHistoryStore history = mock(WhatsAppMessageHistoryStore.class);
        WhatsAppReplyOutbox outbox = mock(WhatsAppReplyOutbox.class);
        var service = new WhatsAppWebhookService(new ObjectMapper(), jdbc,
            new WhatsAppInboundMessageParser(new ObjectMapper()), processor, outbox, history);

        service.accept(PAYLOAD);

        verifyNoInteractions(processor, history, outbox);
    }

    @Test
    void statusUpdateDoesNotAdvanceConversationOrSendReply() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        WhatsAppConversationProcessor processor = mock(WhatsAppConversationProcessor.class);
        WhatsAppMessageHistoryStore history = mock(WhatsAppMessageHistoryStore.class);
        WhatsAppReplyOutbox outbox = mock(WhatsAppReplyOutbox.class);
        var service = new WhatsAppWebhookService(new ObjectMapper(), jdbc,
            new WhatsAppInboundMessageParser(new ObjectMapper()), processor, outbox, history);
        when(jdbc.update(anyString(), any(), any(), any(), any(), any())).thenReturn(1);

        service.accept("""
            {"object":"whatsapp_business_account","entry":[{"changes":[{"value":{
              "statuses":[{"id":"wamid.pipeline.1","status":"delivered"}]}}]}]}
            """);

        verifyNoInteractions(processor, history, outbox);
    }

    @Test
    void failedProcessingDoesNotQueueReplyOrRecordHistory() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        WhatsAppConversationProcessor processor = mock(WhatsAppConversationProcessor.class);
        WhatsAppMessageHistoryStore history = mock(WhatsAppMessageHistoryStore.class);
        WhatsAppReplyOutbox outbox = mock(WhatsAppReplyOutbox.class);
        var inbound = new WhatsAppInboundMessage("wamid.pipeline.1", PHONE, "SIM");
        when(jdbc.update(anyString(), any(), any(), any(), any(), any())).thenReturn(1);
        when(processor.process(inbound)).thenThrow(new IllegalStateException("Database unavailable"));
        var service = new WhatsAppWebhookService(new ObjectMapper(), jdbc,
            new WhatsAppInboundMessageParser(new ObjectMapper()), processor, outbox, history);

        assertThrows(IllegalStateException.class, () -> service.accept(PAYLOAD));
        verifyNoInteractions(history, outbox);
    }
}
