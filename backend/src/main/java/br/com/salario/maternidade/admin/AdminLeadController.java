package br.com.salario.maternidade.admin;

import br.com.salario.maternidade.lead.Lead;
import br.com.salario.maternidade.lead.LeadRepository;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/leads")
public class AdminLeadController {
    private final LeadRepository repository;
    private final HistoricoStatusRepository historico;

    public AdminLeadController(LeadRepository repository, HistoricoStatusRepository historico) {
        this.repository = repository;
        this.historico = historico;
    }

    @GetMapping
    public Page<LeadResumo> listar(@PageableDefault(size = 20, sort = "criadoEm", direction = Sort.Direction.DESC) Pageable pageable,
                                  @RequestParam(required = false) StatusAtendimento status) {
        if (pageable.getPageSize() > 100) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST, "Tamanho máximo da página: 100");
        }
        return (status == null ? repository.findAll(pageable) : repository.findByStatus(status.name(), pageable))
            .map(LeadResumo::from);
    }

    public enum StatusAtendimento { NOVO, EM_ATENDIMENTO, CONCLUIDO }

    public record AtualizarStatusRequest(@NotNull StatusAtendimento status) {}

    @PatchMapping("/{id}/status")
    @Transactional
    public LeadResumo atualizarStatus(@PathVariable UUID id, @Valid @RequestBody AtualizarStatusRequest request) {
        Lead lead = repository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Atendimento não encontrado"));
        String anterior = lead.getStatus();
        String novo = request.status().name();
        if (!anterior.equals(novo)) {
            lead.atualizarStatus(novo);
            historico.save(new HistoricoStatus(id, anterior, novo));
        }
        return LeadResumo.from(repository.save(lead));
    }

    @GetMapping("/{id}/historico")
    public List<HistoricoResumo> listarHistorico(@PathVariable UUID id) {
        if (!repository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Atendimento não encontrado");
        }
        return historico.findByLeadIdOrderByAlteradoEmDesc(id).stream()
            .map(item -> new HistoricoResumo(item.getId(), item.getStatusAnterior(),
                item.getStatusNovo(), item.getAlteradoEm()))
            .toList();
    }

    public record HistoricoResumo(UUID id, String statusAnterior, String statusNovo,
                                  OffsetDateTime alteradoEm) {}

    public record LeadResumo(UUID id, String protocolo, String nome, String telefone,
                             String email, String status, OffsetDateTime criadoEm) {
        static LeadResumo from(Lead lead) {
            return new LeadResumo(lead.getId(), lead.getProtocolo(), lead.getNome(),
                lead.getTelefone(), lead.getEmail(), lead.getStatus(), lead.getCriadoEm());
        }
    }
}
