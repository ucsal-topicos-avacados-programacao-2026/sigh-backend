CREATE TABLE instituicao (
    id          UUID PRIMARY KEY,
    nome        VARCHAR(150) NOT NULL,
    tipo        VARCHAR(30)  NOT NULL,
    credenciada BOOLEAN      NOT NULL
);

CREATE TABLE usuario (
    id             UUID PRIMARY KEY,
    login          VARCHAR(100) NOT NULL UNIQUE,
    nome           VARCHAR(150) NOT NULL,
    senha_hash     VARCHAR(255) NOT NULL,
    mfa_habilitado BOOLEAN      NOT NULL,
    mfa_segredo    VARCHAR(255)
);

CREATE TABLE usuario_perfil (
    usuario_id UUID        NOT NULL REFERENCES usuario (id),
    perfil     VARCHAR(30) NOT NULL,
    PRIMARY KEY (usuario_id, perfil)
);

CREATE TABLE vinculo_institucional (
    id                    UUID PRIMARY KEY,
    usuario_id            UUID      NOT NULL REFERENCES usuario (id),
    instituicao_id        UUID      NOT NULL REFERENCES instituicao (id),
    registro_profissional VARCHAR(30),
    inicio_em             TIMESTAMP NOT NULL,
    fim_em                TIMESTAMP
);

CREATE TABLE vinculo_perfil (
    vinculo_institucional_id UUID        NOT NULL REFERENCES vinculo_institucional (id),
    perfil                   VARCHAR(30) NOT NULL,
    PRIMARY KEY (vinculo_institucional_id, perfil)
);

CREATE TABLE paciente (
    id              UUID PRIMARY KEY,
    cpf             VARCHAR(11)  NOT NULL UNIQUE,
    nome            VARCHAR(150) NOT NULL,
    data_nascimento DATE         NOT NULL,
    conta_id        UUID UNIQUE REFERENCES usuario (id)
);

CREATE TABLE prontuario (
    id          UUID PRIMARY KEY,
    paciente_id UUID      NOT NULL UNIQUE REFERENCES paciente (id),
    criado_em   TIMESTAMP NOT NULL
);

CREATE TABLE representacao_legal (
    id               UUID PRIMARY KEY,
    paciente_id      UUID         NOT NULL REFERENCES paciente (id),
    representante_id UUID         NOT NULL REFERENCES usuario (id),
    inicio_em        TIMESTAMP    NOT NULL,
    fim_em           TIMESTAMP,
    documento        VARCHAR(100) NOT NULL
);

CREATE TABLE atendimento (
    id                    UUID PRIMARY KEY,
    prontuario_id         UUID        NOT NULL REFERENCES prontuario (id),
    instituicao_id        UUID        NOT NULL REFERENCES instituicao (id),
    chegada_em            TIMESTAMP   NOT NULL,
    inicio_clinico_em     TIMESTAMP,
    motivo                TEXT        NOT NULL,
    status                VARCHAR(30) NOT NULL,
    medico_responsavel_id UUID REFERENCES usuario (id)
);

CREATE TABLE registro_clinico (
    id                   UUID PRIMARY KEY,
    prontuario_id        UUID        NOT NULL REFERENCES prontuario (id),
    atendimento_id       UUID REFERENCES atendimento (id),
    tipo                 VARCHAR(30) NOT NULL,
    criado_em            TIMESTAMP   NOT NULL,
    conteudo             TEXT        NOT NULL,
    categoria_informacao VARCHAR(50) NOT NULL,
    codigo_cid10         VARCHAR(10),
    autor_id             UUID        NOT NULL REFERENCES usuario (id),
    origem_id            UUID        NOT NULL REFERENCES instituicao (id)
);

CREATE TABLE consentimento (
    id               UUID PRIMARY KEY,
    paciente_id      UUID        NOT NULL REFERENCES paciente (id),
    versao           INTEGER     NOT NULL,
    tipo             VARCHAR(30) NOT NULL,
    decisao          VARCHAR(30) NOT NULL,
    vigente_desde    TIMESTAMP   NOT NULL,
    categoria_alvo   VARCHAR(50),
    registro_alvo_id UUID REFERENCES registro_clinico (id),
    destinataria_id  UUID REFERENCES instituicao (id),
    outorgante_id    UUID REFERENCES usuario (id),
    anterior_id      UUID REFERENCES consentimento (id)
);

CREATE TABLE restricao_acesso (
    id               UUID PRIMARY KEY,
    prontuario_id    UUID      NOT NULL REFERENCES prontuario (id),
    inicio_em        TIMESTAMP NOT NULL,
    fim_em           TIMESTAMP,
    categoria_alvo   VARCHAR(50),
    registro_alvo_id UUID REFERENCES registro_clinico (id),
    definida_por_id  UUID      NOT NULL REFERENCES usuario (id)
);

CREATE TABLE triagem (
    id                      UUID PRIMARY KEY,
    atendimento_id          UUID      NOT NULL REFERENCES atendimento (id),
    sintomas                TEXT      NOT NULL,
    pressao_arterial        VARCHAR(10),
    frequencia_cardiaca     INTEGER,
    frequencia_respiratoria INTEGER,
    temperatura             NUMERIC(4, 1),
    saturacao_oxigenio      NUMERIC(4, 1),
    cor_sugerida            VARCHAR(30),
    cor_confirmada          VARCHAR(30),
    confirmada_em           TIMESTAMP,
    registrador_id          UUID      NOT NULL REFERENCES usuario (id),
    confirmador_id          UUID REFERENCES usuario (id)
);

CREATE TABLE triagem_fontes_historico (
    triagem_id          UUID NOT NULL REFERENCES triagem (id),
    fontes_historico_id UUID NOT NULL REFERENCES registro_clinico (id),
    PRIMARY KEY (triagem_id, fontes_historico_id)
);

CREATE TABLE resumo_historico_ia (
    id             UUID PRIMARY KEY,
    atendimento_id UUID        NOT NULL REFERENCES atendimento (id),
    solicitante_id UUID        NOT NULL REFERENCES usuario (id),
    validador_id   UUID REFERENCES usuario (id),
    status         VARCHAR(30) NOT NULL,
    texto          TEXT,
    gerado_em      TIMESTAMP,
    modelo_versao  VARCHAR(100)
);

CREATE TABLE resumo_historico_ia_fontes (
    resumo_historico_ia_id UUID NOT NULL REFERENCES resumo_historico_ia (id),
    fontes_id              UUID NOT NULL REFERENCES registro_clinico (id),
    PRIMARY KEY (resumo_historico_ia_id, fontes_id)
);

CREATE TABLE leito (
    id             UUID PRIMARY KEY,
    instituicao_id UUID        NOT NULL REFERENCES instituicao (id),
    codigo         VARCHAR(20) NOT NULL,
    setor          VARCHAR(50) NOT NULL,
    categoria      VARCHAR(30) NOT NULL,
    status         VARCHAR(30) NOT NULL,
    UNIQUE (instituicao_id, codigo)
);

CREATE TABLE internacao (
    id                    UUID PRIMARY KEY,
    prontuario_id         UUID      NOT NULL REFERENCES prontuario (id),
    atendimento_origem_id UUID      NOT NULL REFERENCES atendimento (id),
    admitida_em           TIMESTAMP NOT NULL,
    alta_em               TIMESTAMP,
    resumo_alta_id        UUID REFERENCES registro_clinico (id)
);

CREATE TABLE ocupacao_leito (
    id            UUID PRIMARY KEY,
    leito_id      UUID        NOT NULL REFERENCES leito (id),
    internacao_id UUID        NOT NULL REFERENCES internacao (id),
    inicio_em     TIMESTAMP   NOT NULL,
    fim_em        TIMESTAMP,
    motivo_inicio VARCHAR(30) NOT NULL
);

CREATE TABLE registro_auditoria (
    id                   UUID PRIMARY KEY,
    prontuario_id        UUID         NOT NULL REFERENCES prontuario (id),
    ator_id              UUID         NOT NULL REFERENCES usuario (id),
    perfil               VARCHAR(30)  NOT NULL,
    instituicao_id       UUID REFERENCES instituicao (id),
    ocorrido_em          TIMESTAMP    NOT NULL,
    finalidade_declarada VARCHAR(255) NOT NULL,
    emergencia           BOOLEAN      NOT NULL,
    justificativa        TEXT
);

CREATE TABLE registro_auditoria_registros_acessados (
    registro_auditoria_id  UUID NOT NULL REFERENCES registro_auditoria (id),
    registros_acessados_id UUID NOT NULL REFERENCES registro_clinico (id),
    PRIMARY KEY (registro_auditoria_id, registros_acessados_id)
);

CREATE TABLE analise_auditoria (
    id                    UUID PRIMARY KEY,
    registro_auditoria_id UUID        NOT NULL REFERENCES registro_auditoria (id),
    registrada_em         TIMESTAMP   NOT NULL,
    tipo                  VARCHAR(30) NOT NULL,
    conclusao             VARCHAR(30) NOT NULL,
    descricao             TEXT        NOT NULL,
    auditor_id            UUID REFERENCES usuario (id)
);

CREATE INDEX idx_atendimento_prontuario ON atendimento (prontuario_id);
CREATE INDEX idx_registro_clinico_prontuario ON registro_clinico (prontuario_id);
CREATE INDEX idx_consentimento_paciente ON consentimento (paciente_id);
CREATE INDEX idx_restricao_acesso_prontuario ON restricao_acesso (prontuario_id);
CREATE INDEX idx_triagem_atendimento ON triagem (atendimento_id);
CREATE INDEX idx_leito_instituicao_status ON leito (instituicao_id, status);
CREATE INDEX idx_registro_auditoria_prontuario ON registro_auditoria (prontuario_id);
CREATE INDEX idx_registro_auditoria_ocorrido_em ON registro_auditoria (ocorrido_em);
