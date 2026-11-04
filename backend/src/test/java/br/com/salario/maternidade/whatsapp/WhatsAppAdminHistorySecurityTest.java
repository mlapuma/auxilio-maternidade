package br.com.salario.maternidade.whatsapp;

import br.com.salario.maternidade.config.SecurityConfig;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc(addFilters = true)
@WebMvcTest(WhatsAppAdminHistoryController.class)
@Import(SecurityConfig.class)
class WhatsAppAdminHistorySecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean WhatsAppAdminHistoryStore history;

    @Test
    void anonymousCannotReadConversation() throws Exception {
        mvc.perform(get("/api/admin/leads/" + UUID.randomUUID() + "/whatsapp"))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(history);
    }

    @Test
    @WithMockUser(roles = "USER")
    void nonAdminCannotReadConversation() throws Exception {
        mvc.perform(get("/api/admin/leads/" + UUID.randomUUID() + "/whatsapp"))
            .andExpect(status().isForbidden());
        verifyNoInteractions(history);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanReadLinkedConversationWithoutCache() throws Exception {
        UUID leadId = UUID.randomUUID();
        when(history.findByLeadId(leadId, 50)).thenReturn(Optional.of(
            new WhatsAppAdminHistoryStore.Conversation(
                leadId, "HANDOFF", true, "{}", List.of())));
        mvc.perform(get("/api/admin/leads/" + leadId + "/whatsapp"))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
            .andExpect(jsonPath("$.leadId").value(leadId.toString()))
            .andExpect(jsonPath("$.step").value("HANDOFF"));
        verify(history).findByLeadId(leadId, 50);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void invalidLimitReturnsBadRequestWithoutQuery() throws Exception {
        mvc.perform(get("/api/admin/leads/" + UUID.randomUUID() + "/whatsapp")
                .param("limit", "101"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(history);
    }
}
