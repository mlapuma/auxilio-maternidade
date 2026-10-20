package br.com.salario.maternidade.admin;

import br.com.salario.maternidade.config.SecurityConfig;
import br.com.salario.maternidade.lead.LeadRepository;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
}
