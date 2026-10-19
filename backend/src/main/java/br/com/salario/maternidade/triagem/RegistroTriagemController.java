package br.com.salario.maternidade.triagem;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import br.com.salario.maternidade.lead.LeadRepository;

@RestController
@RequestMapping("/api/triagens")
public class RegistroTriagemController {
    private final TriagemRepository triagens;
    private final LeadRepository leads;
    private final TriagemController analise = new TriagemController();

    public RegistroTriagemController(TriagemRepository triagens, LeadRepository leads) {
        this.triagens = triagens;
        this.leads = leads;
    }

    @PostMapping
    public ResponseEntity<TriagemResponse> registrar(@Valid @RequestBody RegistrarTriagemRequest request) {
        UUID leadId = request.leadId();
        if (!leads.existsById(leadId)) {
            return ResponseEntity.notFound().build();
        }
        TriagemResponse resultado = analise.analisar(request.respostas());
        triagens.save(new Triagem(leadId, request.respostas(), resultado.classificacao()));
        return ResponseEntity.status(HttpStatus.CREATED).body(resultado);
    }
}
