package br.com.salario.maternidade.whatsapp;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Claims a single reply atomically. Failed or ambiguous sends are held for
 * manual review rather than automatically retried (to avoid duplicates).
 */
@Repository
public class WhatsAppOutboxDispatchStore {
    public record PendingReply(UUID id, String phone, String body) {}

    private final JdbcTemplate jdbc;

    public WhatsAppOutboxDispatchStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<PendingReply> claimOne() {
        return jdbc.query("""
            UPDATE whatsapp_outbox SET status = 'ENVIANDO',
                tentativas = tentativas + 1, atualizado_em = CURRENT_TIMESTAMP
            WHERE id = (
                SELECT o.id FROM whatsapp_outbox o
                JOIN whatsapp_conversations c ON c.telefone = o.telefone
                WHERE o.status = 'PENDENTE'
                  AND o.criado_em >= CURRENT_TIMESTAMP - INTERVAL '23 hours'
                  AND c.consentimento = true
                  AND c.etapa <> 'STOPPED'
                ORDER BY o.criado_em, o.id
                FOR UPDATE OF o SKIP LOCKED
                LIMIT 1
            )
            RETURNING id, telefone, corpo
            """, rs -> rs.next()
                ? Optional.of(new PendingReply(
                    (UUID) rs.getObject("id"),
                    rs.getString("telefone"),
                    rs.getString("corpo")))
                : Optional.empty());
    }

    @Transactional
    public void markSent(UUID id) {
        jdbc.update("""
            UPDATE whatsapp_outbox SET status = 'ENVIADO',
                atualizado_em = CURRENT_TIMESTAMP
            WHERE id = ? AND status = 'ENVIANDO'
            """, id);
        // Persist a successful API submission only once, using the outbox UUID
        // as a stable external identifier. Never store the reply without consent.
        jdbc.update("""
            INSERT INTO whatsapp_message_history
                (id, conversa_id, mensagem_externa_id, direcao, conteudo)
            SELECT ?, c.id, 'outbox:' || o.id::text, 'SAIDA', o.corpo
              FROM whatsapp_outbox o
              JOIN whatsapp_conversations c ON c.telefone = o.telefone
             WHERE o.id = ? AND o.status = 'ENVIADO'
               AND c.consentimento = true AND c.etapa <> 'STOPPED'
            ON CONFLICT (mensagem_externa_id) DO NOTHING
            """, UUID.randomUUID(), id);
    }

    public void markForReview(UUID id) {
        jdbc.update("""
            UPDATE whatsapp_outbox SET status = 'REVISAR',
                atualizado_em = CURRENT_TIMESTAMP
            WHERE id = ? AND status = 'ENVIANDO'
            """, id);
    }
}
