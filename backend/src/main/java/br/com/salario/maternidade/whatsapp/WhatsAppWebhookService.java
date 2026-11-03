package br.com.salario.maternidade.whatsapp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WhatsAppWebhookService {
    private final ObjectMapper mapper;
    private final JdbcTemplate jdbc;

    public WhatsAppWebhookService(ObjectMapper mapper, JdbcTemplate jdbc) {
        this.mapper = mapper;
        this.jdbc = jdbc;
    }

    @Transactional
    public void accept(String payload) throws Exception {
        JsonNode root = mapper.readTree(payload);
        if (!"whatsapp_business_account".equals(root.path("object").asText())) {
            throw new IllegalArgumentException("Unexpected webhook object");
        }
        for (JsonNode entry : root.path("entry")) {
            for (JsonNode change : entry.path("changes")) {
                JsonNode value = change.path("value");
                for (JsonNode message : value.path("messages")) {
                    saveEvent(message.path("id").asText(), "MENSAGEM");
                }
                for (JsonNode status : value.path("statuses")) {
                    String id = status.path("id").asText();
                    String state = status.path("status").asText();
                    if (!id.isBlank() && !state.isBlank()) saveEvent(id + ":" + state, "STATUS");
                }
            }
        }
    }

    private void saveEvent(String externalId, String type) {
        if (externalId.isBlank() || externalId.length() > 255) return;
        jdbc.update("INSERT INTO eventos_webhook (id, identificador_externo, tipo_evento, data_recebimento, status_processamento) " +
            "VALUES (?, ?, ?, ?, ?) ON CONFLICT (identificador_externo) DO NOTHING",
            UUID.randomUUID(), externalId, type, OffsetDateTime.now(ZoneOffset.UTC), "RECEBIDO");
    }
}
