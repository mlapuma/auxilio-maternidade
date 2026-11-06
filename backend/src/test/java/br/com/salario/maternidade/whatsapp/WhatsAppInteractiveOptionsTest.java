package br.com.salario.maternidade.whatsapp;

import java.util.HashSet;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static br.com.salario.maternidade.whatsapp.WhatsAppConversationFlow.Step.*;

class WhatsAppInteractiveOptionsTest {
    @Test
    void screeningUsesButtonsAndLongMenusUseLists() {
        assertEquals("button", WhatsAppInteractiveOptions.payload(CONSENT, "Autoriza?").get("type"));
        assertEquals(2, WhatsAppInteractiveOptions.choices(BIRTH).size());
        assertEquals(3, WhatsAppInteractiveOptions.choices(WORK).size());
        assertEquals("list", WhatsAppInteractiveOptions.payload(HANDOFF, "Menu").get("type"));
        assertEquals("list", WhatsAppInteractiveOptions.payload(FAQ, "Dúvidas").get("type"));
        assertNull(WhatsAppInteractiveOptions.payload(START, "Olá"));
        assertNull(WhatsAppInteractiveOptions.payload(HANDOFF, "x".repeat(1025)));
    }

    @Test
    void allChoicesHaveUniqueIdsAndRespectWhatsAppTitleLimits() {
        var ids = new HashSet<String>();
        for (var step : WhatsAppConversationFlow.Step.values()) {
            var choices = WhatsAppInteractiveOptions.choices(step);
            assertTrue(choices.size() <= 10);
            for (var choice : choices) {
                assertTrue(ids.add(choice.id()), choice.id());
                assertTrue(choice.title().length() <= (choices.size() <= 3 ? 20 : 24), choice.title());
                var parsed = WhatsAppInteractiveOptions.parseChoice(choice.id());
                assertEquals(step, parsed.expectedStep());
                assertEquals(choice.answer(), parsed.answer());
            }
        }
    }
}
