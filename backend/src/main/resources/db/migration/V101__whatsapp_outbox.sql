-- Outgoing messages are queued atomically with webhook processing.
-- Message body contains personal communications; apply restricted DB access and retention policy.
CREATE TABLE whatsapp_outbox (
    id UUID PRIMARY KEY,
    mensagem_entrada_id VARCHAR(255) NOT NULL UNIQUE,
    telefone VARCHAR(20) NOT NULL,
    corpo TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDENTE',
    tentativas INTEGER NOT NULL DEFAULT 0,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_whatsapp_outbox_pendentes ON whatsapp_outbox (criado_em)
    WHERE status = 'PENDENTE';
