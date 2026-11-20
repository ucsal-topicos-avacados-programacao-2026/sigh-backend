-- BE-17: as regras de consentimento, restrição e representação legal passam a valer também no banco,
-- inclusive contra requisições simultâneas que o código sozinho não barra.

-- Consentimento amplo não tem alvo; a autorização adicional (RF-009) tem instituição e exatamente
-- um alvo (categoria ou registro) e só é concedida ou revogada.
ALTER TABLE consentimento ADD CONSTRAINT ck_consentimento_formato CHECK (
    (tipo = 'AMPLO' AND destinataria_id IS NULL AND categoria_alvo IS NULL AND registro_alvo_id IS NULL)
    OR (tipo = 'ADICIONAL' AND destinataria_id IS NOT NULL
        AND (categoria_alvo IS NULL) <> (registro_alvo_id IS NULL)
        AND decisao IN ('AUTORIZADO', 'REVOGADO'))
);

-- Cada versão substitui no máximo uma vez a anterior: duas revogações da mesma autorização não passam.
-- O índice também atende às consultas "ainda não revogada" (not exists ... anterior = c).
CREATE UNIQUE INDEX uk_consentimento_anterior ON consentimento (anterior_id) WHERE anterior_id IS NOT NULL;

-- Consentimento é histórico: nenhuma versão é alterada ou apagada (RN-007, como a auditoria na V2).
CREATE FUNCTION impedir_alteracao_do_consentimento() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'Versões de consentimento não podem ser alteradas nem excluídas.';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER consentimento_imutavel
    BEFORE UPDATE OR DELETE ON consentimento
    FOR EACH ROW EXECUTE FUNCTION impedir_alteracao_do_consentimento();

CREATE TRIGGER consentimento_sem_truncate
    BEFORE TRUNCATE ON consentimento
    FOR EACH STATEMENT EXECUTE FUNCTION impedir_alteracao_do_consentimento();

-- Restrição tem exatamente um alvo e guarda quem a encerrou.
ALTER TABLE restricao_acesso ADD CONSTRAINT ck_restricao_acesso_alvo
    CHECK ((categoria_alvo IS NULL) <> (registro_alvo_id IS NULL));
ALTER TABLE restricao_acesso ADD COLUMN encerrada_por_id UUID REFERENCES usuario (id);

-- Uma restrição em aberto por alvo. A categoria é comparada como em RegistroClinicoRepository:
-- sem maiúsculas, acentos nem espaços repetidos.
CREATE UNIQUE INDEX uk_restricao_registro_em_aberto ON restricao_acesso (prontuario_id, registro_alvo_id)
    WHERE fim_em IS NULL AND registro_alvo_id IS NOT NULL;
CREATE UNIQUE INDEX uk_restricao_categoria_em_aberto ON restricao_acesso (prontuario_id,
    lower(translate(regexp_replace(btrim(categoria_alvo), '\s+', ' ', 'g'),
        'ÁÀÂÃÄÉÈÊËÍÌÎÏÓÒÔÕÖÚÙÛÜÇáàâãäéèêëíìîïóòôõöúùûüç',
        'AAAAAEEEEIIIIOOOOOUUUUCaaaaaeeeeiiiiooooouuuuc')))
    WHERE fim_em IS NULL AND categoria_alvo IS NOT NULL;

-- Uma representação em aberto por representante e paciente; o fim não vem antes do início
-- (encerrar uma representação que ainda não começou a fecha no próprio início).
CREATE UNIQUE INDEX uk_representacao_em_aberto ON representacao_legal (paciente_id, representante_id)
    WHERE fim_em IS NULL;
ALTER TABLE representacao_legal ADD CONSTRAINT ck_representacao_periodo
    CHECK (fim_em IS NULL OR fim_em >= inicio_em);
