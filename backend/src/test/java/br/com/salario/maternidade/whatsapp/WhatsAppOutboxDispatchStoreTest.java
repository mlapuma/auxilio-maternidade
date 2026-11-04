package br.com.salario.maternidade.whatsapp;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WhatsAppOutboxDispatchStoreTest {
    @Test
    void marksSentAndRecordsOnlyConsentedOutboundHistory() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        var store = new WhatsAppOutboxDispatchStore(jdbc);
        UUID id = UUID.randomUUID();

        store.markSent(id);

        verify(jdbc).update(argThat(sql -> sql.contains("status = 'ENVIADO'")), eq(id));
        verify(jdbc).update(argThat(sql ->
            sql.contains("INSERT INTO whatsapp_message_history")
            && sql.contains("c.consentimento = true")
            && sql.contains("o.status = 'ENVIADO'")
            && sql.contains("ON CONFLICT (mensagem_externa_id) DO NOTHING")),
            any(), eq(id));
    }

    @Test
    void manualReviewDoesNotWriteHistory() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        var store = new WhatsAppOutboxDispatchStore(jdbc);
        UUID id = UUID.randomUUID();

        store.markForReview(id);

        verify(jdbc, times(1)).update(anyString(), eq(id));
    }
}
