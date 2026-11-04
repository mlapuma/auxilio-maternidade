package br.com.salario.maternidade.whatsapp;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WhatsAppAdminHistoryControllerTest {
    private final WhatsAppAdminHistoryStore store = mock(WhatsAppAdminHistoryStore.class);
    private final WhatsAppAdminHistoryController controller = new WhatsAppAdminHistoryController(store);

    @Test
    void returnsLinkedHistoryWithoutCaching() {
        UUID leadId = UUID.randomUUID();
        var conversation = new WhatsAppAdminHistoryStore.Conversation(
            leadId, "HANDOFF", true, "{\"situacao_bebe\":\"GESTANTE\"}", List.of());
        when(store.findByLeadId(leadId, 50)).thenReturn(Optional.of(conversation));

        var response = controller.history(leadId, 50);

        assertEquals(conversation, response.getBody());
        assertTrue(response.getHeaders().getCacheControl().contains("no-store"));
    }

    @Test
    void rejectsUnboundedHistoryRequest() {
        var error = assertThrows(ResponseStatusException.class,
            () -> controller.history(UUID.randomUUID(), 101));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        verifyNoInteractions(store);
    }

    @Test
    void missingLinkedConversationReturnsNotFound() {
        UUID leadId = UUID.randomUUID();
        var error = assertThrows(ResponseStatusException.class,
            () -> controller.history(leadId, 50));
        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
    }
}
