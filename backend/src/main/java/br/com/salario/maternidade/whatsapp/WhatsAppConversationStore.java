package br.com.salario.maternidade.whatsapp;

import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class WhatsAppConversationStore {
    public record Conversation(String phone, WhatsAppConversationFlow.Step step, boolean consent) {}
    public record SavedQuestionnaire(UUID triageId, java.util.Map<String, String> answers) {}
    public record CorrectionDraft(UUID triageId, java.util.Map<String, String> original,
        java.util.Map<String, String> candidate, String questionId, String nonce) {}
    private static final com.fasterxml.jackson.databind.ObjectMapper JSON = new com.fasterxml.jackson.databind.ObjectMapper();

    private final JdbcTemplate jdbc;

    public WhatsAppConversationStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Conversation> findByPhone(String phone) {
        validatePhone(phone);
        return jdbc.query(
            "SELECT telefone, etapa, consentimento FROM whatsapp_conversations WHERE telefone = ?",
            rs -> rs.next()
                ? Optional.of(new Conversation(rs.getString("telefone"),
                    WhatsAppConversationFlow.Step.valueOf(rs.getString("etapa")),
                    rs.getBoolean("consentimento")))
                : Optional.empty(),
            phone);
    }

    public Optional<SavedQuestionnaire> findSavedQuestionnaire(String phone) {
        validatePhone(phone);
        String national = phone.startsWith("55") && (phone.length() == 12 || phone.length() == 13) ? phone.substring(2) : phone;
        return jdbc.query("""
            SELECT t.id, t.respostas_detalhadas FROM whatsapp_conversations c
              JOIN leads l ON l.id = c.lead_id
              JOIN LATERAL (SELECT id, respostas_detalhadas FROM triagens WHERE lead_id = l.id
                  ORDER BY criado_em DESC, id DESC LIMIT 1) t ON true
             WHERE c.telefone = ? AND c.consentimento = true AND c.etapa <> 'STOPPED'
               AND l.consentimento_contato = true
               AND regexp_replace(l.telefone, '[^0-9]', '', 'g') IN (?, ?)
            """, rs -> {
                if (!rs.next() || rs.getString("respostas_detalhadas") == null) return Optional.empty();
                try {
                    java.util.Map<String, String> answers = JSON.readValue(rs.getString("respostas_detalhadas"),
                        new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, String>>() {});
                    return Optional.of(new SavedQuestionnaire((UUID) rs.getObject("id"), answers));
                } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                    return Optional.empty();
                }
            }, phone, phone, national);
    }

    public Optional<CorrectionDraft> findCorrectionDraft(String phone) {
        validatePhone(phone);
        return jdbc.query("""
            SELECT correcao_pendente::text AS draft FROM whatsapp_conversations
             WHERE telefone = ? AND consentimento = true AND etapa <> 'STOPPED'
            """, rs -> {
                if (!rs.next() || rs.getString("draft") == null) return Optional.empty();
                try { return Optional.of(JSON.readValue(rs.getString("draft"), CorrectionDraft.class)); }
                catch (com.fasterxml.jackson.core.JsonProcessingException e) { return Optional.empty(); }
            }, phone);
    }

    public void saveCorrectionDraft(String phone, CorrectionDraft draft) {
        validatePhone(phone);
        jdbc.update("""
            UPDATE whatsapp_conversations SET correcao_pendente = ?::jsonb, atualizado_em = CURRENT_TIMESTAMP
             WHERE telefone = ? AND consentimento = true AND etapa <> 'STOPPED' AND lead_id IS NOT NULL
            """, json(draft), phone);
    }

    public void clearCorrectionDraft(String phone) {
        validatePhone(phone);
        jdbc.update("UPDATE whatsapp_conversations SET correcao_pendente = NULL WHERE telefone = ?", phone);
    }

    /** Insert a new questionnaire version only after confirmation and an optimistic version check. */
    public boolean applyQuestionnaireCorrection(String phone, CorrectionDraft draft) {
        validatePhone(phone);
        String national = phone.startsWith("55") && (phone.length() == 12 || phone.length() == 13) ? phone.substring(2) : phone;
        boolean birth = WhatsAppQuestionnaire.birth(draft.candidate());
        boolean contribution = WhatsAppQuestionnaire.contribution(draft.candidate());
        boolean wants = WhatsAppQuestionnaire.wantsContact(draft.candidate());
        String answers = json(draft.candidate());
        return jdbc.update("""
            WITH current_questionnaire AS (
                SELECT c.id AS conversation_id, l.id AS lead_id FROM whatsapp_conversations c
                  JOIN leads l ON l.id = c.lead_id
                 WHERE c.telefone = ? AND c.consentimento = true AND c.etapa = 'CORRECT_CONFIRM'
                   AND l.consentimento_contato = true
                   AND regexp_replace(l.telefone, '[^0-9]', '', 'g') IN (?, ?)
                   AND c.correcao_pendente ->> 'nonce' = ?
                   AND (SELECT id FROM triagens WHERE lead_id = l.id ORDER BY criado_em DESC, id DESC LIMIT 1) = ?
                 FOR UPDATE OF c, l
            ), inserted AS (
                INSERT INTO triagens (id, lead_id, houve_nascimento_ou_adocao, possui_vinculo_ou_contribuicao,
                    deseja_atendimento, classificacao, respostas_detalhadas, criado_em)
                SELECT ?, lead_id, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP FROM current_questionnaire
                RETURNING id, lead_id
            )
            UPDATE whatsapp_conversations c SET respostas_triagem = COALESCE(c.respostas_triagem, '{}'::jsonb)
                || jsonb_build_object('triagem_id', i.id, 'respostas_detalhadas', ?::text,
                    'houve_nascimento_ou_adocao', ?::boolean, 'possui_vinculo_ou_contribuicao', ?::boolean,
                    'deseja_atendimento', ?::boolean),
                correcao_pendente = NULL, atualizado_em = CURRENT_TIMESTAMP
              FROM inserted i WHERE c.telefone = ? AND c.lead_id = i.lead_id
            """, phone, phone, national, draft.nonce(), draft.triageId(), UUID.randomUUID(), birth, contribution, wants,
            birth && contribution ? "ENCAMINHAR_ANALISE" : "PRECISA_AVALIACAO", answers,
            answers, birth, contribution, wants, phone) > 0;
    }

    private static String json(Object value) {
        try { return JSON.writeValueAsString(value); }
        catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalArgumentException("Invalid correction data"); }
    }

    public void save(Conversation conversation) {
        validatePhone(conversation.phone());
        if (conversation.step() == null) throw new IllegalArgumentException("Missing step");
        jdbc.update("""
            INSERT INTO whatsapp_conversations
                (id, telefone, etapa, consentimento, criado_em, atualizado_em)
            VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            ON CONFLICT (telefone) DO UPDATE SET
                etapa = EXCLUDED.etapa,
                consentimento = EXCLUDED.consentimento,
                atualizado_em = CURRENT_TIMESTAMP
            """, UUID.randomUUID(), conversation.phone(),
            conversation.step().name(), conversation.consent());
    }

    /** Stores only validated screening answers after consent. */
    public void saveScreeningAnswer(String phone, String key, String value) {
        validatePhone(phone);
        if (!"situacao_bebe".equals(key) && !"situacao_trabalho".equals(key)) {
            throw new IllegalArgumentException("Unsupported screening answer key");
        }
        if (value == null || !value.matches("[A-Z_]{2,20}")) {
            throw new IllegalArgumentException("Invalid screening answer");
        }
        jdbc.update("""
            UPDATE whatsapp_conversations
               SET respostas_triagem = jsonb_set(
                   COALESCE(respostas_triagem, '{}'::jsonb),
                   ARRAY[?]::text[], to_jsonb(?::text), true),
                   atualizado_em = CURRENT_TIMESTAMP
             WHERE telefone = ? AND consentimento = true
            """, key, value, phone);
    }

    /**
     * Remove the conversation-to-lead association on opt-out or restart.
     * A new session may link a lead again only after renewed consent.
     */
    public void unlinkLead(String phone) {
        validatePhone(phone);
        jdbc.update("""
            UPDATE whatsapp_conversations
               SET lead_id = NULL, correcao_pendente = NULL, atualizado_em = CURRENT_TIMESTAMP
             WHERE telefone = ?
            """, phone);
    }

    public void clearScreeningAnswers(String phone) {
        validatePhone(phone);
        jdbc.update("""
            UPDATE whatsapp_conversations
               SET respostas_triagem = NULL, atualizado_em = CURRENT_TIMESTAMP
             WHERE telefone = ?
            """, phone);
    }

    /** Append requests for review without overwriting the submitted questionnaire. */
    public void appendFollowUp(String phone, String type, String text) {
        validatePhone(phone);
        if (!java.util.Set.of("complementos", "correcoes", "duvidas", "solicitacoes_equipe").contains(type)
                || text == null || text.isBlank() || text.length() > 4096) {
            throw new IllegalArgumentException("Invalid follow-up");
        }
        jdbc.update("""
            UPDATE whatsapp_conversations
               SET respostas_triagem = jsonb_set(COALESCE(respostas_triagem, '{}'::jsonb),
                   ARRAY[?]::text[], COALESCE(respostas_triagem -> ?, '[]'::jsonb)
                   || jsonb_build_array(jsonb_build_object('texto', ?::text, 'registrado_em', CURRENT_TIMESTAMP)), true),
                   atualizado_em = CURRENT_TIMESTAMP
             WHERE telefone = ? AND consentimento = true AND etapa <> 'STOPPED'
            """, type, type, text, phone);
    }

    public Optional<String> findLinkedLeadStatus(String phone) {
        validatePhone(phone);
        return jdbc.query("""
            SELECT l.status FROM whatsapp_conversations c JOIN leads l ON l.id = c.lead_id
             WHERE c.telefone = ? AND c.consentimento = true AND c.etapa <> 'STOPPED'
               AND l.consentimento_contato = true
            """, rs -> rs.next() ? Optional.of(rs.getString("status")) : Optional.empty(), phone);
    }

    /**
     * Associates only a uniquely matching existing lead that also consented to contact.
     * Brazilian leads may store a national number (DDD + subscriber) while
     * WhatsApp supplies country code 55. Never use suffix-only matching.
     */
    public int linkUniqueExistingLead(String phone) {
        validatePhone(phone);
        String national = phone.startsWith("55") && (phone.length() == 12 || phone.length() == 13)
            ? phone.substring(2) : phone;
        return jdbc.update("""
            UPDATE whatsapp_conversations c
               SET lead_id = (
                   SELECT l.id FROM leads l
                    WHERE regexp_replace(l.telefone, '[^0-9]', '', 'g') IN (?, ?)
                      AND l.consentimento_contato = true
               ),
                   atualizado_em = CURRENT_TIMESTAMP
             WHERE c.telefone = ?
               AND c.consentimento = true
               AND c.lead_id IS NULL
               AND (SELECT count(*) FROM leads l
                    WHERE regexp_replace(l.telefone, '[^0-9]', '', 'g') IN (?, ?)
                      AND l.consentimento_contato = true) = 1
            """, phone, national, phone, phone, national);
    }

    /** Resume a saved questionnaire only for its consented lead's exact phone. */
    public boolean resumeLandingQuestionnaire(String phone, String protocol) {
        validatePhone(phone);
        if (protocol == null || !protocol.matches("SM-[A-F0-9]{16}")) return false;
        String national = phone.startsWith("55") && (phone.length() == 12 || phone.length() == 13)
            ? phone.substring(2) : phone;
        return jdbc.update("""
            INSERT INTO whatsapp_conversations
                (id, telefone, etapa, consentimento, lead_id, respostas_triagem, criado_em, atualizado_em)
            SELECT ?, ?, 'HANDOFF', true, l.id,
                   jsonb_build_object('origem', 'LANDING_PAGE', 'triagem_id', t.id,
                       'houve_nascimento_ou_adocao', t.houve_nascimento_ou_adocao,
                       'possui_vinculo_ou_contribuicao', t.possui_vinculo_ou_contribuicao,
                       'deseja_atendimento', t.deseja_atendimento,
                       'respostas_detalhadas', t.respostas_detalhadas),
                   CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
              FROM atendimentos a
              JOIN leads l ON l.id = a.lead_id
              JOIN LATERAL (
                  SELECT * FROM triagens WHERE lead_id = l.id
                   ORDER BY criado_em DESC, id DESC LIMIT 1
              ) t ON true
             WHERE a.codigo_publico = ?
               AND regexp_replace(l.telefone, '[^0-9]', '', 'g') IN (?, ?)
               AND l.consentimento_contato = true
               AND t.deseja_atendimento = true
            ON CONFLICT (telefone) DO UPDATE SET
                etapa = EXCLUDED.etapa, consentimento = EXCLUDED.consentimento,
                respostas_triagem = CASE WHEN whatsapp_conversations.lead_id = EXCLUDED.lead_id
                    THEN COALESCE(whatsapp_conversations.respostas_triagem, '{}'::jsonb) || EXCLUDED.respostas_triagem
                    ELSE EXCLUDED.respostas_triagem END,
                lead_id = EXCLUDED.lead_id, correcao_pendente = NULL,
                atualizado_em = CURRENT_TIMESTAMP
            WHERE whatsapp_conversations.etapa <> 'STOPPED'
            """, UUID.randomUUID(), phone, protocol, phone, national) > 0;
    }

    /** Cancels unsent replies when consent is revoked or the flow restarts. */
    /**
     * A consent reply can arrive before the queued invitation is dispatched.
     * Drop obsolete invitations without touching triage replies.
     */
    public int cancelPendingConsentRequests(String phone) {
        validatePhone(phone);
        return jdbc.update("""
            UPDATE whatsapp_outbox
               SET status = 'CANCELADO', atualizado_em = CURRENT_TIMESTAMP
             WHERE telefone = ?
               AND status = 'PENDENTE'
               AND tipo_resposta = 'CONSENT_REQUEST'
            """, phone);
    }

    /**
     * An incoming answer supersedes earlier unsent triage prompts.
     * Consent invitations are handled separately and are never affected here.
     */
    public int cancelPendingStandardReplies(String phone) {
        validatePhone(phone);
        return jdbc.update("""
            UPDATE whatsapp_outbox
               SET status = 'CANCELADO', atualizado_em = CURRENT_TIMESTAMP
             WHERE telefone = ?
               AND status = 'PENDENTE'
               AND tipo_resposta = 'STANDARD'
            """, phone);
    }

    public int cancelPendingReplies(String phone) {
        validatePhone(phone);
        return jdbc.update("""
            UPDATE whatsapp_outbox
               SET status = 'CANCELADO', atualizado_em = CURRENT_TIMESTAMP
             WHERE telefone = ? AND status = 'PENDENTE'
            """, phone);
    }

    public void deleteByPhone(String phone) {
        validatePhone(phone);
        jdbc.update("DELETE FROM whatsapp_conversations WHERE telefone = ?", phone);
    }

    private static void validatePhone(String phone) {
        if (phone == null || !phone.matches("[1-9][0-9]{7,14}")) {
            throw new IllegalArgumentException("Invalid international phone");
        }
    }
}
