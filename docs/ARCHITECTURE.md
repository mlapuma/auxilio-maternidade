# Arquitetura — MVP v1

Monólito modular em Spring Boot, frontend Angular e PostgreSQL.

## Módulos previstos
- leads: captura de interessados e consentimento
- questionario: perguntas versionadas
- preanalise: resultado preliminar informativo, sem decisão jurídica
- atendimento: protocolo, status e encaminhamento humano
- notificacao: aviso à equipe, sem dados sensíveis no corpo do e-mail
- auth: acesso restrito ao painel

## Integrações
WhatsApp Business no MVP: somente link com texto pré-preenchido. Clique não equivale a mensagem enviada. Nenhuma leitura automática de conversas.

## Segurança
Não publicar dados pessoais em logs, URLs ou repositório. Definir política de retenção, base legal, controle de acesso e consentimento antes da coleta real.

## Futuro
Módulos contrato e assinatura eletrônica, desacoplados do fluxo de triagem.
