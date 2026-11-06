package br.com.salario.maternidade.whatsapp;

public record WhatsAppInboundMessage(String messageId, String sender, String text,
                                     WhatsAppConversationFlow.Step expectedStep) {
    public WhatsAppInboundMessage(String messageId, String sender, String text) {
        this(messageId, sender, text, null);
    }
    public WhatsAppInboundMessage {
        if (messageId == null || messageId.isBlank()) throw new IllegalArgumentException("Missing message id");
        if (sender == null || !sender.matches("[1-9][0-9]{7,14}")) throw new IllegalArgumentException("Invalid sender");
        if (text == null || text.isBlank()) throw new IllegalArgumentException("Missing text");
    }
}
