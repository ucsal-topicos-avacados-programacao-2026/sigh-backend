-- BE-22 (RNF-004): controle da manutenção das colunas cifradas (ManutencaoDaCifragem).
--
-- controle_cifragem: uma linha só, gravada quando os dados anteriores (em claro ou no formato sigh:v1,
-- sem vínculo à linha) foram cifrados no formato atual. Depois dela, valor fora do formato atual não é
-- mais cifrado ao subir: é tratado como gravado fora da aplicação, registrado no log e recusado na
-- leitura.
CREATE TABLE controle_cifragem (
    id SMALLINT PRIMARY KEY CHECK (id = 1),
    formato TEXT NOT NULL,
    concluida_em TIMESTAMP NOT NULL
);

-- Tabelas regravadas que ainda precisam de VACUUM FULL (a versão antiga da linha continua nos
-- arquivos do banco até ele rodar). Entram na mesma transação que as regrava; saem depois do VACUUM.
CREATE TABLE cifragem_vacuum_pendente (
    tabela TEXT PRIMARY KEY
);

-- A justificativa da auditoria não pode ser regravada (RN-006), então a que já existia antes desta
-- versão (em claro ou no formato sigh:v1, sem vínculo à linha) continua sendo lida. Só essas linhas,
-- listadas aqui no momento da migração: uma auditoria inserida depois, fora da aplicação, com
-- justificativa sem a cifra atual, é recusada e detectada. A lista não aceita nenhuma alteração.
CREATE TABLE auditoria_justificativa_legada (
    registro_id UUID PRIMARY KEY REFERENCES registro_auditoria (id)
);

INSERT INTO auditoria_justificativa_legada (registro_id)
SELECT id FROM registro_auditoria WHERE justificativa IS NOT NULL;

CREATE FUNCTION impedir_alteracao_da_lista_de_legado() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'A lista de justificativas legadas da auditoria não pode ser alterada.';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER auditoria_justificativa_legada_imutavel
    BEFORE INSERT OR UPDATE OR DELETE ON auditoria_justificativa_legada
    FOR EACH ROW EXECUTE FUNCTION impedir_alteracao_da_lista_de_legado();

CREATE TRIGGER auditoria_justificativa_legada_sem_truncate
    BEFORE TRUNCATE ON auditoria_justificativa_legada
    FOR EACH STATEMENT EXECUTE FUNCTION impedir_alteracao_da_lista_de_legado();
