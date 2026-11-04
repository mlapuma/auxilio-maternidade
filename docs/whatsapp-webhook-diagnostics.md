# Diagnóstico do webhook WhatsApp

O endpoint `GET /api/whatsapp/webhook` **retorna 403 por projeto** quando não recebe `hub.mode=subscribe`, `hub.verify_token` correto e `hub.challenge`. Portanto, o `curl -i` sem parâmetros que retornou 403 **não é evidência de bloqueio do Spring Security**. Em `SecurityConfig`, GET e POST para esse caminho estão explicitamente liberados; no POST, a aplicação exige assinatura HMAC-SHA256 válida.

## Verificação automatizada segura

Com o Spring Boot e o Cloudflare Tunnel em execução, no PowerShell 7+:

```powershell
cd C:\Projetos\auxilio-maternidade-whatsapp\backend
.\scripts\diagnose-whatsapp-webhook.ps1 -PublicBaseUrl "https://SEU-TUNEL.trycloudflare.com"
```

A rotina verifica o GET sem parâmetros (esperado **403**) e o POST com assinatura inválida (esperado **403**) tanto local quanto publicamente. O POST inválido é rejeitado antes do processamento e não deve persistir eventos.

Opcionalmente, configure `WHATSAPP_VERIFY_TOKEN` **somente no ambiente do terminal**, sem publicar o segredo; a rotina testará o GET de challenge válido (esperado **200**). Não compartilhe logs que exponham o token. O script não executa POST com assinatura válida, não envia mensagens e não altera dados.

Se o teste público falhar mas o local passar, investigue o túnel. Se ambos passarem, investigue a entrega real da Meta, os logs HTTP POST, o segredo da aplicação e a tabela `eventos_webhook`. Esses testes não demonstram entrega da Meta nem persistência no Neon.

Para executar os testes Java existentes:

```powershell
mvn -Dtest=WhatsAppWebhookControllerTest,WhatsAppSignatureValidatorTest test
```

Os testes Java precisam ser executados no ambiente de desenvolvimento ou CI; adicionar este roteiro ao GitHub não executa testes automaticamente.
