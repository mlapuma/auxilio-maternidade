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
