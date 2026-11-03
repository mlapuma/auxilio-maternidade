package br.com.salario.maternidade.atendimento;

import br.com.salario.maternidade.lead.Lead;
import br.com.salario.maternidade.lead.LeadRepository;
import br.com.salario.maternidade.triagem.Triagem;
import br.com.salario.maternidade.triagem.TriagemRepository;
import br.com.salario.maternidade.triagem.TriagemRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AtendimentoControllerTest {
    private final AtendimentoRepository atendimentos = mock(AtendimentoRepository.class);
    private final LeadRepository leads = mock(LeadRepository.class);
    private final TriagemRepository triagens = mock(TriagemRepository.class);
    private final AtendimentoController controller = new AtendimentoController(atendimentos, leads, triagens);

    @Test void leadInexistenteNaoCriaAtendimento() {
        UUID id = UUID.randomUUID();
        when(leads.findById(id)).thenReturn(Optional.empty());
        assertEquals(HttpStatus.NOT_FOUND, controller.criar(new AtendimentoController.CriarAtendimentoRequest(id)).getStatusCode());
        verifyNoInteractions(atendimentos);
    }

    @Test void semTriagemNaoCriaAtendimento() {
        UUID id = UUID.randomUUID();
        Lead lead = new Lead(id, "SM-TESTE", "Cliente Teste", "11999999999", null,
            "SITE", "v1", true, OffsetDateTime.now(ZoneOffset.UTC));
        when(leads.findById(id)).thenReturn(Optional.of(lead));
        when(triagens.findByLeadIdOrderByCriadoEmDesc(id)).thenReturn(List.of());
        assertEquals(HttpStatus.BAD_REQUEST, controller.criar(new AtendimentoController.CriarAtendimentoRequest(id)).getStatusCode());
        verifyNoInteractions(atendimentos);
    }
}
