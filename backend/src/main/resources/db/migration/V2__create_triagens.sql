CREATE TABLE triagens (
 id UUID PRIMARY KEY,
 lead_id UUID NOT NULL REFERENCES leads(id),
 houve_nascimento_ou_adocao BOOLEAN NOT NULL,
 possui_vinculo_ou_contribuicao BOOLEAN NOT NULL,
 deseja_atendimento BOOLEAN NOT NULL,
 classificacao VARCHAR(40) NOT NULL,
 criado_em TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_triagens_lead_id ON triagens(lead_id);
