package br.com.salario.maternidade.lead;

import java.time.OffsetDateTime;
import java.util.UUID;

public record LeadResponse(UUID id, String protocolo, String status, OffsetDateTime criadoEm) {
    public static LeadResponse from(Lead lead) {
        return new LeadResponse(lead.getId(), lead.getProtocolo(), lead.getStatus(), lead.getCriadoEm());
    }
}
