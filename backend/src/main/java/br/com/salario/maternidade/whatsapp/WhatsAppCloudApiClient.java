package br.com.salario.maternidade.whatsapp;

import java.util.Map;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final Logger log = LoggerFactory.getLogger(WhatsAppCloudApiClient.class);
    private final RestClient client;
    private final String token;
    private final String phoneNumberId;
    private final boolean enabled;

    /** Defense in depth: restrict real sends to one test recipient when configured. */
    @Value("${WHATSAPP_OUTBOX_TARGET_PHONE:}")
    private String targetPhone = "";

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

    /** Report configuration problems at startup without exposing secrets or sending messages. */
    @PostConstruct
    public void diagnoseConfiguration() {
        boolean tokenPresent = token != null && !token.isBlank();
        boolean phoneIdValid = phoneNumberId != null && phoneNumberId.matches("[0-9]+");
        boolean targetValid = targetPhone == null || targetPhone.isBlank()
            || targetPhone.matches("[1-9][0-9]{7,14}");
        log.info("WhatsApp Cloud API configuration: sendingEnabled={}, tokenPresent={}, phoneNumberIdValid={}, targetPhoneConfigured={}, targetPhoneValid={}",
            enabled, tokenPresent, phoneIdValid,
            targetPhone != null && !targetPhone.isBlank(), targetValid);
        if (enabled && (!tokenPresent || !phoneIdValid || !targetValid)) {
            log.error("WhatsApp Cloud API configuration invalid: check WHATSAPP_ACCESS_TOKEN, WHATSAPP_PHONE_NUMBER_ID and WHATSAPP_OUTBOX_TARGET_PHONE in the active Eclipse launch environment");
        }
        if (enabled && (targetPhone == null || targetPhone.isBlank())) {
            log.warn("WhatsApp Cloud API test recipient restriction is not configured; set WHATSAPP_OUTBOX_TARGET_PHONE for controlled tests");
        }
    }

    /** Read-only diagnostic; never sends a WhatsApp message. */
    public boolean checkMetaCredentials() {
        if (token == null || token.isBlank() || phoneNumberId == null || !phoneNumberId.matches("[0-9]+")) {
            log.error("WhatsApp Meta credential check: result=INVALID_LOCAL_CONFIGURATION");
            return false;
        }
        try {
            client.get().uri("/{phoneNumberId}?fields=id", phoneNumberId)
                .headers(headers -> headers.setBearerAuth(token.trim()))
                .retrieve().toBodilessEntity();
            log.info("WhatsApp Meta credential check: result=OK");
            return true;
        } catch (org.springframework.web.client.RestClientResponseException ex) {
            log.error("WhatsApp Meta credential check: result=HTTP_ERROR, httpStatus={}", ex.getStatusCode().value());
            return false;
        } catch (RuntimeException ex) {
            log.error("WhatsApp Meta credential check: result=ERROR, exceptionType={}", ex.getClass().getSimpleName());
            return false;
        }
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
        if (targetPhone != null && !targetPhone.isBlank() && !targetPhone.equals(recipient)) {
            throw new IllegalStateException("Recipient is not permitted in controlled dispatch mode");
        }
        if (body == null || body.isBlank() || body.length() > 4096) {
            throw new IllegalArgumentException("Invalid WhatsApp text");
        }
        client.post()
            .uri("/{phoneNumberId}/messages", phoneNumberId)
            .headers(headers -> headers.setBearerAuth(token.trim()))
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
