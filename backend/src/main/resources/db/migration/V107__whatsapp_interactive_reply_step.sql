-- Snapshot the question being answered so queued replies keep the correct choices.
ALTER TABLE whatsapp_outbox ADD COLUMN etapa_resposta VARCHAR(24);
