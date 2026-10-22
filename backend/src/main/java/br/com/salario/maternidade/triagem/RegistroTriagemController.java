package br.com.salario.maternidade.triagem;

import jakarta.validation.Valid;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import br.com.salario.maternidade.lead.LeadRepository;

@RestController
@RequestMapping("/api/triagens")
public class RegistroTriagemController {
    private final TriagemRepository triagens;
    private final LeadRepository leads;
    private final ObjectMapper objectMapper;
    private final TriagemController analise = new TriagemController();

    public RegistroTriagemController(TriagemRepository triagens, LeadRepository leads, ObjectMapper objectMapper) {
        this.triagens = triagens;
        this.leads = leads;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<TriagemResponse> registrar(@Valid @RequestBody RegistrarTriagemRequest request) throws JsonProcessingException {
        UUID leadId = request.leadId();
        if (!leads.existsById(leadId)) {
            return ResponseEntity.notFound().build();
        }
        TriagemResponse resultado = analise.analisar(request.respostas());
        triagens.save(new Triagem(leadId, request.respostas(), resultado.classificacao(),
            request.respostasDetalhadas() == null ? null : objectMapper.writeValueAsString(request.respostasDetalhadas())));
        return ResponseEntity.status(HttpStatus.CREATED).body(resultado);
    }
}
