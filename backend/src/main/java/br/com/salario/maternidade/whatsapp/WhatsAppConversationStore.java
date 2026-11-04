package br.com.salario.maternidade.whatsapp;

import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class WhatsAppConversationStore {
    public record Conversation(String phone, WhatsAppConversationFlow.Step step, boolean consent) {}

    private final JdbcTemplate jdbc;

    public WhatsAppConversationStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Conversation> findByPhone(String phone) {
        validatePhone(phone);
        return jdbc.query(
            "SELECT telefone, etapa, consentimento FROM whatsapp_conversations WHERE telefone = ?",
            rs -> rs.next()
                ? Optional.of(new Conversation(rs.getString("telefone"),
                    WhatsAppConversationFlow.Step.valueOf(rs.getString("etapa")),
                    rs.getBoolean("consentimento")))
                : Optional.empty(),
            phone);
    }

    public void save(Conversation conversation) {
        validatePhone(conversation.phone());
        if (conversation.step() == null) throw new IllegalArgumentException("Missing step");
        jdbc.update("""
            INSERT INTO whatsapp_conversations
                (id, telefone, etapa, consentimento, criado_em, atualizado_em)
            VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            ON CONFLICT (telefone) DO UPDATE SET
                etapa = EXCLUDED.etapa,
                consentimento = EXCLUDED.consentimento,
                atualizado_em = CURRENT_TIMESTAMP
            """, UUID.randomUUID(), conversation.phone(),
            conversation.step().name(), conversation.consent());
    }

    /** Stores only validated screening answers after consent. */
    public void saveScreeningAnswer(String phone, String key, String value) {
        validatePhone(phone);
        if (!"situacao_bebe".equals(key) && !"situacao_trabalho".equals(key)) {
            throw new IllegalArgumentException("Unsupported screening answer key");
        }
        if (value == null || !value.matches("[A-Z_]{2,20}")) {
            throw new IllegalArgumentException("Invalid screening answer");
        }
        jdbc.update("""
            UPDATE whatsapp_conversations
               SET respostas_triagem = jsonb_set(
                   COALESCE(respostas_triagem, '{}'::jsonb),
                   ARRAY[?]::text[], to_jsonb(?::text), true),
                   atualizado_em = CURRENT_TIMESTAMP
             WHERE telefone = ? AND consentimento = true
            """, key, value, phone);
    }

    public void clearScreeningAnswers(String phone) {
        validatePhone(phone);
        jdbc.update("""
            UPDATE whatsapp_conversations
               SET respostas_triagem = NULL, atualizado_em = CURRENT_TIMESTAMP
             WHERE telefone = ?
            """, phone);
    }

    /**
     * Associates a consenting conversation with an existing lead only when
     * exactly one lead has the same normalized international phone.
     * Ambiguous matches are intentionally left for manual review.
     */
    public int linkUniqueExistingLead(String phone) {
        validatePhone(phone);
        return jdbc.update("""
            UPDATE whatsapp_conversations c
               SET lead_id = (
                   SELECT l.id FROM leads l
                    WHERE regexp_replace(l.telefone, '[^0-9]', '', 'g') = ?
               ),
                   atualizado_em = CURRENT_TIMESTAMP
             WHERE c.telefone = ?
               AND c.consentimento = true
               AND c.lead_id IS NULL
               AND (SELECT count(*) FROM leads l
                    WHERE regexp_replace(l.telefone, '[^0-9]', '', 'g') = ?) = 1
            """, phone, phone, phone);
    }

    public void deleteByPhone(String phone) {
        validatePhone(phone);
        jdbc.update("DELETE FROM whatsapp_conversations WHERE telefone = ?", phone);
    }

    private static void validatePhone(String phone) {
        if (phone == null || !phone.matches("[1-9][0-9]{7,14}")) {
            throw new IllegalArgumentException("Invalid international phone");
        }
    }
}
