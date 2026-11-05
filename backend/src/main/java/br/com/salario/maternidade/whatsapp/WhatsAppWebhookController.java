package br.com.salario.maternidade.whatsapp;

import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/whatsapp/webhook")
public class WhatsAppWebhookController {
    private static final Logger log = LoggerFactory.getLogger(WhatsAppWebhookController.class);
    private final WhatsAppSignatureValidator validator;
    private final WhatsAppWebhookService service;
    private final String verifyToken;

    public WhatsAppWebhookController(WhatsAppSignatureValidator validator,
            WhatsAppWebhookService service,
            @Value("${WHATSAPP_VERIFY_TOKEN:}") String verifyToken) {
        this.validator = validator;
        this.service = service;
        this.verifyToken = verifyToken;
    }

    @GetMapping
    public ResponseEntity<String> verify(
            @RequestParam(name = "hub.mode", required = false) String mode,
            @RequestParam(name = "hub.verify_token", required = false) String token,
            @RequestParam(name = "hub.challenge", required = false) String challenge) {
        if (!"subscribe".equals(mode) || verifyToken.isBlank() ||
            !verifyToken.equals(token) || challenge == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(challenge);
    }

    @PostMapping
    public ResponseEntity<Void> receive(@RequestBody byte[] body,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature) {
        if (!validator.isValid(body, signature)) {
            log.warn("WhatsApp webhook POST rejected: missing or invalid signature; bytes={}", body.length);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        try {
            log.info("WhatsApp webhook POST signature verified; bytes={}", body.length);
            service.accept(new String(body, StandardCharsets.UTF_8));
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            log.warn("WhatsApp webhook rejected: invalid payload ({})", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            // A Meta poderá tentar novamente. Não expor o conteúdo em logs.
            log.error("WhatsApp webhook processing failed; Meta may retry", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
