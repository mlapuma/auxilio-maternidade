package br.com.salario.maternidade.whatsapp;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class WhatsAppReplyOutbox {
    private final JdbcTemplate jdbc;

    public WhatsAppReplyOutbox(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void enqueue(String inboundMessageId, String phone, String reply) {
        enqueue(inboundMessageId, phone, reply, false);
    }

    public void enqueue(String inboundMessageId, String phone, String reply,
            boolean consentRequest) {
        if (inboundMessageId == null || inboundMessageId.isBlank() || inboundMessageId.length() > 255) {
            throw new IllegalArgumentException("Invalid inbound message id");
        }
        if (phone == null || !phone.matches("[1-9][0-9]{7,14}")) {
            throw new IllegalArgumentException("Invalid recipient");
        }
        if (reply == null || reply.isBlank() || reply.length() > 4096) {
            throw new IllegalArgumentException("Invalid reply");
        }
        jdbc.update("""
            INSERT INTO whatsapp_outbox
                (id, mensagem_entrada_id, telefone, corpo, status, tipo_resposta, etapa_resposta)
            VALUES (?, ?, ?, ?, 'PENDENTE', ?,
                (SELECT etapa FROM whatsapp_conversations WHERE telefone = ?))
            ON CONFLICT (mensagem_entrada_id) DO NOTHING
            """, UUID.randomUUID(), inboundMessageId, phone, reply,
            consentRequest ? "CONSENT_REQUEST" : "STANDARD", phone);
    }
}
