package br.com.salario.maternidade.whatsapp;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final Logger log = LoggerFactory.getLogger(WhatsAppWebhookService.class);
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
        int messagesSeen = 0;
        int statusesSeen = 0;
        int inserted = 0;
        for (JsonNode entry : root.path("entry")) {
            for (JsonNode change : entry.path("changes")) {
                JsonNode value = change.path("value");
                for (JsonNode message : value.path("messages")) {
                    messagesSeen++;
                    String id = message.path("id").asText();
                    if (!id.isBlank() && saveEvent(id, "MENSAGEM")) {
                        insertedMessageIds.add(id);
                        inserted++;
                    }
                }
                for (JsonNode status : value.path("statuses")) {
                    statusesSeen++;
                    String id = status.path("id").asText();
                    String state = status.path("status").asText();
                    if (!id.isBlank() && !state.isBlank() && saveEvent(id + ":" + state, "STATUS")) inserted++;
                }
            }
        }
        log.info("WhatsApp webhook parsed: messages={}, statuses={}, newEvents={}, ignoredOrDuplicate={}",
                messagesSeen, statusesSeen, inserted, messagesSeen + statusesSeen - inserted);
        if (messagesSeen == 0 && statusesSeen == 0) {
            log.info("WhatsApp webhook contained no message or status events; no database rows expected");
        }
        if (inboundParser != null && conversationProcessor != null) {
            for (WhatsAppInboundMessage message : inboundParser.parse(payload)) {
                // Consume each newly inserted event ID once, even if Meta includes
                // the same message more than once in a single webhook payload.
                if (insertedMessageIds.remove(message.messageId())) {
                    var transition = conversationProcessor.process(message);
                    if (historyStore != null) {
                        historyStore.recordInbound(message);
                    }
                    // Do not queue acknowledgements after opt-out/refusal: the
                    // dispatcher correctly forbids sending in STOPPED state.
                    if (replyOutbox != null
                            && transition.nextStep() != WhatsAppConversationFlow.Step.STOPPED) {
                        if (transition.nextStep() == WhatsAppConversationFlow.Step.CONSENT) {
                            replyOutbox.enqueue(message.messageId(), message.sender(), transition.reply(), true);
                        } else {
                            replyOutbox.enqueue(message.messageId(), message.sender(), transition.reply());
                        }
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
