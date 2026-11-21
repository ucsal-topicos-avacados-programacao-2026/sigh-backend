-- Pré-classificação na admissão: a triagem nasce com os sintomas informados na recepção, sem sinais vitais, e a IA
-- sugere a cor em segundo plano. A sugestão é "preliminar" até o enfermeiro registrar os sinais vitais.
ALTER TABLE triagem ADD COLUMN sugestao_preliminar BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE triagem ADD COLUMN situacao_sugestao VARCHAR(20);
