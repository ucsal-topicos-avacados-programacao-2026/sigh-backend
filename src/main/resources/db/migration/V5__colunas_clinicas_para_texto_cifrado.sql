-- BE-09 (RNF-004): os dados clínicos passam a ser gravados cifrados pela aplicação (AES-256-GCM), em
-- texto. As colunas numéricas e curtas viram TEXT; os valores existentes continuam legíveis e são
-- cifrados pela aplicação na inicialização (CifragemDeDadosLegados).
ALTER TABLE registro_clinico ALTER COLUMN codigo_cid10 TYPE TEXT;

ALTER TABLE triagem
    ALTER COLUMN pressao_arterial TYPE TEXT,
    ALTER COLUMN frequencia_cardiaca TYPE TEXT USING frequencia_cardiaca::text,
    ALTER COLUMN frequencia_respiratoria TYPE TEXT USING frequencia_respiratoria::text,
    ALTER COLUMN temperatura TYPE TEXT USING temperatura::text,
    ALTER COLUMN saturacao_oxigenio TYPE TEXT USING saturacao_oxigenio::text;
