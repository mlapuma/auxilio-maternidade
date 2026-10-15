# API — MVP v1

## POST /api/leads
Registra um interessado. Não realiza pré-análise jurídica.

Exemplo:
```json
{
  "nome": "Cliente Exemplo",
  "telefone": "11999999999",
  "email": "cliente@example.com",
  "origem": "facebook",
  "avisoPrivacidadeVersao": "v1",
  "consentimentoContato": true
}
```

Resposta: HTTP 201, com `id`, `protocolo`, `status=NOVO` e `criadoEm`.

**Atenção:** o campo `consentimentoContato` registra uma escolha informada do usuário; o frontend deverá apresentar a finalidade e permitir opção negativa. Não confundir com a base legal para todos os tratamentos. A publicação exige aviso de privacidade revisado, HTTPS, proteção contra abuso e política de retenção.

Não há endpoint público de listagem ou consulta de dados pessoais. Esses recursos dependem de autenticação e autorização no painel administrativo.
