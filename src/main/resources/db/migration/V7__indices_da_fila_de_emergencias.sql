-- BE-18: fila do auditor com os acessos de emergência sem análise conclusiva (GET /auditoria/emergencias/pendentes).
-- Apoiam o "not exists" sobre as análises e a ordenação dos acessos de emergência.
CREATE INDEX idx_analise_auditoria_registro ON analise_auditoria (registro_auditoria_id);
CREATE INDEX idx_registro_auditoria_emergencias ON registro_auditoria (ocorrido_em, id) WHERE emergencia;
