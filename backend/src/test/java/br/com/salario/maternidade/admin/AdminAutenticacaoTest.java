package br.com.salario.maternidade.admin;

import br.com.salario.maternidade.config.SecurityConfig;
import br.com.salario.maternidade.lead.LeadRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.*;

@WebMvcTest(AdminLeadController.class)
@Import(SecurityConfig.class)
class AdminAutenticacaoTest {
    @Autowired MockMvc mvc;
    @MockitoBean LeadRepository repository;
    @MockitoBean HistoricoStatusRepository historico;

    @DynamicPropertySource
    static void configurarCredenciais(DynamicPropertyRegistry registry) {
        registry.add("ADMIN_USER", () -> "escritorio");
        registry.add("ADMIN_PASSWORD_HASH",
            () -> new BCryptPasswordEncoder().encode("senha-de-teste"));
    }

    @Test
    void aceitaCredenciaisValidasComBcrypt() throws Exception {
        when(repository.findAll(any(org.springframework.data.domain.Pageable.class)))
            .thenReturn(org.springframework.data.domain.Page.empty());
        mvc.perform(get("/api/admin/leads")
                .with(httpBasic("escritorio", "senha-de-teste")))
            .andExpect(status().isOk());
        verify(repository).findAll(any(org.springframework.data.domain.Pageable.class));
    }

    @Test
    void rejeitaSenhaIncorreta() throws Exception {
        mvc.perform(get("/api/admin/leads")
                .with(httpBasic("escritorio", "senha-errada")))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(repository, historico);
    }

    @Test
    void rejeitaAcessoSemCredenciais() throws Exception {
        mvc.perform(get("/api/admin/leads"))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(repository, historico);
    }
}
