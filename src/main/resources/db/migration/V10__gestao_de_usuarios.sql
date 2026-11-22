ALTER TABLE usuario ADD COLUMN ativo BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE usuario ADD COLUMN senha_temporaria BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE acao_administrativa (
    id          UUID PRIMARY KEY,
    ator_id     UUID         NOT NULL REFERENCES usuario (id),
    alvo_id     UUID         NOT NULL REFERENCES usuario (id),
    acao        VARCHAR(40)  NOT NULL,
    detalhe     VARCHAR(255),
    ocorrido_em TIMESTAMP    NOT NULL
);

CREATE INDEX idx_acao_administrativa_ocorrido_em ON acao_administrativa (ocorrido_em DESC);

CREATE TRIGGER acao_administrativa_imutavel
    BEFORE UPDATE OR DELETE ON acao_administrativa
    FOR EACH ROW EXECUTE FUNCTION impedir_alteracao_da_auditoria();

CREATE TRIGGER acao_administrativa_sem_truncate
    BEFORE TRUNCATE ON acao_administrativa
    FOR EACH STATEMENT EXECUTE FUNCTION impedir_alteracao_da_auditoria();
