ALTER TABLE whatsapp_conversations ADD COLUMN correcao_pendente JSONB;
ALTER TABLE whatsapp_outbox ADD COLUMN interactive_payload TEXT;
