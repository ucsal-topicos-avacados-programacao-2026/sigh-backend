package com.sigh.sigh.project.triagem.service;

import com.sigh.sigh.project.paciente.entity.Paciente;
import com.sigh.sigh.project.prontuario.entity.Atendimento;
import com.sigh.sigh.project.prontuario.entity.Prontuario;
import com.sigh.sigh.project.triagem.config.TriagemProperties;
import com.sigh.sigh.project.triagem.dto.PacienteNaFilaDTO;
import com.sigh.sigh.project.triagem.entity.CorRisco;
import com.sigh.sigh.project.triagem.entity.Triagem;
import com.sigh.sigh.project.triagem.repository.TriagemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FilaAtendimentoServiceTest {

	private static final UUID INSTITUICAO_ID = UUID.randomUUID();

	@Mock
	private TriagemRepository triagemRepository;

	private FilaAtendimentoService filaAtendimentoService;

	@BeforeEach
	void configurarTemposMaximos() {
		TriagemProperties tempos = new TriagemProperties(Map.of(
				CorRisco.LARANJA, Duration.ofMinutes(10),
				CorRisco.AMARELO, Duration.ofMinutes(60)));
		filaAtendimentoService = new FilaAtendimentoService(triagemRepository, tempos);
	}

	@Test
	void sinalizaQuemPassouDoTempoMaximoDaCor() {
		Triagem laranjaAtrasado = triagemNaFila(CorRisco.LARANJA, 30);
		Triagem amareloNoPrazo = triagemNaFila(CorRisco.AMARELO, 30);
		when(triagemRepository.buscarFila(INSTITUICAO_ID, Pageable.unpaged()))
				.thenReturn(new PageImpl<>(List.of(laranjaAtrasado, amareloNoPrazo)));

		List<PacienteNaFilaDTO> fila = filaAtendimentoService
				.consultarFila(INSTITUICAO_ID, Pageable.unpaged()).getContent();

		assertThat(fila).extracting(PacienteNaFilaDTO::tempoMaximoExcedido).containsExactly(true, false);
		assertThat(fila.getFirst().minutosDeEspera()).isGreaterThanOrEqualTo(30);
	}

	private Triagem triagemNaFila(CorRisco cor, int minutosDeEspera) {
		Paciente paciente = new Paciente();
		paciente.setNome("Paciente Fictício");
		Prontuario prontuario = new Prontuario();
		prontuario.setPaciente(paciente);
		Atendimento atendimento = new Atendimento();
		atendimento.setProntuario(prontuario);
		atendimento.setChegadaEm(LocalDateTime.now().minusMinutes(minutosDeEspera));
		Triagem triagem = new Triagem();
		triagem.setAtendimento(atendimento);
		triagem.setCorConfirmada(cor);
		return triagem;
	}
}
