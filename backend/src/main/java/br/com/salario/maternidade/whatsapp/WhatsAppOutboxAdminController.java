package br.com.salario.maternidade.whatsapp;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Operational counters only. SecurityConfig restricts /api/admin/** to ADMIN.
 * No phone numbers, message content or recipient identifiers are returned.
 */
@RestController
@RequestMapping("/api/admin/whatsapp/outbox")
public class WhatsAppOutboxAdminController {
    private final WhatsAppOutboxMetrics metrics;

    public WhatsAppOutboxAdminController(WhatsAppOutboxMetrics metrics) {
        this.metrics = metrics;
    }

    @GetMapping("/metrics")
    public ResponseEntity<WhatsAppOutboxMetrics.Counts> metrics() {
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .body(metrics.counts());
    }
}
