package com.sigh.sigh.project.usuario;

import com.jayway.jsonpath.JsonPath;
import com.sigh.sigh.project.instituicao.entity.Instituicao;
import com.sigh.sigh.project.instituicao.entity.TipoInstituicao;
import com.sigh.sigh.project.instituicao.repository.InstituicaoRepository;
import com.sigh.sigh.project.usuario.entity.Perfil;
import com.sigh.sigh.project.usuario.entity.Usuario;
import com.sigh.sigh.project.usuario.entity.VinculoInstitucional;
import com.sigh.sigh.project.usuario.repository.UsuarioRepository;
import com.sigh.sigh.project.usuario.repository.VinculoInstitucionalRepository;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AutenticacaoIntegracaoTest {

	private static final String SENHA = "senha-ficticia-123";
	private static final long PERIODO_TOTP_EM_SEGUNDOS = 30;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private InstituicaoRepository instituicaoRepository;

	@Autowired
	private VinculoInstitucionalRepository vinculoRepository;

	@Autowired
	private PasswordEncoder codificadorDeSenha;

	@Autowired
	private SecretGenerator geradorDeSegredo;

	@Test
	void pacienteEntraSemSegundoFator() throws Exception {
		salvarUsuario("paciente.teste", Perfil.PACIENTE, null);

		String token = entrar("paciente.teste", SENHA)
				.andExpect(jsonPath("$.etapa").value("ACESSO_LIBERADO"))
				.andReturn().getResponse().getContentAsString();

		mockMvc.perform(get("/auditoria/meus-acessos").header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(status().isOk());
	}

	@Test
	void senhaErradaNaoEntra() throws Exception {
		salvarUsuario("paciente.teste", Perfil.PACIENTE, null);

		entrar("paciente.teste", "senha-errada").andExpect(status().isUnauthorized());
	}

	@Test
	void requisicaoSemTokenEhRecusada() throws Exception {
		mockMvc.perform(get("/leitos")).andExpect(status().isUnauthorized());
	}

	@Test
	void medicoSoAcessaDepoisDeValidarOSegundoFator() throws Exception {
		String segredo = geradorDeSegredo.generate();
		Usuario medico = salvarUsuario("medico.teste", Perfil.MEDICO, segredo);
		UUID hospital = vincular(medico, Perfil.MEDICO);
		String tokenPendente = entrar("medico.teste", SENHA)
				.andExpect(jsonPath("$.etapa").value("VALIDAR_SEGUNDO_FATOR"))
				.andReturn().getResponse().getContentAsString();

		consultarFila(tokenPendente, hospital).andExpect(status().isForbidden());

		String tokenDeAcesso = validarSegundoFator(tokenPendente, codigoAtual(segredo))
				.andExpect(jsonPath("$.etapa").value("ACESSO_LIBERADO"))
				.andReturn().getResponse().getContentAsString();

		consultarFila(tokenDeAcesso, hospital).andExpect(status().isOk());
		// BE-13: com o segundo fator validado, outra instituição continua fechada sem vínculo.
		consultarFila(tokenDeAcesso, UUID.randomUUID()).andExpect(status().isForbidden());
		mockMvc.perform(get("/leitos").header(HttpHeaders.AUTHORIZATION, bearer(tokenDeAcesso)))
				.andExpect(status().isForbidden());
	}

	@Test
	void codigoErradoNaoLiberaOAcesso() throws Exception {
		salvarUsuario("medico.teste", Perfil.MEDICO, geradorDeSegredo.generate());
		String tokenPendente = entrar("medico.teste", SENHA).andReturn().getResponse().getContentAsString();

		validarSegundoFator(tokenPendente, "000000").andExpect(status().isUnauthorized());
	}

	@Test
	void enfermeiroCadastraOSegundoFatorNoPrimeiroAcesso() throws Exception {
		salvarUsuario("enfermeira.teste", Perfil.ENFERMEIRO, null);
		String tokenPendente = entrar("enfermeira.teste", SENHA)
				.andExpect(jsonPath("$.etapa").value("CADASTRAR_SEGUNDO_FATOR"))
				.andReturn().getResponse().getContentAsString();

		mockMvc.perform(post("/auth/segundo-fator/cadastro").header(HttpHeaders.AUTHORIZATION, bearer(tokenPendente)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.uriAutenticador").value(org.hamcrest.Matchers.startsWith("otpauth://totp/")));
		String segredoCadastrado = usuarioRepository.findByLogin("enfermeira.teste").orElseThrow().getMfaSegredo();

		validarSegundoFator(tokenPendente, codigoAtual(segredoCadastrado)).andExpect(status().isOk());
		assertThat(usuarioRepository.findByLogin("enfermeira.teste").orElseThrow().isMfaHabilitado()).isTrue();
	}

	private ResultActions entrar(String login, String senha) throws Exception {
		String corpo = """
				{"login": "%s", "senha": "%s"}
				""".formatted(login, senha);
		return mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(corpo));
	}

	private ResultActions validarSegundoFator(String respostaComToken, String codigo) throws Exception {
		return mockMvc.perform(post("/auth/segundo-fator/validacao")
				.header(HttpHeaders.AUTHORIZATION, bearer(respostaComToken))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"codigo": "%s"}
						""".formatted(codigo)));
	}

	private ResultActions consultarFila(String respostaComToken, UUID instituicaoId) throws Exception {
		return mockMvc.perform(get("/triagens/fila")
				.param("instituicaoId", instituicaoId.toString())
				.header(HttpHeaders.AUTHORIZATION, bearer(respostaComToken)));
	}

	private String bearer(String respostaComToken) {
		String token = JsonPath.read(respostaComToken, "$.token");
		return "Bearer " + token;
	}

	private String codigoAtual(String segredo) throws Exception {
		long intervaloAtual = Math.floorDiv(new SystemTimeProvider().getTime(), PERIODO_TOTP_EM_SEGUNDOS);
		return new DefaultCodeGenerator().generate(segredo, intervaloAtual);
	}

	/** Vínculo ativo com uma instituição nova (BE-13); devolve o id da instituição. */
	private UUID vincular(Usuario usuario, Perfil perfil) {
		Instituicao instituicao = new Instituicao();
		instituicao.setNome("Hospital Fictício");
		instituicao.setTipo(TipoInstituicao.PUBLICA);
		instituicao.setCredenciada(true);
		instituicaoRepository.save(instituicao);
		VinculoInstitucional vinculo = new VinculoInstitucional();
		vinculo.setUsuario(usuario);
		vinculo.setInstituicao(instituicao);
		vinculo.setPerfisHabilitados(new HashSet<>(Set.of(perfil)));
		vinculo.setInicioEm(LocalDateTime.of(2020, 1, 1, 0, 0));
		vinculoRepository.save(vinculo);
		return instituicao.getId();
	}

	private Usuario salvarUsuario(String login, Perfil perfil, String segredoDoSegundoFator) {
		Usuario usuario = new Usuario();
		usuario.setLogin(login);
		usuario.setNome("Usuário Fictício");
		usuario.setSenhaHash(codificadorDeSenha.encode(SENHA));
		usuario.setPerfis(Set.of(perfil));
		usuario.setMfaSegredo(segredoDoSegundoFator);
		usuario.setMfaHabilitado(segredoDoSegundoFator != null);
		return usuarioRepository.save(usuario);
	}
}
