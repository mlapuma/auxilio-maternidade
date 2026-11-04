package br.com.salario.maternidade.whatsapp;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Aggregate operational metrics only; no phone numbers or message bodies. */
@Repository
public class WhatsAppOutboxMetrics {
    public record Counts(long pending, long sending, long sent, long review, long cancelled, long expired) {}

    private final JdbcTemplate jdbc;

    public WhatsAppOutboxMetrics(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Counts counts() {
        return jdbc.queryForObject("""
            SELECT COUNT(*) FILTER (WHERE status = 'PENDENTE') AS pending,
                   COUNT(*) FILTER (WHERE status = 'ENVIANDO') AS sending,
                   COUNT(*) FILTER (WHERE status = 'ENVIADO') AS sent,
                   COUNT(*) FILTER (WHERE status = 'REVISAR') AS review,
                   COUNT(*) FILTER (WHERE status = 'CANCELADO') AS cancelled,
                   COUNT(*) FILTER (WHERE status = 'EXPIRADO') AS expired
            FROM whatsapp_outbox
            """, (rs, rowNum) -> new Counts(
                rs.getLong("pending"), rs.getLong("sending"),
                rs.getLong("sent"), rs.getLong("review"),
                rs.getLong("cancelled"), rs.getLong("expired")));
    }
}
