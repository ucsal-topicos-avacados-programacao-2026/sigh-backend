package com.sigh.sigh.project.triagem;

import com.jayway.jsonpath.JsonPath;
import com.sigh.sigh.project.ia.service.ClienteIA;
import com.sigh.sigh.project.instituicao.entity.Instituicao;
import com.sigh.sigh.project.support.Cenario;
import com.sigh.sigh.project.support.TesteIntegracao;
import com.sigh.sigh.project.triagem.entity.CorRisco;
import com.sigh.sigh.project.usuario.entity.Perfil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PreClassificacaoNaAdmissaoTest extends TesteIntegracao {

	@Autowired
	JdbcTemplate jdbc;

	@MockitoBean
	ClienteIA clienteIA;

	private Instituicao hospital;
	private String recepcao;
	private String enfermeiro;

	@BeforeEach
	void cenario() {
		hospital = cenario.instituicao(true);
		recepcao = cenario.recepcao(hospital);
		enfermeiro = cenario.profissional(hospital, Perfil.ENFERMEIRO);
	}

	@Test
	void admissaoAbreATriagemComOQueOPacienteSenteESemSinaisVitais() throws Exception {
		when(clienteIA.classificarRisco(anyString())).thenReturn(CorRisco.LARANJA);

		UUID atendimento = admitir(recepcao, hospital, Cenario.novoCpf(), "Dor no peito há uma hora", "AUTORIZADO");
		Map<String, Object> triagem = triagemDe(atendimento);

		assertThat(triagem).containsEntry("pressao_arterial", null).containsEntry("saturacao_oxigenio", null)
				.containsEntry("cor_confirmada", null).containsEntry("sugestao_preliminar", true);
		assertThat(jdbc.queryForObject("select u.login from triagem t join usuario u on u.id = t.registrador_id where t.atendimento_id = ?",
				String.class, atendimento)).isEqualTo(recepcao);
	}

	@Test
	void aIASugereEmSegundoPlanoEOEnfermeiroVeASugestaoPreliminar() throws Exception {
		when(clienteIA.classificarRisco(anyString())).thenReturn(CorRisco.LARANJA);

		UUID atendimento = admitir(recepcao, hospital, Cenario.novoCpf(), "Dor no peito há uma hora", "AUTORIZADO");
		esperarSituacao(atendimento, "PRONTA");

		Map<String, Object> pendente = pendenteDe(atendimento);
		assertThat(pendente).containsEntry("corSugerida", "LARANJA").containsEntry("sugestaoPreliminar", true)
				.containsEntry("situacaoSugestao", "PRONTA").containsEntry("sinaisVitais", null);

		ArgumentCaptor<String> caso = ArgumentCaptor.forClass(String.class);
		verify(clienteIA).classificarRisco(caso.capture());
		assertThat(caso.getValue()).contains("Dor no peito há uma hora").contains("ainda não medidos");
	}

	@Test
	void aRecepcaoVeSoOAndamentoNuncaACor() throws Exception {
		when(clienteIA.classificarRisco(anyString())).thenReturn(CorRisco.VERMELHO);
		UUID atendimento = admitir(recepcao, hospital, Cenario.novoCpf(), "Desmaio", "AUTORIZADO");
		esperarSituacao(atendimento, "PRONTA");

		String corpo = como(recepcao, "RECEPCAO", get("/triagens/pre-classificacao").param("atendimentoId", atendimento.toString())
				.param("instituicaoId", hospital.getId().toString())).andExpect(status().isOk())
				.andExpect(jsonPath("$.situacao").value("PRONTA")).andReturn().getResponse().getContentAsString();

		assertThat(corpo).doesNotContain("VERMELHO").doesNotContain("cor");
	}

	@Test
	void sinaisVitaisDoEnfermeiroRefazemASugestaoSemBotao() throws Exception {
		when(clienteIA.classificarRisco(anyString())).thenReturn(CorRisco.LARANJA, CorRisco.VERMELHO);
		UUID atendimento = admitir(recepcao, hospital, Cenario.novoCpf(), "Falta de ar", "AUTORIZADO");
		esperarSituacao(atendimento, "PRONTA");
		UUID triagem = UUID.fromString((String) pendenteDe(atendimento).get("id"));

		como(enfermeiro, "ENFERMEIRO", patch("/triagens/{id}/sinais-vitais", triagem)
				.content("{\"sinaisVitais\":{\"saturacaoOxigenio\":88.0,\"frequenciaCardiaca\":130}}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.sinaisVitais.saturacaoOxigenio").value(88.0));

		await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(pendenteDe(atendimento))
				.containsEntry("corSugerida", "VERMELHO").containsEntry("sugestaoPreliminar", false).containsEntry("situacaoSugestao", "PRONTA"));
		ArgumentCaptor<String> casos = ArgumentCaptor.forClass(String.class);
		verify(clienteIA, atLeast(2)).classificarRisco(casos.capture());
		assertThat(casos.getAllValues().getLast()).contains("saturação de oxigênio 88").contains("frequência cardíaca 130");
	}

	@Test
	void seAIAFalharAAdmissaoSegueEOEnfermeiroClassificaAMao() throws Exception {
		when(clienteIA.classificarRisco(anyString())).thenThrow(new IllegalStateException("IA fora do ar"));

		UUID atendimento = admitir(recepcao, hospital, Cenario.novoCpf(), "Tontura", "AUTORIZADO");
		esperarSituacao(atendimento, "INDISPONIVEL");

		Map<String, Object> pendente = pendenteDe(atendimento);
		assertThat(pendente).containsEntry("corSugerida", null).containsEntry("situacaoSugestao", "INDISPONIVEL");
		como(enfermeiro, "ENFERMEIRO", post("/triagens/{id}/confirmacao", pendente.get("id")).content("{\"cor\":\"AMARELO\"}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.corConfirmada").value("AMARELO"));
	}

	@Test
	void historicoAutorizadoEntraNoCasoEOAcessoFicaNaAuditoriaEmNomeDaRecepcao() throws Exception {
		when(clienteIA.classificarRisco(anyString())).thenReturn(CorRisco.AMARELO);
		Instituicao outro = cenario.instituicao(true);
		String cpf = Cenario.novoCpf();
		UUID primeiro = admitir(cenario.recepcao(outro), outro, cpf, "Retorno", "AUTORIZADO");
		UUID paciente = jdbc.queryForObject("select p.paciente_id from atendimento a join prontuario p on p.id = a.prontuario_id where a.id = ?",
				UUID.class, primeiro);
		cenario.registroClinico(paciente, outro, "Alergias", "Alergia grave a dipirona");

		UUID atendimento = admitir(recepcao, hospital, cpf, "Dor de cabeça", null);
		esperarSituacao(atendimento, "PRONTA");

		ArgumentCaptor<String> casos = ArgumentCaptor.forClass(String.class);
		verify(clienteIA, atLeast(2)).classificarRisco(casos.capture());
		assertThat(casos.getAllValues().getLast()).contains("Alergia grave a dipirona");
		assertThat(jdbc.queryForObject("""
				select count(*) from registro_auditoria a join usuario u on u.id = a.ator_id
				where u.login = ? and a.perfil = 'RECEPCAO' and a.finalidade_declarada = 'Pré-classificação por IA na admissão'
				""", Integer.class, recepcao)).isEqualTo(1);
	}

	@Test
	void semConsentimentoOHistoricoDeOutraInstituicaoNaoVaiParaAIA() throws Exception {
		when(clienteIA.classificarRisco(anyString())).thenReturn(CorRisco.AMARELO);
		Instituicao outro = cenario.instituicao(true);
		String cpf = Cenario.novoCpf();
		UUID primeiro = admitir(cenario.recepcao(outro), outro, cpf, "Retorno", "NEGADO");
		UUID paciente = jdbc.queryForObject("select p.paciente_id from atendimento a join prontuario p on p.id = a.prontuario_id where a.id = ?",
				UUID.class, primeiro);
		cenario.registroClinico(paciente, outro, "Alergias", "Alergia grave a dipirona");

		UUID atendimento = admitir(recepcao, hospital, cpf, "Dor de cabeça", null);
		esperarSituacao(atendimento, "PRONTA");

		ArgumentCaptor<String> casos = ArgumentCaptor.forClass(String.class);
		verify(clienteIA, atLeast(2)).classificarRisco(casos.capture());
		assertThat(casos.getAllValues().getLast()).doesNotContain("dipirona").contains("Histórico autorizado: nenhum");
	}

	@Test
	void soARecepcaoDaInstituicaoVeOAndamentoEOEnfermeiroNaoAdmite() throws Exception {
		when(clienteIA.classificarRisco(anyString())).thenReturn(CorRisco.VERDE);
		UUID atendimento = admitir(recepcao, hospital, Cenario.novoCpf(), "Corte leve", "AUTORIZADO");
		Instituicao outro = cenario.instituicao(true);

		como(cenario.recepcao(outro), "RECEPCAO", get("/triagens/pre-classificacao").param("atendimentoId", atendimento.toString())
				.param("instituicaoId", hospital.getId().toString())).andExpect(status().isForbidden());
		como(enfermeiro, "ENFERMEIRO", get("/triagens/pre-classificacao").param("atendimentoId", atendimento.toString())
				.param("instituicaoId", hospital.getId().toString())).andExpect(status().isForbidden());
		como(enfermeiro, "ENFERMEIRO", post("/api/pacientes/admissoes").content(corpoDaAdmissao(Cenario.novoCpf(), hospital, "x", null)))
				.andExpect(status().isForbidden());
		como(cenario.administrativo(hospital), "ADMINISTRATIVO", post("/api/pacientes/admissoes")
				.content(corpoDaAdmissao(Cenario.novoCpf(), hospital, "x", null))).andExpect(status().isForbidden());
	}

	private void esperarSituacao(UUID atendimento, String situacao) {
		await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
				assertThat(triagemDe(atendimento)).containsEntry("situacao_sugestao", situacao));
	}

	private Map<String, Object> triagemDe(UUID atendimento) {
		return jdbc.queryForMap("select * from triagem where atendimento_id = ?", atendimento);
	}

	private Map<String, Object> pendenteDe(UUID atendimento) throws Exception {
		String corpo = como(enfermeiro, "ENFERMEIRO", get("/triagens/aguardando").param("instituicaoId", hospital.getId().toString()))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		List<Map<String, Object>> pendentes = JsonPath.read(corpo, "$[?(@.atendimentoId == '" + atendimento + "')].triagemPendente");
		return pendentes.getFirst();
	}

	private UUID admitir(String login, Instituicao instituicao, String cpf, String sintomas, String consentimento) throws Exception {
		String corpo = como(login, "RECEPCAO", post("/api/pacientes/admissoes").content(corpoDaAdmissao(cpf, instituicao, sintomas, consentimento)))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return UUID.fromString(JsonPath.read(corpo, "$.atendimentoId"));
	}

	private static String corpoDaAdmissao(String cpf, Instituicao instituicao, String sintomas, String consentimento) {
		String dados = consentimento == null ? "" : ",\"nome\":\"Paciente Fictício\",\"dataNascimento\":\"1980-05-17\",\"consentimentoInicial\":\"" + consentimento + "\"";
		return "{\"cpf\":\"%s\",\"instituicaoId\":\"%s\",\"motivo\":\"%s\"%s}".formatted(cpf, instituicao.getId(), sintomas, dados);
	}

	private ResultActions como(String login, String perfil, MockHttpServletRequestBuilder requisicao) throws Exception {
		return mvc.perform(requisicao.with(user(login).roles(perfil)).with(csrf()).contentType(MediaType.APPLICATION_JSON));
	}
}
