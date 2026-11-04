-- Explicitly classify non-sensitive consent invitations.
-- Existing messages remain STANDARD and cannot bypass the consent gate.
ALTER TABLE whatsapp_outbox
    ADD COLUMN tipo_resposta VARCHAR(24) NOT NULL DEFAULT 'STANDARD';

ALTER TABLE whatsapp_outbox
    ADD CONSTRAINT chk_whatsapp_outbox_tipo_resposta
    CHECK (tipo_resposta IN ('STANDARD', 'CONSENT_REQUEST'));
