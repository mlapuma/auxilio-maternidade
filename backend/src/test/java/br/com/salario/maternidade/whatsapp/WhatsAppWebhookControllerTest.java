package br.com.salario.maternidade.whatsapp;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WhatsAppWebhookControllerTest {
    private final WhatsAppSignatureValidator validator = mock(WhatsAppSignatureValidator.class);
    private final WhatsAppWebhookService service = mock(WhatsAppWebhookService.class);
    private final WhatsAppWebhookController controller = new WhatsAppWebhookController(validator, service, "token-teste");

    @Test void verificaChallengeApenasComTokenCorreto() {
        assertEquals("desafio", controller.verify("subscribe", "token-teste", "desafio").getBody());
        assertEquals(HttpStatus.FORBIDDEN, controller.verify("subscribe", "errado", "desafio").getStatusCode());
    }

    @Test void rejeitaAssinaturaInvalida() throws Exception {
        byte[] body = "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        when(validator.isValid(body, "invalida")).thenReturn(false);
        assertEquals(HttpStatus.FORBIDDEN, controller.receive(body, "invalida").getStatusCode());
        verifyNoInteractions(service);
    }

    @Test void aceitaNotificacaoValida() throws Exception {
        byte[] body = "{\"object\":\"whatsapp_business_account\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        when(validator.isValid(body, "assinatura")).thenReturn(true);
        assertEquals(HttpStatus.OK, controller.receive(body, "assinatura").getStatusCode());
        verify(service).accept(new String(body, java.nio.charset.StandardCharsets.UTF_8));
    }
}
