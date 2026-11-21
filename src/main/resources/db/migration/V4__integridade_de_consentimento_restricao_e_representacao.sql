-- BE-17: as regras de consentimento, restrição e representação legal passam a valer também no banco,
-- inclusive contra requisições simultâneas e gravações que não passam pela aplicação.

-- Bancos que já tinham dados: encerrar uma representação que ainda não tinha começado gravava o fim
-- antes do início. Ela nunca chegou a valer; o fim passa a ser o próprio início.
UPDATE representacao_legal SET fim_em = inicio_em WHERE fim_em < inicio_em;

-- Duplicatas em aberto não são resolvidas automaticamente: escolher qual manter é decisão de negócio.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM restricao_acesso WHERE fim_em IS NULL AND registro_alvo_id IS NOT NULL
               GROUP BY prontuario_id, registro_alvo_id HAVING count(*) > 1)
       OR EXISTS (SELECT 1 FROM representacao_legal WHERE fim_em IS NULL
                  GROUP BY paciente_id, representante_id HAVING count(*) > 1) THEN
        RAISE EXCEPTION 'Há restrições ou representações duplicadas em aberto; encerre as repetidas antes da V4.';
    END IF;
END;
$$;

-- Consentimento amplo não tem alvo. A autorização adicional (RF-009) tem instituição e exatamente um
-- alvo; a concessão não aponta para versão anterior e a revogação aponta para a autorização revogada.
ALTER TABLE consentimento ADD CONSTRAINT ck_consentimento_formato CHECK (
    (tipo = 'AMPLO' AND destinataria_id IS NULL AND categoria_alvo IS NULL AND registro_alvo_id IS NULL)
    OR (tipo = 'ADICIONAL' AND destinataria_id IS NOT NULL
        AND (categoria_alvo IS NULL) <> (registro_alvo_id IS NULL)
        AND (categoria_alvo IS NULL OR btrim(categoria_alvo) <> '')
        AND ((decisao = 'AUTORIZADO' AND anterior_id IS NULL) OR (decisao = 'REVOGADO' AND anterior_id IS NOT NULL)))
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
ALTER TABLE restricao_acesso ADD COLUMN encerrada_por_id UUID REFERENCES usuario (id);
ALTER TABLE restricao_acesso ADD CONSTRAINT ck_restricao_acesso_alvo CHECK (
    (categoria_alvo IS NULL) <> (registro_alvo_id IS NULL)
    AND (categoria_alvo IS NULL OR btrim(categoria_alvo) <> '')
);

-- Uma restrição em aberto por alvo. A categoria é comparada como em RegistroClinicoRepository:
-- espaços em branco colapsados, sem espaços nas pontas, sem acentos e sem maiúsculas.
CREATE UNIQUE INDEX uk_restricao_registro_em_aberto ON restricao_acesso (prontuario_id, registro_alvo_id)
    WHERE fim_em IS NULL AND registro_alvo_id IS NOT NULL;
CREATE UNIQUE INDEX uk_restricao_categoria_em_aberto ON restricao_acesso (prontuario_id,
    lower(translate(btrim(regexp_replace(categoria_alvo, '\s+', ' ', 'g')),
        'ÁÀÂÃÄÉÈÊËÍÌÎÏÓÒÔÕÖÚÙÛÜÇáàâãäéèêëíìîïóòôõöúùûüç',
        'AAAAAEEEEIIIIOOOOOUUUUCaaaaaeeeeiiiiooooouuuuc')))
    WHERE fim_em IS NULL AND categoria_alvo IS NOT NULL;

-- Restrição não é apagada nem reescrita: só pode ser encerrada uma vez, registrando quem encerrou.
CREATE FUNCTION permitir_so_encerramento_da_restricao() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Restrições de acesso não podem ser excluídas.';
    END IF;
    IF OLD.fim_em IS NOT NULL OR NEW.fim_em IS NULL OR NEW.encerrada_por_id IS NULL
       OR NEW.prontuario_id IS DISTINCT FROM OLD.prontuario_id
       OR NEW.inicio_em IS DISTINCT FROM OLD.inicio_em
       OR NEW.categoria_alvo IS DISTINCT FROM OLD.categoria_alvo
       OR NEW.registro_alvo_id IS DISTINCT FROM OLD.registro_alvo_id
       OR NEW.definida_por_id IS DISTINCT FROM OLD.definida_por_id THEN
        RAISE EXCEPTION 'Restrição de acesso só pode ser encerrada, uma vez.';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER restricao_acesso_so_encerramento
    BEFORE UPDATE OR DELETE ON restricao_acesso
    FOR EACH ROW EXECUTE FUNCTION permitir_so_encerramento_da_restricao();

-- Uma representação em aberto por representante e paciente; o fim não vem antes do início
-- (encerrar uma representação que ainda não começou a fecha no próprio início).
CREATE UNIQUE INDEX uk_representacao_em_aberto ON representacao_legal (paciente_id, representante_id)
    WHERE fim_em IS NULL;
ALTER TABLE representacao_legal ADD CONSTRAINT ck_representacao_periodo
    CHECK (fim_em IS NULL OR fim_em >= inicio_em);
CREATE INDEX idx_representacao_legal_representante ON representacao_legal (representante_id);
CREATE INDEX idx_representacao_legal_paciente ON representacao_legal (paciente_id, representante_id);

-- Representação não é apagada; só o fim pode ser antecipado.
CREATE FUNCTION permitir_so_antecipar_fim_da_representacao() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Representações legais não podem ser excluídas.';
    END IF;
    IF NEW.fim_em IS NULL OR (OLD.fim_em IS NOT NULL AND NEW.fim_em > OLD.fim_em)
       OR NEW.paciente_id IS DISTINCT FROM OLD.paciente_id
       OR NEW.representante_id IS DISTINCT FROM OLD.representante_id
       OR NEW.inicio_em IS DISTINCT FROM OLD.inicio_em
       OR NEW.documento IS DISTINCT FROM OLD.documento THEN
        RAISE EXCEPTION 'Representação legal só pode ter o fim antecipado.';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER representacao_legal_so_antecipar_fim
    BEFORE UPDATE OR DELETE ON representacao_legal
    FOR EACH ROW EXECUTE FUNCTION permitir_so_antecipar_fim_da_representacao();
