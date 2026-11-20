package com.sigh.sigh.project.paciente;

import com.sigh.sigh.project.instituicao.entity.Instituicao;
import com.sigh.sigh.project.paciente.entity.Consentimento;
import com.sigh.sigh.project.paciente.entity.DecisaoConsentimento;
import com.sigh.sigh.project.paciente.entity.Paciente;
import com.sigh.sigh.project.paciente.repository.ConsentimentoRepository;
import com.sigh.sigh.project.paciente.repository.PacienteRepository;
import com.sigh.sigh.project.paciente.service.ConsentimentoService;
import com.sigh.sigh.project.prontuario.dto.ConsultaHistoricoDTO;
import com.sigh.sigh.project.prontuario.entity.RegistroClinico;
import com.sigh.sigh.project.prontuario.entity.TipoRegistro;
import com.sigh.sigh.project.prontuario.repository.AtendimentoRepository;
import com.sigh.sigh.project.prontuario.repository.ProntuarioRepository;
import com.sigh.sigh.project.prontuario.repository.RegistroClinicoRepository;
import com.sigh.sigh.project.prontuario.service.HistoricoAutorizadoService;
import com.sigh.sigh.project.support.RelogioDeTeste;
import com.sigh.sigh.project.usuario.entity.Perfil;
import com.sigh.sigh.project.support.Cenario;
import com.sigh.sigh.project.support.TesteIntegracao;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static com.sigh.sigh.project.paciente.AdmissaoPacienteTest.novoCadastro;
import static com.sigh.sigh.project.paciente.AdmissaoPacienteTest.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ConsentimentoTest extends TesteIntegracao {

	@Autowired
	ConsentimentoService consentimentoService;

	@Autowired
	ConsentimentoRepository consentimentoRepository;

	@Autowired
	PacienteRepository pacienteRepository;

	@Autowired
	TransactionTemplate transacao;

	@Autowired
	HistoricoAutorizadoService historicoAutorizadoService;

	@Autowired
	AtendimentoRepository atendimentoRepository;

	@Autowired
	ProntuarioRepository prontuarioRepository;

	@Autowired
	RegistroClinicoRepository registroClinicoRepository;

	/**
	 * RN-007 no histórico compartilhado (BE-06): depois da revogação, a instituição que já tinha
	 * atendido o paciente continua vendo o histórico; a que só atende depois não vê nada.
	 */
	@Test
	void historicoCompartilhadoRespeitaARevogacaoApenasParaNovasInstituicoes() throws Exception {
		Instituicao hospitalA = cenario.instituicao(true);
		Instituicao hospitalC = cenario.instituicao(true);
		String cpf = Cenario.novoCpf();
		MvcResult primeira = admissao(hospitalA, cpf, "AUTORIZADO");
		UUID pacienteId = uuid(primeira, "$.pacienteId");
		UUID atendimentoEmA = uuid(primeira, "$.atendimentoId");
		UUID registro = registroClinico(uuid(primeira, "$.prontuarioId"), hospitalA);

		relogio.avancar(Duration.ofDays(1));
		mvc.perform(decisao(cenario.contaDoPaciente(pacienteId), "REVOGADO")).andExpect(status().isCreated());
		relogio.avancar(Duration.ofHours(2));
		UUID atendimentoEmC = uuid(admissao(hospitalC, cpf, "AUTORIZADO"), "$.atendimentoId");

		assertThat(historicoVistoNo(atendimentoEmA)).containsExactly(registro);
		assertThat(historicoVistoNo(atendimentoEmC)).isEmpty();
	}

	@Test
	void pacienteConsultaOProprioConsentimento() throws Exception {
		Instituicao hospital = cenario.instituicao(true);
		UUID pacienteId = admitir(hospital, "AUTORIZADO");
		String paciente = cenario.contaDoPaciente(pacienteId);

		mvc.perform(get("/api/pacientes/me/consentimentos").with(user(paciente).roles("PACIENTE")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.vigente.decisao").value("AUTORIZADO"))
				.andExpect(jsonPath("$.vigente.versao").value(1))
				.andExpect(jsonPath("$.vigente.tipo").value("AMPLO"))
				.andExpect(jsonPath("$.vigente.vigenteDesde").value("2026-11-10T12:00:00"))
				.andExpect(jsonPath("$.historico.length()").value(1));
	}

	/** Critério de aceite: a revogação passa a valer apenas para novas instituições (RN-007). */
	@Test
	void revogacaoValeApenasParaInstituicoesQueAindaNaoAtenderamOPaciente() throws Exception {
		Instituicao hospitalA = cenario.instituicao(true);
		Instituicao hospitalB = cenario.instituicao(true);
		Instituicao hospitalC = cenario.instituicao(true);
		String cpf = Cenario.novoCpf();
		UUID pacienteId = admitir(hospitalA, cpf, "AUTORIZADO");
		relogio.avancar(Duration.ofDays(1));
		admitir(hospitalB, cpf, "AUTORIZADO");
		String paciente = cenario.contaDoPaciente(pacienteId);

		assertThat(consentimentoService.podeCompartilharCom(pacienteId, hospitalC.getId())).isTrue();

		relogio.avancar(Duration.ofDays(1));
		mvc.perform(decisao(paciente, "REVOGADO"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.decisao").value("REVOGADO"))
				.andExpect(jsonPath("$.versao").value(2));

		assertThat(consentimentoService.podeCompartilharCom(pacienteId, hospitalA.getId())).isTrue();
		assertThat(consentimentoService.podeCompartilharCom(pacienteId, hospitalB.getId())).isTrue();
		assertThat(consentimentoService.podeCompartilharCom(pacienteId, hospitalC.getId())).isFalse();

		// Atender depois da revogação não dá acesso ao histórico compartilhado.
		relogio.avancar(Duration.ofHours(2));
		admitir(hospitalC, cpf, "AUTORIZADO");
		assertThat(consentimentoService.podeCompartilharCom(pacienteId, hospitalC.getId())).isFalse();

		// A revogação não apaga nada: as versões anteriores continuam no histórico.
		assertThat(consentimentoRepository.historico(pacienteId)).extracting(Consentimento::getDecisao)
				.containsExactly(DecisaoConsentimento.AUTORIZADO, DecisaoConsentimento.REVOGADO);
	}

	@Test
	void pacientePodeAutorizarDeNovoDepoisDeRevogar() throws Exception {
		Instituicao hospitalA = cenario.instituicao(true);
		Instituicao hospitalNovo = cenario.instituicao(true);
		UUID pacienteId = admitir(hospitalA, "AUTORIZADO");
		String paciente = cenario.contaDoPaciente(pacienteId);

		relogio.avancar(Duration.ofHours(1));
		mvc.perform(decisao(paciente, "REVOGADO")).andExpect(status().isCreated());
		assertThat(consentimentoService.podeCompartilharCom(pacienteId, hospitalNovo.getId())).isFalse();

		relogio.avancar(Duration.ofHours(1));
		mvc.perform(decisao(paciente, "AUTORIZADO"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.versao").value(3))
				.andExpect(jsonPath("$.vigenteDesde").value("2026-11-10T14:00:00"));
		assertThat(consentimentoService.podeCompartilharCom(pacienteId, hospitalNovo.getId())).isTrue();

		transacao.executeWithoutResult(s -> {
			List<Consentimento> historico = consentimentoRepository.historico(pacienteId);
			assertThat(historico).extracting(Consentimento::getVersao).containsExactly(1, 2, 3);
			assertThat(historico.get(1).getAnterior().getId()).isEqualTo(historico.get(0).getId());
			assertThat(historico.get(2).getAnterior().getId()).isEqualTo(historico.get(1).getId());
			assertThat(historico.get(0).getOutorgante()).isNull();
			assertThat(historico.get(2).getOutorgante().getLogin()).isEqualTo(paciente);
			assertThat(historico.get(2).getVigenteDesde())
					.isEqualTo(LocalDateTime.ofInstant(INICIO.plus(Duration.ofHours(2)), RelogioDeTeste.FUSO));
		});
	}

	@Test
	void negadoNoPrimeiroCadastroNaoCompartilhaNemComQuemCadastrou() throws Exception {
		Instituicao hospitalA = cenario.instituicao(true);
		Instituicao hospitalB = cenario.instituicao(true);
		UUID pacienteId = admitir(hospitalA, "NEGADO");

		assertThat(consentimentoService.podeCompartilharCom(pacienteId, hospitalA.getId())).isFalse();
		assertThat(consentimentoService.podeCompartilharCom(pacienteId, hospitalB.getId())).isFalse();
	}

	@Test
	void semManifestacaoNaoCompartilhaEOPacientePodeAutorizarDepois() throws Exception {
		Instituicao hospital = cenario.instituicao(true);
		String admin = cenario.administrativo(hospital);
		MvcResult admissao = mvc.perform(post("/api/pacientes/admissoes").with(user(admin).roles("ADMINISTRATIVO"))
						.with(csrf()).contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"cpf":"%s","nome":"Sem Manifestação","dataNascimento":"1960-02-02",
								 "instituicaoId":"%s","motivo":"Queda"}
								""".formatted(Cenario.novoCpf(), hospital.getId())))
				.andExpect(status().isCreated()).andReturn();
		UUID pacienteId = uuid(admissao, "$.pacienteId");
		String paciente = cenario.contaDoPaciente(pacienteId);

		assertThat(consentimentoService.podeCompartilharCom(pacienteId, hospital.getId())).isFalse();
		mvc.perform(get("/api/pacientes/me/consentimentos").with(user(paciente).roles("PACIENTE")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.vigente").doesNotExist())
				.andExpect(jsonPath("$.historico.length()").value(0));

		mvc.perform(decisao(paciente, "REVOGADO")).andExpect(status().isUnprocessableContent());
		mvc.perform(decisao(paciente, "AUTORIZADO"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.versao").value(1));
		assertThat(consentimentoService.podeCompartilharCom(pacienteId, hospital.getId())).isTrue();
	}

	@Test
	void decisoesRepetidasOuForaDasOpcoesSaoRecusadas() throws Exception {
		Instituicao hospital = cenario.instituicao(true);
		UUID pacienteId = admitir(hospital, "AUTORIZADO");
		String paciente = cenario.contaDoPaciente(pacienteId);

		mvc.perform(decisao(paciente, "AUTORIZADO")).andExpect(status().isUnprocessableContent());
		mvc.perform(decisao(paciente, "NEGADO")).andExpect(status().isBadRequest());
		mvc.perform(post("/api/pacientes/me/consentimentos").with(user(paciente).roles("PACIENTE")).with(csrf())
						.contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.campos.decisao").exists());

		mvc.perform(decisao(paciente, "REVOGADO")).andExpect(status().isCreated());
		mvc.perform(decisao(paciente, "REVOGADO")).andExpect(status().isUnprocessableContent());

		assertThat(consentimentoRepository.historico(pacienteId)).hasSize(2);
	}

	@Test
	void pacienteSoEnxergaOProprioConsentimento() throws Exception {
		Instituicao hospital = cenario.instituicao(true);
		UUID pacienteUm = admitir(hospital, "AUTORIZADO");
		UUID pacienteDois = admitir(hospital, "NEGADO");
		String loginUm = cenario.contaDoPaciente(pacienteUm);
		cenario.contaDoPaciente(pacienteDois);

		String idDoOutro = consentimentoRepository.historico(pacienteDois).getFirst().getId().toString();
		MvcResult resultado = mvc.perform(get("/api/pacientes/me/consentimentos").with(user(loginUm).roles("PACIENTE")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.vigente.decisao").value("AUTORIZADO"))
				.andReturn();
		assertThat(resultado.getResponse().getContentAsString()).doesNotContain(idDoOutro);

		// A revogação de um paciente não afeta o outro.
		mvc.perform(decisao(loginUm, "REVOGADO")).andExpect(status().isCreated());
		assertThat(consentimentoRepository.historico(pacienteDois)).hasSize(1);
	}

	@Test
	void acessoAosEndpointsDoPacienteExigePerfilEContaVinculada() throws Exception {
		String semCadastro = cenario.usuario(com.sigh.sigh.project.usuario.entity.Perfil.PACIENTE).getLogin();

		mvc.perform(get("/api/pacientes/me/consentimentos").with(user(semCadastro).roles("PACIENTE")))
				.andExpect(status().isForbidden());
		mvc.perform(decisao(semCadastro, "AUTORIZADO")).andExpect(status().isForbidden());

		for (String papel : List.of("ADMINISTRATIVO", "MEDICO", "ENFERMEIRO", "AUDITOR_GESTOR")) {
			mvc.perform(get("/api/pacientes/me/consentimentos").with(user("qualquer").roles(papel)))
					.andExpect(status().isForbidden());
		}
		mvc.perform(get("/api/pacientes/me/consentimentos")).andExpect(status().isUnauthorized());
	}

	@Test
	void instituicaoNaoCredenciadaOuInexistenteNuncaRecebeCompartilhamento() throws Exception {
		Instituicao hospital = cenario.instituicao(true);
		Instituicao naoCredenciada = cenario.instituicao(false);
		UUID pacienteId = admitir(hospital, "AUTORIZADO");

		assertThat(consentimentoService.podeCompartilharCom(pacienteId, naoCredenciada.getId())).isFalse();
		assertThat(consentimentoService.podeCompartilharCom(pacienteId, UUID.randomUUID())).isFalse();
		assertThat(consentimentoService.podeCompartilharCom(UUID.randomUUID(), hospital.getId())).isFalse();
	}

	private UUID admitir(Instituicao instituicao, String consentimento) throws Exception {
		return admitir(instituicao, Cenario.novoCpf(), consentimento);
	}

	private UUID admitir(Instituicao instituicao, String cpf, String consentimento) throws Exception {
		MvcResult resultado = mvc.perform(post("/api/pacientes/admissoes")
						.with(user(cenario.administrativo(instituicao)).roles("ADMINISTRATIVO")).with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content(novoCadastro(cpf, instituicao.getId(), consentimento)))
				.andExpect(status().isCreated()).andReturn();
		return uuid(resultado, "$.pacienteId");
	}

	private MvcResult admissao(Instituicao instituicao, String cpf, String consentimento) throws Exception {
		return mvc.perform(post("/api/pacientes/admissoes")
						.with(user(cenario.administrativo(instituicao)).roles("ADMINISTRATIVO")).with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content(novoCadastro(cpf, instituicao.getId(), consentimento)))
				.andExpect(status().isCreated()).andReturn();
	}

	private UUID registroClinico(UUID prontuarioId, Instituicao origem) {
		return transacao.execute(status -> {
			RegistroClinico registro = new RegistroClinico();
			registro.setProntuario(prontuarioRepository.getReferenceById(prontuarioId));
			registro.setTipo(TipoRegistro.DIAGNOSTICO);
			registro.setCriadoEm(LocalDateTime.of(2026, 11, 10, 13, 0));
			registro.setConteudo("Registro fictício");
			registro.setCategoriaInformacao("Clínica geral");
			registro.setAutor(cenario.usuario(Perfil.MEDICO));
			registro.setOrigem(origem);
			return registroClinicoRepository.save(registro).getId();
		});
	}

	private List<UUID> historicoVistoNo(UUID atendimentoId) {
		String medico = cenario.usuario(Perfil.MEDICO).getLogin();
		ConsultaHistoricoDTO consulta = new ConsultaHistoricoDTO(medico, Perfil.MEDICO, "Teste", 20);
		return transacao.execute(status -> historicoAutorizadoService
				.consultar(atendimentoRepository.findById(atendimentoId).orElseThrow(), consulta)
				.stream().map(RegistroClinico::getId).toList());
	}

	private MockHttpServletRequestBuilder decisao(String login, String decisao) {
		return post("/api/pacientes/me/consentimentos").with(user(login).roles("PACIENTE")).with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content("{\"decisao\":\"%s\"}".formatted(decisao));
	}
}
