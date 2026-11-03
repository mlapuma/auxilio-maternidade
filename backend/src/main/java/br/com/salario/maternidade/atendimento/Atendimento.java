package br.com.salario.maternidade.atendimento;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "atendimentos")
public class Atendimento {
    @Id private UUID id;
    @Column(name = "lead_id", nullable = false, unique = true) private UUID leadId;
    @Column(name = "codigo_publico", nullable = false, unique = true, length = 32) private String codigoPublico;
    @Column(name = "tipo_assunto", nullable = false, length = 50) private String tipoAssunto;
    @Column(nullable = false, length = 40) private String status;
    @Column(name = "responsavel_id", length = 120) private String responsavelId;
    @Column(name = "data_inicio", nullable = false) private OffsetDateTime dataInicio;
    @Column(name = "data_atualizacao", nullable = false) private OffsetDateTime dataAtualizacao;

    protected Atendimento() {}
    public Atendimento(UUID leadId, String codigoPublico) {
        this.id = UUID.randomUUID();
        this.leadId = leadId;
        this.codigoPublico = codigoPublico;
        this.tipoAssunto = "SALARIO_MATERNIDADE";
        this.status = "NOVO";
        this.dataInicio = OffsetDateTime.now(ZoneOffset.UTC);
        this.dataAtualizacao = this.dataInicio;
    }
    public UUID getId() { return id; }
    public UUID getLeadId() { return leadId; }
    public String getCodigoPublico() { return codigoPublico; }
    public String getStatus() { return status; }
}
