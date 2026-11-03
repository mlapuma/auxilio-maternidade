# WhatsApp Cloud API — Fase 1

Branch isolada: `feature/whatsapp-cloud-chatbot-fase1`. Não habilitar produção antes de revisão.

## Escopo
- V5 cria atendimentos, associa ao lead e registra origem UTM.
- O atendimento reutiliza o protocolo aleatório já gerado no cadastro.
- Após o cadastro, a interface pode oferecer continuação voluntária via WhatsApp.
- Nenhuma mensagem é enviada automaticamente e nenhum dado pessoal entra no texto pré-preenchido.
- A integração com Cloud API, verificação de número, webhooks e IA serão feitas nas próximas fases.

## Configuração
Defina `WHATSAPP_PUBLIC_NUMBER` no build do frontend (formato internacional sem +, por exemplo 5511999999999). Sem número configurado, não mostrar o botão.

## Segurança pendente
O endpoint de criação ainda precisa de proteção contra enumeração e abuso antes de uso público; não divulgar respostas do lead pelo protocolo. Revisar consentimento, política de privacidade e retenção com o escritório.

## Validação recomendada
- Executar `cd backend && mvn test`.
- Executar `cd frontend && npm ci && npm run build`.
- Confirmar migração V5 em banco de homologação.
- Validar que cadastro e triagem continuam funcionais.
