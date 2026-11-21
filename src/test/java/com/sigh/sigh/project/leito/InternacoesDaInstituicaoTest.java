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

import java.time.Duration;
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
 * FE-04 (RF-005, RF-006): o administrativo vê as internações em andamento nos leitos da instituição dele, com o
 * que precisa para transferir ou dar alta, e os atendimentos em aberto sem internação (quem pode ser internado, e
 * a fila de espera quando não há leito livre).
 */
class InternacoesDaInstituicaoTest extends TesteIntegracao {

	@Autowired
	JdbcTemplate jdbc;

	private Instituicao hospitalA;
	private Instituicao hospitalB;
	private String administrativoA;
	private String administrativoB;

	@BeforeEach
	void hospitais() {
		hospitalA = cenario.instituicao(true);
		hospitalB = cenario.instituicao(true);
		administrativoA = cenario.administrativo(hospitalA);
		administrativoB = cenario.administrativo(hospitalB);
	}

	@Test
	void internacoesAtivasSoDosLeitosDaInstituicaoComResumosDaInternacaoEPedidoPendente() throws Exception {
		UUID atendimentoMaria = admitir(hospitalA, "Maria Fictícia");
		// Resumo de alta de antes da internação: não serve para esta alta e não aparece.
		resumoDeAlta(atendimentoMaria, "Dra. Antiga");
		relogio.avancar(Duration.ofMinutes(5));
		UUID leitoZ = leito(administrativoA, hospitalA, "Z-9", "ENFERMARIA");
		UUID leitoA = leito(administrativoA, hospitalA, "A-1", "UTI");
		UUID internacaoMaria = internar(administrativoA, atendimentoMaria, leitoZ);
		relogio.avancar(Duration.ofMinutes(5));
		UUID resumo = resumoDeAlta(atendimentoMaria, "Dra. Recente");
		// Outro tipo de registro depois da admissão: não é resumo de alta e não aparece.
		registro(atendimentoMaria, "EVOLUCAO");
		UUID pedido = id(como(administrativoA, post("/internacoes/{id}/solicitacoes-transferencia", internacaoMaria)
				.content("{\"instituicaoDestinoId\":\"%s\",\"categoria\":\"UTI\"}".formatted(hospitalB.getId())))
				.andExpect(status().isCreated()), "$.id");

		UUID atendimentoJoao = admitir(hospitalA, "João Fictício");
		UUID internacaoJoao = internar(administrativoA, atendimentoJoao, leito(administrativoA, hospitalA, "M-5", "ENFERMARIA"));
		// Transferido dentro do hospital: aparece no leito novo.
		como(administrativoA, post("/internacoes/{id}/transferencia", internacaoJoao).content("{\"leitoDestinoId\":\"%s\"}".formatted(leitoA)))
				.andExpect(status().isOk());

		// Com alta: não aparece.
		UUID atendimentoAna = admitir(hospitalA, "Ana Fictícia");
		UUID internacaoAna = internar(administrativoA, atendimentoAna, leito(administrativoA, hospitalA, "B-2", "ENFERMARIA"));
		como(administrativoA, post("/internacoes/{id}/alta", internacaoAna)
				.content("{\"resumoAltaId\":\"%s\"}".formatted(resumoDeAlta(atendimentoAna, "Dr. Alta")))).andExpect(status().isOk());

		// Internada em outro hospital: não aparece para o hospital A.
		UUID atendimentoBia = admitir(hospitalB, "Bia Fictícia");
		internar(administrativoB, atendimentoBia, leito(administrativoB, hospitalB, "C-3", "ENFERMARIA"));

		List<Map<String, Object>> ativas = lista(como(administrativoA, get("/internacoes").param("instituicaoId", hospitalA.getId().toString()))
				.andExpect(status().isOk()));

		// Ordem pelo código do leito: A-1 (João, transferido) antes de Z-9 (Maria).
		assertThat(ativas).extracting(i -> i.get("paciente")).containsExactly("João Fictício", "Maria Fictícia");
		Map<String, Object> joao = ativas.get(0);
		assertThat(joao).containsEntry("id", internacaoJoao.toString()).containsEntry("leitoId", leitoA.toString())
				.containsEntry("leitoCodigo", "A-1").containsEntry("categoria", "UTI")
				.containsEntry("atendimentoId", atendimentoJoao.toString()).containsEntry("solicitacaoPendenteId", null);
		Map<String, Object> maria = ativas.get(1);
		assertThat(maria).containsEntry("id", internacaoMaria.toString()).containsEntry("leitoCodigo", "Z-9")
				.containsEntry("setor", "Setor").containsEntry("solicitacaoPendenteId", pedido.toString());
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> resumos = (List<Map<String, Object>>) maria.get("resumosDeAlta");
		assertThat(resumos).hasSize(1);
		assertThat(resumos.getFirst()).containsOnlyKeys("id", "criadoEm", "autor").containsEntry("id", resumo.toString())
				.containsEntry("autor", "Dra. Recente");

		// O hospital B só vê a Bia.
		assertThat(lista(como(administrativoB, get("/internacoes").param("instituicaoId", hospitalB.getId().toString()))))
				.extracting(i -> i.get("paciente")).containsExactly("Bia Fictícia");
	}

	@Test
	void internacoesVemPeloCodigoDoLeito() throws Exception {
		// Cinco leitos criados fora da ordem: pela ordem do id, a certa só sairia por acaso (1 em 120).
		for (String codigo : List.of("E-5", "B-2", "D-4", "A-1", "C-3")) {
			internar(administrativoA, admitir(hospitalA, "Paciente " + codigo),
					leito(administrativoA, hospitalA, codigo, "ENFERMARIA"));
		}
		assertThat(lista(como(administrativoA, get("/internacoes").param("instituicaoId", hospitalA.getId().toString()))))
				.extracting(i -> i.get("leitoCodigo")).containsExactly("A-1", "B-2", "C-3", "D-4", "E-5");
	}

	@Test
	void atendimentosSemLeitoVemDoMaisAntigoParaOMaisRecente() throws Exception {
		List<UUID> chegadas = new java.util.ArrayList<>();
		for (int i = 0; i < 5; i++) {
			chegadas.add(admitir(hospitalA, "Chegada " + i));
			relogio.avancar(Duration.ofMinutes(1));
		}
		assertThat(lista(como(administrativoA, get("/internacoes/atendimentos-sem-leito").param("instituicaoId", hospitalA.getId().toString()))))
				.extracting(a -> a.get("atendimentoId")).containsExactlyElementsOf(chegadas.stream().map(UUID::toString).toList());
	}

	@Test
	void atendimentosSemLeitoSaoOsAbertosDaInstituicaoSemInternacaoDoMaisAntigo() throws Exception {
		UUID primeiro = admitir(hospitalA, "Primeira Chegada");
		relogio.avancar(Duration.ofMinutes(3));
		UUID segundo = admitir(hospitalA, "Segunda Chegada");
		relogio.avancar(Duration.ofMinutes(3));
		UUID internado = admitir(hospitalA, "Já Internado");
		internar(administrativoA, internado, leito(administrativoA, hospitalA, "D-4", "ENFERMARIA"));
		UUID encerrado = admitir(hospitalA, "Já Encerrado");
		jdbc.update("update atendimento set status = 'ENCERRADO' where id = ?", encerrado);
		admitir(hospitalB, "De Outro Hospital");
		// O paciente internado volta com outro atendimento: continua fora, porque já ocupa um leito.
		String cpfInternado = jdbc.queryForObject("""
				select p.cpf from atendimento a join prontuario pr on pr.id = a.prontuario_id
				join paciente p on p.id = pr.paciente_id where a.id = ?""", String.class, internado);
		admitirCpf(hospitalA, cpfInternado, "Já Internado");
		// Quem já teve alta pode ser internado de novo: o atendimento novo aparece.
		UUID comAlta = admitir(hospitalA, "Teve Alta");
		UUID internacaoComAlta = internar(administrativoA, comAlta, leito(administrativoA, hospitalA, "E-5", "ENFERMARIA"));
		como(administrativoA, post("/internacoes/{id}/alta", internacaoComAlta)
				.content("{\"resumoAltaId\":\"%s\"}".formatted(resumoDeAlta(comAlta, "Dr. Alta")))).andExpect(status().isOk());
		jdbc.update("update atendimento set status = 'ENCERRADO' where id = ?", comAlta);
		relogio.avancar(Duration.ofMinutes(3));
		String cpfComAlta = jdbc.queryForObject("""
				select p.cpf from atendimento a join prontuario pr on pr.id = a.prontuario_id
				join paciente p on p.id = pr.paciente_id where a.id = ?""", String.class, comAlta);
		UUID voltou = admitirCpf(hospitalA, cpfComAlta, "Teve Alta");

		List<Map<String, Object>> semLeito = lista(como(administrativoA, get("/internacoes/atendimentos-sem-leito")
				.param("instituicaoId", hospitalA.getId().toString())).andExpect(status().isOk()));

		assertThat(semLeito).extracting(a -> a.get("atendimentoId")).containsExactly(primeiro.toString(), segundo.toString(),
				voltou.toString());
		assertThat(semLeito.getFirst()).containsEntry("paciente", "Primeira Chegada").containsEntry("status", "ABERTO")
				.containsEntry("motivo", "Febre alta").containsKey("chegadaEm");
	}

	@Test
	void outraInstituicaoNaoVe() throws Exception {
		como(administrativoB, get("/internacoes").param("instituicaoId", hospitalA.getId().toString())).andExpect(status().isForbidden());
		como(administrativoB, get("/internacoes/atendimentos-sem-leito").param("instituicaoId", hospitalA.getId().toString()))
				.andExpect(status().isForbidden());
		String medico = cenario.profissional(hospitalA, Perfil.MEDICO);
		mvc.perform(get("/internacoes").param("instituicaoId", hospitalA.getId().toString()).with(user(medico).roles("MEDICO")))
				.andExpect(status().isForbidden());
	}

	private UUID admitir(Instituicao hospital, String nome) throws Exception {
		return admitirCpf(hospital, Cenario.novoCpf(), nome);
	}

	private UUID admitirCpf(Instituicao hospital, String cpf, String nome) throws Exception {
		return id(mvc.perform(post("/api/pacientes/admissoes").with(user(cenario.recepcao(hospital)).roles("RECEPCAO")).with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content("""
				{"cpf":"%s","nome":"%s","dataNascimento":"1980-05-17","instituicaoId":"%s","motivo":"Febre alta"}
				""".formatted(cpf, nome, hospital.getId()))).andExpect(status().isCreated()), "$.atendimentoId");
	}

	private UUID internar(String administrativo, UUID atendimento, UUID leito) throws Exception {
		return id(como(administrativo, post("/internacoes").content("{\"atendimentoId\":\"%s\",\"leitoId\":\"%s\"}".formatted(atendimento, leito)))
				.andExpect(status().isCreated()), "$.id");
	}

	private UUID leito(String administrativo, Instituicao hospital, String codigo, String categoria) throws Exception {
		return id(como(administrativo, post("/leitos").content("""
				{"instituicaoId":"%s","codigo":"%s","setor":"Setor","categoria":"%s"}
				""".formatted(hospital.getId(), codigo, categoria))).andExpect(status().isCreated()), "$.id");
	}

	private void registro(UUID atendimento, String tipo) throws Exception {
		String medico = cenario.profissional(hospitalA, Perfil.MEDICO);
		mvc.perform(post("/atendimentos/{id}/registros", atendimento)
				.with(user(medico).roles("MEDICO")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"tipo\":\"%s\",\"conteudo\":\"Evolução estável\",\"categoriaInformacao\":\"Clínica geral\"}".formatted(tipo)))
				.andExpect(status().isCreated());
	}

	/** Resumo de alta registrado por um médico do hospital A, com o nome dado. */
	private UUID resumoDeAlta(UUID atendimento, String nomeDoMedico) throws Exception {
		String medico = cenario.profissional(hospitalA, Perfil.MEDICO);
		jdbc.update("update usuario set nome = ? where login = ?", nomeDoMedico, medico);
		return id(mvc.perform(post("/atendimentos/{id}/registros", atendimento)
				.with(user(medico).roles("MEDICO")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"tipo\":\"RESUMO_ALTA\",\"conteudo\":\"Alta melhorado\",\"categoriaInformacao\":\"Clínica geral\"}"))
				.andExpect(status().isCreated()), "$.id");
	}

	private ResultActions como(String login, MockHttpServletRequestBuilder requisicao) throws Exception {
		return mvc.perform(requisicao.with(user(login).roles("ADMINISTRATIVO")).with(csrf()).contentType(MediaType.APPLICATION_JSON));
	}

	private static List<Map<String, Object>> lista(ResultActions resultado) throws Exception {
		return JsonPath.read(resultado.andReturn().getResponse().getContentAsString(), "$[*]");
	}

	private static UUID id(ResultActions resultado, String caminho) throws Exception {
		MvcResult feito = resultado.andReturn();
		return UUID.fromString(JsonPath.read(feito.getResponse().getContentAsString(), caminho));
	}
}
