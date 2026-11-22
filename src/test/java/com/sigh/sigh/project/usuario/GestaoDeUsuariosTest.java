package com.sigh.sigh.project.usuario;

import com.jayway.jsonpath.JsonPath;
import com.sigh.sigh.project.instituicao.entity.Instituicao;
import com.sigh.sigh.project.support.TesteIntegracao;
import com.sigh.sigh.project.usuario.entity.Perfil;
import com.sigh.sigh.project.usuario.entity.Usuario;
import com.sigh.sigh.project.usuario.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GestaoDeUsuariosTest extends TesteIntegracao {

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	UsuarioRepository usuarioRepository;

	@Autowired
	PasswordEncoder codificadorDeSenha;

	@Autowired
	TransactionTemplate transacao;

	private Instituicao hospital;
	private String auditor;

	@BeforeEach
	void cenario() {
		relogio.definir(Instant.now());
		hospital = cenario.instituicao(true);
		auditor = cenario.usuario(Perfil.AUDITOR_GESTOR).getLogin();
	}

	@Test
	void criaMedicoComVinculoESenhaAleatoriaGuardadaSoComoHash() throws Exception {
		String corpo = criar("dra.nova", "Dra. Nova", "[\"MEDICO\"]", vinculo(hospital, "[\"MEDICO\"]", "CRM 123456"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.usuario.login").value("dra.nova"))
				.andExpect(jsonPath("$.usuario.ativo").value(true))
				.andExpect(jsonPath("$.usuario.senhaTemporaria").value(true))
				.andExpect(jsonPath("$.usuario.vinculos[0].instituicaoId").value(hospital.getId().toString()))
				.andExpect(jsonPath("$.usuario.vinculos[0].registroProfissional").value("CRM 123456"))
				.andExpect(content().string(not(containsString("senhaHash"))))
				.andReturn().getResponse().getContentAsString();

		String senha = JsonPath.read(corpo, "$.senhaTemporaria");
		assertThat(senha).hasSize(16).matches(".*[A-Z].*").matches(".*[a-z].*").matches(".*\\d.*");
		Usuario criado = usuarioRepository.findByLogin("dra.nova").orElseThrow();
		assertThat(criado.getSenhaHash()).isNotEqualTo(senha);
		assertThat(codificadorDeSenha.matches(senha, criado.getSenhaHash())).isTrue();
		assertThat(contarAcoes("USUARIO_CRIADO", "dra.nova")).isEqualTo(1);
	}

	@Test
	void cadaUsuarioRecebeUmaSenhaDiferente() throws Exception {
		String a = JsonPath.read(criar("usuario.a", "A", "[\"ADMINISTRATIVO\"]", vinculo(hospital, "[\"ADMINISTRATIVO\"]", null))
				.andReturn().getResponse().getContentAsString(), "$.senhaTemporaria");
		String b = JsonPath.read(criar("usuario.b", "B", "[\"ADMINISTRATIVO\"]", vinculo(hospital, "[\"ADMINISTRATIVO\"]", null))
				.andReturn().getResponse().getContentAsString(), "$.senhaTemporaria");
		assertThat(a).isNotEqualTo(b);
	}

	@Test
	void primeiroLoginExigeTrocarASenhaGerada() throws Exception {
		String senha = senhaDe(criar("adm.novo", "Adm Novo", "[\"ADMINISTRATIVO\"]", vinculo(hospital, "[\"ADMINISTRATIVO\"]", null)));

		String pendente = login("adm.novo", senha).andExpect(status().isOk())
				.andExpect(jsonPath("$.etapa").value("TROCAR_SENHA")).andReturn().getResponse().getContentAsString();
		String tokenPendente = JsonPath.read(pendente, "$.token");

		mvc.perform(get("/usuarios/eu").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenPendente))
				.andExpect(status().isForbidden());

		trocaInicial(tokenPendente, "NovaSenha2026").andExpect(status().isOk())
				.andExpect(jsonPath("$.etapa").value("ACESSO_LIBERADO"));

		login("adm.novo", senha).andExpect(status().isUnauthorized());
		login("adm.novo", "NovaSenha2026").andExpect(status().isOk()).andExpect(jsonPath("$.etapa").value("ACESSO_LIBERADO"));
		assertThat(usuarioRepository.findByLogin("adm.novo").orElseThrow().isSenhaTemporaria()).isFalse();
	}

	@Test
	void depoisDaTrocaProfissionalSegueParaOSegundoFator() throws Exception {
		String senha = senhaDe(criar("enf.nova", "Enf Nova", "[\"ENFERMEIRO\"]", vinculo(hospital, "[\"ENFERMEIRO\"]", "COREN 99")));
		String token = JsonPath.read(login("enf.nova", senha).andReturn().getResponse().getContentAsString(), "$.token");

		trocaInicial(token, "NovaSenha2026").andExpect(status().isOk())
				.andExpect(jsonPath("$.etapa").value("CADASTRAR_SEGUNDO_FATOR"));
	}

	@ParameterizedTest
	@ValueSource(strings = {"somenteletras", "1234567890", "curta1A"})
	void trocaInicialRecusaSenhaFraca(String fraca) throws Exception {
		String login = "fraca" + Math.abs(fraca.hashCode());
		String senha = senhaDe(criar(login, "Adm", "[\"ADMINISTRATIVO\"]", vinculo(hospital, "[\"ADMINISTRATIVO\"]", null)));
		String token = JsonPath.read(login(login, senha).andReturn().getResponse().getContentAsString(), "$.token");

		trocaInicial(token, fraca).andExpect(status().is4xxClientError());
		assertThat(usuarioRepository.findByLogin(login).orElseThrow().isSenhaTemporaria()).isTrue();
	}

	@Test
	void trocaInicialRecusaIgualAoLoginEIgualASenhaGerada() throws Exception {
		String senha = senhaDe(criar("adm.igual", "Adm", "[\"ADMINISTRATIVO\"]", vinculo(hospital, "[\"ADMINISTRATIVO\"]", null)));
		String token = JsonPath.read(login("adm.igual", senha).andReturn().getResponse().getContentAsString(), "$.token");

		trocaInicial(token, senha).andExpect(status().isUnprocessableContent());
	}

	@Test
	void tokenDeAcessoNaoServeParaATrocaInicial() throws Exception {
		mvc.perform(post("/auth/senha/troca-inicial").with(user(auditor).roles("AUDITOR_GESTOR"))
				.contentType(MediaType.APPLICATION_JSON).content("{\"novaSenha\":\"NovaSenha2026\"}"))
				.andExpect(status().isForbidden());
	}

	@Test
	void recusaOQueNaoFazSentido() throws Exception {
		criar("com.paciente", "X", "[\"PACIENTE\"]", null).andExpect(status().isUnprocessableContent());
		criar("auditor.misto", "X", "[\"AUDITOR_GESTOR\",\"MEDICO\"]", null).andExpect(status().isUnprocessableContent());
		criar("medico.sem.vinculo", "X", "[\"MEDICO\"]", null).andExpect(status().isUnprocessableContent());
		criar("medico.sem.registro", "X", "[\"MEDICO\"]", vinculo(hospital, "[\"MEDICO\"]", " ")).andExpect(status().isUnprocessableContent());
		criar("recepcao.perfil.errado", "X", "[\"RECEPCAO\"]", vinculo(hospital, "[\"MEDICO\"]", "CRM 1")).andExpect(status().isUnprocessableContent());
		criar("auditor.com.vinculo", "X", "[\"AUDITOR_GESTOR\"]", vinculo(hospital, "[\"RECEPCAO\"]", null)).andExpect(status().isUnprocessableContent());
		criar("instituicao.falsa", "X", "[\"RECEPCAO\"]", "{\"instituicaoId\":\"%s\",\"perfis\":[\"RECEPCAO\"]}".formatted(UUID.randomUUID()))
				.andExpect(status().isUnprocessableContent());
		criar("Login Invalido", "X", "[\"RECEPCAO\"]", vinculo(hospital, "[\"RECEPCAO\"]", null)).andExpect(status().isBadRequest());
		assertThat(usuarioRepository.findByLogin("com.paciente")).isEmpty();
	}

	@Test
	void loginDuplicadoERecusado() throws Exception {
		criar("repetido", "A", "[\"RECEPCAO\"]", vinculo(hospital, "[\"RECEPCAO\"]", null)).andExpect(status().isCreated());
		criar("repetido", "B", "[\"RECEPCAO\"]", vinculo(hospital, "[\"RECEPCAO\"]", null)).andExpect(status().isUnprocessableContent());
	}

	@Test
	void auditorPodeCriarOutroAuditorSemVinculo() throws Exception {
		criar("auditor.novo", "Auditor Novo", "[\"AUDITOR_GESTOR\"]", null).andExpect(status().isCreated())
				.andExpect(jsonPath("$.usuario.vinculos.length()").value(0));
	}

	@Test
	void listaComBuscaFiltroDePerfilESituacao() throws Exception {
		criar("lista.medica", "Maria Médica", "[\"MEDICO\"]", vinculo(hospital, "[\"MEDICO\"]", "CRM 1")).andExpect(status().isCreated());
		criar("lista.recepcao", "Rita Recepção", "[\"RECEPCAO\"]", vinculo(hospital, "[\"RECEPCAO\"]", null)).andExpect(status().isCreated());
		UUID rita = usuarioRepository.findByLogin("lista.recepcao").orElseThrow().getId();
		alterarAtivo(rita, false).andExpect(status().isOk());

		como(get("/usuarios").param("busca", "lista.")).andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(2));
		como(get("/usuarios").param("busca", "MARIA")).andExpect(jsonPath("$.content.length()").value(1))
				.andExpect(jsonPath("$.content[0].login").value("lista.medica"))
				.andExpect(jsonPath("$.content[0].vinculos[0].instituicaoNome").value(hospital.getNome()));
		como(get("/usuarios").param("busca", "lista.").param("perfil", "RECEPCAO")).andExpect(jsonPath("$.content.length()").value(1));
		como(get("/usuarios").param("busca", "lista.").param("ativo", "false")).andExpect(jsonPath("$.content.length()").value(1))
				.andExpect(jsonPath("$.content[0].login").value("lista.recepcao"));
		como(get("/usuarios").param("busca", "lista.").param("ativo", "true")).andExpect(jsonPath("$.content.length()").value(1));
	}

	@Test
	void usuarioDesativadoNaoEntraENemUsaOTokenQueJaTinha() throws Exception {
		String senha = senhaDe(criar("sai.fora", "Sai Fora", "[\"ADMINISTRATIVO\"]", vinculo(hospital, "[\"ADMINISTRATIVO\"]", null)));
		String pendente = JsonPath.read(login("sai.fora", senha).andReturn().getResponse().getContentAsString(), "$.token");
		String acesso = JsonPath.read(trocaInicial(pendente, "NovaSenha2026").andReturn().getResponse().getContentAsString(), "$.token");
		mvc.perform(get("/usuarios/eu").header(HttpHeaders.AUTHORIZATION, "Bearer " + acesso)).andExpect(status().isOk());
		UUID id = usuarioRepository.findByLogin("sai.fora").orElseThrow().getId();

		alterarAtivo(id, false).andExpect(status().isOk()).andExpect(jsonPath("$.ativo").value(false));

		login("sai.fora", "NovaSenha2026").andExpect(status().isUnauthorized());
		mvc.perform(get("/usuarios/eu").header(HttpHeaders.AUTHORIZATION, "Bearer " + acesso)).andExpect(status().isUnauthorized());

		alterarAtivo(id, true).andExpect(status().isOk()).andExpect(jsonPath("$.ativo").value(true));
		login("sai.fora", "NovaSenha2026").andExpect(status().isOk());
		assertThat(contarAcoes("USUARIO_DESATIVADO", "sai.fora")).isEqualTo(1);
		assertThat(contarAcoes("USUARIO_REATIVADO", "sai.fora")).isEqualTo(1);
	}

	@Test
	void auditorNaoDesativaASiMesmo() throws Exception {
		UUID eu = usuarioRepository.findByLogin(auditor).orElseThrow().getId();
		alterarAtivo(eu, false).andExpect(status().isUnprocessableContent());
	}

	@Test
	void naoDesativaOUltimoAuditorAtivo() throws Exception {
		UUID eu = usuarioRepository.findByLogin(auditor).orElseThrow().getId();
		criar("outro.auditor", "Outro", "[\"AUDITOR_GESTOR\"]", null).andExpect(status().isCreated());
		UUID outro = usuarioRepository.findByLogin("outro.auditor").orElseThrow().getId();
		jdbc.update("update usuario set ativo = false where id in (select usuario_id from usuario_perfil where perfil = 'AUDITOR_GESTOR')"
				+ " and id not in (?, ?)", eu, outro);

		mvc.perform(patch("/usuarios/{id}/ativo", eu).with(user("outro.auditor").roles("AUDITOR_GESTOR"))
				.contentType(MediaType.APPLICATION_JSON).content("{\"ativo\":false}")).andExpect(status().isOk());
		mvc.perform(patch("/usuarios/{id}/ativo", outro).with(user(auditor).roles("AUDITOR_GESTOR"))
				.contentType(MediaType.APPLICATION_JSON).content("{\"ativo\":false}")).andExpect(status().isUnprocessableContent());
	}

	@Test
	void redefinirSenhaGeraOutraTemporariaEExigeNovaTroca() throws Exception {
		String senha = senhaDe(criar("esqueceu", "Esqueceu", "[\"ADMINISTRATIVO\"]", vinculo(hospital, "[\"ADMINISTRATIVO\"]", null)));
		String pendente = JsonPath.read(login("esqueceu", senha).andReturn().getResponse().getContentAsString(), "$.token");
		trocaInicial(pendente, "NovaSenha2026").andExpect(status().isOk());
		UUID id = usuarioRepository.findByLogin("esqueceu").orElseThrow().getId();

		String corpo = como(post("/usuarios/{id}/senha-temporaria", id)).andExpect(status().isOk())
				.andExpect(jsonPath("$.login").value("esqueceu")).andReturn().getResponse().getContentAsString();
		String nova = JsonPath.read(corpo, "$.senhaTemporaria");

		assertThat(nova).hasSize(16).isNotEqualTo(senha);
		login("esqueceu", "NovaSenha2026").andExpect(status().isUnauthorized());
		login("esqueceu", nova).andExpect(jsonPath("$.etapa").value("TROCAR_SENHA"));
		assertThat(contarAcoes("SENHA_REDEFINIDA", "esqueceu")).isEqualTo(1);
	}

	@Test
	void auditorNaoRedefineAPropriaSenhaPorAqui() throws Exception {
		UUID eu = usuarioRepository.findByLogin(auditor).orElseThrow().getId();
		como(post("/usuarios/{id}/senha-temporaria", eu)).andExpect(status().isUnprocessableContent());
	}

	@Test
	void zerarOSegundoFatorLimpaOCadastroDoAutenticador() throws Exception {
		criar("com.mfa", "Com MFA", "[\"MEDICO\"]", vinculo(hospital, "[\"MEDICO\"]", "CRM 5")).andExpect(status().isCreated());
		transacao.executeWithoutResult(estado -> {
			Usuario existente = usuarioRepository.findByLogin("com.mfa").orElseThrow();
			existente.setMfaHabilitado(true);
			existente.setMfaSegredo("SEGREDOFICTICIO");
		});
		UUID id = usuarioRepository.findByLogin("com.mfa").orElseThrow().getId();
		assertThat(jdbc.queryForObject("select mfa_segredo from usuario where login = 'com.mfa'", String.class)).isNotNull();

		como(post("/usuarios/{id}/segundo-fator/reinicio", id)).andExpect(status().isOk())
				.andExpect(jsonPath("$.mfaHabilitado").value(false));

		assertThat(jdbc.queryForObject("select mfa_segredo from usuario where login = 'com.mfa'", String.class)).isNull();
		assertThat(contarAcoes("SEGUNDO_FATOR_ZERADO", "com.mfa")).isEqualTo(1);
	}

	@Test
	void adicionaEEncerraVinculos() throws Exception {
		criar("varios.hospitais", "Vários", "[\"ENFERMEIRO\"]", vinculo(hospital, "[\"ENFERMEIRO\"]", "COREN 1")).andExpect(status().isCreated());
		UUID id = usuarioRepository.findByLogin("varios.hospitais").orElseThrow().getId();
		Instituicao outro = cenario.instituicao(true);

		como(post("/usuarios/{id}/vinculos", id).content(vinculo(hospital, "[\"ENFERMEIRO\"]", "COREN 1")))
				.andExpect(status().isUnprocessableContent());
		como(post("/usuarios/{id}/vinculos", id).content(vinculo(outro, "[\"MEDICO\"]", "CRM 1")))
				.andExpect(status().isUnprocessableContent());
		String corpo = como(post("/usuarios/{id}/vinculos", id).content(vinculo(outro, "[\"ENFERMEIRO\"]", "COREN 1")))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.vinculos.length()").value(2))
				.andReturn().getResponse().getContentAsString();
		List<String> ids = JsonPath.read(corpo, "$.vinculos[?(@.instituicaoId == '" + outro.getId() + "')].id");
		String vinculoId = ids.getFirst();

		String encerrado = como(post("/usuarios/{id}/vinculos/{vinculo}/encerramento", id, vinculoId))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		List<Boolean> ativos = JsonPath.read(encerrado, "$.vinculos[?(@.instituicaoId == '" + outro.getId() + "')].ativo");
		assertThat(ativos).containsExactly(false);
		como(post("/usuarios/{id}/vinculos/{vinculo}/encerramento", id, vinculoId)).andExpect(status().isUnprocessableContent());
		como(post("/usuarios/{id}/vinculos/{vinculo}/encerramento", id, UUID.randomUUID())).andExpect(status().isNotFound());
		assertThat(contarAcoes("VINCULO_ADICIONADO", "varios.hospitais")).isEqualTo(1);
		assertThat(contarAcoes("VINCULO_ENCERRADO", "varios.hospitais")).isEqualTo(1);
	}

	@Test
	void listaAsAcoesAdministrativasDasMaisRecentesParaAsMaisAntigas() throws Exception {
		criar("acao.um", "Um", "[\"RECEPCAO\"]", vinculo(hospital, "[\"RECEPCAO\"]", null)).andExpect(status().isCreated());
		relogio.avancar(java.time.Duration.ofMinutes(1));
		UUID id = usuarioRepository.findByLogin("acao.um").orElseThrow().getId();
		alterarAtivo(id, false).andExpect(status().isOk());

		como(get("/usuarios/acoes")).andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].acao").value("USUARIO_DESATIVADO"))
				.andExpect(jsonPath("$.content[0].ator").value(auditor))
				.andExpect(jsonPath("$.content[0].alvo").value("acao.um"))
				.andExpect(jsonPath("$.content[1].acao").value("USUARIO_CRIADO"));
	}

	@Test
	void acoesAdministrativasNaoPodemSerAlteradas() throws Exception {
		criar("imutavel", "Imutável", "[\"RECEPCAO\"]", vinculo(hospital, "[\"RECEPCAO\"]", null)).andExpect(status().isCreated());
		org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () -> jdbc.update("update acao_administrativa set detalhe = 'x'"));
		org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () -> jdbc.update("delete from acao_administrativa"));
	}

	@Test
	void soOAuditorGestorGerenciaUsuarios() throws Exception {
		UUID alvo = usuarioRepository.findByLogin(auditor).orElseThrow().getId();
		String corpo = "{\"login\":\"x.y.z\",\"nome\":\"X\",\"perfis\":[\"RECEPCAO\"]}";
		for (String perfil : new String[] {"MEDICO", "ENFERMEIRO", "ADMINISTRATIVO", "RECEPCAO", "PACIENTE"}) {
			mvc.perform(post("/usuarios").with(user("alguem").roles(perfil)).contentType(MediaType.APPLICATION_JSON).content(corpo))
					.andExpect(status().isForbidden());
			mvc.perform(get("/usuarios").with(user("alguem").roles(perfil))).andExpect(status().isForbidden());
			mvc.perform(get("/usuarios/acoes").with(user("alguem").roles(perfil))).andExpect(status().isForbidden());
			mvc.perform(patch("/usuarios/{id}/ativo", alvo).with(user("alguem").roles(perfil))
					.contentType(MediaType.APPLICATION_JSON).content("{\"ativo\":false}")).andExpect(status().isForbidden());
			mvc.perform(post("/usuarios/{id}/senha-temporaria", alvo).with(user("alguem").roles(perfil))).andExpect(status().isForbidden());
			mvc.perform(post("/usuarios/{id}/segundo-fator/reinicio", alvo).with(user("alguem").roles(perfil))).andExpect(status().isForbidden());
		}
		mvc.perform(get("/usuarios")).andExpect(status().isUnauthorized());
	}

	@Test
	void qualquerUsuarioAlteraASuaSenhaInformandoAAtual() throws Exception {
		String senha = senhaDe(criar("quer.trocar", "Quer Trocar", "[\"ADMINISTRATIVO\"]", vinculo(hospital, "[\"ADMINISTRATIVO\"]", null)));
		String pendente = JsonPath.read(login("quer.trocar", senha).andReturn().getResponse().getContentAsString(), "$.token");
		String acesso = JsonPath.read(trocaInicial(pendente, "NovaSenha2026").andReturn().getResponse().getContentAsString(), "$.token");

		alterarSenha(acesso, "errada-errada1", "OutraSenha2026").andExpect(status().isUnprocessableContent());
		alterarSenha(acesso, "NovaSenha2026", "NovaSenha2026").andExpect(status().isUnprocessableContent());
		alterarSenha(acesso, "NovaSenha2026", "OutraSenha2026").andExpect(status().isNoContent());

		login("quer.trocar", "NovaSenha2026").andExpect(status().isUnauthorized());
		login("quer.trocar", "OutraSenha2026").andExpect(status().isOk());
	}

	private ResultActions criar(String login, String nome, String perfis, String vinculo) throws Exception {
		String vinculos = vinculo == null ? "[]" : "[" + vinculo + "]";
		return como(post("/usuarios").content("{\"login\":\"%s\",\"nome\":\"%s\",\"perfis\":%s,\"vinculos\":%s}"
				.formatted(login, nome, perfis, vinculos)));
	}

	private static String vinculo(Instituicao instituicao, String perfis, String registro) {
		String registroJson = registro == null ? "" : ",\"registroProfissional\":\"%s\"".formatted(registro);
		return "{\"instituicaoId\":\"%s\",\"perfis\":%s%s}".formatted(instituicao.getId(), perfis, registroJson);
	}

	private ResultActions alterarAtivo(UUID id, boolean ativo) throws Exception {
		return como(patch("/usuarios/{id}/ativo", id).content("{\"ativo\":%s}".formatted(ativo)));
	}

	private ResultActions como(MockHttpServletRequestBuilder requisicao) throws Exception {
		return mvc.perform(requisicao.with(user(auditor).roles("AUDITOR_GESTOR")).contentType(MediaType.APPLICATION_JSON));
	}

	private ResultActions login(String login, String senha) throws Exception {
		return mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"login\":\"%s\",\"senha\":\"%s\"}".formatted(login, senha)));
	}

	private ResultActions trocaInicial(String token, String novaSenha) throws Exception {
		return mvc.perform(post("/auth/senha/troca-inicial").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content("{\"novaSenha\":\"%s\"}".formatted(novaSenha)));
	}

	private ResultActions alterarSenha(String token, String atual, String nova) throws Exception {
		return mvc.perform(post("/auth/senha/alteracao").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content("{\"senhaAtual\":\"%s\",\"novaSenha\":\"%s\"}".formatted(atual, nova)));
	}

	private static String senhaDe(ResultActions criacao) throws Exception {
		return JsonPath.read(criacao.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.senhaTemporaria");
	}

	private int contarAcoes(String acao, String alvo) {
		return jdbc.queryForObject("select count(*) from acao_administrativa a join usuario u on u.id = a.alvo_id"
				+ " where a.acao = ? and u.login = ?", Integer.class, acao, alvo);
	}
}
