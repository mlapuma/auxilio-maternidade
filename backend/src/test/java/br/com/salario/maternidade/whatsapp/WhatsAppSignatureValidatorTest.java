package br.com.salario.maternidade.whatsapp;

import org.junit.jupiter.api.Test;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import static org.junit.jupiter.api.Assertions.*;

class WhatsAppSignatureValidatorTest {
    @Test void validaAssinaturaCorretaERejeitaInvalida() throws Exception {
        byte[] body = "{\"object\":\"whatsapp_business_account\"}".getBytes(StandardCharsets.UTF_8);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("segredo".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = "sha256=" + HexFormat.of().formatHex(mac.doFinal(body));
        WhatsAppSignatureValidator validator = new WhatsAppSignatureValidator("segredo");
        assertTrue(validator.isValid(body, signature));
        assertFalse(validator.isValid(body, "sha256=" + "0".repeat(64)));
        assertFalse(new WhatsAppSignatureValidator("").isValid(body, signature));
        assertFalse(validator.isValid(body, null));
    }
}
