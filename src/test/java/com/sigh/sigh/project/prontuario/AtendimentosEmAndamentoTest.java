package com.sigh.sigh.project.prontuario;

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

/**
 * FE-07: o atendimento sai da fila de triagem quando o médico faz o primeiro registro. Esta lista é por onde o médico
 * volta ao paciente, e o enfermeiro acompanha quem está em atendimento na instituição.
 */
class AtendimentosEmAndamentoTest extends TesteIntegracao {

	private static final String TRIAGEM = """
			{"atendimentoId":"%s","sintomas":"Febre","sinaisVitais":{"pressaoArterial":"120/80"}}
			""";

	@Autowired
	JdbcTemplate jdbc;

	private Instituicao hospitalA;
	private Instituicao hospitalB;
	private String administrativoA;
	private String enfermeiroA;
	private String medicoA;

	@BeforeEach
	void hospitais() {
		hospitalA = cenario.instituicao(true);
		hospitalB = cenario.instituicao(true);
		administrativoA = cenario.administrativo(hospitalA);
		enfermeiroA = cenario.profissional(hospitalA, Perfil.ENFERMEIRO);
		medicoA = cenario.profissional(hospitalA, Perfil.MEDICO);
		jdbc.update("update usuario set nome = 'Dra. Ana Médica' where login = ?", medicoA);
	}

	@Test
	void soOsEmAtendimentoDaInstituicaoComACorMaisRecenteEOMedico() throws Exception {
		UUID comReclassificacao = admitir(administrativoA, hospitalA, "Reclassificado", "Febre alta");
		classificar(comReclassificacao, "VERDE");
		relogio.avancar(Duration.ofMinutes(1));
		classificar(comReclassificacao, "LARANJA");
		registrar(medicoA, comReclassificacao);
		relogio.avancar(Duration.ofMinutes(1));
		String outroMedico = cenario.profissional(hospitalA, Perfil.MEDICO);
		jdbc.update("update usuario set nome = 'Dr. Outro' where login = ?", outroMedico);
		UUID semTriagem = admitir(administrativoA, hospitalA, "Sem Triagem", "Retorno");
		registrar(outroMedico, semTriagem);

		UUID naFila = admitir(administrativoA, hospitalA, "Na Fila", "Tosse");
		classificar(naFila, "AMARELO");
		UUID encerrado = admitir(administrativoA, hospitalA, "Encerrado", "Corte");
		registrar(medicoA, encerrado);
		mvc.perform(post("/atendimentos/{id}/encerramento", encerrado).with(user(medicoA).roles("MEDICO")).with(csrf()))
				.andExpect(status().isNoContent());
		String medicoB = cenario.profissional(hospitalB, Perfil.MEDICO);
		registrar(medicoB, admitir(cenario.administrativo(hospitalB), hospitalB, "Outro Hospital", "Dor"));

		List<Map<String, Object>> doMedicoA = lista(medicoA, "MEDICO");
		assertThat(doMedicoA).extracting(a -> a.get("atendimentoId")).containsExactly(comReclassificacao.toString(), semTriagem.toString());
		assertThat(doMedicoA.get(0)).containsEntry("paciente", "Reclassificado").containsEntry("motivo", "Febre alta")
				.containsEntry("corConfirmada", "LARANJA").containsEntry("medicoResponsavel", "Dra. Ana Médica")
				.containsEntry("doUsuarioLogado", true).containsKeys("chegadaEm", "inicioClinicoEm");
		assertThat(doMedicoA.get(1)).containsEntry("corConfirmada", null).containsEntry("medicoResponsavel", "Dr. Outro")
				.containsEntry("doUsuarioLogado", false);

		// O enfermeiro também vê, e nenhum é "dele".
		assertThat(lista(enfermeiroA, "ENFERMEIRO")).extracting(a -> a.get("doUsuarioLogado")).containsOnly(false);
	}

	@Test
	void doQueComecouAntesParaOMaisRecente() throws Exception {
		List<String> ordem = new ArrayList<>();
		List<UUID> admitidos = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			admitidos.add(admitir(administrativoA, hospitalA, "Paciente " + i, "Motivo"));
		}
		// Começam na ordem inversa da chegada: a lista segue o início do atendimento, e não a chegada.
		for (int i = 4; i >= 0; i--) {
			relogio.avancar(Duration.ofMinutes(1));
			registrar(medicoA, admitidos.get(i));
			ordem.add(admitidos.get(i).toString());
		}
		assertThat(lista(medicoA, "MEDICO")).extracting(a -> a.get("atendimentoId")).containsExactlyElementsOf(ordem);
	}

	@Test
	void soMedicoOuEnfermeiroComVinculoNaInstituicao() throws Exception {
		mvc.perform(get("/atendimentos/em-andamento").param("instituicaoId", hospitalA.getId().toString())
				.with(user(cenario.profissional(hospitalB, Perfil.MEDICO)).roles("MEDICO"))).andExpect(status().isForbidden());
		mvc.perform(get("/atendimentos/em-andamento").param("instituicaoId", hospitalA.getId().toString())
				.with(user(administrativoA).roles("ADMINISTRATIVO"))).andExpect(status().isForbidden());
	}

	private List<Map<String, Object>> lista(String login, String perfil) throws Exception {
		String corpo = mvc.perform(get("/atendimentos/em-andamento").param("instituicaoId", hospitalA.getId().toString())
				.with(user(login).roles(perfil))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		return JsonPath.read(corpo, "$[*]");
	}

	private UUID admitir(String administrativo, Instituicao hospital, String nome, String motivo) throws Exception {
		return id(como(administrativo, "ADMINISTRATIVO", post("/api/pacientes/admissoes").content("""
				{"cpf":"%s","nome":"%s","dataNascimento":"1980-05-17","instituicaoId":"%s","motivo":"%s"}
				""".formatted(Cenario.novoCpf(), nome, hospital.getId(), motivo))).andExpect(status().isCreated()), "$.atendimentoId");
	}

	private void classificar(UUID atendimento, String cor) throws Exception {
		UUID triagem = id(como(enfermeiroA, "ENFERMEIRO", post("/triagens").content(TRIAGEM.formatted(atendimento)))
				.andExpect(status().isCreated()), "$.id");
		como(enfermeiroA, "ENFERMEIRO", post("/triagens/{id}/confirmacao", triagem).content("{\"cor\":\"%s\"}".formatted(cor)))
				.andExpect(status().isOk());
	}

	private void registrar(String medico, UUID atendimento) throws Exception {
		como(medico, "MEDICO", post("/atendimentos/{id}/registros", atendimento)
				.content("{\"tipo\":\"EVOLUCAO\",\"conteudo\":\"Avaliado\",\"categoriaInformacao\":\"Clínica geral\"}"))
				.andExpect(status().isCreated());
	}

	private ResultActions como(String login, String perfil, MockHttpServletRequestBuilder requisicao) throws Exception {
		return mvc.perform(requisicao.with(user(login).roles(perfil)).with(csrf()).contentType(MediaType.APPLICATION_JSON));
	}

	private static UUID id(ResultActions resultado, String caminho) throws Exception {
		return UUID.fromString(JsonPath.read(resultado.andReturn().getResponse().getContentAsString(), caminho));
	}
}
