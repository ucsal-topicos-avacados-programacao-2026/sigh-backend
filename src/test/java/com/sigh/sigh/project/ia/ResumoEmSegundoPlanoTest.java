package com.sigh.sigh.project.ia;

import com.jayway.jsonpath.JsonPath;
import com.sigh.sigh.project.comum.erro.IAIndisponivelException;
import com.sigh.sigh.project.ia.entity.StatusResumo;
import com.sigh.sigh.project.ia.repository.ResumoHistoricoIARepository;
import com.sigh.sigh.project.ia.service.ClienteIA;
import com.sigh.sigh.project.ia.service.FilaDeResumosIA;
import com.sigh.sigh.project.ia.service.RecuperacaoDosResumos;
import com.sigh.sigh.project.ia.service.ResumoHistoricoService;
import com.sigh.sigh.project.instituicao.entity.Instituicao;
import com.sigh.sigh.project.support.Cenario;
import com.sigh.sigh.project.support.TesteIntegracao;
import com.sigh.sigh.project.usuario.entity.Perfil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BE-25 (RF-004, RNF-001): o resumo é gerado em segundo plano, um por vez, com a fila de verdade. A IA
 * simulada fica presa até o teste soltá-la, como um modelo que demora a responder.
 */
@TestPropertySource(properties = "sigh.ia.fila-maxima-resumos=1")
class ResumoEmSegundoPlanoTest extends TesteIntegracao {

	private static final String CONSULTA_DE_RESUMO = "Consulta de resumo gerado por IA";

	@MockitoBean
	ClienteIA clienteIA;

	@Autowired
	ResumoHistoricoIARepository resumoHistoricoIARepository;

	@MockitoSpyBean
	ResumoHistoricoService resumoHistoricoService;

	@Autowired
	FilaDeResumosIA filaDeResumos;

	@Autowired
	RecuperacaoDosResumos recuperacaoDosResumos;

	@Autowired
	JdbcTemplate jdbc;

	private final CountDownLatch soltarIA = new CountDownLatch(1);
	private final AtomicInteger emAndamento = new AtomicInteger();
	private final AtomicInteger maximoAoMesmoTempo = new AtomicInteger();
	private final AtomicInteger chamadas = new AtomicInteger();
	private Instituicao hospital;
	private String medico;

	@BeforeEach
	void iaQueDemora() {
		when(clienteIA.resumirHistorico(anyString())).thenAnswer(chamada -> {
			int agora = emAndamento.incrementAndGet();
			maximoAoMesmoTempo.accumulateAndGet(agora, Math::max);
			chamadas.incrementAndGet();
			try {
				assertThat(soltarIA.await(30, TimeUnit.SECONDS)).as("o teste solta a IA").isTrue();
				return "Resumo fictício.";
			} finally {
				emAndamento.decrementAndGet();
			}
		});
		hospital = cenario.instituicao(true);
		medico = cenario.profissional(hospital, Perfil.MEDICO);
	}

	@AfterEach
	void soltarOQueFicou() throws Exception {
		soltarIA.countDown();
		esperar(() -> doHospital("SOLICITADO") == 0);
	}

	@Test
	void pedidoVoltaNaHoraEmGeracaoEAConsultaSoEntraNaAuditoriaQuandoHaTexto() throws Exception {
		UUID atendimentoId = atendimentoComHistorico();

		UUID resumoId = idDe(pedir(atendimentoId)
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.status").value("SOLICITADO"))
				.andExpect(jsonPath("$.texto").doesNotExist())
				.andExpect(jsonPath("$.aviso").value(org.hamcrest.Matchers.containsString("em geração"))));

		como(get("/resumos/{id}", resumoId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("SOLICITADO"))
				.andExpect(jsonPath("$.texto").doesNotExist())
				.andExpect(jsonPath("$.aviso").value(org.hamcrest.Matchers.containsString("em geração")));
		assertThat(consultasDeResumoNaAuditoria()).as("consulta sem texto não entra na auditoria").isZero();

		soltarIA.countDown();
		esperar(() -> statusDo(resumoId) == StatusResumo.GERADO);

		como(get("/resumos/{id}", resumoId))
				.andExpect(jsonPath("$.status").value("GERADO"))
				.andExpect(jsonPath("$.texto").value("Resumo fictício."))
				.andExpect(jsonPath("$.aviso").value(org.hamcrest.Matchers.containsString("sujeito à validação")));
		assertThat(consultasDeResumoNaAuditoria()).isEqualTo(1);
	}

	@Test
	void resumosPedidosAoMesmoTempoSaoGeradosUmPorVezEFilaCheiaRecusaNaHora() throws Exception {
		// Fila de 1: um em geração, um esperando, e o terceiro é recusado.
		UUID primeiro = idDe(pedir(atendimentoComHistorico()).andExpect(status().isAccepted()));
		esperar(() -> chamadas.get() == 1);
		UUID segundo = idDe(pedir(atendimentoComHistorico()).andExpect(status().isAccepted()));
		long falhosAntes = doHospital("FALHOU");

		pedir(atendimentoComHistorico())
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("fila")));
		assertThat(doHospital("FALHOU")).as("o recusado fica registrado como falho").isEqualTo(falhosAntes + 1);
		assertThat(statusDo(segundo)).isEqualTo(StatusResumo.SOLICITADO);

		soltarIA.countDown();
		esperar(() -> statusDo(primeiro) == StatusResumo.GERADO && statusDo(segundo) == StatusResumo.GERADO);
		assertThat(maximoAoMesmoTempo).as("nunca dois resumos ao mesmo tempo na IA").hasValue(1);
	}

	@Test
	void falhaDaIANoSegundoPlanoDeixaOResumoFalhoComAviso() throws Exception {
		// doThrow: um when(...) aqui chamaria a IA simulada do @BeforeEach, que fica presa.
		doThrow(new IAIndisponivelException("O serviço de IA não respondeu.")).when(clienteIA).resumirHistorico(anyString());

		UUID resumoId = idDe(pedir(atendimentoComHistorico()).andExpect(status().isAccepted()));
		esperar(() -> statusDo(resumoId) == StatusResumo.FALHOU);

		como(get("/resumos/{id}", resumoId))
				.andExpect(jsonPath("$.status").value("FALHOU"))
				.andExpect(jsonPath("$.texto").doesNotExist())
				.andExpect(jsonPath("$.aviso").value(org.hamcrest.Matchers.containsString("não gerou")));
	}

	/** RF-009: o paciente restringe um registro enquanto o pedido espera na fila; o histórico não vai à IA. */
	@Test
	void restricaoFeitaComOPedidoNaFilaImpedeQueOHistoricoVaAIA() throws Exception {
		UUID emGeracao = idDe(pedir(atendimentoComHistorico()).andExpect(status().isAccepted()));
		esperar(() -> chamadas.get() == 1);
		Caso naFila = caso();
		UUID esperando = idDe(pedir(naFila.atendimentoId()).andExpect(status().isAccepted()));

		mvc.perform(post("/api/pacientes/{id}/restricoes", naFila.pacienteId())
						.with(user(cenario.contaDoPaciente(naFila.pacienteId())).roles("PACIENTE")).with(csrf())
						.contentType(MediaType.APPLICATION_JSON).content("{\"categoria\":\"Clínica geral\"}"))
				.andExpect(status().isCreated());
		soltarIA.countDown();

		esperar(() -> statusDo(emGeracao) == StatusResumo.GERADO && statusDo(esperando) == StatusResumo.FALHOU);
		assertThat(chamadas).as("o histórico restrito não foi mandado à IA").hasValue(1);
	}

	/** Banco fora quando a IA responde: nem o texto nem a falha são gravados, mas o resumo não fica preso. */
	@Test
	void resumoQueNaoConseguiuGravarOResultadoApareceComoFalhoNaConsulta() throws Exception {
		doThrow(new IllegalStateException("banco fora")).when(resumoHistoricoService).concluir(any(), anyString());
		doThrow(new IllegalStateException("banco fora")).when(resumoHistoricoService).registrarFalha(any());
		soltarIA.countDown();

		UUID resumoId = idDe(pedir(atendimentoComHistorico()).andExpect(status().isAccepted()));
		esperar(() -> chamadas.get() == 1 && !filaDeResumos.pendente(resumoId));
		assertThat(statusDo(resumoId)).as("nada foi gravado").isEqualTo(StatusResumo.SOLICITADO);

		como(get("/resumos/{id}", resumoId))
				.andExpect(jsonPath("$.status").value("FALHOU"))
				.andExpect(jsonPath("$.aviso").value(org.hamcrest.Matchers.containsString("não gerou")));
		assertThat(statusDo(resumoId)).isEqualTo(StatusResumo.FALHOU);
	}

	/** Outra instância deu o resumo como falho enquanto esta o gerava: o texto que chega depois é descartado. */
	@Test
	void textoQueChegaDepoisDeOResumoSerDadoComoFalhoEhDescartado() throws Exception {
		UUID resumoId = idDe(pedir(atendimentoComHistorico()).andExpect(status().isAccepted()));
		esperar(() -> chamadas.get() == 1);

		recuperacaoDosResumos.afterSingletonsInstantiated();
		soltarIA.countDown();

		esperar(() -> !filaDeResumos.pendente(resumoId));
		assertThat(statusDo(resumoId)).isEqualTo(StatusResumo.FALHOU);
	}

	@Test
	void resumoQueEstavaNaFilaQuandoAAplicacaoParouViraFalhoAoSubir() {
		UUID atendimentoId = atendimentoComHistorico();
		// Pedido gravado, mas que nunca chegou à fila: é o que sobra de uma fila perdida quando a aplicação para.
		UUID resumoId = resumoHistoricoService.solicitar(atendimentoId, medico).resumoId();
		assertThat(statusDo(resumoId)).isEqualTo(StatusResumo.SOLICITADO);

		recuperacaoDosResumos.afterSingletonsInstantiated();

		assertThat(statusDo(resumoId)).isEqualTo(StatusResumo.FALHOU);
	}

	private record Caso(UUID atendimentoId, UUID pacienteId) {
	}

	private UUID atendimentoComHistorico() {
		return caso().atendimentoId();
	}

	private Caso caso() {
		try {
			MvcResult admissao = mvc.perform(post("/api/pacientes/admissoes")
							.with(user(cenario.administrativo(hospital)).roles("ADMINISTRATIVO")).with(csrf())
							.contentType(MediaType.APPLICATION_JSON)
							.content("""
									{"cpf":"%s","nome":"Paciente Fictício","dataNascimento":"1980-05-17",
									 "consentimentoInicial":"AUTORIZADO","instituicaoId":"%s","motivo":"Febre"}
									""".formatted(Cenario.novoCpf(), hospital.getId())))
					.andExpect(status().isCreated()).andReturn();
			String corpo = admissao.getResponse().getContentAsString();
			UUID pacienteId = UUID.fromString(JsonPath.read(corpo, "$.pacienteId"));
			cenario.registroClinico(pacienteId, hospital, "Clínica geral", "Asma");
			return new Caso(UUID.fromString(JsonPath.read(corpo, "$.atendimentoId")), pacienteId);
		} catch (Exception falha) {
			throw new IllegalStateException(falha);
		}
	}

	private ResultActions pedir(UUID atendimentoId) throws Exception {
		return como(post("/atendimentos/{id}/resumos", atendimentoId));
	}

	private ResultActions como(MockHttpServletRequestBuilder pedido) throws Exception {
		return mvc.perform(pedido.with(user(medico).roles("MEDICO")).with(csrf()));
	}

	private static UUID idDe(ResultActions resposta) throws Exception {
		return UUID.fromString(JsonPath.read(resposta.andReturn().getResponse().getContentAsString(), "$.id"));
	}

	private StatusResumo statusDo(UUID resumoId) {
		return resumoHistoricoIARepository.findById(resumoId).orElseThrow().getStatus();
	}

	/** Resumos do hospital deste teste com o status: os de outras classes de teste não interferem. */
	private long doHospital(String status) {
		return jdbc.queryForObject("select count(*) from resumo_historico_ia r join atendimento a on a.id = r.atendimento_id"
				+ " where a.instituicao_id = ? and r.status = ?", Long.class, hospital.getId(), status);
	}

	private long consultasDeResumoNaAuditoria() {
		return jdbc.queryForObject("select count(*) from registro_auditoria r join usuario u on u.id = r.ator_id"
				+ " where u.login = ? and r.finalidade_declarada = ?", Long.class, medico, CONSULTA_DE_RESUMO);
	}

	@FunctionalInterface
	private interface Condicao {
		boolean valida() throws Exception;
	}

	private static void esperar(Condicao condicao) throws Exception {
		List<Throwable> erros = new ArrayList<>();
		for (int tentativa = 0; tentativa < 200; tentativa++) {
			try {
				if (condicao.valida()) {
					return;
				}
			} catch (RuntimeException erro) {
				erros.add(erro);
			}
			Thread.sleep(50);
		}
		throw new AssertionError("A condição não foi atingida em 10 s. " + erros.stream().findFirst().map(Throwable::toString).orElse(""));
	}
}
