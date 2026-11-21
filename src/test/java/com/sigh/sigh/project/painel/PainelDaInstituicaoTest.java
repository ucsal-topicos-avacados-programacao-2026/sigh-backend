package com.sigh.sigh.project.painel;

import com.jayway.jsonpath.JsonPath;
import com.sigh.sigh.project.instituicao.entity.Instituicao;
import com.sigh.sigh.project.support.Cenario;
import com.sigh.sigh.project.support.TesteIntegracao;
import com.sigh.sigh.project.usuario.entity.Perfil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BE-27 (FE-09): os números da instituição para o painel. Só contagens e tempos, com as mesmas regras das listas do
 * enfermeiro e do administrativo; nenhum dado de paciente.
 */
class PainelDaInstituicaoTest extends TesteIntegracao {

	private static final String TRIAGEM = """
			{"atendimentoId":"%s","sintomas":"Febre","sinaisVitais":{"pressaoArterial":"120/80"}}
			""";
	private static final String NOME = "Paciente do Painel ";
	private static final String MOTIVO = "Motivo do painel ";

	@Autowired
	JdbcTemplate jdbc;

	private Instituicao hospitalA;
	private Instituicao hospitalB;
	private String administrativoA;
	private String enfermeiroA;
	private String medicoA;
	private int admitidos;

	@BeforeEach
	void hospitais() {
		hospitalA = cenario.instituicao(true);
		hospitalB = cenario.instituicao(true);
		administrativoA = cenario.administrativo(hospitalA);
		enfermeiroA = cenario.profissional(hospitalA, Perfil.ENFERMEIRO);
		medicoA = cenario.profissional(hospitalA, Perfil.MEDICO);
	}

	@Test
	void contaLeitosFilaEsperaInternadosChegadasETransferenciasSoDaInstituicao() throws Exception {
		LocalDateTime inicio = LocalDateTime.now(relogio);
		UUID aguardandoHaMais = admitir(hospitalA);
		classificar(admitir(hospitalA), "AMARELO");
		relogio.avancar(Duration.ofMinutes(50));
		classificar(admitir(hospitalA), "VERDE");
		UUID reclassificado = admitir(hospitalA);
		classificar(reclassificado, "VERDE");
		relogio.avancar(Duration.ofMinutes(10));
		classificar(reclassificado, "LARANJA");
		// Triagem começada e não confirmada: continua aguardando triagem.
		como(enfermeiroA, "ENFERMEIRO", post("/triagens").content(TRIAGEM.formatted(admitir(hospitalA))))
				.andExpect(status().isCreated());
		UUID internado = admitir(hospitalA);
		registrar(internado);
		UUID enfermariaOcupada = leito(administrativoA, hospitalA, "ENFERMARIA");
		leito(administrativoA, hospitalA, "ENFERMARIA");
		UUID uti = leito(administrativoA, hospitalA, "UTI");
		como(administrativoA, "ADMINISTRATIVO", patch("/leitos/{id}/status", uti).content("{\"status\":\"BLOQUEADO\"}"))
				.andExpect(status().isOk());
		UUID internacao = internar(administrativoA, internado, enfermariaOcupada);
		relogio.avancar(Duration.ofMinutes(20));
		classificar(admitir(hospitalA), "AMARELO");
		// Encerrados: um chegou no último segundo de ontem, o outro à meia-noite de hoje.
		LocalDateTime meiaNoite = LocalDate.now(relogio).atStartOfDay();
		encerrarComChegada(admitir(hospitalA), meiaNoite.minusSeconds(1));
		encerrarComChegada(admitir(hospitalA), meiaNoite);

		// O outro hospital: quem aguarda, um internado e um pedido de transferência para o hospital A.
		String administrativoB = cenario.administrativo(hospitalB);
		admitir(hospitalB);
		UUID internacaoB = internar(administrativoB, admitir(hospitalB), leito(administrativoB, hospitalB, "ENFERMARIA"));
		pedirTransferencia(administrativoB, internacaoB, hospitalA);
		pedirTransferencia(administrativoA, internacao, hospitalB);
		relogio.avancar(Duration.ofMinutes(10));

		String corpo = painel(administrativoA, "ADMINISTRATIVO", hospitalA).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		Map<String, Object> painel = JsonPath.read(corpo, "$");
		assertThat(painel).containsOnlyKeys("instituicaoId", "instituicaoNome", "tipo", "geradoEm", "leitos",
				"aguardandoTriagem", "fila", "emAtendimento", "internados", "chegadasHoje", "transferencias");
		assertThat(painel).containsEntry("instituicaoId", hospitalA.getId().toString())
				.containsEntry("instituicaoNome", hospitalA.getNome()).containsEntry("tipo", "PUBLICA")
				.containsEntry("emAtendimento", 1).containsEntry("internados", 1).containsEntry("chegadasHoje", 8);
		assertThat(LocalDateTime.parse((String) painel.get("geradoEm"))).isEqualTo(inicio.plusMinutes(90));
		assertThat(JsonPath.<List<Map<String, Object>>>read(corpo, "$.leitos")).containsExactly(
				Map.of("categoria", "ENFERMARIA", "status", "LIVRE", "quantidade", 1),
				Map.of("categoria", "ENFERMARIA", "status", "OCUPADO", "quantidade", 1),
				Map.of("categoria", "UTI", "status", "BLOQUEADO", "quantidade", 1));
		assertThat(JsonPath.<Map<String, Object>>read(corpo, "$.aguardandoTriagem"))
				.isEqualTo(Map.of("quantidade", 2, "maiorEsperaMinutos", 90));
		assertThat(aguardandoHaMais).isNotNull();
		List<Map<String, Object>> fila = JsonPath.read(corpo, "$.fila");
		assertThat(fila).extracting(item -> item.get("cor")).containsExactly("VERMELHO", "LARANJA", "AMARELO", "VERDE", "AZUL");
		assertThat(fila.get(0)).containsEntry("quantidade", 0).containsEntry("maiorEsperaMinutos", null)
				.containsEntry("tempoMaximoMinutos", 0).containsEntry("acimaDoTempoMaximo", 0);
		// O reclassificado conta só na cor mais recente, e passou dos 10 minutos do laranja.
		assertThat(fila.get(1)).containsEntry("quantidade", 1).containsEntry("maiorEsperaMinutos", 40)
				.containsEntry("tempoMaximoMinutos", 10).containsEntry("acimaDoTempoMaximo", 1);
		// Dois amarelos: o de 90 minutos passou dos 60; o de 10, não.
		assertThat(fila.get(2)).containsEntry("quantidade", 2).containsEntry("maiorEsperaMinutos", 90)
				.containsEntry("tempoMaximoMinutos", 60).containsEntry("acimaDoTempoMaximo", 1);
		assertThat(fila.get(3)).containsEntry("quantidade", 1).containsEntry("maiorEsperaMinutos", 40)
				.containsEntry("tempoMaximoMinutos", 120).containsEntry("acimaDoTempoMaximo", 0);
		assertThat(fila.get(4)).containsEntry("quantidade", 0).containsEntry("tempoMaximoMinutos", 240);
		assertThat(JsonPath.<Map<String, Object>>read(corpo, "$.transferencias"))
				.isEqualTo(Map.of("recebidas", 1, "enviadas", 1));
		// Nenhum nome nem motivo de paciente na resposta.
		assertThat(corpo).doesNotContain(NOME).doesNotContain(MOTIVO);
	}

	@Test
	void transferidoDeLeitoContaUmaVezEPedidosRecebidosEEnviadosSaoContadosAParte() throws Exception {
		UUID internado = admitir(hospitalA);
		registrar(internado);
		UUID primeiro = leito(administrativoA, hospitalA, "ENFERMARIA");
		UUID segundo = leito(administrativoA, hospitalA, "ENFERMARIA");
		UUID internacao = internar(administrativoA, internado, primeiro);
		// Transferência dentro do hospital: a ocupação do primeiro leito termina e começa a do segundo.
		como(administrativoA, "ADMINISTRATIVO", post("/internacoes/{id}/transferencia", internacao)
				.content("{\"leitoDestinoId\":\"%s\"}".formatted(segundo))).andExpect(status().isOk());
		// Dois pedidos chegam do hospital B; o hospital A envia um.
		String administrativoB = cenario.administrativo(hospitalB);
		for (int i = 0; i < 2; i++) {
			UUID internacaoB = internar(administrativoB, admitir(hospitalB),
					leito(administrativoB, hospitalB, "ENFERMARIA"));
			pedirTransferencia(administrativoB, internacaoB, hospitalA);
		}
		pedirTransferencia(administrativoA, internacao, hospitalB);

		String corpo = painel(administrativoA, "ADMINISTRATIVO", hospitalA).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		assertThat(JsonPath.<Integer>read(corpo, "$.internados")).isEqualTo(1);
		assertThat(JsonPath.<Map<String, Object>>read(corpo, "$.transferencias"))
				.isEqualTo(Map.of("recebidas", 2, "enviadas", 1));
		assertThat(JsonPath.<Map<String, Object>>read(painel(administrativoB, "ADMINISTRATIVO", hospitalB)
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.transferencias"))
				.isEqualTo(Map.of("recebidas", 1, "enviadas", 2));
	}

	@Test
	void tempoNoLimiteDaCorNaoContaComoAcima() throws Exception {
		classificar(admitir(hospitalA), "AMARELO");
		relogio.avancar(Duration.ofMinutes(60));
		List<Map<String, Object>> fila = JsonPath.read(painel(enfermeiroA, "ENFERMEIRO", hospitalA)
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.fila");
		assertThat(fila.get(2)).containsEntry("maiorEsperaMinutos", 60).containsEntry("acimaDoTempoMaximo", 0);
		relogio.avancar(Duration.ofSeconds(1));
		fila = JsonPath.read(painel(enfermeiroA, "ENFERMEIRO", hospitalA)
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.fila");
		assertThat(fila.get(2)).containsEntry("acimaDoTempoMaximo", 1);
	}

	@Test
	void instituicaoVaziaTemZerosENenhumaEspera() throws Exception {
		String corpo = painel(medicoA, "MEDICO", hospitalA).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		assertThat(JsonPath.<List<Object>>read(corpo, "$.leitos")).isEmpty();
		assertThat(JsonPath.<Map<String, Object>>read(corpo, "$.aguardandoTriagem"))
				.containsEntry("quantidade", 0).containsEntry("maiorEsperaMinutos", null);
		assertThat(JsonPath.<List<Integer>>read(corpo, "$.fila[*].quantidade")).containsOnly(0);
		assertThat(JsonPath.<Integer>read(corpo, "$.chegadasHoje")).isZero();
	}

	@Test
	void gestorDaRedeVeQualquerInstituicaoSemVinculo() throws Exception {
		String gestor = cenario.usuario(Perfil.AUDITOR_GESTOR).getLogin();
		admitir(hospitalB);
		String corpo = painel(gestor, "AUDITOR_GESTOR", hospitalB).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		assertThat(JsonPath.<String>read(corpo, "$.instituicaoId")).isEqualTo(hospitalB.getId().toString());
		assertThat(JsonPath.<Integer>read(corpo, "$.aguardandoTriagem.quantidade")).isEqualTo(1);
		painel(gestor, "AUDITOR_GESTOR", hospitalA).andExpect(status().isOk());
	}

	@Test
	void gestorComInstituicaoInexistenteRecebe404() throws Exception {
		String gestor = cenario.usuario(Perfil.AUDITOR_GESTOR).getLogin();
		mvc.perform(get("/painel").param("instituicaoId", UUID.randomUUID().toString()).with(user(gestor).roles("AUDITOR_GESTOR")))
				.andExpect(status().isNotFound());
	}

	@Test
	void profissionalSemVinculoRecebe403MesmoParaInstituicaoInexistente() throws Exception {
		String medicoB = cenario.profissional(hospitalB, Perfil.MEDICO);
		painel(medicoB, "MEDICO", hospitalA).andExpect(status().isForbidden());
		mvc.perform(get("/painel").param("instituicaoId", UUID.randomUUID().toString()).with(user(medicoB).roles("MEDICO")))
				.andExpect(status().isForbidden());
	}

	@Test
	void pacienteNaoVeOPainel() throws Exception {
		painel(cenario.usuario(Perfil.PACIENTE).getLogin(), "PACIENTE", hospitalA).andExpect(status().isForbidden());
	}

	@Test
	void semLoginRecebe401() throws Exception {
		mvc.perform(get("/painel").param("instituicaoId", hospitalA.getId().toString())).andExpect(status().isUnauthorized());
	}

	private ResultActions painel(String login, String perfil, Instituicao hospital) throws Exception {
		return mvc.perform(get("/painel").param("instituicaoId", hospital.getId().toString()).with(user(login).roles(perfil)));
	}

	private UUID admitir(Instituicao hospital) throws Exception {
		admitidos++;
		return id(como(cenario.recepcao(hospital), "RECEPCAO", post("/api/pacientes/admissoes").content("""
				{"cpf":"%s","nome":"%s","dataNascimento":"1980-05-17","instituicaoId":"%s","motivo":"%s"}
				""".formatted(Cenario.novoCpf(), NOME + admitidos, hospital.getId(), MOTIVO + admitidos)))
				.andExpect(status().isCreated()), "$.atendimentoId");
	}

	private void classificar(UUID atendimento, String cor) throws Exception {
		UUID triagem = id(como(enfermeiroA, "ENFERMEIRO", post("/triagens").content(TRIAGEM.formatted(atendimento)))
				.andExpect(status().isCreated()), "$.id");
		como(enfermeiroA, "ENFERMEIRO", post("/triagens/{id}/confirmacao", triagem).content("{\"cor\":\"%s\"}".formatted(cor)))
				.andExpect(status().isOk());
	}

	private void registrar(UUID atendimento) throws Exception {
		como(medicoA, "MEDICO", post("/atendimentos/{id}/registros", atendimento)
				.content("{\"tipo\":\"EVOLUCAO\",\"conteudo\":\"Avaliado\",\"categoriaInformacao\":\"Clínica geral\"}"))
				.andExpect(status().isCreated());
	}

	private void encerrarComChegada(UUID atendimento, LocalDateTime chegada) throws Exception {
		registrar(atendimento);
		como(medicoA, "MEDICO", post("/atendimentos/{id}/encerramento", atendimento)).andExpect(status().isNoContent());
		jdbc.update("update atendimento set chegada_em = ? where id = ?", chegada, atendimento);
	}

	private UUID leito(String administrativo, Instituicao hospital, String categoria) throws Exception {
		return id(como(administrativo, "ADMINISTRATIVO", post("/leitos").content("""
				{"instituicaoId":"%s","codigo":"P-%s","setor":"Setor","categoria":"%s"}
				""".formatted(hospital.getId(), UUID.randomUUID().toString().substring(0, 8), categoria)))
				.andExpect(status().isCreated()), "$.id");
	}

	private UUID internar(String administrativo, UUID atendimento, UUID leito) throws Exception {
		return id(como(administrativo, "ADMINISTRATIVO", post("/internacoes")
				.content("{\"atendimentoId\":\"%s\",\"leitoId\":\"%s\"}".formatted(atendimento, leito)))
				.andExpect(status().isCreated()), "$.id");
	}

	private void pedirTransferencia(String administrativo, UUID internacao, Instituicao destino) throws Exception {
		como(administrativo, "ADMINISTRATIVO", post("/internacoes/{id}/solicitacoes-transferencia", internacao)
				.content("{\"instituicaoDestinoId\":\"%s\",\"categoria\":\"ENFERMARIA\"}".formatted(destino.getId())))
				.andExpect(status().isCreated());
	}

	private ResultActions como(String login, String perfil, MockHttpServletRequestBuilder requisicao) throws Exception {
		return mvc.perform(requisicao.with(user(login).roles(perfil)).with(csrf()).contentType(MediaType.APPLICATION_JSON));
	}

	private static UUID id(ResultActions resultado, String caminho) throws Exception {
		return UUID.fromString(JsonPath.read(resultado.andReturn().getResponse().getContentAsString(), caminho));
	}
}
