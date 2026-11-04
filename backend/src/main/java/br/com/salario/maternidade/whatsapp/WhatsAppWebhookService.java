package br.com.salario.maternidade.whatsapp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WhatsAppWebhookService {
    private final ObjectMapper mapper;
    private final JdbcTemplate jdbc;
    private final WhatsAppInboundMessageParser inboundParser;
    private final WhatsAppConversationProcessor conversationProcessor;
    private final WhatsAppReplyOutbox replyOutbox;
    private final WhatsAppMessageHistoryStore historyStore;

    public WhatsAppWebhookService(ObjectMapper mapper, JdbcTemplate jdbc) {
        this.mapper = mapper;
        this.jdbc = jdbc;
        this.inboundParser = null;
        this.conversationProcessor = null;
        this.replyOutbox = null;
        this.historyStore = null;
    }

    public WhatsAppWebhookService(ObjectMapper mapper, JdbcTemplate jdbc,
            WhatsAppInboundMessageParser inboundParser,
            WhatsAppConversationProcessor conversationProcessor) {
        this(mapper, jdbc, inboundParser, conversationProcessor, null, null);
    }

    public WhatsAppWebhookService(ObjectMapper mapper, JdbcTemplate jdbc,
            WhatsAppInboundMessageParser inboundParser,
            WhatsAppConversationProcessor conversationProcessor,
            WhatsAppReplyOutbox replyOutbox) {
        this(mapper, jdbc, inboundParser, conversationProcessor, replyOutbox, null);
    }

    @Autowired
    public WhatsAppWebhookService(ObjectMapper mapper, JdbcTemplate jdbc,
            WhatsAppInboundMessageParser inboundParser,
            WhatsAppConversationProcessor conversationProcessor,
            WhatsAppReplyOutbox replyOutbox,
            WhatsAppMessageHistoryStore historyStore) {
        this.mapper = mapper;
        this.jdbc = jdbc;
        this.inboundParser = inboundParser;
        this.conversationProcessor = conversationProcessor;
        this.replyOutbox = replyOutbox;
        this.historyStore = historyStore;
    }

    @Transactional
    public void accept(String payload) throws Exception {
        JsonNode root = mapper.readTree(payload);
        if (!"whatsapp_business_account".equals(root.path("object").asText())) {
            throw new IllegalArgumentException("Unexpected webhook object");
        }
        if (!root.path("entry").isArray()) {
            throw new IllegalArgumentException("Missing webhook entries");
        }
        java.util.Set<String> insertedMessageIds = new java.util.HashSet<>();
        for (JsonNode entry : root.path("entry")) {
            for (JsonNode change : entry.path("changes")) {
                JsonNode value = change.path("value");
                for (JsonNode message : value.path("messages")) {
                    String id = message.path("id").asText();
                    if (!id.isBlank() && saveEvent(id, "MENSAGEM")) insertedMessageIds.add(id);
                }
                for (JsonNode status : value.path("statuses")) {
                    String id = status.path("id").asText();
                    String state = status.path("status").asText();
                    if (!id.isBlank() && !state.isBlank()) saveEvent(id + ":" + state, "STATUS");
                }
            }
        }
        if (inboundParser != null && conversationProcessor != null) {
            for (WhatsAppInboundMessage message : inboundParser.parse(payload)) {
                // Only newly inserted webhook events can advance conversation state.
                if (insertedMessageIds.contains(message.messageId())) {
                    var transition = conversationProcessor.process(message);
                    if (historyStore != null) {
                        historyStore.recordInbound(message);
                    }
                    if (replyOutbox != null) {
                        replyOutbox.enqueue(message.messageId(), message.sender(), transition.reply());
                    }
                }
            }
        }
    }

    private boolean saveEvent(String externalId, String type) {
        if (externalId.isBlank() || externalId.length() > 255) return false;
        return jdbc.update("INSERT INTO eventos_webhook (id, identificador_externo, tipo_evento, data_recebimento, status_processamento) " +
            "VALUES (?, ?, ?, ?, ?) ON CONFLICT (identificador_externo) DO NOTHING",
            UUID.randomUUID(), externalId, type, OffsetDateTime.now(ZoneOffset.UTC), "RECEBIDO") > 0;
    }
}
