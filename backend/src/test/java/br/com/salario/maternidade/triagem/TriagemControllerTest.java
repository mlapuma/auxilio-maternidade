package br.com.salario.maternidade.triagem;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class TriagemControllerTest {
    private final TriagemController controller = new TriagemController();

    @Test
    void encaminhaParaAnaliseSemPrometerBeneficio() {
        var response = controller.analisar(new TriagemRequest(true, true, true));
        assertEquals("ENCAMINHAR_ANALISE", response.classificacao());
        assertTrue(response.encaminharAtendimento());
        assertFalse(response.mensagem().contains("direito garantido"));
    }

    @Test
    void ausenciaDeContribuicaoNaoDeterminaInelegibilidade() {
        var response = controller.analisar(new TriagemRequest(true, false, true));
        assertEquals("PRECISA_AVALIACAO", response.classificacao());
    }

    @Test
    void respeitaPreferenciaDeContato() {
        var response = controller.analisar(new TriagemRequest(false, false, false));
        assertFalse(response.encaminharAtendimento());
    }
}
