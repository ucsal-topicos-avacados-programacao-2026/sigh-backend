package com.sigh.sigh.project.paciente;

import com.jayway.jsonpath.JsonPath;
import com.sigh.sigh.project.auditoria.repository.RegistroAuditoriaRepository;
import com.sigh.sigh.project.instituicao.entity.Instituicao;
import com.sigh.sigh.project.support.Cenario;
import com.sigh.sigh.project.support.TesteIntegracao;
import com.sigh.sigh.project.usuario.entity.Perfil;
import com.sigh.sigh.project.usuario.entity.Usuario;
import com.sigh.sigh.project.usuario.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** BE-15: o Administrativo liga a conta do paciente e registra o representante legal. */
class AcessoAoCadastroTest extends TesteIntegracao {

	@Autowired
	RegistroAuditoriaRepository registroAuditoriaRepository;

	@Autowired
	UsuarioRepository usuarioRepository;

	@Autowired
	TransactionTemplate transacao;

	private Instituicao hospital;
	private String administrativo;
	private UUID pacienteId;

	@BeforeEach
	void prepararPaciente() throws Exception {
		hospital = cenario.instituicao(true);
		administrativo = cenario.administrativo(hospital);
		pacienteId = admitir(hospital, administrativo);
	}

	@Test
	void depoisDeVincularAContaOPacienteUsaAsPropriasRotas() throws Exception {
		String conta = cenario.usuario(Perfil.PACIENTE).getLogin();
		consentimentosDe(conta).andExpect(status().isForbidden());

		vincularConta(administrativo, hospital, conta).andExpect(status().isNoContent());

		consentimentosDe(conta).andExpect(status().isOk()).andExpect(jsonPath("$.vigente.decisao").value("AUTORIZADO"));
		mvc.perform(get("/api/pacientes/{id}/historico", pacienteId).with(user(conta).roles("PACIENTE")))
				.andExpect(status().isOk());
	}

	@Test
	void contaPrecisaSerDePacienteLivreEOCadastroSemConta() throws Exception {
		vincularConta(administrativo, hospital, cenario.usuario(Perfil.MEDICO).getLogin())
				.andExpect(status().isUnprocessableContent());
		vincularConta(administrativo, hospital, "login-inexistente").andExpect(status().isUnprocessableContent());
		vincularConta(administrativo, hospital, administrativo).andExpect(status().isUnprocessableContent());

		String contaDeOutro = cenario.contaDoPaciente(admitir(hospital, administrativo));
		vincularConta(administrativo, hospital, contaDeOutro).andExpect(status().isUnprocessableContent());

		vincularConta(administrativo, hospital, cenario.usuario(Perfil.PACIENTE).getLogin()).andExpect(status().isNoContent());
		vincularConta(administrativo, hospital, cenario.usuario(Perfil.PACIENTE).getLogin())
				.andExpect(status().isUnprocessableContent());
	}

	/** Quem não tem relação com o paciente não consegue dar acesso aos dados dele. */
	@Test
	void soAdministrativoComVinculoNumaInstituicaoQueAtendeuOPacienteDaAcesso() throws Exception {
		Instituicao outra = cenario.instituicao(true);
		String conta = cenario.usuario(Perfil.PACIENTE).getLogin();

		vincularConta(cenario.administrativo(outra), outra, conta).andExpect(status().isForbidden());
		vincularConta(cenario.administrativo(outra), hospital, conta).andExpect(status().isForbidden());
		mvc.perform(post("/api/pacientes/{id}/conta", pacienteId).with(user(administrativo).roles("PACIENTE")).with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"instituicaoId\":\"%s\",\"login\":\"%s\"}".formatted(hospital.getId(), conta)))
				.andExpect(status().isForbidden());
		representantes(cenario.administrativo(outra), outra).andExpect(status().isForbidden());
		mvc.perform(post("/api/pacientes/{id}/conta", UUID.randomUUID()).with(user(administrativo).roles("ADMINISTRATIVO"))
						.with(csrf()).contentType(MediaType.APPLICATION_JSON)
						.content("{\"instituicaoId\":\"%s\",\"login\":\"%s\"}".formatted(hospital.getId(), conta)))
				.andExpect(status().isForbidden());
	}

	@Test
	void representanteRegistradoGerenciaEPerdeOAcessoAoSerEncerrado() throws Exception {
		String representante = cenario.usuario(Perfil.PACIENTE).getLogin();
		mvc.perform(get("/api/pacientes/{id}/historico", pacienteId).with(user(representante).roles("PACIENTE")))
				.andExpect(status().isForbidden());

		String registrado = registrarRepresentante(representante, "{}")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.vigente").value(true))
				.andExpect(jsonPath("$.documento").value("Termo de curatela nº 123"))
				.andReturn().getResponse().getContentAsString();
		registrarRepresentante(representante, "{}").andExpect(status().isUnprocessableContent());
		mvc.perform(get("/api/pacientes/{id}/historico", pacienteId).with(user(representante).roles("PACIENTE")))
				.andExpect(status().isOk());
		representantes(administrativo, hospital).andExpect(jsonPath("$.length()").value(1));

		relogio.avancar(Duration.ofMinutes(5));
		mvc.perform(post("/api/pacientes/{id}/representantes/{r}/encerramento", pacienteId, JsonPath.read(registrado, "$.id"))
						.with(user(administrativo).roles("ADMINISTRATIVO")).with(csrf())
						.contentType(MediaType.APPLICATION_JSON).content("{\"instituicaoId\":\"%s\"}".formatted(hospital.getId())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.vigente").value(false));
		mvc.perform(get("/api/pacientes/{id}/historico", pacienteId).with(user(representante).roles("PACIENTE")))
				.andExpect(status().isForbidden());
	}

	/** Dar acesso ao prontuário a alguém fica na auditoria imutável, com quem fez e em qual instituição. */
	@Test
	void vinculoDeContaERepresentacaoFicamNaAuditoria() throws Exception {
		vincularConta(administrativo, hospital, cenario.usuario(Perfil.PACIENTE).getLogin()).andExpect(status().isNoContent());
		String registrado = registrarRepresentante(cenario.usuario(Perfil.PACIENTE).getLogin(), "{}")
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		mvc.perform(post("/api/pacientes/{id}/representantes/{r}/encerramento", pacienteId, JsonPath.read(registrado, "$.id"))
						.with(user(administrativo).roles("ADMINISTRATIVO")).with(csrf())
						.contentType(MediaType.APPLICATION_JSON).content("{\"instituicaoId\":\"%s\"}".formatted(hospital.getId())))
				.andExpect(status().isOk());

		List<String> finalidades = transacao.execute(status -> registroAuditoriaRepository.findAll().stream()
				.filter(registro -> registro.getAtor().getLogin().equals(administrativo))
				.filter(registro -> registro.getInstituicao().getId().equals(hospital.getId()))
				.map(registro -> registro.getFinalidadeDeclarada())
				.toList());
		assertThat(finalidades).containsExactlyInAnyOrder("Vínculo da conta de acesso do paciente",
				"Registro de representante legal", "Encerramento de representação legal");
	}

	@Test
	void administrativoNaoSeRegistraComoRepresentante() throws Exception {
		Usuario proprio = usuarioRepository.findByLogin(administrativo).orElseThrow();
		proprio.getPerfis().add(Perfil.PACIENTE);
		usuarioRepository.save(proprio);

		registrarRepresentante(administrativo, "{}").andExpect(status().isUnprocessableContent());
		vincularConta(administrativo, hospital, administrativo).andExpect(status().isUnprocessableContent());
	}

	@Test
	void representacaoFuturaEncerradaNaoEhEncerradaDeNovo() throws Exception {
		String futura = registrarRepresentante(cenario.usuario(Perfil.PACIENTE).getLogin(),
				"{\"inicioEm\":\"2026-12-01T00:00:00\"}")
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		String id = JsonPath.read(futura, "$.id");

		encerrarRepresentacao(id).andExpect(status().isOk()).andExpect(jsonPath("$.fimEm").value("2026-12-01T00:00:00"));
		encerrarRepresentacao(id).andExpect(status().isUnprocessableContent());
	}

	private ResultActions encerrarRepresentacao(String representacaoId) throws Exception {
		return mvc.perform(post("/api/pacientes/{id}/representantes/{r}/encerramento", pacienteId, representacaoId)
				.with(user(administrativo).roles("ADMINISTRATIVO")).with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content("{\"instituicaoId\":\"%s\"}".formatted(hospital.getId())));
	}

	@Test
	void representacaoPrecisaDeDocumentoEPeriodoValido() throws Exception {
		String representante = cenario.usuario(Perfil.PACIENTE).getLogin();
		mvc.perform(post("/api/pacientes/{id}/representantes", pacienteId).with(user(administrativo).roles("ADMINISTRATIVO"))
						.with(csrf()).contentType(MediaType.APPLICATION_JSON)
						.content("{\"instituicaoId\":\"%s\",\"login\":\"%s\"}".formatted(hospital.getId(), representante)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.campos.documento").exists());
		registrarRepresentante(representante, "{\"inicioEm\":\"2026-12-01T00:00:00\",\"fimEm\":\"2026-11-30T00:00:00\"}")
				.andExpect(status().isUnprocessableContent());
		registrarRepresentante(cenario.usuario(Perfil.ENFERMEIRO).getLogin(), "{}").andExpect(status().isUnprocessableContent());

		String conta = cenario.contaDoPaciente(pacienteId);
		registrarRepresentante(conta, "{}").andExpect(status().isUnprocessableContent());
	}

	private ResultActions vincularConta(String loginAdministrativo, Instituicao instituicao, String conta) throws Exception {
		return mvc.perform(post("/api/pacientes/{id}/conta", pacienteId)
				.with(user(loginAdministrativo).roles("ADMINISTRATIVO")).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"instituicaoId\":\"%s\",\"login\":\"%s\"}".formatted(instituicao.getId(), conta)));
	}

	private ResultActions registrarRepresentante(String login, String periodo) throws Exception {
		String corpo = periodo.substring(0, periodo.length() - 1) + (periodo.length() > 2 ? "," : "")
				+ "\"instituicaoId\":\"%s\",\"login\":\"%s\",\"documento\":\"Termo de curatela nº 123\"}"
				.formatted(hospital.getId(), login);
		return mvc.perform(post("/api/pacientes/{id}/representantes", pacienteId)
				.with(user(administrativo).roles("ADMINISTRATIVO")).with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content(corpo));
	}

	private ResultActions representantes(String loginAdministrativo, Instituicao instituicao) throws Exception {
		return mvc.perform(get("/api/pacientes/{id}/representantes", pacienteId)
				.param("instituicaoId", instituicao.getId().toString())
				.with(user(loginAdministrativo).roles("ADMINISTRATIVO")));
	}

	private ResultActions consentimentosDe(String conta) throws Exception {
		return mvc.perform(get("/api/pacientes/me/consentimentos").with(user(conta).roles("PACIENTE")));
	}

	private UUID admitir(Instituicao instituicao, String loginAdministrativo) throws Exception {
		MvcResult resultado = mvc.perform(post("/api/pacientes/admissoes")
						.with(user(loginAdministrativo).roles("ADMINISTRATIVO")).with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"cpf":"%s","nome":"Maria Fictícia","dataNascimento":"1980-05-17",
								 "consentimentoInicial":"AUTORIZADO","instituicaoId":"%s","motivo":"Consulta"}
								""".formatted(Cenario.novoCpf(), instituicao.getId())))
				.andExpect(status().isCreated()).andReturn();
		return UUID.fromString(JsonPath.read(resultado.getResponse().getContentAsString(), "$.pacienteId"));
	}
}
