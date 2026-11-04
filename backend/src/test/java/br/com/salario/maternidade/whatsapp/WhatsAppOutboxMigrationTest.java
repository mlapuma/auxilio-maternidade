package br.com.salario.maternidade.whatsapp;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WhatsAppOutboxMigrationTest {
    @Test
    void migrationIndexesOnlyActiveDispatchesByRecipient() throws Exception {
        String resource = "/db/migration/V105__whatsapp_outbox_inflight_phone_index.sql";
        try (var input = getClass().getResourceAsStream(resource)) {
            assertNotNull(input, "Flyway migration V105 must be packaged");
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(sql.contains("CREATE INDEX idx_whatsapp_outbox_enviando_telefone"));
            assertTrue(sql.contains("ON whatsapp_outbox (telefone)"));
            assertTrue(sql.contains("WHERE status = 'ENVIANDO'"));
        }
    }

    @Test
    void migrationDefinesAllSixOperationalStatesAndRecoveryIndex() throws Exception {
        String resource = "/db/migration/V103__whatsapp_outbox_status_integrity.sql";
        try (var input = getClass().getResourceAsStream(resource)) {
            assertNotNull(input, "Flyway migration must be packaged");
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            for (String status : new String[] {
                    "PENDENTE", "ENVIANDO", "ENVIADO", "REVISAR", "CANCELADO", "EXPIRADO"}) {
                assertTrue(sql.contains("'" + status + "'"), "Missing status: " + status);
            }
            assertTrue(sql.contains("CHECK (status IN"));
            assertTrue(sql.contains("WHERE status = 'ENVIANDO'"));
        }
    }
}
