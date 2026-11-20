package com.sigh.sigh.project.paciente;

import com.jayway.jsonpath.JsonPath;
import com.sigh.sigh.project.auditoria.repository.RegistroAuditoriaRepository;
import com.sigh.sigh.project.instituicao.entity.Instituicao;
import com.sigh.sigh.project.paciente.entity.Consentimento;
import com.sigh.sigh.project.paciente.repository.ConsentimentoRepository;
import com.sigh.sigh.project.support.Cenario;
import com.sigh.sigh.project.support.TesteIntegracao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Rotas do titular e do representante legal sobre os dados do paciente: histórico (BE-16),
 * restrições e autorizações adicionais (BE-07) e consentimento pelo representante (BE-15).
 */
class DadosDoPacienteTest extends TesteIntegracao {

	@Autowired
	ConsentimentoRepository consentimentoRepository;

	@Autowired
	TransactionTemplate transacao;

	@Autowired
	RegistroAuditoriaRepository registroAuditoriaRepository;

	private Instituicao hospital;
	private UUID pacienteId;
	private String titular;
	private UUID registro;

	@BeforeEach
	void prepararPaciente() throws Exception {
		hospital = cenario.instituicao(true);
		pacienteId = admitir(hospital, Cenario.novoCpf());
		titular = cenario.contaDoPaciente(pacienteId);
		registro = cenario.registroClinico(pacienteId, hospital, "Saúde mental", "Transtorno de ansiedade");
	}

	@Test
	void titularConsultaOProprioHistoricoComAMarcacaoDoQueEstaRestrito() throws Exception {
		comoPaciente(get("/api/pacientes/{id}/historico", pacienteId), titular)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value(registro.toString()))
				.andExpect(jsonPath("$[0].instituicaoOrigem").value(hospital.getNome()))
				.andExpect(jsonPath("$[0].restrito").value(false));

		restringir(titular, "{\"categoria\":\"Saúde mental\"}").andExpect(status().isCreated());

		comoPaciente(get("/api/pacientes/{id}/historico", pacienteId), titular)
				.andExpect(jsonPath("$[0].restrito").value(true));
	}

	@Test
	void restricaoTemCicloDeVidaSemApagarNada() throws Exception {
		String restricao = restringir(titular, "{\"categoria\":\"  Saúde mental  \"}")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.categoria").value("Saúde mental"))
				.andExpect(jsonPath("$.vigente").value(true))
				.andReturn().getResponse().getContentAsString();
		restringir(titular, "{\"categoria\":\"SAÚDE MENTAL\"}").andExpect(status().isUnprocessableContent());

		relogio.avancar(Duration.ofMinutes(10));
		String id = JsonPath.read(restricao, "$.id");
		encerrar(id, titular).andExpect(status().isOk()).andExpect(jsonPath("$.vigente").value(false));
		encerrar(id, titular).andExpect(status().isUnprocessableContent());

		restringir(titular, "{\"categoria\":\"Saúde mental\"}").andExpect(status().isCreated());
		comoPaciente(get("/api/pacientes/{id}/restricoes", pacienteId), titular)
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[*].vigente").value(containsInAnyOrder(true, false)));
	}

	@Test
	void alvoDaRestricaoPrecisaSerUmaCategoriaOuUmRegistroDoProprioProntuario() throws Exception {
		restringir(titular, "{}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.campos.alvo").exists());
		restringir(titular, "{\"categoria\":\"Saúde mental\",\"registroId\":\"%s\"}".formatted(registro))
				.andExpect(status().isBadRequest());
		restringir(titular, "{\"categoria\":\"%s\"}".formatted("x".repeat(51))).andExpect(status().isBadRequest());

		UUID outroPaciente = admitir(hospital, Cenario.novoCpf());
		UUID registroDeOutro = cenario.registroClinico(outroPaciente, hospital, "Clínica geral", "Gripe");
		restringir(titular, "{\"registroId\":\"%s\"}".formatted(registroDeOutro)).andExpect(status().isNotFound());
		restringir(titular, "{\"registroId\":\"%s\"}".formatted(registro)).andExpect(status().isCreated());
	}

	@Test
	void outroPacienteNaoVeNemMexeNosDadosDoTitular() throws Exception {
		String intruso = cenario.contaDoPaciente(admitir(hospital, Cenario.novoCpf()));

		comoPaciente(get("/api/pacientes/{id}/historico", pacienteId), intruso).andExpect(status().isForbidden());
		comoPaciente(get("/api/pacientes/{id}/restricoes", pacienteId), intruso).andExpect(status().isForbidden());
		comoPaciente(get("/api/pacientes/{id}/autorizacoes", pacienteId), intruso).andExpect(status().isForbidden());
		comoPaciente(get("/api/pacientes/{id}/consentimentos", pacienteId), intruso).andExpect(status().isForbidden());
		restringir(intruso, "{\"categoria\":\"Saúde mental\"}").andExpect(status().isForbidden());
		comoPaciente(get("/api/pacientes/{id}/historico", UUID.randomUUID()), titular).andExpect(status().isForbidden());

		mvc.perform(get("/api/pacientes/{id}/historico", pacienteId).with(user(titular).roles("MEDICO")))
				.andExpect(status().isForbidden());
	}

	@Test
	void representanteVigenteGerenciaEDepoisDoFimPerdeOAcesso() throws Exception {
		LocalDateTime agora = LocalDateTime.now(relogio);
		String representante = cenario.representante(pacienteId, agora.minusDays(1), agora.plusDays(1));
		String futuro = cenario.representante(pacienteId, agora.plusDays(1), null);

		comoPaciente(get("/api/pacientes/gerenciados"), representante)
				.andExpect(jsonPath("$[*].relacao").value(containsInAnyOrder("REPRESENTANTE_LEGAL")))
				.andExpect(jsonPath("$[0].pacienteId").value(pacienteId.toString()));
		comoPaciente(get("/api/pacientes/{id}/historico", pacienteId), representante).andExpect(status().isOk());
		restringir(representante, "{\"categoria\":\"Saúde mental\"}")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.definidaPor").value("Usuário Fictício"));
		comoPaciente(get("/api/pacientes/{id}/historico", pacienteId), futuro).andExpect(status().isForbidden());

		relogio.avancar(Duration.ofDays(2));
		comoPaciente(get("/api/pacientes/{id}/historico", pacienteId), representante).andExpect(status().isForbidden());
		comoPaciente(get("/api/pacientes/gerenciados"), representante).andExpect(jsonPath("$.length()").value(0));
	}

	/** A leitura do prontuário pelo representante fica na auditoria; a do próprio titular, não. */
	@Test
	void leituraPeloRepresentanteFicaNaAuditoria() throws Exception {
		LocalDateTime agora = LocalDateTime.now(relogio);
		String representante = cenario.representante(pacienteId, agora.minusDays(1), null);
		long antes = registroAuditoriaRepository.count();

		comoPaciente(get("/api/pacientes/{id}/historico", pacienteId), titular).andExpect(status().isOk());
		assertThat(registroAuditoriaRepository.count()).isEqualTo(antes);

		comoPaciente(get("/api/pacientes/{id}/historico", pacienteId), representante).andExpect(status().isOk());
		comoPaciente(get("/auditoria/meus-acessos"), titular)
				.andExpect(jsonPath("$.content[0].finalidade").value("Consulta do histórico pelo representante legal"))
				.andExpect(jsonPath("$.content[0].perfil").value("PACIENTE"));
	}

	@Test
	void representanteRevogaOConsentimentoEFicaComoOutorgante() throws Exception {
		LocalDateTime agora = LocalDateTime.now(relogio);
		String representante = cenario.representante(pacienteId, agora.minusDays(1), null);

		comoPaciente(post("/api/pacientes/{id}/consentimentos", pacienteId).with(csrf())
						.contentType(MediaType.APPLICATION_JSON).content("{\"decisao\":\"REVOGADO\"}"), representante)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.decisao").value("REVOGADO"));
		comoPaciente(get("/api/pacientes/{id}/consentimentos", pacienteId), titular)
				.andExpect(jsonPath("$.vigente.decisao").value("REVOGADO"));

		String outorgante = transacao.execute(status -> {
			List<Consentimento> historico = consentimentoRepository.historico(pacienteId);
			return historico.getLast().getOutorgante().getLogin();
		});
		assertThat(outorgante).isEqualTo(representante);
	}

	@Test
	void autorizacaoAdicionalExigeInstituicaoCredenciadaENaoSeRepete() throws Exception {
		Instituicao credenciada = cenario.instituicao(true);
		Instituicao naoCredenciada = cenario.instituicao(false);

		autorizar(naoCredenciada, "{\"categoria\":\"Saúde mental\"").andExpect(status().isUnprocessableContent());
		autorizar(UUID.randomUUID(), "{\"categoria\":\"Saúde mental\"").andExpect(status().isUnprocessableContent());
		String concedida = autorizar(credenciada, "{\"registroId\":\"%s\"".formatted(registro))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.registroId").value(registro.toString()))
				.andExpect(jsonPath("$.instituicao").value(credenciada.getNome()))
				.andReturn().getResponse().getContentAsString();
		autorizar(credenciada, "{\"registroId\":\"%s\"".formatted(registro)).andExpect(status().isUnprocessableContent());

		comoPaciente(get("/api/pacientes/{id}/autorizacoes", pacienteId), titular)
				.andExpect(jsonPath("$.length()").value(1));
		String id = JsonPath.read(concedida, "$.id");
		comoPaciente(post("/api/pacientes/{id}/autorizacoes/{a}/revogacao", pacienteId, id).with(csrf()), titular)
				.andExpect(status().isCreated());
		comoPaciente(post("/api/pacientes/{id}/autorizacoes/{a}/revogacao", pacienteId, id).with(csrf()), titular)
				.andExpect(status().isNotFound());
		comoPaciente(get("/api/pacientes/{id}/autorizacoes", pacienteId), titular)
				.andExpect(jsonPath("$.length()").value(0));
	}

	private ResultActions restringir(String login, String corpo) throws Exception {
		return comoPaciente(post("/api/pacientes/{id}/restricoes", pacienteId).with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content(corpo), login);
	}

	private ResultActions encerrar(String restricaoId, String login) throws Exception {
		return comoPaciente(post("/api/pacientes/{id}/restricoes/{r}/encerramento", pacienteId, restricaoId)
				.with(csrf()), login);
	}

	private ResultActions autorizar(Instituicao instituicao, String inicioDoCorpo) throws Exception {
		return autorizar(instituicao.getId(), inicioDoCorpo);
	}

	private ResultActions autorizar(UUID instituicaoId, String inicioDoCorpo) throws Exception {
		return comoPaciente(post("/api/pacientes/{id}/autorizacoes", pacienteId).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(inicioDoCorpo + ",\"instituicaoId\":\"%s\"}".formatted(instituicaoId)), titular);
	}

	private ResultActions comoPaciente(MockHttpServletRequestBuilder requisicao, String login) throws Exception {
		return mvc.perform(requisicao.with(user(login).roles("PACIENTE")));
	}

	private UUID admitir(Instituicao instituicao, String cpf) throws Exception {
		MvcResult resultado = mvc.perform(post("/api/pacientes/admissoes")
						.with(user(cenario.administrativo(instituicao)).roles("ADMINISTRATIVO")).with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"cpf":"%s","nome":"Maria Fictícia","dataNascimento":"1980-05-17",
								 "consentimentoInicial":"AUTORIZADO","instituicaoId":"%s","motivo":"Consulta"}
								""".formatted(cpf, instituicao.getId())))
				.andExpect(status().isCreated()).andReturn();
		return UUID.fromString(JsonPath.read(resultado.getResponse().getContentAsString(), "$.pacienteId"));
	}
}
