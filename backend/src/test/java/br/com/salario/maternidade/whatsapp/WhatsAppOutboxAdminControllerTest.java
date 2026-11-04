package br.com.salario.maternidade.whatsapp;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class WhatsAppOutboxAdminControllerTest {
    @Test
    void exposesOnlyAggregateCountsAndDisablesCaching() throws Exception {
        var metrics = mock(WhatsAppOutboxMetrics.class);
        when(metrics.counts()).thenReturn(new WhatsAppOutboxMetrics.Counts(2, 1, 7, 3, 4, 5));
        var mvc = MockMvcBuilders.standaloneSetup(
            new WhatsAppOutboxAdminController(metrics)).build();
        mvc.perform(get("/api/admin/whatsapp/outbox/metrics"))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.pending").value(2))
            .andExpect(jsonPath("$.sending").value(1))
            .andExpect(jsonPath("$.sent").value(7))
            .andExpect(jsonPath("$.review").value(3))
            .andExpect(jsonPath("$.cancelled").value(4))
            .andExpect(jsonPath("$.expired").value(5))
            .andExpect(jsonPath("$.phone").doesNotExist())
            .andExpect(jsonPath("$.body").doesNotExist());
        verify(metrics).counts();
    }
}
