package br.com.salario.maternidade.whatsapp;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class WhatsAppCloudApiClientTest {
    @Test
    void sendsBirthQuestionAsTwoReplyButtons() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://graph.facebook.com/v23.0/123456/messages"))
            .andExpect(jsonPath("$.type").value("interactive"))
            .andExpect(jsonPath("$.interactive.type").value("button"))
            .andExpect(jsonPath("$.interactive.action.buttons.length()").value(2))
            .andExpect(jsonPath("$.interactive.action.buttons[0].reply.id").value("birth_born"))
            .andExpect(jsonPath("$.interactive.action.buttons[0].reply.title").value("Já nasceu"))
            .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        new WhatsAppCloudApiClient(builder, "test-token", "123456", "v23.0", true)
            .sendInteractiveReply("5511999999999", "O bebê já nasceu?", "BIRTH");
        server.verify();
    }

    @Test
    void sendsFollowUpMenuAsList() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://graph.facebook.com/v23.0/123456/messages"))
            .andExpect(jsonPath("$.interactive.type").value("list"))
            .andExpect(jsonPath("$.interactive.action.sections[0].rows.length()").value(5))
            .andExpect(jsonPath("$.interactive.action.sections[0].rows[4].id").value("menu_status"))
            .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        new WhatsAppCloudApiClient(builder, "test-token", "123456", "v23.0", true)
            .sendInteractiveReply("5511999999999", "Como posso ajudar?", "HANDOFF");
        server.verify();
    }
    @Test
    void sendsOfficialTypingIndicatorForInboundMessage() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://graph.facebook.com/v23.0/123456/messages"))
            .andExpect(method(org.springframework.http.HttpMethod.POST))
            .andExpect(header("Authorization", "Bearer test-token"))
            .andExpect(jsonPath("$.messaging_product").value("whatsapp"))
            .andExpect(jsonPath("$.status").value("read"))
            .andExpect(jsonPath("$.message_id").value("wamid.inbound"))
            .andExpect(jsonPath("$.typing_indicator.type").value("text"))
            .andExpect(jsonPath("$.text").doesNotExist())
            .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        var client = new WhatsAppCloudApiClient(builder, "test-token", "123456", "v23.0", true);
        client.sendTypingIndicator("5511999999999", "wamid.inbound");
        server.verify();
    }

    @Test
    void typingIndicatorRespectsDisabledSendingAndRecipientRestriction() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        var disabled = new WhatsAppCloudApiClient(builder, "test-token", "123456", "v23.0", false);
        assertThrows(IllegalStateException.class, () -> disabled.sendTypingIndicator("5511999999999", "wamid.1"));
        var restricted = new WhatsAppCloudApiClient(builder, "test-token", "123456", "v23.0", true);
        org.springframework.test.util.ReflectionTestUtils.setField(restricted, "targetPhone", "5511888888888");
        assertThrows(IllegalStateException.class, () -> restricted.sendTypingIndicator("5511999999999", "wamid.1"));
        server.verify();
    }

    @Test
    void typingIndicatorRejectsMissingInboundMessage() {
        var client = new WhatsAppCloudApiClient(RestClient.builder(), "test-token", "123456", "v23.0", true);
        assertThrows(IllegalArgumentException.class, () -> client.sendTypingIndicator("5511999999999", ""));
    }
    @Test
    void reportsRootCauseTypeWithoutLeakingExceptionMessage() {
        var root = new javax.net.ssl.SSLHandshakeException("sensitive endpoint details");
        var wrapped = new org.springframework.web.client.ResourceAccessException("request failed", root);
        assertEquals("SSLHandshakeException", WhatsAppCloudApiClient.rootCauseType(wrapped));
    }

    @Test
    void readOnlyCredentialCheckUsesMetaGet() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://graph.facebook.com/v23.0/123456?fields=id"))
            .andExpect(method(org.springframework.http.HttpMethod.GET))
            .andExpect(header("Authorization", "Bearer test-token"))
            .andRespond(withSuccess("{\"id\":\"123456\"}", MediaType.APPLICATION_JSON));
        var client = new WhatsAppCloudApiClient(builder, "test-token", "123456", "v23.0", false);
        assertTrue(client.checkMetaCredentials());
        server.verify();
    }

    @Test
    void readOnlyCredentialCheckReportsUnauthorized() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://graph.facebook.com/v23.0/123456?fields=id"))
            .andRespond(withUnauthorizedRequest());
        var client = new WhatsAppCloudApiClient(builder, "bad-token", "123456", "v23.0", false);
        assertFalse(client.checkMetaCredentials());
        server.verify();
    }

    @Test
    void disabledByDefaultPreventsRequests() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        var client = new WhatsAppCloudApiClient(builder, "test-token", "123456", "v23.0", false);
        assertThrows(IllegalStateException.class,
            () -> client.sendText("5511999999999", "Olá"));
        server.verify();
    }

    @Test
    void sendsTextToOfficialEndpointWhenExplicitlyEnabled() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://graph.facebook.com/v23.0/123456/messages"))
            .andExpect(method(org.springframework.http.HttpMethod.POST))
            .andExpect(header("Authorization", "Bearer test-token"))
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.to").value("5511999999999"))
            .andExpect(jsonPath("$.text.body").value("Olá"))
            .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        var client = new WhatsAppCloudApiClient(builder, "test-token", "123456", "v23.0", true);
        client.sendText("5511999999999", "Olá");
        server.verify();
    }

    @Test
    void rejectsInvalidRecipientsAndBlankText() {
        var client = new WhatsAppCloudApiClient(RestClient.builder(), "test-token", "123456", "v23.0", true);
        assertThrows(IllegalArgumentException.class, () -> client.sendText("11-9999", "Olá"));
        assertThrows(IllegalArgumentException.class, () -> client.sendText("5511999999999", ""));
    }

    @Test
    void rejectsMissingCredentialsAndInvalidApiVersion() {
        assertThrows(IllegalArgumentException.class,
            () -> new WhatsAppCloudApiClient(RestClient.builder(), "token", "123", "invalid", true));
        var client = new WhatsAppCloudApiClient(RestClient.builder(), "", "", "v23.0", true);
        assertThrows(IllegalStateException.class,
            () -> client.sendText("5511999999999", "Olá"));
    }
}
