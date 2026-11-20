package com.sigh.sigh.project.triagem;

import com.jayway.jsonpath.JsonPath;
import com.sigh.sigh.project.instituicao.entity.Instituicao;
import com.sigh.sigh.project.support.Cenario;
import com.sigh.sigh.project.support.TesteIntegracao;
import com.sigh.sigh.project.triagem.dto.PacienteNaFilaDTO;
import com.sigh.sigh.project.triagem.dto.SinaisVitaisDTO;
import com.sigh.sigh.project.triagem.dto.TriagemRequestDTO;
import com.sigh.sigh.project.triagem.dto.TriagemResponseDTO;
import com.sigh.sigh.project.triagem.entity.CorRisco;
import com.sigh.sigh.project.triagem.service.FilaAtendimentoService;
import com.sigh.sigh.project.triagem.service.TriagemService;
import com.sigh.sigh.project.usuario.entity.Perfil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BE-12: com a JVM em UTC (pom) e o relógio da aplicação em America/Bahia, a chegada, a confirmação
 * da triagem e o tempo de espera da fila (RN-005) saem do mesmo relógio e não ganham 3 horas.
 */
class FilaComRelogioUnicoTest extends TesteIntegracao {

	@Autowired
	TriagemService triagemService;

	@Autowired
	FilaAtendimentoService filaAtendimentoService;

	@Test
	void tempoDeEsperaDaFilaUsaOMesmoRelogioDaChegada() throws Exception {
		Instituicao hospital = cenario.instituicao(true);
		String enfermeiro = cenario.vinculo(hospital, Perfil.ENFERMEIRO, LocalDateTime.of(2026, 1, 1, 0, 0), null);
		UUID atendimentoId = admitir(hospital);

		TriagemResponseDTO triagem = triagemService.registrar(new TriagemRequestDTO(atendimentoId, "Febre e tosse",
				new SinaisVitaisDTO("120/80", 88, 18, new BigDecimal("38.2"), new BigDecimal("97.0"))), enfermeiro);
		relogio.avancar(Duration.ofMinutes(5));
		TriagemResponseDTO confirmada = triagemService.confirmarRisco(triagem.id(), CorRisco.AMARELO, enfermeiro);
		assertThat(confirmada.confirmadaEm()).isEqualTo(LocalDateTime.of(2026, 11, 10, 12, 5));

		relogio.avancar(Duration.ofMinutes(25));
		PacienteNaFilaDTO noPrazo = naFila(hospital, atendimentoId, enfermeiro);
		assertThat(noPrazo.chegadaEm()).isEqualTo(LocalDateTime.of(2026, 11, 10, 12, 0));
		assertThat(noPrazo.minutosDeEspera()).isEqualTo(30);
		assertThat(noPrazo.tempoMaximoExcedido()).isFalse();

		// Amarelo tolera 60 minutos (application.properties).
		relogio.avancar(Duration.ofMinutes(31));
		PacienteNaFilaDTO atrasado = naFila(hospital, atendimentoId, enfermeiro);
		assertThat(atrasado.minutosDeEspera()).isEqualTo(61);
		assertThat(atrasado.tempoMaximoExcedido()).isTrue();
	}

	private UUID admitir(Instituicao hospital) throws Exception {
		MvcResult resultado = mvc.perform(post("/api/pacientes/admissoes")
						.with(user(cenario.administrativo(hospital)).roles("ADMINISTRATIVO")).with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"cpf":"%s","nome":"Maria Fictícia","dataNascimento":"1980-05-17",
								 "instituicaoId":"%s","motivo":"Febre"}
								""".formatted(Cenario.novoCpf(), hospital.getId())))
				.andExpect(status().isCreated()).andReturn();
		return UUID.fromString(JsonPath.read(resultado.getResponse().getContentAsString(), "$.atendimentoId"));
	}

	private PacienteNaFilaDTO naFila(Instituicao hospital, UUID atendimentoId, String enfermeiro) {
		List<PacienteNaFilaDTO> fila = filaAtendimentoService.consultarFila(hospital.getId(), enfermeiro, Pageable.unpaged())
				.getContent();
		assertThat(fila).extracting(PacienteNaFilaDTO::atendimentoId).containsExactly(atendimentoId);
		return fila.getFirst();
	}
}
