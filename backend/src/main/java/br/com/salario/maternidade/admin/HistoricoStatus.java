package br.com.salario.maternidade.admin;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "historico_status")
public class HistoricoStatus {
    @Id private UUID id;
    @Column(name = "lead_id", nullable = false) private UUID leadId;
    @Column(name = "status_anterior", nullable = false) private String statusAnterior;
    @Column(name = "status_novo", nullable = false) private String statusNovo;
    @Column(name = "alterado_em", nullable = false) private OffsetDateTime alteradoEm;

    protected HistoricoStatus() {}
    public HistoricoStatus(UUID leadId, String anterior, String novo) {
        this.id = UUID.randomUUID();
        this.leadId = leadId;
        this.statusAnterior = anterior;
        this.statusNovo = novo;
        this.alteradoEm = OffsetDateTime.now(ZoneOffset.UTC);
    }
    public UUID getId() { return id; }
    public String getStatusAnterior() { return statusAnterior; }
    public String getStatusNovo() { return statusNovo; }
    public OffsetDateTime getAlteradoEm() { return alteradoEm; }
}
