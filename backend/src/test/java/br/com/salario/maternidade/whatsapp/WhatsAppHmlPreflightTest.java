package br.com.salario.maternidade.whatsapp;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Static safety checks: the operational preflight must remain read-only. */
class WhatsAppHmlPreflightTest {
    @Test
    void preflightChecksMigrationsAndOperationalRisksWithoutMutatingData() throws Exception {
        String sql = Files.readString(
            Path.of("scripts/whatsapp_hml_preflight.sql"), StandardCharsets.UTF_8);
        assertTrue(sql.contains("BEGIN TRANSACTION READ ONLY"));
        assertTrue(sql.contains("flyway_schema_history"));
        assertTrue(sql.contains("'105'"));
        assertTrue(sql.contains("idx_whatsapp_outbox_enviando_telefone"));
        assertTrue(sql.contains("stuck_sending"));
        assertTrue(sql.contains("linked_without_consent"));
        assertTrue(sql.contains("recipients_with_multiple_inflight"));
        assertTrue(sql.contains("COMMIT;"));
        String withoutComments = sql.replaceAll("(?m)^\\s*--[^\\r\\n]*", "");
        assertFalse(withoutComments.matches("(?is).*\\b(INSERT|UPDATE|DELETE|TRUNCATE|DROP|ALTER|CREATE)\\s+.*"));
    }
}
