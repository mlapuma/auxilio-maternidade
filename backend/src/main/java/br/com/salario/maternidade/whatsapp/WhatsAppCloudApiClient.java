package br.com.salario.maternidade.whatsapp;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Explicit WhatsApp Cloud API sender. No scheduled or automatic sending.
 * Caller must enforce opt-in and Meta's customer service window.
 */
@Component
public class WhatsAppCloudApiClient {
    private final RestClient client;
    private final String token;
    private final String phoneNumberId;
    private final boolean enabled;

    public WhatsAppCloudApiClient(RestClient.Builder builder,
            @Value("${WHATSAPP_ACCESS_TOKEN:}") String token,
            @Value("${WHATSAPP_PHONE_NUMBER_ID:}") String phoneNumberId,
            @Value("${WHATSAPP_GRAPH_API_VERSION:v23.0}") String version,
            @Value("${WHATSAPP_SENDING_ENABLED:false}") boolean enabled) {
        if (!version.matches("v[0-9]+\\.[0-9]+")) {
            throw new IllegalArgumentException("Invalid Graph API version");
        }
        this.client = builder.baseUrl("https://graph.facebook.com/" + version).build();
        this.token = token;
        this.phoneNumberId = phoneNumberId;
        this.enabled = enabled;
    }

    public void sendText(String recipient, String body) {
        if (!enabled) throw new IllegalStateException("WhatsApp sending is disabled");
        if (token.isBlank() || phoneNumberId.isBlank()) {
            throw new IllegalStateException("WhatsApp credentials are not configured");
        }
        if (!phoneNumberId.matches("[0-9]+")) {
            throw new IllegalStateException("Invalid WhatsApp phone number ID");
        }
        if (recipient == null || !recipient.matches("[1-9][0-9]{7,14}")) {
            throw new IllegalArgumentException("Invalid international recipient");
        }
        if (body == null || body.isBlank() || body.length() > 4096) {
            throw new IllegalArgumentException("Invalid WhatsApp text");
        }
        client.post()
            .uri("/{phoneNumberId}/messages", phoneNumberId)
            .headers(headers -> headers.setBearerAuth(token))
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of(
                "messaging_product", "whatsapp",
                "recipient_type", "individual",
                "to", recipient,
                "type", "text",
                "text", Map.of("preview_url", false, "body", body)))
            .retrieve()
            .toBodilessEntity();
    }
}
