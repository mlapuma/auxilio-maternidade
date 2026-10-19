package br.com.salario.maternidade.triagem;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/triagem")
public class TriagemController {
    @PostMapping
    public TriagemResponse analisar(@Valid @RequestBody TriagemRequest request) {
        if (!request.houveNascimentoOuAdocao()) {
            return new TriagemResponse("PRECISA_AVALIACAO",
                "É necessário avaliar o evento e a situação individual com o escritório.", request.desejaAtendimento());
        }
        if (!request.possuiVinculoOuContribuicao()) {
            return new TriagemResponse("PRECISA_AVALIACAO",
                "A ausência de contribuição informada não determina inelegibilidade. Solicite análise individual.", request.desejaAtendimento());
        }
        return new TriagemResponse("ENCAMINHAR_ANALISE",
            "Há informações iniciais para avaliação pelo escritório. Isto não confirma direito ao benefício.", request.desejaAtendimento());
    }
}
