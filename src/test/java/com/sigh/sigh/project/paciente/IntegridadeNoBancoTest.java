package com.sigh.sigh.project.paciente;

import com.jayway.jsonpath.JsonPath;
import com.sigh.sigh.project.instituicao.entity.Instituicao;
import com.sigh.sigh.project.support.Cenario;
import com.sigh.sigh.project.support.TesteIntegracao;
import com.sigh.sigh.project.usuario.entity.Perfil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BE-17: as regras de consentimento, restrição e representação valem no banco (V4), inclusive para
 * gravações que não passam pelos serviços ou que chegam ao mesmo tempo.
 */
class IntegridadeNoBancoTest extends TesteIntegracao {

	private static final Timestamp AGORA = Timestamp.valueOf(LocalDateTime.of(2026, 11, 10, 12, 0));

	@Autowired
	JdbcTemplate jdbc;

	private Instituicao hospital;
	private UUID pacienteId;
	private UUID prontuarioId;
	private UUID usuarioId;
	private UUID registroId;

	@BeforeEach
	void prepararPaciente() throws Exception {
		hospital = cenario.instituicao(true);
		MvcResult admissao = mvc.perform(post("/api/pacientes/admissoes")
						.with(user(cenario.administrativo(hospital)).roles("ADMINISTRATIVO")).with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"cpf":"%s","nome":"Maria Fictícia","dataNascimento":"1980-05-17",
								 "consentimentoInicial":"AUTORIZADO","instituicaoId":"%s","motivo":"Consulta"}
								""".formatted(Cenario.novoCpf(), hospital.getId())))
				.andExpect(status().isCreated()).andReturn();
		pacienteId = UUID.fromString(JsonPath.read(admissao.getResponse().getContentAsString(), "$.pacienteId"));
		prontuarioId = UUID.fromString(JsonPath.read(admissao.getResponse().getContentAsString(), "$.prontuarioId"));
		usuarioId = cenario.usuario(Perfil.PACIENTE).getId();
		registroId = cenario.registroClinico(pacienteId, hospital, "Saúde mental", "Ansiedade");
	}

	@Test
	void consentimentoComFormatoInvalidoEhRecusado() {
		assertThatThrownBy(() -> consentimento(10, "ADICIONAL", "AUTORIZADO", null, "Saúde mental", null, null))
				.as("adicional sem instituição").isInstanceOf(DataAccessException.class);
		assertThatThrownBy(() -> consentimento(11, "ADICIONAL", "AUTORIZADO", hospital.getId(), "Saúde mental", registroId, null))
				.as("adicional com dois alvos").isInstanceOf(DataAccessException.class);
		assertThatThrownBy(() -> consentimento(12, "ADICIONAL", "NEGADO", hospital.getId(), "Saúde mental", null, null))
				.as("adicional negado").isInstanceOf(DataAccessException.class);
		assertThatThrownBy(() -> consentimento(13, "AMPLO", "AUTORIZADO", hospital.getId(), null, null, null))
				.as("amplo com instituição").isInstanceOf(DataAccessException.class);
		assertThatCode(() -> consentimento(14, "ADICIONAL", "AUTORIZADO", hospital.getId(), null, registroId, null))
				.doesNotThrowAnyException();
	}

	@Test
	void autorizacaoNaoEhRevogadaDuasVezesEConsentimentoNaoMuda() {
		UUID autorizacao = consentimento(20, "ADICIONAL", "AUTORIZADO", hospital.getId(), "Saúde mental", null, null);
		consentimento(21, "ADICIONAL", "REVOGADO", hospital.getId(), "Saúde mental", null, autorizacao);

		assertThatThrownBy(() -> consentimento(22, "ADICIONAL", "REVOGADO", hospital.getId(), "Saúde mental", null, autorizacao))
				.isInstanceOf(DataAccessException.class);
		assertThatThrownBy(() -> jdbc.update("update consentimento set decisao = 'AUTORIZADO' where id = ?", autorizacao))
				.isInstanceOf(DataAccessException.class).hasMessageContaining("não podem ser alteradas");
		assertThatThrownBy(() -> jdbc.update("delete from consentimento where id = ?", autorizacao))
				.isInstanceOf(DataAccessException.class);
	}

	@Test
	void restricaoEmAbertoNaoSeRepeteNemComOutraGrafia() {
		restricao("Saúde mental", null, null);

		assertThatThrownBy(() -> restricao("  SAUDE   mental", null, null)).isInstanceOf(DataAccessException.class);
		assertThatCode(() -> restricao("Saúde mental", null, AGORA)).as("restrição já encerrada").doesNotThrowAnyException();
		restricao(null, registroId, null);
		assertThatThrownBy(() -> restricao(null, registroId, null)).isInstanceOf(DataAccessException.class);
		assertThatThrownBy(() -> restricao("Ortopedia", registroId, null)).as("dois alvos").isInstanceOf(DataAccessException.class);
		assertThatThrownBy(() -> restricao(null, null, null)).as("sem alvo").isInstanceOf(DataAccessException.class);
	}

	@Test
	void representacaoEmAbertoNaoSeRepeteEFimNaoVemAntesDoInicio() {
		representacao(AGORA, null);

		assertThatThrownBy(() -> representacao(AGORA, null)).isInstanceOf(DataAccessException.class);
		assertThatThrownBy(() -> representacao(AGORA, Timestamp.valueOf(LocalDateTime.of(2026, 11, 9, 0, 0))))
				.isInstanceOf(DataAccessException.class);
		assertThatCode(() -> representacao(AGORA, AGORA)).doesNotThrowAnyException();
	}

	@Test
	void quemEncerrouARestricaoFicaRegistrado() throws Exception {
		String titular = cenario.contaDoPaciente(pacienteId);
		String criada = mvc.perform(post("/api/pacientes/{id}/restricoes", pacienteId).with(user(titular).roles("PACIENTE"))
						.with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"categoria\":\"Saúde mental\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.encerradaPor").doesNotExist())
				.andReturn().getResponse().getContentAsString();

		mvc.perform(post("/api/pacientes/{id}/restricoes/{r}/encerramento", pacienteId, JsonPath.read(criada, "$.id"))
						.with(user(titular).roles("PACIENTE")).with(csrf()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.encerradaPor").value("Usuário Fictício"));
	}

	private UUID consentimento(int versao, String tipo, String decisao, UUID destinataria, String categoria, UUID registro,
			UUID anterior) {
		UUID id = UUID.randomUUID();
		jdbc.update("""
				insert into consentimento (id, paciente_id, versao, tipo, decisao, vigente_desde, categoria_alvo,
				  registro_alvo_id, destinataria_id, outorgante_id, anterior_id)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""", id, pacienteId, versao, tipo, decisao, AGORA, categoria, registro, destinataria, usuarioId, anterior);
		return id;
	}

	private void restricao(String categoria, UUID registro, Timestamp fim) {
		jdbc.update("""
				insert into restricao_acesso (id, prontuario_id, inicio_em, fim_em, categoria_alvo, registro_alvo_id,
				  definida_por_id)
				values (?, ?, ?, ?, ?, ?, ?)
				""", UUID.randomUUID(), prontuarioId, AGORA, fim, categoria, registro, usuarioId);
	}

	private void representacao(Timestamp inicio, Timestamp fim) {
		jdbc.update("""
				insert into representacao_legal (id, paciente_id, representante_id, inicio_em, fim_em, documento)
				values (?, ?, ?, ?, ?, 'Termo fictício')
				""", UUID.randomUUID(), pacienteId, usuarioId, inicio, fim);
	}
}
