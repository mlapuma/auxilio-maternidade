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
                    String id = message.path("id").asText();
                    String sender = message.path("from").asText();
                    String text;
                    WhatsAppConversationFlow.Step expectedStep = null;
                    if ("text".equals(message.path("type").asText())) {
                        text = message.path("text").path("body").asText();
                    } else if ("interactive".equals(message.path("type").asText())) {
                        JsonNode interactive = message.path("interactive");
                        String type = interactive.path("type").asText();
                        if (!"button_reply".equals(type) && !"list_reply".equals(type)) continue;
                        var choice = WhatsAppInteractiveOptions.parseChoice(interactive.path(type).path("id").asText());
                        if (choice == null) continue;
                        text = choice.answer();
                        expectedStep = choice.expectedStep();
                    } else continue;
                    if (id.isBlank() || sender.isBlank() || text.isBlank()) continue;
                    try {
                        result.add(new WhatsAppInboundMessage(id, sender, text, expectedStep));
                    } catch (IllegalArgumentException ignored) {
                        // Ignore malformed messages without exposing personal content.
                    }
                }
            }
        }
        return List.copyOf(result);
    }
}
