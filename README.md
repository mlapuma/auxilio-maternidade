# Salário-Maternidade — MVP v1

Sistema de captação e triagem inicial para o escritório de advocacia.

## Fluxo
Campanha → landing page → questionário → pré-análise informativa → cadastro → link WhatsApp Business → atendimento humano.

**A pré-análise não determina direito ao benefício nem substitui a avaliação jurídica.** Conteúdo, critérios e publicidade devem ser aprovados pelo advogado antes da publicação.

## Stack
- Java 21, Spring Boot 3.5, PostgreSQL, Flyway
- Angular (frontend: próxima entrega)
- Docker Compose para banco local

## Executar backend
1. Instale Java 21, Maven e Docker.
2. Execute `docker compose up -d`.
3. Execute `cd backend && mvn spring-boot:run`.
4. Teste `GET http://localhost:8080/api/health`.

## Roadmap
- [x] Estrutura inicial do backend, banco e endpoint de saúde
- [ ] Questionário e cadastro de interessados
- [ ] Pré-análise configurável aprovada pelo escritório
- [ ] Landing page Angular e link para WhatsApp Business
- [ ] E-mail de notificação e painel autenticado
- [ ] Testes, segurança, LGPD e implantação

### Evolução futura (v2)
Contratos gerados automaticamente e assinatura eletrônica integrada, sem alterar o fluxo básico de cadastro.
