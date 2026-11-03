package br.com.salario.maternidade.whatsapp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class WhatsAppInboundMessageParserTest {
    private final WhatsAppInboundMessageParser parser =
        new WhatsAppInboundMessageParser(new ObjectMapper());

    @Test
    void parsesTextMessage() throws Exception {
        String payload = """
            {"object":"whatsapp_business_account","entry":[{"changes":[{"value":{
              "messages":[{"id":"wamid.123","from":"5511999999999","type":"text",
              "text":{"body":"Olá"}}]}}]}]}
            """;
        List<WhatsAppInboundMessage> messages = parser.parse(payload);
        assertEquals(1, messages.size());
        assertEquals("wamid.123", messages.get(0).messageId());
        assertEquals("5511999999999", messages.get(0).sender());
        assertEquals("Olá", messages.get(0).text());
    }

    @Test
    void ignoresStatusesAndNonTextMessages() throws Exception {
        String payload = """
            {"object":"whatsapp_business_account","entry":[{"changes":[{"value":{
              "statuses":[{"id":"wamid.123","status":"delivered"}],
              "messages":[{"id":"wamid.456","from":"5511999999999","type":"image"}]}}]}]}
            """;
        assertTrue(parser.parse(payload).isEmpty());
    }

    @Test
    void ignoresMalformedSender() throws Exception {
        String payload = """
            {"object":"whatsapp_business_account","entry":[{"changes":[{"value":{
              "messages":[{"id":"wamid.123","from":"invalid","type":"text",
              "text":{"body":"Oi"}}]}}]}]}
            """;
        assertTrue(parser.parse(payload).isEmpty());
    }

    @Test
    void rejectsUnexpectedObject() {
        assertThrows(IllegalArgumentException.class,
            () -> parser.parse("{\"object\":\"invalid\",\"entry\":[]}"));
    }
}
