-- The webhook history writer uses ON CONFLICT (mensagem_externa_id).
-- PostgreSQL requires a matching unique constraint/index for that conflict target.
-- Preserve existing data: fail migration if duplicate external IDs exist,
-- so they can be reviewed rather than silently deleting message history.
CREATE UNIQUE INDEX IF NOT EXISTS ux_whatsapp_message_history_external_id
    ON whatsapp_message_history (mensagem_externa_id);
