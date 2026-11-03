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
