-- Persist only the current state and consent; never store message bodies here.
CREATE TABLE IF NOT EXISTS whatsapp_conversations (
    id UUID PRIMARY KEY,
    telefone VARCHAR(20) NOT NULL UNIQUE,
    etapa VARCHAR(24) NOT NULL,
    consentimento BOOLEAN NOT NULL DEFAULT FALSE,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_whatsapp_conversations_atualizado
    ON whatsapp_conversations (atualizado_em);
