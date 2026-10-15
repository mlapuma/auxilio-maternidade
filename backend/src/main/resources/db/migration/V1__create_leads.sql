CREATE TABLE leads (
    id UUID PRIMARY KEY,
    protocolo VARCHAR(32) NOT NULL UNIQUE,
    nome VARCHAR(160) NOT NULL,
    telefone VARCHAR(20) NOT NULL,
    email VARCHAR(254),
    origem VARCHAR(80),
    status VARCHAR(32) NOT NULL,
    aviso_privacidade_versao VARCHAR(20) NOT NULL,
    consentimento_contato BOOLEAN NOT NULL,
    criado_em TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_leads_criado_em ON leads (criado_em);
