CREATE TABLE eventos_webhook (
    id UUID PRIMARY KEY,
    identificador_externo VARCHAR(255) NOT NULL UNIQUE,
    tipo_evento VARCHAR(40) NOT NULL,
    data_recebimento TIMESTAMPTZ NOT NULL,
    status_processamento VARCHAR(40) NOT NULL
);
CREATE INDEX idx_eventos_webhook_recebimento ON eventos_webhook(data_recebimento DESC);
