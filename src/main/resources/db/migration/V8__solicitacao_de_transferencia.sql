-- BE-21 (RF-006): transferência entre instituições da rede. Pela ERS (Processo 2 - Gestão de Leitos), a
-- origem solicita a transferência e a unidade de destino executa o próprio processo: o administrativo do
-- destino aceita escolhendo um leito livre dele, ou recusa. A origem pode cancelar enquanto estiver pendente.
CREATE TABLE solicitacao_transferencia (
    id                     UUID PRIMARY KEY,
    internacao_id          UUID         NOT NULL REFERENCES internacao (id),
    instituicao_origem_id  UUID         NOT NULL REFERENCES instituicao (id),
    instituicao_destino_id UUID         NOT NULL REFERENCES instituicao (id),
    categoria              VARCHAR(30)  NOT NULL,
    status                 VARCHAR(20)  NOT NULL,
    solicitada_em          TIMESTAMP    NOT NULL,
    solicitante_id         UUID         NOT NULL REFERENCES usuario (id),
    decidida_em            TIMESTAMP,
    decisor_id             UUID REFERENCES usuario (id),
    leito_destino_id       UUID REFERENCES leito (id),
    motivo_recusa          VARCHAR(500),
    CONSTRAINT ck_solicitacao_outra_instituicao CHECK (instituicao_origem_id <> instituicao_destino_id),
    CONSTRAINT ck_solicitacao_status CHECK (status IN ('PENDENTE', 'ACEITA', 'RECUSADA', 'CANCELADA')),
    CONSTRAINT ck_solicitacao_decisao CHECK ((status = 'PENDENTE') = (decidida_em IS NULL AND decisor_id IS NULL)),
    CONSTRAINT ck_solicitacao_aceite CHECK ((status = 'ACEITA') = (leito_destino_id IS NOT NULL)),
    CONSTRAINT ck_solicitacao_recusa CHECK ((status = 'RECUSADA') = (motivo_recusa IS NOT NULL))
);

-- Uma solicitação pendente por internação, inclusive com pedidos simultâneos.
CREATE UNIQUE INDEX uk_solicitacao_pendente_por_internacao
    ON solicitacao_transferencia (internacao_id) WHERE status = 'PENDENTE';
-- Caixa de entrada do destino: pendentes da instituição, da mais antiga para a mais nova.
CREATE INDEX idx_solicitacao_pendente_por_destino
    ON solicitacao_transferencia (instituicao_destino_id, solicitada_em) WHERE status = 'PENDENTE';
