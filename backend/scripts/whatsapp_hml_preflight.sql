-- WhatsApp HML preflight (PostgreSQL, READ ONLY).
-- Run with psql -X -v ON_ERROR_STOP=1 -f backend/scripts/whatsapp_hml_preflight.sql
-- Requires SELECT on Flyway and WhatsApp tables. No personal message bodies are returned.
BEGIN TRANSACTION READ ONLY;

-- All WhatsApp Flyway migrations must be applied successfully.
SELECT version, description, success, installed_on
  FROM flyway_schema_history
 WHERE version IN ('100','101','102','103','104','105')
 ORDER BY installed_rank;

-- Explicit PASS/FAIL for migration coverage.
SELECT CASE WHEN COUNT(*) = 6 THEN 'PASS' ELSE 'FAIL' END AS migration_check,
       COUNT(*) AS successful_migrations, 6 AS expected
  FROM flyway_schema_history
 WHERE version IN ('100','101','102','103','104','105')
   AND success = true;

-- Schema objects and indexes required by consent-aware dispatch.
SELECT to_regclass('whatsapp_conversations') IS NOT NULL AS conversations_present,
       to_regclass('whatsapp_outbox') IS NOT NULL AS outbox_present,
       to_regclass('whatsapp_message_history') IS NOT NULL AS history_present,
       to_regclass('idx_whatsapp_outbox_enviando_telefone') IS NOT NULL AS inflight_index_present;

-- Only aggregate counts; never print phones, names, tokens, or message content.
SELECT status, COUNT(*) AS total
  FROM whatsapp_outbox
 GROUP BY status
 ORDER BY status;

SELECT etapa, consentimento, COUNT(*) AS total
  FROM whatsapp_conversations
 GROUP BY etapa, consentimento
 ORDER BY etapa, consentimento;

-- Invalid operational conditions worth reviewing before enabling the sender.
SELECT
    (SELECT COUNT(*) FROM whatsapp_outbox
      WHERE status = 'ENVIANDO'
        AND atualizado_em < CURRENT_TIMESTAMP - INTERVAL '10 minutes') AS stuck_sending,
    (SELECT COUNT(*) FROM whatsapp_outbox
      WHERE status = 'PENDENTE'
        AND criado_em < CURRENT_TIMESTAMP - INTERVAL '23 hours') AS expired_pending,
    (SELECT COUNT(*) FROM whatsapp_conversations
      WHERE etapa = 'STOPPED' AND consentimento = true) AS stopped_with_consent,
    (SELECT COUNT(*) FROM whatsapp_conversations
      WHERE consentimento = false AND lead_id IS NOT NULL) AS linked_without_consent,
    (SELECT COUNT(*) FROM whatsapp_conversations
      WHERE consentimento = false AND respostas_triagem IS NOT NULL) AS answers_without_consent,
    (SELECT COUNT(*) FROM whatsapp_outbox o
      LEFT JOIN whatsapp_conversations c ON c.telefone = o.telefone
      WHERE o.status = 'PENDENTE' AND c.id IS NULL) AS pending_without_conversation;

-- Duplicate in-flight recipients indicate a concurrency issue.
SELECT COUNT(*) AS recipients_with_multiple_inflight
  FROM (SELECT telefone FROM whatsapp_outbox
        WHERE status = 'ENVIANDO'
        GROUP BY telefone HAVING COUNT(*) > 1) duplicates;

COMMIT;
