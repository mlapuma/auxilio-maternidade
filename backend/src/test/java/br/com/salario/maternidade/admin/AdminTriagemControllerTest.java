package br.com.salario.maternidade.admin;

import br.com.salario.maternidade.config.SecurityConfig;
import br.com.salario.maternidade.lead.LeadRepository;
import br.com.salario.maternidade.triagem.TriagemRepository;
import java.util.UUID;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminTriagemController.class)
@Import(SecurityConfig.class)
class AdminTriagemControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean LeadRepository leads;
    @MockitoBean TriagemRepository triagens;

    private static final UUID LEAD_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String URL = "/api/admin/leads/" + LEAD_ID + "/triagens";

    @Test
    void bloqueiaConsultaAnonima() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isUnauthorized());
        verifyNoInteractions(leads, triagens);
    }

    @Test
    @WithMockUser(roles = "USER")
    void bloqueiaUsuarioSemPermissao() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isForbidden());
        verifyNoInteractions(leads, triagens);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void retorna404ParaLeadInexistente() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isNotFound());
        verify(leads).existsById(LEAD_ID);
        verifyNoInteractions(triagens);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void permiteConsultaDoAdministrador() throws Exception {
        when(leads.existsById(LEAD_ID)).thenReturn(true);
        mvc.perform(get(URL)).andExpect(status().isOk());
        verify(triagens).findByLeadIdOrderByCriadoEmDesc(LEAD_ID);
    }
}
