package br.com.salario.maternidade.whatsapp;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Claims a single reply atomically. Failed or ambiguous sends are held for
 * manual review rather than automatically retried (to avoid duplicates).
 */
@Repository
public class WhatsAppOutboxDispatchStore {
    public record PendingReply(UUID id, String phone, String body, String inboundMessageId) {
        public PendingReply(UUID id, String phone, String body) {
            this(id, phone, body, null);
        }
    }

    private final JdbcTemplate jdbc;

    /** Optional recipient restriction for controlled staging dispatch. */
    @Value("${WHATSAPP_OUTBOX_TARGET_PHONE:}")
    private String targetPhone = "";

    public WhatsAppOutboxDispatchStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Expires replies older than the conservative dispatch window.
     * They must not remain indefinitely pending or be sent after a restart.
     */
    public int expireStalePendingReplies() {
        return jdbc.update("""
            UPDATE whatsapp_outbox
               SET status = 'EXPIRADO', atualizado_em = CURRENT_TIMESTAMP
             WHERE status = 'PENDENTE'
               AND criado_em < CURRENT_TIMESTAMP - INTERVAL '23 hours'
            """);
    }

    /**
     * A process may crash after claiming a message or after Meta accepted it.
     * Never automatically retry these ambiguous deliveries: require review.
     */
    public int flagStuckSendingForReview() {
        return jdbc.update("""
            UPDATE whatsapp_outbox
               SET status = 'REVISAR', atualizado_em = CURRENT_TIMESTAMP
             WHERE status = 'ENVIANDO'
               AND atualizado_em < CURRENT_TIMESTAMP - INTERVAL '10 minutes'
            """);
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
                  AND (? = '' OR o.telefone = ?)
                  -- Do not dispatch two replies to the same recipient concurrently.
                  -- A previously claimed reply may still be in flight at Meta.
                  AND NOT EXISTS (
                      SELECT 1 FROM whatsapp_outbox active
                       WHERE active.telefone = o.telefone
                         AND active.status = 'ENVIANDO'
                  )
                  AND ((o.tipo_resposta = 'STANDARD'
                        AND c.consentimento = true AND c.etapa <> 'STOPPED')
                    OR (o.tipo_resposta = 'CONSENT_REQUEST'
                        AND c.consentimento = false AND c.etapa = 'CONSENT'))
                ORDER BY o.criado_em, o.id
                FOR UPDATE OF o, c SKIP LOCKED
                LIMIT 1
            )
            RETURNING id, telefone, corpo, mensagem_entrada_id
            """, rs -> rs.next()
                ? Optional.of(new PendingReply(
                    (UUID) rs.getObject("id"),
                    rs.getString("telefone"),
                    rs.getString("corpo"), rs.getString("mensagem_entrada_id")))
                : Optional.empty(), targetPhone == null ? "" : targetPhone,
                targetPhone == null ? "" : targetPhone);
    }

    /**
     * Recheck the conversation just before the external API call.
     * A user may revoke consent after the message was claimed.
     * This narrows the race window; it cannot make the network send atomic.
     */
    public boolean stillEligible(UUID id) {
        Boolean eligible = jdbc.queryForObject("""
            SELECT EXISTS (
                SELECT 1 FROM whatsapp_outbox o
                JOIN whatsapp_conversations c ON c.telefone = o.telefone
                WHERE o.id = ? AND o.status = 'ENVIANDO'
                  AND o.criado_em >= CURRENT_TIMESTAMP - INTERVAL '23 hours'
                  AND (? = '' OR o.telefone = ?)
                  AND ((o.tipo_resposta = 'STANDARD'
                        AND c.consentimento = true AND c.etapa <> 'STOPPED')
                    OR (o.tipo_resposta = 'CONSENT_REQUEST'
                        AND c.consentimento = false AND c.etapa = 'CONSENT'))
            )
            """, Boolean.class, id, targetPhone == null ? "" : targetPhone,
                targetPhone == null ? "" : targetPhone);
        return Boolean.TRUE.equals(eligible);
    }

    public void cancelClaimedReply(UUID id) {
        jdbc.update("""
            UPDATE whatsapp_outbox
               SET status = 'CANCELADO', atualizado_em = CURRENT_TIMESTAMP
             WHERE id = ? AND status = 'ENVIANDO'
            """, id);
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
            ON CONFLICT (mensagem_externa_id) WHERE mensagem_externa_id IS NOT NULL DO NOTHING
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
