-- Accelerate the per-recipient in-flight guard used by claimOne().
-- Only active dispatch claims need to be indexed; historical rows are excluded.
CREATE INDEX idx_whatsapp_outbox_enviando_telefone
    ON whatsapp_outbox (telefone)
    WHERE status = 'ENVIANDO';
