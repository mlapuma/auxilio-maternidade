package br.com.salario.maternidade.atendimento;

import br.com.salario.maternidade.lead.Lead;
import br.com.salario.maternidade.lead.LeadRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/atendimentos")
public class AtendimentoController {
    private final AtendimentoRepository atendimentos;
    private final LeadRepository leads;

    public AtendimentoController(AtendimentoRepository atendimentos, LeadRepository leads) {
        this.atendimentos = atendimentos;
        this.leads = leads;
    }

    public record CriarAtendimentoRequest(@NotNull UUID leadId) {}
    public record AtendimentoResponse(UUID id, String codigoPublico, String status) {
        static AtendimentoResponse from(Atendimento atendimento) {
            return new AtendimentoResponse(atendimento.getId(), atendimento.getCodigoPublico(), atendimento.getStatus());
        }
    }

    @PostMapping
    @Transactional
    public ResponseEntity<AtendimentoResponse> criar(@Valid @RequestBody CriarAtendimentoRequest request) {
        // O endpoint não retorna dados do lead nem do questionário.
        // A verificação de posse do telefone será obrigatória antes de divulgar dados via WhatsApp.
        Lead lead = leads.findById(request.leadId()).orElse(null);
        if (lead == null) return ResponseEntity.notFound().build();
        if (!lead.isConsentimentoContato()) return ResponseEntity.badRequest().build();
        Atendimento atendimento = atendimentos.findByLeadId(lead.getId()).orElseGet(() ->
            atendimentos.save(new Atendimento(lead.getId(), lead.getProtocolo())));
        return ResponseEntity.created(URI.create("/api/atendimentos/" + atendimento.getId()))
            .body(AtendimentoResponse.from(atendimento));
    }
}
