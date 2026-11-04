package br.com.salario.maternidade.whatsapp;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Read-only view of WhatsApp history explicitly linked to a lead. */
@Repository
public class WhatsAppAdminHistoryStore {
    public record Message(UUID id, String direction, String content, OffsetDateTime createdAt) {}
    public record Conversation(UUID leadId, String step, boolean consent,
                               String screeningAnswers, List<Message> messages) {}

    private final JdbcTemplate jdbc;

    public WhatsAppAdminHistoryStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public java.util.Optional<Conversation> findByLeadId(UUID leadId, int limit) {
        var conversations = jdbc.query("""
            SELECT id, etapa, consentimento, respostas_triagem::text AS respostas
              FROM whatsapp_conversations
             WHERE lead_id = ?
             ORDER BY atualizado_em DESC, id DESC
             LIMIT 1
            """, (rs, rowNum) -> new Object[] {
                (UUID) rs.getObject("id"), rs.getString("etapa"),
                rs.getBoolean("consentimento"), rs.getString("respostas")
            }, leadId);
        if (conversations.isEmpty()) return java.util.Optional.empty();

        var row = conversations.get(0);
        List<Message> messages = jdbc.query("""
            SELECT h.id, h.direcao, h.conteudo, h.criado_em
              FROM whatsapp_message_history h
             WHERE h.conversa_id = ?
             ORDER BY h.criado_em DESC, h.id DESC
             LIMIT ?
            """, (rs, rowNum) -> new Message(
                (UUID) rs.getObject("id"), rs.getString("direcao"),
                rs.getString("conteudo"), rs.getObject("criado_em", OffsetDateTime.class)),
            row[0], limit);
        return java.util.Optional.of(new Conversation(leadId, (String) row[1],
            (Boolean) row[2], (String) row[3], messages));
    }
}
