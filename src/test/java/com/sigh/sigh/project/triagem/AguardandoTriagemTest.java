package com.sigh.sigh.project.triagem;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AguardandoTriagemTest extends TesteIntegracao {

	private static final String TRIAGEM = """
			{"atendimentoId":"%s","sintomas":"%s","sinaisVitais":{"pressaoArterial":"120/80","frequenciaCardiaca":90,
			 "frequenciaRespiratoria":18,"temperatura":38.5,"saturacaoOxigenio":96.0}}
			""";

	@Autowired
	JdbcTemplate jdbc;

	private Instituicao hospitalA;
	private Instituicao hospitalB;
	private String administrativoA;
	private String enfermeiroA;

	@BeforeEach
	void hospitais() {
		hospitalA = cenario.instituicao(true);
		hospitalB = cenario.instituicao(true);
		administrativoA = cenario.administrativo(hospitalA);
		enfermeiroA = cenario.profissional(hospitalA, Perfil.ENFERMEIRO);
	}

	@Test
	void soQuemNaoTemClassificacaoConfirmadaComATriagemPendente() throws Exception {
		UUID semTriagem = admitir(hospitalA, "Sem Triagem", "Dor de cabeça");
		relogio.avancar(Duration.ofMinutes(1));
		UUID comPendente = admitir(hospitalA, "Triagem Começada", "Febre");
		UUID pendente = UUID.fromString(jdbc.queryForObject("select cast(id as text) from triagem where atendimento_id = ?", String.class, comPendente));
		relogio.avancar(Duration.ofMinutes(1));
		UUID confirmado = admitir(hospitalA, "Já Confirmado", "Tosse");
		confirmar(registrarTriagem(confirmado, "Tosse seca"), "VERDE");
		UUID encerrado = admitir(hospitalA, "Já Encerrado", "Retorno");
		jdbc.update("update atendimento set status = 'ENCERRADO' where id = ?", encerrado);
		admitir(hospitalB, "Outro Hospital", "Corte");

		List<Map<String, Object>> aguardando = aguardando(enfermeiroA);

		assertThat(aguardando).extracting(a -> a.get("atendimentoId")).containsExactly(semTriagem.toString(), comPendente.toString());
		assertThat(aguardando.get(0)).containsEntry("paciente", "Sem Triagem").containsEntry("motivo", "Dor de cabeça")
				.containsKey("chegadaEm");
		@SuppressWarnings("unchecked")
		Map<String, Object> daRecepcao = (Map<String, Object>) aguardando.get(0).get("triagemPendente");
		assertThat(daRecepcao).containsEntry("sintomas", "Dor de cabeça").containsEntry("sinaisVitais", null)
				.containsEntry("sugestaoPreliminar", true).containsEntry("corSugerida", null);
		@SuppressWarnings("unchecked")
		Map<String, Object> triagem = (Map<String, Object>) aguardando.get(1).get("triagemPendente");
		assertThat(triagem).containsEntry("id", pendente.toString()).containsEntry("sintomas", "Febre")
				.containsEntry("corSugerida", null);

		jdbc.update("update triagem set cor_sugerida = 'AMARELO' where id = ?", pendente);
		@SuppressWarnings("unchecked")
		Map<String, Object> comSugestao = (Map<String, Object>) aguardando(enfermeiroA).get(1).get("triagemPendente");
		assertThat(comSugestao).containsEntry("corSugerida", "AMARELO");
	}

	@Test
	void entreDuasTriagensPendentesValeAQueTemSugestao() throws Exception {
		UUID atendimento = admitir(hospitalA, "Duas Triagens", "Falta de ar");
		List<UUID> pendentes = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			pendentes.add(registrarTriagem(atendimento, "Registro " + i));
		}
		UUID comSugestao = pendentes.stream().max(UUID::compareTo).orElseThrow();
		jdbc.update("update triagem set cor_sugerida = 'LARANJA' where id = ?", comSugestao);

		@SuppressWarnings("unchecked")
		Map<String, Object> triagem = (Map<String, Object>) aguardando(enfermeiroA).getFirst().get("triagemPendente");
		assertThat(triagem).containsEntry("id", comSugestao.toString()).containsEntry("corSugerida", "LARANJA");
	}

	@Test
	void porOrdemDeChegada() throws Exception {
		List<String> chegadas = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			chegadas.add(admitir(hospitalA, "Chegada " + i, "Motivo").toString());
			relogio.avancar(Duration.ofMinutes(1));
		}
		assertThat(aguardando(enfermeiroA)).extracting(a -> a.get("atendimentoId")).containsExactlyElementsOf(chegadas);
	}

	@Test
	void soEnfermeiroComVinculoNaInstituicao() throws Exception {
		String enfermeiroB = cenario.profissional(hospitalB, Perfil.ENFERMEIRO);
		mvc.perform(get("/triagens/aguardando").param("instituicaoId", hospitalA.getId().toString())
				.with(user(enfermeiroB).roles("ENFERMEIRO"))).andExpect(status().isForbidden());
		mvc.perform(get("/triagens/aguardando").param("instituicaoId", hospitalA.getId().toString())
				.with(user(administrativoA).roles("ADMINISTRATIVO"))).andExpect(status().isForbidden());
	}

	private List<Map<String, Object>> aguardando(String enfermeiro) throws Exception {
		String corpo = mvc.perform(get("/triagens/aguardando").param("instituicaoId", hospitalA.getId().toString())
				.with(user(enfermeiro).roles("ENFERMEIRO"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		return JsonPath.read(corpo, "$[*]");
	}

	private UUID admitir(Instituicao hospital, String nome, String motivo) throws Exception {
		return id(como(cenario.recepcao(hospital), "RECEPCAO", post("/api/pacientes/admissoes").content("""
				{"cpf":"%s","nome":"%s","dataNascimento":"1980-05-17","instituicaoId":"%s","motivo":"%s"}
				""".formatted(Cenario.novoCpf(), nome, hospital.getId(), motivo))).andExpect(status().isCreated()), "$.atendimentoId");
	}

	private UUID registrarTriagem(UUID atendimento, String sintomas) throws Exception {
		return id(como(enfermeiroA, "ENFERMEIRO", post("/triagens").content(TRIAGEM.formatted(atendimento, sintomas)))
				.andExpect(status().isCreated()), "$.id");
	}

	private void confirmar(UUID triagem, String cor) throws Exception {
		como(enfermeiroA, "ENFERMEIRO", post("/triagens/{id}/confirmacao", triagem).content("{\"cor\":\"%s\"}".formatted(cor)))
				.andExpect(status().isOk());
	}

	private ResultActions como(String login, String perfil, MockHttpServletRequestBuilder requisicao) throws Exception {
		return mvc.perform(requisicao.with(user(login).roles(perfil)).with(csrf()).contentType(MediaType.APPLICATION_JSON));
	}

	private static UUID id(ResultActions resultado, String caminho) throws Exception {
		return UUID.fromString(JsonPath.read(resultado.andReturn().getResponse().getContentAsString(), caminho));
	}
}
