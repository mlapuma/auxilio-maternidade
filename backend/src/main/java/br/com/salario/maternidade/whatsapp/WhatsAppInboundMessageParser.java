package br.com.salario.maternidade.whatsapp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class WhatsAppInboundMessageParser {
    private final ObjectMapper mapper;

    public WhatsAppInboundMessageParser(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public List<WhatsAppInboundMessage> parse(String payload) throws Exception {
        JsonNode root = mapper.readTree(payload);
        if (!"whatsapp_business_account".equals(root.path("object").asText())) {
            throw new IllegalArgumentException("Unexpected webhook object");
        }
        if (!root.path("entry").isArray()) {
            throw new IllegalArgumentException("Missing webhook entries");
        }
        List<WhatsAppInboundMessage> result = new ArrayList<>();
        for (JsonNode entry : root.path("entry")) {
            for (JsonNode change : entry.path("changes")) {
                for (JsonNode message : change.path("value").path("messages")) {
                    if (!"text".equals(message.path("type").asText())) continue;
                    String id = message.path("id").asText();
                    String sender = message.path("from").asText();
                    String text = message.path("text").path("body").asText();
                    if (id.isBlank() || sender.isBlank() || text.isBlank()) continue;
                    try {
                        result.add(new WhatsAppInboundMessage(id, sender, text));
                    } catch (IllegalArgumentException ignored) {
                        // Ignore malformed messages without exposing personal content.
                    }
                }
            }
        }
        return List.copyOf(result);
    }
}
