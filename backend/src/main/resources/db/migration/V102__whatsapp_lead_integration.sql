-- V102 - Integracao WhatsApp, leads e historico

ALTER TABLE whatsapp_conversations
    ADD COLUMN IF NOT EXISTS lead_id UUID;

ALTER TABLE whatsapp_conversations
    ADD COLUMN IF NOT EXISTS respostas_triagem JSONB;

CREATE TABLE IF NOT EXISTS whatsapp_message_history (
    id UUID PRIMARY KEY,
    conversa_id UUID NOT NULL,
    mensagem_externa_id VARCHAR(255),
    direcao VARCHAR(10) NOT NULL,
    conteudo TEXT NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_whatsapp_message_history_conversation
        FOREIGN KEY (conversa_id)
        REFERENCES whatsapp_conversations(id),

    CONSTRAINT chk_whatsapp_message_history_direction
        CHECK (direcao IN ('ENTRADA', 'SAIDA'))
);

CREATE INDEX IF NOT EXISTS idx_whatsapp_conversations_lead_id
    ON whatsapp_conversations(lead_id);

CREATE INDEX IF NOT EXISTS idx_whatsapp_message_history_conversation
    ON whatsapp_message_history(conversa_id, criado_em);

CREATE UNIQUE INDEX IF NOT EXISTS uq_whatsapp_message_history_external
    ON whatsapp_message_history(mensagem_externa_id)
    WHERE mensagem_externa_id IS NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_whatsapp_conversations_lead'
          AND conrelid = 'whatsapp_conversations'::regclass
    ) THEN
        ALTER TABLE whatsapp_conversations
        ADD CONSTRAINT fk_whatsapp_conversations_lead
        FOREIGN KEY (lead_id)
        REFERENCES leads(id);
    END IF;
END $$;

-- Remover a exclusao em cascata da chave existente em HML
ALTER TABLE whatsapp_message_history
    DROP CONSTRAINT IF EXISTS fk_whatsapp_message_history_conversation;

ALTER TABLE whatsapp_message_history
    ADD CONSTRAINT fk_whatsapp_message_history_conversation
    FOREIGN KEY (conversa_id)
    REFERENCES whatsapp_conversations(id);