package br.com.salario.maternidade.whatsapp;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WhatsAppMessageHistoryStoreTest {
    @Test
    void storesInboundOnlyWhenConversationHasConsent() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        var store = new WhatsAppMessageHistoryStore(jdbc);
        store.recordInbound(new WhatsAppInboundMessage("wamid.123", "5511999999999", "GESTANTE"));
        verify(jdbc).update(
            allOf(contains("consentimento = true"), contains("ON CONFLICT"), contains("etapa <> 'STOPPED'")),
            any(), eq("wamid.123"), eq("GESTANTE"), eq("5511999999999"));
    }
}
