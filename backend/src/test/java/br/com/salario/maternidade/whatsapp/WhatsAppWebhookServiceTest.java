package br.com.salario.maternidade.whatsapp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WhatsAppWebhookServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final WhatsAppWebhookService service = new WhatsAppWebhookService(new ObjectMapper(), jdbc);

    @Test
    void registraMensagemValida() throws Exception {
        service.accept("{\"object\":\"whatsapp_business_account\",\"entry\":[{\"changes\":[{\"value\":{\"messages\":[{\"id\":\"wamid.123\"}]}}]}]}");
        verify(jdbc).update(contains("ON CONFLICT"), any(), eq("wamid.123"), eq("MENSAGEM"), any(), eq("RECEBIDO"));
    }

    @Test
    void ignoraMensagemSemIdentificador() throws Exception {
        service.accept("{\"object\":\"whatsapp_business_account\",\"entry\":[{\"changes\":[{\"value\":{\"messages\":[{}]}}]}]}");
        verifyNoInteractions(jdbc);
    }

    @Test
    void rejeitaPayloadSemEntradas() {
        assertThrows(IllegalArgumentException.class,
            () -> service.accept("{\"object\":\"whatsapp_business_account\"}"));
        verifyNoInteractions(jdbc);
    }

    @Test
    void rejeitaObjetoDesconhecido() {
        assertThrows(IllegalArgumentException.class,
            () -> service.accept("{\"object\":\"other\",\"entry\":[]}"));
        verifyNoInteractions(jdbc);
    }

    @Test
    void registraAtualizacaoDeStatus() throws Exception {
        service.accept("{\"object\":\"whatsapp_business_account\",\"entry\":[{\"changes\":[{\"value\":{\"statuses\":[{\"id\":\"wamid.123\",\"status\":\"delivered\"}]}}]}]}");
        verify(jdbc).update(contains("ON CONFLICT"), any(), eq("wamid.123:delivered"), eq("STATUS"), any(), eq("RECEBIDO"));
    }
}
