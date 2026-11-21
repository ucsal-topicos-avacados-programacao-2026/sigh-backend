package com.sigh.sigh.project.paciente;

import com.sigh.sigh.project.comum.erro.ViolacaoDeUnicidade;
import com.sigh.sigh.project.instituicao.entity.Instituicao;
import com.sigh.sigh.project.paciente.entity.Consentimento;
import com.sigh.sigh.project.paciente.entity.DecisaoConsentimento;
import com.sigh.sigh.project.paciente.entity.Paciente;
import com.sigh.sigh.project.paciente.entity.TipoConsentimento;
import com.sigh.sigh.project.paciente.repository.ConsentimentoRepository;
import com.sigh.sigh.project.paciente.repository.PacienteRepository;
import com.sigh.sigh.project.support.Cenario;
import com.sigh.sigh.project.support.TesteIntegracao;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(OutputCaptureExtension.class)
class ProtecaoDeDadosTest extends TesteIntegracao {

	@Autowired
	PacienteRepository pacienteRepository;

	@Autowired
	ConsentimentoRepository consentimentoRepository;

	@Test
	void violacaoDeCpfUnicoNaoExpoeOCpfNoLogNemNaExcecao(CapturedOutput saida) {
		String cpf = Cenario.novoCpf();
		pacienteRepository.saveAndFlush(paciente(cpf));

		DataIntegrityViolationException erro = catchThrowableOfType(DataIntegrityViolationException.class,
				() -> pacienteRepository.saveAndFlush(paciente(cpf)));

		assertThat(erro).isNotNull();
		assertThat(ViolacaoDeUnicidade.eh(erro)).isTrue();
		assertThat(ViolacaoDeUnicidade.restricao(erro)).isEqualTo("paciente_cpf_key");
		assertThat(pilha(erro)).doesNotContain(cpf);
		assertThat(saida.getAll()).doesNotContain(cpf);
	}

	@Test
	void fluxoDeAdmissaoNaoRegistraOCpfNoLog(CapturedOutput saida) throws Exception {
		Instituicao hospital = cenario.instituicao(true);
		String admin = cenario.recepcao(hospital);
		String cpf = Cenario.novoCpf();

		for (int i = 0; i < 2; i++) {
			mvc.perform(post("/api/pacientes/admissoes").with(user(admin).roles("RECEPCAO")).with(csrf())
							.contentType(MediaType.APPLICATION_JSON)
							.content(AdmissaoPacienteTest.novoCadastro(cpf, hospital.getId(), "AUTORIZADO")))
					.andExpect(status().isCreated());
		}
		mvc.perform(post("/api/pacientes/busca").with(user(admin).roles("RECEPCAO")).with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"cpf\":\"%s\",\"instituicaoId\":\"%s\"}".formatted(cpf, hospital.getId())))
				.andExpect(status().isOk());

		assertThat(saida.getAll()).doesNotContain(cpf).doesNotContain(Cenario.comMascara(cpf));
	}

	@Test
	void versaoDeConsentimentoRepetidaEhBarradaPeloBanco() {
		Paciente paciente = pacienteRepository.saveAndFlush(paciente(Cenario.novoCpf()));
		consentimentoRepository.saveAndFlush(consentimento(paciente, 1));

		DataIntegrityViolationException erro = catchThrowableOfType(DataIntegrityViolationException.class,
				() -> consentimentoRepository.saveAndFlush(consentimento(paciente, 1)));

		assertThat(erro).isNotNull();
		assertThat(ViolacaoDeUnicidade.eh(erro)).isTrue();
		assertThat(consentimentoRepository.historico(paciente.getId())).hasSize(1);
	}

	private static Paciente paciente(String cpf) {
		Paciente paciente = new Paciente();
		paciente.setCpf(cpf);
		paciente.setNome("Paciente Fictício");
		paciente.setDataNascimento(LocalDate.of(1990, 1, 1));
		return paciente;
	}

	private static Consentimento consentimento(Paciente paciente, int versao) {
		Consentimento consentimento = new Consentimento();
		consentimento.setPaciente(paciente);
		consentimento.setVersao(versao);
		consentimento.setTipo(TipoConsentimento.AMPLO);
		consentimento.setDecisao(DecisaoConsentimento.AUTORIZADO);
		consentimento.setVigenteDesde(LocalDateTime.of(2026, 11, 10, 12, 0));
		return consentimento;
	}

	private static String pilha(Throwable erro) {
		StringWriter texto = new StringWriter();
		erro.printStackTrace(new PrintWriter(texto));
		return texto.toString();
	}
}
