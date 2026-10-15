package br.com.salario.maternidade.lead;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "leads")
public class Lead {
    @Id
    private UUID id;
    @Column(nullable = false, unique = true, length = 32)
    private String protocolo;
    @Column(nullable = false, length = 160)
    private String nome;
    @Column(nullable = false, length = 20)
    private String telefone;
    @Column(length = 254)
    private String email;
    @Column(length = 80)
    private String origem;
    @Column(nullable = false, length = 32)
    private String status;
    @Column(name = "aviso_privacidade_versao", nullable = false, length = 20)
    private String avisoPrivacidadeVersao;
    @Column(name = "consentimento_contato", nullable = false)
    private boolean consentimentoContato;
    @Column(name = "criado_em", nullable = false)
    private OffsetDateTime criadoEm;

    protected Lead() {}
    public Lead(UUID id, String protocolo, String nome, String telefone, String email,
                String origem, String avisoPrivacidadeVersao, boolean consentimentoContato,
                OffsetDateTime criadoEm) {
        this.id = id;
        this.protocolo = protocolo;
        this.nome = nome;
        this.telefone = telefone;
        this.email = email;
        this.origem = origem;
        this.status = "NOVO";
        this.avisoPrivacidadeVersao = avisoPrivacidadeVersao;
        this.consentimentoContato = consentimentoContato;
        this.criadoEm = criadoEm;
    }
    public UUID getId() { return id; }
    public String getProtocolo() { return protocolo; }
    public String getStatus() { return status; }
    public OffsetDateTime getCriadoEm() { return criadoEm; }
}
