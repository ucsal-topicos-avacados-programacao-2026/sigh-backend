package com.sigh.sigh.project.leito;

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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BE-21 (RF-006): pela ERS (Processo 2 - Gestão de Leitos), a transferência para outra unidade da rede
 * é solicitada pela origem, e a unidade de destino executa o próprio processo: o administrativo do
 * destino aceita escolhendo um leito livre dele, ou recusa. A origem não ocupa leito de outra instituição.
 */
class TransferenciaEntreInstituicoesTest extends TesteIntegracao {

	@Autowired
	JdbcTemplate jdbc;

	private Instituicao hospitalA;
	private Instituicao hospitalB;
	private String administrativoA;
	private String administrativoB;
	private UUID atendimentoId;
	private UUID leitoA;
	private UUID utiB;
	private UUID enfermariaB;
	private UUID internacaoId;

	@BeforeEach
	void internarNoHospitalA() throws Exception {
		hospitalA = cenario.instituicao(true);
		hospitalB = cenario.instituicao(true);
		administrativoA = cenario.administrativo(hospitalA);
		administrativoB = cenario.administrativo(hospitalB);
		atendimentoId = id(mvc.perform(post("/api/pacientes/admissoes").with(user(cenario.recepcao(hospitalA)).roles("RECEPCAO")).with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content("""
				{"cpf":"%s","nome":"Maria Fictícia","dataNascimento":"1980-05-17",
				 "consentimentoInicial":"AUTORIZADO","instituicaoId":"%s","motivo":"Pneumonia"}
				""".formatted(Cenario.novoCpf(), hospitalA.getId()))).andExpect(status().isCreated()), "$.atendimentoId");
		leitoA = leito(administrativoA, hospitalA, "ENFERMARIA");
		utiB = leito(administrativoB, hospitalB, "UTI");
		enfermariaB = leito(administrativoB, hospitalB, "ENFERMARIA");
		internacaoId = id(como(administrativoA, post("/internacoes")
						.content("{\"atendimentoId\":\"%s\",\"leitoId\":\"%s\"}".formatted(atendimentoId, leitoA)))
				.andExpect(status().isCreated()), "$.id");
	}

	@Test
	void origemNaoOcupaLeitoDeOutraInstituicao() throws Exception {
		como(administrativoA, post("/internacoes/{id}/transferencia", internacaoId)
						.content("{\"leitoDestinoId\":\"%s\"}".formatted(utiB)))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.detail").value(containsString("por solicitação")));

		assertThat(statusDoLeito(utiB)).isEqualTo("LIVRE");
		assertThat(statusDoLeito(leitoA)).isEqualTo("OCUPADO");
	}

	@Test
	void destinoAceitaEscolhendoUmLeitoLivreDaCategoriaPedida() throws Exception {
		UUID solicitacao = solicitar("UTI");
		como(administrativoB, get("/solicitacoes-transferencia/recebidas").param("instituicaoId", hospitalB.getId().toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[*].id").value(hasItem(solicitacao.toString())))
				.andExpect(jsonPath("$.content[0].paciente").value("Maria Fictícia"));
		como(administrativoA, get("/solicitacoes-transferencia/recebidas").param("instituicaoId", hospitalB.getId().toString()))
				.andExpect(status().isForbidden());

		aceitar(administrativoB, solicitacao, enfermariaB).andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.detail").value(containsString("categoria")));
		// Leito livre e da categoria pedida, mas da origem: o destino só aceita com leito dele.
		UUID utiLivreEmA = leito(administrativoA, hospitalA, "UTI");
		aceitar(administrativoB, solicitacao, utiLivreEmA).andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.detail").value(containsString("instituição de destino")));
		assertThat(statusDoLeito(utiLivreEmA)).isEqualTo("LIVRE");
		aceitar(administrativoA, solicitacao, utiB).andExpect(status().isForbidden());
		aceitar(cenario.administrativo(cenario.instituicao(true)), solicitacao, utiB).andExpect(status().isForbidden());

		aceitar(administrativoB, solicitacao, utiB).andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ACEITA"))
				.andExpect(jsonPath("$.leitoDestinoId").value(utiB.toString()));

		assertThat(statusDoLeito(utiB)).isEqualTo("OCUPADO");
		assertThat(statusDoLeito(leitoA)).as("o leito da origem vai para higienização").isEqualTo("EM_HIGIENIZACAO");
		como(administrativoB, get("/solicitacoes-transferencia/recebidas").param("instituicaoId", hospitalB.getId().toString()))
				.andExpect(jsonPath("$.content[*].id").value(not(hasItem(solicitacao.toString()))));
		// Agora o paciente está em B: quem responde pela internação é o administrativo de B.
		como(administrativoA, post("/internacoes/{id}/transferencia", internacaoId)
						.content("{\"leitoDestinoId\":\"%s\"}".formatted(enfermariaB)))
				.andExpect(status().isForbidden());
		como(administrativoB, post("/internacoes/{id}/transferencia", internacaoId)
						.content("{\"leitoDestinoId\":\"%s\"}".formatted(enfermariaB)))
				.andExpect(status().isOk());
		aceitar(administrativoB, solicitacao, enfermariaB).andExpect(status().isUnprocessableContent());
	}

	@Test
	void destinoRecusaComMotivoEOPacienteFicaNaOrigem() throws Exception {
		UUID solicitacao = solicitar("UTI");
		como(administrativoA, post("/solicitacoes-transferencia/{id}/recusa", solicitacao)
						.content("{\"motivo\":\"A origem não decide pelo destino\"}"))
				.andExpect(status().isForbidden());

		como(administrativoB, post("/solicitacoes-transferencia/{id}/recusa", solicitacao).content("{\"motivo\":\" \"}"))
				.andExpect(status().isBadRequest());
		como(administrativoB, post("/solicitacoes-transferencia/{id}/recusa", solicitacao)
						.content("{\"motivo\":\"Sem equipe de UTI pediátrica hoje\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("RECUSADA"))
				.andExpect(jsonPath("$.motivoRecusa").value("Sem equipe de UTI pediátrica hoje"));

		assertThat(statusDoLeito(leitoA)).isEqualTo("OCUPADO");
		assertThat(statusDoLeito(utiB)).isEqualTo("LIVRE");
		aceitar(administrativoB, solicitacao, utiB).andExpect(status().isUnprocessableContent());
		// A origem fica sabendo da recusa e do motivo; o destino não vê os pedidos que a origem fez.
		como(administrativoA, get("/solicitacoes-transferencia/enviadas").param("instituicaoId", hospitalA.getId().toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[?(@.id == '" + solicitacao + "')].status").value(hasItem("RECUSADA")))
				.andExpect(jsonPath("$.content[?(@.id == '" + solicitacao + "')].motivoRecusa")
						.value(hasItem("Sem equipe de UTI pediátrica hoje")));
		como(administrativoB, get("/solicitacoes-transferencia/enviadas").param("instituicaoId", hospitalA.getId().toString()))
				.andExpect(status().isForbidden());
		assertThat(solicitar("UTI")).as("recusada, a origem pode pedir de novo (a outra instituição ou à mesma)").isNotNull();
	}

	@Test
	void origemCancelaEnquantoPendenteESoTemUmPedidoPorVez() throws Exception {
		UUID solicitacao = solicitar("UTI");
		como(administrativoA, post("/internacoes/{id}/solicitacoes-transferencia", internacaoId)
						.content("{\"instituicaoDestinoId\":\"%s\",\"categoria\":\"UTI\"}".formatted(hospitalB.getId())))
				.andExpect(status().isUnprocessableContent());

		como(administrativoB, post("/solicitacoes-transferencia/{id}/cancelamento", solicitacao))
				.andExpect(status().isForbidden());
		como(administrativoA, post("/solicitacoes-transferencia/{id}/cancelamento", solicitacao))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CANCELADA"));

		aceitar(administrativoB, solicitacao, utiB).andExpect(status().isUnprocessableContent());
		assertThat(statusDoLeito(utiB)).isEqualTo("LIVRE");
	}

	@Test
	void naoSePedeParaAMesmaInstituicaoNemParaInstituicaoDescredenciada() throws Exception {
		como(administrativoA, post("/internacoes/{id}/solicitacoes-transferencia", internacaoId)
						.content("{\"instituicaoDestinoId\":\"%s\",\"categoria\":\"UTI\"}".formatted(hospitalA.getId())))
				.andExpect(status().isUnprocessableContent());
		como(administrativoA, post("/internacoes/{id}/solicitacoes-transferencia", internacaoId)
						.content("{\"instituicaoDestinoId\":\"%s\",\"categoria\":\"UTI\"}"
								.formatted(cenario.instituicao(false).getId())))
				.andExpect(status().isUnprocessableContent());
		como(cenario.administrativo(hospitalB), post("/internacoes/{id}/solicitacoes-transferencia", internacaoId)
						.content("{\"instituicaoDestinoId\":\"%s\",\"categoria\":\"UTI\"}".formatted(hospitalB.getId())))
				.andExpect(status().isForbidden());
	}

	@Test
	void destinoNaoAceitaComLeitoQueNaoEstaLivreNemDepoisDePerderOCredenciamento() throws Exception {
		UUID solicitacao = solicitar("UTI");
		jdbc.update("update leito set status = 'BLOQUEADO' where id = ?", utiB);
		aceitar(administrativoB, solicitacao, utiB).andExpect(status().isUnprocessableContent());
		jdbc.update("update leito set status = 'LIVRE' where id = ?", utiB);

		jdbc.update("update instituicao set credenciada = false where id = ?", hospitalB.getId());
		aceitar(administrativoB, solicitacao, utiB).andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.detail").value(containsString("credenciada")));
		assertThat(statusDoLeito(utiB)).isEqualTo("LIVRE");
		assertThat(statusDoLeito(leitoA)).isEqualTo("OCUPADO");
	}

	/**
	 * Origem e destino agindo ao mesmo tempo sobre a mesma internação: a trava da internação os põe em
	 * fila, e o paciente nunca fica com dois leitos, nem com leito ocupado depois da alta.
	 */
	@Test
	void aceiteAoMesmoTempoQueATransferenciaDiretaOuQueAAltaNaoCorrompeOsLeitos() throws Exception {
		for (int rodada = 0; rodada < 5; rodada++) {
			internarNoHospitalA();
			UUID enfermariaA = leito(administrativoA, hospitalA, "ENFERMARIA");
			UUID solicitacao = solicitar("UTI");
			aoMesmoTempo(() -> aceitar(administrativoB, solicitacao, utiB),
					() -> como(administrativoA, post("/internacoes/{id}/transferencia", internacaoId)
							.content("{\"leitoDestinoId\":\"%s\"}".formatted(enfermariaA))));
			assertThat(ocupacoesAbertas()).as("rodada %d: aceite x transferência direta", rodada).isOne();
			assertThat(jdbc.queryForObject("select count(*) from leito where id in (?, ?, ?) and status = 'OCUPADO'",
					Integer.class, leitoA, enfermariaA, utiB)).isOne();

			internarNoHospitalA();
			UUID outraSolicitacao = solicitar("UTI");
			UUID resumoAlta = resumoDeAlta();
			aoMesmoTempo(() -> aceitar(administrativoB, outraSolicitacao, utiB),
					() -> como(administrativoA, post("/internacoes/{id}/alta", internacaoId)
							.content("{\"resumoAltaId\":\"%s\"}".formatted(resumoAlta))));
			boolean deAlta = jdbc.queryForObject("select alta_em is not null from internacao where id = ?", Boolean.class,
					internacaoId);
			String status = jdbc.queryForObject("select status from solicitacao_transferencia where id = ?", String.class,
					outraSolicitacao);
			assertThat(ocupacoesAbertas()).as("rodada %d: aceite x alta", rodada).isEqualTo(deAlta ? 0 : 1);
			assertThat(status).isEqualTo(deAlta ? "CANCELADA" : "ACEITA");
			assertThat(statusDoLeito(utiB)).isEqualTo(deAlta ? "LIVRE" : "OCUPADO");
		}
	}

	/** Com a alta na origem, o pedido pendente é cancelado e o destino não pode mais aceitá-lo. */
	@Test
	void altaCancelaOPedidoPendente() throws Exception {
		UUID solicitacao = solicitar("UTI");
		String medico = cenario.profissional(hospitalA, Perfil.MEDICO);
		UUID resumoAlta = id(mvc.perform(post("/atendimentos/{id}/registros", atendimentoId)
						.with(user(medico).roles("MEDICO")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
						.content("{\"tipo\":\"RESUMO_ALTA\",\"conteudo\":\"Alta melhorado\",\"categoriaInformacao\":\"Clínica geral\"}"))
				.andExpect(status().isCreated()), "$.id");

		como(administrativoA, post("/internacoes/{id}/alta", internacaoId)
						.content("{\"resumoAltaId\":\"%s\"}".formatted(resumoAlta)))
				.andExpect(status().isOk());

		assertThat(jdbc.queryForObject("select status from solicitacao_transferencia where id = ?", String.class, solicitacao))
				.isEqualTo("CANCELADA");
		aceitar(administrativoB, solicitacao, utiB).andExpect(status().isUnprocessableContent());
	}

	private interface Requisicao {
		ResultActions executar() throws Exception;
	}

	/** Dispara as duas requisições juntas e espera as duas terminarem. */
	private void aoMesmoTempo(Requisicao primeira, Requisicao segunda) throws Exception {
		CountDownLatch largada = new CountDownLatch(1);
		ExecutorService threads = Executors.newFixedThreadPool(2);
		try {
			List<Future<ResultActions>> resultados = List.of(
					threads.submit(() -> { largada.await(); return primeira.executar(); }),
					threads.submit(() -> { largada.await(); return segunda.executar(); }));
			largada.countDown();
			for (Future<ResultActions> resultado : resultados) {
				int codigo = resultado.get(30, TimeUnit.SECONDS).andReturn().getResponse().getStatus();
				assertThat(codigo).as("nenhuma das duas termina em erro interno (deadlock, duas ocupações)").isLessThan(500);
			}
		} finally {
			threads.shutdownNow();
		}
	}

	private Integer ocupacoesAbertas() {
		return jdbc.queryForObject("select count(*) from ocupacao_leito where internacao_id = ? and fim_em is null",
				Integer.class, internacaoId);
	}

	private UUID resumoDeAlta() throws Exception {
		String medico = cenario.profissional(hospitalA, Perfil.MEDICO);
		return id(mvc.perform(post("/atendimentos/{id}/registros", atendimentoId)
						.with(user(medico).roles("MEDICO")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
						.content("{\"tipo\":\"RESUMO_ALTA\",\"conteudo\":\"Alta melhorado\",\"categoriaInformacao\":\"Clínica geral\"}"))
				.andExpect(status().isCreated()), "$.id");
	}

	private UUID solicitar(String categoria) throws Exception {
		return id(como(administrativoA, post("/internacoes/{id}/solicitacoes-transferencia", internacaoId)
						.content("{\"instituicaoDestinoId\":\"%s\",\"categoria\":\"%s\"}".formatted(hospitalB.getId(), categoria)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("PENDENTE")), "$.id");
	}

	private ResultActions aceitar(String login, UUID solicitacao, UUID leito) throws Exception {
		return como(login, post("/solicitacoes-transferencia/{id}/aceite", solicitacao)
				.content("{\"leitoId\":\"%s\"}".formatted(leito)));
	}

	private UUID leito(String administrativo, Instituicao hospital, String categoria) throws Exception {
		return id(como(administrativo, post("/leitos").content("""
				{"instituicaoId":"%s","codigo":"L-%s","setor":"Setor","categoria":"%s"}
				""".formatted(hospital.getId(), UUID.randomUUID().toString().substring(0, 8), categoria)))
				.andExpect(status().isCreated()), "$.id");
	}

	private String statusDoLeito(UUID leito) {
		return jdbc.queryForObject("select status from leito where id = ?", String.class, leito);
	}

	private ResultActions como(String login, MockHttpServletRequestBuilder requisicao) throws Exception {
		return mvc.perform(requisicao.with(user(login).roles("ADMINISTRATIVO")).with(csrf())
				.contentType(MediaType.APPLICATION_JSON));
	}

	private static UUID id(ResultActions resultado, String caminho) throws Exception {
		MvcResult feito = resultado.andReturn();
		return UUID.fromString(JsonPath.read(feito.getResponse().getContentAsString(), caminho));
	}
}
