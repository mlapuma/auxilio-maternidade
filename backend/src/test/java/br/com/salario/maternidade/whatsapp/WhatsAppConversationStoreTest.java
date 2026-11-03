package br.com.salario.maternidade.whatsapp;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WhatsAppConversationStoreTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final WhatsAppConversationStore store = new WhatsAppConversationStore(jdbc);

    @Test
    void savesStateWithoutMessageBody() {
        store.save(new WhatsAppConversationStore.Conversation(
            "5511999999999", WhatsAppConversationFlow.Step.CONSENT, false));
        verify(jdbc).update(contains("ON CONFLICT (telefone)"), any(),
            eq("5511999999999"), eq("CONSENT"), eq(false));
    }

    @Test
    void rejectsInvalidPhone() {
        assertThrows(IllegalArgumentException.class, () -> store.deleteByPhone("invalid"));
        assertThrows(IllegalArgumentException.class, () -> store.findByPhone(""));
        verifyNoInteractions(jdbc);
    }

    @Test
    void removesConversationOnRequest() {
        store.deleteByPhone("5511999999999");
        verify(jdbc).update(contains("DELETE FROM whatsapp_conversations"), eq("5511999999999"));
    }
}
