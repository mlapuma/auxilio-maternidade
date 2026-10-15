package br.com.salario.maternidade.lead;

import jakarta.validation.Valid;
import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/leads")
public class LeadController {
    private final LeadRepository repository;
    public LeadController(LeadRepository repository) { this.repository = repository; }

    @PostMapping
    public ResponseEntity<LeadResponse> criar(@Valid @RequestBody CriarLeadRequest request) {
        UUID id = UUID.randomUUID();
        // Protocolo aleatório: não expõe sequência de cadastros nem dados pessoais.
        String protocolo = "SM-" + id.toString().replace("-", "").substring(0, 16).toUpperCase();
        Lead lead = new Lead(id, protocolo, request.nome().trim(), request.telefone().trim(),
            request.email() == null ? null : request.email().trim(),
            request.origem() == null ? null : request.origem().trim(),
            request.avisoPrivacidadeVersao(), request.consentimentoContato(), OffsetDateTime.now(ZoneOffset.UTC));
        repository.save(lead);
        return ResponseEntity.created(URI.create("/api/leads/" + id)).body(LeadResponse.from(lead));
    }
}
