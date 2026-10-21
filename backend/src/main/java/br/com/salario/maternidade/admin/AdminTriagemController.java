package br.com.salario.maternidade.admin;

import br.com.salario.maternidade.lead.LeadRepository;
import br.com.salario.maternidade.triagem.TriagemRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin/leads/{leadId}/triagens")
public class AdminTriagemController {
    private final LeadRepository leads;
    private final TriagemRepository triagens;

    public AdminTriagemController(LeadRepository leads, TriagemRepository triagens) {
        this.leads = leads;
        this.triagens = triagens;
    }

    @GetMapping
    public List<TriagemDetalhe> listar(@PathVariable UUID leadId) {
        if (!leads.existsById(leadId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Atendimento não encontrado");
        }
        return triagens.findByLeadIdOrderByCriadoEmDesc(leadId).stream()
            .map(t -> new TriagemDetalhe(t.getId(), t.getClassificacao(),
                t.isHouveNascimentoOuAdocao(), t.isPossuiVinculoOuContribuicao(),
                t.isDesejaAtendimento(), t.getCriadoEm()))
            .toList();
    }

    public record TriagemDetalhe(UUID id, String classificacao,
        boolean houveNascimentoOuAdocao, boolean possuiVinculoOuContribuicao,
        boolean desejaAtendimento, OffsetDateTime criadoEm) {}
}
