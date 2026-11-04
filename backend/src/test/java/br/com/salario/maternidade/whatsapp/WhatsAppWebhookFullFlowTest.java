package br.com.salario.maternidade.whatsapp;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Exercises real parser, webhook orchestration, conversation processor and flow
 * together, with only persistence and outbound delivery mocked.
 */
class WhatsAppWebhookFullFlowTest {
    private static final String PHONE = "5511999999999";

    @Test
    void webhookProcessesFullScreeningExactlyOnceAndEnqueuesExpectedReplies() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        WhatsAppConversationStore store = mock(WhatsAppConversationStore.class);
        WhatsAppReplyOutbox outbox = mock(WhatsAppReplyOutbox.class);
        WhatsAppMessageHistoryStore history = mock(WhatsAppMessageHistoryStore.class);
        AtomicReference<WhatsAppConversationStore.Conversation> state = new AtomicReference<>();
        List<String> answers = new ArrayList<>();

        when(store.findByPhone(PHONE)).thenAnswer(inv -> Optional.ofNullable(state.get()));
        doAnswer(inv -> {
            state.set(inv.getArgument(0));
            return null;
        }).when(store).save(any(WhatsAppConversationStore.Conversation.class));
        doAnswer(inv -> {
            answers.add(inv.getArgument(1) + "=" + inv.getArgument(2));
            return null;
        }).when(store).saveScreeningAnswer(eq(PHONE), anyString(), anyString());

        // Simulate the unique constraint on eventos_webhook for repeated webhook IDs.
        java.util.Set<String> accepted = new java.util.HashSet<>();
        when(jdbc.update(anyString(), any(), any(), any(), any(), any()))
            .thenAnswer(inv -> accepted.add(inv.getArgument(2)) ? 1 : 0);

        ObjectMapper mapper = new ObjectMapper();
        var service = new WhatsAppWebhookService(mapper, jdbc,
            new WhatsAppInboundMessageParser(mapper),
            new WhatsAppConversationProcessor(store), outbox, history);

        acceptTwice(service, "wamid.full.1", "Oi");
        assertEquals(WhatsAppConversationFlow.Step.CONSENT, state.get().step());
        assertFalse(state.get().consent());

        acceptTwice(service, "wamid.full.2", "SIM");
        assertEquals(WhatsAppConversationFlow.Step.BIRTH, state.get().step());
        assertTrue(state.get().consent());

        acceptTwice(service, "wamid.full.3", "GESTANTE");
        acceptTwice(service, "wamid.full.4", "NÃO SEI");
        assertEquals(WhatsAppConversationFlow.Step.HANDOFF, state.get().step());
        assertEquals(List.of("situacao_bebe=GESTANTE", "situacao_trabalho=NAO_SEI"), answers);

        verify(outbox, times(3)).enqueue(anyString(), eq(PHONE), anyString());
        verify(outbox).enqueue(eq("wamid.full.1"), eq(PHONE), anyString(), eq(true));
        verify(history, times(4)).recordInbound(any(WhatsAppInboundMessage.class));
        verify(store, times(3)).linkUniqueExistingLead(PHONE);
        verify(store, times(4)).save(any(WhatsAppConversationStore.Conversation.class));
    }

    @Test
    void webhookRefusalNeverLinksLeadOrStoresAnswers() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        WhatsAppConversationStore store = mock(WhatsAppConversationStore.class);
        WhatsAppReplyOutbox outbox = mock(WhatsAppReplyOutbox.class);
        WhatsAppMessageHistoryStore history = mock(WhatsAppMessageHistoryStore.class);
        AtomicReference<WhatsAppConversationStore.Conversation> state = new AtomicReference<>();
        when(jdbc.update(anyString(), any(), any(), any(), any(), any())).thenReturn(1);
        when(store.findByPhone(PHONE)).thenAnswer(inv -> Optional.ofNullable(state.get()));
        doAnswer(inv -> {
            state.set(inv.getArgument(0));
            return null;
        }).when(store).save(any(WhatsAppConversationStore.Conversation.class));
        ObjectMapper mapper = new ObjectMapper();
        var service = new WhatsAppWebhookService(mapper, jdbc,
            new WhatsAppInboundMessageParser(mapper),
            new WhatsAppConversationProcessor(store), outbox, history);

        service.accept(payload("wamid.refusal.1", "Oi"));
        service.accept(payload("wamid.refusal.2", "NÃO"));

        assertEquals(WhatsAppConversationFlow.Step.STOPPED, state.get().step());
        assertFalse(state.get().consent());
        verify(store, never()).linkUniqueExistingLead(anyString());
        verify(store, never()).saveScreeningAnswer(anyString(), anyString(), anyString());
        verify(outbox).enqueue(eq("wamid.refusal.1"), eq(PHONE), anyString(), eq(true));
        verify(outbox, never()).enqueue(eq("wamid.refusal.2"), eq(PHONE), anyString());
        verify(outbox, never()).enqueue(eq("wamid.refusal.2"), eq(PHONE), anyString(), anyBoolean());
    }

    private static void acceptTwice(WhatsAppWebhookService service, String id, String body)
            throws Exception {
        service.accept(payload(id, body));
        service.accept(payload(id, body));
    }

    private static String payload(String id, String body) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            return "{\"object\":\"whatsapp_business_account\",\"entry\":[{\"changes\":[{\"value\":"
                + "{\"messages\":[{\"id\":" + mapper.writeValueAsString(id)
                + ",\"from\":\"" + PHONE + "\",\"type\":\"text\",\"text\":{\"body\":"
                + mapper.writeValueAsString(body) + "}}]}}]}]}";
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
