package br.com.salario.maternidade.lead;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import br.com.salario.maternidade.config.SecurityConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(LeadController.class)
@Import(SecurityConfig.class)
class LeadControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean LeadRepository repository;

    @Test
    void criaLead() throws Exception {
        when(repository.save(any(Lead.class))).thenAnswer(invocation -> invocation.getArgument(0));
        mvc.perform(post("/api/leads").contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"nome":"Cliente Teste","telefone":"11999999999","origem":"SITE",
                "avisoPrivacidadeVersao":"v1","consentimentoContato":true}
                """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("NOVO"))
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.protocolo").exists());
        verify(repository).save(any(Lead.class));
    }

    @Test
    void rejeitaTelefoneInvalido() throws Exception {
        mvc.perform(post("/api/leads").contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"nome":"Cliente Teste","telefone":"abc","avisoPrivacidadeVersao":"v1",
                "consentimentoContato":true}
                """))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(repository);
    }

    @Test
    void rejeitaConsentimentoNegado() throws Exception {
        mvc.perform(post("/api/leads").contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"nome":"Cliente Teste","telefone":"11999999999","avisoPrivacidadeVersao":"v1",
                "consentimentoContato":false}
                """))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(repository);
    }

    @Test
    void rejeitaConsentimentoAusente() throws Exception {
        mvc.perform(post("/api/leads").contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"nome":"Cliente Teste","telefone":"11999999999","avisoPrivacidadeVersao":"v1"}
                """))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(repository);
    }

    @Test
    void rejeitaNomeEmBranco() throws Exception {
        mvc.perform(post("/api/leads").contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"nome":" ","telefone":"11999999999","avisoPrivacidadeVersao":"v1",
                "consentimentoContato":true}
                """))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(repository);
    }
}
