package com.sigh.sigh.project.usuario;

import com.jayway.jsonpath.JsonPath;
import com.sigh.sigh.project.ia.service.ResumoHistoricoService;
import com.sigh.sigh.project.instituicao.entity.Instituicao;
import com.sigh.sigh.project.prontuario.repository.RegistroClinicoRepository;
import com.sigh.sigh.project.support.Cenario;
import com.sigh.sigh.project.support.TesteIntegracao;
import com.sigh.sigh.project.usuario.entity.Perfil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class VinculoObrigatorioTest extends TesteIntegracao {

	private static final String REGISTRO = """
			{"tipo":"DIAGNOSTICO","conteudo":"Pneumonia","categoriaInformacao":"Clínica geral"}
			""";
	private static final String TRIAGEM = """
			{"atendimentoId":"%s","sintomas":"Febre","sinaisVitais":{"pressaoArterial":"120/80","frequenciaCardiaca":90,
			 "frequenciaRespiratoria":18,"temperatura":38.5,"saturacaoOxigenio":96.0}}
			""";

	@Autowired
	RegistroClinicoRepository registroClinicoRepository;

	@Autowired
	ResumoHistoricoService resumoHistoricoService;

	private Instituicao hospital;
	private Instituicao outroHospital;
	private UUID pacienteId;
	private UUID atendimentoId;

	@BeforeEach
	void prepararAtendimento() throws Exception {
		hospital = cenario.instituicao(true);
		outroHospital = cenario.instituicao(true);
		MvcResult admissao = mvc.perform(post("/api/pacientes/admissoes")
						.with(user(cenario.recepcao(hospital)).roles("RECEPCAO")).with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"cpf":"%s","nome":"Maria Fictícia","dataNascimento":"1980-05-17",
								 "consentimentoInicial":"AUTORIZADO","instituicaoId":"%s","motivo":"Febre"}
								""".formatted(Cenario.novoCpf(), hospital.getId())))
				.andExpect(status().isCreated()).andReturn();
		pacienteId = id(admissao, "$.pacienteId");
		atendimentoId = id(admissao, "$.atendimentoId");
	}

	@Test
	void medicoDeOutraInstituicaoNaoRegistraNemEncerraOAtendimento() throws Exception {
		String medicoDeFora = cenario.profissional(outroHospital, Perfil.MEDICO);
		long registrosAntes = registroClinicoRepository.count();

		como(medicoDeFora, "MEDICO", post("/atendimentos/{id}/registros", atendimentoId).content(REGISTRO))
				.andExpect(status().isForbidden());
		como(medicoDeFora, "MEDICO", post("/atendimentos/{id}/encerramento", atendimentoId))
				.andExpect(status().isForbidden());
		assertThat(registroClinicoRepository.count()).isEqualTo(registrosAntes);

		como(cenario.profissional(hospital, Perfil.MEDICO), "MEDICO",
				post("/atendimentos/{id}/registros", atendimentoId).content(REGISTRO))
				.andExpect(status().isCreated());
	}

	@Test
	void vinculoEncerradoOuComOutroPerfilNaoVale() throws Exception {
		LocalDateTime agora = LocalDateTime.now(relogio);
		String exMedico = cenario.vinculo(hospital, Perfil.MEDICO, agora.minusYears(1), agora.minusDays(1));
		String enfermeiroComoMedico = cenario.profissional(hospital, Perfil.ENFERMEIRO);

		como(exMedico, "MEDICO", post("/atendimentos/{id}/registros", atendimentoId).content(REGISTRO))
				.andExpect(status().isForbidden());
		como(enfermeiroComoMedico, "MEDICO", post("/atendimentos/{id}/registros", atendimentoId).content(REGISTRO))
				.andExpect(status().isForbidden());
	}

	@Test
	void enfermeiroDeOutraInstituicaoNaoTriaNemVeAFila() throws Exception {
		String enfermeiroDeFora = cenario.profissional(outroHospital, Perfil.ENFERMEIRO);
		String enfermeiro = cenario.profissional(hospital, Perfil.ENFERMEIRO);

		como(enfermeiroDeFora, "ENFERMEIRO", post("/triagens").content(TRIAGEM.formatted(atendimentoId)))
				.andExpect(status().isForbidden());
		String triagem = como(enfermeiro, "ENFERMEIRO", post("/triagens").content(TRIAGEM.formatted(atendimentoId)))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		String triagemId = JsonPath.read(triagem, "$.id");

		como(enfermeiroDeFora, "ENFERMEIRO", post("/triagens/{id}/sugestao", triagemId)).andExpect(status().isForbidden());
		como(enfermeiroDeFora, "ENFERMEIRO", post("/triagens/{id}/confirmacao", triagemId).content("{\"cor\":\"VERDE\"}"))
				.andExpect(status().isForbidden());
		como(enfermeiroDeFora, "ENFERMEIRO", get("/triagens/fila").param("instituicaoId", hospital.getId().toString()))
				.andExpect(status().isForbidden());
		como(cenario.profissional(outroHospital, Perfil.MEDICO), "MEDICO",
				get("/triagens/fila").param("instituicaoId", hospital.getId().toString()))
				.andExpect(status().isForbidden());

		como(enfermeiro, "ENFERMEIRO", post("/triagens/{id}/confirmacao", triagemId).content("{\"cor\":\"VERDE\"}"))
				.andExpect(status().isOk());
		como(cenario.profissional(hospital, Perfil.MEDICO), "MEDICO",
				get("/triagens/fila").param("instituicaoId", hospital.getId().toString()))
				.andExpect(status().isOk());
	}

	@Test
	void medicoDeOutraInstituicaoNaoLeNemValidaOResumoPorIA() throws Exception {
		cenario.registroClinico(pacienteId, hospital, "Clínica geral", "Asma");
		UUID resumoId = resumoHistoricoService.solicitar(atendimentoId, cenario.profissional(hospital, Perfil.MEDICO))
				.resumoId();
		String medicoDeFora = cenario.profissional(outroHospital, Perfil.MEDICO);

		como(medicoDeFora, "MEDICO", get("/resumos/{id}", resumoId)).andExpect(status().isForbidden());
		como(medicoDeFora, "MEDICO", post("/resumos/{id}/validacao", resumoId)).andExpect(status().isForbidden());
		como(medicoDeFora, "MEDICO", post("/atendimentos/{id}/resumos", atendimentoId)).andExpect(status().isForbidden());
		como(cenario.profissional(hospital, Perfil.MEDICO), "MEDICO", get("/resumos/{id}", resumoId))
				.andExpect(status().isOk());
	}

	@Test
	void administrativoSoMexeEmLeitosEInternacoesDaPropriaInstituicao() throws Exception {
		String administrativo = cenario.administrativo(hospital);
		String administrativoDeFora = cenario.administrativo(outroHospital);
		String leito = """
				{"instituicaoId":"%s","codigo":"%s","setor":"Enfermaria","categoria":"ENFERMARIA"}
				""";

		como(administrativoDeFora, "ADMINISTRATIVO", post("/leitos").content(leito.formatted(hospital.getId(), "E-01")))
				.andExpect(status().isForbidden());
		String criado = como(administrativo, "ADMINISTRATIVO", post("/leitos").content(leito.formatted(hospital.getId(), "E-01")))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		String leitoId = JsonPath.read(criado, "$.id");
		String leitoDeFora = JsonPath.read(como(administrativoDeFora, "ADMINISTRATIVO",
						post("/leitos").content(leito.formatted(outroHospital.getId(), "E-02")))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");

		como(administrativoDeFora, "ADMINISTRATIVO", patch("/leitos/{id}/status", leitoId).content("{\"status\":\"BLOQUEADO\"}"))
				.andExpect(status().isForbidden());
		como(administrativoDeFora, "ADMINISTRATIVO", post("/internacoes")
						.content("{\"atendimentoId\":\"%s\",\"leitoId\":\"%s\"}".formatted(atendimentoId, leitoId)))
				.andExpect(status().isForbidden());
		como(administrativo, "ADMINISTRATIVO", post("/internacoes")
						.content("{\"atendimentoId\":\"%s\",\"leitoId\":\"%s\"}".formatted(atendimentoId, leitoDeFora)))
				.andExpect(status().isUnprocessableContent());
		como(administrativo, "ADMINISTRATIVO", post("/internacoes")
						.content("{\"atendimentoId\":\"%s\",\"leitoId\":\"%s\"}".formatted(atendimentoId, leitoId)))
				.andExpect(status().isCreated());
	}

	private ResultActions como(String login, String perfil, MockHttpServletRequestBuilder requisicao) throws Exception {
		return mvc.perform(requisicao.with(user(login).roles(perfil)).with(csrf()).contentType(MediaType.APPLICATION_JSON));
	}

	private static UUID id(MvcResult resultado, String caminho) throws Exception {
		return UUID.fromString(JsonPath.read(resultado.getResponse().getContentAsString(), caminho));
	}
}
