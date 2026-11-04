package br.com.salario.maternidade.whatsapp;

import br.com.salario.maternidade.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc(addFilters = true)
@WebMvcTest(WhatsAppOutboxAdminController.class)
@Import(SecurityConfig.class)
class WhatsAppOutboxAdminSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean WhatsAppOutboxMetrics metrics;

    @Test
    void anonymousCannotReadMetrics() throws Exception {
        mvc.perform(get("/api/admin/whatsapp/outbox/metrics"))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(metrics);
    }

    @Test
    @WithMockUser(roles = "USER")
    void regularUserCannotReadMetrics() throws Exception {
        mvc.perform(get("/api/admin/whatsapp/outbox/metrics"))
            .andExpect(status().isForbidden());
        verifyNoInteractions(metrics);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void administratorCanReadMetrics() throws Exception {
        when(metrics.counts()).thenReturn(new WhatsAppOutboxMetrics.Counts(2, 1, 7, 3, 4, 5));
        mvc.perform(get("/api/admin/whatsapp/outbox/metrics"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pending").value(2))
            .andExpect(jsonPath("$.review").value(3))
            .andExpect(jsonPath("$.cancelled").value(4))
            .andExpect(jsonPath("$.expired").value(5))
            .andExpect(jsonPath("$.phone").doesNotExist());
        verify(metrics).counts();
    }
}
