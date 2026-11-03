# Fase 2 — Webhook WhatsApp Cloud API (implementação inicial)

## Meta
Configurar o app na Meta for Developers, selecionar WhatsApp Business Platform, vincular WABA e número.
Definir no Render (nunca no Git):
- `WHATSAPP_VERIFY_TOKEN` — token arbitrário forte para validação inicial
- `WHATSAPP_APP_SECRET` — App Secret da Meta
- `WHATSAPP_ACCESS_TOKEN` — reservado para cliente de envio (ainda não implementado)
- `WHATSAPP_PHONE_NUMBER_ID` — reservado para cliente de envio
- `WHATSAPP_GRAPH_VERSION` — reservar versão suportada pela Meta
- `WHATSAPP_PUBLIC_NUMBER` — número público em formato internacional, apenas dígitos

Webhook HTTPS: `https://auxilio-maternidade.onrender.com/api/whatsapp/webhook`
GET responde challenge apenas com verify token correto.
POST verifica HMAC-SHA256 sobre bytes originais antes de processar JSON.
V6 armazena identificadores de mensagens e status para deduplicação básica.

## Limitações e próximos passos
Ainda não há envio de mensagens pela Cloud API, vínculo seguro de telefone, histórico de conteúdo, fila durável ou chatbot.
A deduplicação usa índice único, mas é necessário tratar concorrência e retry com transação independente para operação robusta.
Não conectar o webhook de produção antes de revisar a política de retenção, testes e capacidade de recebimento.
