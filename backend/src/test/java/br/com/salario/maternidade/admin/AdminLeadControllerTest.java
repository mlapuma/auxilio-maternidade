package br.com.salario.maternidade.admin;

import br.com.salario.maternidade.config.SecurityConfig;
import br.com.salario.maternidade.lead.LeadRepository;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import org.springframework.http.MediaType;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminLeadController.class)
@Import(SecurityConfig.class)
class AdminLeadControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean LeadRepository repository;
    @MockitoBean HistoricoStatusRepository historico;

    @Test
    void bloqueiaAlteracaoAnonima() throws Exception {
        mvc.perform(patch("/api/admin/leads/00000000-0000-0000-0000-000000000001/status")
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CONCLUIDO\"}"))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(repository);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void rejeitaStatusInvalido() throws Exception {
        mvc.perform(patch("/api/admin/leads/00000000-0000-0000-0000-000000000001/status")
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"INDEFINIDO\"}"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(repository);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void rejeitaAtendimentoInexistente() throws Exception {
        mvc.perform(patch("/api/admin/leads/00000000-0000-0000-0000-000000000001/status")
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CONCLUIDO\"}"))
            .andExpect(status().isNotFound());
    }

    @Test
    void bloqueiaAcessoAnonimo() throws Exception {
        mvc.perform(get("/api/admin/leads")).andExpect(status().isUnauthorized());
        verifyNoInteractions(repository);
    }

    @Test
    @WithMockUser(roles = "USER")
    void bloqueiaUsuarioSemPermissao() throws Exception {
        mvc.perform(get("/api/admin/leads")).andExpect(status().isForbidden());
        verifyNoInteractions(repository);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void rejeitaPaginaExcessiva() throws Exception {
        mvc.perform(get("/api/admin/leads?size=101")).andExpect(status().isBadRequest());
        verifyNoInteractions(repository);
    }
    @Test
    void bloqueiaHistoricoAnonimo() throws Exception {
        mvc.perform(get("/api/admin/leads/00000000-0000-0000-0000-000000000001/historico"))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(repository, historico);
    }

    @Test
    @WithMockUser(roles = "USER")
    void bloqueiaHistoricoSemPermissao() throws Exception {
        mvc.perform(get("/api/admin/leads/00000000-0000-0000-0000-000000000001/historico"))
            .andExpect(status().isForbidden());
        verifyNoInteractions(repository, historico);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void retorna404ParaHistoricoDeLeadInexistente() throws Exception {
        mvc.perform(get("/api/admin/leads/00000000-0000-0000-0000-000000000001/historico"))
            .andExpect(status().isNotFound());
        verifyNoInteractions(historico);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void permiteConsultarHistorico() throws Exception {
        java.util.UUID id = java.util.UUID.fromString("00000000-0000-0000-0000-000000000001");
        when(repository.existsById(id)).thenReturn(true);
        mvc.perform(get("/api/admin/leads/" + id + "/historico"))
            .andExpect(status().isOk());
        verify(historico).findByLeadIdOrderByAlteradoEmDesc(id);
    }

}
