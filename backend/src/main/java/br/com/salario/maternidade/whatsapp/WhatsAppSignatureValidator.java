package br.com.salario.maternidade.whatsapp;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class WhatsAppSignatureValidator {
    private final String appSecret;
    public WhatsAppSignatureValidator(@Value("${WHATSAPP_APP_SECRET:}") String appSecret) {
        this.appSecret = appSecret;
    }
    public boolean isValid(byte[] body, String signature) {
        if (appSecret.isBlank() || signature == null || !signature.matches("sha256=[0-9a-fA-F]{64}")) return false;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = mac.doFinal(body);
            byte[] provided = java.util.HexFormat.of().parseHex(signature.substring(7));
            return MessageDigest.isEqual(expected, provided);
        } catch (Exception e) {
            return false;
        }
    }
}
