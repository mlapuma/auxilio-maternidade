package br.com.salario.maternidade.whatsapp;

import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WhatsAppAdminHistoryStoreTest {
    @Test
    void messageQueryUsesSelectedConversationIdNotAllConversationsForLead() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        UUID leadId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        when(jdbc.query(anyString(), any(RowMapper.class), eq(leadId)))
            .thenAnswer(invocation -> {
                String sql = invocation.getArgument(0);
                assertTrue(sql.contains("WHERE lead_id = ?"));
                ResultSet rs = mock(ResultSet.class);
                when(rs.getObject("id")).thenReturn(conversationId);
                when(rs.getString("etapa")).thenReturn("HANDOFF");
                when(rs.getBoolean("consentimento")).thenReturn(true);
                when(rs.getString("respostas")).thenReturn("{}");
                @SuppressWarnings("unchecked")
                RowMapper<Object[]> mapper = invocation.getArgument(1);
                return java.util.Collections.singletonList(mapper.mapRow(rs, 0));
            });
        when(jdbc.query(anyString(), any(RowMapper.class), eq(conversationId), eq(50)))
            .thenAnswer(invocation -> {
                String sql = invocation.getArgument(0);
                assertTrue(sql.contains("WHERE h.conversa_id = ?"));
                assertFalse(sql.contains("WHERE c.lead_id = ?"));
                return List.of();
            });

        var result = new WhatsAppAdminHistoryStore(jdbc).findByLeadId(leadId, 50);

        assertTrue(result.isPresent());
        assertEquals("HANDOFF", result.orElseThrow().step());
        verify(jdbc).query(anyString(), any(RowMapper.class), eq(conversationId), eq(50));
    }
}
