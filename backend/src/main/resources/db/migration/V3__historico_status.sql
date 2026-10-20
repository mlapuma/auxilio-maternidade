CREATE TABLE historico_status (
 id UUID PRIMARY KEY,
 lead_id UUID NOT NULL REFERENCES leads(id),
 status_anterior VARCHAR(32) NOT NULL,
 status_novo VARCHAR(32) NOT NULL,
 alterado_em TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_historico_status_lead ON historico_status(lead_id, alterado_em DESC);
