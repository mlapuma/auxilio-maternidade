package br.com.salario.maternidade.whatsapp;

import java.sql.ResultSet;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WhatsAppOutboxMetricsTest {
    @Test
    void countsAllOperationalStatesWithoutExposingPersonalData() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("pending")).thenReturn(1L);
        when(rs.getLong("sending")).thenReturn(2L);
        when(rs.getLong("sent")).thenReturn(3L);
        when(rs.getLong("review")).thenReturn(4L);
        when(rs.getLong("cancelled")).thenReturn(5L);
        when(rs.getLong("expired")).thenReturn(6L);
        when(jdbc.queryForObject(anyString(), org.mockito.ArgumentMatchers.<RowMapper<WhatsAppOutboxMetrics.Counts>>any()))
            .thenAnswer(inv -> {
                RowMapper<WhatsAppOutboxMetrics.Counts> mapper = inv.getArgument(1);
                return mapper.mapRow(rs, 0);
            });

        var counts = new WhatsAppOutboxMetrics(jdbc).counts();

        assertEquals(new WhatsAppOutboxMetrics.Counts(1, 2, 3, 4, 5, 6), counts);
        verify(jdbc).queryForObject(org.mockito.ArgumentMatchers.<String>argThat(sql ->
            sql.contains("status = 'CANCELADO'")
            && sql.contains("status = 'EXPIRADO'")
            && sql.contains("status = 'REVISAR'")
            && !sql.contains("telefone")
            && !sql.contains("corpo")),
            org.mockito.ArgumentMatchers.<RowMapper<WhatsAppOutboxMetrics.Counts>>any());
    }
}
