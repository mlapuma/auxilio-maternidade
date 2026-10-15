package br.com.salario.maternidade.lead;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(LeadController.class)
class LeadControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean LeadRepository repository;

    @Test
    void criaLead() throws Exception {
        when(repository.save(any(Lead.class))).thenAnswer(invocation -> invocation.getArgument(0));
        mvc.perform(post("/api/leads").contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"nome":"Cliente Teste","telefone":"11999999999","origem":"facebook",
                "avisoPrivacidadeVersao":"v1","consentimentoContato":true}
                """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("NOVO"))
            .andExpect(jsonPath("$.protocolo").exists());
    }

    @Test
    void rejeitaTelefoneInvalido() throws Exception {
        mvc.perform(post("/api/leads").contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"nome":"Cliente Teste","telefone":"abc","avisoPrivacidadeVersao":"v1",
                "consentimentoContato":false}
                """))
            .andExpect(status().isBadRequest());
    }
}
