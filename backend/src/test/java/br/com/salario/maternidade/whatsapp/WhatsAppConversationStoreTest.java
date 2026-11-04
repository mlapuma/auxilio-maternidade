package br.com.salario.maternidade.whatsapp;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WhatsAppConversationStoreTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final WhatsAppConversationStore store = new WhatsAppConversationStore(jdbc);

    @Test
    void unlinkLeadOnlyClearsConversationAssociation() {
        org.springframework.jdbc.core.JdbcTemplate jdbc =
            org.mockito.Mockito.mock(org.springframework.jdbc.core.JdbcTemplate.class);
        var store = new WhatsAppConversationStore(jdbc);

        store.unlinkLead("5511999999999");

        org.mockito.Mockito.verify(jdbc).update(
            org.mockito.ArgumentMatchers.<String>argThat(sql ->
                sql.contains("UPDATE whatsapp_conversations")
                && sql.contains("SET lead_id = NULL")
                && sql.contains("WHERE telefone = ?")
                && !sql.contains("DELETE")),
            org.mockito.ArgumentMatchers.eq("5511999999999"));
    }

    @Test
    void cancelsOnlyPendingConsentRequests() {
        org.springframework.jdbc.core.JdbcTemplate jdbc =
            org.mockito.Mockito.mock(org.springframework.jdbc.core.JdbcTemplate.class);
        var store = new WhatsAppConversationStore(jdbc);

        store.cancelPendingConsentRequests("5511999999999");

        org.mockito.Mockito.verify(jdbc).update(
            org.mockito.ArgumentMatchers.<String>argThat(sql ->
                sql.contains("status = 'CANCELADO'")
                && sql.contains("status = 'PENDENTE'")
                && sql.contains("tipo_resposta = 'CONSENT_REQUEST'")
                && sql.contains("telefone = ?")),
            org.mockito.ArgumentMatchers.eq("5511999999999"));
    }

    @Test
    void savesStateWithoutMessageBody() {
        store.save(new WhatsAppConversationStore.Conversation(
            "5511999999999", WhatsAppConversationFlow.Step.CONSENT, false));
        verify(jdbc).update(contains("ON CONFLICT (telefone)"), any(),
            eq("5511999999999"), eq("CONSENT"), eq(false));
    }

    @Test
    void rejectsInvalidPhone() {
        assertThrows(IllegalArgumentException.class, () -> store.deleteByPhone("invalid"));
        assertThrows(IllegalArgumentException.class, () -> store.findByPhone(""));
        verifyNoInteractions(jdbc);
    }

    @Test
    void removesConversationOnRequest() {
        store.deleteByPhone("5511999999999");
        verify(jdbc).update(contains("DELETE FROM whatsapp_conversations"), eq("5511999999999"));
    }
    @Test
    void storesStructuredAnswerOnlyWithConsent() {
        store.saveScreeningAnswer("5511999999999", "situacao_trabalho", "NAO_SEI");
        verify(jdbc).update(contains("consentimento = true"),
            eq("situacao_trabalho"), eq("NAO_SEI"), eq("5511999999999"));
    }

    @Test
    void rejectsUnsupportedScreeningKey() {
        assertThrows(IllegalArgumentException.class, () ->
            store.saveScreeningAnswer("5511999999999", "outro", "SIM"));
        verifyNoInteractions(jdbc);
    }
    @Test
    void linksBrazilianLeadWithNationalOrInternationalFormatOnlyIfUniqueAndConsenting() {
        store.linkUniqueExistingLead("5511999999999");
        verify(jdbc).update(argThat(sql ->
            sql.contains("c.consentimento = true")
            && sql.contains("c.lead_id IS NULL")
            && sql.contains("count(*)")
            && sql.contains("regexp_replace")
            && sql.contains("IN (?, ?)")),
            eq("5511999999999"), eq("11999999999"), eq("5511999999999"),
            eq("5511999999999"), eq("11999999999"));
    }

    @Test
    void doesNotStripCountryCodeFromOtherCountries() {
        store.linkUniqueExistingLead("14155552671");
        verify(jdbc).update(anyString(), eq("14155552671"), eq("14155552671"),
            eq("14155552671"), eq("14155552671"), eq("14155552671"));
    }

    @Test
    void supportsBrazilianLandlineWithAreaCode() {
        store.linkUniqueExistingLead("551133334444");
        verify(jdbc).update(anyString(), eq("551133334444"), eq("1133334444"),
            eq("551133334444"), eq("551133334444"), eq("1133334444"));
    }

    @Test
    void cancelsOnlyPendingRepliesForPhone() {
        store.cancelPendingReplies("5511999999999");
        verify(jdbc).update(argThat(sql ->
            sql.contains("status = 'CANCELADO'")
            && sql.contains("status = 'PENDENTE'")
            && sql.contains("WHERE telefone = ?")),
            eq("5511999999999"));
    }

    @Test
    void refusesInvalidPhoneForLeadLink() {
        assertThrows(IllegalArgumentException.class, () -> store.linkUniqueExistingLead("invalid"));
        verifyNoInteractions(jdbc);
    }
}
