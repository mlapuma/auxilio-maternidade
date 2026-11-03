package br.com.salario.maternidade.atendimento;

import br.com.salario.maternidade.lead.Lead;
import br.com.salario.maternidade.lead.LeadRepository;
import br.com.salario.maternidade.triagem.TriagemRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/atendimentos")
public class AtendimentoController {
    private final AtendimentoRepository atendimentos;
    private final LeadRepository leads;
    private final TriagemRepository triagens;
    @Value("${WHATSAPP_PUBLIC_NUMBER:}") private String whatsappPublicNumber;

    public AtendimentoController(AtendimentoRepository atendimentos, LeadRepository leads, TriagemRepository triagens) {
        this.atendimentos = atendimentos;
        this.leads = leads;
        this.triagens = triagens;
    }

    public record CriarAtendimentoRequest(@NotNull UUID leadId) {}
    public record AtendimentoResponse(UUID id, String codigoPublico, String status, String whatsappUrl) {}
    private AtendimentoResponse response(Atendimento atendimento) {
        String number = whatsappPublicNumber.replaceAll("[^0-9]", "");
        String url = number.matches("[1-9][0-9]{9,14}")
            ? "https://wa.me/" + number + "?text=" + URLEncoder.encode("Codigo " + atendimento.getCodigoPublico(), StandardCharsets.UTF_8)
            : null;
        return new AtendimentoResponse(atendimento.getId(), atendimento.getCodigoPublico(), atendimento.getStatus(), url);
    }

    @PostMapping
    @Transactional
    public ResponseEntity<AtendimentoResponse> criar(@Valid @RequestBody CriarAtendimentoRequest request) {
        // O endpoint não retorna dados do lead nem do questionário.
        // A verificação de posse do telefone será obrigatória antes de divulgar dados via WhatsApp.
        Lead lead = leads.findById(request.leadId()).orElse(null);
        if (lead == null) return ResponseEntity.notFound().build();
        if (!lead.isConsentimentoContato() || triagens.findByLeadIdOrderByCriadoEmDesc(lead.getId()).isEmpty()) return ResponseEntity.badRequest().build();
        Atendimento atendimento = atendimentos.findByLeadId(lead.getId()).orElseGet(() ->
            atendimentos.save(new Atendimento(lead.getId(), lead.getProtocolo())));
        return ResponseEntity.created(URI.create("/api/atendimentos/" + atendimento.getId()))
            .body(response(atendimento));
    }
}
