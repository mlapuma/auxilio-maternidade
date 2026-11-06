package br.com.salario.maternidade.lead;

import br.com.salario.maternidade.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(LeadController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "CORS_ALLOWED_ORIGINS=https://auxilio-maternidade-hml-mlapuma.onrender.com")
class HomologationCorsTest {
    @Autowired MockMvc mvc;
    @MockitoBean LeadRepository repository;

    @Test void permitsConfiguredHomologationOrigin() throws Exception {
        mvc.perform(options("/api/leads").header("Origin", "https://auxilio-maternidade-hml-mlapuma.onrender.com")
            .header("Access-Control-Request-Method", "POST").header("Access-Control-Request-Headers", "content-type"))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin", "https://auxilio-maternidade-hml-mlapuma.onrender.com"));
    }
    @Test void doesNotMixOtherEnvironmentsOrOpenAdminAccess() throws Exception {
        mvc.perform(options("/api/leads").header("Origin", "https://other.example.com")
            .header("Access-Control-Request-Method", "POST")).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/leads")).andExpect(status().isUnauthorized());
    }
}
