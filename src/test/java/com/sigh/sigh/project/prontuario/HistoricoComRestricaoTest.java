package com.sigh.sigh.project.prontuario;

import com.jayway.jsonpath.JsonPath;
import com.sigh.sigh.project.auditoria.entity.RegistroAuditoria;
import com.sigh.sigh.project.auditoria.repository.RegistroAuditoriaRepository;
import com.sigh.sigh.project.ia.dto.CasoResumoDTO;
import com.sigh.sigh.project.ia.service.ResumoHistoricoService;
import com.sigh.sigh.project.instituicao.entity.Instituicao;
import com.sigh.sigh.project.paciente.repository.PacienteRepository;
import com.sigh.sigh.project.support.Cenario;
import com.sigh.sigh.project.support.TesteIntegracao;
import com.sigh.sigh.project.triagem.dto.SinaisVitaisDTO;
import com.sigh.sigh.project.triagem.dto.TriagemRequestDTO;
import com.sigh.sigh.project.triagem.service.ContextoTriagemService;
import com.sigh.sigh.project.triagem.service.TriagemService;
import com.sigh.sigh.project.usuario.entity.Perfil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BE-07: o paciente restringe informações sensíveis (RF-009), libera para uma instituição com
 * autorização adicional, e o profissional só vê o restrito em emergência, com justificativa
 * registrada antes da exibição (RF-010, RN-003). BE-14: a instituição vê o que ela mesma registrou.
 */
class HistoricoComRestricaoTest extends TesteIntegracao {

	private static final String JUSTIFICATIVA = "Paciente inconsciente com suspeita de overdose, risco iminente de morte";

	@Autowired
	TriagemService triagemService;

	@Autowired
	ContextoTriagemService contextoTriagemService;

	@Autowired
	ResumoHistoricoService resumoHistoricoService;

	@Autowired
	RegistroAuditoriaRepository registroAuditoriaRepository;

	@Autowired
	TransactionTemplate transacao;

	@Autowired
	PacienteRepository pacienteRepository;

	private Instituicao hospitalA;
	private Instituicao hospitalB;
	private UUID pacienteId;
	private String contaDoPaciente;
	private UUID atendimentoEmB;
	private UUID asma;
	private UUID depressao;

	/** Paciente com consentimento amplo atendido em A e em B; A registrou asma e depressão. */
	@BeforeEach
	void prepararPaciente() throws Exception {
		hospitalA = cenario.instituicao(true);
		hospitalB = cenario.instituicao(true);
		String cpf = Cenario.novoCpf();
		pacienteId = uuid(admitir(hospitalA, cpf, "AUTORIZADO"), "$.pacienteId");
		asma = cenario.registroClinico(pacienteId, hospitalA, "Clínica geral", "Asma persistente");
		depressao = cenario.registroClinico(pacienteId, hospitalA, "Saúde mental", "Episódio depressivo grave");
		atendimentoEmB = uuid(admitir(hospitalB, cpf, "AUTORIZADO"), "$.atendimentoId");
		contaDoPaciente = cenario.contaDoPaciente(pacienteId);
	}

	@Test
	void semRestricaoOOutroHospitalVeTudoPeloConsentimentoAmplo() throws Exception {
		historico(atendimentoEmB, cenario.profissional(hospitalB, Perfil.MEDICO), "MEDICO")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.registros[*].id").value(containsInAnyOrder(asma.toString(), depressao.toString())))
				.andExpect(jsonPath("$.possuiInformacoesRestritas").value(false));
	}

	/** Critério de aceite: o restrito não aparece sem autorização, nem indiretamente pela triagem e pelo resumo por IA. */
	@Test
	void categoriaRestritaSomeDaConsultaDaTriagemEDoResumoPorIA() throws Exception {
		restringirCategoria("saúde mental");
		String medicoB = cenario.profissional(hospitalB, Perfil.MEDICO);
		String enfermeiroB = cenario.profissional(hospitalB, Perfil.ENFERMEIRO);

		historico(atendimentoEmB, medicoB, "MEDICO")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.registros[*].id").value(containsInAnyOrder(asma.toString())))
				.andExpect(jsonPath("$.possuiInformacoesRestritas").value(true));

		UUID triagemId = triagemService.registrar(new TriagemRequestDTO(atendimentoEmB, "Falta de ar",
				new SinaisVitaisDTO("130/85", 110, 28, new BigDecimal("36.8"), new BigDecimal("91.0"))), enfermeiroB).id();
		assertThat(contextoTriagemService.montarCaso(triagemId, enfermeiroB).registrosUsados()).containsExactly(asma);

		CasoResumoDTO caso = resumoHistoricoService.solicitar(atendimentoEmB, medicoB);
		assertThat(caso.historico()).contains("Asma persistente").doesNotContain("depressivo");
	}

	@Test
	void restricaoDeUmRegistroEspecificoEscondeSoEle() throws Exception {
		mvc.perform(post("/api/pacientes/{id}/restricoes", pacienteId).with(paciente()).with(csrf())
						.contentType(MediaType.APPLICATION_JSON).content("{\"registroId\":\"%s\"}".formatted(asma)))
				.andExpect(status().isCreated());

		historico(atendimentoEmB, cenario.profissional(hospitalB, Perfil.MEDICO), "MEDICO")
				.andExpect(jsonPath("$.registros[*].id").value(containsInAnyOrder(depressao.toString())));
	}

	@Test
	void autorizacaoAdicionalLiberaORestritoSoParaAInstituicaoEscolhida() throws Exception {
		restringirCategoria("Saúde mental");
		Instituicao hospitalC = cenario.instituicao(true);
		UUID atendimentoEmC = uuid(admitirExistente(hospitalC), "$.atendimentoId");
		String autorizacao = mvc.perform(post("/api/pacientes/{id}/autorizacoes", pacienteId).with(paciente()).with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"instituicaoId\":\"%s\",\"categoria\":\"Saúde mental\"}".formatted(hospitalB.getId())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.decisao").value("AUTORIZADO"))
				.andReturn().getResponse().getContentAsString();

		historico(atendimentoEmB, cenario.profissional(hospitalB, Perfil.MEDICO), "MEDICO")
				.andExpect(jsonPath("$.registros[*].id").value(containsInAnyOrder(asma.toString(), depressao.toString())))
				.andExpect(jsonPath("$.registros[?(@.id == '%s')].restrito".formatted(depressao)).value(hasItem(true)))
				.andExpect(jsonPath("$.possuiInformacoesRestritas").value(false));
		historico(atendimentoEmC, cenario.profissional(hospitalC, Perfil.MEDICO), "MEDICO")
				.andExpect(jsonPath("$.registros[*].id").value(containsInAnyOrder(asma.toString())));

		mvc.perform(post("/api/pacientes/{id}/autorizacoes/{a}/revogacao", pacienteId, JsonPath.read(autorizacao, "$.id"))
						.with(paciente()).with(csrf()))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.decisao").value("REVOGADO"));
		historico(atendimentoEmB, cenario.profissional(hospitalB, Perfil.MEDICO), "MEDICO")
				.andExpect(jsonPath("$.registros[*].id").value(containsInAnyOrder(asma.toString())));
	}

	/** Critério de aceite: acesso emergencial só com justificativa, e identificado no histórico de acessos. */
	@Test
	void emergenciaMostraORestritoDepoisDeRegistrarAJustificativa() throws Exception {
		restringirCategoria("Saúde mental");
		String enfermeiroB = cenario.profissional(hospitalB, Perfil.ENFERMEIRO);
		long acessosAntes = registroAuditoriaRepository.count();

		emergencia(atendimentoEmB, enfermeiroB, "ENFERMEIRO", "{}").andExpect(status().isBadRequest());
		emergencia(atendimentoEmB, enfermeiroB, "ENFERMEIRO", justificativa("urgente")).andExpect(status().isBadRequest());
		assertThat(registroAuditoriaRepository.count()).isEqualTo(acessosAntes);

		emergencia(atendimentoEmB, enfermeiroB, "ENFERMEIRO", justificativa(JUSTIFICATIVA))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.acessoDeEmergencia").value(true))
				.andExpect(jsonPath("$.registros[*].id").value(containsInAnyOrder(asma.toString(), depressao.toString())))
				.andExpect(jsonPath("$.registros[?(@.id == '%s')].restrito".formatted(depressao)).value(hasItem(true)));

		List<RegistroAuditoria> emergencias = transacao.execute(status -> registroAuditoriaRepository.findAll().stream()
				.filter(RegistroAuditoria::isEmergencia)
				.filter(registro -> registro.getAtor().getLogin().equals(enfermeiroB))
				.peek(registro -> registro.getRegistrosAcessados().size())
				.toList());
		assertThat(emergencias).singleElement().satisfies(registro -> {
			assertThat(registro.getJustificativa()).isEqualTo(JUSTIFICATIVA);
			assertThat(registro.getPerfil()).isEqualTo(Perfil.ENFERMEIRO);
			assertThat(registro.getFinalidadeDeclarada()).isEqualTo("Acesso de emergência");
			assertThat(registro.getInstituicao().getId()).isEqualTo(hospitalB.getId());
			assertThat(registro.getRegistrosAcessados()).extracting(r -> r.getId()).contains(depressao);
		});

		mvc.perform(get("/auditoria/meus-acessos").with(paciente()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[?(@.emergencia == true)].finalidade").value(hasItem("Acesso de emergência")));
	}

	@Test
	void emergenciaExigeVinculoNaInstituicaoEAtendimentoEmAndamento() throws Exception {
		Instituicao outro = cenario.instituicao(true);
		emergencia(atendimentoEmB, cenario.profissional(outro, Perfil.MEDICO), "MEDICO", justificativa(JUSTIFICATIVA))
				.andExpect(status().isForbidden());
		emergencia(atendimentoEmB, cenario.profissional(hospitalB, Perfil.ADMINISTRATIVO), "ADMINISTRATIVO",
				justificativa(JUSTIFICATIVA))
				.andExpect(status().isForbidden());

		String medicoB = cenario.profissional(hospitalB, Perfil.MEDICO);
		mvc.perform(post("/atendimentos/{id}/encerramento", atendimentoEmB).with(user(medicoB).roles("MEDICO")).with(csrf()))
				.andExpect(status().isNoContent());
		emergencia(atendimentoEmB, medicoB, "MEDICO", justificativa(JUSTIFICATIVA))
				.andExpect(status().isUnprocessableContent());
	}

	/** BE-14: sem consentimento amplo, cada instituição vê só o que ela mesma registrou. */
	@Test
	void semConsentimentoCadaInstituicaoVeOsProprioRegistros() throws Exception {
		String cpf = Cenario.novoCpf();
		MvcResult emA = admitir(hospitalA, cpf, "NEGADO");
		UUID semConsentimento = uuid(emA, "$.pacienteId");
		UUID registroDeA = cenario.registroClinico(semConsentimento, hospitalA, "Clínica geral", "Hipertensão");
		UUID atendimentoB = uuid(admitir(hospitalB, cpf, "NEGADO"), "$.atendimentoId");
		UUID registroDeB = cenario.registroClinico(semConsentimento, hospitalB, "Clínica geral", "Diabetes");

		historico(uuid(emA, "$.atendimentoId"), cenario.profissional(hospitalA, Perfil.MEDICO), "MEDICO")
				.andExpect(jsonPath("$.registros[*].id").value(containsInAnyOrder(registroDeA.toString())));
		historico(atendimentoB, cenario.profissional(hospitalB, Perfil.MEDICO), "MEDICO")
				.andExpect(jsonPath("$.registros[*].id").value(containsInAnyOrder(registroDeB.toString())));
	}

	private void restringirCategoria(String categoria) throws Exception {
		mvc.perform(post("/api/pacientes/{id}/restricoes", pacienteId).with(paciente()).with(csrf())
						.contentType(MediaType.APPLICATION_JSON).content("{\"categoria\":\"%s\"}".formatted(categoria)))
				.andExpect(status().isCreated());
	}

	private ResultActions historico(UUID atendimentoId, String login, String perfil) throws Exception {
		return mvc.perform(get("/atendimentos/{id}/historico", atendimentoId).with(user(login).roles(perfil)));
	}

	private ResultActions emergencia(UUID atendimentoId, String login, String perfil, String corpo) throws Exception {
		return mvc.perform(post("/atendimentos/{id}/historico/emergencia", atendimentoId)
				.with(user(login).roles(perfil)).with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content(corpo));
	}

	private static String justificativa(String texto) {
		return "{\"justificativa\":\"%s\"}".formatted(texto);
	}

	private RequestPostProcessor paciente() {
		return user(contaDoPaciente).roles("PACIENTE");
	}

	private MvcResult admitir(Instituicao hospital, String cpf, String consentimento) throws Exception {
		return mvc.perform(post("/api/pacientes/admissoes")
						.with(user(cenario.administrativo(hospital)).roles("ADMINISTRATIVO")).with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"cpf":"%s","nome":"Maria Fictícia","dataNascimento":"1980-05-17",
								 "consentimentoInicial":"%s","instituicaoId":"%s","motivo":"Falta de ar"}
								""".formatted(cpf, consentimento, hospital.getId())))
				.andExpect(status().isCreated()).andReturn();
	}

	private MvcResult admitirExistente(Instituicao hospital) throws Exception {
		String cpf = transacao.execute(status -> pacienteCpf());
		return admitir(hospital, cpf, "AUTORIZADO");
	}

	private String pacienteCpf() {
		return pacienteRepository.findById(pacienteId).orElseThrow().getCpf();
	}

	private static UUID uuid(MvcResult resultado, String caminho) throws Exception {
		return UUID.fromString(JsonPath.read(resultado.getResponse().getContentAsString(), caminho));
	}
}
