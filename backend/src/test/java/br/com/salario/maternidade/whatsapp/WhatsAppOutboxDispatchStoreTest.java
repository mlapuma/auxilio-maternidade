package br.com.salario.maternidade.whatsapp;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WhatsAppOutboxDispatchStoreTest {
    @Test
    void flagsOrphanedSendingWithoutRetrying() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        var store = new WhatsAppOutboxDispatchStore(jdbc);

        store.flagStuckSendingForReview();

        verify(jdbc).update(org.mockito.ArgumentMatchers.<String>argThat(sql ->
            sql.contains("status = 'REVISAR'")
            && sql.contains("status = 'ENVIANDO'")
            && sql.contains("atualizado_em < CURRENT_TIMESTAMP - INTERVAL '10 minutes'")));
    }

    @Test
    void expiresOnlyOldPendingReplies() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        var store = new WhatsAppOutboxDispatchStore(jdbc);

        store.expireStalePendingReplies();

        verify(jdbc).update(org.mockito.ArgumentMatchers.<String>argThat(sql ->
            sql.contains("status = 'EXPIRADO'")
            && sql.contains("status = 'PENDENTE'")
            && sql.contains("criado_em < CURRENT_TIMESTAMP - INTERVAL '23 hours'")));
    }

    @Test
    void claimsOnlyPendingMessagesWithActiveConsentAndUnstoppedConversation() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        var store = new WhatsAppOutboxDispatchStore(jdbc);

        store.claimOne();

        verify(jdbc).query(org.mockito.ArgumentMatchers.<String>argThat(sql ->
            sql.contains("JOIN whatsapp_conversations c ON c.telefone = o.telefone")
            && sql.contains("c.consentimento = true")
            && sql.contains("c.etapa <> 'STOPPED'")
            && sql.contains("o.tipo_resposta = 'CONSENT_REQUEST'")
            && sql.contains("c.consentimento = false AND c.etapa = 'CONSENT'")
            && sql.contains("o.status = 'PENDENTE'")
            && sql.contains("FOR UPDATE OF o SKIP LOCKED")
            && sql.contains("INTERVAL '23 hours'")),
            any(org.springframework.jdbc.core.ResultSetExtractor.class));
    }

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
