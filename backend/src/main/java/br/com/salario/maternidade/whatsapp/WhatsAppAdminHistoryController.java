package br.com.salario.maternidade.whatsapp;

import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Restricted by SecurityConfig: /api/admin/** requires ADMIN. */
@RestController
@RequestMapping("/api/admin/leads")
public class WhatsAppAdminHistoryController {
    private final WhatsAppAdminHistoryStore history;

    public WhatsAppAdminHistoryController(WhatsAppAdminHistoryStore history) {
        this.history = history;
    }

    @GetMapping("/{leadId}/whatsapp")
    public ResponseEntity<WhatsAppAdminHistoryStore.Conversation> history(
            @PathVariable UUID leadId,
            @RequestParam(defaultValue = "50") int limit) {
        if (limit < 1 || limit > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Limite deve estar entre 1 e 100");
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(history.findByLeadId(leadId, limit)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Conversa WhatsApp não encontrada para este lead")));
    }
}
