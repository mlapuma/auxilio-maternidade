ALTER TABLE leads ADD COLUMN utm_source VARCHAR(120);
ALTER TABLE leads ADD COLUMN utm_medium VARCHAR(120);
ALTER TABLE leads ADD COLUMN utm_campaign VARCHAR(160);
CREATE TABLE atendimentos (
 id UUID PRIMARY KEY,
 lead_id UUID NOT NULL REFERENCES leads(id),
 codigo_publico VARCHAR(32) NOT NULL UNIQUE,
 tipo_assunto VARCHAR(50) NOT NULL DEFAULT 'SALARIO_MATERNIDADE',
 status VARCHAR(40) NOT NULL DEFAULT 'NOVO',
 responsavel_id VARCHAR(120),
 data_inicio TIMESTAMPTZ NOT NULL,
 data_atualizacao TIMESTAMPTZ NOT NULL,
 CONSTRAINT uq_atendimentos_lead UNIQUE (lead_id)
);
CREATE INDEX idx_atendimentos_status_data ON atendimentos(status, data_inicio DESC);
CREATE INDEX idx_leads_utm_campaign ON leads(utm_campaign);
