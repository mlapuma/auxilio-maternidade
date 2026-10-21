# Implantação da versão 1 — checklist

> **Não publicar ainda:** executar revisão de segurança, testes ponta a ponta e aprovação jurídica antes de liberar campanhas.

## 1. Banco PostgreSQL
- Provisionar PostgreSQL gerenciado, com armazenamento persistente, backups automáticos e acesso restrito ao backend.
- Criar banco e usuário exclusivo da aplicação, com permissões mínimas.
- Exigir conexão criptografada entre backend e banco conforme o provedor (configurar SSL no `DB_URL`).
- Testar restauração de backup em ambiente separado.

## 2. Backend Java
- Implantar a pasta `backend/` com Java 21, executando o JAR gerado por `mvn -B clean package`.
- Definir `SPRING_PROFILES_ACTIVE=prod`.
- Definir `DB_URL`, `DB_USER`, `DB_PASSWORD` no gerenciador de segredos do provedor.
- Definir `ADMIN_USER` e `ADMIN_PASSWORD_HASH` (hash BCrypt, nunca senha em texto puro).
- Definir `PORT` se exigido pela plataforma.
- Verificar `GET /api/health` e migrações Flyway.
- **Não** expor o backend diretamente na internet sem HTTPS e proteção de acesso; não publicar porta do banco.
- Configurar limitação de requisições, proteção contra abuso e logs sem dados pessoais.

## 3. Frontend Angular
- Compilar com `npm ci && npm run build` na pasta `frontend/`.
- Hospedar os arquivos estáticos gerados em serviço com HTTPS e fallback para `index.html`.
- Configurar proxy reverso para encaminhar `/api/*` ao backend, preservando a mesma origem; não enviar credenciais a outro domínio.
- Verificar rotas, formulários, envio de lead e autenticação do painel em navegador real.

## 4. Privacidade e validação
- Publicar política de privacidade revisada pelo escritório, identificar controlador e canal de atendimento ao titular.
- Revisar a base legal, retenção, controle de acesso e exclusão de dados segundo a LGPD.
- Confirmar textos, critérios de pré-análise e publicidade com advogado responsável.
- Testar fluxo completo: campanha → questionário → pré-análise → cadastro → WhatsApp → atendimento.
- Testar acesso não autorizado ao painel, restauração de backup, comportamento sob falha do banco e tratamento de erros.
- Fazer homologação com o escritório antes de apontar o domínio/subdomínio definitivo.

## 5. Liberação
- Registrar domínio/subdomínio e DNS **somente após** a homologação.
- Configurar alertas de indisponibilidade, renovação de certificados e procedimentos de incidentes.
- Registrar versão implantada e procedimento de rollback.

**Situação:** documento de preparação; não implica que infraestrutura ou publicação tenham sido executadas.
