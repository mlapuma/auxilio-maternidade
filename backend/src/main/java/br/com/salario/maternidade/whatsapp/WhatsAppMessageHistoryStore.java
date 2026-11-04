package br.com.salario.maternidade.whatsapp;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Records inbound text only for conversations with active consent. */
@Repository
public class WhatsAppMessageHistoryStore {
    private final JdbcTemplate jdbc;

    public WhatsAppMessageHistoryStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void recordInbound(WhatsAppInboundMessage message) {
        jdbc.update("""
            INSERT INTO whatsapp_message_history
                (id, conversa_id, mensagem_externa_id, direcao, conteudo)
            SELECT ?, c.id, ?, 'ENTRADA', ?
              FROM whatsapp_conversations c
             WHERE c.telefone = ? AND c.consentimento = true
               AND c.etapa <> 'STOPPED'
            ON CONFLICT (mensagem_externa_id) DO NOTHING
            """, UUID.randomUUID(), message.messageId(), message.text(), message.sender());
    }
}
