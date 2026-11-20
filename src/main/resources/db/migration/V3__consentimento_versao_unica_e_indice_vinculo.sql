-- Cada versão de consentimento é única por paciente: duas decisões simultâneas
-- não podem gerar a mesma versão (a segunda falha e precisa ser reenviada).
ALTER TABLE consentimento
    ADD CONSTRAINT uk_consentimento_paciente_versao UNIQUE (paciente_id, versao);

-- Verificação de vínculo ativo do usuário com a instituição (feita a cada busca e admissão).
CREATE INDEX idx_vinculo_institucional_usuario_instituicao
    ON vinculo_institucional (usuario_id, instituicao_id);
