package br.com.salario.maternidade.whatsapp;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Set;
import java.util.HashSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Opt-in integration checks against a real PostgreSQL HML database.
 * Never writes data or prints personal information.
 *
 * WHATSAPP_HML_JDBC_URL=jdbc:postgresql://host:5432/database
 * WHATSAPP_HML_DB_USER=...
 * WHATSAPP_HML_DB_PASSWORD=...
 *
 * Run: mvn -Dtest=WhatsAppPostgresHmlIntegrationTest test
 */
class WhatsAppPostgresHmlIntegrationTest {
    @Test
    @EnabledIfEnvironmentVariable(named = "WHATSAPP_HML_JDBC_URL", matches = "jdbc:postgresql:.*")
    void requiredMigrationsAndIndexesArePresentOnRealPostgres() throws Exception {
        String url = System.getenv("WHATSAPP_HML_JDBC_URL");
        String user = System.getenv("WHATSAPP_HML_DB_USER");
        String password = System.getenv("WHATSAPP_HML_DB_PASSWORD");
        assertNotNull(user, "Set WHATSAPP_HML_DB_USER");
        assertNotNull(password, "Set WHATSAPP_HML_DB_PASSWORD");

        try (Connection connection = DriverManager.getConnection(url, user, password)) {
            connection.setReadOnly(true);
            connection.setAutoCommit(false);
            try {
                Set<String> versions = new HashSet<>();
                try (Statement statement = connection.createStatement();
                     ResultSet rs = statement.executeQuery("""
                         SELECT version FROM flyway_schema_history
                          WHERE success = true
                            AND version IN ('100','101','102','103','104','105')
                         """)) {
                    while (rs.next()) versions.add(rs.getString(1));
                }
                assertEquals(Set.of("100", "101", "102", "103", "104", "105"), versions,
                    "Missing successful WhatsApp Flyway migrations");

                try (Statement statement = connection.createStatement();
                     ResultSet rs = statement.executeQuery("""
                         SELECT to_regclass('whatsapp_conversations') IS NOT NULL,
                                to_regclass('whatsapp_outbox') IS NOT NULL,
                                to_regclass('whatsapp_message_history') IS NOT NULL,
                                to_regclass('idx_whatsapp_outbox_enviando_telefone') IS NOT NULL
                         """)) {
                    assertTrue(rs.next());
                    for (int i = 1; i <= 4; i++) {
                        assertTrue(rs.getBoolean(i), "Missing required table/index at position " + i);
                    }
                }

                // Verify actual PostgreSQL constraints, not just migration filenames.
                try (Statement statement = connection.createStatement();
                     ResultSet rs = statement.executeQuery("""
                         SELECT conname
                           FROM pg_constraint
                          WHERE conrelid = 'whatsapp_outbox'::regclass
                            AND conname IN ('chk_whatsapp_outbox_status',
                                            'chk_whatsapp_outbox_tipo_resposta')
                         """)) {
                    Set<String> constraints = new HashSet<>();
                    while (rs.next()) constraints.add(rs.getString(1));
                    assertEquals(Set.of("chk_whatsapp_outbox_status",
                        "chk_whatsapp_outbox_tipo_resposta"), constraints,
                        "Missing WhatsApp outbox integrity constraints");
                }

                // Confirm that the recipient index is partial, so historical
                // messages do not inflate it. This is a metadata-only query.
                try (Statement statement = connection.createStatement();
                     ResultSet rs = statement.executeQuery("""
                         SELECT indexdef FROM pg_indexes
                          WHERE schemaname = current_schema()
                            AND tablename = 'whatsapp_outbox'
                            AND indexname = 'idx_whatsapp_outbox_enviando_telefone'
                         """)) {
                    assertTrue(rs.next(), "Missing in-flight recipient index");
                    String definition = rs.getString(1);
                    assertTrue(definition.contains("(telefone)"));
                    assertTrue(definition.contains("ENVIANDO"));
                }

                // EXPLAIN (without ANALYZE) plans the actual UPDATE ... RETURNING
                // used in production. It does not execute the update or claim a row.
                try (Statement statement = connection.createStatement();
                     ResultSet rs = statement.executeQuery("""
                         EXPLAIN
                         UPDATE whatsapp_outbox SET status = 'ENVIANDO',
                             tentativas = tentativas + 1,
                             atualizado_em = CURRENT_TIMESTAMP
                          WHERE id = (
                              SELECT o.id FROM whatsapp_outbox o
                              JOIN whatsapp_conversations c ON c.telefone = o.telefone
                              WHERE o.status = 'PENDENTE'
                                AND o.criado_em >= CURRENT_TIMESTAMP - INTERVAL '23 hours'
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
                         RETURNING id, telefone, corpo
                         """)) {
                    assertTrue(rs.next(), "PostgreSQL could not plan the actual outbox claim");
                }

                // Plan the remaining production SQL statements without executing
                // them. EXPLAIN without ANALYZE is safe for read-only HML.
                String[] dispatchStatements = {
                    """
                    UPDATE whatsapp_outbox
                       SET status = 'EXPIRADO', atualizado_em = CURRENT_TIMESTAMP
                     WHERE status = 'PENDENTE'
                       AND criado_em < CURRENT_TIMESTAMP - INTERVAL '23 hours'
                    """,
                    """
                    UPDATE whatsapp_outbox
                       SET status = 'REVISAR', atualizado_em = CURRENT_TIMESTAMP
                     WHERE status = 'ENVIANDO'
                       AND atualizado_em < CURRENT_TIMESTAMP - INTERVAL '10 minutes'
                    """,
                    """
                    UPDATE whatsapp_outbox
                       SET status = 'CANCELADO', atualizado_em = CURRENT_TIMESTAMP
                     WHERE id = '00000000-0000-0000-0000-000000000000'::uuid
                       AND status = 'ENVIANDO'
                    """,
                    """
                    INSERT INTO whatsapp_message_history
                        (id, conversa_id, mensagem_externa_id, direcao, conteudo)
                    SELECT '00000000-0000-0000-0000-000000000000'::uuid,
                           c.id, 'outbox:' || o.id::text, 'SAIDA', o.corpo
                      FROM whatsapp_outbox o
                      JOIN whatsapp_conversations c ON c.telefone = o.telefone
                     WHERE o.id = '00000000-0000-0000-0000-000000000000'::uuid
                       AND o.status = 'ENVIADO'
                       AND c.consentimento = true AND c.etapa <> 'STOPPED'
                    ON CONFLICT (mensagem_externa_id) WHERE mensagem_externa_id IS NOT NULL DO NOTHING
                    """
                };
                for (String sql : dispatchStatements) {
                    try (Statement statement = connection.createStatement();
                         ResultSet rs = statement.executeQuery("EXPLAIN " + sql)) {
                        assertTrue(rs.next(), "PostgreSQL could not plan a dispatch statement");
                    }
                }

                // No two messages should be in flight for the same recipient.
                // Return only an aggregate count; never expose phone numbers.
                try (Statement statement = connection.createStatement();
                     ResultSet rs = statement.executeQuery("""
                         SELECT COUNT(*) FROM (
                             SELECT telefone
                               FROM whatsapp_outbox
                              WHERE status = 'ENVIANDO'
                              GROUP BY telefone
                             HAVING COUNT(*) > 1
                         ) duplicated
                         """)) {
                    assertTrue(rs.next());
                    assertEquals(0L, rs.getLong(1),
                        "Multiple active sends exist for the same recipient");
                }

                // Detect operational anomalies before enabling outbound sends.
                try (Statement statement = connection.createStatement();
                     ResultSet rs = statement.executeQuery("""
                         SELECT COUNT(*) FROM whatsapp_outbox
                          WHERE (status = 'ENVIANDO'
                                 AND atualizado_em < CURRENT_TIMESTAMP - INTERVAL '10 minutes')
                             OR (status = 'PENDENTE'
                                 AND criado_em < CURRENT_TIMESTAMP - INTERVAL '23 hours')
                         """)) {
                    assertTrue(rs.next());
                    assertEquals(0L, rs.getLong(1),
                        "Stuck or expired outbox messages require operational cleanup");
                }

                try (Statement statement = connection.createStatement();
                     ResultSet rs = statement.executeQuery("""
                         SELECT COUNT(*) FROM whatsapp_conversations
                          WHERE (etapa = 'STOPPED' AND consentimento = true)
                             OR (consentimento = false AND lead_id IS NOT NULL)
                             OR (consentimento = false AND respostas_triagem IS NOT NULL)
                         """)) {
                    assertTrue(rs.next());
                    assertEquals(0L, rs.getLong(1),
                        "HML contains conversations inconsistent with revoked consent");
                }
            } finally {
                connection.rollback();
            }
        }
    }
}
