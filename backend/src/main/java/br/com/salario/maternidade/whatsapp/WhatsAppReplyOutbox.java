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
        enqueue(inboundMessageId, phone, reply, consentRequest, null);
    }

    public void enqueue(String inboundMessageId, String phone, String reply,
            boolean consentRequest, java.util.Map<String, Object> interactive) {
        enqueueTyped(inboundMessageId, phone, reply, consentRequest ? "CONSENT_REQUEST" : "STANDARD", interactive);
    }

    public void enqueueClosureAck(String inboundMessageId, String phone, String reply) {
        if (reply == null || reply.length() > 512) throw new IllegalArgumentException("Invalid closure acknowledgement");
        enqueueTyped(inboundMessageId, phone, reply, "CLOSURE_ACK", null);
    }

    private void enqueueTyped(String inboundMessageId, String phone, String reply, String type,
            java.util.Map<String, Object> interactive) {
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
                (id, mensagem_entrada_id, telefone, corpo, status, tipo_resposta, etapa_resposta, interactive_payload)
            VALUES (?, ?, ?, ?, 'PENDENTE', ?,
                (SELECT etapa FROM whatsapp_conversations WHERE telefone = ?), ?)
            ON CONFLICT (mensagem_entrada_id) DO NOTHING
            """, UUID.randomUUID(), inboundMessageId, phone, reply,
            type, phone, encode(interactive));
    }

    private static String encode(java.util.Map<String, Object> interactive) {
        if (interactive == null) return null;
        try { return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(interactive); }
        catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalArgumentException("Invalid interactive reply"); }
    }
}
