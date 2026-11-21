-- Dados fictícios do teste de carga (TST-04): uma unidade de médio porte (Hospital A) que consulta
-- prontuários com registros de outra instituição da rede (Hospital B).
--
-- Rode com carga/carregar-dados.sh, num banco sigh_carga já migrado pela aplicação e com ela parada.
-- O script define as variáveis :senha e :segredo_mfa pela entrada do psql, nunca como argumento.
-- O texto clínico entra em claro e a linha de controle_cifragem é apagada no fim: ao subir de novo,
-- a aplicação cifra tudo com a chave atual (veja "Manutenção ao subir" no README).
--
-- Volume: 200 profissionais (150 médicos, 40 enfermeiros, 10 administrativos), 2.000 pacientes,
-- 4.000 atendimentos, 40.000 registros clínicos (metade de cada instituição) e 300 pacientes na fila.

\set ON_ERROR_STOP on
BEGIN;
SET LOCAL TIME ZONE 'America/Bahia';
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE FUNCTION pg_temp.id(prefixo text, n int) RETURNS uuid
	LANGUAGE sql IMMUTABLE AS $$ SELECT md5('carga:' || prefixo || ':' || n)::uuid $$;

-- CPF fictício com dígitos verificadores válidos, a partir de 9 dígitos.
CREATE FUNCTION pg_temp.cpf(n int) RETURNS text LANGUAGE plpgsql IMMUTABLE AS $$
DECLARE
	base text := lpad((100000000 + n * 37)::text, 9, '0');
	soma int := 0; d1 int; d2 int; i int;
BEGIN
	FOR i IN 1..9 LOOP soma := soma + substr(base, i, 1)::int * (11 - i); END LOOP;
	d1 := CASE WHEN soma % 11 < 2 THEN 0 ELSE 11 - soma % 11 END;
	soma := 0;
	FOR i IN 1..9 LOOP soma := soma + substr(base, i, 1)::int * (12 - i); END LOOP;
	soma := soma + d1 * 2;
	d2 := CASE WHEN soma % 11 < 2 THEN 0 ELSE 11 - soma % 11 END;
	RETURN base || d1 || d2;
END $$;

INSERT INTO instituicao (id, nome, tipo, credenciada) VALUES
	(pg_temp.id('instituicao', 1), 'Hospital A (carga)', 'PUBLICA', TRUE),
	(pg_temp.id('instituicao', 2), 'Hospital B (carga)', 'PRIVADA', TRUE);

-- Profissionais: 1-150 médicos, 151-190 enfermeiros, 191-200 administrativos. Mesma senha e mesmo
-- segredo do segundo fator para todos, só para o teste.
CREATE TEMP TABLE profissional AS
SELECT n,
	CASE WHEN n <= 150 THEN 'MEDICO' WHEN n <= 190 THEN 'ENFERMEIRO' ELSE 'ADMINISTRATIVO' END AS perfil
FROM generate_series(1, 200) n;

INSERT INTO usuario (id, login, nome, senha_hash, mfa_habilitado, mfa_segredo)
SELECT pg_temp.id('usuario', n), lower(perfil) || '.carga.' || lpad(n::text, 3, '0'),
	'Profissional ' || n || ' (carga)', hash.valor, perfil <> 'ADMINISTRATIVO',
	CASE WHEN perfil <> 'ADMINISTRATIVO' THEN :'segredo_mfa' END
FROM profissional, (SELECT crypt(:'senha', gen_salt('bf', 10)) AS valor) hash;

INSERT INTO usuario_perfil (usuario_id, perfil) SELECT pg_temp.id('usuario', n), perfil FROM profissional;

INSERT INTO vinculo_institucional (id, usuario_id, instituicao_id, registro_profissional, inicio_em, fim_em)
SELECT pg_temp.id('vinculo', n), pg_temp.id('usuario', n), pg_temp.id('instituicao', 1),
	CASE perfil WHEN 'MEDICO' THEN 'CRM ' WHEN 'ENFERMEIRO' THEN 'COREN ' END || lpad(n::text, 6, '0'),
	localtimestamp - INTERVAL '2 years', NULL
FROM profissional;

INSERT INTO vinculo_perfil (vinculo_institucional_id, perfil) SELECT pg_temp.id('vinculo', n), perfil FROM profissional;

-- Os registros do Hospital B têm como autor o médico 1, com vínculo também lá.
INSERT INTO vinculo_institucional (id, usuario_id, instituicao_id, registro_profissional, inicio_em, fim_em)
VALUES (pg_temp.id('vinculo-b', 1), pg_temp.id('usuario', 1), pg_temp.id('instituicao', 2), 'CRM 000001',
	localtimestamp - INTERVAL '2 years', NULL);
INSERT INTO vinculo_perfil (vinculo_institucional_id, perfil) VALUES (pg_temp.id('vinculo-b', 1), 'MEDICO');

INSERT INTO paciente (id, cpf, nome, data_nascimento, conta_id)
SELECT pg_temp.id('paciente', n), pg_temp.cpf(n), 'Paciente ' || n || ' (carga)',
	DATE '1950-01-01' + (n * 7) % 25000, NULL
FROM generate_series(1, 2000) n;

INSERT INTO prontuario (id, paciente_id, criado_em)
SELECT pg_temp.id('prontuario', n), pg_temp.id('paciente', n), localtimestamp - INTERVAL '3 years'
FROM generate_series(1, 2000) n;

INSERT INTO consentimento (id, paciente_id, versao, tipo, decisao, vigente_desde, categoria_alvo, registro_alvo_id,
	destinataria_id, outorgante_id, anterior_id)
SELECT pg_temp.id('consentimento', n), pg_temp.id('paciente', n), 1, 'AMPLO', 'AUTORIZADO',
	localtimestamp - INTERVAL '3 years', NULL, NULL, NULL, NULL, NULL
FROM generate_series(1, 2000) n;

-- Cada paciente: um atendimento encerrado no Hospital B e um atual no Hospital A. Os 300 últimos
-- estão na fila (EM_TRIAGEM, com triagem confirmada); os demais, em atendimento com um médico.
INSERT INTO atendimento (id, prontuario_id, instituicao_id, chegada_em, inicio_clinico_em, motivo, status,
	medico_responsavel_id)
SELECT pg_temp.id('atendimento-b', n), pg_temp.id('prontuario', n), pg_temp.id('instituicao', 2),
	localtimestamp - INTERVAL '90 days', localtimestamp - INTERVAL '90 days' + INTERVAL '15 minutes',
	'Consulta de acompanhamento ' || n, 'ENCERRADO', pg_temp.id('usuario', 1)
FROM generate_series(1, 2000) n
UNION ALL
SELECT pg_temp.id('atendimento-a', n), pg_temp.id('prontuario', n), pg_temp.id('instituicao', 1),
	localtimestamp - make_interval(mins => 5 + n % 120),
	CASE WHEN n <= 1700 THEN localtimestamp - make_interval(mins => n % 120) END,
	'Dor torácica e falta de ar há ' || (1 + n % 5) || ' dias',
	CASE WHEN n <= 1700 THEN 'EM_ATENDIMENTO' ELSE 'EM_TRIAGEM' END,
	CASE WHEN n <= 1700 THEN pg_temp.id('usuario', 1 + n % 150) END
FROM generate_series(1, 2000) n;

INSERT INTO triagem (id, atendimento_id, sintomas, pressao_arterial, frequencia_cardiaca, frequencia_respiratoria,
	temperatura, saturacao_oxigenio, cor_sugerida, cor_confirmada, confirmada_em, registrador_id, confirmador_id)
SELECT pg_temp.id('triagem', n), pg_temp.id('atendimento-a', n),
	'Dor torácica em aperto, sudorese e falta de ar aos pequenos esforços', '130/85', '96', '20', '37.2', '95.0',
	(ARRAY['VERMELHO', 'LARANJA', 'AMARELO', 'VERDE', 'AZUL'])[1 + n % 5],
	(ARRAY['VERMELHO', 'LARANJA', 'AMARELO', 'VERDE', 'AZUL'])[1 + n % 5],
	localtimestamp - make_interval(mins => n % 120),
	pg_temp.id('usuario', 151 + n % 40), pg_temp.id('usuario', 151 + (n + 1) % 40)
FROM generate_series(1, 2000) n;

-- 20 registros por paciente: 10 do atendimento no Hospital B e 10 do atendimento atual no Hospital A.
INSERT INTO registro_clinico (id, prontuario_id, atendimento_id, tipo, criado_em, conteudo, categoria_informacao,
	codigo_cid10, autor_id, origem_id)
SELECT pg_temp.id('registro', n * 100 + r), pg_temp.id('prontuario', n),
	CASE WHEN r <= 10 THEN pg_temp.id('atendimento-b', n) ELSE pg_temp.id('atendimento-a', n) END,
	(ARRAY['DIAGNOSTICO', 'PRESCRICAO', 'EXAME', 'EVOLUCAO', 'ALERGIA'])[1 + r % 5],
	CASE WHEN r <= 10 THEN localtimestamp - INTERVAL '90 days' + make_interval(mins => r)
		ELSE localtimestamp - make_interval(mins => 30 - r) END,
	'Registro ' || r || ' do paciente ' || n || ': paciente refere dor torácica em aperto, com irradiação '
		|| 'para o braço esquerdo, sudorese e náusea. Sinais vitais estáveis. Conduta conforme protocolo.',
	(ARRAY['Clínica geral', 'Cardiologia', 'Medicamentos', 'Exames', 'Alergias'])[1 + r % 5],
	CASE WHEN r % 5 = 0 THEN 'I20.9' END,
	CASE WHEN r <= 10 THEN pg_temp.id('usuario', 1) ELSE pg_temp.id('usuario', 1 + n % 150) END,
	CASE WHEN r <= 10 THEN pg_temp.id('instituicao', 2) ELSE pg_temp.id('instituicao', 1) END
FROM generate_series(1, 2000) n, generate_series(1, 20) r;

-- Ao subir, a aplicação cifra o texto clínico inserido acima.
DELETE FROM controle_cifragem;
COMMIT;
ANALYZE;
