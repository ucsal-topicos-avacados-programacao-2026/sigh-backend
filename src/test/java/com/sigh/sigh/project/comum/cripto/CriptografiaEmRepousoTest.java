package com.sigh.sigh.project.comum.cripto;

import com.jayway.jsonpath.JsonPath;
import com.sigh.sigh.project.ia.service.ResumoHistoricoService;
import com.sigh.sigh.project.instituicao.entity.Instituicao;
import com.sigh.sigh.project.support.Cenario;
import com.sigh.sigh.project.support.TesteIntegracao;
import com.sigh.sigh.project.usuario.entity.Perfil;
import com.sigh.sigh.project.usuario.repository.UsuarioRepository;
import com.sigh.sigh.project.usuario.service.SegundoFatorService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.health.contributor.Status;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.postgresql.PostgreSQLContainer;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.sql.DataSource;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Critérios de aceite do BE-09 e do BE-22 (RNF-004): o dado clínico não aparece em texto claro no
 * banco (e, por isso, nem nas cópias); a aplicação continua lendo o texto claro; valor copiado de outra
 * linha não é decifrado; valor em claro gravado direto no banco é detectado e recusado.
 */
@ExtendWith(OutputCaptureExtension.class)
class CriptografiaEmRepousoTest extends TesteIntegracao {

	private static final String TRIAGEM = """
			{"atendimentoId":"%s","sintomas":"Dor torácica irradiada","sinaisVitais":{"pressaoArterial":"150/95",
			 "frequenciaCardiaca":112,"frequenciaRespiratoria":24,"temperatura":37.8,"saturacaoOxigenio":93.5}}
			""";
	/** Chave dos testes (src/test/resources/config) e a anterior, de id 0. */
	private static final byte[] CHAVE_ATUAL = Base64.getDecoder().decode("dGVzdGUtc29tZW50ZS1jaGF2ZS0zMi1ieXRlcy0hISE=");
	private static final String CHAVE_ANTERIOR = "Y2hhdmUtYW50ZXJpb3ItZmljdGljaWEtMzJieXRlcyE=";

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	ManutencaoDaCifragem manutencao;

	@Autowired
	ResumoHistoricoService resumoHistoricoService;

	@Autowired
	PostgreSQLContainer banco;

	@Autowired
	SaudeDaCifragem saudeDaCifragem;

	@Autowired
	SegundoFatorService segundoFatorService;

	@Autowired
	UsuarioRepository usuarioRepository;

	@Autowired
	DataSource fonte;

	@Autowired
	CifraDeDadosClinicos cifra;

	@Test
	void dadosClinicosFicamCifradosNoBancoELegiveisNaAplicacao() throws Exception {
		Instituicao hospital = cenario.instituicao(true);
		MvcResult admissao = admitir(hospital);
		UUID pacienteId = id(admissao, "$.pacienteId");
		UUID atendimentoId = id(admissao, "$.atendimentoId");
		String medico = cenario.profissional(hospital, Perfil.MEDICO);
		String enfermeiro = cenario.profissional(hospital, Perfil.ENFERMEIRO);

		String triagem = mvc.perform(post("/triagens").with(user(enfermeiro).roles("ENFERMEIRO")).with(csrf())
						.contentType(MediaType.APPLICATION_JSON).content(TRIAGEM.formatted(atendimentoId)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.sintomas").value("Dor torácica irradiada"))
				.andExpect(jsonPath("$.sinaisVitais.frequenciaCardiaca").value(112))
				.andExpect(jsonPath("$.sinaisVitais.temperatura").value(37.8))
				.andReturn().getResponse().getContentAsString();
		UUID triagemId = UUID.fromString(JsonPath.read(triagem, "$.id"));
		// Classificação vermelha: o acesso de emergência mais abaixo só vale com risco imediato (BE-18).
		mvc.perform(post("/triagens/{id}/confirmacao", triagemId).with(user(enfermeiro).roles("ENFERMEIRO")).with(csrf())
						.contentType(MediaType.APPLICATION_JSON).content("{\"cor\":\"VERMELHO\"}"))
				.andExpect(status().isOk());
		// O médico registra depois da triagem: com o atendimento em curso, não se tria mais.
		mvc.perform(post("/atendimentos/{id}/registros", atendimentoId).with(user(medico).roles("MEDICO")).with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"tipo\":\"DIAGNOSTICO\",\"conteudo\":\"Infarto agudo do miocárdio\",\"categoriaInformacao\":\"Cardiologia\",\"codigoCID10\":\"I21.9\"}"))
				.andExpect(status().isCreated());
		// O resumo é gravado pendente e o texto entra depois: cobre o caminho do UPDATE.
		UUID resumoId = resumoHistoricoService.solicitar(atendimentoId, medico).resumoId();
		resumoHistoricoService.concluir(resumoId, "Infarto agudo do miocárdio recente.");
		mvc.perform(post("/atendimentos/{id}/historico/emergencia", atendimentoId).with(user(medico).roles("MEDICO"))
						.with(csrf()).contentType(MediaType.APPLICATION_JSON)
						.content("{\"justificativa\":\"Parada cardiorrespiratória em andamento na sala vermelha\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.registros[*].conteudo").value(hasItem("Infarto agudo do miocárdio")))
				.andExpect(jsonPath("$.registros[*].codigoCID10").value(hasItem("I21.9")));
		mvc.perform(get("/resumos/{resumoId}", resumoId).with(user(medico).roles("MEDICO")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.texto").value("Infarto agudo do miocárdio recente."));

		List<String> gravados = jdbc.queryForList("""
				select conteudo from registro_clinico where prontuario_id = (select id from prontuario where paciente_id = ?)
				union all select codigo_cid10 from registro_clinico where prontuario_id = (select id from prontuario where paciente_id = ?)
				union all select motivo from atendimento where id = ?
				union all select sintomas from triagem where id = ?
				union all select pressao_arterial from triagem where id = ?
				union all select frequencia_cardiaca from triagem where id = ?
				union all select frequencia_respiratoria from triagem where id = ?
				union all select temperatura from triagem where id = ?
				union all select saturacao_oxigenio from triagem where id = ?
				union all select texto from resumo_historico_ia where id = ?
				union all select justificativa from registro_auditoria where emergencia and prontuario_id =
				  (select id from prontuario where paciente_id = ?)
				""", String.class, pacienteId, pacienteId, atendimentoId, triagemId, triagemId, triagemId, triagemId,
				triagemId, triagemId, resumoId, pacienteId);

		assertThat(gravados).hasSize(11).allSatisfy(valor -> assertThat(valor).startsWith("sigh:v2:1:"));
		// Só trechos que não podem surgir por acaso no Base64 (acento, ponto, espaço ou palavra longa).
		assertThat(String.join(" ", gravados)).doesNotContain("Infarto", "I21.9", "torácica", "150/95", "37.8", "93.5",
				"Parada", "Febre alta");
	}

	/** Critério de aceite do BE-22: valor copiado de outra linha não é decifrado. */
	@Test
	void valorCifradoCopiadoDeOutraLinhaNaoEhDecifrado(CapturedOutput saida) throws Exception {
		Instituicao hospital = cenario.instituicao(true);
		UUID pacienteId = id(admitir(hospital), "$.pacienteId");
		String titular = cenario.contaDoPaciente(pacienteId);
		UUID sensivel = cenario.registroClinico(pacienteId, hospital, "Infectologia", "HIV positivo, carga viral detectável");
		UUID comum = cenario.registroClinico(pacienteId, hospital, "Clínica geral", "Resfriado comum");
		String original = conteudo(comum);

		jdbc.update("update registro_clinico set conteudo = ? where id = ?", conteudo(sensivel), comum);
		try {
			mvc.perform(get("/api/pacientes/{id}/historico", pacienteId).with(user(titular).roles("PACIENTE")))
					.andExpect(status().isInternalServerError())
					.andExpect(jsonPath("$.detail").value(containsString("verificação de integridade")))
					.andExpect(content().string(not(containsString("HIV"))));
			assertThat(saida).contains("Leitura recusada: Dado clínico ilegível em registro_clinico.conteudo (linha " + comum)
					.doesNotContain("carga viral");
		} finally {
			jdbc.update("update registro_clinico set conteudo = ? where id = ?", original, comum);
		}
		mvc.perform(get("/api/pacientes/{id}/historico", pacienteId).with(user(titular).roles("PACIENTE")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].conteudo").value(hasItem("Resfriado comum")));
	}

	/**
	 * Critério de aceite do BE-22: valor em claro gravado direto no banco, depois da migração, é detectado
	 * (log ao subir e na leitura), não é cifrado nem aceito como dado.
	 */
	@Test
	void valorEmClaroGravadoDiretoNoBancoEhDetectadoERecusado(CapturedOutput saida) throws Exception {
		Instituicao hospital = cenario.instituicao(true);
		UUID pacienteId = id(admitir(hospital), "$.pacienteId");
		String titular = cenario.contaDoPaciente(pacienteId);
		UUID registro = cenario.registroClinico(pacienteId, hospital, "Clínica geral", "Lombalgia");
		String original = conteudo(registro);

		jdbc.update("update registro_clinico set conteudo = 'Texto injetado sem cifra' where id = ?", registro);
		try {
			ManutencaoDaCifragem.Resultado resultado = manutencao.executar();

			assertThat(resultado.foraDoFormato()).isPositive();
			assertThat(resultado.cifrados()).as("depois da migração, texto claro não é mais cifrado ao subir").isZero();
			assertThat(conteudo(registro)).isEqualTo("Texto injetado sem cifra");
			assertThat(saida).contains("valor(es) sem a cifra atual em registro_clinico.conteudo")
					.doesNotContain("Texto injetado sem cifra");
			assertThat(saudeDaCifragem.health().getStatus()).isEqualTo(Status.DOWN);
			assertThat(saudeDaCifragem.health().getDetails().toString()).contains("registro_clinico.conteudo")
					.doesNotContain("injetado");
			mvc.perform(get("/api/pacientes/{id}/historico", pacienteId).with(user(titular).roles("PACIENTE")))
					.andExpect(status().isInternalServerError())
					.andExpect(jsonPath("$.detail").value(containsString("verificação de integridade")))
					.andExpect(content().string(not(containsString("injetado"))));
			assertThat(saida).contains("Leitura recusada: Dado clínico ilegível em registro_clinico.conteudo (linha "
					+ registro + "): valor sem a cifra atual");
		} finally {
			jdbc.update("update registro_clinico set conteudo = ? where id = ?", original, registro);
		}
		assertThat(manutencao.executar().foraDoFormato()).isZero();
		assertThat(saudeDaCifragem.health().getStatus()).isEqualTo(Status.UP);
		mvc.perform(get("/api/pacientes/{id}/historico", pacienteId).with(user(titular).roles("PACIENTE")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].conteudo").value(hasItem("Lombalgia")));
	}

	/**
	 * Valor que passa de nulo para preenchido sem nenhum outro campo mudar (o cadastro do segundo fator):
	 * precisa chegar ao banco cifrado. Sem transação no teste, como na aplicação: cada chamada relê do banco.
	 */
	@Test
	void valorQuePassaDeNuloParaPreenchidoSozinhoEhGravadoCifrado() {
		String login = cenario.usuario(Perfil.MEDICO).getLogin();
		assertThat(jdbc.queryForObject("select mfa_segredo from usuario where login = ?", String.class, login)).isNull();

		String uri = segundoFatorService.cadastrar(login).uriAutenticador();

		String gravado = jdbc.queryForObject("select mfa_segredo from usuario where login = ?", String.class, login);
		assertThat(gravado).startsWith("sigh:v2:1:");
		String segredo = uri.replaceAll(".*[?&]secret=([A-Z2-7]+).*", "$1");
		assertThat(gravado).doesNotContain(segredo);
		assertThat(usuarioRepository.findByLogin(login).orElseThrow().getMfaSegredo()).isEqualTo(segredo);
	}

	/**
	 * Auditoria: só as linhas listadas na migração continuam lendo o legado. Uma auditoria inserida
	 * depois, fora da aplicação, com justificativa em claro, é detectada e recusada.
	 */
	@Test
	void auditoriaForjadaComJustificativaEmClaroEhRecusadaEALegadaContinuaLegivel(CapturedOutput saida) throws Exception {
		Instituicao hospital = cenario.instituicao(true);
		UUID pacienteId = id(admitir(hospital), "$.pacienteId");
		UUID prontuario = jdbc.queryForObject("select id from prontuario where paciente_id = ?", UUID.class, pacienteId);
		UUID ator = cenario.usuario(Perfil.MEDICO).getId();
		String auditor = cenario.usuario(Perfil.AUDITOR_GESTOR).getLogin();
		UUID forjada = UUID.randomUUID();
		UUID legada = UUID.randomUUID();
		String inserir = "insert into registro_auditoria (id, prontuario_id, ator_id, perfil, instituicao_id, ocorrido_em,"
				+ " finalidade_declarada, emergencia, justificativa) values (?, ?, ?, 'MEDICO', ?, ?, 'Atendimento', true, ?)";
		jdbc.update(inserir, legada, prontuario, ator, hospital.getId(), LocalDateTime.of(2001, 1, 1, 10, 0),
				"Justificativa legada em claro");
		semGatilhos(conexao -> conexao.createStatement().executeUpdate(
				"insert into auditoria_justificativa_legada (registro_id) values ('" + legada + "')"));
		assertThatThrownBy(() -> jdbc.update("insert into auditoria_justificativa_legada (registro_id) values (?)", forjada))
				.as("a lista de legado não aceita linha nova").hasStackTraceContaining("não pode ser alterada");
		try {
			mvc.perform(get("/auditoria/registros").param("inicio", "2001-01-01").param("fim", "2001-01-01")
							.with(user(auditor).roles("AUDITOR_GESTOR")))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.content[0].justificativa").value("Justificativa legada em claro"));
			assertThat(manutencao.executar().foraDoFormato()).as("o legado listado não é anomalia").isZero();

			jdbc.update(inserir, forjada, prontuario, ator, hospital.getId(), LocalDateTime.of(2001, 1, 1, 11, 0),
					"Justificativa forjada em claro");

			assertThat(manutencao.executar().foraDoFormato()).isEqualTo(1);
			assertThat(saida).contains("1 valor(es) sem a cifra atual em registro_auditoria.justificativa");
			mvc.perform(get("/auditoria/registros").param("inicio", "2001-01-01").param("fim", "2001-01-01")
							.with(user(auditor).roles("AUDITOR_GESTOR")))
					.andExpect(status().isInternalServerError())
					.andExpect(content().string(not(containsString("forjada"))));
		} finally {
			semGatilhos(conexao -> {
				conexao.createStatement().executeUpdate("delete from auditoria_justificativa_legada where registro_id = '"
						+ legada + "'");
				return conexao.createStatement().executeUpdate("delete from registro_auditoria where id in ('" + legada
						+ "', '" + forjada + "')");
			});
		}
		assertThat(manutencao.executar().foraDoFormato()).isZero();
	}

	/** Roda com os gatilhos desligados (só o teste faz isso, como superusuário) para montar e limpar o cenário. */
	private void semGatilhos(ConnectionCallback<Integer> acao) {
		jdbc.execute((ConnectionCallback<Integer>) conexao -> {
			conexao.createStatement().execute("set session_replication_role = replica");
			try {
				return acao.doInConnection(conexao);
			} finally {
				conexao.createStatement().execute("reset session_replication_role");
			}
		});
	}

	/**
	 * Primeira execução depois desta versão: o que estava em claro (anterior à criptografia) ou no
	 * formato sem vínculo (BE-09) é cifrado no formato atual, e a versão antiga da linha some também dos
	 * arquivos do banco (VACUUM FULL), que é o que o backup físico copia.
	 */
	@Test
	void primeiraExecucaoCifraOLegadoESomeComAVersaoAntigaDaLinha() throws Exception {
		Instituicao hospital = cenario.instituicao(true);
		MvcResult admissao = admitir(hospital);
		UUID pacienteId = id(admissao, "$.pacienteId");
		UUID atendimentoId = id(admissao, "$.atendimentoId");
		UUID registro = cenario.registroClinico(pacienteId, hospital, "Clínica geral", "Asma");
		String marcador = "AsmaLegadaEmClaro" + UUID.randomUUID().toString().substring(0, 8);
		jdbc.update("update registro_clinico set conteudo = ?, codigo_cid10 = 'J45' where id = ?", marcador, registro);
		jdbc.update("update atendimento set motivo = ? where id = ?", formatoAntigo("Crise asmática"), atendimentoId);
		// As estatísticas (o autovacuum também faz isso) guardam amostras dos valores da coluna. Com o alvo
		// alto, a amostra inclui todas as linhas da tabela, e o controle abaixo não depende do acaso.
		jdbc.execute("alter table registro_clinico alter column conteudo set statistics 10000");
		jdbc.execute("analyze registro_clinico");
		assertThat(jdbc.queryForObject("select count(*) from pg_stats where tablename = 'registro_clinico'"
				+ " and attname = 'conteudo' and concat(most_common_vals::text, histogram_bounds::text) like ?",
				Integer.class, "%" + marcador + "%")).as("controle: as estatísticas guardam o valor legado").isOne();
		jdbc.update("delete from controle_cifragem");
		jdbc.execute("checkpoint");
		assertThat(nosArquivosDoBanco(marcador)).as("controle: o valor legado está nos arquivos").isTrue();

		ManutencaoDaCifragem.Resultado resultado;
		try {
			resultado = manutencao.executar();
		} finally {
			jdbc.update("insert into controle_cifragem (id, formato, concluida_em) values (1, 'v2', now()) on conflict do nothing");
			jdbc.execute("alter table registro_clinico alter column conteudo set statistics -1");
		}

		assertThat(resultado.cifrados()).isGreaterThanOrEqualTo(3);
		assertThat(jdbc.queryForObject("select formato from controle_cifragem", String.class)).isEqualTo("v2");
		assertThat(jdbc.queryForObject("select count(*) from cifragem_vacuum_pendente", Integer.class)).isZero();
		jdbc.execute("checkpoint");
		assertThat(nosArquivosDoBanco(marcador)).as("nem a versão antiga da linha, nem as estatísticas, sobram em claro")
				.isFalse();
		assertThat(conteudo(registro)).startsWith("sigh:v2:1:").doesNotContain("Asma");
		assertThat(jdbc.queryForObject("select motivo from atendimento where id = ?", String.class, atendimentoId))
				.startsWith("sigh:v2:1:");
		assertThat(manutencao.executar().cifrados()).isZero();

		String titular = cenario.contaDoPaciente(pacienteId);
		mvc.perform(get("/api/pacientes/{id}/historico", pacienteId).with(user(titular).roles("PACIENTE")))
				.andExpect(jsonPath("$[0].conteudo").value(marcador))
				.andExpect(jsonPath("$[0].codigoCID10").value("J45"));
	}

	/**
	 * Depois de trocar a chave, os valores da chave anterior são regravados com a atual, e o valor antigo
	 * some dos arquivos do banco; a chave anterior pode então sair do ambiente.
	 */
	@Test
	void trocaDeChaveRegravaComAChaveAtual() throws Exception {
		Instituicao hospital = cenario.instituicao(true);
		UUID pacienteId = id(admitir(hospital), "$.pacienteId");
		UUID registro = cenario.registroClinico(pacienteId, hospital, "Clínica geral", "Gastrite");
		CifraDeDadosClinicos anterior = new CifraDeDadosClinicos(CHAVE_ANTERIOR, "0", "");
		String daChaveAnterior = anterior.cifrar("Gastrite crônica", "registro_clinico.conteudo", registro);
		jdbc.update("update registro_clinico set conteudo = ? where id = ?", daChaveAnterior, registro);
		jdbc.execute("checkpoint");
		String trecho = daChaveAnterior.substring(daChaveAnterior.length() - 30);
		assertThat(nosArquivosDoBanco(trecho)).as("controle: o valor da chave anterior está nos arquivos").isTrue();

		assertThat(manutencao.executar().regravados()).isPositive();

		assertThat(conteudo(registro)).startsWith("sigh:v2:1:");
		jdbc.execute("checkpoint");
		assertThat(nosArquivosDoBanco(trecho)).as("o valor da chave anterior some dos arquivos").isFalse();
		mvc.perform(get("/api/pacientes/{id}/historico", pacienteId)
						.with(user(cenario.contaDoPaciente(pacienteId)).roles("PACIENTE")))
				.andExpect(jsonPath("$[*].conteudo").value(hasItem("Gastrite crônica")));
	}

	/**
	 * Se a aplicação cair depois de regravar e antes do VACUUM FULL, a tabela fica na fila e o VACUUM é
	 * feito na próxima inicialização. Um nome que não é de tabela cifrada nunca chega ao comando.
	 */
	@Test
	void vacuumQueFicouPendenteEhFeitoNaProximaInicializacao() throws Exception {
		Instituicao hospital = cenario.instituicao(true);
		UUID pacienteId = id(admitir(hospital), "$.pacienteId");
		UUID registro = cenario.registroClinico(pacienteId, hospital, "Clínica geral", "Cefaleia");
		String marcador = "VersaoAntigaDaLinha" + UUID.randomUUID().toString().substring(0, 8);
		jdbc.update("update registro_clinico set categoria_informacao = ? where id = ?", marcador, registro);
		jdbc.update("update registro_clinico set categoria_informacao = 'Clínica geral' where id = ?", registro);
		jdbc.update("insert into cifragem_vacuum_pendente (tabela) values ('registro_clinico'), ('controle_cifragem; drop table controle_cifragem')");
		jdbc.execute("checkpoint");
		assertThat(nosArquivosDoBanco(marcador)).as("controle: a versão antiga da linha está nos arquivos").isTrue();

		manutencao.executar();

		jdbc.execute("checkpoint");
		assertThat(nosArquivosDoBanco(marcador)).isFalse();
		assertThat(jdbc.queryForObject("select count(*) from cifragem_vacuum_pendente", Integer.class)).isZero();
		assertThat(jdbc.queryForObject("select to_regclass('controle_cifragem') is not null", Boolean.class)).isTrue();
	}

	/** Com outra sessão segurando um retrato antigo, o VACUUM FULL não limparia: fica na fila para depois. */
	@Test
	void vacuumEsperaASessaoQueSeguraUmRetratoAntigo(CapturedOutput saida) throws Exception {
		jdbc.update("insert into cifragem_vacuum_pendente (tabela) values ('atendimento') on conflict do nothing");
		try (Connection outra = fonte.getConnection()) {
			outra.setAutoCommit(false);
			outra.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
			outra.createStatement().executeQuery("select count(*) from atendimento").close();

			manutencao.executar();

			assertThat(jdbc.queryForObject("select count(*) from cifragem_vacuum_pendente where tabela = 'atendimento'",
					Integer.class)).isOne();
			assertThat(saida).contains("VACUUM FULL de atendimento adiado: outra sessão segura um retrato antigo");
			assertThat(saudeDaCifragem.health().getStatus()).as("VACUUM pendente acende o alerta").isEqualTo(Status.DOWN);
			assertThat(saudeDaCifragem.health().getDetails().toString()).contains("atendimento");
			outra.rollback();
		}
		manutencao.executar();
		assertThat(jdbc.queryForObject("select count(*) from cifragem_vacuum_pendente", Integer.class)).isZero();
		assertThat(saudeDaCifragem.health().getStatus()).isEqualTo(Status.UP);
	}

	/**
	 * Com a aplicação conectada como dona das tabelas, mas sem ser superusuária (como recomendado), o
	 * VACUUM das estatísticas seria pulado em silêncio: a tabela fica na fila até o papel pg_maintain ser
	 * concedido.
	 */
	@Test
	void semPermissaoNasEstatisticasOVacuumFicaNaFila(CapturedOutput saida) {
		String papel = "sigh_app_teste";
		jdbc.execute("create role " + papel + " login password 'senha-ficticia-do-papel'");
		jdbc.execute("grant select, insert, update, delete on all tables in schema public to " + papel);
		jdbc.execute("alter table atendimento owner to " + papel);
		try {
			DriverManagerDataSource comoAplicacao = new DriverManagerDataSource(banco.getJdbcUrl(), papel,
					"senha-ficticia-do-papel");
			ManutencaoDaCifragem semSuperusuario = new ManutencaoDaCifragem(new JdbcTemplate(comoAplicacao),
					new TransactionTemplate(new DataSourceTransactionManager(comoAplicacao)), cifra, Clock.systemUTC());
			jdbc.update("insert into cifragem_vacuum_pendente (tabela) values ('atendimento') on conflict do nothing");

			semSuperusuario.executar();

			assertThat(jdbc.queryForObject("select count(*) from cifragem_vacuum_pendente where tabela = 'atendimento'",
					Integer.class)).isOne();
			assertThat(saida).contains("sem permissão para limpar as estatísticas", "GRANT pg_maintain");

			jdbc.execute("grant pg_maintain to " + papel);
			semSuperusuario.executar();

			assertThat(jdbc.queryForObject("select count(*) from cifragem_vacuum_pendente", Integer.class)).isZero();
		} finally {
			jdbc.update("delete from cifragem_vacuum_pendente");
			jdbc.execute("alter table atendimento owner to " + banco.getUsername());
			jdbc.execute("revoke all on all tables in schema public from " + papel);
			jdbc.execute("revoke pg_maintain from " + papel);
			jdbc.execute("drop role " + papel);
		}
	}

	/**
	 * As colunas cifradas, lidas das entidades (as mesmas que o listener e a manutenção usam), são as
	 * esperadas e existem no banco como texto, com espaço para o valor cifrado. Um campo clínico novo precisa entrar aqui de propósito.
	 */
	@Test
	void colunasCifradasSaoAsEsperadasEExistemNoBanco() {
		Set<String> cifradas = CamposCifrados.daAplicacao().stream().map(CamposCifrados.Campo::nome)
				.collect(Collectors.toCollection(TreeSet::new));

		assertThat(cifradas).containsExactly("analise_auditoria.descricao", "atendimento.motivo",
				"registro_auditoria.justificativa", "registro_clinico.codigo_cid10", "registro_clinico.conteudo",
				"resumo_historico_ia.texto", "triagem.frequencia_cardiaca", "triagem.frequencia_respiratoria",
				"triagem.pressao_arterial", "triagem.saturacao_oxigenio", "triagem.sintomas", "triagem.temperatura",
				"usuario.mfa_segredo");
		for (CamposCifrados.Campo campo : CamposCifrados.daAplicacao()) {
			// Texto sem limite, ou com folga para o valor cifrado (a semente do TOTP cifrada tem ~90 caracteres).
			assertThat(jdbc.queryForObject("select coalesce(character_maximum_length, 1000000) from information_schema.columns"
					+ " where table_name = ? and column_name = ? and data_type in ('text', 'character varying')", Integer.class,
					campo.tabela(), campo.coluna())).as(campo.nome()).isGreaterThanOrEqualTo(255);
		}
		assertThat(CamposCifrados.daAplicacao()).filteredOn(CamposCifrados.Campo::legadoImutavel)
				.extracting(CamposCifrados.Campo::nome).containsExactly("registro_auditoria.justificativa");
	}

	@Test
	void nenhumaColunaClinicaFicaForaDoFormatoAtualDepoisDaInicializacao() {
		assertThat(manutencao.executar().foraDoFormato()).isZero();
		for (CamposCifrados.Campo campo : CamposCifrados.daAplicacao()) {
			if (campo.legadoImutavel()) {
				continue;
			}
			Integer fora = jdbc.queryForObject("select count(*) from " + campo.tabela() + " where " + campo.coluna()
					+ " is not null and not starts_with(" + campo.coluna() + ", 'sigh:v2:1:')", Integer.class);
			assertThat(fora).as(campo.nome()).isZero();
		}
	}

	private String conteudo(UUID registro) {
		return jdbc.queryForObject("select conteudo from registro_clinico where id = ?", String.class, registro);
	}

	private boolean nosArquivosDoBanco(String texto) throws Exception {
		ExecResult busca = banco.execInContainer("sh", "-c",
				"LC_ALL=C grep -r -a -l -F '" + texto + "' /var/lib/postgresql/data/base");
		assertThat(busca.getExitCode()).as(busca.getStderr()).isIn(0, 1);
		return busca.getExitCode() == 0;
	}

	/** Valor como o BE-09 gravava (sigh:v1, sem vínculo à linha), com a chave atual dos testes. */
	private static String formatoAntigo(String texto) throws Exception {
		byte[] iv = new byte[12];
		Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
		aes.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(CHAVE_ATUAL, "AES"), new GCMParameterSpec(128, iv));
		byte[] cifrado = aes.doFinal(texto.getBytes(StandardCharsets.UTF_8));
		return "sigh:v1:1:" + Base64.getEncoder().encodeToString(ByteBuffer.allocate(12 + cifrado.length).put(iv).put(cifrado).array());
	}

	private MvcResult admitir(Instituicao hospital) throws Exception {
		return mvc.perform(post("/api/pacientes/admissoes")
						.with(user(cenario.recepcao(hospital)).roles("RECEPCAO")).with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"cpf":"%s","nome":"Maria Fictícia","dataNascimento":"1980-05-17",
								 "consentimentoInicial":"AUTORIZADO","instituicaoId":"%s","motivo":"Febre alta"}
								""".formatted(Cenario.novoCpf(), hospital.getId())))
				.andExpect(status().isCreated()).andReturn();
	}

	private static UUID id(MvcResult resultado, String caminho) throws Exception {
		return UUID.fromString(JsonPath.read(resultado.getResponse().getContentAsString(), caminho));
	}
}
