package com.sigh.sigh.project.prontuario;

import com.jayway.jsonpath.JsonPath;
import com.sigh.sigh.project.instituicao.entity.Instituicao;
import com.sigh.sigh.project.support.Cenario;
import com.sigh.sigh.project.support.TesteIntegracao;
import com.sigh.sigh.project.triagem.entity.CorRisco;
import com.sigh.sigh.project.usuario.entity.Perfil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BE-18 (RF-010, RF-011, RN-003): controles contra o abuso do acesso de emergência, decididos pelo
 * grupo em 20/11/2026, sem teto numérico de acessos:
 * <ul>
 * <li>gravidade: só com a classificação de risco confirmada mais recente vermelha ou laranja;</li>
 * <li>janela: só até 12 horas depois dessa classificação;</li>
 * <li>revisão obrigatória: todo acesso de emergência fica na fila do auditor até uma análise conclusiva.</li>
 * </ul>
 * E os do BE-24: reclassificação durante o atendimento, quem classifica não usa a emergência com base
 * na própria classificação, e a tentativa recusada fica na auditoria.
 */
class EmergenciaControladaTest extends TesteIntegracao {

	private static final String JUSTIFICATIVA = "{\"justificativa\":\"Parada cardiorrespiratória na sala vermelha\"}";

	@Autowired
	JdbcTemplate jdbc;

	private Instituicao hospital;
	private UUID pacienteId;
	private UUID atendimentoId;
	private String medico;

	@BeforeEach
	void admitirPaciente() throws Exception {
		hospital = cenario.instituicao(true);
		MvcResult admissao = mvc.perform(post("/api/pacientes/admissoes")
						.with(user(cenario.administrativo(hospital)).roles("ADMINISTRATIVO")).with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"cpf":"%s","nome":"Maria Fictícia","dataNascimento":"1980-05-17",
								 "consentimentoInicial":"AUTORIZADO","instituicaoId":"%s","motivo":"Dor no peito"}
								""".formatted(Cenario.novoCpf(), hospital.getId())))
				.andExpect(status().isCreated()).andReturn();
		pacienteId = UUID.fromString(JsonPath.read(admissao.getResponse().getContentAsString(), "$.pacienteId"));
		atendimentoId = UUID.fromString(JsonPath.read(admissao.getResponse().getContentAsString(), "$.atendimentoId"));
		medico = cenario.profissional(hospital, Perfil.MEDICO);
	}

	@Test
	void semClassificacaoVermelhaOuLaranjaNaoHaEmergencia() throws Exception {
		emergencia().andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.detail").value(containsString("vermelha ou laranja")));

		cenario.classificarRisco(atendimentoId, hospital, CorRisco.AMARELO);
		emergencia().andExpect(status().isUnprocessableContent());

		assertThat(acessosDeEmergencia()).as("acesso recusado não é registrado como emergência").isZero();
		assertThat(tentativasRecusadas("sem classificação vermelha ou laranja confirmada"))
				.as("mas a tentativa recusada fica na auditoria, com o motivo (BE-24)").isEqualTo(2);
		String auditor = cenario.usuario(Perfil.AUDITOR_GESTOR).getLogin();
		mvc.perform(get("/auditoria/indicadores").param("inicio", "2026-11-10").param("fim", "2026-11-10")
						.with(user(auditor).roles("AUDITOR_GESTOR")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.instituicaoId == '" + hospital.getId() + "')]").isEmpty());
	}

	/**
	 * BE-24: o paciente já com o médico pode ser reclassificado. O que piora durante o atendimento (ou
	 * nunca passou por triagem) ganha a emergência; a janela recomeça a cada reclassificação.
	 */
	@Test
	void reclassificacaoDuranteOAtendimentoRenovaAEmergencia() throws Exception {
		mvc.perform(post("/atendimentos/{id}/registros", atendimentoId).with(user(medico).roles("MEDICO")).with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"tipo\":\"EVOLUCAO\",\"conteudo\":\"Estável\",\"categoriaInformacao\":\"Clínica geral\"}"))
				.andExpect(status().isCreated());
		assertThat(statusDoAtendimento()).isEqualTo("EM_ATENDIMENTO");
		emergencia().andExpect(status().isUnprocessableContent());

		cenario.classificarRisco(atendimentoId, hospital, CorRisco.VERMELHO);
		assertThat(statusDoAtendimento()).as("a reclassificação não devolve o paciente para a fila").isEqualTo("EM_ATENDIMENTO");
		emergencia().andExpect(status().isOk());

		relogio.avancar(Duration.ofHours(13));
		emergencia().andExpect(status().isUnprocessableContent());
		cenario.classificarRisco(atendimentoId, hospital, CorRisco.LARANJA);
		emergencia().andExpect(status().isOk());
	}

	/** BE-24: quem confirmou a classificação não usa a emergência com base nela; outra pessoa, sim. */
	@Test
	void quemConfirmouAClassificacaoNaoUsaAEmergenciaComBaseNela() throws Exception {
		String enfermeiro = cenario.profissional(hospital, Perfil.ENFERMEIRO);
		cenario.classificarRisco(atendimentoId, CorRisco.VERMELHO, enfermeiro);

		emergencia(enfermeiro, "ENFERMEIRO").andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.detail").value(containsString("Quem confirmou a classificação")));
		assertThat(tentativasRecusadas("quem confirmou a classificação tentou usá-la")).isOne();

		emergencia(cenario.profissional(hospital, Perfil.ENFERMEIRO), "ENFERMEIRO").andExpect(status().isOk());
		emergencia().andExpect(status().isOk());
	}

	/** Classificação sem confirmador identificado (só por dado gravado fora da aplicação) não libera nada. */
	@Test
	void classificacaoSemConfirmadorNaoLiberaAEmergencia() throws Exception {
		UUID triagem = cenario.classificarRisco(atendimentoId, hospital, CorRisco.VERMELHO);
		jdbc.update("update triagem set confirmador_id = null where id = ?", triagem);

		emergencia().andExpect(status().isUnprocessableContent());
	}

	/** A regra olha quem confirmou a classificação, não quem registrou a triagem. */
	@Test
	void valeQuemConfirmouENaoQuemRegistrouATriagem() throws Exception {
		String registrou = cenario.profissional(hospital, Perfil.ENFERMEIRO);
		String confirmou = cenario.profissional(hospital, Perfil.ENFERMEIRO);
		cenario.classificarRisco(atendimentoId, CorRisco.VERMELHO, registrou, confirmou);

		emergencia(confirmou, "ENFERMEIRO").andExpect(status().isUnprocessableContent());
		emergencia(registrou, "ENFERMEIRO").andExpect(status().isOk());
	}

	@Test
	void vermelhoOuLaranjaLiberamSoAte12HorasDepoisDaClassificacao() throws Exception {
		cenario.classificarRisco(atendimentoId, hospital, CorRisco.LARANJA);
		emergencia().andExpect(status().isOk());

		relogio.avancar(Duration.ofHours(12));
		emergencia().andExpect(status().isOk());

		relogio.avancar(Duration.ofMinutes(1));
		emergencia().andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.detail").value(containsString("12 horas")));
		assertThat(acessosDeEmergencia()).isEqualTo(2);
		assertThat(tentativasRecusadas("fora da janela depois da classificação")).isOne();
	}

	/** Vale a classificação mais recente: o paciente que melhora perde a emergência, o que piora a recupera. */
	@Test
	void valeAClassificacaoMaisRecente() throws Exception {
		cenario.classificarRisco(atendimentoId, hospital, CorRisco.VERMELHO);
		relogio.avancar(Duration.ofHours(1));
		cenario.classificarRisco(atendimentoId, hospital, CorRisco.VERDE);
		emergencia().andExpect(status().isUnprocessableContent());

		relogio.avancar(Duration.ofHours(13));
		cenario.classificarRisco(atendimentoId, hospital, CorRisco.VERMELHO);
		emergencia().andExpect(status().isOk());
	}

	/** Todo acesso de emergência entra na fila do auditor e só sai com uma análise conclusiva. */
	@Test
	void acessoDeEmergenciaFicaNaFilaDoAuditorAteAAnaliseConclusiva() throws Exception {
		cenario.classificarRisco(atendimentoId, hospital, CorRisco.VERMELHO);
		emergencia().andExpect(status().isOk());
		// Consulta comum, com um registro a ver (é o acesso a registros que vai para a auditoria): não entra na fila.
		cenario.registroClinico(pacienteId, hospital, "Cardiologia", "Hipertensão arterial");
		mvc.perform(get("/atendimentos/{id}/historico", atendimentoId).with(user(medico).roles("MEDICO")))
				.andExpect(status().isOk());
		UUID registro = jdbc.queryForObject("select id from registro_auditoria where emergencia and prontuario_id ="
				+ " (select id from prontuario where paciente_id = ?)", UUID.class, pacienteId);
		UUID consultaComum = jdbc.queryForObject("select id from registro_auditoria where not emergencia and prontuario_id ="
				+ " (select id from prontuario where paciente_id = ?)", UUID.class, pacienteId);
		String auditor = cenario.usuario(Perfil.AUDITOR_GESTOR).getLogin();

		Map<String, String> pendentes = filaInteira(auditor);
		assertThat(pendentes).containsEntry(registro.toString(), "Parada cardiorrespiratória na sala vermelha")
				.doesNotContainKey(consultaComum.toString());

		analisar(auditor, registro, "PENDENTE");
		assertThat(filaInteira(auditor)).containsKey(registro.toString());

		analisar(auditor, registro, "REGULAR");
		assertThat(filaInteira(auditor)).doesNotContainKey(registro.toString());

		mvc.perform(get("/auditoria/emergencias/pendentes").with(user(medico).roles("MEDICO")))
				.andExpect(status().isForbidden());
	}

	private ResultActions emergencia() throws Exception {
		return emergencia(medico, "MEDICO");
	}

	private ResultActions emergencia(String login, String perfil) throws Exception {
		return mvc.perform(post("/atendimentos/{id}/historico/emergencia", atendimentoId)
				.with(user(login).roles(perfil)).with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content(JUSTIFICATIVA));
	}

	/** Tentativas recusadas na auditoria do paciente, com o motivo exato. */
	private Integer tentativasRecusadas(String motivo) {
		return jdbc.queryForObject("select count(*) from registro_auditoria where not emergencia and prontuario_id ="
				+ " (select id from prontuario where paciente_id = ?) and finalidade_declarada = ?", Integer.class,
				pacienteId, "Tentativa de acesso de emergência recusada: " + motivo);
	}

	private String statusDoAtendimento() {
		return jdbc.queryForObject("select status from atendimento where id = ?", String.class, atendimentoId);
	}

	/**
	 * Id e justificativa de todos os acessos da fila, página a página: o banco é compartilhado com os
	 * outros testes, então o acesso deste teste pode estar em qualquer página.
	 */
	private Map<String, String> filaInteira(String auditor) throws Exception {
		Map<String, String> pendentes = new LinkedHashMap<>();
		for (int pagina = 0; ; pagina++) {
			String corpo = mvc.perform(get("/auditoria/emergencias/pendentes").param("page", String.valueOf(pagina))
							.param("size", "100").with(user(auditor).roles("AUDITOR_GESTOR")))
					.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
			List<Map<String, Object>> conteudo = JsonPath.read(corpo, "$.content");
			if (conteudo.isEmpty()) {
				return pendentes;
			}
			conteudo.forEach(item -> pendentes.put((String) item.get("id"), (String) item.get("justificativa")));
		}
	}

	private void analisar(String auditor, UUID registro, String conclusao) throws Exception {
		mvc.perform(post("/auditoria/registros/{id}/analises", registro)
						.with(user(auditor).roles("AUDITOR_GESTOR")).with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"tipo\":\"PARECER\",\"conclusao\":\"%s\",\"descricao\":\"Conferido com a equipe.\"}"
								.formatted(conclusao)))
				.andExpect(status().isCreated());
	}

	private Integer acessosDeEmergencia() {
		return jdbc.queryForObject("select count(*) from registro_auditoria where emergencia and prontuario_id ="
				+ " (select id from prontuario where paciente_id = ?)", Integer.class, pacienteId);
	}
}
