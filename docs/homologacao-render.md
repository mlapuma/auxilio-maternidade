# Demonstração no Render com Neon de homologação

O arquivo `render.homolog.yaml` descreve dois serviços separados dos serviços existentes na branch `main`.

- API: `auxilio-maternidade-hml-mlapuma-api`, Docker, contexto `backend/`, health check `/api/health`.
- Página e painel: `auxilio-maternidade-hml-mlapuma`, static site, diretório publicado `frontend/dist/frontend/browser` e rewrite `/*` para `/index.html`.
- Branch de código: `feature/whatsapp-lead-integration-v102`.
- Banco: usar somente a conexão da branch Neon `homologacao-whatsapp`, confirmada pelo responsável.

## Configuração

As credenciais do banco e da Meta devem ser cadastradas como variáveis privadas do serviço API. Não as inclua no Git. O Blueprint declara essas variáveis com `sync: false`.

O frontend recebe `API_BASE_URL` durante `npm run build`. O script `prebuild` gera `public/config.js`, carregado antes do Angular. Esse arquivo contém somente o endereço público da API. Em localhost, as chamadas continuam usando o proxy local.

A API recebe `CORS_ALLOWED_ORIGINS` com o endereço HTTPS exato do frontend de homologação. Não adicionar `*` nem misturar outros ambientes. O painel usa `ADMIN_USER` e `ADMIN_PASSWORD_HASH` BCrypt. Sem essas duas variáveis válidas, a autenticação permanece desabilitada.

## Ativação

1. Confirmar a conexão Neon de homologação.
2. Implantar a API e conferir as migrações Flyway e `/api/health`.
3. Implantar o frontend com a URL pública real da API; ajustar CORS se o Render atribuir um domínio diferente.
4. Testar `/questionario`, `/contato`, `/admin`, autenticação e criação de um cadastro fictício.
5. Configurar na Meta o callback HTTPS da API de homologação, no caminho `/api/whatsapp/webhook`, com o verify token correspondente.
6. Testar WhatsApp pelo número permitido. A restrição de destinatário não deve ser removida implicitamente para a demonstração.
7. Desligar a execução local durante o teste final para comprovar que a demonstração depende apenas dos serviços hospedados.

O plano Free pode suspender a API após inatividade, atrasando o primeiro acesso. Os serviços existentes não devem ser alterados por este deploy. Os deploys automáticos ficam desativados para manter estável a versão apresentada.

## Verificação

- `npm run test:runtime-config`
- `npm test -- --watch=false --include=src/app/api-base-url.spec.ts`
- `npm run build` com `API_BASE_URL` do ambiente.
- Testes backend `HomologationCorsTest`, `LeadControllerTest`, `WhatsAppAdminHistorySecurityTest` e `WhatsAppOutboxAdminSecurityTest`.
