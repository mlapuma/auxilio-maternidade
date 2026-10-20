package br.com.salario.maternidade.triagem;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "triagens")
public class Triagem {
    @Id
    private UUID id;
    @Column(name = "lead_id", nullable = false)
    private UUID leadId;
    @Column(name = "houve_nascimento_ou_adocao", nullable = false)
    private boolean houveNascimentoOuAdocao;
    @Column(name = "possui_vinculo_ou_contribuicao", nullable = false)
    private boolean possuiVinculoOuContribuicao;
    @Column(name = "deseja_atendimento", nullable = false)
    private boolean desejaAtendimento;
    @Column(nullable = false, length = 40)
    private String classificacao;
    @Column(name = "criado_em", nullable = false)
    private OffsetDateTime criadoEm;

    protected Triagem() {}
    public Triagem(UUID leadId, TriagemRequest request, String classificacao) {
        this.id = UUID.randomUUID();
        this.leadId = leadId;
        this.houveNascimentoOuAdocao = request.houveNascimentoOuAdocao();
        this.possuiVinculoOuContribuicao = request.possuiVinculoOuContribuicao();
        this.desejaAtendimento = request.desejaAtendimento();
        this.classificacao = classificacao;
        this.criadoEm = OffsetDateTime.now(java.time.ZoneOffset.UTC);
    }
    public UUID getId() { return id; }
    public String getClassificacao() { return classificacao; }
    public boolean isHouveNascimentoOuAdocao() { return houveNascimentoOuAdocao; }
    public boolean isPossuiVinculoOuContribuicao() { return possuiVinculoOuContribuicao; }
    public boolean isDesejaAtendimento() { return desejaAtendimento; }
    public OffsetDateTime getCriadoEm() { return criadoEm; }
}
