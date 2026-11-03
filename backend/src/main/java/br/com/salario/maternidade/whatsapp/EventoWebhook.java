package br.com.salario.maternidade.whatsapp;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "eventos_webhook")
public class EventoWebhook {
    @Id private UUID id;
    @Column(name = "identificador_externo", nullable = false, unique = true, length = 255)
    private String identificadorExterno;
    @Column(name = "tipo_evento", nullable = false, length = 40)
    private String tipoEvento;
    @Column(name = "data_recebimento", nullable = false)
    private OffsetDateTime dataRecebimento;
    @Column(name = "status_processamento", nullable = false, length = 40)
    private String statusProcessamento;

    protected EventoWebhook() {}
    public EventoWebhook(String externalId, String type) {
        id = UUID.randomUUID();
        identificadorExterno = externalId;
        tipoEvento = type;
        dataRecebimento = OffsetDateTime.now(ZoneOffset.UTC);
        statusProcessamento = "RECEBIDO";
    }
}
