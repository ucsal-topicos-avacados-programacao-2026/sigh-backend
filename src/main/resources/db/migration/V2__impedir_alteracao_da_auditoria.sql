CREATE FUNCTION impedir_alteracao_da_auditoria() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'Registros de auditoria não podem ser alterados nem excluídos.';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER registro_auditoria_imutavel
    BEFORE UPDATE OR DELETE ON registro_auditoria
    FOR EACH ROW EXECUTE FUNCTION impedir_alteracao_da_auditoria();

CREATE TRIGGER registro_auditoria_sem_truncate
    BEFORE TRUNCATE ON registro_auditoria
    FOR EACH STATEMENT EXECUTE FUNCTION impedir_alteracao_da_auditoria();

CREATE TRIGGER registros_acessados_imutavel
    BEFORE UPDATE OR DELETE ON registro_auditoria_registros_acessados
    FOR EACH ROW EXECUTE FUNCTION impedir_alteracao_da_auditoria();

CREATE TRIGGER registros_acessados_sem_truncate
    BEFORE TRUNCATE ON registro_auditoria_registros_acessados
    FOR EACH STATEMENT EXECUTE FUNCTION impedir_alteracao_da_auditoria();
