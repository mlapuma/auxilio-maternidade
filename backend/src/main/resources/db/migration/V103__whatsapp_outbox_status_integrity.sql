-- Enforce the six operational states used by the outbox dispatcher and admin metrics.
-- V101 used VARCHAR(20) without a CHECK constraint.
ALTER TABLE whatsapp_outbox
    ADD CONSTRAINT chk_whatsapp_outbox_status
    CHECK (status IN ('PENDENTE', 'ENVIANDO', 'ENVIADO',
                     'REVISAR', 'CANCELADO', 'EXPIRADO'));

-- Helps identify orphaned dispatch claims without scanning unrelated statuses.
CREATE INDEX idx_whatsapp_outbox_enviando_atualizado
    ON whatsapp_outbox (atualizado_em)
    WHERE status = 'ENVIANDO';
