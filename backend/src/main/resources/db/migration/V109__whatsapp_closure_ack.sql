-- Only an explicit opt-out/refusal acknowledgement may be sent in STOPPED state.
ALTER TABLE whatsapp_outbox DROP CONSTRAINT chk_whatsapp_outbox_tipo_resposta;
ALTER TABLE whatsapp_outbox ADD CONSTRAINT chk_whatsapp_outbox_tipo_resposta
    CHECK (tipo_resposta IN ('STANDARD', 'CONSENT_REQUEST', 'CLOSURE_ACK'));
