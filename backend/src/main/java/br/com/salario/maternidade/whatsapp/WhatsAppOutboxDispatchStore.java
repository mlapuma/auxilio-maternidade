package br.com.salario.maternidade.whatsapp;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

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
                SELECT id FROM whatsapp_outbox
                WHERE status = 'PENDENTE'
                  AND criado_em >= CURRENT_TIMESTAMP - INTERVAL '23 hours'
                ORDER BY criado_em, id
                FOR UPDATE SKIP LOCKED
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

    public void markSent(UUID id) {
        jdbc.update("""
            UPDATE whatsapp_outbox SET status = 'ENVIADO',
                atualizado_em = CURRENT_TIMESTAMP
            WHERE id = ? AND status = 'ENVIANDO'
            """, id);
    }

    public void markForReview(UUID id) {
        jdbc.update("""
            UPDATE whatsapp_outbox SET status = 'REVISAR',
                atualizado_em = CURRENT_TIMESTAMP
            WHERE id = ? AND status = 'ENVIANDO'
            """, id);
    }
}
