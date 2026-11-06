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

    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void verifyMetaAtStartup() {
        if (enabled) checkMetaCredentials();
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
        } catch (org.springframework.web.client.ResourceAccessException ex) {
            log.error("WhatsApp Meta credential check: result=NETWORK_ERROR, exceptionType={}, rootCause={}",
                ex.getClass().getSimpleName(), rootCauseType(ex));
            if (hasCause(ex, java.security.InvalidAlgorithmParameterException.class)) {
                logTrustStoreDiagnostics();
            }
            return false;
        } catch (RuntimeException ex) {
            log.error("WhatsApp Meta credential check: result=ERROR, exceptionType={}, rootCause={}",
                ex.getClass().getSimpleName(), rootCauseType(ex));
            return false;
        }
    }

    /** Diagnose empty PKIX trust anchors without weakening TLS or exposing secrets. */
    private static void logTrustStoreDiagnostics() {
        String configured = System.getProperty("javax.net.ssl.trustStore");
        java.nio.file.Path javaHome = java.nio.file.Path.of(System.getProperty("java.home"));
        java.nio.file.Path defaultStore = javaHome.resolve("lib").resolve("security").resolve("cacerts");
        try {
            java.nio.file.Path selected = configured != null && !configured.isBlank()
                ? java.nio.file.Path.of(configured) : defaultStore;
            log.error("WhatsApp TLS truststore: customPathConfigured={}, fileExists={}, javaVersion={}, javaVendor={}",
                configured != null && !configured.isBlank(), java.nio.file.Files.isRegularFile(selected),
                System.getProperty("java.version"), System.getProperty("java.vendor"));
            log.error("WhatsApp TLS has no usable trust anchors. Check JVM -Djavax.net.ssl.trustStore and -Djavax.net.ssl.trustStorePassword, JAVA_HOME, and cacerts; do not disable certificate validation.");
        } catch (RuntimeException ex) {
            log.error("WhatsApp TLS truststore diagnostic failed: exceptionType={}", ex.getClass().getSimpleName());
        }
    }

    private static boolean hasCause(Throwable error, Class<? extends Throwable> type) {
        java.util.Set<Throwable> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (Throwable current = error; current != null && seen.add(current); current = current.getCause()) {
            if (type.isInstance(current)) return true;
        }
        return false;
    }

    /** Log only exception class names; messages may contain URLs or sensitive details. */
    static String rootCauseType(Throwable error) {
        Throwable current = error;
        java.util.Set<Throwable> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        while (current.getCause() != null && seen.add(current) && !seen.contains(current.getCause())) {
            current = current.getCause();
        }
        return current.getClass().getSimpleName();
    }

    public void sendText(String recipient, String body) {
        validateRecipient(recipient);
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

    /** Also marks the customer's incoming message as read, as required by Meta. */
    public void sendTypingIndicator(String recipient, String inboundMessageId) {
        validateRecipient(recipient);
        if (inboundMessageId == null || inboundMessageId.isBlank() || inboundMessageId.length() > 255) {
            throw new IllegalArgumentException("Invalid inbound message id");
        }
        client.post()
            .uri("/{phoneNumberId}/messages", phoneNumberId)
            .headers(headers -> headers.setBearerAuth(token.trim()))
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of(
                "messaging_product", "whatsapp",
                "status", "read",
                "message_id", inboundMessageId,
                "typing_indicator", Map.of("type", "text")))
            .retrieve()
            .toBodilessEntity();
    }

    private void validateRecipient(String recipient) {
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
    }
}
